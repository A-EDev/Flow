package com.flow.youtube.data.music

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.notification.NotificationHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.io.File
import java.io.FileOutputStream
import java.net.URL

private val Context.downloadDataStore: DataStore<Preferences> by preferencesDataStore(name = "downloads")

/**
 * Download status for tracks
 */
enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

data class DownloadedTrack(
    val track: MusicTrack,
    val filePath: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0,
    val downloadId: Long = -1
)

/**
 * Manages music downloads for offline playback
 */
class DownloadManager(private val context: Context) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private val DOWNLOADED_TRACKS_KEY = stringPreferencesKey("downloaded_tracks")
    }
    
    // Download progress tracking
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress
    
    private val _downloadStatus = MutableStateFlow<Map<String, DownloadStatus>>(emptyMap())
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = _downloadStatus
    
    // Get all downloaded tracks
    val downloadedTracks: Flow<List<DownloadedTrack>> = context.downloadDataStore.data.map { prefs ->
        val json = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
        val type = object : TypeToken<List<DownloadedTrack>>() {}.type
        gson.fromJson(json, type)
    }
    
    /**
     * Download a track for offline playback
     */
    suspend fun downloadTrack(track: MusicTrack, audioUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            // Update status
            updateDownloadStatus(track.videoId, DownloadStatus.DOWNLOADING)
            
            // Sanitize filename
            val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9\\s-]"), "").take(50)
            val fileName = "${sanitizedTitle}.m4a"
            
            // Use Android DownloadManager
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(audioUrl))
                .setTitle(track.title)
                .setDescription("Downloading music...")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_HIDDEN)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val downloadId = downloadManager.enqueue(request)
            
            // Start monitoring progress
            monitorDownloadProgress(downloadId, track)
            
            // Save metadata
            val filePath = "${android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES)}/$fileName"
            val downloadedTrack = DownloadedTrack(
                track = track,
                filePath = filePath,
                fileSize = 0, // Will be updated when finished
                downloadId = downloadId
            )
            
            saveDownloadedTrack(downloadedTrack)
            
            Result.success(filePath)
        } catch (e: Exception) {
            Log.e("DownloadManager", "Download failed", e)
            updateDownloadStatus(track.videoId, DownloadStatus.FAILED)
            Result.failure(e)
        }
    }

    private fun monitorDownloadProgress(downloadId: Long, track: MusicTrack) {
        scope.launch {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            var downloading = true
            
            while (downloading) {
                val query = android.app.DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)
                
                if (cursor != null && cursor.moveToFirst()) {
                    val bytesDownloadedIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val bytesTotalIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val statusIdx = cursor.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS)
                    
                    if (bytesDownloadedIdx != -1 && bytesTotalIdx != -1 && statusIdx != -1) {
                        val bytesDownloaded = cursor.getInt(bytesDownloadedIdx)
                        val bytesTotal = cursor.getInt(bytesTotalIdx)
                        val status = cursor.getInt(statusIdx)
                        
                        if (status == android.app.DownloadManager.STATUS_SUCCESSFUL) {
                            downloading = false
                            updateDownloadStatus(track.videoId, DownloadStatus.DOWNLOADED)
                            NotificationHelper.showDownloadComplete(context, track.title, null, track.thumbnailUrl)
                        } else if (status == android.app.DownloadManager.STATUS_FAILED) {
                            downloading = false
                            updateDownloadStatus(track.videoId, DownloadStatus.FAILED)
                            NotificationHelper.cancelNotification(context, NotificationHelper.NOTIFICATION_DOWNLOAD_PROGRESS)
                        } else {
                            val progress = if (bytesTotal > 0) (bytesDownloaded * 100L / bytesTotal).toInt() else 0
                            updateDownloadProgress(track.videoId, progress)
                            
                            // Show custom notification
                            NotificationHelper.showDownloadProgress(
                                context = context,
                                videoTitle = track.title,
                                progress = progress,
                                thumbnailUrl = track.thumbnailUrl,
                                downloadId = downloadId
                            )
                        }
                    }
                    cursor.close()
                } else {
                    downloading = false
                }
                
                if (downloading) {
                    delay(1000) // Update every second
                }
            }
        }
    }
    

    
    /**
     * Check if track is downloaded
     */
    suspend fun isDownloaded(videoId: String): Boolean {
        return downloadedTracks.map { list -> list.any { it.track.videoId == videoId } }.firstOrNull() ?: false
    }
    
    /**
     * Get downloaded track file path
     */
    suspend fun getDownloadedTrackPath(videoId: String): String? {
        return downloadedTracks.map { list -> list.find { it.track.videoId == videoId }?.filePath }.firstOrNull()
    }
    
    /**
     * Delete downloaded track
     */
    suspend fun deleteDownload(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Get track info
            val track = downloadedTracks.map { list -> list.find { it.track.videoId == videoId } }.firstOrNull()
            
            if (track != null) {
                // Delete file
                val file = File(track.filePath)
                if (file.exists()) {
                    file.delete()
                }
                
                // Remove from DataStore
                removeDownloadedTrack(videoId)
                
                // Update status
                updateDownloadStatus(videoId, DownloadStatus.NOT_DOWNLOADED)
                
                Log.d("DownloadManager", "Deleted download: $videoId")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("DownloadManager", "Failed to delete download: $videoId", e)
            false
        }
    }
    
    /**
     * Get total downloads size
     */
    suspend fun getTotalDownloadsSize(): Long {
        return downloadedTracks.map { list -> list.sumOf { it.fileSize } }.firstOrNull() ?: 0L
    }
    
    private suspend fun saveDownloadedTrack(downloadedTrack: DownloadedTrack) {
        context.downloadDataStore.edit { prefs ->
            val currentJson = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<DownloadedTrack>>() {}.type
            val currentList: MutableList<DownloadedTrack> = gson.fromJson(currentJson, type)
            
            // Remove if already exists
            currentList.removeIf { it.track.videoId == downloadedTrack.track.videoId }
            // Add new
            currentList.add(downloadedTrack)
            
            prefs[DOWNLOADED_TRACKS_KEY] = gson.toJson(currentList)
        }
    }
    
    private suspend fun removeDownloadedTrack(videoId: String) {
        context.downloadDataStore.edit { prefs ->
            val currentJson = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<DownloadedTrack>>() {}.type
            val currentList: MutableList<DownloadedTrack> = gson.fromJson(currentJson, type)
            
            currentList.removeIf { it.track.videoId == videoId }
            
            prefs[DOWNLOADED_TRACKS_KEY] = gson.toJson(currentList)
        }
    }
    
    private fun updateDownloadProgress(videoId: String, progress: Int) {
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
            this[videoId] = progress
        }
    }
    
    private fun updateDownloadStatus(videoId: String, status: DownloadStatus) {
        _downloadStatus.value = _downloadStatus.value.toMutableMap().apply {
            this[videoId] = status
        }
    }
}
