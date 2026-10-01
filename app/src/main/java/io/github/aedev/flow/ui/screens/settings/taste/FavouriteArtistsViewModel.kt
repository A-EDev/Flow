package io.github.aedev.flow.ui.screens.settings.taste

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.newmusic.InnertubeMusicService
import io.github.aedev.flow.data.recommendation.music.FavouriteArtist
import io.github.aedev.flow.data.recommendation.music.FavouriteArtistsStore
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SEARCH_DEBOUNCE_MS = 300L

internal data class FavouriteArtistsState(
    val picked: List<FavouriteArtist> = emptyList(),
    /** The chart's artists while nothing is typed, search results otherwise; null while loading. */
    val candidates: List<FavouriteArtist>? = null,
    val searching: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class FavouriteArtistsViewModel
    @Inject
    constructor(
        private val store: FavouriteArtistsStore,
    ) : ViewModel() {
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        private val popular = MutableStateFlow<List<FavouriteArtist>?>(null)

        private val searchResults =
            _query
                .debounce(SEARCH_DEBOUNCE_MS)
                .flatMapLatest { typed ->
                    if (typed.isBlank()) {
                        flowOf<List<FavouriteArtist>?>(null)
                    } else {
                        flow<List<FavouriteArtist>?> {
                            emit(null)
                            emit(search(typed.trim()))
                        }
                    }
                }.flowOn(PerformanceDispatcher.networkIO)

        val state: StateFlow<FavouriteArtistsState> =
            combine(store.artists, popular, searchResults, _query) { picked, chart, results, typed ->
                FavouriteArtistsState(
                    picked = picked,
                    candidates = if (typed.isBlank()) chart else results,
                    searching = typed.isNotBlank(),
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavouriteArtistsState())

        init {
            viewModelScope.launch(PerformanceDispatcher.networkIO) {
                popular.value =
                    InnertubeMusicService
                        .fetchCharts()
                        ?.artists
                        .orEmpty()
                        .map { FavouriteArtist(it.channelId, it.name, it.thumbnailUrl) }
            }
        }

        fun onQueryChange(query: String) {
            _query.value = query
        }

        fun setFavourite(
            artist: FavouriteArtist,
            favourite: Boolean,
        ) {
            viewModelScope.launch { store.setFavourite(artist, favourite) }
        }

        private suspend fun search(query: String): List<FavouriteArtist> =
            YouTube
                .search(query, YouTube.SearchFilter.FILTER_ARTIST)
                .getOrNull()
                ?.items
                .orEmpty()
                .filterIsInstance<ArtistItem>()
                .map { FavouriteArtist(it.id, it.title, it.thumbnail.orEmpty()) }
    }
