package com.flow.youtube.ui.screens.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.YouTube.SearchFilter
import com.flow.youtube.innertube.models.SearchSuggestions
import com.flow.youtube.innertube.models.YTItem
import com.flow.youtube.innertube.pages.SearchSummaryPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    private fun fetchSuggestions(q: String) {
        viewModelScope.launch {
            YouTube.searchSuggestions(q).onSuccess { suggestions ->
                _uiState.update { it.copy(
                    suggestions = suggestions.queries,
                    recommendedItems = suggestions.recommendedItems
                ) }
            }
        }
    }

    fun performSearch(q: String = _query.value) {
        if (q.isBlank()) return
        
        _query.value = q
        _uiState.update { it.copy(isLoading = true, isSearching = true, activeFilter = null) }
        
        viewModelScope.launch {
            YouTube.searchSummary(q).onSuccess { summaryPage ->
                _uiState.update { state -> state.copy(
                    searchSummary = summaryPage,
                    isLoading = false,
                    isSearching = true
                ) }
            }.onFailure { throwable ->
                _uiState.update { state -> state.copy(isLoading = false, error = throwable.message) }
            }
        }
    }

    fun applyFilter(filter: SearchFilter?) {
        val q = _query.value
        if (q.isBlank()) return
        
        _uiState.update { state -> state.copy(isLoading = true, activeFilter = filter) }
        
        viewModelScope.launch {
            if (filter == null) {
                performSearch(q)
            } else {
                YouTube.search(q, filter).onSuccess { result ->
                    _uiState.update { state -> state.copy(
                        filteredResults = result.items,
                        isLoading = false
                    ) }
                }.onFailure { throwable ->
                    _uiState.update { state -> state.copy(isLoading = false, error = throwable.message) }
                }
            }
        }
    }

    fun clearSearch() {
        _query.value = ""
        _uiState.value = MusicSearchUiState()
    }

    fun getArtistTracks(artistId: String, callback: (List<YTItem>) -> Unit) {
        viewModelScope.launch {
            YouTube.artist(artistId).onSuccess { artistPage ->
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
