package com.flow.youtube.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.SearchFilter
import com.flow.youtube.data.local.UploadDate
import com.flow.youtube.data.local.Duration
import com.flow.youtube.data.local.SortBy
import com.flow.youtube.data.local.ContentType
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.model.Channel
import com.flow.youtube.data.model.Playlist
import com.flow.youtube.data.repository.YouTubeRepository
import com.flow.youtube.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.Page

class SearchViewModel(
    private val repository: YouTubeRepository = YouTubeRepository.getInstance()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()
    
    private var currentPage: Page? = null
    private var currentQuery: String = ""
    private var currentFilters: SearchFilter? = null
    private var isLoadingMore = false
    
    /**
     *  PERFORMANCE OPTIMIZED: Search with timeout protection
     */
    fun search(query: String, filters: SearchFilter? = null) {
        if (query.isBlank()) {
            _uiState.value = SearchUiState()
            return
        }
        
        currentQuery = query
        currentFilters = filters
        currentPage = null
        _uiState.value = _uiState.value.copy(
            isLoading = true, 
            error = null, 
            videos = emptyList(),
            channels = emptyList(),
            playlists = emptyList(),
            filters = filters
        )
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                // Build content filters based on ContentType
                val contentFilters = buildContentFilters(filters?.contentType)
                
                val searchResult = withTimeoutOrNull(15_000L) {
                    repository.search(query, contentFilters, null)
                }
                
                if (searchResult != null) {
                    currentPage = null // TODO: Implement pagination for multi-type search
                    
                    // Apply client-side filtering if filters are set
                    val filteredVideos = applyFilters(searchResult.videos, filters)
                    
                    _uiState.value = _uiState.value.copy(
                        videos = filteredVideos,
                        channels = searchResult.channels,
                        playlists = searchResult.playlists,
                        isLoading = false,
                        hasMorePages = true,
                        query = query
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Search timed out"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Search failed"
                )
            }
        }
    }
    
    // Build content filters for NewPipe API
    private fun buildContentFilters(contentType: ContentType?): List<String> {
        return when (contentType) {
            ContentType.VIDEOS -> listOf("videos")
            ContentType.CHANNELS -> listOf("channels")
            ContentType.PLAYLISTS -> listOf("playlists")
            ContentType.ALL, null -> emptyList() // Empty list = all content types
        }
    }
    
    /**
     *  PERFORMANCE OPTIMIZED: Get search suggestions with timeout
     */
    suspend fun getSearchSuggestions(query: String): List<String> {
        if (query.length < 2) return emptyList()
        return try {
            withTimeoutOrNull(5_000L) {
                repository.getSearchSuggestions(query)
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    fun updateFilters(filters: SearchFilter) {
        _uiState.value = _uiState.value.copy(filters = filters)
        
        // Re-search with new filters if there's an active query
        if (currentQuery.isNotBlank()) {
            search(currentQuery, filters)
        }
    }
    
    fun hasActiveFilters(filters: SearchFilter?): Boolean {
        if (filters == null) return false
        return filters.uploadDate != UploadDate.ANY ||
               filters.duration != Duration.ANY ||
               filters.sortBy != SortBy.RELEVANCE ||
               filters.features.isNotEmpty()
    }
    
    private fun applyFilters(videos: List<Video>, filters: SearchFilter?): List<Video> {
        if (filters == null) return videos
        
        var result = videos
        
        // Apply duration filter
        result = when (filters.duration) {
            Duration.UNDER_4_MINUTES -> result.filter { it.duration < 240 }
            Duration.FOUR_TO_20_MINUTES -> result.filter { it.duration in 240..1200 }
            Duration.OVER_20_MINUTES -> result.filter { it.duration > 1200 }
            else -> result
        }
        
        // Apply sorting
        result = when (filters.sortBy) {
            SortBy.UPLOAD_DATE -> result // Can't sort by upload date without proper date parsing
            SortBy.VIEW_COUNT -> result.sortedByDescending { it.viewCount }
            SortBy.RATING -> result // Rating not available in Video model
            else -> result
        }
        
        return result
    }
    
    /**
     *  PERFORMANCE OPTIMIZED: Load more results with timeout
     */
    fun loadMoreResults() {
        if (isLoadingMore || !_uiState.value.hasMorePages || currentPage == null) return
        
        isLoadingMore = true
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val result = withTimeoutOrNull(12_000L) {
                    repository.searchVideos(currentQuery, currentPage)
                }
                
                if (result != null) {
                    val (videos, nextPage) = result
                    currentPage = nextPage
                    
                    // Apply filters to new videos
                    val filteredVideos = applyFilters(videos, currentFilters)
                    val updatedVideos = _uiState.value.videos + filteredVideos
                    
                    _uiState.value = _uiState.value.copy(
                        videos = updatedVideos,
                        isLoadingMore = false,
                        hasMorePages = nextPage != null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        error = "Loading more results timed out"
                    )
                }
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
        currentFilters = null
    }
}

data class SearchUiState(
    val videos: List<Video> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val query: String = "",
    val filters: SearchFilter? = null,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null
)

