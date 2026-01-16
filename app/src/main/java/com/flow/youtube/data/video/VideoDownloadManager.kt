package com.flow.youtube.data.video

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flow.youtube.data.model.Video
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

private val Context.videoDownloadDataStore: DataStore<Preferences> by preferencesDataStore(name = "video_downloads")

data class DownloadedVideo(
    val video: Video,
    val filePath: String,
    val downloadedAt: Long = System.currentTimeMillis(),
    val fileSize: Long = 0,
    val downloadId: Long = -1,
    val quality: String = "Unknown"
)

class VideoDownloadManager private constructor(private val context: Context) {
    private val gson = Gson()
    
    companion object {
        private val DOWNLOADED_VIDEOS_KEY = stringPreferencesKey("downloaded_videos")
        
        @Volatile
        private var INSTANCE: VideoDownloadManager? = null

        fun getInstance(context: Context): VideoDownloadManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VideoDownloadManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // Get all downloaded videos
    val downloadedVideos: Flow<List<DownloadedVideo>> = context.videoDownloadDataStore.data.map { prefs ->
        val json = prefs[DOWNLOADED_VIDEOS_KEY] ?: "[]"
        val type = object : TypeToken<List<DownloadedVideo>>() {}.type
        gson.fromJson(json, type)
    }
    
    suspend fun saveDownloadedVideo(downloadedVideo: DownloadedVideo) {
        context.videoDownloadDataStore.edit { prefs ->
            val currentJson = prefs[DOWNLOADED_VIDEOS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<DownloadedVideo>>() {}.type
            val currentList: MutableList<DownloadedVideo> = gson.fromJson(currentJson, type)
            
            // Remove if already exists
            currentList.removeIf { it.video.id == downloadedVideo.video.id }
            // Add new
            currentList.add(downloadedVideo)
            
            prefs[DOWNLOADED_VIDEOS_KEY] = gson.toJson(currentList)
        }
    }
    
    suspend fun removeDownloadedVideo(videoId: String) {
        context.videoDownloadDataStore.edit { prefs ->
            val currentJson = prefs[DOWNLOADED_VIDEOS_KEY] ?: "[]"
            val type = object : TypeToken<MutableList<DownloadedVideo>>() {}.type
            val currentList: MutableList<DownloadedVideo> = gson.fromJson(currentJson, type)
            
            currentList.removeIf { it.video.id == videoId }
            
            prefs[DOWNLOADED_VIDEOS_KEY] = gson.toJson(currentList)
        }
    }

    suspend fun isDownloaded(videoId: String): Boolean {
        return downloadedVideos.map { list -> list.any { it.video.id == videoId } }.firstOrNull() ?: false
    }

    suspend fun deleteDownload(videoId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val video = downloadedVideos.map { list -> list.find { it.video.id == videoId } }.firstOrNull()
            
            if (video != null) {
                val file = File(video.filePath)
                if (file.exists()) {
                    file.delete()
                }
                
                removeDownloadedVideo(videoId)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("VideoDownloadManager", "Failed to delete download: $videoId", e)
            false
        }
    }
}
