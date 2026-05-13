package io.github.aedev.flow.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.data.music.DownloadManager as MusicDownloadManager
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.data.video.downloader.FlowDownloadService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val videoDownloadManager: VideoDownloadManager,
    private val musicDownloadManager: MusicDownloadManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    /**
     * IDs of items currently being deleted (optimistically hidden from the list).
     */
    private val _pendingDeleteIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        observeDownloads()
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            combine(
                musicDownloadManager.downloadedTracks,
                _pendingDeleteIds
            ) { tracks, pending ->
                tracks.filter { it.track.videoId !in pending }
            }.collect { tracks ->
                _uiState.update { it.copy(downloadedMusic = tracks) }
            }
        }

        viewModelScope.launch {
            combine(
                videoDownloadManager.downloadedVideos,
                _pendingDeleteIds
            ) { videos, pending ->
                videos.filter { it.video.id !in pending }
            }.collect { videos ->
                _uiState.update { it.copy(downloadedVideos = videos) }
            }
        }

        viewModelScope.launch {
            combine(
                videoDownloadManager.allDownloads,
                _pendingDeleteIds
            ) { downloads, pending ->
                val incomplete = downloads.filter { download ->
                    download.download.videoId !in pending &&
                        download.overallStatus != io.github.aedev.flow.data.local.entity.DownloadItemStatus.COMPLETED
                }
                incomplete.filter { !it.isAudioOnly } to incomplete.size
            }.collect { (incomplete, incompleteCount) ->
                _uiState.update { state ->
                    // Auto-clear merging flags for downloads that are no longer active
                    val activeIds = incomplete.map { it.download.videoId }.toSet()
                    state.copy(
                        incompleteVideoDownloads = incomplete,
                        incompleteDownloadCount = incompleteCount,
                        mergingVideoIds = state.mergingVideoIds.intersect(activeIds)
                    )
                }
            }
        }

        viewModelScope.launch {
            videoDownloadManager.progressUpdates.collect { update ->
                _uiState.update { state ->
                    val newMerging = if (update.isMerging) {
                        state.mergingVideoIds + update.videoId
                    } else {
                        state.mergingVideoIds - update.videoId
                    }
                    state.copy(
                        downloadProgressMap = state.downloadProgressMap + (update.videoId to update.progress),
                        mergingVideoIds = newMerging
                    )
                }
            }
        }
    }

    fun deleteVideoDownload(videoId: String) {
        _pendingDeleteIds.update { it + videoId }
        viewModelScope.launch(Dispatchers.IO) {
            val download = videoDownloadManager.getDownloadWithItems(videoId)
            if (download?.overallStatus != io.github.aedev.flow.data.local.entity.DownloadItemStatus.COMPLETED) {
                FlowDownloadService.cancelDownload(appContext, videoId)
                delay(500L)
            }
            videoDownloadManager.deleteDownload(videoId)
            _pendingDeleteIds.update { it - videoId }
        }
    }

    fun deleteMusicDownload(videoId: String) {
        _pendingDeleteIds.update { it + videoId }
        viewModelScope.launch(Dispatchers.IO) {
            musicDownloadManager.deleteDownload(videoId)
            _pendingDeleteIds.update { it - videoId }
        }
    }

    fun pauseVideoDownload(videoId: String) {
        FlowDownloadService.pauseDownload(appContext, videoId)
    }

    fun resumeVideoDownload(videoId: String) {
        FlowDownloadService.resumeDownload(appContext, videoId)
    }

    fun removeIncompleteDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            val ids = videoDownloadManager.allDownloads.first()
                .filter { it.overallStatus != io.github.aedev.flow.data.local.entity.DownloadItemStatus.COMPLETED }
                .map { it.download.videoId }
            if (ids.isEmpty()) return@launch

            _pendingDeleteIds.update { it + ids }
            ids.forEach { videoId -> FlowDownloadService.cancelDownload(appContext, videoId) }
            delay(500L)
            videoDownloadManager.deleteIncompleteDownloads()
            _pendingDeleteIds.update { it - ids.toSet() }
        }
    }

    fun rescan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            videoDownloadManager.scanAndRecoverDownloads()
            _uiState.update { it.copy(isScanning = false) }
        }
    }
}

data class DownloadsUiState(
    val downloadedVideos: List<DownloadedVideo> = emptyList(),
    val incompleteVideoDownloads: List<DownloadWithItems> = emptyList(),
    val downloadedMusic: List<DownloadedTrack> = emptyList(),
    val downloadProgressMap: Map<String, Float> = emptyMap(),
    val mergingVideoIds: Set<String> = emptySet(),
    val incompleteDownloadCount: Int = 0,
    val isLoading: Boolean = false,
    val isScanning: Boolean = false
)
