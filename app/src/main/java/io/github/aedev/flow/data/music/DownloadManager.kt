package io.github.aedev.flow.data.music

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.data.download.DownloadUtil
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.data.video.downloader.FlowDownloadService
import io.github.aedev.flow.service.ExoDownloadService
import io.github.aedev.flow.utils.MusicPlayerUtils
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadService
import java.io.File
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
    private val downloadUtil: DownloadUtil,
    private val videoDownloadManager: VideoDownloadManager
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
    
    /**
     * Check if a track is cached for offline playback.
     * This checks the actual cache, not the download state metadata.
     */
    fun isCachedForOffline(mediaId: String): Boolean = downloadUtil.isCachedForOffline(mediaId)
    
    val downloadedTracks: Flow<List<DownloadedTrack>> = combine(
        context.downloadDataStore.data.map { prefs -> parseDownloadedTracks(prefs[DOWNLOADED_TRACKS_KEY]) },
        videoDownloadManager.audioOnlyDownloads
    ) { storedTracks, audioDownloads ->
        val roomById = audioDownloads
            .filter { it.overallStatus == DownloadItemStatus.COMPLETED }
            .associateBy { it.download.videoId }

        storedTracks.mapNotNull { storedTrack ->
            val roomDownload = roomById[storedTrack.track.videoId]
            when {
                roomDownload != null -> {
                    val audioItem = roomDownload.items.firstOrNull {
                        it.status == DownloadItemStatus.COMPLETED && isReadablePath(it.filePath)
                    }
                    audioItem?.let {
                        storedTrack.copy(
                            filePath = it.filePath,
                            downloadedAt = roomDownload.download.createdAt,
                            fileSize = it.totalBytes.takeIf { size -> size > 0 } ?: it.downloadedBytes
                        )
                    }
                }
                downloadUtil.isFullyDownloaded(storedTrack.track.videoId) ||
                    downloadUtil.isCachedForOffline(storedTrack.track.videoId) -> storedTrack
                else -> null
            }
        }.distinctBy { it.track.videoId }
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
            if (isDownloaded(track.videoId) || hasActiveFileDownload(track.videoId)) {
                return@withContext Result.success(track.videoId)
            }

            val downloadedTrack = DownloadedTrack(
                track = track,
                filePath = "",
                fileSize = 0,
                downloadId = 0
            )
            saveDownloadedTrack(downloadedTrack)

            val playbackData = MusicPlayerUtils.playerResponseForPlayback(track.videoId).getOrThrow()
            val streamUrl = playbackData.streamUrl
            val contentLength = playbackData.format.contentLength
            val downloadUrl = if (contentLength != null) {
                val sep = if ("?" in streamUrl) "&" else "?"
                "${streamUrl}${sep}range=0-${contentLength}"
            } else {
                streamUrl
            }

            val extension = "mp3"
            val mimeType = "audio/mpeg"
            val quality = playbackData.format.averageBitrate
                ?.takeIf { it > 0 }
                ?.let { "${it / 1000}kbps" }
                ?: playbackData.format.bitrate
                    .takeIf { it > 0 }
                    ?.let { "${it / 1000}kbps" }
                ?: "Music"

            val video = Video(
                id = track.videoId,
                title = track.title,
                channelName = track.artist,
                channelId = track.channelId,
                thumbnailUrl = track.thumbnailUrl,
                duration = track.duration,
                viewCount = track.views,
                uploadDate = System.currentTimeMillis().toString(),
                description = track.album,
                isMusic = true
            )

            FlowDownloadService.startDownload(
                context = context,
                video = video,
                url = downloadUrl,
                quality = quality,
                audioOnly = true,
                userAgent = playbackData.usedClient.userAgent,
                audioExtension = extension,
                audioMimeType = mimeType.ifBlank { "audio/mp4" },
                isMusic = true
            )

            Result.success(track.videoId)
        } catch (e: Exception) {
            Log.e("DownloadManager", "Download failed", e)
            Result.failure(e)
        }
    }
    
    suspend fun updateDownloadedTrack(videoId: String, size: Long = 0) {
        context.downloadDataStore.edit { prefs ->
             val json = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
             val storedTracks = parseDownloadedTracks(json).toMutableList()
             
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
        if (getCompletedAudioFilePath(videoId) != null) return true
        val download = downloadUtil.downloads.value[videoId]
        return download?.state == Download.STATE_COMPLETED
    }
    
    suspend fun getDownloadedTrackPath(videoId: String): String? {
        return getCompletedAudioFilePath(videoId)
    }
    
    suspend fun deleteDownload(videoId: String) {
        videoDownloadManager.getDownloadWithItems(videoId)
            ?.takeIf { it.isAudioOnly }
            ?.let { videoDownloadManager.deleteDownload(videoId) }

        DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, videoId, false)
        
        context.downloadDataStore.edit { prefs ->
             val json = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
             val currentTracks = parseDownloadedTracks(json).toMutableList()
             currentTracks.removeAll { it.track.videoId == videoId }
             prefs[DOWNLOADED_TRACKS_KEY] = gson.toJson(currentTracks)
        }
    }

    private suspend fun saveDownloadedTrack(track: DownloadedTrack) {
        context.downloadDataStore.edit { prefs ->
            val json = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
            val currentTracks = parseDownloadedTracks(json).toMutableList()
            currentTracks.removeAll { it.track.videoId == track.track.videoId } 
            currentTracks.add(track)
            prefs[DOWNLOADED_TRACKS_KEY] = gson.toJson(currentTracks)
        }
    }

    private suspend fun updateDownloadedTrackSize(videoId: String, size: Long) {
        context.downloadDataStore.edit { prefs ->
             val json = prefs[DOWNLOADED_TRACKS_KEY] ?: "[]"
             val currentTracks = parseDownloadedTracks(json).toMutableList()
             val index = currentTracks.indexOfFirst { it.track.videoId == videoId }
             if (index != -1) {
                 val existing = currentTracks[index]
                 currentTracks[index] = existing.copy(fileSize = size, downloadedAt = System.currentTimeMillis()) 
             prefs[DOWNLOADED_TRACKS_KEY] = gson.toJson(currentTracks)
             }
        }
    }

    private fun parseDownloadedTracks(json: String?): List<DownloadedTrack> {
        return runCatching {
            val type = object : TypeToken<List<DownloadedTrack>>() {}.type
            gson.fromJson<List<DownloadedTrack>>(json ?: "[]", type).orEmpty()
        }.getOrElse {
            Log.w("DownloadManager", "Failed to parse music downloads", it)
            emptyList()
        }
    }

    private suspend fun getCompletedAudioFilePath(videoId: String): String? {
        val download = videoDownloadManager.getDownloadWithItems(videoId) ?: return null
        if (!download.isAudioOnly || download.overallStatus != DownloadItemStatus.COMPLETED) return null
        return download.items.firstOrNull {
            it.status == DownloadItemStatus.COMPLETED && isReadablePath(it.filePath)
        }?.filePath
    }

    private suspend fun hasActiveFileDownload(videoId: String): Boolean {
        val download = videoDownloadManager.getDownloadWithItems(videoId) ?: return false
        return download.isAudioOnly && download.overallStatus in setOf(
            DownloadItemStatus.PENDING,
            DownloadItemStatus.DOWNLOADING,
            DownloadItemStatus.PAUSED
        )
    }

    private fun isReadablePath(path: String): Boolean {
        return path.startsWith("content://") || File(path).exists()
    }
}
