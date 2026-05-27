package io.github.aedev.flow.data.video

import android.content.Context
import android.content.ContentUris
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.provider.MediaStore
import android.content.ContentValues
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.local.entity.DownloadEntity
import io.github.aedev.flow.data.local.entity.DownloadFileType
import io.github.aedev.flow.data.local.entity.DownloadItemEntity
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress update emitted during active downloads.
 */
data class DownloadProgressUpdate(
    val videoId: String,
    val itemId: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val status: DownloadItemStatus,
    val isMerging: Boolean = false
) {
    val progress: Float
        get() = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
}

/**
 * Legacy compat — wraps DownloadWithItems for backward compatibility with existing UI.
 */
data class DownloadedVideo(
    val video: Video,
    val filePath: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0,
    val downloadId: Long = -1,
    val quality: String = "Unknown",
    val isAudioOnly: Boolean = false
)

/**
 * Manages all video/audio download persistence and file operations.
 * Backed by Room database via DownloadDao.
 */
@Singleton
class VideoDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadDao: DownloadDao
) {
    companion object {
        private const val TAG = "VideoDownloadManager"
        const val VIDEO_DIR = "Flow"
        const val AUDIO_DIR = "Flow"

        /**
         * Legacy bridge — callers that still use getInstance() will get a crash
         * with a clear message telling them to switch to DI.
         */
        @Deprecated("Use Hilt injection instead", level = DeprecationLevel.ERROR)
        fun getInstance(context: Context): VideoDownloadManager {
            throw UnsupportedOperationException(
                "VideoDownloadManager is now Hilt-managed. Use @Inject instead of getInstance()."
            )
        }
    }

   
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** In-memory guard: paths whose DB entry was deleted but whose file deletion is in-flight. */
    private val recentlyDeletedPaths: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Persistent tombstone: file paths deleted from the DB by the user but whose physical
     * file could not be removed (e.g. Android 11+ scoped storage issue, file in use).
     * Prevents scanAndRecoverDownloads() from re-inserting them across app restarts.
     */
    private val tombstonePrefs by lazy {
        context.getSharedPreferences("flow_file_tombstones", Context.MODE_PRIVATE)
    }

    // Progress updates emitted by FlowDownloadService
    private val _progressUpdates = MutableSharedFlow<DownloadProgressUpdate>(extraBufferCapacity = 64)
    val progressUpdates: SharedFlow<DownloadProgressUpdate> = _progressUpdates.asSharedFlow()

    fun emitProgress(update: DownloadProgressUpdate) {
        _progressUpdates.tryEmit(update)
    }

    // ===== Directory Management =====

    /** Custom download location set by user (null = use defaults) */
    @Volatile
    var customDownloadPath: String? = null

    /** Check if the app has All Files Access (MANAGE_EXTERNAL_STORAGE) on Android 11+ */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            // Pre-Android 11: WRITE_EXTERNAL_STORAGE would be needed but
            // we just use app-private dirs which need no permissions
            false
        }
    }

    /**
     * Request All Files Access permission (MANAGE_EXTERNAL_STORAGE) on Android 11+.
     * This opens the system settings page where the user can grant the permission.
     * Call this before starting downloads to ensure files go to public storage.
     */
    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to open MANAGE_ALL_FILES_ACCESS settings, trying fallback", e)
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e2: Exception) {
                    Log.e(TAG, "Could not open file access settings", e2)
                }
            }
        }
    }

    /**
     * Tell the Android system to scan the newly downloaded file.
     * This adds the file into the system's MediaStore index so it's instantly 
     * visible to external gallery and media player apps, without needing a duplicate copy.
     */
    fun scanFile(filePath: String, mimeType: String = "video/mp4") {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "scanFile: File does not exist: $filePath")
                return
            }
            
            android.media.MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                arrayOf(mimeType)
            ) { path, uri ->
                Log.d(TAG, "scanFile: Scanned $path: -> uri=$uri")
            }
        } catch (e: Exception) {
            Log.e(TAG, "scanFile failed", e)
        }
    }

    /**
     * Get the video download directory.
*/
    fun getVideoDownloadDir(): File {
        customDownloadPath?.let { custom ->
            val dir = File(custom)
            if (!dir.exists()) dir.mkdirs()
            if (dir.exists() && dir.canWrite()) return dir
            Log.w(TAG, "Custom download path not writable: $custom, falling back to defaults")
        }
        // Downloads folder is always writable without extra permissions on all API levels
        try {
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                VIDEO_DIR
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            if (downloadsDir.canWrite()) return downloadsDir
        } catch (e: Exception) {
            Log.w(TAG, "Could not use Downloads dir", e)
        }
        // Use public Movies dir if MANAGE_EXTERNAL_STORAGE is granted (Android 11+)
        if (hasAllFilesAccess()) {
            try {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    VIDEO_DIR
                )
                if (!dir.exists()) dir.mkdirs()
                if (dir.canWrite()) return dir
            } catch (e: Exception) {
                Log.w(TAG, "Could not use Movies dir with MANAGE_EXTERNAL_STORAGE", e)
            }
        }
        // Final fallback: app-private external storage (no permissions needed)
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), VIDEO_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Get the audio download directory.
     * Priority: custom path > public Music/Flow > public Downloads/Flow
     */
    fun getAudioDownloadDir(): File {
        customDownloadPath?.let { custom ->
            val dir = File(custom)
            if (!dir.exists()) dir.mkdirs()
            if (dir.exists() && dir.canWrite()) return dir
            Log.w(TAG, "Custom audio download path not writable: $custom, falling back to defaults")
        }
        try {
            val musicDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                AUDIO_DIR
            )
            if (!musicDir.exists()) musicDir.mkdirs()
            if (musicDir.canWrite()) return musicDir
        } catch (e: Exception) {
            Log.w(TAG, "Could not use Music dir for audio", e)
        }
        try {
            val downloadsDir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                AUDIO_DIR
            )
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            if (downloadsDir.canWrite()) return downloadsDir
        } catch (e: Exception) {
            Log.w(TAG, "Could not use Downloads dir for audio", e)
        }
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), AUDIO_DIR)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Fallback to internal app storage if external isn't available */
    fun getInternalDownloadDir(): File {
        val dir = File(context.filesDir, "downloads")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /** Get appropriate download dir based on file type and storage availability */
    fun getDownloadDir(fileType: DownloadFileType): File {
        return try {
            val externalDir = if (fileType == DownloadFileType.AUDIO) getAudioDownloadDir() else getVideoDownloadDir()
            if (externalDir.canWrite()) externalDir else getInternalDownloadDir()
        } catch (e: Exception) {
            Log.w(TAG, "External storage not available, using internal", e)
            getInternalDownloadDir()
        }
    }

    /** Get the display name for the current download location */
    fun getDownloadLocationDisplayName(): String {
        customDownloadPath?.let { custom ->
            val file = File(custom)
            if (file.exists()) return file.absolutePath
        }
        return try {
            if (hasAllFilesAccess()) {
                val moviesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                File(moviesDir, VIDEO_DIR).absolutePath
            } else {
                File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), VIDEO_DIR).absolutePath
            }
        } catch (e: Exception) {
            "Internal App Storage"
        }
    }

    // ===== Database Operations =====

    /** All downloads with their items */
    val allDownloads: Flow<List<DownloadWithItems>> = downloadDao.getAllDownloadsWithItems()

    /** Only video downloads */
    val videoDownloads: Flow<List<DownloadWithItems>> = downloadDao.getVideoDownloads()

    /** Only audio-only downloads */
    val audioOnlyDownloads: Flow<List<DownloadWithItems>> = downloadDao.getAudioOnlyDownloads()

    /** Active (in-progress/pending/paused) downloads */
    val activeDownloads: Flow<List<DownloadWithItems>> = downloadDao.getActiveDownloads()

    /**
     * Legacy compatibility — exposes only COMPLETED downloads as DownloadedVideo list.
     * Used by DownloadsScreen and VideoPlayerViewModel for offline playback.
     * Only includes downloads with at least one COMPLETED item and an existing file.
     */
    val downloadedVideos: Flow<List<DownloadedVideo>>
        get() = allDownloads.map { list ->
            list.filter { dwi ->
                dwi.overallStatus == DownloadItemStatus.COMPLETED && !dwi.isAudioOnly
            }.map { toDownloadedVideo(it) }
        }

    /** Save a new download with its items */
    suspend fun saveDownload(
        video: Video,
        items: List<DownloadItemEntity>
    ) {
        downloadDao.insertDownload(
            DownloadEntity(
                videoId = video.id,
                title = video.title,
                uploader = video.channelName,
                duration = video.duration.toLong(),
                thumbnailUrl = video.thumbnailUrl,
                createdAt = System.currentTimeMillis()
            )
        )
        downloadDao.insertItems(items)
    }

    /** Save download with a single muxed file (simplified for completed downloads) */
    suspend fun saveCompletedDownload(
        video: Video,
        filePath: String,
        quality: String,
        fileSize: Long,
        fileType: DownloadFileType = DownloadFileType.VIDEO
    ) {
        val fileName = File(filePath).name
        downloadDao.insertDownload(
            DownloadEntity(
                videoId = video.id,
                title = video.title,
                uploader = video.channelName,
                duration = video.duration.toLong(),
                thumbnailUrl = video.thumbnailUrl,
                createdAt = System.currentTimeMillis()
            )
        )
        downloadDao.insertItem(
            DownloadItemEntity(
                videoId = video.id,
                fileType = fileType,
                fileName = fileName,
                filePath = filePath,
                format = if (fileType == DownloadFileType.VIDEO) "mp4" else "m4a",
                quality = quality,
                downloadedBytes = fileSize,
                totalBytes = fileSize,
                status = DownloadItemStatus.COMPLETED
            )
        )
    }

    /** Legacy compat — wraps saveCompletedDownload for callers using DownloadedVideo */
    suspend fun saveDownloadedVideo(downloadedVideo: DownloadedVideo) {
        saveCompletedDownload(
            video = downloadedVideo.video,
            filePath = downloadedVideo.filePath,
            quality = downloadedVideo.quality,
            fileSize = downloadedVideo.fileSize,
            fileType = DownloadFileType.VIDEO
        )
    }

    /** Insert a download item and return its generated ID */
    suspend fun insertItem(item: DownloadItemEntity): Int {
        return downloadDao.insertItem(item).toInt()
    }

    /** Update download progress */
    suspend fun updateProgress(itemId: Int, downloadedBytes: Long, status: DownloadItemStatus) {
        downloadDao.updateProgress(itemId, downloadedBytes, status)
    }

    /** Update download item with full info including totalBytes */
    suspend fun updateItemFull(itemId: Int, downloadedBytes: Long, totalBytes: Long, status: DownloadItemStatus) {
        downloadDao.updateItemFull(itemId, downloadedBytes, totalBytes, status)
    }

    /** Update item status */
    suspend fun updateStatus(itemId: Int, status: DownloadItemStatus) {
        downloadDao.updateStatus(itemId, status)
    }

    /** Update all items for a video */
    suspend fun updateAllItemsStatus(videoId: String, status: DownloadItemStatus) {
        downloadDao.updateAllItemsStatus(videoId, status)
    }

    /** Check if a video is downloaded */
    suspend fun isDownloaded(videoId: String): Boolean {
        return downloadDao.isDownloaded(videoId)
    }

    /** Persist SponsorBlock segments JSON for a downloaded video. */
    suspend fun saveSponsorBlockData(videoId: String, json: String) {
        downloadDao.updateSponsorBlockData(videoId, json)
    }

    /** Retrieve the stored SponsorBlock segments JSON, or null if not available. */
    suspend fun getSponsorBlockData(videoId: String): String? {
        return downloadDao.getSponsorBlockData(videoId)
    }

    /** Get download with items */
    suspend fun getDownloadWithItems(videoId: String): DownloadWithItems? {
        return downloadDao.getDownloadWithItems(videoId)
    }

    /** Delete download and its files from disk.
     *
     * Based on NewPipe's deletion order:
     *  1. Collect file paths                    (before DB removal)
     *  2. Guard paths against scanner re-insert (ConcurrentHashMap set)
     *  3. Delete DB entry                       (UI disappears instantly via Room Flow)
     *  4. Delete files in app-scoped ioScope    (immune to ViewModel back-press cancellation)
     *  5. Notify MediaScanner each file is gone (removes stale index on Android 10+)
     */
    suspend fun deleteDownload(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val download = downloadDao.getDownloadWithItems(videoId)
                ?: return@withContext false

            val filePaths = download.items.flatMap { artifactPathsFor(it.filePath) }.distinct()
            val thumbPath = download.download.thumbnailPath

            recentlyDeletedPaths.addAll(filePaths)

            downloadDao.deleteDownload(videoId)

            ioScope.launch {
                filePaths.forEach { path ->
                    val fileGone = deleteFileFromDisk(path)
                    if (fileGone) {
                        recentlyDeletedPaths.remove(path)
                        tombstonePrefs.edit().remove(path).apply()
                        MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                        Log.d(TAG, "Deleted: $path")
                    } else {
                        tombstonePrefs.edit().putBoolean(path, true).apply()
                        Log.w(TAG, "File not deleted (kept in guard + tombstoned): $path")
                    }
                }
                thumbPath?.let { tp ->
                    try { File(tp).takeIf { it.exists() }?.delete() } catch (_: Exception) {}
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete download: $videoId", e)
            false
        }
    }

    /**
     * Delete every non-completed download row and its partial artifacts.
     * Completed downloads are never touched.
     */
    suspend fun deleteIncompleteDownloads(): Int = withContext(Dispatchers.IO) {
        val incomplete = downloadDao.getAllDownloadsWithItemsOnce()
            .filter { download ->
                download.items.isEmpty() ||
                    download.items.any { it.status != DownloadItemStatus.COMPLETED }
            }

        var deleted = 0
        incomplete.forEach { download ->
            if (deleteDownload(download.download.videoId)) deleted++
        }
        deleted
    }

    private fun artifactPathsFor(path: String): List<String> =
        listOf(path, "$path.video.tmp", "$path.audio.tmp")

    /**
     * Delete a single file from disk.
     * Returns true if the file is confirmed gone (never existed, successfully deleted,
     * or removed via MediaStore fallback on Android Q+).
     */
    private fun deleteFileFromDisk(path: String): Boolean {
        val file = File(path)
        if (!file.exists()) return true          
        if (file.delete()) return true           

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                context.contentResolver.query(
                    contentUri,
                    arrayOf(MediaStore.MediaColumns._ID),
                    "${MediaStore.MediaColumns.DATA} = ?",
                    arrayOf(path),
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(0)
                        val fileUri = ContentUris.withAppendedId(contentUri, id)
                        if (context.contentResolver.delete(fileUri, null, null) > 0) {
                            return !file.exists()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore delete fallback failed for: $path", e)
            }
        }

        return !file.exists()  
    }

    /** Legacy compat — same as deleteDownload */
    suspend fun removeDownloadedVideo(videoId: String) {
        deleteDownload(videoId)
    }

    /** Get total storage used by downloads */
    suspend fun getTotalDownloadSize(): Long {
        return downloadDao.getTotalDownloadSize()
    }

    /** Legacy compatibility: convert DownloadWithItems to DownloadedVideo for existing UI */
    fun toDownloadedVideo(dwi: DownloadWithItems): DownloadedVideo {
        return DownloadedVideo(
            video = Video(
                id = dwi.download.videoId,
                title = dwi.download.title,
                channelName = dwi.download.uploader,
                channelId = "local",
                thumbnailUrl = dwi.download.thumbnailUrl,
                duration = dwi.download.duration.toInt(),
                viewCount = 0,
                uploadDate = dwi.download.createdAt.toString(),
                description = "Downloaded locally"
            ),
            filePath = dwi.primaryFilePath ?: "",
            downloadedAt = dwi.download.createdAt,
            fileSize = dwi.totalSize,
            downloadId = dwi.download.createdAt,
            quality = dwi.items.firstOrNull()?.quality ?: "Unknown",
            isAudioOnly = dwi.isAudioOnly
        )
    }

    /** Generate a safe filename from title and quality.
     *
     * Supports international characters (Arabic, Chinese, Japanese, etc.) by using
     * Unicode-aware character classes instead of ASCII-only ranges.
     * Only characters that are illegal in filenames on Android/FAT filesystems
     * are replaced with underscores.
     */
    fun generateFileName(title: String, quality: String, extension: String = "mp4"): String {
        val safeTitle = title
            .replace(Regex("[^\\p{L}\\p{M}\\p{N}\\s._-]"), "_")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("_+"), "_")
            .trim('_', ' ')
            .take(100)
            .ifEmpty { "video" }
        return "${safeTitle}_${quality}.$extension"
    }

    /**
     * Scans all known download directories for video/audio files that are not tracked in the
     * database (e.g. after a database wipe) and re-inserts them as completed downloads so they
     * appear in the Downloads screen and can be played back locally.
     *
     * Safe to call repeatedly — files already recorded in the DB are skipped.
     */
    suspend fun scanAndRecoverDownloads() = withContext(Dispatchers.IO) {
        try {
            val dirsToScan = buildList {
                customDownloadPath?.let { add(File(it)) }
                add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), VIDEO_DIR))
                add(File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), VIDEO_DIR))
                add(File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), VIDEO_DIR))
                add(File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), VIDEO_DIR))
            }

            val videoExtensions = setOf("mp4", "webm", "mkv", "avi", "mov")
            val audioExtensions = setOf("m4a", "mp3", "aac", "opus", "ogg")

            for (dir in dirsToScan.distinctBy { it.canonicalPath }) {
                if (!dir.exists() || !dir.isDirectory) continue
                val files = dir.listFiles() ?: continue

                for (file in files) {
                    if (!file.isFile) continue
                    val ext = file.extension.lowercase()
                    val isVideo = ext in videoExtensions
                    val isAudio = ext in audioExtensions
                    if (!isVideo && !isAudio) continue

                    val filePath = file.absolutePath
                    if (downloadDao.existsByFilePath(filePath)) continue
                    if (recentlyDeletedPaths.contains(filePath)) continue

                    if (tombstonePrefs.contains(filePath)) {
                        if (deleteFileFromDisk(filePath)) {
                            tombstonePrefs.edit().remove(filePath).apply()
                        }
                        continue
                    }

                    val pseudoId = "recovered_${filePath.hashCode().toLong() and 0xFFFFFFFFL}"

                    val mmr = android.media.MediaMetadataRetriever()
                    var title = file.nameWithoutExtension
                    var artist = "Local File"
                    var durationMs = 0L
                    try {
                        mmr.setDataSource(filePath)
                        mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_TITLE)
                            ?.takeIf { it.isNotBlank() }?.let { title = it }
                        mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_ARTIST)
                            ?.takeIf { it.isNotBlank() }?.let { artist = it }
                        mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                            ?.toLongOrNull()?.let { durationMs = it }
                    } catch (_: Exception) {
                    } finally {
                        try { mmr.release() } catch (_: Exception) {}
                    }

                    val fileType = if (isVideo) DownloadFileType.VIDEO else DownloadFileType.AUDIO

                    val thumbnailUrl: String = if (isVideo) {
                        try {
                            val mmr2 = android.media.MediaMetadataRetriever()
                            mmr2.setDataSource(filePath)
                            val bmp = mmr2.getFrameAtTime(
                                1_000_000L, 
                                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                            ) ?: mmr2.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                            mmr2.release()
                            if (bmp != null) {
                                val thumbFile = java.io.File(context.cacheDir, "thumb_$pseudoId.jpg")
                                thumbFile.outputStream().use {
                                    bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
                                }
                                bmp.recycle()
                                if (thumbFile.exists() && thumbFile.length() > 0) "file://${thumbFile.absolutePath}" else ""
                            } else ""
                        } catch (_: Exception) { "" }
                    } else ""

                    downloadDao.insertDownload(
                        DownloadEntity(
                            videoId = pseudoId,
                            title = title,
                            uploader = artist,
                            duration = durationMs / 1000,
                            thumbnailUrl = thumbnailUrl,
                            createdAt = file.lastModified()
                        )
                    )
                    downloadDao.insertItem(
                        DownloadItemEntity(
                            videoId = pseudoId,
                            fileType = fileType,
                            fileName = file.name,
                            filePath = filePath,
                            format = ext,
                            quality = "Local",
                            mimeType = if (isVideo) "video/mp4" else "audio/mp4",
                            downloadedBytes = file.length(),
                            totalBytes = file.length(),
                            status = DownloadItemStatus.COMPLETED
                        )
                    )

                    Log.i(TAG, "scanAndRecoverDownloads: recovered '${file.name}' as $pseudoId")
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                scanViaMediaStore(videoExtensions, audioExtensions)
            } else Unit
        } catch (e: Exception) {
            Log.e(TAG, "scanAndRecoverDownloads failed", e)
        }
    }

    /**
     * MediaStore-based recovery scan.  Queries the system media index for video and audio files
     * stored in any folder named [VIDEO_DIR] ("Flow").  Works with only READ_MEDIA_VIDEO /
     * READ_MEDIA_AUDIO — no MANAGE_EXTERNAL_STORAGE needed.  Safe to call repeatedly.
     * Scans: Downloads/Flow, Movies/Flow, app-private external dirs, and the user's custom path.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun scanViaMediaStore(
        videoExtensions: Set<String>,
        audioExtensions: Set<String>
    ) = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION
        )

        val pathPrefixes = buildList {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                ?.let { File(it, VIDEO_DIR).canonicalPath }?.let { add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                ?.let { File(it, VIDEO_DIR).canonicalPath }?.let { add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?.canonicalPath?.let { add(it) }
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?.canonicalPath?.let { add(it) }
            customDownloadPath?.let { add(File(it).canonicalPath) }
        }

        if (pathPrefixes.isEmpty()) return@withContext

        val collections = listOf(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        )

        for (collectionUri in collections) {
            try {
                context.contentResolver.query(
                    collectionUri,
                    projection,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                    val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                    val durIdx  = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)

                    while (cursor.moveToNext()) {
                        val filePath = cursor.getString(dataIdx) ?: continue
                        val fileName = cursor.getString(nameIdx) ?: continue
                        val fileSize = cursor.getLong(sizeIdx)
                        val durationMs = cursor.getLong(durIdx)
                        if (pathPrefixes.none { prefix -> filePath.startsWith(prefix) }) continue

                        val ext = fileName.substringAfterLast('.', "").lowercase()
                        val isVideo = ext in videoExtensions
                        val isAudio = ext in audioExtensions
                        if (!isVideo && !isAudio) continue

                        if (downloadDao.existsByFilePath(filePath)) continue
                        if (recentlyDeletedPaths.contains(filePath)) continue
                        if (tombstonePrefs.contains(filePath)) {
                            if (deleteFileFromDisk(filePath)) tombstonePrefs.edit().remove(filePath).apply()
                            continue
                        }

                        val file = File(filePath)
                        val pseudoId = "recovered_${filePath.hashCode().toLong() and 0xFFFFFFFFL}"

                        val thumbnailUrl: String = if (isVideo) {
                            try {
                                val mmr = android.media.MediaMetadataRetriever()
                                mmr.setDataSource(filePath)
                                val bmp = mmr.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                    ?: mmr.getFrameAtTime(0L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                                mmr.release()
                                if (bmp != null) {
                                    val thumbFile = File(context.cacheDir, "thumb_$pseudoId.jpg")
                                    thumbFile.outputStream().use {
                                        bmp.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, it)
                                    }
                                    bmp.recycle()
                                    if (thumbFile.exists() && thumbFile.length() > 0) "file://${thumbFile.absolutePath}" else ""
                                } else ""
                            } catch (_: Exception) { "" }
                        } else ""

                        val fileType = if (isVideo) DownloadFileType.VIDEO else DownloadFileType.AUDIO

                        downloadDao.insertDownload(
                            DownloadEntity(
                                videoId = pseudoId,
                                title = fileName.substringBeforeLast('.'),
                                uploader = "Local File",
                                duration = durationMs / 1000,
                                thumbnailUrl = thumbnailUrl,
                                createdAt = if (file.exists()) file.lastModified() else System.currentTimeMillis()
                            )
                        )
                        downloadDao.insertItem(
                            DownloadItemEntity(
                                videoId = pseudoId,
                                fileType = fileType,
                                fileName = fileName,
                                filePath = filePath,
                                format = ext,
                                quality = "Local",
                                mimeType = if (isVideo) "video/mp4" else "audio/mp4",
                                downloadedBytes = fileSize,
                                totalBytes = fileSize,
                                status = DownloadItemStatus.COMPLETED
                            )
                        )
                        Log.i(TAG, "scanViaMediaStore: recovered '$fileName' as $pseudoId")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "scanViaMediaStore: query failed for $collectionUri", e)
            }
        }
    }
}
