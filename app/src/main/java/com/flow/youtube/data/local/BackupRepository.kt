package com.flow.youtube.data.local

import android.content.Context
import android.net.Uri
import com.flow.youtube.data.local.entity.PlaylistEntity
import com.flow.youtube.data.local.entity.PlaylistVideoCrossRef
import com.flow.youtube.data.local.entity.VideoEntity
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.stream.JsonReader
import java.io.StringReader
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

data class BackupData(
    val version: Int = 1,
    val timestamp: Long = System.currentTimeMillis(),
    val viewHistory: List<VideoHistoryEntry>? = emptyList(),
    val searchHistory: List<SearchHistoryItem>? = emptyList(),
    val subscriptions: List<ChannelSubscription>? = emptyList(),
    val playlists: List<PlaylistEntity>? = emptyList(),
    val playlistVideos: List<PlaylistVideoCrossRef>? = emptyList(),
    val videos: List<VideoEntity>? = emptyList()
)

class BackupRepository(private val context: Context) {
    private val gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    private fun parseBackupJson(json: String): BackupData? {
        val reader = JsonReader(StringReader(json))
        reader.setStrictness(Strictness.LENIENT)
        return gson.fromJson(reader, BackupData::class.java)
    }
    private val viewHistory = ViewHistory.getInstance(context)
    private val searchHistoryRepo = SearchHistoryRepository(context)
    private val subscriptionRepo = SubscriptionRepository.getInstance(context)
    private val database = AppDatabase.getDatabase(context)

    suspend fun exportData(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupData = BackupData(
                viewHistory = viewHistory.getAllHistory().first(),
                searchHistory = searchHistoryRepo.getSearchHistoryFlow().first(),
                subscriptions = subscriptionRepo.getAllSubscriptions().first(),
                playlists = database.playlistDao().getAllPlaylists().first(),
                playlistVideos = database.playlistDao().getAllPlaylistVideoCrossRefs(),
                videos = database.videoDao().getAllVideos()
            )

            val json = gson.toJson(backupData)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(json)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importData(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: return@withContext Result.failure(Exception("Could not read file"))

            val backupData = parseBackupJson(json)
                ?: return@withContext Result.failure(Exception("Invalid backup file"))

            // Import View History
            backupData.viewHistory?.forEach { entry ->
                viewHistory.savePlaybackPosition(
                    videoId = entry.videoId,
                    position = entry.position,
                    duration = entry.duration,
                    title = entry.title,
                    thumbnailUrl = entry.thumbnailUrl,
                    channelName = entry.channelName,
                    channelId = entry.channelId,
                    isMusic = entry.isMusic
                )
            }

            // Import Search History
            backupData.searchHistory?.forEach { item ->
                searchHistoryRepo.saveSearchQuery(item.query, item.type)
            }

            // Import Subscriptions
            backupData.subscriptions?.forEach { sub ->
                subscriptionRepo.subscribe(sub)
            }

            // Import Room Data
            database.withTransaction {
                // We merge (insert with ignore/replace)
                backupData.videos?.forEach { database.videoDao().insertVideo(it) }
                backupData.playlists?.forEach { database.playlistDao().insertPlaylist(it) }
                backupData.playlistVideos?.forEach { database.playlistDao().insertPlaylistVideoCrossRef(it) }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importNewPipe(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var importedCount = 0
            val subscriptionsToImport = mutableListOf<ChannelSubscription>()
            val semaphore = Semaphore(5) // Limit concurrent requests

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonObject = org.json.JSONObject(jsonString)
                
                if (jsonObject.has("subscriptions")) {
                    val subscriptionsArray = jsonObject.getJSONArray("subscriptions")
                    
                    for (i in 0 until subscriptionsArray.length()) {
                        val item = subscriptionsArray.getJSONObject(i)
                        // NewPipe Export Format: service_id, url, name
                        val url = item.optString("url")
                        val name = item.optString("name")
                        
                        if (url.isNotEmpty() && name.isNotEmpty()) {
                            var channelId = ""
                            if (url.contains("/channel/")) {
                                channelId = url.substringAfter("/channel/")
                            } else if (url.contains("/user/")) {
                                channelId = url.substringAfter("/user/")
                            }
                            
                            if (channelId.contains("/")) channelId = channelId.substringBefore("/")
                            if (channelId.contains("?")) channelId = channelId.substringBefore("?")
                            
                            if (channelId.isNotEmpty()) {
                                val subscription = ChannelSubscription(
                                    channelId = channelId,
                                    channelName = name,
                                    channelThumbnail = "", // Will be fetched
                                    subscribedAt = System.currentTimeMillis()
                                )
                                subscriptionsToImport.add(subscription)
                            }
                        }
                    }
                }
            }

            // Fetch avatars in parallel with rate limiting
            val subscriptionsWithAvatars = supervisorScope {
                subscriptionsToImport.map { sub ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                val avatarUrl = fetchChannelAvatar(sub.channelId)
                                sub.copy(channelThumbnail = avatarUrl)
                            } catch (e: Exception) {
                                sub // Return original if fail
                            }
                        }
                    }
                }.awaitAll()
            }

            subscriptionsWithAvatars.forEach {
                subscriptionRepo.subscribe(it)
                importedCount++
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importYouTube(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var importedCount = 0
            val subscriptionsToImport = mutableListOf<ChannelSubscription>()
            val semaphore = Semaphore(5) // Limit concurrent requests
            
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().use { reader ->
                    // 1. Skip the Header line blindly (handles all languages)
                    reader.readLine()
                    
                    reader.forEachLine { line ->
                        // 2. Limit split to 3 parts to handle commas in titles safely
                        // "UC123,http://...,My Cool, Channel" -> ["UC123", "http://...", "My Cool, Channel"]
                        val parts = line.split(",", limit = 3)
                        
                        if (parts.size >= 3) {
                            val channelId = parts[0]
                            val channelUrl = parts[1]
                            val channelName = parts[2] // This now contains the full name, even with commas
                            
                            if (channelId.isNotEmpty() && channelName.isNotEmpty()) {
                                 val subscription = ChannelSubscription(
                                    channelId = channelId,
                                    channelName = channelName,
                                    channelThumbnail = "", // Will be fetched
                                    subscribedAt = System.currentTimeMillis()
                                )
                                subscriptionsToImport.add(subscription)
                            }
                        }
                    }
                }
            }
            
            // Fetch avatars in parallel with rate limiting
            val subscriptionsWithAvatars = supervisorScope {
                subscriptionsToImport.map { sub ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            try {
                                val avatarUrl = fetchChannelAvatar(sub.channelId)
                                sub.copy(channelThumbnail = avatarUrl)
                            } catch (e: Exception) {
                                sub // Return original if fail
                            }
                        }
                    }
                }.awaitAll()
            }
            
            subscriptionsWithAvatars.forEach {
                subscriptionRepo.subscribe(it)
                importedCount++
            }
            
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper to fetch channel avatar using NewPipe
    private fun fetchChannelAvatar(channelId: String): String {
        return try {
            val url = "https://www.youtube.com/channel/$channelId"
            val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
            info.avatars.maxByOrNull { it.height }?.url ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
