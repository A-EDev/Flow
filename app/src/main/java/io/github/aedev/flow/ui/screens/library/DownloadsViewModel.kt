package io.github.aedev.flow.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.data.music.DownloadManager as MusicDownloadManager
import io.github.aedev.flow.data.music.DownloadedTrack
import io.github.aedev.flow.data.video.VideoDownloadManager
import io.github.aedev.flow.data.video.DownloadedVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val videoDownloadManager: VideoDownloadManager,
    private val musicDownloadManager: MusicDownloadManager
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
    }

    fun deleteVideoDownload(videoId: String) {
        _pendingDeleteIds.update { it + videoId }
        viewModelScope.launch(Dispatchers.IO) {
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
    val downloadedMusic: List<DownloadedTrack> = emptyList(),
    val isLoading: Boolean = false,
    val isScanning: Boolean = false
)
