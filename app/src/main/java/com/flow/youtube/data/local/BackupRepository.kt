package com.flow.youtube.data.local

import android.content.Context
import android.net.Uri
import com.flow.youtube.data.local.entity.PlaylistEntity
import com.flow.youtube.data.local.entity.PlaylistVideoCrossRef
import com.flow.youtube.data.local.entity.VideoEntity
import com.google.gson.GsonBuilder
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

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
    private val gson = GsonBuilder().setPrettyPrinting().create()
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

            val backupData = gson.fromJson(json, BackupData::class.java) ?: return@withContext Result.failure(Exception("Invalid backup file"))

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
}
