package com.flow.youtube.data.music

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.data.download.DownloadUtil
import com.flow.youtube.service.ExoDownloadService
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import javax.inject.Inject
import javax.inject.Singleton
import dagger.hilt.android.qualifiers.ApplicationContext

private val Context.downloadDataStore: DataStore<Preferences> by preferencesDataStore(name = "downloads")

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

@Singleton
class DownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadUtil: DownloadUtil
) {
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        private val DOWNLOADED_TRACKS_KEY = stringPreferencesKey("downloaded_tracks")
    }
    
    val downloadProgress: StateFlow<Map<String, Int>> = downloadUtil.downloads.map { downloads ->
        downloads.mapValues { (_, download) ->
            download.percentDownloaded.toInt()
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    val downloadStatus: StateFlow<Map<String, DownloadStatus>> = downloadUtil.downloads.map { downloads ->
        downloads.mapValues { (_, download) ->
            when (download.state) {
                 Download.STATE_COMPLETED -> DownloadStatus.DOWNLOADED
                 Download.STATE_FAILED -> DownloadStatus.FAILED
                 Download.STATE_DOWNLOADING, Download.STATE_QUEUED, Download.STATE_RESTARTING -> DownloadStatus.DOWNLOADING
                 else -> DownloadStatus.NOT_DOWNLOADED
            }
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyMap())
    
    val downloadedTracks: Flow<List<DownloadedTrack>> = context.downloadDataStore.data.map { prefs ->
        val json = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
        val type = object : TypeToken<List<DownloadedTrack>>() {}.type
        gson.fromJson(json, type)
    }

    init {
        downloadUtil.getDownloadManagerInstance().addListener(object : androidx.media3.exoplayer.offline.DownloadManager.Listener {
            override fun onDownloadChanged(
                downloadManager: androidx.media3.exoplayer.offline.DownloadManager,
                download: Download,
                finalException: Exception?
            ) {
                if (download.state == Download.STATE_COMPLETED) {
                   scope.launch {
                       updateDownloadedTrack(download.request.id, download.contentLength)
                   }
                }
            }
        })
    }
    
    suspend fun downloadTrack(track: MusicTrack): Result<String> = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse("music://${track.videoId}")
            
            val downloadRequest = DownloadRequest.Builder(track.videoId, uri)
                .setCustomCacheKey(track.videoId)
                .setData(track.title.toByteArray())
                .build()
            
            DownloadService.sendAddDownload(context, ExoDownloadService::class.java, downloadRequest, false)
            
            val downloadedTrack = DownloadedTrack(
                track = track,
                filePath = track.videoId, 
                fileSize = 0, 
                downloadId = 0 
            )
            saveDownloadedTrack(downloadedTrack)
            
            Result.success(track.videoId)
        } catch (e: Exception) {
            Log.e("DownloadManager", "Download failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateDownloadedTrack(videoId: String, size: Long = 0) {
        context.downloadDataStore.edit { prefs ->
             val json = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
             val type = object : TypeToken<List<DownloadedTrack>>() {}.type
             val storedTracks: MutableList<DownloadedTrack> = gson.fromJson(json, type)
             
             val index = storedTracks.indexOfFirst { it.track.videoId == videoId }
             if (index != -1) {
                 val updated = storedTracks[index].copy(
                      fileSize = size,
                      downloadedAt = System.currentTimeMillis()
                 )
                 storedTracks[index] = updated
                 prefs[DOWNLOADED_TRACKS_KEY] = gson.toJson(storedTracks)
             }
        }
    }
    
    suspend fun isDownloaded(videoId: String): Boolean {
        val download = downloadUtil.downloads.value[videoId]
        return download?.state == Download.STATE_COMPLETED
    }
    
    suspend fun getDownloadedTrackPath(videoId: String): String? {
        return if (isDownloaded(videoId)) videoId else null
    }
    
    suspend fun deleteDownload(videoId: String) {
        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, videoId, false)
        
        context.downloadDataStore.edit { prefs ->
             val json = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
             val type = object : TypeToken<List<DownloadedTrack>>() {}.type
             val currentTracks: MutableList<DownloadedTrack> = gson.fromJson(json, type)
             currentTracks.removeAll { it.track.videoId == videoId }
             prefs[DOWNLOADED_TRACKS_KEY] = gson.toJson(currentTracks)
        }
    }

    private suspend fun saveDownloadedTrack(track: DownloadedTrack) {
        context.downloadDataStore.edit { prefs ->
            val json = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
            val type = object : TypeToken<List<DownloadedTrack>>() {}.type
            val currentTracks: MutableList<DownloadedTrack> = gson.fromJson(json, type)
            currentTracks.removeAll { it.track.videoId == track.track.videoId } 
            currentTracks.add(track)
            prefs[DOWNLOADED_TRACKS_KEY] = gson.toJson(currentTracks)
        }
    }

    private suspend fun updateDownloadedTrackSize(videoId: String, size: Long) {
        context.downloadDataStore.edit { prefs ->
             val json = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
             val type = object : TypeToken<List<DownloadedTrack>>() {}.type
             val currentTracks: MutableList<DownloadedTrack> = gson.fromJson(json, type)
             val index = currentTracks.indexOfFirst { it.track.videoId == videoId }
             if (index != -1) {
                 val existing = currentTracks[index]
                 currentTracks[index] = existing.copy(fileSize = size, downloadedAt = System.currentTimeMillis()) 
                 prefs[DOWNLOADED_TRACKS_KEY] = gson.toJson(currentTracks)
             }
        }
    }
}
