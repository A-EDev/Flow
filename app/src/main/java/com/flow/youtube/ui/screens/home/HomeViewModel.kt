package com.flow.youtube.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.repository.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

class HomeViewModel(
    private val repository: YouTubeRepository = YouTubeRepository.getInstance()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var currentPage: Page? = null
    private var isLoadingMore = false
    
    init {
        loadTrendingVideos()
    }
    
    fun loadTrendingVideos(region: String = "US") {
        if (_uiState.value.isLoading) return
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val (videos, nextPage) = repository.getTrendingVideos(region, null)
                currentPage = nextPage
                
                _uiState.value = _uiState.value.copy(
                    videos = videos,
                    isLoading = false,
                    hasMorePages = nextPage != null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load videos"
                )
            }
        }
    }
    
    fun loadMoreVideos(region: String = "US") {
        if (isLoadingMore || !_uiState.value.hasMorePages || currentPage == null) return
        
        isLoadingMore = true
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        
        viewModelScope.launch {
            try {
                val (videos, nextPage) = repository.getTrendingVideos(region, currentPage)
                currentPage = nextPage
                
                val updatedVideos = _uiState.value.videos + videos
                
                _uiState.value = _uiState.value.copy(
                    videos = updatedVideos,
                    isLoadingMore = false,
                    hasMorePages = nextPage != null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.message ?: "Failed to load more videos"
                )
            } finally {
                isLoadingMore = false
            }
        }
    }
    
    fun retry() {
        loadTrendingVideos()
    }
}

data class HomeUiState(
    val videos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null
)
