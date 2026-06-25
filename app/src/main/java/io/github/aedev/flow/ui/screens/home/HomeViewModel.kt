package io.github.aedev.flow.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.data.recommendation.SeedInput
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.VideoHistoryEntry
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.schabi.newpipe.extractor.Page

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Seed candidates for related-graph retrieval: recent, mostly-watched, non-Short videos. */
internal fun seedInputsFrom(history: List<VideoHistoryEntry>, max: Int = 10): List<SeedInput> =
    history.filter { !it.isShort && it.progressPercentage >= 70f }
        .sortedByDescending { it.timestamp }
        .take(max)
        .map { SeedInput(it.videoId, it.title, (it.progressPercentage / 100f).toDouble()) }

/** Keeps only visible grid keys that map to real feed videos (drops shelf/loader keys). */
internal fun feedImpressionIds(visibleKeys: List<String>, knownIds: Set<String>): List<String> =
    visibleKeys.filter { it in knownIds }

/** Saved-interest seed pools: watch history (newest-first), liked videos, and saved playlists. */
internal data class SavedSeedSources(
    val historyIds: List<String>,
    val likedIds: List<String>,
    val playlistIds: List<String>
)

private fun sampleIds(ids: List<String>, range: IntRange, random: kotlin.random.Random): List<String> {
    if (ids.isEmpty() || range.last <= 0) return emptyList()
    val n = random.nextInt(range.first, range.last + 1).coerceAtMost(ids.size)
    return ids.shuffled(random).take(n)
}

/**
 * Picks seed video ids to expand via the related (/next) graph: the latest watched video plus a
 * random sample from history, liked, and saved playlists. Ids on cooldown are excluded.
 */
internal fun selectSavedInterestSeeds(
    sources: SavedSeedSources,
    cooldown: Set<String>,
    random: kotlin.random.Random,
    historyRandom: IntRange = 1..2,
    likedPicks: IntRange = 1..2,
    playlistPicks: IntRange = 1..4,
    maxSeeds: Int = 5
): List<String> {
    val picked = LinkedHashSet<String>()
    val history = sources.historyIds.filterNot { it in cooldown }
    history.firstOrNull()?.let { picked.add(it) } 
    picked += sampleIds(history.drop(1), historyRandom, random)
    picked += sampleIds(sources.likedIds.filterNot { it in cooldown }, likedPicks, random)
    picked += sampleIds(sources.playlistIds.filterNot { it in cooldown }, playlistPicks, random)
    return picked.take(maxSeeds)
}

// Format signals often tied to low-effort feed filler. NOT a blocklist: they only demote
// exploration candidates, and only when the user shows no matching interest.
internal val FEED_FORMAT_MARKERS = listOf(
    "compilation", "satisfying", "hour of", "hours of", "best of",
    "ending explained", "full movie", "full episode", "marathon",
    "movie recap", "series recap", "all parts"
)

// Per-persona long-form comfort. 0 ⇒ no duration demotion (the user watches long content).
internal const val DURATION_COMFORT_DEFAULT_SEC = 3600 // 60 min: generic browse comfort
private const val DURATION_COMFORT_SKIMMER_SEC = 1500 // 25 min: fast-content persona
private const val FIT_PENALTY_WEIGHT = 0.6            // how hard a poor fit demotes engine rank

/** Per-user feed taste, read from the learned brain — drives demotion, never a global ban. */
internal data class FeedTasteProfile(
    val comfortDurationSec: Int,
    val affinityTopics: Set<String>
)

internal fun feedTasteProfile(brain: UserBrain, persona: FlowPersona): FeedTasteProfile {
    val comfort = when (persona) {
        FlowPersona.DEEP_DIVER, FlowPersona.SCHOLAR,
        FlowPersona.BINGER, FlowPersona.AUDIOPHILE -> 0
        FlowPersona.SKIMMER -> DURATION_COMFORT_SKIMMER_SEC
        else -> DURATION_COMFORT_DEFAULT_SEC
    }
    val affinity = (brain.topicAffinities.filterValues { it > 0.0 }.keys + brain.preferredTopics)
        .mapNotNull { it.lowercase().takeIf(String::isNotBlank) }
        .toSet()
    return FeedTasteProfile(comfort, affinity)
}

/** 0 = good fit for this user; →1 = poor fit. Demotes exploration candidates, never drops them. */
internal fun feedFitPenalty(video: Video, profile: FeedTasteProfile): Double {
    var penalty = 0.0
    val cap = profile.comfortDurationSec
    if (cap > 0 && video.duration > cap) {
        val over = (video.duration - cap).toDouble() / cap
        penalty += (0.5 * over).coerceAtMost(0.6)
    }
    val title = video.title.lowercase()
    if (FEED_FORMAT_MARKERS.any { title.contains(it) } &&
        profile.affinityTopics.none { title.contains(it) }
    ) {
        penalty += 0.4
    }
    return penalty.coerceAtMost(1.0)
}

/** Stable re-rank pushing poor-fit items below well-fit ones while preserving engine order. */
internal fun demoteByFit(ranked: List<Video>, profile: FeedTasteProfile): List<Video> {
    if (ranked.size < 2) return ranked
    val n = ranked.size.toDouble()
    return ranked.withIndex()
        .sortedByDescending { (i, v) -> (1.0 - i / n) - FIT_PENALTY_WEIGHT * feedFitPenalty(v, profile) }
        .map { it.value }
}

/**
 * Greedy reorder that keeps same-channel items at least `gap` slots apart when possible; order is
 * otherwise preserved. seedRecent primes the cooldown with the prior page's tail to space appends.
 */
internal fun spaceByChannel(
    videos: List<Video>, gap: Int = 1, seedRecent: List<String> = emptyList()
): List<Video> {
    if (videos.size < 2) return videos
    val remaining = videos.toMutableList()
    val out = ArrayList<Video>(videos.size)
    val recent = ArrayDeque<String>()
    seedRecent.takeLast(gap).forEach { recent.addLast(it) }
    while (remaining.isNotEmpty()) {
        val idx = remaining.indexOfFirst { it.channelId.isBlank() || it.channelId !in recent }
            .let { if (it < 0) 0 else it }
        val pick = remaining.removeAt(idx)
        out.add(pick)
        if (pick.channelId.isNotBlank()) {
            recent.addLast(pick.channelId)
            while (recent.size > gap) recent.removeFirst()
        }
    }
    return out
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val subscriptionRepository: SubscriptionRepository, 
    private val shortsRepository: ShortsRepository,
    private val playerPreferences: io.github.aedev.flow.data.local.PlayerPreferences,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    companion object {
        private const val TAG = "HomeViewModel"
        private const val HOME_TARGET_SIZE = 40
        private const val FRESH_SUB_WINDOW_MS = 72L * 60L * 60L * 1000L
        private const val HOME_MAX_SUGGESTION_AGE_MS = 365L * 24L * 60L * 60L * 1000L
        private const val RELATED_TTL_MS = 45L * 60L * 1000L
        private const val MAX_RELATED_SEEDS = 4
        private const val MIN_PAGE_SIZE = 8
        private const val MAX_SAVED_SEEDS = 5
        private const val SAVED_RELATED_SLOTS = 8
        private const val SAVED_SEED_COOLDOWN_MS = 3L * 60L * 60L * 1000L
    }

    // Saved-interest enrichment sources (history/liked/playlists) + per-seed cooldown.
    private val likedVideosRepository by lazy { LikedVideosRepository.getInstance(appContext) }
    private val playlistRepository by lazy { PlaylistRepository(appContext) }
    private val historyRepository by lazy { ViewHistory.getInstance(appContext) }
    private val savedSeedCooldown = java.util.concurrent.ConcurrentHashMap<String, Long>()

    
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    
    private var currentPage: Page? = null
    private var isLoadingMore = false
    private var isInitialized = false

    private var subsBacklog: List<Video> = emptyList()
    
    private var currentQueryIndex = 0
    private val discoveryQueries = mutableListOf<String>()
    private var wave2Job: kotlinx.coroutines.Job? = null
    
    private var viewHistory: ViewHistory? = null
    
    private val sessionWatchedTopics = mutableListOf<String>()

    // Video IDs the user has watched >=90 % — excluded from recommendations.
    private val watchedVideoIds = MutableStateFlow<Set<String>>(emptySet())

    // Related-graph (/next) per-seed cache, keyed by seed video id.
    private data class CachedRelated(val videos: List<Video>, val ts: Long)
    private val relatedCache = java.util.concurrent.ConcurrentHashMap<String, CachedRelated>()
    private val relatedSemaphore = Semaphore(3)
    
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
        viewModelScope.launch {
            viewHistory!!.getVideoHistoryFlow()
                .combine(playerPreferences.watchedThreshold) { history, threshold ->
                    history.filter { threshold.isWatched(it.position, it.duration) }
                        .map { it.videoId }
                        .toHashSet()
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

    fun removeContinueWatchingEntry(videoId: String) {
        viewModelScope.launch {
            viewHistory?.clearVideoHistory(videoId)
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

                    // ── Related-graph lane: harvest /next neighbours of recent positives ──
                    val deferredRelated = async {
                        fetchRelatedFor(FlowNeuroEngine.selectRelatedSeeds(buildSeedInputs(), MAX_RELATED_SEEDS))
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
                        }
                    }

                    listOf(deferredSubs.await(), deferredDiscovery.await(), viralResult, deferredRelated.await())
                }

                val rawSubs = results[0]
                val rawDiscovery = results[1]
                val rawViral = results[2]
                val rawRelated = results[3]

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
                val brain = FlowNeuroEngine.getBrainSnapshot()
                val taste = feedTasteProfile(brain, FlowNeuroEngine.getPersona(brain))

                val feedShorts = (rawSubs.extractShorts() + rawDiscovery.extractShorts() + rawViral.extractShorts())
                    .distinctBy { it.id }
                    .filterWatched(watchedVideoIds.value)
                    .filterRecentHomeSuggestion(now)
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
                    .filterRecentHomeSuggestion(now)
                val viralPool = rawViral.filterValid().filterWatched(watched)
                    .filterRecentHomeSuggestion(now)

                Log.d(
                    TAG,
                    "Flow candidates: subs=${subsPool.size}, discovery=${discoveryPool.size}, viral=${viralPool.size}, subCount=${userSubs.size}"
                )

                val subsByRecency = subsPool.sortedByDescending { it.timestamp }
                val freshSlotTarget = dynamicFreshSubSlots(userSubs.size)
                val freshSubsLane = subsByRecency
                    .filter { isFreshSubscribedCandidate(it, now) }
                    .take(freshSlotTarget)
                val freshIds = freshSubsLane.map { it.id }.toHashSet()

                val rankedSubs = FlowNeuroEngine.rank(subsPool, userSubs)
                val bestSubs = rankedSubs
                    .filter { !freshIds.contains(it.id) }
                    .take(15)

                val bestDiscovery = demoteByFit(FlowNeuroEngine.rank(discoveryPool, userSubs), taste).take(15)
                val bestViral = demoteByFit(FlowNeuroEngine.rank(viralPool, userSubs), taste).take(6)

                val relatedPool = rawRelated.filterValid().filterWatched(watched)
                    .filterRecentHomeSuggestion(now)
                val bestRelated = demoteByFit(FlowNeuroEngine.rank(relatedPool, userSubs), taste).take(12)

                val finalMix = mutableListOf<Video>()
                val usedChannelCounts = mutableMapOf<String, Int>()
                val usedVideoIds = mutableSetOf<String>()

                freshSubsLane.forEach { video ->
                    addUnique(video, finalMix, usedChannelCounts, usedVideoIds)
                }

                val remaining = (HOME_TARGET_SIZE - finalMix.size).coerceAtLeast(0)
                val subsQuota = (remaining * 0.40).toInt().coerceAtLeast(0)
                val relatedQuota = (remaining * 0.25).toInt().coerceAtLeast(0)
                val discoveryQuota = (remaining * 0.25).toInt().coerceAtLeast(0)
                val viralQuota = (remaining - subsQuota - relatedQuota - discoveryQuota).coerceAtLeast(0)

                val qSubs = java.util.ArrayDeque(bestSubs)
                val qRelated = java.util.ArrayDeque(bestRelated)
                val qDisc = java.util.ArrayDeque(bestDiscovery)
                val qViral = java.util.ArrayDeque(bestViral)

                var subsAdded = 0
                var relatedAdded = 0
                var discoveryAdded = 0
                var viralAdded = 0
                
                while (
                    finalMix.size < HOME_TARGET_SIZE &&
                    (qSubs.isNotEmpty() || qRelated.isNotEmpty() || qDisc.isNotEmpty() || qViral.isNotEmpty())
                ) {
                    var addedThisRound = false

                    if (subsAdded < subsQuota && addUnique(qSubs.pollFirst(), finalMix, usedChannelCounts, usedVideoIds)) {
                        subsAdded++
                        addedThisRound = true
                    }

                    if (relatedAdded < relatedQuota && addUnique(qRelated.pollFirst(), finalMix, usedChannelCounts, usedVideoIds)) {
                        relatedAdded++
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
                            addUnique(qRelated.pollFirst(), finalMix, usedChannelCounts, usedVideoIds) ||
                            addUnique(qDisc.pollFirst(), finalMix, usedChannelCounts, usedVideoIds) ||
                            addUnique(qViral.pollFirst(), finalMix, usedChannelCounts, usedVideoIds)
                        if (!forced) break
                    }
                }

                if (finalMix.size < HOME_TARGET_SIZE) {
                    val fallback = bestSubs + bestRelated + bestDiscovery + bestViral
                    fallback.forEach { video ->
                        if (finalMix.size >= HOME_TARGET_SIZE) return@forEach
                        addUnique(video, finalMix, usedChannelCounts, usedVideoIds)
                    }
                }

                subsBacklog = subsByRecency.filterNot { usedVideoIds.contains(it.id) }

                if (finalMix.isEmpty()) {
                   loadTrendingFallback()
                   return@launch
                }

                Log.d(
                    TAG,
                    "Flow mix: freshLane=${freshSubsLane.size}, final=${finalMix.size}, quotas=s:$subsQuota r:$relatedQuota d:$discoveryQuota v:$viralQuota"
                )

                val spacedMix = spaceByChannel(finalMix)
                _uiState.update { it.copy(
                    videos = spacedMix,
                    isLoading = false,
                    isRefreshing = false,
                    hasMorePages = true,
                    isFlowFeed = true,
                    lastRefreshTime = now
                )}
                HomeFeedCache.update(spacedMix, _uiState.value.shorts)

                // Enrich (post-paint) with related neighbours of saved/watched videos.
                enrichFeedWithSavedInterest(userSubs, taste)

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

                            val wave2Ranked = demoteByFit(FlowNeuroEngine.rank(wave2Valid, userSubs), taste)
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
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + rankedMore).distinctBy { it.id })
                    }
                }
                
                val newVideos = rawVideos.filterValid()
                    .filterWatched(watchedVideoIds.value)
                    .filterRecentHomeSuggestion(System.currentTimeMillis())

                val currentIds = _uiState.value.videos.map { it.id }.toHashSet()
                val page = mutableListOf<Video>()
                val channelCounts = HashMap<String, Int>()
                val pageIds = HashSet<String>(currentIds)

                if (newVideos.isNotEmpty()) {
                    val userSubs = subscriptionRepository.getAllSubscriptionIds()
                    val brain = FlowNeuroEngine.getBrainSnapshot()
                    val taste = feedTasteProfile(brain, FlowNeuroEngine.getPersona(brain))
                    demoteByFit(FlowNeuroEngine.rank(newVideos, userSubs), taste)
                        .forEach { addUnique(it, page, channelCounts, pageIds, maxPerChannel = 2) }
                }

                if (page.size < MIN_PAGE_SIZE && subsBacklog.isNotEmpty()) {
                    for (v in subsBacklog) {
                        if (page.size >= MIN_PAGE_SIZE) break
                        addUnique(v, page, channelCounts, pageIds, maxPerChannel = 2)
                    }
                    subsBacklog = subsBacklog.filterNot { pageIds.contains(it.id) }
                }

                if (page.isNotEmpty()) {
                    _uiState.update { state ->
                        val tailChannels = state.videos.takeLast(2).map { it.channelId }
                        state.copy(
                            videos = state.videos + spaceByChannel(page, seedRecent = tailChannels),
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
                val ranked = FlowNeuroEngine.rank(
                    videos.filterRecentHomeSuggestion(System.currentTimeMillis()),
                    userSubs
                )
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
        val ranked = FlowNeuroEngine.rank(
            videos.filterRecentHomeSuggestion(System.currentTimeMillis()),
            userSubs
        )
        updateVideosAndShorts(ranked, append = false)
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

        // Related-graph items carry no channelId; cap per-channel only when present.
        val hasChannel = video.channelId.isNotBlank()
        val count = channelCounts[video.channelId] ?: 0
        if (hasChannel && count >= maxPerChannel) return false
        if (!usedVideoIds.add(video.id)) return false
        targetList.add(video)
        if (hasChannel) channelCounts[video.channelId] = count + 1
        return true
    }

    private suspend fun buildSeedInputs(): List<SeedInput> {
        val history = viewHistory?.getVideoHistoryFlow()?.first() ?: return emptyList()
        return seedInputsFrom(history)
    }

    /** Expands seed video ids into their related (/next) neighbours, cached and concurrency-bounded. */
    private suspend fun fetchRelatedFor(seedIds: List<String>): List<Video> = coroutineScope {
        if (seedIds.isEmpty()) return@coroutineScope emptyList()
        seedIds.map { seedId ->
            async {
                val ts = System.currentTimeMillis()
                relatedCache[seedId]?.takeIf { ts - it.ts < RELATED_TTL_MS }?.videos
                    ?: (relatedSemaphore.withPermit {
                        withTimeoutOrNull(4_000L) { repository.getRelatedCandidates(seedId) } ?: emptyList()
                    }).also { relatedCache[seedId] = CachedRelated(it, ts) }
            }
        }.awaitAll().flatten()
    }

    private suspend fun gatherSavedSeedSources(): SavedSeedSources {
        val historyIds = runCatching {
            historyRepository.getVideoHistoryFlow().first()
                .filter { !it.isShort }
                .sortedByDescending { it.timestamp }
                .map { it.videoId }
        }.getOrElse { emptyList() }
        val likedIds = runCatching {
            likedVideosRepository.getLikedVideosFlow().first().map { it.videoId }
        }.getOrElse { emptyList() }
        val playlistIds = runCatching {
            playlistRepository.getSavedVideoPlaylistVideoIds()
        }.getOrElse { emptyList() }
        return SavedSeedSources(historyIds, likedIds, playlistIds)
    }

    private fun activeSavedSeedCooldown(now: Long): Set<String> {
        savedSeedCooldown.entries.removeAll { now - it.value > SAVED_SEED_COOLDOWN_MS }
        return savedSeedCooldown.keys.toHashSet()
    }

    /**
     * Enriches the feed with related neighbours of the videos the user saved/watched, on top of the
     * lane quotas. Runs after first paint so it never delays load; chosen seeds enter a cooldown.
     */
    private fun enrichFeedWithSavedInterest(userSubs: Set<String>, taste: FeedTasteProfile) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val now = System.currentTimeMillis()
                val seeds = selectSavedInterestSeeds(
                    gatherSavedSeedSources(), activeSavedSeedCooldown(now),
                    kotlin.random.Random.Default, maxSeeds = MAX_SAVED_SEEDS
                )
                if (seeds.isEmpty()) return@launch
                seeds.forEach { savedSeedCooldown[it] = now }

                val related = fetchRelatedFor(seeds)
                if (related.isEmpty()) return@launch

                val existing = _uiState.value.videos.mapTo(HashSet()) { it.id }
                val enriched = demoteByFit(
                    FlowNeuroEngine.rank(
                        related.filterValid().filterWatched(watchedVideoIds.value)
                            .filterRecentHomeSuggestion(now)
                            .filterNot { existing.contains(it.id) },
                        userSubs
                    ),
                    taste
                ).take(SAVED_RELATED_SLOTS)
                if (enriched.isEmpty()) return@launch

                _uiState.update { state ->
                    val tail = state.videos.takeLast(2).map { it.channelId }
                    val merged = state.videos + spaceByChannel(enriched, seedRecent = tail)
                    HomeFeedCache.update(merged, state.shorts)
                    state.copy(videos = merged)
                }
                Log.d(TAG, "Saved-interest enrichment: +${enriched.size} from ${seeds.size} seeds")
            } catch (e: Exception) {
                Log.d(TAG, "Saved-interest enrichment failed: ${e.message}")
            }
        }
    }

    // Viewport impressions: count only items actually scrolled into view.
    fun recordImpressions(visibleKeys: List<String>) {
        if (visibleKeys.isEmpty()) return
        val knownIds = _uiState.value.videos.mapTo(HashSet()) { it.id }
        val ids = feedImpressionIds(visibleKeys, knownIds)
        if (ids.isEmpty()) return
        viewModelScope.launch { FlowNeuroEngine.recordFeedImpressions(ids) }
    }

    private fun dynamicFreshSubSlots(subCount: Int): Int {
        return when {
            subCount >= 120 -> 5
            subCount >= 40 -> 4
            subCount >= 5 -> 3
            else -> 2
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