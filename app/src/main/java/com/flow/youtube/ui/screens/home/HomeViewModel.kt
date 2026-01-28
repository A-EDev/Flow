package com.flow.youtube.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.data.model.Video
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
    private val subscriptionRepository: SubscriptionRepository, 
    private val shortsRepository: ShortsRepository,
    private val playerPreferences: com.flow.youtube.data.local.PlayerPreferences
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var currentPage: Page? = null
    private var isLoadingMore = false
    private var isInitialized = false
    
    // NEW: Infinite Feed State
    private var currentQueryIndex = 0
    private val discoveryQueries = mutableListOf<String>()
    
    // NEW: ViewHistory for recursive related videos (Strategy 3)
    private var viewHistory: ViewHistory? = null
    
    // NEW: Session fatigue tracking - tracks primary topics of recently displayed videos
    private val sessionWatchedTopics = mutableListOf<String>()
    
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
        
        // Initialize ViewHistory for recursive related videos
        viewHistory = ViewHistory.getInstance(context)
        
        viewModelScope.launch {
            FlowNeuroEngine.initialize(context)
        }
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
     * Uses "Parallel Fetch" to create a massive initial pool (~60-100 videos)
     * 
     * Now includes:
     * - Bridge queries (topic combinations)
     * - Persona-based suffixes
     * - Anti-gravity exploration
     * - Recursive related videos from watch history (Strategy 3)
     */
    fun loadFlowFeed(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !forceRefresh) return
        
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch {
            try {
                // 1. Generate Brain Queries (now includes Bridge, Persona, Anti-Gravity)
                discoveryQueries.clear()
                discoveryQueries.addAll(FlowNeuroEngine.generateDiscoveryQueries())
                currentQueryIndex = 0
                
                val userSubs = subscriptionRepository.getAllSubscriptionIds()
                val region = "US" // Default to US for broader trending, or use user pref

                // 2. PARALLEL FETCH (The "Wall of Content")
                // We launch multiple async tasks to get content from everywhere at once.
                
                val deferredSubs = async { 
                    try {
                        if (userSubs.isNotEmpty()) {
                            val raw = repository.getSubscriptionFeed(userSubs.toList())
                            raw.filter { !it.isShort && ((it.duration > 80) || (it.duration == 0 && it.isLive)) }
                        } else emptyList()
                    } catch (e: Exception) { emptyList() }
                }

                val deferredQ1 = async {
                    try {
                        val q = discoveryQueries.getOrNull(0) ?: "Science"
                        repository.searchVideos(q).first
                            .filter { !it.isShort && ((it.duration > 80) || (it.duration == 0 && it.isLive)) }
                    } catch (e: Exception) { emptyList() }
                }

                val deferredQ2 = async {
                    try {
                        val q = discoveryQueries.getOrNull(1) ?: "Gaming"
                        repository.searchVideos(q).first
                             .filter { !it.isShort && ((it.duration > 80) || (it.duration == 0 && it.isLive)) }
                    } catch (e: Exception) { emptyList() }
                }
                
                val deferredTrending = async {
                    try {
                         // Still fetch trending as a solid base
                         repository.getTrendingVideos(region).first
                             .filter { !it.isShort && ((it.duration > 80) || (it.duration == 0 && it.isLive)) }
                    } catch (e: Exception) { emptyList() }
                }
                
                // ============================================================
                // STRATEGY 3: RECURSIVE RELATED VIDEOS (The "Infinite Content" Booster)
                // Fetch related videos from the user's watch history
                // ============================================================
                val deferredRelated = async {
                    try {
                        val history = viewHistory?.getAllHistory()?.first() ?: emptyList()
                        if (history.isNotEmpty()) {
                            // Get related videos from the most recently watched video
                            val lastWatchedId = history.first().videoId
                            repository.getRelatedVideos(lastWatchedId)
                                .filter { !it.isShort && ((it.duration > 80) || (it.duration == 0 && it.isLive)) }
                                .take(15) // Limit to prevent overloading
                        } else emptyList()
                    } catch (e: Exception) { emptyList() }
                }

                // 3. Await All
                val subsVideos = deferredSubs.await()
                val q1Videos = deferredQ1.await()
                val q2Videos = deferredQ2.await()
                val trendingVideos = deferredTrending.await()
                val relatedVideos = deferredRelated.await()
                
                // Bump index since we used 0 and 1
                currentQueryIndex = 2
                
                // Combine ALL sources including recursive related videos
                val rawPool = (subsVideos + q1Videos + q2Videos + trendingVideos + relatedVideos).distinctBy { it.id }
                
                if (rawPool.isEmpty()) {
                    loadTrendingFallback()
                    return@launch
                }
                
                // 4. RANKING with session fatigue
                // Pass recently watched topics to prevent repetition
                val rankedVideos = FlowNeuroEngine.rank(
                    candidates = rawPool, 
                    userSubs = userSubs,
                    lastWatchedTopics = sessionWatchedTopics.takeLast(10)
                )
                
                // 5. UPDATE UI
                _uiState.update { it.copy(
                    videos = rankedVideos, 
                    isLoading = false,
                    isRefreshing = false,
                    hasMorePages = true,
                    isFlowFeed = true,
                    lastRefreshTime = System.currentTimeMillis()
                )}
                
            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = "Failed to load feed") }
                 loadTrendingFallback() 
            }
        }
    }
    
    /**
     * INFINITE SCROLL LOADER (The "Keep Digging" Fix)
     * Fetches NEW topics instead of just paging old ones.
     */
    fun loadMoreVideos() {
        if (isLoadingMore) return
        
        isLoadingMore = true
        _uiState.update { it.copy(isLoadingMore = true) }
        
        viewModelScope.launch {
            try {
                // 1. Get next query
                val nextQuery = discoveryQueries.getOrNull(currentQueryIndex++) ?: run {
                    // Refill if empty
                    discoveryQueries.addAll(FlowNeuroEngine.generateDiscoveryQueries())
                     discoveryQueries.getOrNull(0) ?: "Viral"
                }

                // 2. Fetch
                val (newVideos, _) = repository.searchVideos(nextQuery)
                val filtered = newVideos.filter { !it.isShort && ((it.duration > 80) || (it.duration == 0 && it.isLive)) }
                
                if (filtered.isNotEmpty()) {
                    // 3. Rank New Batch
                    val userSubs = subscriptionRepository.getAllSubscriptionIds()
                    val rankedBatch = FlowNeuroEngine.rank(filtered.distinctBy { it.id }, userSubs)
                    
                    // 4. Append
                    _uiState.update { state ->
                        val currentIds = state.videos.map { it.id }.toHashSet()
                        val uniqueNew = rankedBatch.filter { !currentIds.contains(it.id) }
                        state.copy(
                            videos = state.videos + uniqueNew,
                            isLoadingMore = false,
                            hasMorePages = true
                        )
                    }
                } else {
                    _uiState.update { it.copy(isLoadingMore = false) }
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
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null,
    val isFlowFeed: Boolean = false,
    val lastRefreshTime: Long = 0L
)