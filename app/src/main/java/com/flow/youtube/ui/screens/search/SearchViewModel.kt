package com.flow.youtube.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.repository.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.Page

class SearchViewModel(
    private val repository: YouTubeRepository = YouTubeRepository.getInstance()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private var currentPage: Page? = null
    private var currentQuery: String = ""
    private var isLoadingMore = false
    
    fun search(query: String) {
        if (query.isBlank()) {
            _uiState.value = SearchUiState()
            return
        }
        
        currentQuery = query
        currentPage = null
        _uiState.value = _uiState.value.copy(isLoading = true, error = null, videos = emptyList())
        
        viewModelScope.launch {
            try {
                val (videos, nextPage) = repository.searchVideos(query, null)
                currentPage = nextPage
                
                _uiState.value = _uiState.value.copy(
                    videos = videos,
                    isLoading = false,
                    hasMorePages = nextPage != null,
                    query = query
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }
    
    fun loadMoreResults() {
        if (isLoadingMore || !_uiState.value.hasMorePages || currentPage == null) return
        
        isLoadingMore = true
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        
        viewModelScope.launch {
            try {
                val (videos, nextPage) = repository.searchVideos(currentQuery, currentPage)
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
                    error = e.message ?: "Failed to load more results"
                )
            } finally {
                isLoadingMore = false
            }
        }
    }
    
    fun clearSearch() {
        _uiState.value = SearchUiState()
        currentQuery = ""
        currentPage = null
    }
}

data class SearchUiState(
    val videos: List<Video> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null
)
