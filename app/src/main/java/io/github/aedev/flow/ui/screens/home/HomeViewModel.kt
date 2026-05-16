package io.github.aedev.flow.ui.screens.home

import android.content.Context
import android.util.Log
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
    companion object {
        private const val TAG = "HomeViewModel"
        private const val HOME_TARGET_SIZE = 40
        private const val FRESH_SUB_WINDOW_MS = 72L * 60L * 60L * 1000L
        private const val HOME_MAX_SUGGESTION_AGE_MS = 365L * 24L * 60L * 60L * 1000L
    }

    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var currentPage: Page? = null
    private var isLoadingMore = false
    private var isInitialized = false
    
    private var currentQueryIndex = 0
    private val discoveryQueries = mutableListOf<String>()
    private var wave2Job: kotlinx.coroutines.Job? = null
    
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
        // When hideWatchedVideos is ON: filter videos watched at least 10%.
        // When OFF: keep current behaviour (only >=90% watched are excluded).
        viewModelScope.launch {
            viewHistory!!.getVideoHistoryFlow()
                .combine(playerPreferences.hideWatchedVideos) { history, hideWatched ->
                    if (hideWatched) {
                        history.filter { it.progressPercentage >= 10f }
                            .map { it.videoId }
                            .toHashSet()
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
                                videos = state.videos.filter { it.id != event.videoId },
                                shorts = state.shorts.filter { it.id != event.videoId }
                            )
                        }
                        // Full clear — topic signals changed, discovery queries will differ
                        shortsRepository.clearCaches()
                    }
                    is FeedInvalidationBus.Event.MarkedWatched -> {
                        HomeFeedCache.filterOut(videoId = event.videoId)
                        _uiState.update { state ->
                            state.copy(
                                videos = state.videos.filter { it.id != event.videoId },
                                shorts = state.shorts.filter { it.id != event.videoId }
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
                    .filter { !it.isShort && it.progressPercentage in 3f..90f }
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
                    .sortedByDescending { it.timestamp }
            )
        }
    }

    
    fun loadFlowFeed(forceRefresh: Boolean = false) {
        if (_uiState.value.isLoading && !forceRefresh) return
        
        wave2Job?.cancel()
        _uiState.update { it.copy(isLoading = true, error = null) }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                discoveryQueries.clear()
                discoveryQueries.addAll(FlowNeuroEngine.generateDiscoveryQueries())
                currentQueryIndex = 0
                
                val userSubs = subscriptionRepository.getAllSubscriptionIds()
                val region = playerPreferences.trendingRegion.first()
                val fetchStart = System.currentTimeMillis()

                // ── Wave 1: first 3 queries + subs + trending ──
                val wave1QueryCount = discoveryQueries.size.coerceAtMost(3)
                val wave1Queries = discoveryQueries.take(wave1QueryCount)
                currentQueryIndex = wave1QueryCount

                val results = supervisorScope {
                    val deferredSubs = async {
                        if (userSubs.isNotEmpty()) {
                            withTimeoutOrNull(8_000L) {
                                runCatching {
                                    repository.getSubscriptionFeed(userSubs.toList())
                                }.getOrElse { emptyList() }
                            } ?: emptyList()
                        } else emptyList()
                    }

                    val deferredDiscovery = async {
                        wave1Queries.map { query ->
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
                    if (viralResult.isNotEmpty() && userSubs.isEmpty()) {
                        val watched = watchedVideoIds.value
                        val quickFeed = FlowNeuroEngine.rank(
                            viralResult.filterValid()
                                .filterWatched(watched)
                                .filterRecentHomeSuggestion(System.currentTimeMillis()),
                            userSubs
                        ).take(15)
                        if (quickFeed.isNotEmpty()) {
                            _uiState.update { state ->
                                state.copy(
                                    videos = quickFeed,
                                    isLoading = true,
                                    isFlowFeed = true
                                )
                            }
                            FlowNeuroEngine.recordFeedImpressions(quickFeed)
                        }
                    }

                    Triple(deferredSubs.await(), deferredDiscovery.await(), viralResult)
                }
                
                val (rawSubs, rawDiscovery, rawViral) = results

                Log.d(TAG, "Wave 1 fetch completed in ${System.currentTimeMillis() - fetchStart}ms")

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
                val now = System.currentTimeMillis()

                val feedShorts = (rawSubs.extractShorts() + rawDiscovery.extractShorts() + rawViral.extractShorts())
                    .distinctBy { it.id }
                    .filterWatched(watchedVideoIds.value)
                    .filterRecentHomeSuggestion(now)
                if (feedShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    val rankedShorts = FlowNeuroEngine.rank(feedShorts, userSubs)
                    val rankIndex = rankedShorts.mapIndexed { index, video -> video.id to index }.toMap()
                    val latestShorts = feedShorts.sortedWith(
                        compareByDescending<Video> { it.timestamp }
                            .thenBy { rankIndex[it.id] ?: Int.MAX_VALUE }
                    )
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + latestShorts).distinctBy { it.id }
                            .sortedByDescending { it.timestamp })
                    }
                    FlowNeuroEngine.recordFeedImpressions(rankedShorts)
                }
                
                // Filter to regular videos for the main feed
                val watched = watchedVideoIds.value
                val subsPool = rawSubs.filterValid().filterWatched(watched).enrichAvatars()
                val discoveryPool = rawDiscovery.filterValid().filterWatched(watched)
                    .filterRecentHomeSuggestion(now)
                val viralPool = rawViral.filterValid().filterWatched(watched)
                    .filterRecentHomeSuggestion(now)

                Log.d(
                    TAG,
                    "Flow candidates: subs=${subsPool.size}, discovery=${discoveryPool.size}, viral=${viralPool.size}, subCount=${userSubs.size}"
                )

                val rankedSubs = FlowNeuroEngine.rank(subsPool, userSubs)
                val freshSlotTarget = dynamicFreshSubSlots(userSubs.size)
                val freshSubsLane = rankedSubs
                    .filter { isFreshSubscribedCandidate(it, now) }
                    .take(freshSlotTarget)
                val freshIds = freshSubsLane.map { it.id }.toHashSet()

                val bestSubs = rankedSubs
                    .filter { !freshIds.contains(it.id) }
                    .take(15)

                val bestDiscovery = FlowNeuroEngine.rank(discoveryPool, userSubs).take(15)
                val bestViral = FlowNeuroEngine.rank(viralPool, userSubs).take(6)

                val finalMix = mutableListOf<Video>()
                val usedChannelCounts = mutableMapOf<String, Int>()
                val usedVideoIds = mutableSetOf<String>()

                freshSubsLane.forEach { video ->
                    addUnique(video, finalMix, usedChannelCounts, usedVideoIds)
                }

                val remaining = (HOME_TARGET_SIZE - finalMix.size).coerceAtLeast(0)
                val subsQuota = (remaining * 0.50).toInt().coerceAtLeast(0)
                val discoveryQuota = (remaining * 0.40).toInt().coerceAtLeast(0)
                val viralQuota = (remaining - subsQuota - discoveryQuota).coerceAtLeast(0)
                
                val qSubs = java.util.ArrayDeque(bestSubs)
                val qDisc = java.util.ArrayDeque(bestDiscovery)
                val qViral = java.util.ArrayDeque(bestViral)

                var subsAdded = 0
                var discoveryAdded = 0
                var viralAdded = 0
                
                while (
                    finalMix.size < HOME_TARGET_SIZE &&
                    (qSubs.isNotEmpty() || qDisc.isNotEmpty() || qViral.isNotEmpty())
                ) {
                    var addedThisRound = false

                    if (subsAdded < subsQuota && addUnique(qSubs.pollFirst(), finalMix, usedChannelCounts, usedVideoIds)) {
                        subsAdded++
                        addedThisRound = true
                    }

                    if (discoveryAdded < discoveryQuota && addUnique(qDisc.pollFirst(), finalMix, usedChannelCounts, usedVideoIds)) {
                        discoveryAdded++
                        addedThisRound = true
                    }

                    if (viralAdded < viralQuota && addUnique(qViral.pollFirst(), finalMix, usedChannelCounts, usedVideoIds)) {
                        viralAdded++
                        addedThisRound = true
                    }

                    if (!addedThisRound) {
                        val forced = addUnique(qSubs.pollFirst(), finalMix, usedChannelCounts, usedVideoIds) ||
                            addUnique(qDisc.pollFirst(), finalMix, usedChannelCounts, usedVideoIds) ||
                            addUnique(qViral.pollFirst(), finalMix, usedChannelCounts, usedVideoIds)
                        if (!forced) break
                    }
                }

                if (finalMix.size < HOME_TARGET_SIZE) {
                    val fallback = bestSubs + bestDiscovery + bestViral
                    fallback.forEach { video ->
                        if (finalMix.size >= HOME_TARGET_SIZE) return@forEach
                        addUnique(video, finalMix, usedChannelCounts, usedVideoIds)
                    }
                }

                if (finalMix.isEmpty()) {
                   loadTrendingFallback()
                   return@launch
                }

                Log.d(
                    TAG,
                    "Flow mix: freshLane=${freshSubsLane.size}, final=${finalMix.size}, quotas=s:$subsQuota d:$discoveryQuota v:$viralQuota"
                )

                _uiState.update { it.copy(
                    videos = finalMix, 
                    isLoading = false,
                    isRefreshing = false,
                    hasMorePages = true,
                    isFlowFeed = true,
                    lastRefreshTime = now
                )}
                HomeFeedCache.update(finalMix, _uiState.value.shorts)
                FlowNeuroEngine.recordFeedImpressions(finalMix)

                // ── Wave 2: remaining queries loaded in background ──
                val wave2Queries = discoveryQueries.drop(currentQueryIndex)
                if (wave2Queries.isNotEmpty()) {
                    val wave2FinalMixIds = finalMix.map { it.id }.toHashSet()
                    wave2Job = viewModelScope.launch(PerformanceDispatcher.networkIO) wave2@{
                        try {
                            val wave2Raw = wave2Queries.map { q ->
                                async {
                                    withTimeoutOrNull(6_000L) {
                                        runCatching { repository.searchVideos(q).first }.getOrElse { emptyList() }
                                    } ?: emptyList()
                                }
                            }.awaitAll().flatten()

                            val wave2Watched = watchedVideoIds.value
                            val wave2Valid = wave2Raw.filterValid().filterWatched(wave2Watched)
                                .filter { !wave2FinalMixIds.contains(it.id) }
                            if (wave2Valid.isEmpty()) return@wave2

                            val wave2Ranked = FlowNeuroEngine.rank(wave2Valid, userSubs)
                                .take(15)

                            if (wave2Ranked.isNotEmpty()) {
                                _uiState.update { state ->
                                    val currentIds = state.videos.map { it.id }.toHashSet()
                                    val uniqueNew = wave2Ranked.filter { !currentIds.contains(it.id) }
                                        .distinctBy { it.channelId }
                                    if (uniqueNew.isEmpty()) return@update state
                                    val updated = state.videos + uniqueNew
                                    HomeFeedCache.update(updated, state.shorts)
                                    state.copy(videos = updated)
                                }
                                FlowNeuroEngine.recordFeedImpressions(wave2Ranked)
                                currentQueryIndex = discoveryQueries.size
                                Log.d(TAG, "Wave 2 merged ${wave2Ranked.size} extra candidates")
                            }
                        } catch (e: Exception) {
                            Log.d(TAG, "Wave 2 failed: ${e.message}")
                        }
                    }
                }
                
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
                       withTimeoutOrNull(6_000L) {
                           runCatching {
                               repository.searchVideos(q).first
                           }.getOrElse { emptyList() }
                       } ?: emptyList()
                   }
                }.awaitAll().flatten()
                
                // Extract shorts for shelf — rank through FlowNeuro
                val moreShorts = rawVideos.extractShorts()
                    .filterWatched(watchedVideoIds.value)
                    .filterRecentHomeSuggestion(System.currentTimeMillis())
                if (moreShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    val subs = subscriptionRepository.getAllSubscriptionIds()
                    val rankedMore = FlowNeuroEngine.rank(moreShorts, subs)
                    val rankIndex = rankedMore.mapIndexed { index, video -> video.id to index }.toMap()
                    val latestMore = moreShorts.sortedWith(
                        compareByDescending<Video> { it.timestamp }
                            .thenBy { rankIndex[it.id] ?: Int.MAX_VALUE }
                    )
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + latestMore).distinctBy { it.id }
                            .sortedByDescending { it.timestamp })
                    }
                    FlowNeuroEngine.recordFeedImpressions(rankedMore)
                }
                
                val newVideos = rawVideos.filterValid()
                    .filterWatched(watchedVideoIds.value)
                    .filterRecentHomeSuggestion(System.currentTimeMillis())

                
                if (newVideos.isNotEmpty()) {
                    val userSubs = subscriptionRepository.getAllSubscriptionIds()
                    val rankedBatch = FlowNeuroEngine.rank(newVideos, userSubs)
                                        .shuffled()
                                        .distinctBy { it.channelId } 
                    val currentIds = _uiState.value.videos.map { it.id }.toHashSet()
                    val uniqueNew = rankedBatch.filter { !currentIds.contains(it.id) }

                    _uiState.update { state ->
                        state.copy(
                            videos = state.videos + uniqueNew,
                            isLoadingMore = false,
                            hasMorePages = true
                        )
                    }
                    FlowNeuroEngine.recordFeedImpressions(uniqueNew)
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
                val ranked = FlowNeuroEngine.rank(
                    videos.filterRecentHomeSuggestion(System.currentTimeMillis()),
                    userSubs
                )
                updateVideosAndShorts(ranked, append = false)
                FlowNeuroEngine.recordFeedImpressions(ranked)

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
        val ranked = FlowNeuroEngine.rank(
            videos.filterRecentHomeSuggestion(System.currentTimeMillis()),
            userSubs
        )
        updateVideosAndShorts(ranked, append = false)
        FlowNeuroEngine.recordFeedImpressions(ranked)
        _uiState.update { it.copy(
            isLoading = false,
            hasMorePages = nextPage != null,
            isFlowFeed = false,
            error = null
        )}
    }
    
    fun refreshFeed() {
        wave2Job?.cancel()
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
        channelCounts: MutableMap<String, Int>,
        usedVideoIds: MutableSet<String>,
        maxPerChannel: Int = 2
    ): Boolean {
        if (video == null) return false

        val count = channelCounts[video.channelId] ?: 0
        if (count >= maxPerChannel) return false
        if (!usedVideoIds.add(video.id)) return false
        targetList.add(video)
        channelCounts[video.channelId] = count + 1
        return true
    }

    private fun dynamicFreshSubSlots(subCount: Int): Int {
        return when {
            subCount >= 120 -> 3
            subCount >= 40 -> 2
            else -> 1
        }
    }

    private fun isFreshSubscribedCandidate(video: Video, now: Long): Boolean {
        val ageByTimestamp = now - video.timestamp
        if (ageByTimestamp in 0..FRESH_SUB_WINDOW_MS) return true

        val text = video.uploadDate.lowercase()
        if (text.contains("second") || text.contains("minute") || text.contains("hour")) {
            return true
        }

        if (text.contains("day")) {
            val days = text.filter { it.isDigit() }.toIntOrNull() ?: 1
            return days <= 3
        }

        return false
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

    private fun List<Video>.filterRecentHomeSuggestion(now: Long): List<Video> =
        filter { video -> isRecentHomeSuggestion(video, now) }

    private fun isRecentHomeSuggestion(video: Video, now: Long): Boolean {
        val text = video.uploadDate.lowercase()
        if (text.isBlank() || text == "unknown") return video.isLive

        val age = now - video.timestamp
        if (age in 0..HOME_MAX_SUGGESTION_AGE_MS) return true

        val value = text.filter { it.isDigit() }.toIntOrNull() ?: 1
        return when {
            text.contains("second") || text.contains("minute") || text.contains("hour") -> true
            text.contains("day") -> value <= 365
            text.contains("week") -> value <= 52
            text.contains("month") -> value <= 12
            text.contains("year") -> value <= 1
            else -> false
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
        shorts = newShorts.sortedByDescending { it.timestamp }
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