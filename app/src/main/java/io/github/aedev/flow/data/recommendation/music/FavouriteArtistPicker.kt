package io.github.aedev.flow.data.recommendation.music

import io.github.aedev.flow.data.newmusic.InnertubeMusicService
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.CoroutineScope
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private const val SEARCH_DEBOUNCE_MS = 300L

/** Artists to pick from: the chart's, fetched once per process, or a search. */
@Singleton
class FavouriteArtistCatalog
    @Inject
    constructor() {
        private val lock = Mutex()
        private var popular: List<FavouriteArtist>? = null

        suspend fun popular(): List<FavouriteArtist> =
            lock.withLock {
                popular ?: InnertubeMusicService
                    .fetchCharts()
                    ?.artists
                    .orEmpty()
                    .map { FavouriteArtist(it.channelId, it.name, it.thumbnailUrl) }
                    .also { if (it.isNotEmpty()) popular = it }
            }

        suspend fun search(query: String): List<FavouriteArtist> =
            YouTube
                .search(query, YouTube.SearchFilter.FILTER_ARTIST)
                .getOrNull()
                ?.items
                .orEmpty()
                .filterIsInstance<ArtistItem>()
                .map { FavouriteArtist(it.id, it.title, it.thumbnail.orEmpty()) }
    }

data class FavouriteArtistsState(
    val picked: List<FavouriteArtist> = emptyList(),
    /** The chart's artists while nothing is typed, search results otherwise; null while loading. */
    val candidates: List<FavouriteArtist>? = null,
    val searching: Boolean = false,
) {
    val pickedIds: Set<String> get() = picked.mapTo(HashSet(), FavouriteArtist::id)
}

/**
 * The artist picker behind both Settings and onboarding. Nothing is fetched until [state] is
 * collected, so a screen that never shows the picker costs nothing.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class FavouriteArtistPicker(
    private val scope: CoroutineScope,
    private val store: FavouriteArtistsStore,
    private val catalog: FavouriteArtistCatalog,
) {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val popular = flow<List<FavouriteArtist>?> { emit(catalog.popular()) }.flowOn(PerformanceDispatcher.networkIO)

    private val results =
        _query
            .debounce(SEARCH_DEBOUNCE_MS)
            .flatMapLatest { typed ->
                if (typed.isBlank()) {
                    flowOf<List<FavouriteArtist>?>(null)
                } else {
                    flow<List<FavouriteArtist>?> {
                        emit(null)
                        emit(catalog.search(typed.trim()))
                    }
                }
            }.flowOn(PerformanceDispatcher.networkIO)

    val state: StateFlow<FavouriteArtistsState> =
        combine(store.artists, popular, results, _query) { picked, chart, found, typed ->
            FavouriteArtistsState(picked = picked, candidates = if (typed.isBlank()) chart else found, searching = typed.isNotBlank())
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), FavouriteArtistsState())

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun setFavourite(
        artist: FavouriteArtist,
        favourite: Boolean,
    ) {
        scope.launch { store.setFavourite(artist, favourite) }
    }
}
