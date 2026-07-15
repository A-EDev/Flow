package io.github.aedev.flow.ui.screens.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.data.recommendation.GraphSeedInput
import io.github.aedev.flow.data.recommendation.GraphSeedSelector
import io.github.aedev.flow.data.recommendation.GraphSeedSource
import io.github.aedev.flow.data.recommendation.UserBrain
import io.github.aedev.flow.data.local.CachedHomeVideo
import io.github.aedev.flow.data.local.HomeFeedCacheFilters
import io.github.aedev.flow.data.local.HomeFeedCacheRepository
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
import kotlin.math.ln
import org.schabi.newpipe.extractor.Page

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Watch-history seed candidates for related-graph retrieval, newest first. */
internal fun graphSeedInputsFromHistory(history: List<VideoHistoryEntry>, max: Int = 40): List<GraphSeedInput> =
    history.filter { !it.isShort }
        .sortedByDescending { it.timestamp }
        .take(max)
        .map {
            GraphSeedInput(
                id = it.videoId,
                title = it.title,
                channelId = it.channelId,
                source = GraphSeedSource.WATCH_HISTORY,
                engagementWeight = (it.progressPercentage / 100.0).coerceIn(0.0, 1.0),
                timestamp = it.timestamp,
                durationSec = it.duration.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                percentWatched = it.progressPercentage.toDouble(),
                isShort = it.isShort
            )
        }

/** Keeps only visible grid keys that map to real feed videos (drops shelf/loader keys). */
internal fun feedImpressionIds(visibleKeys: List<String>, knownIds: Set<String>): List<String> =
    visibleKeys.filter { it in knownIds }

/** Saved-interest seed pools: watch history (newest-first), liked videos, and saved playlists. */
internal data class SavedSeedSources(
    val history: List<GraphSeedInput>,
    val liked: List<GraphSeedInput>,
    val playlists: List<GraphSeedInput>
)

internal data class GraphCandidate(
    val video: Video,
    val seedId: String,
    val seedScore: Double,
    val graphRank: Int,
    val seedCluster: String,
    val seedResultCount: Int,
    val hitCount: Int = 1
)

private data class GraphCandidateAccumulator(
    var candidate: GraphCandidate,
    val seedIds: MutableSet<String>
)

internal fun savedInterestSeedInputs(
    sources: SavedSeedSources,
    cooldown: Set<String>,
    maxPerSource: Int = 40
): List<GraphSeedInput> =
    listOf(sources.history, sources.liked, sources.playlists)
        .flatMap { seeds -> seeds.filterNot { it.id in cooldown }.take(maxPerSource) }

internal fun mergeGraphCandidates(candidates: List<GraphCandidate>): List<GraphCandidate> {
    if (candidates.isEmpty()) return emptyList()
    val merged = LinkedHashMap<String, GraphCandidateAccumulator>()
    for (candidate in candidates) {
        val videoId = candidate.video.id
        val accumulator = merged[videoId]
        if (accumulator == null) {
            merged[videoId] = GraphCandidateAccumulator(
                candidate = candidate,
                seedIds = mutableSetOf(candidate.seedId)
            )
            continue
        }

        accumulator.seedIds.add(candidate.seedId)
        val current = accumulator.candidate
        val strongerSeed = candidate.seedScore > current.seedScore
        accumulator.candidate = current.copy(
            video = if (strongerSeed) candidate.video else current.video,
            seedId = if (strongerSeed) candidate.seedId else current.seedId,
            seedScore = maxOf(current.seedScore, candidate.seedScore),
            graphRank = minOf(current.graphRank, candidate.graphRank),
            seedCluster = if (strongerSeed) candidate.seedCluster else current.seedCluster,
            seedResultCount = maxOf(current.seedResultCount, candidate.seedResultCount),
            hitCount = accumulator.seedIds.size
        )
    }
    return merged.values.map { it.candidate }
}

internal fun graphBoost(candidate: GraphCandidate): Double {
    val highSeedBoost = if (candidate.seedScore >= 1.0) 0.04 else 0.0
    val convergenceBoost = if (candidate.hitCount > 1) 0.03 else 0.0
    val topThirdCount = (candidate.seedResultCount + 2) / 3
    val graphRankBoost = if (candidate.graphRank < topThirdCount.coerceAtLeast(1)) 0.02 else 0.0
    return (highSeedBoost + convergenceBoost + graphRankBoost).coerceAtMost(0.08)
}

internal fun applyGraphBoost(
    ranked: List<Video>,
    metadata: Map<String, GraphCandidate>
): List<Video> {
    if (ranked.size <= 1 || metadata.isEmpty()) return ranked
    val maxIndex = (ranked.size - 1).coerceAtLeast(1)
    return ranked.withIndex()
        .sortedWith(
            compareByDescending<IndexedValue<Video>> { indexed ->
                val base = 1.0 - (indexed.index.toDouble() / maxIndex)
                base + (metadata[indexed.value.id]?.let(::graphBoost) ?: 0.0)
            }.thenBy { it.index }
        )
        .map { it.value }
}

private data class Wave1FeedResults(
    val subs: List<Video>,
    val discovery: List<Video>,
    val viral: List<Video>,
    val related: RelatedGraphFetchResult
)

private data class RelatedGraphFetchResult(
    val seedInputs: List<GraphSeedInput>,
    val seedIds: List<String>,
    val candidates: List<GraphCandidate>,
    val fetchedPerSeed: Map<String, Int>
)

internal data class RelatedLaneMetrics(
    val seedCandidatesAvailable: Int,
    val seedsSelected: Int,
    val seedSourceCounts: Map<GraphSeedSource, Int>,
    val nextEmptyResponses: Int,
    val relatedCandidatesFetched: Int,
    val relatedCandidatesMerged: Int,
    val relatedCandidatesSurvivingFilters: Int,
    val finalRelatedCount: Int,
    val finalFeedCount: Int,
    val relatedWatchThroughProxy: Double,
    val relatedSkipDislikeProxy: Double,
    val sourceEntropy: Double
) {
    val nextEmptyRate: Double
        get() = if (seedsSelected == 0) 0.0 else nextEmptyResponses.toDouble() / seedsSelected

    val relatedDedupeRate: Double
        get() = if (relatedCandidatesFetched == 0) 0.0
        else 1.0 - (relatedCandidatesMerged.toDouble() / relatedCandidatesFetched.toDouble())

    val finalRelatedShare: Double
        get() = if (finalFeedCount == 0) 0.0 else finalRelatedCount.toDouble() / finalFeedCount.toDouble()

    fun toLogString(): String =
        "Related metrics: seedCandidates=$seedCandidatesAvailable, seeds=$seedsSelected, " +
            "seedSources=$seedSourceCounts, nextEmpty=${"%.2f".format(nextEmptyRate)}, " +
            "fetched=$relatedCandidatesFetched, merged=$relatedCandidatesMerged, " +
            "dedupe=${"%.2f".format(relatedDedupeRate)}, survived=$relatedCandidatesSurvivingFilters, " +
            "finalRelated=$finalRelatedCount/$finalFeedCount, share=${"%.2f".format(finalRelatedShare)}, " +
            "watchProxy=${"%.2f".format(relatedWatchThroughProxy)}, " +
            "negativeProxy=${"%.2f".format(relatedSkipDislikeProxy)}, entropy=${"%.2f".format(sourceEntropy)}"
}

internal fun sourceEntropy(sourceCounts: Map<FeedSource, Int>): Double {
    val total = sourceCounts.values.sum()
    if (total <= 0) return 0.0
    val activeSources = sourceCounts.values.count { it > 0 }
    if (activeSources <= 1) return 0.0
    val entropy = sourceCounts.values
        .filter { it > 0 }
        .sumOf { count ->
            val p = count.toDouble() / total.toDouble()
            -p * ln(p)
        }
    return entropy / ln(activeSources.toDouble())
}

internal fun buildRelatedLaneMetrics(
    seedInputs: List<GraphSeedInput>,
    seedIds: List<String>,
    fetchedPerSeed: Map<String, Int>,
    mergedRelatedCandidates: List<GraphCandidate>,
    filteredRelatedCandidates: List<GraphCandidate>,
    selectedSourceCounts: Map<FeedSource, Int>,
    finalFeedCount: Int,
    finalRelatedVideoIds: Set<String>,
    brain: UserBrain
): RelatedLaneMetrics {
    val selectedSeedSet = seedIds.toSet()
    val seedSourceCounts = seedInputs
        .filter { it.id in selectedSeedSet }
        .groupingBy { it.source }
        .eachCount()
    val watchedRelated = finalRelatedVideoIds.count { (brain.watchHistoryMap[it] ?: 0f) >= 0.40f }
    val negativeRelated = finalRelatedVideoIds.count { it in brain.suppressedVideoIds }
    val denominator = finalRelatedVideoIds.size.takeIf { it > 0 } ?: 1

    return RelatedLaneMetrics(
        seedCandidatesAvailable = seedInputs.size,
        seedsSelected = seedIds.size,
        seedSourceCounts = seedSourceCounts,
        nextEmptyResponses = seedIds.count { (fetchedPerSeed[it] ?: 0) == 0 },
        relatedCandidatesFetched = fetchedPerSeed.values.sum(),
        relatedCandidatesMerged = mergedRelatedCandidates.size,
        relatedCandidatesSurvivingFilters = filteredRelatedCandidates.size,
        finalRelatedCount = selectedSourceCounts[FeedSource.RELATED] ?: 0,
        finalFeedCount = finalFeedCount,
        relatedWatchThroughProxy = watchedRelated.toDouble() / denominator.toDouble(),
        relatedSkipDislikeProxy = negativeRelated.toDouble() / denominator.toDouble(),
        sourceEntropy = sourceEntropy(selectedSourceCounts)
    )
}

internal enum class FeedSource {
    SUBS,
    RELATED,
    DISCOVERY,
    VIRAL
}

internal data class FeedCandidate(
    val video: Video,
    val source: FeedSource
)

internal data class FeedMixResult(
    val items: List<FeedCandidate>,
    val sourceCounts: Map<FeedSource, Int>
) {
    val videos: List<Video> get() = items.map { it.video }
}

internal fun homeFeedQuotas(
    remaining: Int,
    subCount: Int,
    totalInteractions: Int
): Map<FeedSource, Int> {
    val slots = remaining.coerceAtLeast(0)
    if (slots == 0) {
        return FeedSource.entries.associateWith { 0 }
    }

    val subs = when {
        subCount <= 0 -> 0
        totalInteractions > 50 -> (slots * 0.40).toInt()
        else -> (slots * 0.35).toInt()
    }.coerceAtLeast(0)
    val related = when {
        subCount <= 0 -> (slots * 0.35).toInt()
        totalInteractions > 50 -> (slots * 0.25).toInt()
        else -> (slots * 0.30).toInt()
    }.coerceAtLeast(0)
    val discovery = when {
        subCount <= 0 -> (slots * 0.45).toInt()
        else -> (slots * 0.25).toInt()
    }.coerceAtLeast(0)
    val viral = (slots - subs - related - discovery).coerceAtLeast(0)

    return mapOf(
        FeedSource.SUBS to subs,
        FeedSource.RELATED to related,
        FeedSource.DISCOVERY to discovery,
        FeedSource.VIRAL to viral
    )
}

internal fun addUniqueVideo(
    video: Video?,
    targetList: MutableList<Video>,
    channelCounts: MutableMap<String, Int>,
    usedVideoIds: MutableSet<String>,
    maxPerChannel: Int = 2
): Boolean {
    if (video == null) return false

    val hasChannel = video.channelId.isNotBlank()
    val count = channelCounts[video.channelId] ?: 0
    if (hasChannel && count >= maxPerChannel) return false
    if (!usedVideoIds.add(video.id)) return false
    targetList.add(video)
    if (hasChannel) channelCounts[video.channelId] = count + 1
    return true
}

internal fun addUniquePageVideos(
    candidates: Iterable<Video>,
    targetList: MutableList<Video>,
    channelCounts: MutableMap<String, Int>,
    usedVideoIds: MutableSet<String>,
    targetSize: Int,
    maxPerChannel: Int = 2
): Int {
    var added = 0
    for (candidate in candidates) {
        if (targetList.size >= targetSize) break
        if (addUniqueVideo(candidate, targetList, channelCounts, usedVideoIds, maxPerChannel)) {
            added++
        }
    }
    return added
}

private fun addUniqueCandidate(
    candidate: FeedCandidate?,
    targetList: MutableList<FeedCandidate>,
    channelCounts: MutableMap<String, Int>,
    usedVideoIds: MutableSet<String>,
    maxPerChannel: Int = 2
): Boolean {
    if (candidate == null) return false
    val temp = mutableListOf<Video>()
    if (!addUniqueVideo(candidate.video, temp, channelCounts, usedVideoIds, maxPerChannel)) return false
    targetList.add(candidate)
    return true
}

internal fun blendFeedSources(
    lanes: Map<FeedSource, List<Video>>,
    quotas: Map<FeedSource, Int>,
    targetSize: Int,
    channelCounts: MutableMap<String, Int> = mutableMapOf(),
    usedVideoIds: MutableSet<String> = mutableSetOf()
): FeedMixResult {
    val target = targetSize.coerceAtLeast(0)
    if (target == 0) return FeedMixResult(emptyList(), emptyMap())

    val queues = FeedSource.entries.associateWith { source ->
        java.util.ArrayDeque(lanes[source].orEmpty().map { FeedCandidate(it, source) })
    }
    val quotaOrder = listOf(FeedSource.SUBS, FeedSource.RELATED, FeedSource.DISCOVERY, FeedSource.VIRAL)
    val scarcityOrder = listOf(FeedSource.RELATED, FeedSource.DISCOVERY, FeedSource.SUBS, FeedSource.VIRAL)
    val addedBySource = mutableMapOf<FeedSource, Int>()
    val out = mutableListOf<FeedCandidate>()

    while (out.size < target && queues.any { it.value.isNotEmpty() }) {
        var addedThisRound = false
        for (source in quotaOrder) {
            if (out.size >= target) break
            val added = addedBySource[source] ?: 0
            val quota = quotas[source] ?: 0
            if (added < quota && addUniqueCandidate(queues[source]?.pollFirst(), out, channelCounts, usedVideoIds)) {
                addedBySource[source] = added + 1
                addedThisRound = true
            }
        }

        if (!addedThisRound) {
            val forced = scarcityOrder.any { source ->
                if (out.size >= target) true
                else addUniqueCandidate(queues[source]?.pollFirst(), out, channelCounts, usedVideoIds).also { added ->
                    if (added) addedBySource[source] = (addedBySource[source] ?: 0) + 1
                }
            }
            if (!forced) break
        }
    }

    return FeedMixResult(
        items = out,
        sourceCounts = FeedSource.entries.associateWith { source -> addedBySource[source] ?: 0 }
    )
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

private fun Video.withChannelMetadataFrom(enriched: Video): Video {
    val avatarUrl = enriched.channelThumbnailUrl.ifBlank { channelThumbnailUrl }
    return copy(
        channelId = enriched.channelId.ifBlank { channelId },
        channelName = enriched.channelName.ifBlank { channelName },
        channelThumbnailUrl = avatarUrl,
        channelThumbnailUrls = if (avatarUrl.isNotBlank()) {
            (listOf(avatarUrl) +
                enriched.channelThumbnailUrls +
                channelThumbnailUrls).distinct()
        } else {
            channelThumbnailUrls
        }
    )
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
        private const val LOAD_MORE_GRAPH_SEEDS = 2
        private const val MAX_SAVED_SEEDS = 5
        private const val SAVED_RELATED_SLOTS = 8
        private const val SAVED_SEED_COOLDOWN_MS = 3L * 60L * 60L * 1000L
    }

    // Saved-interest enrichment sources (history/liked/playlists) + per-seed cooldown.
    private val likedVideosRepository by lazy { LikedVideosRepository.getInstance(appContext) }
    private val playlistRepository by lazy { PlaylistRepository(appContext) }
    private val historyRepository by lazy { ViewHistory.getInstance(appContext) }
    private val persistentHomeFeedCache by lazy { HomeFeedCacheRepository(appContext) }
    private val savedSeedCooldown = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val channelMetadataEnrichmentInFlight =
        java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    
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
            hydratePersistentHomeFeed()
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
                        HomeFeedCache.filterOut(videoId = event.videoId)
                        viewModelScope.launch(PerformanceDispatcher.networkIO) {
                            persistentHomeFeedCache.deleteChannel(event.channelId)
                            persistentHomeFeedCache.deleteVideo(event.videoId)
                        }
                        _uiState.update { state ->
                            state.copy(
                                videos = state.videos.filter {
                                    it.id != event.videoId && it.channelId != event.channelId
                                },
                                shorts = state.shorts.filter {
                                    it.id != event.videoId && it.channelId != event.channelId
                                }
                            )
                        }
                        // Targeted eviction — preserves other channel caches in discovery engine
                        shortsRepository.evictChannel(event.channelId)
                    }
                    is FeedInvalidationBus.Event.NotInterested -> {
                        HomeFeedCache.filterOut(videoId = event.videoId)
                        viewModelScope.launch(PerformanceDispatcher.networkIO) {
                            persistentHomeFeedCache.deleteVideo(event.videoId)
                        }
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
                        viewModelScope.launch(PerformanceDispatcher.networkIO) {
                            persistentHomeFeedCache.deleteVideo(event.videoId)
                        }
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

    private suspend fun cacheFilters(): HomeFeedCacheFilters {
        val brain = runCatching { FlowNeuroEngine.getBrainSnapshot() }.getOrElse { UserBrain() }
        return HomeFeedCacheFilters(
            watchedVideoIds = watchedVideoIds.value,
            suppressedVideoIds = brain.suppressedVideoIds.keys,
            blockedChannelIds = brain.blockedChannels,
            suppressedChannelIds = brain.suppressedChannels.keys
        )
    }

    private fun hydratePersistentHomeFeed() {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            val cached = runCatching {
                persistentHomeFeedCache.loadLastFeed(cacheFilters())
            }.getOrElse { emptyList() }
            if (cached.isEmpty()) return@launch
            val hydratedCached = repository.enrichLikelyCollabAvatarStacks(cached, limit = 8)

            _uiState.update { state ->
                if (state.videos.isNotEmpty()) return@update state
                HomeFeedCache.update(hydratedCached, state.shorts)
                state.copy(
                    videos = hydratedCached,
                    isFlowFeed = true,
                    error = null,
                    lastRefreshTime = System.currentTimeMillis()
                )
            }
            enrichVisibleChannelMetadata(hydratedCached)?.let {
                persistentHomeFeedCache.saveLastFeed(it)
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
                        val seedInputs = buildSeedInputs()
                        val seedIds = FlowNeuroEngine.selectRelatedSeeds(seedInputs, MAX_RELATED_SEEDS)
                        fetchRelatedGraph(seedInputs, seedIds)
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

                    Wave1FeedResults(
                        subs = deferredSubs.await(),
                        discovery = deferredDiscovery.await(),
                        viral = viralResult,
                        related = deferredRelated.await()
                    )
                }

                val rawSubs = results.subs
                val rawDiscovery = results.discovery
                val rawViral = results.viral
                val relatedFetch = results.related
                val rawRelated = relatedFetch.candidates

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
                            v.copy(
                                channelThumbnailUrl = subAvatarMap.getValue(v.channelId),
                                channelThumbnailUrls = v.channelThumbnailUrls.ifEmpty {
                                    listOf(subAvatarMap.getValue(v.channelId))
                                }
                            )
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
                    "Flow candidates: subs=${subsPool.size}, discovery=${discoveryPool.size}, viral=${viralPool.size}, related=${rawRelated.size}, subCount=${userSubs.size}"
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

                val relatedCandidates = rawRelated.filterValidGraph().filterWatchedGraph(watched)
                    .filterRecentHomeSuggestionGraph(now)
                val relatedPool = relatedCandidates.map { it.video }
                val relatedMetadata = relatedCandidates.associateBy { it.video.id }
                val bestRelated = demoteByFit(
                    applyGraphBoost(FlowNeuroEngine.rank(relatedPool, userSubs), relatedMetadata),
                    taste
                ).take(12)

                val finalMix = mutableListOf<Video>()
                val usedChannelCounts = mutableMapOf<String, Int>()
                val usedVideoIds = mutableSetOf<String>()
                var freshAdded = 0

                freshSubsLane.forEach { video ->
                    if (addUnique(video, finalMix, usedChannelCounts, usedVideoIds)) freshAdded++
                }

                val remaining = (HOME_TARGET_SIZE - finalMix.size).coerceAtLeast(0)
                val quotas = homeFeedQuotas(remaining, userSubs.size, brain.totalInteractions)
                val sourceMix = blendFeedSources(
                    lanes = mapOf(
                        FeedSource.SUBS to bestSubs,
                        FeedSource.RELATED to bestRelated,
                        FeedSource.DISCOVERY to bestDiscovery,
                        FeedSource.VIRAL to bestViral
                    ),
                    quotas = quotas,
                    targetSize = remaining,
                    channelCounts = usedChannelCounts,
                    usedVideoIds = usedVideoIds
                )
                finalMix += sourceMix.videos

                subsBacklog = subsByRecency.filterNot { usedVideoIds.contains(it.id) }

                if (finalMix.isEmpty()) {
                   loadTrendingFallback()
                   return@launch
                }

                val selectedSourceCounts = sourceMix.sourceCounts.toMutableMap().also { counts ->
                    counts[FeedSource.SUBS] = (counts[FeedSource.SUBS] ?: 0) + freshAdded
                }
                val relatedMetrics = buildRelatedLaneMetrics(
                    seedInputs = relatedFetch.seedInputs,
                    seedIds = relatedFetch.seedIds,
                    fetchedPerSeed = relatedFetch.fetchedPerSeed,
                    mergedRelatedCandidates = rawRelated,
                    filteredRelatedCandidates = relatedCandidates,
                    selectedSourceCounts = selectedSourceCounts,
                    finalFeedCount = finalMix.size,
                    finalRelatedVideoIds = sourceMix.items
                        .filter { it.source == FeedSource.RELATED }
                        .mapTo(HashSet()) { it.video.id },
                    brain = brain
                )
                Log.d(TAG, relatedMetrics.toLogString())

                Log.d(
                    TAG,
                    "Flow mix: freshLane=$freshAdded, final=${finalMix.size}, quotas=${quotas}, selected=${sourceMix.sourceCounts}"
                )

                val spacedMix = repository.enrichLikelyCollabAvatarStacks(
                    spaceByChannel(finalMix),
                    limit = 8
                )
                val renderedIds = spacedMix.mapTo(HashSet()) { it.id }
                val reserveCandidates =
                    cacheRelatedCandidates(bestRelated, relatedMetadata, renderedIds) +
                    cacheCandidates(FeedSource.DISCOVERY, bestDiscovery, renderedIds) +
                    cacheCandidates(FeedSource.SUBS, bestSubs, renderedIds) +
                    cacheCandidates(FeedSource.VIRAL, bestViral, renderedIds)
                _uiState.update { it.copy(
                    videos = spacedMix,
                    isLoading = false,
                    isRefreshing = false,
                    hasMorePages = true,
                    isFlowFeed = true,
                    lastRefreshTime = now
                )}
                HomeFeedCache.update(spacedMix, _uiState.value.shorts)
                persistentHomeFeedCache.saveLastFeed(spacedMix)
                persistentHomeFeedCache.saveReserve(reserveCandidates)
                enrichVisibleChannelMetadata(spacedMix)?.let {
                    persistentHomeFeedCache.saveLastFeed(it)
                }

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
                                var updatedSnapshot: List<Video>? = null
                                _uiState.update { state ->
                                    val currentIds = state.videos.map { it.id }.toHashSet()
                                    val uniqueNew = wave2Ranked.filter { !currentIds.contains(it.id) }
                                        .distinctBy { it.channelId }
                                    if (uniqueNew.isEmpty()) return@update state
                                    val updated = state.videos + uniqueNew
                                    updatedSnapshot = updated
                                    HomeFeedCache.update(updated, state.shorts)
                                    state.copy(videos = updated)
                                }
                                updatedSnapshot?.let { persistentHomeFeedCache.saveLastFeed(it) }
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
                val now = System.currentTimeMillis()
                val userSubs = subscriptionRepository.getAllSubscriptionIds()
                val brain = FlowNeuroEngine.getBrainSnapshot()
                val taste = feedTasteProfile(brain, FlowNeuroEngine.getPersona(brain))
                val currentIds = _uiState.value.videos.map { it.id }.toHashSet()
                val page = mutableListOf<Video>()
                val channelCounts = HashMap<String, Int>()
                val pageIds = HashSet<String>(currentIds)

                val reserveVideos = runCatching {
                    persistentHomeFeedCache.loadReservePage(cacheFilters()).map { it.video }
                }.getOrElse { emptyList() }
                    .filterValid()
                    .filterRecentHomeSuggestion(now)
                val reserveAdded = addUniquePageVideos(
                    candidates = reserveVideos,
                    targetList = page,
                    channelCounts = channelCounts,
                    usedVideoIds = pageIds,
                    targetSize = MIN_PAGE_SIZE
                )
                if (page.size >= MIN_PAGE_SIZE) {
                    appendLoadMorePage(page)?.let { persistentHomeFeedCache.saveLastFeed(it) }
                    Log.d(TAG, "Load-more filled from reserve: +$reserveAdded")
                    return@launch
                }

                val seedInputs = buildSeedInputs()
                val seedIds = FlowNeuroEngine.selectRelatedSeeds(seedInputs, LOAD_MORE_GRAPH_SEEDS)
                if (seedIds.isNotEmpty()) {
                    val graphFetch = fetchRelatedGraph(seedInputs, seedIds)
                    val graphCandidates = graphFetch.candidates
                        .filterValidGraph()
                        .filterWatchedGraph(watchedVideoIds.value)
                        .filterRecentHomeSuggestionGraph(now)
                    val graphMetadata = graphCandidates.associateBy { it.video.id }
                    val graphRanked = demoteByFit(
                        applyGraphBoost(
                            FlowNeuroEngine.rank(graphCandidates.map { it.video }, userSubs),
                            graphMetadata
                        ),
                        taste
                    )
                    val graphStartIndex = page.size
                    addUniquePageVideos(
                        candidates = graphRanked,
                        targetList = page,
                        channelCounts = channelCounts,
                        usedVideoIds = pageIds,
                        targetSize = MIN_PAGE_SIZE
                    )
                    persistentHomeFeedCache.saveReserve(
                        cacheRelatedCandidates(graphRanked, graphMetadata, pageIds)
                    )
                    if (page.size >= MIN_PAGE_SIZE) {
                        val selectedGraphIds = page.drop(graphStartIndex).mapTo(HashSet()) { it.id }
                        Log.d(
                            TAG,
                            buildRelatedLaneMetrics(
                                seedInputs = graphFetch.seedInputs,
                                seedIds = graphFetch.seedIds,
                                fetchedPerSeed = graphFetch.fetchedPerSeed,
                                mergedRelatedCandidates = graphFetch.candidates,
                                filteredRelatedCandidates = graphCandidates,
                                selectedSourceCounts = mapOf(FeedSource.RELATED to selectedGraphIds.size),
                                finalFeedCount = selectedGraphIds.size,
                                finalRelatedVideoIds = selectedGraphIds,
                                brain = brain
                            ).toLogString()
                        )
                        appendLoadMorePage(page)?.let { persistentHomeFeedCache.saveLastFeed(it) }
                        Log.d(TAG, "Load-more filled from reserve/graph: reserve=$reserveAdded graphSeeds=${seedIds.size}")
                        return@launch
                    }
                }

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
                    .filterRecentHomeSuggestion(now)
                if (moreShorts.isNotEmpty() && playerPreferences.homeShortsShelfEnabled.first()) {
                    val rankedMore = FlowNeuroEngine.rank(moreShorts, userSubs)
                    _uiState.update { state ->
                        state.copy(shorts = (state.shorts + rankedMore).distinctBy { it.id })
                    }
                }
                
                val newVideos = rawVideos.filterValid()
                    .filterWatched(watchedVideoIds.value)
                    .filterRecentHomeSuggestion(now)

                if (newVideos.isNotEmpty()) {
                    val rankedDiscovery = demoteByFit(FlowNeuroEngine.rank(newVideos, userSubs), taste)
                    addUniquePageVideos(
                        candidates = rankedDiscovery,
                        targetList = page,
                        channelCounts = channelCounts,
                        usedVideoIds = pageIds,
                        targetSize = MIN_PAGE_SIZE
                    )
                    persistentHomeFeedCache.saveReserve(
                        cacheCandidates(FeedSource.DISCOVERY, rankedDiscovery, pageIds)
                    )
                }

                if (page.size < MIN_PAGE_SIZE && subsBacklog.isNotEmpty()) {
                    addUniquePageVideos(
                        candidates = subsBacklog,
                        targetList = page,
                        channelCounts = channelCounts,
                        usedVideoIds = pageIds,
                        targetSize = MIN_PAGE_SIZE
                    )
                    subsBacklog = subsBacklog.filterNot { pageIds.contains(it.id) }
                }

                if (page.isNotEmpty()) {
                    appendLoadMorePage(page)?.let { persistentHomeFeedCache.saveLastFeed(it) }
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

    private suspend fun appendLoadMorePage(page: List<Video>): List<Video>? {
        if (page.isEmpty()) return null
        var updatedSnapshot: List<Video>? = null
        _uiState.update { state ->
            val tailChannels = state.videos.takeLast(2).map { it.channelId }
            val updated = state.videos + spaceByChannel(page, seedRecent = tailChannels)
            updatedSnapshot = updated
            HomeFeedCache.update(updated, state.shorts)
            state.copy(
                videos = updated,
                isLoadingMore = false,
                hasMorePages = true
            )
        }
        return enrichVisibleChannelMetadata(page) ?: updatedSnapshot
    }

    private suspend fun enrichVisibleChannelMetadata(videos: List<Video>): List<Video>? {
        val enriched = repository.enrichMissingChannelMetadata(videos)
        if (enriched == videos) return null

        val originalById = videos.associateBy { it.id }
        val updates = enriched
            .filter { enrichedVideo -> originalById[enrichedVideo.id] != enrichedVideo }
            .associateBy { it.id }
        var updatedSnapshot: List<Video>? = null
        _uiState.update { state ->
            val updated = state.videos.map { current ->
                updates[current.id]?.let(current::withChannelMetadataFrom) ?: current
            }
            if (updated == state.videos) return@update state
            updatedSnapshot = updated
            HomeFeedCache.update(updated, state.shorts)
            state.copy(videos = updated)
        }
        return updatedSnapshot
    }

    fun enrichChannelMetadataIfMissing(videoId: String) {
        val video = _uiState.value.videos.firstOrNull { it.id == videoId } ?: return
        val needsMetadata = video.channelId.isBlank() ||
            !video.channelId.startsWith("UC") ||
            video.channelThumbnailUrl.isBlank()
        if (!needsMetadata || !channelMetadataEnrichmentInFlight.add(videoId)) return

        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val enriched = repository.enrichMissingChannelMetadata(listOf(video), limit = 1)
                    .firstOrNull()
                    ?: return@launch
                if (enriched == video) return@launch

                _uiState.update { state ->
                    val updated = state.videos.map { current ->
                        if (current.id != videoId) {
                            current
                        } else {
                            current.withChannelMetadataFrom(enriched)
                        }
                    }
                    if (updated == state.videos) state else {
                        HomeFeedCache.update(updated, state.shorts)
                        state.copy(videos = updated)
                    }
                }
            } finally {
                channelMetadataEnrichmentInFlight.remove(videoId)
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

    private fun cacheCandidates(
        source: FeedSource,
        videos: List<Video>,
        excludedIds: Set<String> = emptySet()
    ): List<CachedHomeVideo> =
        videos.asSequence()
            .filterNot { it.id in excludedIds }
            .distinctBy { it.id }
            .map { CachedHomeVideo(it, source.name) }
            .toList()

    private fun cacheRelatedCandidates(
        videos: List<Video>,
        metadata: Map<String, GraphCandidate>,
        excludedIds: Set<String> = emptySet()
    ): List<CachedHomeVideo> =
        videos.asSequence()
            .filterNot { it.id in excludedIds }
            .distinctBy { it.id }
            .map { video ->
                CachedHomeVideo(
                    video = video,
                    source = FeedSource.RELATED.name,
                    relatedSeedId = metadata[video.id]?.seedId
                )
            }
            .toList()

    private fun addUnique(
        video: Video?, 
        targetList: MutableList<Video>, 
        channelCounts: MutableMap<String, Int>,
        usedVideoIds: MutableSet<String>,
        maxPerChannel: Int = 2
    ): Boolean = addUniqueVideo(video, targetList, channelCounts, usedVideoIds, maxPerChannel)

    private suspend fun buildSeedInputs(): List<GraphSeedInput> {
        val history = viewHistory?.getVideoHistoryFlow()?.first() ?: return emptyList()
        return graphSeedInputsFromHistory(history)
    }

    private suspend fun fetchRelatedVideos(seedId: String): List<Video> {
        val ts = System.currentTimeMillis()
        relatedCache[seedId]?.takeIf { ts - it.ts < RELATED_TTL_MS }?.videos?.let { return it }

        val persisted = runCatching {
            persistentHomeFeedCache.loadRelated(seedId, cacheFilters(), ts)
        }.getOrElse { emptyList() }
        if (persisted.isNotEmpty()) {
            relatedCache[seedId] = CachedRelated(persisted, ts)
            return persisted
        }

        return (relatedSemaphore.withPermit {
            withTimeoutOrNull(4_000L) { repository.getRelatedCandidates(seedId) } ?: emptyList()
        }).also {
            relatedCache[seedId] = CachedRelated(it, ts)
            persistentHomeFeedCache.saveRelated(seedId, it, ts)
        }
    }

    /** Expands seed video ids into related (/next) neighbours with graph metadata. */
    private suspend fun fetchRelatedGraph(
        seedInputs: List<GraphSeedInput>,
        seedIds: List<String>
    ): RelatedGraphFetchResult = coroutineScope {
        if (seedIds.isEmpty()) {
            return@coroutineScope RelatedGraphFetchResult(seedInputs, seedIds, emptyList(), emptyMap())
        }
        val now = System.currentTimeMillis()
        val seedMetadata = seedInputs
            .filter { it.id in seedIds }
            .groupBy { it.id }
            .mapValues { (_, seeds) -> seeds.maxBy { GraphSeedSelector.scoreSeed(it, now) } }

        val perSeed = seedIds.map { seedId ->
            async {
                val seed = seedMetadata[seedId]
                val videos = fetchRelatedVideos(seedId)
                val seedScore = seed?.let { GraphSeedSelector.scoreSeed(it, now) } ?: 0.0
                val seedCluster = seed?.let { GraphSeedSelector.clusterKey(it) } ?: "misc"
                val candidates = videos.mapIndexed { index, video ->
                    GraphCandidate(
                        video = video,
                        seedId = seedId,
                        seedScore = seedScore,
                        graphRank = index,
                        seedCluster = seedCluster,
                        seedResultCount = videos.size
                    )
                }
                seedId to candidates
            }
        }.awaitAll()
        val rawCandidates = perSeed.flatMap { it.second }
        RelatedGraphFetchResult(
            seedInputs = seedInputs,
            seedIds = seedIds,
            candidates = mergeGraphCandidates(rawCandidates),
            fetchedPerSeed = perSeed.associate { (seedId, candidates) -> seedId to candidates.size }
        )
    }

    /** Expands seed video ids into related (/next) neighbours with graph metadata. */
    private suspend fun fetchRelatedGraphCandidates(
        seedInputs: List<GraphSeedInput>,
        seedIds: List<String>
    ): List<GraphCandidate> = fetchRelatedGraph(seedInputs, seedIds).candidates

    private suspend fun gatherSavedSeedSources(): SavedSeedSources {
        val historySeeds = runCatching {
            graphSeedInputsFromHistory(historyRepository.getVideoHistoryFlow().first())
        }.getOrElse { emptyList() }
        val likedSeeds = runCatching {
            likedVideosRepository.getLikedVideosFlow().first().map {
                GraphSeedInput(
                    id = it.videoId,
                    title = it.title,
                    channelId = "",
                    source = GraphSeedSource.LIKED,
                    engagementWeight = 1.0,
                    timestamp = it.likedAt,
                    durationSec = 0,
                    percentWatched = 0.0
                )
            }
        }.getOrElse { emptyList() }
        val playlistSeeds = runCatching {
            playlistRepository.getSavedVideoPlaylistVideos().map {
                GraphSeedInput(
                    id = it.id,
                    title = it.title,
                    channelId = it.channelId,
                    source = GraphSeedSource.PLAYLIST,
                    engagementWeight = 1.0,
                    timestamp = it.timestamp,
                    durationSec = it.duration,
                    percentWatched = 0.0
                )
            }
        }.getOrElse { emptyList() }
        return SavedSeedSources(historySeeds, likedSeeds, playlistSeeds)
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
                val seedInputs = savedInterestSeedInputs(gatherSavedSeedSources(), activeSavedSeedCooldown(now))
                val seeds = FlowNeuroEngine.selectRelatedSeeds(
                    seedInputs,
                    MAX_SAVED_SEEDS
                )
                if (seeds.isEmpty()) return@launch
                seeds.forEach { savedSeedCooldown[it] = now }

                val relatedCandidates = fetchRelatedGraphCandidates(seedInputs, seeds)
                    .filterValidGraph()
                    .filterWatchedGraph(watchedVideoIds.value)
                    .filterRecentHomeSuggestionGraph(now)
                if (relatedCandidates.isEmpty()) return@launch

                val existing = _uiState.value.videos.mapTo(HashSet()) { it.id }
                val relatedMetadata = relatedCandidates.associateBy { it.video.id }
                val enriched = demoteByFit(
                    applyGraphBoost(
                        FlowNeuroEngine.rank(
                            relatedCandidates.map { it.video }
                                .filterNot { existing.contains(it.id) },
                            userSubs
                        ),
                        relatedMetadata
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

    private fun List<GraphCandidate>.filterValidGraph(): List<GraphCandidate> =
        filter { candidate ->
            !candidate.video.isShort &&
                ((candidate.video.duration > 120) || (candidate.video.duration == 0 && candidate.video.isLive))
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

    private fun List<GraphCandidate>.filterRecentHomeSuggestionGraph(now: Long): List<GraphCandidate> =
        filter { candidate -> isRecentHomeSuggestion(candidate.video, now) }

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

    private fun List<GraphCandidate>.filterWatchedGraph(watchedIds: Set<String>): List<GraphCandidate> {
        if (watchedIds.isEmpty()) return this
        return filter { !watchedIds.contains(it.video.id) }
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
