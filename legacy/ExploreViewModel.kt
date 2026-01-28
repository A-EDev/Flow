package com.flow.youtube.ui.screens.explore

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.repository.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExploreViewModel(
    private val repository: YouTubeRepository = YouTubeRepository.getInstance()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ExploreUiState())
    val uiState: StateFlow<ExploreUiState> = _uiState.asStateFlow()
    
    init {
        loadAllSections()
    }
    
    private fun loadAllSections() {
        loadFeatured()
        loadTrending()
        loadMusic()
        loadGaming()
    }
    
    private fun loadFeatured() {
        viewModelScope.launch {
            try {
                val (videos, _) = repository.getTrendingVideos()
                _uiState.value = _uiState.value.copy(
                    featuredVideo = videos.firstOrNull(),
                    isFeaturedLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isFeaturedLoading = false)
            }
        }
    }
    
    private fun loadTrending() {
        viewModelScope.launch {
            try {
                val (videos, _) = repository.getTrendingVideos()
                _uiState.value = _uiState.value.copy(
                    trendingVideos = videos.take(10),
                    isTrendingLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isTrendingLoading = false)
            }
        }
    }
    
    private fun loadMusic() {
        viewModelScope.launch {
            try {
                val (videos, _) = repository.searchVideos("music official video")
                _uiState.value = _uiState.value.copy(
                    musicVideos = videos.take(10),
                    isMusicLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isMusicLoading = false)
            }
        }
    }
    
    private fun loadGaming() {
        viewModelScope.launch {
            try {
                val (videos, _) = repository.searchVideos("gaming gameplay")
                _uiState.value = _uiState.value.copy(
                    gamingVideos = videos.take(10),
                    isGamingLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isGamingLoading = false)
            }
        }
    }
    
    fun retry() {
        _uiState.value = ExploreUiState()
        loadAllSections()
    }
}

data class ExploreUiState(
    val featuredVideo: Video? = null,
    val trendingVideos: List<Video> = emptyList(),
    val musicVideos: List<Video> = emptyList(),
    val gamingVideos: List<Video> = emptyList(),
    val isFeaturedLoading: Boolean = true,
    val isTrendingLoading: Boolean = true,
    val isMusicLoading: Boolean = true,
    val isGamingLoading: Boolean = true,
    val error: String? = null
)
