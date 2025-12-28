package com.flow.youtube.ui.screens.music

import android.app.Application

import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.music.DownloadManager
import com.flow.youtube.data.music.DownloadStatus
import com.flow.youtube.data.music.DownloadedTrack
import com.flow.youtube.data.music.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.lifecycle.ViewModel

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val downloadManager: DownloadManager
) : ViewModel() {
    // private val playlistRepository = PlaylistRepository(application) // Injected
    // private val downloadManager = DownloadManager(application) // Injected
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    init {
        loadLibraryData()
    }
    
    private fun loadLibraryData() {
        viewModelScope.launch {
            // Combine all flows
            combine(
                playlistRepository.playlists,
                playlistRepository.favorites,
                playlistRepository.history,
                downloadManager.downloadedTracks,
                downloadManager.downloadProgress,
                downloadManager.downloadStatus
            ) { args: Array<*> ->
                @Suppress("UNCHECKED_CAST")
                val playlists = args[0] as List<com.flow.youtube.data.music.Playlist>
                @Suppress("UNCHECKED_CAST")
                val favorites = args[1] as List<MusicTrack>
                @Suppress("UNCHECKED_CAST")
                val history = args[2] as List<MusicTrack>
                @Suppress("UNCHECKED_CAST")
                val downloads = args[3] as List<DownloadedTrack>
                @Suppress("UNCHECKED_CAST")
                val progress = args[4] as Map<String, Int>
                @Suppress("UNCHECKED_CAST")
                val status = args[5] as Map<String, DownloadStatus>
                
                LibraryUiState(
                    playlists = playlists,
                    favorites = favorites,
                    history = history,
                    downloads = downloads,
                    downloadProgress = progress,
                    downloadStatus = status
                )
            }.collect { state ->
                _uiState.value = state.copy(
                    showCreatePlaylistDialog = _uiState.value.showCreatePlaylistDialog
                )
            }
        }
    }
    
    fun createPlaylist(name: String, description: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, description)
            showCreatePlaylistDialog(false)
        }
    }
    
    fun removeFromFavorites(videoId: String) {
        viewModelScope.launch {
            playlistRepository.removeFromFavorites(videoId)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            playlistRepository.clearHistory()
        }
    }
    
    fun deleteDownload(videoId: String) {
        viewModelScope.launch {
            downloadManager.deleteDownload(videoId)
        }
    }
    
    fun showCreatePlaylistDialog(show: Boolean) {
        _uiState.value = _uiState.value.copy(showCreatePlaylistDialog = show)
    }
}

data class LibraryUiState(
    val playlists: List<com.flow.youtube.data.music.Playlist> = emptyList(),
    val favorites: List<MusicTrack> = emptyList(),
    val history: List<MusicTrack> = emptyList(),
    val downloads: List<DownloadedTrack> = emptyList(),
    val downloadProgress: Map<String, Int> = emptyMap(),
    val downloadStatus: Map<String, DownloadStatus> = emptyMap(),
    val showCreatePlaylistDialog: Boolean = false
)
