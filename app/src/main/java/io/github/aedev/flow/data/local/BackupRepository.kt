package io.github.aedev.flow.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.local.entity.PlaylistVideoCrossRef
import io.github.aedev.flow.data.local.entity.VideoEntity
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
import java.util.concurrent.atomic.AtomicInteger
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine

data class SettingsBackup(
    val strings: Map<String, String> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
    val longs: Map<String, Long> = emptyMap()
)

data class BackupData(
    val version: Int = 2,
    val timestamp: Long = System.currentTimeMillis(),
    val viewHistory: List<VideoHistoryEntry>? = emptyList(),
    val searchHistory: List<SearchHistoryItem>? = emptyList(),
    val subscriptions: List<ChannelSubscription>? = emptyList(),
    val playlists: List<PlaylistEntity>? = emptyList(),
    val playlistVideos: List<PlaylistVideoCrossRef>? = emptyList(),
    val videos: List<VideoEntity>? = emptyList(),
    val likedVideos: List<LikedVideoInfo>? = emptyList(),
    val settings: SettingsBackup? = null
)

class BackupRepository(private val context: Context) {
    private val playerPreferences = PlayerPreferences(context)
    private val localDataManager = LocalDataManager(context)
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
    private val likedVideosRepo = LikedVideosRepository.getInstance(context)
    private val database = AppDatabase.getDatabase(context)

    suspend fun exportData(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val playerSettings = playerPreferences.getExportData()
            val localSettings = localDataManager.getExportData()
            
            val mergedSettings = SettingsBackup(
                strings = playerSettings.strings + localSettings.strings,
                booleans = playerSettings.booleans + localSettings.booleans,
                ints = playerSettings.ints + localSettings.ints,
                floats = playerSettings.floats + localSettings.floats,
                longs = playerSettings.longs + localSettings.longs
            )

            val backupData = BackupData(
                viewHistory = viewHistory.getAllHistory().first(),
                searchHistory = searchHistoryRepo.getSearchHistoryFlow().first(),
                subscriptions = subscriptionRepo.getAllSubscriptions().first(),
                playlists = database.playlistDao().getAllPlaylists().first(),
                playlistVideos = database.playlistDao().getAllPlaylistVideoCrossRefs(),
                videos = database.videoDao().getAllVideos(),
                likedVideos = likedVideosRepo.getAllLikedVideos().first(),
                settings = mergedSettings
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

            // Import View History (bulk-insert for performance with large backups)
            backupData.viewHistory?.let { entries ->
                if (entries.isNotEmpty()) viewHistory.bulkSaveHistoryEntries(entries)
            }

            // Import Liked Videos
            backupData.likedVideos?.forEach { info ->
                likedVideosRepo.likeVideo(info)
            }

            // Import Search History
            backupData.searchHistory?.forEach { item ->
                searchHistoryRepo.saveSearchQuery(item.query, item.type)
            }

            // Import Subscriptions
            backupData.subscriptions?.let { subs ->
                subs.forEach { sub ->
                    subscriptionRepo.subscribe(sub)
                }
                // V9.2: Seed recommendation engine from imported subscriptions
                val channelNames = subs.map { it.channelName }.filter { it.isNotEmpty() }
                if (channelNames.isNotEmpty()) {
                    try {
                        FlowNeuroEngine.bootstrapFromSubscriptions(context, channelNames)
                    } catch (e: Exception) {
                    }
                }
            }

            // Import Room Data
            database.withTransaction {
                // We merge (insert with ignore so existing richer data is not overwritten)
                backupData.videos?.forEach { database.videoDao().insertVideoOrIgnore(it) }
                backupData.playlists?.forEach { database.playlistDao().insertPlaylist(it) }
                backupData.playlistVideos?.forEach { database.playlistDao().insertPlaylistVideoCrossRef(it) }
            }

            // Import Settings Data
            backupData.settings?.let { 
                playerPreferences.restoreData(it) 
                localDataManager.restoreData(it)
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importNewPipe(uri: Uri, onProgress: ((current: Int, total: Int) -> Unit)? = null): Result<Int> = withContext(Dispatchers.IO) {
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
                            } else if (url.contains("/@")) {
                                channelId = url.substringAfter("/@")
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
            val totalForProgress = subscriptionsToImport.size
            val completedCount = AtomicInteger(0)
            onProgress?.invoke(0, totalForProgress)
            val subscriptionsWithAvatars = supervisorScope {
                subscriptionsToImport.map { sub ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val result = try {
                                val avatarUrl = fetchChannelAvatar(sub.channelId)
                                sub.copy(channelThumbnail = avatarUrl)
                            } catch (e: Exception) {
                                sub // Return original if fail
                            }
                            onProgress?.invoke(completedCount.incrementAndGet(), totalForProgress)
                            result
                        }
                    }
                }.awaitAll()
            }

            subscriptionsWithAvatars.forEach {
                subscriptionRepo.subscribe(it)
                importedCount++
            }

            // V9.2: Seed recommendation engine from imported subscriptions
            val channelNames = subscriptionsWithAvatars.map { it.channelName }.filter { it.isNotEmpty() }
            if (channelNames.isNotEmpty()) {
                try {
                    FlowNeuroEngine.bootstrapFromSubscriptions(context, channelNames)
                } catch (e: Exception) {
                }
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importYouTube(uri: Uri, onProgress: ((current: Int, total: Int) -> Unit)? = null): Result<Int> = withContext(Dispatchers.IO) {
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
                            val channelId = parts[0].trim().trimStart('\uFEFF')
                            val channelUrl = parts[1].trim()
                            val channelName = parts[2].trim().removeSurrounding("\"")
                            
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
            val ytTotalForProgress = subscriptionsToImport.size
            val ytCompletedCount = AtomicInteger(0)
            onProgress?.invoke(0, ytTotalForProgress)
            val subscriptionsWithAvatars = supervisorScope {
                subscriptionsToImport.map { sub ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val result = try {
                                val avatarUrl = fetchChannelAvatar(sub.channelId)
                                sub.copy(channelThumbnail = avatarUrl)
                            } catch (e: Exception) {
                                sub // Return original if fail
                            }
                            onProgress?.invoke(ytCompletedCount.incrementAndGet(), ytTotalForProgress)
                            result
                        }
                    }
                }.awaitAll()
            }
            
            subscriptionsWithAvatars.forEach {
                subscriptionRepo.subscribe(it)
                importedCount++
            }

            // V9.2: Seed recommendation engine from imported subscriptions
            val ytChannelNames = subscriptionsWithAvatars.map { it.channelName }.filter { it.isNotEmpty() }
            if (ytChannelNames.isNotEmpty()) {
                try {
                    FlowNeuroEngine.bootstrapFromSubscriptions(context, ytChannelNames)
                } catch (e: Exception) {
                }
            }
            
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import YouTube watch history from a YouTube Takeout HTML file (watch-history.html).
     *
     * Streams the file in 64KB chunks so large files (50MB+) do not cause OOM.
     * Saves entries in bulk DataStore transactions (500 per batch) for performance.
     */
    suspend fun importYouTubeWatchHistory(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val READ_SIZE  = 65_536  // 64 KB read buffer
            val OVERLAP    = 2_048   // bytes kept from previous chunk to catch cross-boundary matches
            val BATCH_SIZE = 500     // entries saved per DataStore transaction

            // Match: watch?v=VIDEO_ID">TITLE</a> optionally followed by channel link
            val videoPattern = Regex(
                """href="https://www\.youtube\.com/watch\?v=([\w-]{10,12})"[^>]*?>([^<]+)</a>""",
                RegexOption.IGNORE_CASE
            )
            val channelPattern = Regex(
                """href="https://www\.youtube\.com/channel/([^"&\s]+)"[^>]*?>([^<]+)</a>""",
                RegexOption.IGNORE_CASE
            )

            var importedCount = 0
            val batch = mutableListOf<VideoHistoryEntry>()

            context.contentResolver.openInputStream(uri)
                ?.bufferedReader(Charsets.UTF_8)
                ?.use { reader ->
                    val buf  = CharArray(READ_SIZE)
                    val tail = StringBuilder(OVERLAP)

                    while (true) {
                        val n = reader.read(buf)
                        if (n == -1) break

                        val window = tail.toString() + String(buf, 0, n)

                        val videoMatches   = videoPattern.findAll(window).toList()
                        val channelMatches = channelPattern.findAll(window).toList()
                        var chIdx = 0

                        for (vm in videoMatches) {
                            if (vm.range.last < tail.length) continue

                            val videoId = vm.groupValues[1].trim()
                            if (videoId.isEmpty()) continue

                            val title = unescapeHtmlEntities(vm.groupValues[2].trim())

                            while (chIdx < channelMatches.size &&
                                channelMatches[chIdx].range.first <= vm.range.first
                            ) chIdx++

                            val cm = channelMatches.getOrNull(chIdx)
                            val channelId: String
                            val channelName: String
                            if (cm != null && cm.range.first - vm.range.last < 2_000) {
                                channelId   = cm.groupValues[1].trim()
                                channelName = unescapeHtmlEntities(cm.groupValues[2].trim())
                            } else {
                                channelId   = ""
                                channelName = ""
                            }

                            batch.add(
                                VideoHistoryEntry(
                                    videoId      = videoId,
                                    position     = 0L,
                                    duration     = 0L,
                                    timestamp    = System.currentTimeMillis() - importedCount, 
                                    title        = title,
                                    thumbnailUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                    channelName  = channelName,
                                    channelId    = channelId,
                                    isMusic      = false
                                )
                            )
                            importedCount++
                        }

                        tail.clear()
                        if (window.length > OVERLAP) {
                            tail.append(window, window.length - OVERLAP, window.length)
                        } else {
                            tail.append(window)
                        }

                        if (batch.size >= BATCH_SIZE) {
                            viewHistory.bulkSaveHistoryEntries(batch)
                            batch.clear()
                            kotlinx.coroutines.yield() 
                        }
                    }

                    if (batch.isNotEmpty()) {
                        viewHistory.bulkSaveHistoryEntries(batch)
                        batch.clear()
                    }
                } ?: return@withContext Result.failure(Exception("Could not read file"))

            if (importedCount == 0) {
                return@withContext Result.failure(Exception("no_entries"))
            }

            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun unescapeHtmlEntities(text: String): String = text
        .replace("&amp;",  "&")
        .replace("&lt;",   "<")
        .replace("&gt;",   ">")
        .replace("&quot;", "\"")
        .replace("&#39;",  "'")
        .replace("&apos;", "'")
        .replace("&#x27;", "'")

    /**
     * Import a YouTube playlist from a YouTube Takeout CSV file (e.g. "Watch later-videos.csv").
     *
     * Actual Takeout CSV schema (2-column, no metadata header):
     *   Video ID,Playlist Video Creation Timestamp
     *   wxmYUyLS47w,2026-02-25T19:35:57+00:00
     *
     * The playlist name is derived from the file's display name: strip "-videos.csv" suffix.
     */
    suspend fun importYouTubePlaylist(uri: Uri, isMusic: Boolean = false): Result<Pair<String, Int>> = withContext(Dispatchers.IO) {
        try {
            val displayName = context.contentResolver.query(
                uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            } ?: "Imported Playlist"

            val playlistName = displayName
                .removeSuffix(".csv")
                .let { if (it.endsWith("-videos", ignoreCase = true)) it.dropLast(7) else it }
                .trim()
                .ifEmpty { "Imported Playlist" }

            val videoIds = mutableListOf<String>()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    var headerSkipped = false
                    reader.forEachLine { raw ->
                        val line = raw.trim()
                        if (line.isEmpty()) return@forEachLine

                        if (!headerSkipped) {
                            headerSkipped = true
                            if (line.startsWith("Video ID", ignoreCase = true)) return@forEachLine
                        }

                        val videoId = line.split(",").firstOrNull()?.trim() ?: return@forEachLine

                        if (videoId.isNotEmpty() &&
                            videoId.all { it.isLetterOrDigit() || it == '_' || it == '-' }
                        ) {
                            videoIds.add(videoId)
                        }
                    }
                }
            } ?: return@withContext Result.failure(Exception("Could not read file"))

            if (videoIds.isEmpty()) {
                return@withContext Result.failure(Exception("no_videos"))
            }

            val isWatchLater = playlistName.equals("watch later", ignoreCase = true)
            val playlistId  = if (isWatchLater) PlaylistRepository.WATCH_LATER_ID
                              else "yt_import_${System.currentTimeMillis()}"
            val finalName   = if (isWatchLater) "Watch Later" else playlistName
            val firstThumb  = "https://i.ytimg.com/vi/${videoIds.first()}/hqdefault.jpg"

            database.withTransaction {
                val existingPlaylist = database.playlistDao().getPlaylist(playlistId)
                if (existingPlaylist == null) {
                    database.playlistDao().insertPlaylist(
                        PlaylistEntity(
                            id           = playlistId,
                            name         = finalName,
                            description  = if (isWatchLater) "Your watch later list"
                                           else "Imported from YouTube Takeout",
                            thumbnailUrl = firstThumb,
                            isPrivate    = isWatchLater,
                            createdAt    = System.currentTimeMillis(),
                            isMusic      = isMusic
                        )
                    )
                }

                videoIds.forEachIndexed { index, videoId ->
                    database.videoDao().insertVideoOrIgnore(
                        VideoEntity(
                            id                  = videoId,
                            title               = "",
                            channelName         = "",
                            channelId           = "",
                            thumbnailUrl        = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                            duration            = 0,
                            viewCount           = 0L,
                            uploadDate          = "",
                            description         = "",
                            channelThumbnailUrl = "",
                            isMusic             = isMusic
                        )
                    )
                    database.playlistDao().insertPlaylistVideoCrossRef(
                        PlaylistVideoCrossRef(
                            playlistId = playlistId,
                            videoId    = videoId,
                            position   = index.toLong()
                        )
                    )
                }
            }

            Result.success(Pair(finalName, videoIds.size))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import subscriptions from a LibreTube backup file (JSON).
     *
     * LibreTube native backup format:
     *   { "format": "Piped", "version": 1, "subscriptions": [{channelId, name, avatar}, ...], ... }
     *
     * Avatars are usually included in the backup; fetching is only done when a stored URL is absent.
     */
    suspend fun importLibreTube(uri: Uri, onProgress: ((current: Int, total: Int) -> Unit)? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val subscriptionsToImport = mutableListOf<ChannelSubscription>()
            val semaphore = Semaphore(5)

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val jsonString = inputStream.bufferedReader().use { it.readText() }
                val jsonObject = org.json.JSONObject(jsonString)

                if (jsonObject.has("subscriptions")) {
                    val array = jsonObject.getJSONArray("subscriptions")
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)

                        // LibreTube native: { channelId, name, avatar }
                        val channelId = item.optString("channelId")
                        val name      = item.optString("name", "")
                        val avatar    = item.optString("avatar", "")

                        if (channelId.isNotEmpty()) {
                            subscriptionsToImport.add(
                                ChannelSubscription(
                                    channelId        = channelId,
                                    channelName      = name,
                                    channelThumbnail = avatar,
                                    subscribedAt     = System.currentTimeMillis()
                                )
                            )
                        }
                    }
                }
            }

            val total     = subscriptionsToImport.size
            val completed = AtomicInteger(0)
            onProgress?.invoke(0, total)

            val finalSubs = supervisorScope {
                subscriptionsToImport.map { sub ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            val result = if (sub.channelThumbnail.isEmpty()) {
                                try {
                                    sub.copy(channelThumbnail = fetchChannelAvatar(sub.channelId))
                                } catch (e: Exception) { sub }
                            } else sub
                            onProgress?.invoke(completed.incrementAndGet(), total)
                            result
                        }
                    }
                }.awaitAll()
            }

            finalSubs.forEach { subscriptionRepo.subscribe(it) }

            // V9.2: Seed recommendation engine from imported subscriptions
            val ltChannelNames = finalSubs.map { it.channelName }.filter { it.isNotEmpty() }
            if (ltChannelNames.isNotEmpty()) {
                try {
                    FlowNeuroEngine.bootstrapFromSubscriptions(context, ltChannelNames)
                } catch (e: Exception) {
                }
            }

            Result.success(finalSubs.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import music playlists from a Metrolist backup file (ZIP containing "song.db").
     *
     * The backup is a ZIP archive with two entries:
     *   • settings.preferences_pb   – app settings (ignored)
     *   • song.db                   – Room/SQLite database
     *
     * Tables read:
     *   playlist          (id TEXT, name TEXT, thumbnailUrl TEXT, isLocal INTEGER)
     *   song              (id TEXT, title TEXT, thumbnailUrl TEXT, duration INTEGER, isLocal INTEGER)
     *   playlist_song_map (playlistId TEXT, songId TEXT, position INTEGER)
     *
     * All imported content is tagged isMusic = true so it appears in the Music section.
     */
    suspend fun importMetrolist(uri: Uri, onProgress: ((current: Int, total: Int) -> Unit)? = null): Result<Int> = withContext(Dispatchers.IO) {
        val tempDb = java.io.File(context.cacheDir, "metrolist_import_${System.currentTimeMillis()}.db")
        try {
            // 1. Extract "song.db" from the ZIP archive
            var foundDb = false
            context.contentResolver.openInputStream(uri)?.use { raw ->
                java.util.zip.ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "song.db") {
                            tempDb.outputStream().use { zip.copyTo(it) }
                            foundDb = true
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }

            if (!foundDb) return@withContext Result.failure(Exception("invalid_format"))

            // 2. Open the extracted database read-only
            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                tempDb.absolutePath, null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )

            var importedCount = 0

            db.use {
                // 3. Load playlists (exclude local-file playlists)
                val playlists = mutableListOf<Triple<String, String, String>>()
                db.rawQuery("SELECT id, name, COALESCE(thumbnailUrl,'') FROM playlist WHERE isLocal = 0", null).use { c ->
                    while (c.moveToNext()) playlists.add(Triple(c.getString(0), c.getString(1), c.getString(2)))
                }

                // 4. Load non-local songs
                data class SongRow(val id: String, val title: String, val thumb: String, val duration: Int)
                val songs = mutableMapOf<String, SongRow>()
                db.rawQuery("SELECT id, title, COALESCE(thumbnailUrl,''), COALESCE(duration,0) FROM song WHERE isLocal = 0", null).use { c ->
                    while (c.moveToNext())
                        songs[c.getString(0)] = SongRow(c.getString(0), c.getString(1), c.getString(2), c.getInt(3))
                }

                // 5. Load playlist-song mappings ordered by position
                val playlistSongs = mutableMapOf<String, MutableList<Pair<String, Int>>>()
                db.rawQuery("SELECT playlistId, songId, position FROM playlist_song_map ORDER BY playlistId, position", null).use { c ->
                    while (c.moveToNext())
                        playlistSongs.getOrPut(c.getString(0)) { mutableListOf() }
                            .add(Pair(c.getString(1), c.getInt(2)))
                }

                val total = playlists.size
                var done  = 0
                onProgress?.invoke(0, total)

                // 6. Insert into Flow's database
                database.withTransaction {
                    for ((plId, plName, plThumb) in playlists) {
                        val songList   = playlistSongs[plId] ?: emptyList()
                        val newPlId    = "metro_${System.currentTimeMillis()}_${plId.take(8)}"
                        val thumbUrl   = plThumb.takeIf { it.isNotEmpty() }
                            ?: songList.firstOrNull()?.let { (sid, _) ->
                                songs[sid]?.thumb?.takeIf { it.isNotEmpty() }
                            }
                            ?: ""

                        database.playlistDao().insertPlaylist(
                            PlaylistEntity(
                                id           = newPlId,
                                name         = plName,
                                description  = "Imported from Metrolist",
                                thumbnailUrl = thumbUrl,
                                isPrivate    = false,
                                createdAt    = System.currentTimeMillis(),
                                isMusic      = true
                            )
                        )

                        songList.forEachIndexed { index, (songId, _) ->
                            val row = songs[songId]
                            val thumb = row?.thumb?.ifEmpty {
                                "https://i.ytimg.com/vi/$songId/hqdefault.jpg"
                            } ?: "https://i.ytimg.com/vi/$songId/hqdefault.jpg"

                            database.videoDao().insertVideoOrIgnore(
                                VideoEntity(
                                    id                  = songId,
                                    title               = row?.title ?: "",
                                    channelName         = "",
                                    channelId           = "",
                                    thumbnailUrl        = thumb,
                                    duration            = row?.duration ?: 0,
                                    viewCount           = 0L,
                                    uploadDate          = "",
                                    description         = "",
                                    channelThumbnailUrl = "",
                                    isMusic             = true
                                )
                            )
                            database.playlistDao().insertPlaylistVideoCrossRef(
                                PlaylistVideoCrossRef(
                                    playlistId = newPlId,
                                    videoId    = songId,
                                    position   = index.toLong()
                                )
                            )
                            importedCount++
                        }

                        done++
                        onProgress?.invoke(done, total)
                    }
                }
            }

            if (importedCount == 0) return@withContext Result.failure(Exception("no_content"))
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDb.delete()
        }
    }

    // Helper to fetch channel avatar using NewPipe
    private fun fetchChannelAvatar(channelId: String): String {
        return try {
            val url = if (channelId.startsWith("UC") && channelId.length > 20)
                "https://www.youtube.com/channel/$channelId"
            else
                "https://www.youtube.com/@$channelId"
            val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
            info.avatars.maxByOrNull { it.height }?.url ?: ""
        } catch (e: Exception) {
            ""
        }
    }
}
