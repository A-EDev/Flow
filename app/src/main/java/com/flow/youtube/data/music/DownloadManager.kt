package com.flow.youtube.data.music

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flow.youtube.ui.screens.music.MusicTrack
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
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
    val fileSize: Long = 0
)

/**
 * Manages music downloads for offline playback
 */
class DownloadManager(private val context: Context) {
    private val gson = Gson()
    
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
            
            // Create downloads directory
            val downloadsDir = File(context.filesDir, "music_downloads")
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            // Sanitize filename
            val sanitizedTitle = track.title.replace(Regex("[^a-zA-Z0-9\\s-]"), "").take(50)
            val fileName = "${track.videoId}_${sanitizedTitle}.m4a"
            val outputFile = File(downloadsDir, fileName)
            
            // Download file with progress tracking
            val url = URL(audioUrl)
            val connection = url.openConnection()
            connection.connect()
            
            val fileLength = connection.contentLength
            
            connection.getInputStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var totalBytesRead = 0L
                    
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalBytesRead += bytesRead
                        
                        // Update progress
                        if (fileLength > 0) {
                            val progress = ((totalBytesRead * 100) / fileLength).toInt()
                            updateDownloadProgress(track.videoId, progress)
                        }
                    }
                }
            }
            
            // Save to DataStore
            val downloadedTrack = DownloadedTrack(
                track = track,
                filePath = outputFile.absolutePath,
                fileSize = outputFile.length()
            )
            saveDownloadedTrack(downloadedTrack)
            
            // Update status
            updateDownloadStatus(track.videoId, DownloadStatus.DOWNLOADED)
            updateDownloadProgress(track.videoId, 100)
            
            Log.d("DownloadManager", "Downloaded ${track.title} to ${outputFile.absolutePath}")
            Result.success(outputFile.absolutePath)
            
        } catch (e: Exception) {
            Log.e("DownloadManager", "Download failed for ${track.title}", e)
            updateDownloadStatus(track.videoId, DownloadStatus.FAILED)
            Result.failure(e)
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
