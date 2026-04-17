package io.github.aedev.flow.ui.screens.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.shorts.ShortsRepository
import io.github.aedev.flow.ui.components.FeedInvalidationBus
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.Page

import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val subscriptionRepository: SubscriptionRepository, 
    private val shortsRepository: ShortsRepository,
    private val playerPreferences: io.github.aedev.flow.data.local.PlayerPreferences
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var currentPage: Page? = null
    private var isLoadingMore = false
    private var isInitialized = false
    
    private var currentQueryIndex = 0
    private val discoveryQueries = mutableListOf<String>()
    
    private var viewHistory: ViewHistory? = null
    
    private val sessionWatchedTopics = mutableListOf<String>()

    // Video IDs the user has watched >=90 % — excluded from recommendations.
    private val watchedVideoIds = MutableStateFlow<Set<String>>(emptySet())
    
    init {
        if (HomeFeedCache.isFresh()) {
            _uiState.update {
                it.copy(
                    videos = HomeFeedCache.videos,
                    shorts = HomeFeedCache.shorts,
                    isLoading = false,
                    isFlowFeed = true,
                    lastRefreshTime = HomeFeedCache.timestamp
                )
            }
        } else {
            loadFlowFeed(forceRefresh = true)
            loadHomeShorts()
        }
    }
    

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        
        viewHistory = ViewHistory.getInstance(context)
        
        // Keep the watched-IDs set up to date so the feed can filter them out.
        // When hideWatchedVideos is ON: filter ALL history items from the feed.
        // When OFF: keep current behaviour (only >=90% watched are excluded).
        viewModelScope.launch {
            viewHistory!!.getVideoHistoryFlow()
                .combine(playerPreferences.hideWatchedVideos) { history, hideWatched ->
                    if (hideWatched) {
                        history.map { it.videoId }.toHashSet()
                    } else {
                        history.filter { it.progressPercentage >= 90f }
                            .map { it.videoId }
                            .toHashSet()
                    }
                }
                .collect { ids -> watchedVideoIds.value = ids }
        }
        
        viewModelScope.launch {
            FlowNeuroEngine.initialize(context)
        }

        viewModelScope.launch {
            FeedInvalidationBus.events.collect { event ->
                when (event) {
                    is FeedInvalidationBus.Event.ChannelBlocked -> {
                        HomeFeedCache.filterOut(channelId = event.channelId)
                        _uiState.update { state ->
                            state.copy(
                                videos = state.videos.filter { it.channelId != event.channelId },
                                shorts = state.shorts.filter { it.channelId != event.channelId }
                            )
                        }
                        // Targeted eviction — preserves other channel caches in discovery engine
                        shortsRepository.evictChannel(event.channelId)
                    }
                    is FeedInvalidationBus.Event.NotInterested -> {
                        HomeFeedCache.filterOut(videoId = event.videoId)
                        _uiState.update { state ->
                            state.copy(
                                videos = state.videos.filter { it.id != event.videoId }
                            )
                        }
                        // Full clear — topic signals changed, discovery queries will differ
                        shortsRepository.clearCaches()
                    }
                    is FeedInvalidationBus.Event.MarkedWatched -> {
                        HomeFeedCache.filterOut(videoId = event.videoId)
                        _uiState.update { state ->
                            state.copy(
                                videos = state.videos.filter { it.id != event.videoId }
                            )
                        }
                    }
                }
            }
        }

        viewModelScope.launch {
            playerPreferences.homeShortsShelfEnabled.collect { enabled ->
                if (!enabled) {
                    _uiState.update { it.copy(shorts = emptyList()) }
                } else if (_uiState.value.shorts.isEmpty()) {
                    loadHomeShorts()
                }
            }
        }

        viewModelScope.launch {
            playerPreferences.continueWatchingEnabled.collect { enabled ->
                if (!enabled) {
                    _uiState.update { it.copy(continueWatchingVideos = emptyList()) }
                } else {
                    loadContinueWatching()
                }
            }
        }
    }

    private fun loadContinueWatching() {
        viewModelScope.launch {
            viewHistory?.getVideoHistoryFlow()?.collect { history ->
                val inProgress = history
                    .filter { it.progressPercentage in 3f..90f }
                    .sortedByDescending { it.timestamp }
                    .take(20)
                _uiState.update { it.copy(continueWatchingVideos = inProgress) }
            }
        }
    }
    

    private fun loadHomeShorts() {
        viewModelScope.launch {
            if (!playerPreferences.homeShortsShelfEnabled.first()) return@launch
            try {
                val shorts = shortsRepository.getHomeFeedShorts().map { it.toVideo() }
                if (shorts.isNotEmpty()) {
                    _uiState.update { it.copy(shorts = shorts) }
                }
            } catch (e: Exception) {
            }
        }
    }
    

    private fun updateVideosAndShorts(newVideos: List<Video>, append: Boolean = false) {
        val (newShorts, regularVideos) = newVideos.partition { 
            it.isShort || (it.duration in 1..120) || (it.duration == 0 && !it.isLive)
        }
        
        _uiState.update { state ->
            val updatedVideos = if (append) (state.videos + regularVideos) else regularVideos
            state.copy(
                videos = updatedVideos.distinctBy { it.id },
                shorts = (state.shorts + newShorts).distinctBy { it.id }
            )
        }
    }

    
    fun loadFlowFeed(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !forceRefresh) return
        
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                discoveryQueries.clear()
                discoveryQueries.addAll(FlowNeuroEngine.generateDiscoveryQueries())
                currentQueryIndex = 0
                
                val userSubs = subscriptionRepository.getAllSubscriptionIds()
                val region = playerPreferences.trendingRegion.first()

                val results = supervisorScope {
                    val deferredSubs = async {
                        if (userSubs.isNotEmpty()) {
                            runCatching { 
                                repository.getSubscriptionFeed(userSubs.toList())
                            }.getOrElse { emptyList() }
                        } else emptyList()
                    }

                    val deferredDiscovery = async {
                        val queries = discoveryQueries.take(3)
                        queries.map { query ->
                            async { 
                                runCatching { 
                                    repository.searchVideos(query).first
                                }.getOrElse { emptyList() }
                            }
                        }.awaitAll().flatten()
                    }
                    
                    val deferredViral = async {
                        runCatching {
                             repository.getTrendingVideos(region).first
                        }.getOrElse { emptyList() }
                    }

                    // ── Fast first paint ────────────────────────────────────────
                    val viralResult = deferredViral.await()
                    if (viralResult.isNotEmpty()) {
                        val watched = watchedVideoIds.value
                        val quickFeed = FlowNeuroEngine.rank(
                            viralResult.filterValid().filterWatched(watched), userSubs
                        ).take(15)
                        if (quickFeed.isNotEmpty()) {
                            _uiState.update { state ->
                                state.copy(
                                    videos = quickFeed,
                                    isLoading = true,
                                    isFlowFeed = true
                                )
                            }
                        }
                    }

                    Triple(deferredSubs.await(), deferredDiscovery.await(), viralResult)
                }
                
                currentQueryIndex = 3
                
                val (rawSubs, rawDiscovery, rawViral) = results

                val subAvatarMap: Map<String, String> = runCatching {
                    subscriptionRepository.getAllSubscriptions().first()
                        .filter { it.channelThumbnail.isNotEmpty() }
                        .associate { it.channelId to it.channelThumbnail }
                }.getOrElse { emptyMap() }

                fun List<Video>.enrichAvatars(): List<Video> =
                    if (subAvatarMap.isEmpty()) this
                    else map { v ->
                        if (v.channelThumbnailUrl.isEmpty() && subAvatarMap.containsKey(v.channelId))
                            v.copy(channelThumbnailUrl = subAvatarMap.getValue(v.channelId))
                        else v
                    }

                // Extract shorts from all sources for the shelf, ranked by FlowNeuro
                val feedShorts = (rawSubs.extractShorts() + rawDiscovery.extractShorts() + rawViral.extractShorts())
                    .distinctBy { it.id }
                if (feedShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    val rankedShorts = FlowNeuroEngine.rank(feedShorts, userSubs)
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + rankedShorts).distinctBy { it.id })
                    }
                }
                
                // Filter to regular videos for the main feed
                val watched = watchedVideoIds.value
                val subsPool = rawSubs.filterValid().filterWatched(watched).enrichAvatars()
                val discoveryPool = rawDiscovery.filterValid().filterWatched(watched)
                val viralPool = rawViral.filterValid().filterWatched(watched)
                
                val bestSubs = FlowNeuroEngine.rank(subsPool, userSubs).take(10)
                val bestDiscovery = FlowNeuroEngine.rank(discoveryPool, userSubs)
                    .filter { video ->
                        val isOld = video.uploadDate.contains("year") && 
                                    (video.uploadDate.filter { it.isDigit() }.toIntOrNull() ?: 0) > 4
                        
                        val isClassic = video.viewCount > 5_000_000 
                        
                        !isOld || isClassic
                    }
                    .take(15) 
                val bestViral = FlowNeuroEngine.rank(viralPool, userSubs).take(10)

                val finalMix = mutableListOf<Video>()
                val usedChannelIds = mutableSetOf<String>()
                
                val qSubs = java.util.ArrayDeque(bestSubs)
                val qDisc = java.util.ArrayDeque(bestDiscovery)
                val qViral = java.util.ArrayDeque(bestViral)
                
                while (qSubs.isNotEmpty() || qDisc.isNotEmpty() || qViral.isNotEmpty()) {
                    addUnique(qSubs.pollFirst(), finalMix, usedChannelIds)
                    
                    addUnique(qDisc.pollFirst(), finalMix, usedChannelIds)
                    
                    addUnique(qSubs.pollFirst(), finalMix, usedChannelIds)
                    
                    addUnique(qViral.pollFirst(), finalMix, usedChannelIds)
                }

                if (finalMix.isEmpty()) {
                   loadTrendingFallback()
                   return@launch
                }

                val now = System.currentTimeMillis()
                _uiState.update { it.copy(
                    videos = finalMix, 
                    isLoading = false,
                    isRefreshing = false,
                    hasMorePages = true,
                    isFlowFeed = true,
                    lastRefreshTime = now
                )}
                HomeFeedCache.update(finalMix, _uiState.value.shorts)
                
            } catch (e: Exception) {
                 _uiState.update { it.copy(isLoading = false, isRefreshing = false, error = "Failed to load feed") }
                 loadTrendingFallback() 
            }
        }
    }
    

    fun loadMoreVideos() {
        if (isLoadingMore) return
        
        isLoadingMore = true
        _uiState.update { it.copy(isLoadingMore = true) }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {

                if (currentQueryIndex >= discoveryQueries.size) {
                    discoveryQueries.addAll(FlowNeuroEngine.generateDiscoveryQueries())
                }
                
                val queryA = discoveryQueries.getOrNull(currentQueryIndex++)
                val queryB = discoveryQueries.getOrNull(currentQueryIndex++)
                
                val searchQueries = listOfNotNull(queryA, queryB)
                
                val finalQueries = if (searchQueries.isEmpty()) listOf("Viral") else searchQueries

                val rawVideos = finalQueries.map { q ->
                   async { 
                       runCatching {
                           repository.searchVideos(q).first
                       }.getOrElse { emptyList() }
                   }
                }.awaitAll().flatten()
                
                // Extract shorts for shelf — rank through FlowNeuro
                val moreShorts = rawVideos.extractShorts()
                if (moreShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    val subs = subscriptionRepository.getAllSubscriptionIds()
                    val rankedMore = FlowNeuroEngine.rank(moreShorts, subs)
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + rankedMore).distinctBy { it.id })
                    }
                }
                
                val newVideos = rawVideos.filterValid().filterWatched(watchedVideoIds.value)

                
                if (newVideos.isNotEmpty()) {
                    val userSubs = subscriptionRepository.getAllSubscriptionIds()
                    val rankedBatch = FlowNeuroEngine.rank(newVideos, userSubs)
                                        .shuffled()
                                        .distinctBy { it.channelId } 
                    
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
    

    fun loadTrendingVideos() {
        if (_uiState.value.isLoading && _uiState.value.videos.isEmpty()) return
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val region = playerPreferences.trendingRegion.first()
                val (videos, nextPage) = repository.getTrendingVideos(region, null)
                currentPage = nextPage

                val userSubs = subscriptionRepository.getAllSubscriptionIds()
                val ranked = FlowNeuroEngine.rank(videos, userSubs)
                updateVideosAndShorts(ranked, append = false)

                _uiState.update { it.copy(
                    isLoading = false,
                    hasMorePages = nextPage != null,
                    isFlowFeed = false
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
        val region = playerPreferences.trendingRegion.first()
        val (videos, nextPage) = repository.getTrendingVideos(region, null)
        currentPage = nextPage

        val userSubs = subscriptionRepository.getAllSubscriptionIds()
        val ranked = FlowNeuroEngine.rank(videos, userSubs)
        updateVideosAndShorts(ranked, append = false)
        _uiState.update { it.copy(
            isLoading = false,
            hasMorePages = nextPage != null,
            isFlowFeed = false,
            error = null
        )}
    }
    
    fun refreshFeed() {
        HomeFeedCache.clear()
        _uiState.update { it.copy(isRefreshing = true) }
        loadFlowFeed(forceRefresh = true)
    }
    
    fun retry() {
        loadFlowFeed(forceRefresh = true)
    }


    private fun addUnique(
        video: Video?, 
        targetList: MutableList<Video>, 
        usedChannels: MutableSet<String>
    ) {
        if (video == null) return
        

        if (!usedChannels.contains(video.channelId)) {
            targetList.add(video)
            usedChannels.add(video.channelId)
        }
    }
    
    private fun List<Video>.filterValid(): List<Video> {
        return this.filter { 
            !it.isShort && 
            ((it.duration > 120) || (it.duration == 0 && it.isLive)) 
        }
    }
    
    /**
     * Filter that extracts shorts from a video list for the shelf.
     * Complements filterValid() by capturing what it discards.
     */
    private fun List<Video>.extractShorts(): List<Video> {
        return this.filter { 
            it.isShort || (it.duration in 1..120 && !it.isLive)
        }
    }

    /**
     * Remove videos the user has already fully watched (≥90 % progress)
     * so they don't re-appear in the home feed.
     */
    private fun List<Video>.filterWatched(watchedIds: Set<String>): List<Video> {
        if (watchedIds.isEmpty()) return this
        return this.filter { !watchedIds.contains(it.id) }
    }
}

/**
 * Process-lifetime in-memory cache for the Home feed.
 *
 * Survives ViewModel recreation (which happens when the user navigates away
 * from Home and comes back via the bottom nav), preventing an unwanted
 * network reload on every tab switch. The cache expires after [CACHE_TTL_MS]
 * (default 30 minutes) and is explicitly cleared when the user pulls-to-refresh.
 */
internal object HomeFeedCache {
    private const val CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes

    @Volatile var videos: List<Video> = emptyList()
        private set
    @Volatile var shorts: List<Video> = emptyList()
        private set
    @Volatile var timestamp: Long = 0L
        private set

    fun isFresh(): Boolean =
        videos.isNotEmpty() && (System.currentTimeMillis() - timestamp) < CACHE_TTL_MS

    fun update(newVideos: List<Video>, newShorts: List<Video>) {
        videos = newVideos
        shorts = newShorts
        timestamp = System.currentTimeMillis()
    }

    fun clear() {
        videos = emptyList()
        shorts = emptyList()
        timestamp = 0L
    }

    /**
     * Remove videos by blocked channel/topic from the cached feed without
     * requiring a network refetch, keeping the cache TTL alive.
     */
    fun filterOut(channelId: String? = null, videoId: String? = null) {
        if (channelId != null) {
            videos = videos.filter { it.channelId != channelId }
            shorts = shorts.filter { it.channelId != channelId }
        }
        if (videoId != null) {
            videos = videos.filter { it.id != videoId }
            shorts = shorts.filter { it.id != videoId }
        }
    }
}

data class HomeUiState(
    val videos: List<Video> = emptyList(),
    val shorts: List<Video> = emptyList(),
    val continueWatchingVideos: List<io.github.aedev.flow.data.local.VideoHistoryEntry> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isRefreshing: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null,
    val isFlowFeed: Boolean = false,
    val lastRefreshTime: Long = 0L
)