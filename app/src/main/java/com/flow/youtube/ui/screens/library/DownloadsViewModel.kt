package com.flow.youtube.ui.screens.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.music.DownloadManager as MusicDownloadManager
import com.flow.youtube.data.music.DownloadedTrack
import com.flow.youtube.data.video.VideoDownloadManager
import com.flow.youtube.data.video.DownloadedVideo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val UNDO_WINDOW_MS = 4_000L

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val videoDownloadManager: VideoDownloadManager,
    private val musicDownloadManager: MusicDownloadManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    /**
     * IDs of items currently in the undo-window (optimistically hidden from the list).
     * The real I/O deletion fires after [UNDO_WINDOW_MS] unless cancelled by [cancelDelete].
     */
    private val _pendingDeleteIds = MutableStateFlow<Set<String>>(emptySet())

    private val pendingDeleteJobs = mutableMapOf<String, Job>()

    init {
        observeDownloads()
        viewModelScope.launch {
            videoDownloadManager.scanAndRecoverDownloads()
        }
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

    /**
     * Begin a delete flow for a video download.
     *
     * The item is immediately hidden from the UI.  The actual I/O deletion fires after
     * [UNDO_WINDOW_MS] unless the user calls [cancelDelete] within that window.
     * Because the Job lives in [viewModelScope] it is **not** cancelled when the user
     * navigates away — the deletion will always complete.
     */
    fun requestDeleteVideo(videoId: String) {
        scheduleDelete(videoId) {
            videoDownloadManager.deleteDownload(videoId)
        }
    }

    /**
     * Begin a delete flow for a music download.
     * Same lifecycle guarantees as [requestDeleteVideo].
     */
    fun requestDeleteMusic(videoId: String) {
        scheduleDelete(videoId) {
            musicDownloadManager.deleteDownload(videoId)
        }
    }

    /**
     * Cancel a pending deletion within the undo window.
     * The item reappears in the list immediately.
     */
    fun cancelDelete(videoId: String) {
        pendingDeleteJobs.remove(videoId)?.cancel()
        _pendingDeleteIds.update { it - videoId }
    }

    private fun scheduleDelete(id: String, doDelete: suspend () -> Unit) {
        pendingDeleteJobs.remove(id)?.cancel()

        _pendingDeleteIds.update { it + id }

        val job = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            doDelete()
            _pendingDeleteIds.update { it - id }
        }
        pendingDeleteJobs[id] = job
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
