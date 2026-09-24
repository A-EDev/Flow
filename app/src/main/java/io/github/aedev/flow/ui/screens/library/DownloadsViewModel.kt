package io.github.aedev.flow.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.entity.DownloadItemStatus
import io.github.aedev.flow.data.local.entity.DownloadWithItems
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.video.DownloadedVideo
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.data.video.downloader.FlowDownloadService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import io.github.aedev.flow.data.music.DownloadManager as MusicDownloadManager

@HiltViewModel
class DownloadsViewModel
    @Inject
    constructor(
        private val videoDownloadManager: VideoDownloadManager,
        private val musicDownloadManager: MusicDownloadManager,
        @ApplicationContext private val appContext: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(DownloadsUiState())
        val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

        // Hidden from the lists while their deletion runs; each id drops out once the row is gone.
        private val pendingDeleteIds = MutableStateFlow<Set<String>>(emptySet())

        init {
            observeDownloads()
            if (!videoDownloadManager.hasScannedThisSession) rescan()
        }

        private fun observeDownloads() {
            viewModelScope.launch {
                combine(
                    musicDownloadManager.downloadedTracks,
                    pendingDeleteIds,
                ) { tracks, pending ->
                    tracks.filter { it.track.videoId !in pending }
                }.collect { tracks ->
                    _uiState.update { it.copy(downloadedMusic = tracks) }
                }
            }

            viewModelScope.launch {
                combine(
                    videoDownloadManager.downloadedVideos,
                    pendingDeleteIds,
                ) { videos, pending ->
                    videos.filter { it.video.id !in pending }
                }.collect { videos ->
                    _uiState.update { it.copy(downloadedVideos = videos) }
                }
            }

            viewModelScope.launch {
                combine(
                    videoDownloadManager.allDownloads,
                    pendingDeleteIds,
                ) { downloads, pending ->
                    pendingDeleteIds.update { ids -> ids.intersect(downloads.mapTo(HashSet()) { it.download.videoId }) }
                    downloads.filter { download ->
                        download.download.videoId !in pending && download.overallStatus != DownloadItemStatus.COMPLETED
                    }
                }.collect { incomplete ->
                    _uiState.update { state ->
                        val activeIds = incomplete.mapTo(HashSet()) { it.download.videoId }
                        state.copy(
                            incompleteVideoDownloads = incomplete.filterNot { it.isAudioOnly },
                            incompleteMusicDownloads = incomplete.filter { it.isAudioOnly },
                            mergingVideoIds = state.mergingVideoIds.intersect(activeIds),
                            downloadProgressMap = state.downloadProgressMap.filterKeys { it in activeIds },
                        )
                    }
                }
            }

            viewModelScope.launch {
                videoDownloadManager.progressUpdates.collect { update ->
                    _uiState.update { state ->
                        val newMerging =
                            if (update.isMerging) {
                                state.mergingVideoIds + update.videoId
                            } else {
                                state.mergingVideoIds - update.videoId
                            }
                        state.copy(
                            downloadProgressMap = state.downloadProgressMap + (update.videoId to update.progress),
                            mergingVideoIds = newMerging,
                        )
                    }
                }
            }
        }

        /**
         * A finished download is deleted here; one still running is cancelled through the service,
         * which waits for its writes to stop before deleting, so the file can't be recreated.
         */
        fun deleteVideoDownload(videoId: String) {
            pendingDeleteIds.update { it + videoId }
            viewModelScope.launch {
                val download = videoDownloadManager.getDownloadWithItems(videoId)
                if (download?.overallStatus == DownloadItemStatus.COMPLETED) {
                    videoDownloadManager.deleteDownload(videoId)
                } else {
                    FlowDownloadService.cancelDownload(appContext, videoId)
                }
            }
        }

        fun deleteMusicDownload(videoId: String) {
            pendingDeleteIds.update { it + videoId }
            viewModelScope.launch(Dispatchers.IO) {
                musicDownloadManager.deleteDownload(videoId)
                pendingDeleteIds.update { it - videoId }
            }
        }

        fun pauseVideoDownload(videoId: String) {
            FlowDownloadService.pauseDownload(appContext, videoId)
        }

        fun resumeVideoDownload(videoId: String) {
            FlowDownloadService.resumeDownload(appContext, videoId)
        }

        fun retryVideoDownload(videoId: String) {
            FlowDownloadService.retryDownload(appContext, videoId)
        }

        /** Cancels what [kind] shows as incomplete; the service deletes each once it has stopped. */
        fun removeIncompleteDownloads(audioOnly: Boolean) {
            val state = _uiState.value
            val ids =
                (if (audioOnly) state.incompleteMusicDownloads else state.incompleteVideoDownloads)
                    .map { it.download.videoId }
            if (ids.isEmpty()) return
            pendingDeleteIds.update { it + ids }
            ids.forEach { videoId -> FlowDownloadService.cancelDownload(appContext, videoId) }
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
    val incompleteMusicDownloads: List<DownloadWithItems> = emptyList(),
    val downloadedMusic: List<DownloadedTrack> = emptyList(),
    val downloadProgressMap: Map<String, Float> = emptyMap(),
    val mergingVideoIds: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isScanning: Boolean = false,
)
