package com.flow.youtube.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.RecommendationRepository
import com.flow.youtube.data.recommendation.RecommendationWorker
import com.flow.youtube.data.recommendation.ScoredVideo
import com.flow.youtube.data.repository.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.schabi.newpipe.extractor.Page

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val recommendationRepository: RecommendationRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var currentPage: Page? = null
    private var isLoadingMore = false
    // private var recommendationRepository: RecommendationRepository? = null // Injected
    private var isInitialized = false
    
    init {
        // We can initialize immediately or wait for explicit call. 
        // Previously initialize(context) was called.
        // RecommendationRepository is already initialized via Hilt provider.
        // RecommendationWorker scheduling might still need context, but that should ideally go to Application or WorkerFactory.
        // For now, let's keep the load logic, but remove the context-dependent repo init.
        loadFlowFeed()
    }
    
    /**
     * Initialize with context for accessing recommendation repository
     * Kept for compatibility if needed, but repo is now injected.
     * RecommendationWorker.schedulePeriodicRefresh(context) needs context.
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        
        // recommendationRepository = RecommendationRepository.getInstance(context) // Handled by Hilt
        
        // Schedule periodic background refresh
        RecommendationWorker.schedulePeriodicRefresh(context)
        
        // Load feed (if not already loaded by init)
        // loadFlowFeed() 
    }
    
    /**
     * Load the personalized Flow feed
     * Uses cached data if valid, otherwise fetches fresh
     * If no personalized content available, mixes with trending
     */
    fun loadFlowFeed(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading) return
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val repo = recommendationRepository
                
                // PARALLEL FETCHING: Optimize start-up time by acting speculatively
                // 1. Try to get personalized feed
                // 2. Concurrently fetch trending as a backup to avoid waterfall delay if personalized is empty/fails
                
                val personalizedJob = async {
                    if (true) { // Repo is always inclusive now
                        val cacheValid = repo.isCacheValid()
                        if (!forceRefresh && cacheValid) {
                            repo.getCachedFeed().first()
                        } else {
                            repo.refreshFeed()
                        }
                    } else {
                        emptyList()
                    }
                }

                // Fetch trending in parallel as backup
                val trendingJob = async {
                    try {
                         repository.getTrendingVideos("US", null)
                    } catch (e: Exception) {
                        Pair(emptyList<Video>(), null)
                    }
                }
                
                val scoredVideos = personalizedJob.await()
                
                // If we have personalized content, use it
                if (scoredVideos.isNotEmpty()) {
                    val videos = scoredVideos.map { it.video }
                    val lastRefresh = repo.getLastRefreshTime().first()
                    
                    _uiState.value = _uiState.value.copy(
                        videos = videos,
                        scoredVideos = scoredVideos,
                        isLoading = false,
                        hasMorePages = false,
                        isFlowFeed = true,
                        lastRefreshTime = lastRefresh
                    )
                    return@launch
                }
                
                // No personalized content, use the pre-fetched trending data
                val (trendingVideos, nextPage) = trendingJob.await()
                
                if (trendingVideos.isNotEmpty()) {
                    currentPage = nextPage
                    
                    _uiState.value = _uiState.value.copy(
                        videos = trendingVideos,
                        scoredVideos = emptyList(),
                        isLoading = false,
                        hasMorePages = nextPage != null,
                        isFlowFeed = true, // Keep as For You feed
                        lastRefreshTime = System.currentTimeMillis()
                    )
                    return@launch
                }
                
                // Trending is also empty, try search fallback (rare case, can stay sequential)
                val (searchVideos, _) = repository.searchVideos("popular videos today")
                _uiState.value = _uiState.value.copy(
                    videos = searchVideos,
                    scoredVideos = emptyList(),
                    isLoading = false,
                    hasMorePages = false,
                    isFlowFeed = true,
                    lastRefreshTime = System.currentTimeMillis()
                )
                
            } catch (e: Exception) {
                // ... same fallback logic ...
                // Even on error, try to load trending as fallback
                 try {
                    val (trendingVideos, nextPage) = repository.getTrendingVideos("US", null)
                    if (trendingVideos.isNotEmpty()) {
                        currentPage = nextPage
                        
                        _uiState.value = _uiState.value.copy(
                            videos = trendingVideos,
                            scoredVideos = emptyList(),
                            isLoading = false,
                            hasMorePages = nextPage != null,
                            isFlowFeed = true,
                            error = null
                        )
                        return@launch
                    }
                    
                    // Trending returned empty, try search fallback
                    val (searchVideos, _) = repository.searchVideos("music videos 2024")
                    if (searchVideos.isNotEmpty()) {
                        _uiState.value = _uiState.value.copy(
                            videos = searchVideos,
                            scoredVideos = emptyList(),
                            isLoading = false,
                            hasMorePages = false,
                            isFlowFeed = true,
                            error = null
                        )
                        return@launch
                    }
                    
                    // Nothing worked
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "No content available. Check your internet connection."
                    )
                } catch (e2: Exception) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to load feed: ${e2.message}"
                    )
                }
            }
        }
    }
    
    /**
     * Pull-to-refresh: Force a fresh fetch of the Flow feed
     */
    fun refreshFeed(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            
            try {
                // Request immediate background refresh
                RecommendationWorker.requestImmediateRefresh(context)
                
                // Try to get personalized content
                val repo = recommendationRepository
                var gotPersonalizedContent = false
                
                if (true) { // Always true now
                    val scoredVideos = repo.refreshFeed()
                    if (scoredVideos.isNotEmpty()) {
                        val videos = scoredVideos.map { it.video }
                        val lastRefresh = repo.getLastRefreshTime().first()
                        
                        _uiState.value = _uiState.value.copy(
                            videos = videos,
                            scoredVideos = scoredVideos,
                            isRefreshing = false,
                            isFlowFeed = true,
                            hasMorePages = false,
                            lastRefreshTime = lastRefresh
                        )
                        gotPersonalizedContent = true
                    }
                }
                
                // If no personalized content, refresh trending
                if (!gotPersonalizedContent) {
                    val (trendingVideos, nextPage) = repository.getTrendingVideos("US", null)
                    currentPage = nextPage
                    
                    _uiState.value = _uiState.value.copy(
                        videos = trendingVideos,
                        scoredVideos = emptyList(),
                        isRefreshing = false,
                        isFlowFeed = true,
                        hasMorePages = nextPage != null,
                        lastRefreshTime = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isRefreshing = false,
                    error = "Refresh failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Fallback: Load trending videos (original behavior)
     */
    fun loadTrendingVideos(region: String = "US") {
        if (_uiState.value.isLoading && _uiState.value.videos.isEmpty()) return
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                val (videos, nextPage) = repository.getTrendingVideos(region, null)
                currentPage = nextPage
                
                _uiState.value = _uiState.value.copy(
                    videos = videos,
                    scoredVideos = emptyList(),
                    isLoading = false,
                    hasMorePages = nextPage != null,
                    isFlowFeed = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load videos"
                )
            }
        }
    }
    
    fun loadMoreVideos(region: String = "US") {
        // Only load more if we have pagination available
        if (isLoadingMore || !_uiState.value.hasMorePages || currentPage == null) return
        
        isLoadingMore = true
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        
        viewModelScope.launch {
            try {
                val (videos, nextPage) = repository.getTrendingVideos(region, currentPage)
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
                    error = e.message ?: "Failed to load more videos"
                )
            } finally {
                isLoadingMore = false
            }
        }
    }
    
    fun retry() {
        loadFlowFeed(forceRefresh = true)
    }
}

data class HomeUiState(
    val videos: List<Video> = emptyList(),
    val scoredVideos: List<ScoredVideo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null,
    val isFlowFeed: Boolean = false, // true = personalized Flow feed, false = trending
    val lastRefreshTime: Long = 0L
)
