@file:Suppress("ktlint:standard:backing-property-naming")

package io.github.aedev.flow.ui.screens.search

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.paging.SearchPagingSource
import io.github.aedev.flow.data.paging.SearchResultItem
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.shorts.ShortsContentFilter
import io.github.aedev.flow.data.shorts.queue.ShortsQueueHandoff
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

// ── UI state ─────────────────────────────────────────────────────────────────

data class SearchUiState(
    val query: String = "",
    val filters: SearchFilter? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: YouTubeRepository,
        private val shortsContentFilter: ShortsContentFilter,
        private val shortsQueueHandoff: ShortsQueueHandoff,
    ) : ViewModel() {
        // Signal each distinct submitted query once — typing/filter churn stays silent.
        private var lastSignaledQuery: String? = null
        private val _uiState = MutableStateFlow(SearchUiState())
        val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

        /**
         * Internal trigger: emitting a new value here restarts the pager from page 0.
         * Holds (query, contentFilters) so the PagingSource gets fresh arguments.
         */
        private data class SearchKey(
            val query: String,
            val contentFilters: List<String>,
            val searchFilter: SearchFilter?,
        )

        private val _searchKey = MutableStateFlow<SearchKey?>(null)

        /**
         * flatMapLatest restarts the pager whenever [_searchKey] changes (new search
         * or filter change), and cachedIn survives configuration changes.
         */
        val searchResults: Flow<PagingData<SearchResultItem>> =
            _searchKey
                .filterNotNull()
                .filter { it.query.isNotBlank() }
                .combine(shortsContentFilter.enabled) { key, shortsEnabled -> key to shortsEnabled }
                .flatMapLatest { (key, shortsEnabled) ->
                    Pager(
                        config =
                            PagingConfig(
                                pageSize = 20,
                                prefetchDistance = 6,
                                enablePlaceholders = false,
                                initialLoadSize = 20,
                            ),
                        pagingSourceFactory = {
                            SearchPagingSource(key.query, key.contentFilters, key.searchFilter, shortsEnabled)
                        },
                    ).flow
                }.cachedIn(viewModelScope)

        // ── public API ────────────────────────────────────────────────────────────

        fun shortsShelfSource(
            shelf: List<Video>,
            tapped: Video,
        ): ShortsQueueSource = shortsQueueHandoff.sourceForShelf(shelf, tapped)

        fun search(
            query: String,
            filters: SearchFilter? = null,
        ) {
            if (query.isBlank()) {
                _uiState.value = SearchUiState()
                _searchKey.value = null
                return
            }
            _uiState.value = SearchUiState(query = query, filters = filters)
            _searchKey.value = SearchKey(query, buildContentFilters(filters), filters)

            // A typed search is the most explicit interest statement the user makes.
            val normalized = query.trim().lowercase()
            if (normalized != lastSignaledQuery) {
                lastSignaledQuery = normalized
                viewModelScope.launch {
                    runCatching { FlowNeuroEngine.onSearchQuery(context, query) }
                }
            }
        }

        fun updateFilters(filters: SearchFilter) {
            val currentQuery = _uiState.value.query
            _uiState.value = _uiState.value.copy(filters = filters)
            if (currentQuery.isNotBlank()) {
                _searchKey.value = SearchKey(currentQuery, buildContentFilters(filters), filters)
            }
        }

        fun clearSearch() {
            _uiState.value = SearchUiState()
            _searchKey.value = null
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
                ContentType.VIDEOS -> {
                    list.add("videos")
                }

                ContentType.CHANNELS -> {
                    list.add("channels")
                }

                ContentType.PLAYLISTS -> {
                    list.add("playlists")
                }

                ContentType.LIVE -> {
                    list.add("videos")
                }

                else -> {}
            }

            return list
        }
    }
