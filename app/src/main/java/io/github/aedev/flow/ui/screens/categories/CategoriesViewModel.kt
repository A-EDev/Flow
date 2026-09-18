package io.github.aedev.flow.ui.screens.categories

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.paging.FeedTabPagingSource
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.explore.ExploreDestination
import io.github.aedev.flow.innertube.pages.explore.ExploreSectionKind
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What one destination is currently showing. Only one of [shelves] and the pager is ever populated:
 * a landing page is shelves, a see-all or the gaming tab is a paged grid, a chart is a fixed list.
 */
data class CategoriesUiState(
    val selected: ExploreDestination = CATEGORY_TABS.first().destination,
    val sectionKind: ExploreSectionKind = ExploreSectionKind.SHELVES,
    val shelves: List<FeedShelf> = emptyList(),
    val chartEntries: List<Video> = emptyList(),
    val subTabs: List<CategorySubTab> = emptyList(),
    val selectedSubTab: String? = null,
    val openShelfTitle: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isListView: Boolean = false,
)

/** A destination's own category tabs — News ships seven; every other destination ships none. */
data class CategorySubTab(
    val title: String,
    val params: String?,
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class CategoriesViewModel
    @Inject
    constructor(
        private val preferences: PlayerPreferences,
        @ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _uiState = MutableStateFlow(CategoriesUiState())
        val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

        val trendingRegion: StateFlow<String> =
            preferences.trendingRegion.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), "US")

        val showRegionPicker: StateFlow<Boolean> =
            preferences.showRegionPickerInExplore
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), true)

        private data class GridKey(
            val browseId: String,
            val params: String,
        )

        private val gridKey = MutableStateFlow<GridKey?>(null)

        /** The paged grid behind a shelf's "see all" and behind the Gaming tab. */
        val gridItems: Flow<PagingData<FeedItem>> =
            gridKey
                .filterNotNull()
                .flatMapLatest { key ->
                    Pager(
                        config =
                            PagingConfig(
                                pageSize = PAGE_SIZE,
                                prefetchDistance = PREFETCH_DISTANCE,
                                enablePlaceholders = false,
                                initialLoadSize = PAGE_SIZE,
                            ),
                        pagingSourceFactory = {
                            FeedTabPagingSource(
                                browseId = key.browseId,
                                params = key.params,
                                kind = ChannelTabKind.Videos,
                            )
                        },
                    ).flow
                }.cachedIn(viewModelScope)

        private var loadJob: Job? = null

        init {
            viewModelScope.launch {
                _uiState.update { it.copy(isListView = preferences.categoriesIsListView.first()) }
            }
            select(CATEGORY_TABS.first().destination)
        }

        fun select(destination: ExploreDestination) {
            if (_uiState.value.selected == destination && _uiState.value.error == null) return
            _uiState.update {
                CategoriesUiState(
                    selected = destination,
                    sectionKind = destination.kind,
                    isListView = it.isListView,
                )
            }
            load(destination)
        }

        /** Opens a shelf's "see all" as a paged grid, keeping the destination tab selected. */
        fun openShelf(shelf: FeedShelf) {
            val params = shelf.moreParams ?: return
            _uiState.update {
                it.copy(
                    sectionKind = ExploreSectionKind.GRID,
                    openShelfTitle = shelf.title,
                    error = null,
                )
            }
            gridKey.value = GridKey(_uiState.value.selected.browseId, params)
        }

        /** Back out of a "see all" to the destination's shelves, which are still in state. */
        fun closeShelf() {
            if (_uiState.value.openShelfTitle == null) return
            _uiState.update { it.copy(sectionKind = ExploreSectionKind.SHELVES, openShelfTitle = null) }
        }

        fun selectSubTab(subTab: CategorySubTab) {
            if (_uiState.value.selectedSubTab == subTab.title) return
            _uiState.update { it.copy(selectedSubTab = subTab.title) }
            load(_uiState.value.selected, params = subTab.params)
        }

        fun toggleViewMode() {
            val next = !_uiState.value.isListView
            _uiState.update { it.copy(isListView = next) }
            viewModelScope.launch { preferences.setCategoriesIsListView(next) }
        }

        fun refresh() {
            val state = _uiState.value
            load(state.selected, params = state.subTabParams())
        }

        fun setRegion(region: String) {
            viewModelScope.launch {
                preferences.setTrendingRegion(region)
                load(_uiState.value.selected, params = _uiState.value.subTabParams())
            }
        }

        private fun load(
            destination: ExploreDestination,
            params: String? = destination.params,
        ) {
            loadJob?.cancel()
            _uiState.update {
                it.copy(
                    sectionKind = destination.kind,
                    isLoading = true,
                    error = null,
                    openShelfTitle = null,
                    shelves = emptyList(),
                    chartEntries = emptyList(),
                )
            }
            loadJob =
                viewModelScope.launch {
                    when (destination.kind) {
                        ExploreSectionKind.CHART -> loadChart(destination)
                        ExploreSectionKind.GRID -> loadGrid(destination, params)
                        ExploreSectionKind.SHELVES -> loadShelves(destination, params)
                    }
                }
        }

        private suspend fun loadShelves(
            destination: ExploreDestination,
            params: String?,
        ) {
            YouTube
                .exploreDestination(destination.browseId, params)
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            shelves = page.shelves,
                            subTabs = page.tabs.map { tab -> CategorySubTab(tab.title, tab.params) },
                            selectedSubTab = it.selectedSubTab ?: page.tabs.firstOrNull { tab -> tab.selected }?.title,
                            isLoading = false,
                            error = if (page.shelves.isEmpty()) context.getString(R.string.error_no_videos_for_category) else null,
                        )
                    }
                }.onFailure { failed(it) }
        }

        private fun loadGrid(
            destination: ExploreDestination,
            params: String?,
        ) {
            gridKey.value = params?.let { GridKey(destination.browseId, it) }
            _uiState.update { it.copy(isLoading = false) }
        }

        private suspend fun loadChart(destination: ExploreDestination) {
            val chartType = destination.chartType ?: return
            val region = preferences.trendingRegion.first()
            YouTube
                .videoCharts(chartType, destination.chartCountryFor(region).orEmpty())
                .onSuccess { page ->
                    _uiState.update {
                        it.copy(
                            chartEntries = page.entries,
                            isLoading = false,
                            error = if (page.entries.isEmpty()) context.getString(R.string.error_no_videos_for_category) else null,
                        )
                    }
                }.onFailure { failed(it) }
        }

        private fun failed(error: Throwable) {
            _uiState.update {
                it.copy(
                    isLoading = false,
                    error = error.localizedMessage ?: context.getString(R.string.error_failed_to_load_videos),
                )
            }
        }

        private fun CategoriesUiState.subTabParams(): String? =
            subTabs.firstOrNull { it.title == selectedSubTab }?.params ?: selected.params
    }

private const val PAGE_SIZE = 20
private const val PREFETCH_DISTANCE = 6
private const val SUBSCRIPTION_GRACE_MS = 5_000L
