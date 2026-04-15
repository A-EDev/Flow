package io.github.aedev.flow.ui.screens.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.repository.YouTubeRepository.TrendingCategory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CategoriesUiState(
    val selectedCategory: TrendingCategory = TrendingCategory.ALL,
    val videos: List<Video> = emptyList(),
    val displayedVideos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: String? = null,
    val isListView: Boolean = false,
    val canLoadMore: Boolean = false,
    val currentPage: Int = 0
)

private const val PAGE_SIZE = 20

@HiltViewModel
class CategoriesViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val preferences: PlayerPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(CategoriesUiState())
    val uiState: StateFlow<CategoriesUiState> = _uiState.asStateFlow()

    val trendingRegion: StateFlow<String> = preferences.trendingRegion
        .stateIn(viewModelScope, SharingStarted.Eagerly, "US")

    val showRegionPickerInExplore: StateFlow<Boolean> = preferences.showRegionPickerInExplore
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // Cache per-category so switching tabs is instant after first load
    private val cache = mutableMapOf<TrendingCategory, List<Video>>()
    private var loadJob: Job? = null

    init {
        loadCategory(TrendingCategory.ALL)
    }

    fun selectCategory(category: TrendingCategory) {
        if (_uiState.value.selectedCategory == category && _uiState.value.videos.isNotEmpty()) return
        _uiState.update { it.copy(selectedCategory = category) }
        loadCategory(category)
    }

    private fun loadCategory(category: TrendingCategory) {
        loadJob?.cancel()
        val cached = cache[category]
        if (cached != null) {
            _uiState.update {
                it.copy(
                    videos = cached,
                    displayedVideos = cached.take(PAGE_SIZE),
                    currentPage = 1,
                    canLoadMore = cached.size > PAGE_SIZE,
                    isLoading = false,
                    error = null
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                error = null,
                videos = emptyList(),
                displayedVideos = emptyList(),
                currentPage = 0,
                canLoadMore = false
            )
        }

        loadJob = viewModelScope.launch {
            try {
                val region = preferences.trendingRegion.first()
                val rawVideos = repository.getTrendingByCategory(category, region)
                
                cache[category] = rawVideos
                _uiState.update {
                    it.copy(
                        videos = rawVideos,
                        displayedVideos = rawVideos.take(PAGE_SIZE),
                        currentPage = 1,
                        canLoadMore = rawVideos.size > PAGE_SIZE,
                        isLoading = false,
                        error = if (rawVideos.isEmpty()) "No videos found for this category." else null
                    )
                }

                // Background avatar enrichment — fills in missing channel thumbnails
                viewModelScope.launch {
                    try {
                        val enriched = repository.enrichVideosWithAvatars(rawVideos)
                        if (enriched !== rawVideos) {          
                            cache[category] = enriched
                            _uiState.update { state ->
                                state.copy(
                                    videos = enriched,
                                    displayedVideos = enriched.take(state.currentPage * PAGE_SIZE)
                                )
                            }
                        }
                    } catch (_: Exception) { /* silently skip avatar enrichment */ }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Failed to load videos."
                    )
                }
            }
        }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.canLoadMore) return

        val nextPage = state.currentPage + 1
        val nextChunk = state.videos.take(nextPage * PAGE_SIZE)
        val hasMore = state.videos.size > nextPage * PAGE_SIZE

        _uiState.update {
            it.copy(
                displayedVideos = nextChunk,
                currentPage = nextPage,
                canLoadMore = hasMore,
                isLoadingMore = false
            )
        }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isListView = !it.isListView) }
    }

    fun refresh() {
        val category = _uiState.value.selectedCategory
        cache.remove(category)
        loadCategory(category)
    }

    fun setRegion(region: String) {
        viewModelScope.launch {
            preferences.setTrendingRegion(region)
            cache.clear()
            loadCategory(_uiState.value.selectedCategory)
        }
    }
}
