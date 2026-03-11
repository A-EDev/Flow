package com.flow.youtube.ui.screens.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.flow.youtube.data.local.ContentType
import com.flow.youtube.data.local.SearchFilter
import com.flow.youtube.data.paging.SearchPagingSource
import com.flow.youtube.data.paging.SearchResultItem
import com.flow.youtube.data.repository.YouTubeRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*

// ── UI state ─────────────────────────────────────────────────────────────────

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilter? = null
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val repository: YouTubeRepository = YouTubeRepository.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /**
     * Internal trigger: emitting a new value here restarts the pager from page 0.
     * Holds (query, contentFilters) so the PagingSource gets fresh arguments.
     */
    private data class SearchKey(val query: String, val contentFilters: List<String>)
    private val _searchKey = MutableStateFlow<SearchKey?>(null)

    /**
     * Paging3 stream – the infinite-scroll source of truth for the search list.
     * flatMapLatest restarts the pager whenever [_searchKey] changes (new search
     * or filter change), and cachedIn survives configuration changes.
     */
    val searchResults: Flow<PagingData<SearchResultItem>> = _searchKey
        .filterNotNull()
        .filter { it.query.isNotBlank() }
        .flatMapLatest { key ->
            Pager(
                config = PagingConfig(
                    pageSize = 20,
                    prefetchDistance = 6,
                    enablePlaceholders = false,
                    initialLoadSize = 20
                ),
                pagingSourceFactory = { SearchPagingSource(key.query, key.contentFilters) }
            ).flow
        }
        .cachedIn(viewModelScope)

    // ── public API ────────────────────────────────────────────────────────────

    fun search(query: String, filters: SearchFilter? = null) {
        if (query.isBlank()) {
            _uiState.value = SearchUiState()
            _searchKey.value = null
            return
        }
        _uiState.value = SearchUiState(query = query, filters = filters)
        _searchKey.value = SearchKey(query, buildContentFilters(filters))
    }

    fun updateFilters(filters: SearchFilter) {
        val currentQuery = _uiState.value.query
        _uiState.value = _uiState.value.copy(filters = filters)
        if (currentQuery.isNotBlank()) {
            _searchKey.value = SearchKey(currentQuery, buildContentFilters(filters))
        }
    }

    fun clearSearch() {
        _uiState.value = SearchUiState()
        _searchKey.value = null
    }

    fun hasActiveFilters(filters: SearchFilter?): Boolean {
        if (filters == null) return false
        return filters.contentType != ContentType.ALL
    }

    suspend fun getSearchSuggestions(query: String): List<String> {
        if (query.length < 2) return emptyList()
        return try {
            repository.getSearchSuggestions(query)
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun buildContentFilters(filters: SearchFilter?): List<String> {
        val list = mutableListOf<String>()
        if (filters == null) return list
        
        when (filters.contentType) {
            ContentType.VIDEOS -> list.add("videos")
            ContentType.CHANNELS -> list.add("channels")
            ContentType.PLAYLISTS -> list.add("playlists")
            else -> {} 
        }
        
        return list
    }
}

