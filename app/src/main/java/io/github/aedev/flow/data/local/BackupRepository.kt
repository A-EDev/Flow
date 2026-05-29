package io.github.aedev.flow.data.local

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.local.entity.PlaylistVideoCrossRef
import io.github.aedev.flow.data.local.entity.SubscriptionGroupEntity
import io.github.aedev.flow.data.local.entity.VideoEntity
import com.google.gson.GsonBuilder
import com.google.gson.Strictness
import com.google.gson.annotations.SerializedName
import com.google.gson.stream.JsonReader
import java.io.StringReader
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.roundToLong
import java.util.concurrent.atomic.AtomicInteger
import java.time.Instant
import java.time.OffsetDateTime
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.ThumbnailUrlResolver

data class SettingsBackup(
    val strings: Map<String, String> = emptyMap(),
    val booleans: Map<String, Boolean> = emptyMap(),
    val ints: Map<String, Int> = emptyMap(),
    val floats: Map<String, Float> = emptyMap(),
    val longs: Map<String, Long> = emptyMap()
)

data class ContentPreferencesBackup(
    val preferredTopics: Set<String> = emptySet(),
    val blockedTopics: Set<String> = emptySet(),
    val blockedChannels: Set<String> = emptySet()
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
    val subscriptionGroups: List<SubscriptionGroupEntity>? = emptyList(),
    val likedVideos: List<LikedVideoInfo>? = emptyList(),
    val contentPreferences: ContentPreferencesBackup? = null,
    val settings: SettingsBackup? = null
)

data class NewPipeSubscriptionItem(
    @SerializedName("service_id")
    val serviceId: Int,
    val url: String,
    val name: String
)

data class NewPipeSubscriptionExport(
    val subscriptions: List<NewPipeSubscriptionItem>,
    @SerializedName("app_version")
    val appVersion: String = BuildConfig.VERSION_NAME,
    @SerializedName("app_version_int")
    val appVersionInt: Int = BuildConfig.VERSION_CODE
)

private data class FreeTubeHistoryExportEntry(
    val videoId: String? = null,
    val author: String? = null,
    val authorId: String? = null,
    val title: String? = null,
    val timeWatched: Long? = null,
    val lengthSeconds: Long? = null,
    val watchProgress: Double? = null
)

private data class YouTubeTakeoutHistoryEntryOut(
    val header: String = "YouTube",
    val title: String,
    val titleUrl: String,
    val subtitles: List<YouTubeTakeoutSubtitleOut>,
    val time: String,
    val products: List<String> = listOf("YouTube"),
    val activityControls: List<String> = listOf("YouTube watch history")
)

private data class YouTubeTakeoutSubtitleOut(
    val name: String,
    val url: String
)

private data class YouTubeArchiveSubtitle(
    val name: String? = null,
    val url: String? = null
)

private data class YouTubeArchiveHistoryEntry(
    val title: String? = null,
    val titleUrl: String? = null,
    val subtitles: List<YouTubeArchiveSubtitle>? = null,
    val time: String? = null,
    val details: Any? = null
)

private enum class HistoryImportFormat {
    HTML,
    JSON
}

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

    private suspend fun getContentPreferencesBackup(): ContentPreferencesBackup {
        val engine = FlowNeuroEngine.getInstance(context)
        engine.initialize()
        val brain = engine.getBrainSnapshot()
        return ContentPreferencesBackup(
            preferredTopics = brain.preferredTopics,
            blockedTopics = brain.blockedTopics,
            blockedChannels = brain.blockedChannels
        )
    }

    private suspend fun exportBrainBytes(): ByteArray {
        val engine = FlowNeuroEngine.getInstance(context)
        engine.initialize()
        return ByteArrayOutputStream().also { bos ->
            engine.exportBrainToStream(bos)
        }.toByteArray()
    }

    private fun rememberNeuroBootstrapCandidate(
        candidates: LinkedHashMap<String, VideoHistoryEntry>,
        entry: VideoHistoryEntry,
        limit: Int = 800
    ) {
        if (candidates.size >= limit) return
        if (entry.videoId.isBlank() || entry.title.isBlank()) return
        candidates.putIfAbsent(entry.videoId, entry)
    }

    private suspend fun bootstrapNeuroFromImportedHistory(
        entries: Collection<VideoHistoryEntry>
    ) {
        val videos = entries
            .asSequence()
            .filter { !it.isMusic && it.videoId.isNotBlank() && it.title.isNotBlank() }
            .distinctBy { it.videoId }
            .sortedByDescending { it.timestamp }
            .map { entry ->
                Video(
                    id = entry.videoId,
                    title = entry.title,
                    channelName = entry.channelName,
                    channelId = entry.channelId,
                    thumbnailUrl = entry.thumbnailUrl,
                    duration = if (entry.duration > 0) (entry.duration / 1000).toInt() else 0,
                    viewCount = 0L,
                    uploadDate = "",
                    timestamp = entry.timestamp
                )
            }
            .take(500)
            .toList()

        if (videos.isNotEmpty()) {
            FlowNeuroEngine.bootstrapFromWatchHistory(context, videos)
        }
    }

    /** Detect which launcher icon alias is currently enabled via PackageManager. */
    private fun detectActiveIconSuffix(): String? {
        val pm = context.packageManager
        val pkg = context.packageName
        val knownSuffixes = listOf(
            ".IconFlowRed",
            ".IconFlowLight",
            ".IconAmoled",
            ".IconMonochrome",
            ".IconGhost",
            ".IconDynamic",
            ".IconMaterialSky",
            ".IconMaterialMint"
        )
        return knownSuffixes.firstOrNull { suffix ->
            pm.getComponentEnabledSetting(
                ComponentName(pkg, "io.github.aedev.flow$suffix")
            ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        }
    }

    suspend fun exportData(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val playerSettings = playerPreferences.getExportData()
            val localSettings = localDataManager.getExportData()

            val activeIconSuffix = detectActiveIconSuffix()
            val exportedStrings = if (activeIconSuffix != null)
                playerSettings.strings + mapOf("app_icon_suffix" to activeIconSuffix) + localSettings.strings
            else
                playerSettings.strings + localSettings.strings

            val mergedSettings = SettingsBackup(
                strings = exportedStrings,
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
                subscriptionGroups = database.subscriptionGroupDao().getAllGroupsOnce(),
                likedVideos = likedVideosRepo.getAllLikedVideos().first(),
                contentPreferences = getContentPreferencesBackup(),
                settings = mergedSettings
            )

            val json = gson.toJson(backupData)
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
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

            importBackupData(backupData)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportSubscriptionsAsNewPipe(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val subscriptions = subscriptionRepo.getAllSubscriptions().first()
            val items = subscriptions.mapNotNull { sub ->
                val url = toNewPipeChannelUrl(sub.channelId) ?: return@mapNotNull null
                NewPipeSubscriptionItem(
                    serviceId = 0,
                    url = url,
                    name = sub.channelName.ifBlank { sub.channelId.trim() }
                )
            }
            val payload = NewPipeSubscriptionExport(subscriptions = items)

            val json = gson.toJson(payload)
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                }
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportWatchHistory(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val history = viewHistory.getAllHistory().first()
            val ucIdRegex = Regex("UC[0-9A-Za-z_-]{22}")
            val items = history.mapNotNull { entry ->
                val videoId = entry.videoId.trim()
                if (videoId.isEmpty()) return@mapNotNull null
                val channelUrl = ucIdRegex.find(entry.channelId.trim())?.value
                    ?.let { "https://www.youtube.com/channel/$it" }
                    ?: "https://www.youtube.com/"
                YouTubeTakeoutHistoryEntryOut(
                    title = "Watched ${entry.title}",
                    titleUrl = "https://www.youtube.com/watch?v=$videoId",
                    subtitles = listOf(
                        YouTubeTakeoutSubtitleOut(
                            name = entry.channelName.ifBlank { "YouTube" },
                            url = channelUrl
                        )
                    ),
                    time = Instant.ofEpochMilli(entry.timestamp).toString()
                )
            }

            val json = gson.toJson(items)
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                }
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))

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

    suspend fun importYouTubeWatchHistory(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val format = detectHistoryImportFormat(uri)
            val result = when (format) {
                HistoryImportFormat.HTML -> importHtmlWatchHistory(uri)
                HistoryImportFormat.JSON -> importJsonWatchHistory(uri)
            }

            if (result.isSuccess) {
                result
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("invalid_format"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importNewPipeWatchHistory(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        val tempDb = java.io.File(context.cacheDir, "newpipe_history_${System.currentTimeMillis()}.db")
        try {
            if (!copyNewPipeDatabaseToTempFile(uri, tempDb)) {
                return@withContext Result.failure(Exception("invalid_format"))
            }

            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                tempDb.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )

            val entries = mutableListOf<VideoHistoryEntry>()
            val neuroBootstrapCandidates = LinkedHashMap<String, VideoHistoryEntry>()

            db.rawQuery(
                """
                SELECT s.url, s.title, s.duration, s.uploader, s.uploader_url, s.thumbnail_url,
                       h.access_date, COALESCE(ss.progress_time, 0)
                FROM stream_history h
                INNER JOIN streams s ON s.uid = h.stream_id
                LEFT JOIN stream_state ss ON ss.stream_id = s.uid
                ORDER BY h.access_date DESC
                """.trimIndent(),
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val videoUrl = cursor.getString(0).orEmpty()
                    val videoId = extractYouTubeVideoId(videoUrl) ?: continue

                    val title = cursor.getString(1).orEmpty()
                    if (title.isBlank()) continue
                    val durationMs = if (cursor.isNull(2)) 0L else cursor.getLong(2).coerceAtLeast(0L) * 1000L
                    val channelName = cursor.getString(3).orEmpty()
                    val channelId = extractImportedChannelId(cursor.getString(4))
                    val storedThumbnail = cursor.getString(5).orEmpty()
                    val timestamp = readSqliteTimestamp(cursor, 6) ?: (System.currentTimeMillis() - entries.size)
                    val rawPositionMs = if (cursor.isNull(7)) 0L else cursor.getLong(7).coerceAtLeast(0L)
                    val positionMs = if (durationMs > 0L) rawPositionMs.coerceAtMost(durationMs) else rawPositionMs

                    val historyEntry = VideoHistoryEntry(
                        videoId = videoId,
                        position = positionMs,
                        duration = durationMs,
                        timestamp = timestamp,
                        title = title,
                        thumbnailUrl = storedThumbnail.ifBlank {
                            ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId)
                        },
                        channelName = channelName,
                        channelId = channelId,
                        isMusic = false
                    )
                    entries.add(historyEntry)
                    rememberNeuroBootstrapCandidate(neuroBootstrapCandidates, historyEntry)
                }
            }
            db.close()

            if (entries.isEmpty()) {
                return@withContext Result.failure(Exception("no_entries"))
            }

            viewHistory.bulkSaveHistoryEntries(entries)

            try {
                bootstrapNeuroFromImportedHistory(neuroBootstrapCandidates.values)
            } catch (_: Exception) {
            }

            Result.success(entries.size)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDb.delete()
        }
    }

    private fun detectHistoryImportFormat(uri: Uri): HistoryImportFormat {
        val sample = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { reader ->
                val buffer = CharArray(1024)
                val count = reader.read(buffer)
                if (count > 0) String(buffer, 0, count) else ""
            }
            ?: throw Exception("Could not read file")

        val trimmed = sample.trimStart()
        return when {
            trimmed.startsWith("<") -> HistoryImportFormat.HTML
            trimmed.startsWith("[") || trimmed.startsWith("{") -> HistoryImportFormat.JSON
            else -> throw Exception("invalid_format")
        }
    }

    private suspend fun importHtmlWatchHistory(uri: Uri): Result<Int> {
        val readSize = 65_536
        val overlap = 2_048
        val batchSize = 500

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
        val neuroBootstrapCandidates = LinkedHashMap<String, VideoHistoryEntry>()

        context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use { reader ->
                val buffer = CharArray(readSize)
                val tail = StringBuilder(overlap)

                while (true) {
                    val count = reader.read(buffer)
                    if (count == -1) break

                    val window = tail.toString() + String(buffer, 0, count)
                    val videoMatches = videoPattern.findAll(window).toList()
                    val channelMatches = channelPattern.findAll(window).toList()
                    var channelIndex = 0

                    for (videoMatch in videoMatches) {
                        if (videoMatch.range.last < tail.length) continue

                        val videoId = videoMatch.groupValues[1].trim()
                        if (videoId.isEmpty()) continue

                        val title = unescapeHtmlEntities(videoMatch.groupValues[2].trim())

                        while (channelIndex < channelMatches.size &&
                            channelMatches[channelIndex].range.first <= videoMatch.range.first
                        ) {
                            channelIndex++
                        }

                        val channelMatch = channelMatches.getOrNull(channelIndex)
                        val channelId: String
                        val channelName: String
                        if (channelMatch != null && channelMatch.range.first - videoMatch.range.last < 2_000) {
                            channelId = channelMatch.groupValues[1].trim()
                            channelName = unescapeHtmlEntities(channelMatch.groupValues[2].trim())
                        } else {
                            channelId = ""
                            channelName = ""
                        }

                        val historyEntry = VideoHistoryEntry(
                            videoId = videoId,
                            position = 0L,
                            duration = 0L,
                            timestamp = System.currentTimeMillis() - importedCount,
                            title = title,
                            thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId),
                            channelName = channelName,
                            channelId = channelId,
                            isMusic = false
                        )
                        batch.add(historyEntry)
                        rememberNeuroBootstrapCandidate(neuroBootstrapCandidates, historyEntry)
                        importedCount++
                    }

                    tail.clear()
                    if (window.length > overlap) {
                        tail.append(window, window.length - overlap, window.length)
                    } else {
                        tail.append(window)
                    }

                    if (batch.size >= batchSize) {
                        viewHistory.bulkSaveHistoryEntries(batch)
                        batch.clear()
                        kotlinx.coroutines.yield()
                    }
                }

                if (batch.isNotEmpty()) {
                    viewHistory.bulkSaveHistoryEntries(batch)
                    batch.clear()
                }
            } ?: return Result.failure(Exception("Could not read file"))

        if (importedCount == 0) {
            return Result.failure(Exception("no_entries"))
        }

        try {
            bootstrapNeuroFromImportedHistory(neuroBootstrapCandidates.values)
        } catch (_: Exception) {
        }

        return Result.success(importedCount)
    }

    private suspend fun importJsonWatchHistory(uri: Uri): Result<Int> {
        val raw = context.contentResolver.openInputStream(uri)
            ?.bufferedReader(Charsets.UTF_8)
            ?.use(BufferedReader::readText)
            ?: return Result.failure(Exception("Could not read file"))

        val entries = parseJsonWatchHistoryEntries(raw)
        if (entries.isEmpty()) {
            return Result.failure(Exception("no_entries"))
        }

        viewHistory.bulkSaveHistoryEntries(entries)

        try {
            bootstrapNeuroFromImportedHistory(entries)
        } catch (_: Exception) {
        }

        return Result.success(entries.size)
    }

    private fun parseJsonWatchHistoryEntries(raw: String): List<VideoHistoryEntry> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return emptyList()

        return when {
            trimmed.startsWith("[") -> parseYouTubeArchiveHistoryEntries(trimmed)
            trimmed.startsWith("{") -> parseFreeTubeHistoryEntries(trimmed)
            else -> emptyList()
        }
    }

    private fun parseFreeTubeHistoryEntries(raw: String): List<VideoHistoryEntry> {
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        return lines.mapNotNullIndexed { index, line ->
            runCatching {
                gson.fromJson(line, FreeTubeHistoryExportEntry::class.java)
            }.getOrNull()?.toVideoHistoryEntry(index)
        }
    }

    private fun parseYouTubeArchiveHistoryEntries(raw: String): List<VideoHistoryEntry> {
        val items = runCatching {
            gson.fromJson(raw, Array<YouTubeArchiveHistoryEntry>::class.java)?.toList().orEmpty()
        }.getOrDefault(emptyList())

        return items.mapNotNullIndexed { index, item -> item.toVideoHistoryEntry(index) }
    }

    private fun FreeTubeHistoryExportEntry.toVideoHistoryEntry(index: Int): VideoHistoryEntry? {
        val normalizedVideoId = videoId?.trim().orEmpty()
        val normalizedTitle = title?.trim().orEmpty()
        if (normalizedVideoId.isEmpty() || normalizedTitle.isEmpty()) return null

        val durationMs = (lengthSeconds ?: 0L).coerceAtLeast(0L) * 1000L
        val progress = (watchProgress ?: 0.0).coerceIn(0.0, 1.0)
        val positionMs = if (durationMs > 0L && progress > 0.0) {
            (durationMs.toDouble() * progress).roundToLong().coerceIn(0L, durationMs)
        } else {
            0L
        }

        return VideoHistoryEntry(
            videoId = normalizedVideoId,
            position = positionMs,
            duration = durationMs,
            timestamp = timeWatched ?: (System.currentTimeMillis() - index),
            title = normalizedTitle,
            thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(normalizedVideoId),
            channelName = author?.trim().orEmpty(),
            channelId = authorId?.trim().orEmpty(),
            isMusic = false
        )
    }

    private fun YouTubeArchiveHistoryEntry.toVideoHistoryEntry(index: Int): VideoHistoryEntry? {
        if (details != null) return null

        val videoUrl = titleUrl?.trim().orEmpty()
        val videoId = extractWatchHistoryVideoId(videoUrl)
        if (videoId.isEmpty()) return null

        val rawTitle = title?.trim().orEmpty()
        val normalizedTitle = rawTitle.removePrefix("Watched ").trim()
        if (normalizedTitle.isEmpty()) return null

        val subtitle = subtitles.orEmpty().firstOrNull()
        return VideoHistoryEntry(
            videoId = videoId,
            position = 0L,
            duration = 0L,
            timestamp = parseArchiveTimestamp(time) ?: (System.currentTimeMillis() - index),
            title = normalizedTitle,
            thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId),
            channelName = subtitle?.name?.trim().orEmpty(),
            channelId = extractChannelId(subtitle?.url),
            isMusic = false
        )
    }

    private fun extractWatchHistoryVideoId(url: String): String {
        return Regex("""[?&]v=([\w-]{10,12})""")
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    }

    private fun extractChannelId(url: String?): String {
        return Regex("""/channel/([^/?#]+)""")
            .find(url.orEmpty())
            ?.groupValues
            ?.getOrNull(1)
            .orEmpty()
    }

    private fun parseArchiveTimestamp(value: String?): Long? {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
    }

    private fun readSqliteTimestamp(cursor: android.database.Cursor, columnIndex: Int): Long? {
        if (cursor.isNull(columnIndex)) return null
        return when (cursor.getType(columnIndex)) {
            android.database.Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(columnIndex)
            android.database.Cursor.FIELD_TYPE_STRING -> {
                val raw = cursor.getString(columnIndex).orEmpty().trim()
                raw.toLongOrNull()
                    ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
                    ?: parseArchiveTimestamp(raw)
            }
            else -> null
        }
    }

    private inline fun <T, R> Iterable<T>.mapNotNullIndexed(transform: (index: Int, T) -> R?): List<R> {
        val destination = ArrayList<R>()
        forEachIndexed { index, item ->
            transform(index, item)?.let(destination::add)
        }
        return destination
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
            val firstThumb = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoIds.first())

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
                            thumbnailUrl        = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId),
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
                                ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(songId)
                            } ?: ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(songId)

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

    // ── Google Takeout all-in-one import ──

    suspend fun importYouTubeTakeout(
        uri: Uri,
        onProgress: ((label: String, current: Int, total: Int) -> Unit)? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            var subscriptionsImported = 0
            var historyImported = 0
            var playlistsImported = 0
            var playlistVideosImported = 0

            val playlistMeta = mutableMapOf<String, String>()
            val videoCsvData = mutableMapOf<String, List<String>>()
            data class SubRow(val channelId: String, val channelName: String)
            val subRows = mutableListOf<SubRow>()
            val neuroBootstrapCandidates = LinkedHashMap<String, VideoHistoryEntry>()

            val OVERLAP    = 2_048
            val READ_SIZE  = 65_536
            val BATCH_SIZE = 500

            val videoPattern = Regex(
                """href="https://www\.youtube\.com/watch\?v=([\w-]{10,12})"[^>]*?>([^<]+)</a>""",
                RegexOption.IGNORE_CASE
            )
            val channelPattern = Regex(
                """href="https://www\.youtube\.com/channel/([^"&\s]+)"[^>]*?>([^<]+)</a>""",
                RegexOption.IGNORE_CASE
            )
            val historyBatch = mutableListOf<VideoHistoryEntry>()

            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        when {
                            name.endsWith("subscriptions/subscriptions.csv", ignoreCase = true) -> {
                                onProgress?.invoke("Subscriptions", 0, 0)
                                val reader = zip.bufferedReader(Charsets.UTF_8)
                                reader.readLine()
                                var line = reader.readLine()
                                while (line != null) {
                                    val parts = line.split(",", limit = 3)
                                    if (parts.size >= 3) {
                                        val channelId = parts[0].trim().trimStart('\uFEFF')
                                        val channelName = parts[2].trim().removeSurrounding("\"")
                                        if (channelId.isNotEmpty() && channelName.isNotEmpty()) {
                                            subRows.add(SubRow(channelId, channelName))
                                        }
                                    }
                                    line = reader.readLine()
                                }
                            }

                            name.endsWith("history/watch-history.html", ignoreCase = true) -> {
                                onProgress?.invoke("Watch history", 0, 0)
                                val reader = zip.bufferedReader(Charsets.UTF_8)
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
                                        val chId: String; val chName: String
                                        if (cm != null && cm.range.first - vm.range.last < 2_000) {
                                            chId = cm.groupValues[1].trim(); chName = unescapeHtmlEntities(cm.groupValues[2].trim())
                                        } else { chId = ""; chName = "" }
                                        val historyEntry = VideoHistoryEntry(
                                            videoId      = videoId,
                                            position     = 0L, duration = 0L,
                                            timestamp    = System.currentTimeMillis() - historyImported,
                                            title        = title,
                                            thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId),
                                            channelName  = chName, channelId = chId, isMusic = false
                                        )
                                        historyBatch.add(historyEntry)
                                        rememberNeuroBootstrapCandidate(neuroBootstrapCandidates, historyEntry)
                                        historyImported++
                                        if (historyBatch.size >= BATCH_SIZE) {
                                            viewHistory.bulkSaveHistoryEntries(historyBatch)
                                            historyBatch.clear()
                                            kotlinx.coroutines.yield()
                                        }
                                    }
                                    tail.clear()
                                    if (window.length > OVERLAP) tail.append(window, window.length - OVERLAP, window.length)
                                    else tail.append(window)
                                }
                                if (historyBatch.isNotEmpty()) {
                                    viewHistory.bulkSaveHistoryEntries(historyBatch)
                                    historyBatch.clear()
                                }
                            }

                            name.endsWith("playlists/playlists.csv", ignoreCase = true) -> {
                                val reader = zip.bufferedReader(Charsets.UTF_8)
                                reader.readLine()
                                var line = reader.readLine()
                                while (line != null) {
                                    if (line.isNotBlank()) {
                                        val cols = line.split(",")
                                        val id = cols.getOrNull(0)?.trim() ?: ""
                                        if (cols.size >= 11 && id.startsWith("PL")) {
                                            val title = cols[10].trim().removeSurrounding("\"")
                                            if (title.isNotEmpty()) playlistMeta[id] = title
                                        }
                                    }
                                    line = reader.readLine()
                                }
                            }

                            name.contains("/playlists/") && name.endsWith("-videos.csv", ignoreCase = true) -> {
                                val filename = name.substringAfterLast("/")
                                val ids = mutableListOf<String>()
                                val reader = zip.bufferedReader(Charsets.UTF_8)
                                var headerSkipped = false
                                var rawLine = reader.readLine()
                                while (rawLine != null) {
                                    val line = rawLine.trim()
                                    if (line.isNotEmpty()) {
                                        if (!headerSkipped) {
                                            headerSkipped = true
                                            if (!line.startsWith("Video ID", ignoreCase = true)) {
                                                val videoId = line.split(",").firstOrNull()?.trim() ?: ""
                                                if (videoId.isNotEmpty() && videoId.all { it.isLetterOrDigit() || it == '_' || it == '-' }) ids.add(videoId)
                                            }
                                        } else {
                                            val videoId = line.split(",").firstOrNull()?.trim() ?: ""
                                            if (videoId.isNotEmpty() && videoId.all { it.isLetterOrDigit() || it == '_' || it == '-' }) ids.add(videoId)
                                        }
                                    }
                                    rawLine = reader.readLine()
                                }
                                if (ids.isNotEmpty()) videoCsvData[filename] = ids
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return@withContext Result.failure(Exception("Could not open file"))

            if (subRows.isNotEmpty()) {
                onProgress?.invoke("Subscriptions", 0, subRows.size)
                val semaphore = Semaphore(5)
                val completed = AtomicInteger(0)
                supervisorScope {
                    subRows.map { sub ->
                        async(Dispatchers.IO) {
                            semaphore.withPermit {
                                val avatar = try { fetchChannelAvatar(sub.channelId) } catch (e: Exception) { "" }
                                subscriptionRepo.subscribe(ChannelSubscription(
                                    channelId = sub.channelId, channelName = sub.channelName,
                                    channelThumbnail = avatar, subscribedAt = System.currentTimeMillis()
                                ))
                                subscriptionsImported++
                                onProgress?.invoke("Subscriptions", completed.incrementAndGet(), subRows.size)
                            }
                        }
                    }.awaitAll()
                }
                val channelNames = subRows.map { it.channelName }.filter { it.isNotEmpty() }
                if (channelNames.isNotEmpty()) {
                    try { FlowNeuroEngine.bootstrapFromSubscriptions(context, channelNames) } catch (_: Exception) {}
                }
            }

            videoCsvData.forEach { (filename, videoIds) ->
                val derivedName = filename.removeSuffix(".csv")
                    .let { if (it.endsWith("-videos", ignoreCase = true)) it.dropLast(7) else it }
                    .trim().ifEmpty { "Imported Playlist" }

                val playlistName = playlistMeta.values.firstOrNull {
                    it.equals(derivedName, ignoreCase = true)
                } ?: derivedName

                val isWatchLater = playlistName.equals("watch later", ignoreCase = true)
                val playlistId = if (isWatchLater) PlaylistRepository.WATCH_LATER_ID
                                 else "yt_takeout_${playlistName.take(40)}_${System.currentTimeMillis()}"
                val firstThumb = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoIds.first())

                database.withTransaction {
                    val existing = database.playlistDao().getPlaylist(playlistId)
                    if (existing == null) {
                        database.playlistDao().insertPlaylist(PlaylistEntity(
                            id = playlistId, name = if (isWatchLater) "Watch Later" else playlistName,
                            description = "Imported from Google Takeout",
                            thumbnailUrl = firstThumb,
                            isPrivate = isWatchLater, createdAt = System.currentTimeMillis(),
                            isMusic = false, isUserCreated = true
                        ))
                    }
                    videoIds.forEachIndexed { index, videoId ->
                        database.videoDao().insertVideoOrIgnore(VideoEntity(
                            id = videoId, title = "", channelName = "", channelId = "",
                            thumbnailUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId),
                            duration = 0, viewCount = 0L, uploadDate = "", description = "",
                            channelThumbnailUrl = "", isMusic = false
                        ))
                        database.playlistDao().insertPlaylistVideoCrossRef(PlaylistVideoCrossRef(
                            playlistId = playlistId, videoId = videoId, position = index.toLong()
                        ))
                    }
                }
                playlistsImported++
                playlistVideosImported += videoIds.size
            }

            if (subscriptionsImported == 0 && historyImported == 0 && playlistsImported == 0) {
                return@withContext Result.failure(Exception("no_content"))
            }

            if (historyImported > 0) {
                try {
                    bootstrapNeuroFromImportedHistory(neuroBootstrapCandidates.values)
                } catch (_: Exception) {
                }
            }

            val parts = buildList {
                if (subscriptionsImported > 0) add("$subscriptionsImported subscriptions")
                if (historyImported > 0) add("$historyImported history entries")
                if (playlistsImported > 0) add("$playlistsImported playlists ($playlistVideosImported videos)")
            }
            Result.success(parts.joinToString(", "))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── NewPipe playlist import (ZIP containing SQLite DB) ──

    suspend fun importNewPipePlaylists(
        uri: Uri,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val tempDb = java.io.File(context.cacheDir, "newpipe_playlists_${System.currentTimeMillis()}.db")
        try {
            if (!copyNewPipeDatabaseToTempFile(uri, tempDb)) {
                return@withContext Result.failure(Exception("invalid_format"))
            }

            val db = android.database.sqlite.SQLiteDatabase.openDatabase(
                tempDb.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY
            )

            val streamUrlMap = mutableMapOf<Long, String>()
            db.rawQuery("SELECT uid, url FROM streams", null).use { cursor ->
                while (cursor.moveToNext()) {
                    val uid = cursor.getLong(0)
                    val url = cursor.getString(1)
                    streamUrlMap[uid] = url
                }
            }

            data class NpPlaylist(val uid: Long, val name: String)
            val playlists = mutableListOf<NpPlaylist>()
            db.rawQuery("SELECT uid, name FROM playlists", null).use { cursor ->
                while (cursor.moveToNext()) {
                    playlists.add(NpPlaylist(cursor.getLong(0), cursor.getString(1)))
                }
            }

            val playlistStreams = mutableMapOf<Long, MutableList<Long>>()
            db.rawQuery(
                "SELECT playlist_id, stream_id FROM playlist_stream_join ORDER BY join_index ASC",
                null
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val plId = cursor.getLong(0)
                    val streamId = cursor.getLong(1)
                    playlistStreams.getOrPut(plId) { mutableListOf() }.add(streamId)
                }
            }
            db.close()

            var importedCount = 0
            val total = playlists.size

            playlists.forEachIndexed { idx, playlist ->
                onProgress?.invoke(idx, total)

                val videoIds = (playlistStreams[playlist.uid] ?: emptyList()).mapNotNull { streamId ->
                    extractYouTubeVideoId(streamUrlMap[streamId] ?: return@mapNotNull null)
                }

                if (videoIds.isEmpty()) return@forEachIndexed

                val playlistId = "newpipe_pl_${playlist.uid}_${System.currentTimeMillis()}"
                val firstThumb = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoIds.first())

                database.withTransaction {
                    database.playlistDao().insertPlaylist(
                        PlaylistEntity(
                            id           = playlistId,
                            name         = playlist.name,
                            description  = "Imported from NewPipe",
                            thumbnailUrl = firstThumb,
                            isPrivate    = false,
                            createdAt    = System.currentTimeMillis(),
                            isMusic      = false,
                            isUserCreated = true
                        )
                    )
                    videoIds.forEachIndexed { index, videoId ->
                        database.videoDao().insertVideoOrIgnore(
                            VideoEntity(
                                id                  = videoId,
                                title               = "",
                                channelName         = "",
                                channelId           = "",
                                thumbnailUrl        = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId),
                                duration            = 0,
                                viewCount           = 0L,
                                uploadDate          = "",
                                description         = "",
                                channelThumbnailUrl = "",
                                isMusic             = false
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
                importedCount += videoIds.size
            }

            onProgress?.invoke(total, total)
            if (importedCount == 0) return@withContext Result.failure(Exception("no_content"))
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            tempDb.delete()
        }
    }

    // ── LibreTube playlist import (JSON backup) ──

    suspend fun importLibreTubePlaylists(
        uri: Uri,
        onProgress: ((current: Int, total: Int) -> Unit)? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use {
                it.bufferedReader().readText()
            } ?: return@withContext Result.failure(Exception("Could not read file"))

            val root = org.json.JSONObject(jsonString)
            var importedCount = 0

            if (root.has("localPlaylists")) {
                val localPlaylists = root.getJSONArray("localPlaylists")
                val total = localPlaylists.length()
                for (i in 0 until total) {
                    onProgress?.invoke(i, total)
                    val entry = localPlaylists.getJSONObject(i)
                    val playlistObj = entry.optJSONObject("playlist") ?: continue
                    val name = playlistObj.optString("name", "Imported Playlist")
                    val videosArray = entry.optJSONArray("videos") ?: continue

                    if (videosArray.length() == 0) continue

                    val videoIds = mutableListOf<String>()
                    val titleMap = mutableMapOf<String, String>()
                    val thumbMap = mutableMapOf<String, String>()
                    val durationMap = mutableMapOf<String, Int>()

                    for (j in 0 until videosArray.length()) {
                        val v = videosArray.getJSONObject(j)
                        val videoId = v.optString("videoId")
                        if (videoId.isNotBlank()) {
                            videoIds.add(videoId)
                            v.optString("title").takeIf { it.isNotBlank() }?.let { titleMap[videoId] = it }
                            v.optString("thumbnailUrl").takeIf { it.isNotBlank() }?.let { thumbMap[videoId] = it }
                            val dur = v.optInt("duration", 0)
                            if (dur > 0) durationMap[videoId] = dur
                        }
                    }

                    if (videoIds.isEmpty()) continue

                    val playlistId = "libretube_local_${i}_${System.currentTimeMillis()}"
                    val firstThumb = thumbMap[videoIds.first()]
                        ?: ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoIds.first())

                    database.withTransaction {
                        database.playlistDao().insertPlaylist(
                            PlaylistEntity(
                                id           = playlistId,
                                name         = name,
                                description  = "Imported from LibreTube",
                                thumbnailUrl = firstThumb,
                                isPrivate    = false,
                                createdAt    = System.currentTimeMillis(),
                                isMusic      = false,
                                isUserCreated = true
                            )
                        )
                        videoIds.forEachIndexed { index, videoId ->
                            database.videoDao().insertVideoOrIgnore(
                                VideoEntity(
                                    id                  = videoId,
                                    title               = titleMap[videoId] ?: "",
                                    channelName         = "",
                                    channelId           = "",
                                    thumbnailUrl        = thumbMap[videoId]
                                        ?: ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId),
                                    duration            = durationMap[videoId] ?: 0,
                                    viewCount           = 0L,
                                    uploadDate          = "",
                                    description         = "",
                                    channelThumbnailUrl = "",
                                    isMusic             = false
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
                    importedCount += videoIds.size
                }
            }

            // ── playlists (Piped format) ──
            if (root.has("playlists")) {
                val pipedPlaylists = root.getJSONArray("playlists")
                for (i in 0 until pipedPlaylists.length()) {
                    val entry = pipedPlaylists.getJSONObject(i)
                    val name = entry.optString("name", "Imported Playlist")
                    val videosArray = entry.optJSONArray("videos") ?: continue

                    val videoIds = mutableListOf<String>()
                    for (j in 0 until videosArray.length()) {
                        val url = videosArray.getString(j)
                        val videoId = extractYouTubeVideoId(url)
                        if (videoId != null) videoIds.add(videoId)
                    }

                    if (videoIds.isEmpty()) continue

                    val playlistId = "libretube_piped_${i}_${System.currentTimeMillis()}"
                    val firstThumb = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoIds.first())

                    database.withTransaction {
                        database.playlistDao().insertPlaylist(
                            PlaylistEntity(
                                id           = playlistId,
                                name         = name,
                                description  = "Imported from LibreTube",
                                thumbnailUrl = firstThumb,
                                isPrivate    = false,
                                createdAt    = System.currentTimeMillis(),
                                isMusic      = false,
                                isUserCreated = true
                            )
                        )
                        videoIds.forEachIndexed { index, videoId ->
                            database.videoDao().insertVideoOrIgnore(
                                VideoEntity(
                                    id                  = videoId,
                                    title               = "",
                                    channelName         = "",
                                    channelId           = "",
                                    thumbnailUrl        = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(videoId),
                                    duration            = 0,
                                    viewCount           = 0L,
                                    uploadDate          = "",
                                    description         = "",
                                    channelThumbnailUrl = "",
                                    isMusic             = false
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
                    importedCount += videoIds.size
                }
            }

            if (importedCount == 0) return@withContext Result.failure(Exception("no_content"))
            Result.success(importedCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper: extract YouTube video ID from various URL forms

    private fun extractYouTubeVideoId(url: String): String? {
        return when {
            url.contains("youtube.com/watch") -> {
                val queryStart = url.indexOf('?')
                if (queryStart == -1) null
                else url.substring(queryStart + 1)
                    .split('&')
                    .firstOrNull { it.startsWith("v=") }
                    ?.substring(2)
                    ?.takeIf { it.isNotBlank() }
            }
            url.contains("youtu.be/") -> {
                url.substringAfter("youtu.be/")
                    .substringBefore("?")
                    .substringBefore("/")
                    .takeIf { it.isNotBlank() }
            }
            else -> null
        }
    }

    private fun extractImportedChannelId(url: String?): String {
        val normalized = url.orEmpty()
        return when {
            normalized.contains("/channel/") -> normalized.substringAfter("/channel/")
            normalized.contains("/@") -> normalized.substringAfter("/@")
            normalized.contains("/user/") -> normalized.substringAfter("/user/")
            else -> ""
        }.substringBefore("/").substringBefore("?")
    }

    private fun copyNewPipeDatabaseToTempFile(uri: Uri, tempDb: java.io.File): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        val isZip = mimeType == "application/zip" || mimeType == "application/octet-stream" ||
            uri.lastPathSegment?.endsWith(".zip", ignoreCase = true) == true

        return if (isZip) {
            var foundDb = false
            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (entry.name == "newpipe.db" || entry.name.endsWith("/newpipe.db")) {
                            tempDb.outputStream().use { zip.copyTo(it) }
                            foundDb = true
                            break
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            }
            foundDb
        } else {
            context.contentResolver.openInputStream(uri)?.use { raw ->
                tempDb.outputStream().use { raw.copyTo(it) }
            } != null
        }
    }

    // ── Master Backup (app data + engine brain in one ZIP) ──

    suspend fun exportMasterBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val playerSettings = playerPreferences.getExportData()
            val localSettings = localDataManager.getExportData()
            val activeIconSuffix = detectActiveIconSuffix()
            val exportedStrings = if (activeIconSuffix != null)
                playerSettings.strings + mapOf("app_icon_suffix" to activeIconSuffix) + localSettings.strings
            else
                playerSettings.strings + localSettings.strings
            val mergedSettings = SettingsBackup(
                strings = exportedStrings,
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
                subscriptionGroups = database.subscriptionGroupDao().getAllGroupsOnce(),
                likedVideos = likedVideosRepo.getAllLikedVideos().first(),
                contentPreferences = getContentPreferencesBackup(),
                settings = mergedSettings
            )
            val appDataJson = gson.toJson(backupData)

            val brainBytes = exportBrainBytes()

            context.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("app_data.json"))
                    zip.write(appDataJson.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()

                    zip.putNextEntry(ZipEntry("engine_brain.json"))
                    zip.write(brainBytes)
                    zip.closeEntry()
                }
            } ?: return@withContext Result.failure(Exception("Could not open output stream"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importMasterBackup(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            var appDataJson: String? = null
            var brainBytes: ByteArray? = null
            var contentPreferences: ContentPreferencesBackup? = null

            context.contentResolver.openInputStream(uri)?.use { raw ->
                ZipInputStream(raw).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        when (entry.name) {
                            "app_data.json" -> appDataJson = zip.readBytes().toString(Charsets.UTF_8)
                            "engine_brain.json" -> brainBytes = zip.readBytes()
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: return@withContext Result.failure(Exception("Could not read file"))

            if (appDataJson == null && brainBytes == null) {
                return@withContext Result.failure(Exception("Invalid master backup file"))
            }

            appDataJson?.let { json ->
                val backupData = parseBackupJson(json)
                    ?: return@withContext Result.failure(Exception("Invalid app data in backup"))
                contentPreferences = backupData.contentPreferences
                importBackupData(backupData, restoreContentPreferences = false)
            }

            brainBytes?.let { bytes ->
                FlowNeuroEngine.importBrainFromStream(context, bytes.inputStream())
            }

            contentPreferences?.let { preferences ->
                FlowNeuroEngine.restoreContentPreferences(
                    context = context,
                    preferredTopics = preferences.preferredTopics,
                    blockedTopics = preferences.blockedTopics,
                    blockedChannels = preferences.blockedChannels
                )
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun importBackupData(
        backupData: BackupData,
        restoreContentPreferences: Boolean = true
    ) {
        backupData.viewHistory?.let { entries ->
            if (entries.isNotEmpty()) viewHistory.bulkSaveHistoryEntries(entries)
        }
        backupData.likedVideos?.forEach { info -> likedVideosRepo.likeVideo(info) }
        backupData.searchHistory?.forEach { item ->
            searchHistoryRepo.saveSearchQuery(item.query, item.type)
        }
        backupData.subscriptions?.let { subs ->
            subs.forEach { subscriptionRepo.subscribe(it) }
            val channelNames = subs.map { it.channelName }.filter { it.isNotEmpty() }
            if (channelNames.isNotEmpty()) {
                try { FlowNeuroEngine.bootstrapFromSubscriptions(context, channelNames) } catch (_: Exception) {}
            }
        }
        database.withTransaction {
            backupData.videos?.forEach { database.videoDao().insertVideoOrIgnore(it) }
            backupData.playlists?.forEach { database.playlistDao().insertPlaylist(it) }
            backupData.playlistVideos?.forEach { database.playlistDao().insertPlaylistVideoCrossRef(it) }
            backupData.subscriptionGroups?.let { groups ->
                if (groups.isNotEmpty()) {
                    database.subscriptionGroupDao().insertAll(groups)
                }
            }
        }
        if (restoreContentPreferences) {
            backupData.contentPreferences?.let { preferences ->
                FlowNeuroEngine.restoreContentPreferences(
                    context = context,
                    preferredTopics = preferences.preferredTopics,
                    blockedTopics = preferences.blockedTopics,
                    blockedChannels = preferences.blockedChannels
                )
            }
        }
        backupData.settings?.let { settings ->
            playerPreferences.restoreData(settings)
            localDataManager.restoreData(settings)
            val savedIconSuffix = settings.strings["app_icon_suffix"]
            if (!savedIconSuffix.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    val pm = context.packageManager
                    val pkg = context.packageName
                    val allSuffixes = listOf(
                        ".IconFlowRed",
                        ".IconFlowLight",
                        ".IconAmoled",
                        ".IconMonochrome",
                        ".IconGhost",
                        ".IconDynamic",
                        ".IconMaterialSky",
                        ".IconMaterialMint"
                    )
                    for (suffix in allSuffixes) {
                        val cn = ComponentName(pkg, "io.github.aedev.flow$suffix")
                        val want = if (suffix == savedIconSuffix)
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                        else
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                        pm.setComponentEnabledSetting(cn, want, PackageManager.DONT_KILL_APP)
                    }
                }
            }
        }
    }

    private fun toNewPipeChannelUrl(channelId: String): String? {
        val trimmed = channelId.trim()
        if (trimmed.isEmpty()) return null

        val ucId = Regex("UC[0-9A-Za-z_-]{22}").find(trimmed)?.value
        if (ucId != null) {
            return "https://www.youtube.com/channel/$ucId"
        }

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }

        if (trimmed.startsWith("@")) {
            return "https://www.youtube.com/$trimmed"
        }

        return "https://www.youtube.com/@$trimmed"
    }

    private suspend fun writeToFolder(
        folderUri: Uri,
        filename: String,
        mimeType: String,
        write: (OutputStream) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(folderUri)
            val treeUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, treeDocId)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, treeDocId)

            val existingDocId = context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                var found: String? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(nameCol) == filename) {
                        found = cursor.getString(idCol)
                        break
                    }
                }
                found
            }

            val targetUri = if (existingDocId != null) {
                DocumentsContract.buildDocumentUriUsingTree(folderUri, existingDocId)
            } else {
                DocumentsContract.createDocument(context.contentResolver, treeUri, mimeType, filename)
                    ?: return@withContext Result.failure(Exception("Failed to create document: $filename"))
            }

            context.contentResolver.openOutputStream(targetUri, "wt")?.use { outputStream ->
                write(outputStream)
            } ?: return@withContext Result.failure(Exception("Could not open output stream for $filename"))

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportDataToFolder(folderUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val playerSettings = playerPreferences.getExportData()
            val localSettings = localDataManager.getExportData()
            val activeIconSuffix = detectActiveIconSuffix()
            val exportedStrings = if (activeIconSuffix != null)
                playerSettings.strings + mapOf("app_icon_suffix" to activeIconSuffix) + localSettings.strings
            else
                playerSettings.strings + localSettings.strings
            val mergedSettings = SettingsBackup(
                strings = exportedStrings,
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
                subscriptionGroups = database.subscriptionGroupDao().getAllGroupsOnce(),
                likedVideos = likedVideosRepo.getAllLikedVideos().first(),
                contentPreferences = getContentPreferencesBackup(),
                settings = mergedSettings
            )
            val json = gson.toJson(backupData)
            writeToFolder(folderUri, "flow_backup.json", "application/json") { out ->
                out.write(json.toByteArray(Charsets.UTF_8))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportBrainToFolder(folderUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val brainBytes = exportBrainBytes()
            writeToFolder(folderUri, "flow_engine.json", "application/json") { out ->
                out.write(brainBytes)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun exportMasterToFolder(folderUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val playerSettings = playerPreferences.getExportData()
            val localSettings = localDataManager.getExportData()
            val activeIconSuffix = detectActiveIconSuffix()
            val exportedStrings = if (activeIconSuffix != null)
                playerSettings.strings + mapOf("app_icon_suffix" to activeIconSuffix) + localSettings.strings
            else
                playerSettings.strings + localSettings.strings
            val mergedSettings = SettingsBackup(
                strings = exportedStrings,
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
                contentPreferences = getContentPreferencesBackup(),
                settings = mergedSettings
            )
            val appDataJson = gson.toJson(backupData)
            val brainBytes = exportBrainBytes()

            writeToFolder(folderUri, "flow_master_backup.zip", "application/zip") { out ->
                ZipOutputStream(out).use { zip ->
                    zip.putNextEntry(ZipEntry("app_data.json"))
                    zip.write(appDataJson.toByteArray(Charsets.UTF_8))
                    zip.closeEntry()
                    zip.putNextEntry(ZipEntry("engine_brain.json"))
                    zip.write(brainBytes)
                    zip.closeEntry()
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
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
