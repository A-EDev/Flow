package com.flow.youtube.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.YouTube.SearchFilter
import com.flow.youtube.innertube.models.SearchSuggestions
import com.flow.youtube.innertube.models.YTItem
import com.flow.youtube.innertube.pages.SearchSummaryPage
import com.flow.youtube.utils.PerformanceDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@OptIn(FlowPreview::class)
@HiltViewModel
class MusicSearchViewModel @Inject constructor() : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _uiState = MutableStateFlow(MusicSearchUiState())
    val uiState: StateFlow<MusicSearchUiState> = _uiState.asStateFlow()

    init {
        // Handle search suggestions with debounce
        _query
            .debounce(300)
            .filter { it.isNotBlank() }
            .onEach { q ->
                fetchSuggestions(q)
            }
            .launchIn(viewModelScope)
    }

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
        if (newQuery.isBlank()) {
            _uiState.update { it.copy(suggestions = emptyList(), recommendedItems = emptyList()) }
        }
    }

    /**
     *  PERFORMANCE OPTIMIZED: Fetch suggestions with timeout
     */
    private fun fetchSuggestions(q: String) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            val result = withTimeoutOrNull(5_000L) {
                YouTube.searchSuggestions(q)
            }
            
            result?.onSuccess { suggestions ->
                _uiState.update { it.copy(
                    suggestions = suggestions.queries,
                    recommendedItems = suggestions.recommendedItems
                ) }
            }
        }
    }

    /**
     *  PERFORMANCE OPTIMIZED: Perform search with timeout protection
     */
    fun performSearch(q: String = _query.value) {
        if (q.isBlank()) return
        
        _query.value = q
        _uiState.update { it.copy(isLoading = true, isSearching = true, activeFilter = null) }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            val result = withTimeoutOrNull(15_000L) {
                YouTube.searchSummary(q)
            }
            
            result?.onSuccess { summaryPage ->
                _uiState.update { state -> state.copy(
                    searchSummary = summaryPage,
                    isLoading = false,
                    isSearching = true
                ) }
            }?.onFailure { throwable ->
                _uiState.update { state -> state.copy(isLoading = false, error = throwable.message) }
            } ?: run {
                _uiState.update { state -> state.copy(isLoading = false, error = "Search timed out") }
            }
        }
    }

    /**
     *  PERFORMANCE OPTIMIZED: Apply filter with timeout protection
     */
    fun applyFilter(filter: SearchFilter?) {
        val q = _query.value
        if (q.isBlank()) return
        
        _uiState.update { state -> state.copy(isLoading = true, activeFilter = filter) }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            if (filter == null) {
                performSearch(q)
            } else {
                val result = withTimeoutOrNull(12_000L) {
                    YouTube.search(q, filter)
                }
                
                result?.onSuccess { searchResult ->
                    _uiState.update { state -> state.copy(
                        filteredResults = searchResult.items,
                        isLoading = false
                    ) }
                }?.onFailure { throwable ->
                    _uiState.update { state -> state.copy(isLoading = false, error = throwable.message) }
                } ?: run {
                    _uiState.update { state -> state.copy(isLoading = false, error = "Filter search timed out") }
                }
            }
        }
    }

    fun clearSearch() {
        _query.value = ""
        _uiState.value = MusicSearchUiState()
    }

    /**
     *  PERFORMANCE OPTIMIZED: Get artist tracks with timeout
     */
    fun getArtistTracks(artistId: String, callback: (List<YTItem>) -> Unit) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            val result = withTimeoutOrNull(10_000L) {
                YouTube.artist(artistId)
            }
            
            result?.onSuccess { artistPage ->
                val songsSection = artistPage.sections.find { it.title.contains("Songs", ignoreCase = true) }
                if (songsSection != null) {
                    callback(songsSection.items)
                } else {
                    // Fallback to first section if no "Songs" section found
                    callback(artistPage.sections.firstOrNull()?.items ?: emptyList())
                }
            }
        }
    }
}

data class MusicSearchUiState(
    val suggestions: List<String> = emptyList(),
    val recommendedItems: List<YTItem> = emptyList(),
    val searchSummary: SearchSummaryPage? = null,
    val filteredResults: List<YTItem> = emptyList(),
    val activeFilter: SearchFilter? = null,
    val isLoading: Boolean = false,
    val isSearching: Boolean = false,
    val error: String? = null
)

