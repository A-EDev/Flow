package com.flow.youtube.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.music.DownloadManager as MusicDownloadManager
import com.flow.youtube.data.music.DownloadedTrack
import com.flow.youtube.data.video.VideoDownloadManager
import com.flow.youtube.data.video.DownloadedVideo
import dagger.hilt.android.lifecycle.HiltViewModel
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

    init {
        observeDownloads()
        viewModelScope.launch {
            videoDownloadManager.scanAndRecoverDownloads()
        }
    }

    private fun observeDownloads() {
        viewModelScope.launch {
            musicDownloadManager.downloadedTracks.collect { tracks ->
                _uiState.update { it.copy(downloadedMusic = tracks) }
            }
        }

        viewModelScope.launch {
            videoDownloadManager.downloadedVideos.collect { videos ->
                _uiState.update { it.copy(downloadedVideos = videos) }
            }
        }
    }

    fun deleteVideoDownload(videoId: String) {
        viewModelScope.launch {
            videoDownloadManager.deleteDownload(videoId)
        }
    }

    fun deleteMusicDownload(videoId: String) {
        viewModelScope.launch {
            musicDownloadManager.deleteDownload(videoId)
        }
    }

    fun rescan() {
        viewModelScope.launch {
            videoDownloadManager.scanAndRecoverDownloads()
        }
    }
}

data class DownloadsUiState(
    val downloadedVideos: List<DownloadedVideo> = emptyList(),
    val downloadedMusic: List<DownloadedTrack> = emptyList(),
    val isLoading: Boolean = false
)
