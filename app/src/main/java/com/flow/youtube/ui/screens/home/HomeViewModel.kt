package com.flow.youtube.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.RecommendationRepository
import com.flow.youtube.data.recommendation.RecommendationWorker
import com.flow.youtube.data.recommendation.ScoredVideo
import com.flow.youtube.data.recommendation.VideoSource
import com.flow.youtube.data.repository.YouTubeRepository
import com.flow.youtube.data.shorts.ShortsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.schabi.newpipe.extractor.Page

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val recommendationRepository: RecommendationRepository,
    private val subscriptionRepository: SubscriptionRepository, 
    private val shortsRepository: ShortsRepository,
    private val playerPreferences: com.flow.youtube.data.local.PlayerPreferences
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var currentPage: Page? = null
    private var isLoadingMore = false
    private var isInitialized = false
    
    init {
        // Load the intelligent feed immediately on startup
        loadFlowFeed(forceRefresh = true)
        // Load shorts in parallel
        loadHomeShorts()
    }
    
    /**
     * Initialize with context for accessing recommendation repository workers
     */
    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        
        viewModelScope.launch {
            FlowNeuroEngine.initialize(context)
        }
        RecommendationWorker.schedulePeriodicRefresh(context)
    }
    
    /**
     * Load shorts specifically for the home screen
     */
    private fun loadHomeShorts() {
        viewModelScope.launch {
            try {
                // Use the new smart shorts repo
                val shorts = shortsRepository.getHomeFeedShorts()
                if (shorts.isNotEmpty()) {
                    _uiState.update { it.copy(shorts = shorts) }
                }
            } catch (e: Exception) {
                // Shorts failure is non-critical, ignore
            }
        }
    }
    
    /**
     * Helper to safely update the video list
     * Filters out shorts from the main list to prevent clutter
     */
    private fun updateVideosAndShorts(newVideos: List<Video>, append: Boolean = false) {
        // ULTIMATE FILTER: 
        // 1. Exclude if isShort is true
        // 2. Exclude if duration is 1-80s
        // 3. Exclude if duration is 0 UNLESS it is Live
        val (newShorts, regularVideos) = newVideos.partition { 
            it.isShort || (it.duration in 1..80) || (it.duration == 0 && !it.isLive)
        }
        
        _uiState.update { state ->
            val updatedVideos = if (append) (state.videos + regularVideos) else regularVideos
            state.copy(
                videos = updatedVideos.distinctBy { it.id },
                // If we found shorts in the trending mix, add them to the shorts shelf too
                shorts = (state.shorts + newShorts).distinctBy { it.id }
            )
        }
    }

    /**
     * MAIN FEED LOADER (The Smart Algo)
     * Uses "Fetch Until Full" logic to guarantee ~60 videos
     */
    fun loadFlowFeed(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !forceRefresh) return
        
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            try {
                val userSubs = subscriptionRepository.getAllSubscriptionIds()
                val region = playerPreferences.trendingRegion.first()

                // 1. FETCH SUBSCRIPTIONS (Parallel)
                val subsDeferred = async { 
                    try {
                        if (userSubs.isNotEmpty()) {
                            val rawSubs = repository.getSubscriptionFeed(userSubs.toList())
                            // Filter subs: No shorts, no unknown 0-duration
                            rawSubs.filter { !it.isShort && ((it.duration > 80) || (it.duration == 0 && it.isLive)) }
                        } else emptyList()
                    } catch (e: Exception) { emptyList<Video>() }
                }

                // 2. FETCH TRENDING LOOP (The "Dig Deep" Fix)
                val trendingVideos = mutableListOf<Video>()
                val trendingShorts = mutableListOf<Video>()
                var tempPage: Page? = null
                var pagesFetched = 0
                
                // We keep fetching pages until we have at least 60 LONG videos or hit 6 pages.
                while (trendingVideos.size < 60 && pagesFetched < 6) {
                    try {
                        val (batch, next) = repository.getTrendingVideos(region, tempPage)
                        
                        // Split immediately: Longs are > 80s or Live
                        val (shorts, longs) = batch.partition { 
                            it.isShort || (it.duration in 1..80) || (it.duration == 0 && !it.isLive)
                        }
                        trendingVideos.addAll(longs) 
                        trendingShorts.addAll(shorts)
                        
                        tempPage = next
                        if (next == null) break
                        pagesFetched++
                    } catch (e: Exception) { break }
                }
                
                currentPage = tempPage 
                val subs = subsDeferred.await()

                // 3. MERGE & SHUFFLE
                val pool = (subs + trendingVideos).distinctBy { it.id }
                
                if (pool.isEmpty()) {
                    loadTrendingFallback()
                    return@launch
                }

                // 4. RANK WITH NEURO ENGINE
                val rankedVideos = FlowNeuroEngine.rank(pool, userSubs)
                
                // 5. BACKFILL SAFEGUARD
                val finalVideos = if (rankedVideos.size < 30) {
                    val fillers = pool.filter { p -> rankedVideos.none { r -> r.id == p.id } }.take(30)
                    (rankedVideos + fillers).distinctBy { it.id }
                } else {
                    rankedVideos
                }

                // 6. UPDATE UI
                _uiState.update { it.copy(
                    videos = finalVideos,
                    shorts = (it.shorts + trendingShorts).distinctBy { s -> s.id }.shuffled().take(25),
                    isLoading = false,
                    isRefreshing = false,
                    hasMorePages = currentPage != null, 
                    isFlowFeed = true,
                    lastRefreshTime = System.currentTimeMillis()
                )}
                
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = "Failed to load feed") }
            }
        }
    }
    
    /**
     * INFINITE SCROLL LOADER (The "Keep Digging" Fix)
     */
    fun loadMoreVideos() {
        if (isLoadingMore || !_uiState.value.hasMorePages) return
        
        isLoadingMore = true
        _uiState.update { it.copy(isLoadingMore = true) }
        
        viewModelScope.launch {
            try {
                val actualRegion = playerPreferences.trendingRegion.first()
                val newVideos = mutableListOf<Video>()
                var tempPage = currentPage
                var attempts = 0
                
                // 1. TRY TRENDING FIRST
                while (newVideos.size < 20 && tempPage != null && attempts < 3) {
                    val (batch, next) = repository.getTrendingVideos(actualRegion, tempPage)
                    val longs = batch.filter { !it.isShort && ((it.duration > 80) || (it.duration == 0 && it.isLive)) }
                    
                    newVideos.addAll(longs)
                    tempPage = next
                    attempts++
                }
                
                currentPage = tempPage 
                
                // 2. FALLBACK: If Trending is dry, fetch from Subscriptions
                if (newVideos.size < 10) {
                    val userSubs = subscriptionRepository.getAllSubscriptionIds()
                    if (userSubs.isNotEmpty()) {
                        val subsBatch = repository.getSubscriptionFeed(userSubs.toList())
                        val filteredSubs = subsBatch.filter { !it.isShort && ((it.duration > 80) || (it.duration == 0 && it.isLive)) }
                        newVideos.addAll(filteredSubs)
                    }
                }
                
                // 3. RANK & APPEND
                if (newVideos.isNotEmpty()) {
                    val userSubs = subscriptionRepository.getAllSubscriptionIds()
                    val rankedAppend = FlowNeuroEngine.rank(newVideos.distinctBy { it.id }, userSubs)
                    
                    val existingIds = _uiState.value.videos.map { it.id }.toSet()
                    val uniqueNewVideos = rankedAppend.filter { it.id !in existingIds }
                    
                    _uiState.update { state ->
                        state.copy(
                            videos = state.videos + uniqueNewVideos,
                            hasMorePages = currentPage != null || userSubs.isNotEmpty(), // Keep it alive if we have subs
                            isLoadingMore = false
                        )
                    }
                } else {
                    // Truly empty
                    _uiState.update { it.copy(isLoadingMore = false, hasMorePages = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingMore = false) }
            } finally {
                isLoadingMore = false
            }
        }
    }
    
    /**
     * MANUAL REGION LOAD / FALLBACK
     * (Restored for compatibility with Settings Screen)
     */
    fun loadTrendingVideos(region: String = "US") {
        if (_uiState.value.isLoading && _uiState.value.videos.isEmpty()) return
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            try {
                // Simple fetch logic for manual overrides
                val (videos, nextPage) = repository.getTrendingVideos(region, null)
                currentPage = nextPage
                
                updateVideosAndShorts(videos, append = false)
                
                _uiState.update { it.copy(
                    scoredVideos = emptyList(),
                    isLoading = false,
                    hasMorePages = nextPage != null,
                    isFlowFeed = false // Plain trending is NOT a Flow Feed
                )}
            } catch (e: Exception) {
                _uiState.update { it.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load videos"
                ) }
            }
        }
    }

    private suspend fun loadTrendingFallback() {
        // Reuse the logic, but internally
        val region = playerPreferences.trendingRegion.first()
        val (videos, nextPage) = repository.getTrendingVideos(region, null)
        currentPage = nextPage
        
        updateVideosAndShorts(videos, append = false)
        _uiState.update { it.copy(
            scoredVideos = emptyList(),
            isLoading = false,
            hasMorePages = nextPage != null,
            isFlowFeed = false,
            error = null
        )}
    }
    
    fun refreshFeed() {
        _uiState.update { it.copy(isRefreshing = true) }
        loadFlowFeed(forceRefresh = true)
    }
    
    fun retry() {
        loadFlowFeed(forceRefresh = true)
    }
}

data class HomeUiState(
    val videos: List<Video> = emptyList(),
    val shorts: List<Video> = emptyList(),
    val scoredVideos: List<ScoredVideo> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null,
    val isFlowFeed: Boolean = false,
    val lastRefreshTime: Long = 0L
)