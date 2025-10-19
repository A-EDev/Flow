package com.flow.youtube.ui.screens.library

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.LikedVideosRepository
import com.flow.youtube.data.local.ViewHistory
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LibraryViewModel : ViewModel() {
    
    private lateinit var likedVideosRepository: LikedVideosRepository
    private lateinit var viewHistory: ViewHistory
    
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()
    
    fun initialize(context: Context) {
        likedVideosRepository = LikedVideosRepository.getInstance(context)
        viewHistory = ViewHistory.getInstance(context)
        
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
    }
}

data class LibraryUiState(
    val likedVideosCount: Int = 0,
    val watchHistoryCount: Int = 0,
    val playlistsCount: Int = 0,
    val downloadsCount: Int = 0
)
