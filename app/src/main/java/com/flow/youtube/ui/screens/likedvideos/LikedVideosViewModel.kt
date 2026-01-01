package com.flow.youtube.ui.screens.likedvideos

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.LikedVideosRepository
import com.flow.youtube.data.local.LikedVideoInfo
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class LikedVideosViewModel : ViewModel() {
    
    private lateinit var likedVideosRepository: LikedVideosRepository
    
    private val _uiState = MutableStateFlow(LikedVideosUiState())
    val uiState: StateFlow<LikedVideosUiState> = _uiState.asStateFlow()
    
    fun initialize(context: Context, isMusic: Boolean = false) {
        likedVideosRepository = LikedVideosRepository.getInstance(context)
        
        // Load liked videos
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val flow = if (isMusic) likedVideosRepository.getLikedMusicFlow() else likedVideosRepository.getLikedVideosFlow()
            flow.collect { likedVideos ->
                _uiState.update { 
                    it.copy(
                        likedVideos = likedVideos,
                        isLoading = false
                    )
                }
            }
        }
    }
    
    fun removeLike(videoId: String) {
        viewModelScope.launch {
            likedVideosRepository.removeLikeState(videoId)
        }
    }
}

data class LikedVideosUiState(
    val likedVideos: List<LikedVideoInfo> = emptyList(),
    val isLoading: Boolean = false
)
