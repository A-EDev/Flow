package com.flow.youtube.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.LikedVideosRepository
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.data.local.PlaylistRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {
    
    private lateinit var likedVideosRepository: LikedVideosRepository
    private lateinit var viewHistory: ViewHistory
    private var playlistRepository: PlaylistRepository? = null
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    fun initialize(context: Context) {
        likedVideosRepository = LikedVideosRepository.getInstance(context)
        viewHistory = ViewHistory.getInstance(context)
        playlistRepository = PlaylistRepository(context)
        
        // Load liked videos count
        viewModelScope.launch {
            likedVideosRepository.getAllLikedVideos().collect { likedVideos ->
                _uiState.update { it.copy(likedVideosCount = likedVideos.size) }
            }
        }
        
        // Load watch history count
        viewModelScope.launch {
            viewHistory.getAllHistory().collect { history ->
                _uiState.update { it.copy(watchHistoryCount = history.size) }
            }
        }

        // Load playlists count and watch-later count
        playlistRepository?.let { repo ->
            viewModelScope.launch {
                repo.getPlaylistIdsFlow().collect { playlists ->
                    _uiState.update { it.copy(playlistsCount = playlists.size) }
                }
            }

            viewModelScope.launch {
                repo.getWatchLaterIdsFlow().collect { ids ->
                    // watchLater entries are counted in the library view
                    _uiState.update { it.copy(downloadsCount = it.downloadsCount) }
                }
            }
        }
    }
}

data class LibraryUiState(
    val likedVideosCount: Int = 0,
    val watchHistoryCount: Int = 0,
    val playlistsCount: Int = 0,
    val downloadsCount: Int = 0
)
