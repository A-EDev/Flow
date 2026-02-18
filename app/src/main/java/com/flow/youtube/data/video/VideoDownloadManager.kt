package com.flow.youtube.data.video

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.provider.MediaStore
import android.content.ContentValues
import com.flow.youtube.data.local.dao.DownloadDao
import com.flow.youtube.data.local.entity.DownloadEntity
import com.flow.youtube.data.local.entity.DownloadFileType
import com.flow.youtube.data.local.entity.DownloadItemEntity
import com.flow.youtube.data.local.entity.DownloadItemStatus
import com.flow.youtube.data.local.entity.DownloadWithItems
import com.flow.youtube.data.model.Video
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
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
    val status: DownloadItemStatus
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
    val quality: String = "Unknown"
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
     * Copy a downloaded file to public storage via MediaStore (Android 10+).
     * This allows the file to be visible in the system file manager and
     * accessible to external players without MANAGE_EXTERNAL_STORAGE.
     * Returns the content URI of the inserted file, or null on failure.
     */
    fun copyToPublicStorage(filePath: String, title: String, mimeType: String = "video/mp4"): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        
        try {
            val file = File(filePath)
            if (!file.exists()) {
                Log.e(TAG, "copyToPublicStorage: File does not exist: $filePath")
                return null
            }
            
            val isAudio = mimeType.startsWith("audio/")
            val collection = if (isAudio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, 
                    if (isAudio) "${Environment.DIRECTORY_MUSIC}/$AUDIO_DIR" 
                    else "${Environment.DIRECTORY_MOVIES}/$VIDEO_DIR")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(collection, values) ?: return null
            
            resolver.openOutputStream(uri)?.use { outputStream ->
                file.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            
            Log.d(TAG, "copyToPublicStorage: Copied $filePath to MediaStore: $uri")
            return uri
        } catch (e: Exception) {
            Log.e(TAG, "copyToPublicStorage failed", e)
            return null
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
     * Priority: custom path > public Downloads/Flow > public Music/Flow (if permission granted)
     */
    fun getAudioDownloadDir(): File {
        customDownloadPath?.let { custom ->
            val dir = File(custom)
            if (!dir.exists()) dir.mkdirs()
            if (dir.exists() && dir.canWrite()) return dir
            Log.w(TAG, "Custom audio download path not writable: $custom, falling back to defaults")
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
        if (hasAllFilesAccess()) {
            try {
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    AUDIO_DIR
                )
                if (!dir.exists()) dir.mkdirs()
                if (dir.canWrite()) return dir
            } catch (e: Exception) {
                Log.w(TAG, "Could not use Music dir with MANAGE_EXTERNAL_STORAGE", e)
            }
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
                dwi.overallStatus == DownloadItemStatus.COMPLETED
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

    /** Get download with items */
    suspend fun getDownloadWithItems(videoId: String): DownloadWithItems? {
        return downloadDao.getDownloadWithItems(videoId)
    }

    /** Delete download and its files from disk */
    suspend fun deleteDownload(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val download = downloadDao.getDownloadWithItems(videoId)
            if (download != null) {
                // Delete physical files
                for (item in download.items) {
                    try {
                        val file = File(item.filePath)
                        if (file.exists()) {
                            file.delete()
                            Log.d(TAG, "Deleted file: ${item.filePath}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete file: ${item.filePath}", e)
                    }
                }
                // Delete thumbnail if exists
                download.download.thumbnailPath?.let { thumbPath ->
                    try {
                        File(thumbPath).takeIf { it.exists() }?.delete()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to delete thumbnail", e)
                    }
                }
                downloadDao.deleteDownload(videoId)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete download: $videoId", e)
            false
        }
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
            quality = dwi.items.firstOrNull()?.quality ?: "Unknown"
        )
    }

    /** Generate a safe filename from title and quality */
    fun generateFileName(title: String, quality: String, extension: String = "mp4"): String {
        val safeTitle = title.replace(Regex("[^a-zA-Z0-9\\s.-]"), "_")
            .replace(Regex("\\s+"), "_")
            .take(100) 
        return "${safeTitle}_${quality}.$extension"
    }
}
