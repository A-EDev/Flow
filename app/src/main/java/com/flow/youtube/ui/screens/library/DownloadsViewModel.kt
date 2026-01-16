package com.flow.youtube.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.music.DownloadManager as MusicDownloadManager
import com.flow.youtube.data.music.DownloadedTrack
import com.flow.youtube.data.video.VideoDownloadManager
import com.flow.youtube.data.video.DownloadedVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val musicDownloadManager = MusicDownloadManager(context)
    private val videoDownloadManager = VideoDownloadManager.getInstance(context)

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        observeDownloads()
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
}

data class DownloadsUiState(
    val downloadedVideos: List<DownloadedVideo> = emptyList(),
    val downloadedMusic: List<DownloadedTrack> = emptyList(),
    val isLoading: Boolean = false
)
