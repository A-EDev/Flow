package io.github.aedev.flow.ui.screens.likedvideos

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.LikedVideosRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LikedVideosViewModel
    @Inject
    constructor(
        private val likedVideosRepository: LikedVideosRepository,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(LikedVideosUiState())
        val uiState: StateFlow<LikedVideosUiState> = _uiState.asStateFlow()

        init {
            // Load all likes so the screen can switch between videos and music locally.
            viewModelScope.launch {
                _uiState.update { it.copy(isLoading = true) }
                likedVideosRepository.getAllLikedVideos().collect { likedVideos ->
                    _uiState.update {
                        it.copy(
                            likedVideos = likedVideos,
                            isLoading = false,
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
    val isLoading: Boolean = false,
)
