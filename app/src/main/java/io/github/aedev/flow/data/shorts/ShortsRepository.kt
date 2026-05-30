package io.github.aedev.flow.data.shorts

import android.content.Context
import android.util.Log
import android.util.LruCache
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.ShortsSequenceResult
import io.github.aedev.flow.data.model.toShortVideo
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.innertube.pages.NewPipeExtractor
import io.github.aedev.flow.player.quality.QualityManager
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Shorts Repository — Discovery-First Architecture
 *
 * Architecture:
 * 1. PRIMARY (seedVideoId == null):
 *    ShortsDiscoveryEngine builds a topic-aware candidate pool from:
 *      a) Subscribed channel recent uploads filtered to ≤60s
 *      b) Topic-driven searches using FlowNeuroEngine's learned interests
 *    This ensures the candidate pool is already ~80% relevant before ranking.
 *
 * 2. SEED (seedVideoId != null):
 *    InnerTube reel_watch_sequence endpoint — related content from a specific video.
 *    Kept as-is because the user explicitly started from a video they wanted.
 *
 * 3. FALLBACK: NewPipe Extractor search-based discovery
 *    Used when all primary sources fail.
 *
 * 4. CACHING:
 *    - Stream URL cache (LRU, 50 entries) — avoids re-resolving on swipe-back
 *    - StreamInfo cache (LRU, 30 entries) — full stream metadata for player setup
 *    - Discovery engine has its own per-channel + per-query caches
 */
class ShortsRepository private constructor(private val context: Context) {

    private val youtubeRepository = YouTubeRepository.getInstance()
    private val subscriptionRepository = SubscriptionRepository.getInstance(context)
    private val viewHistory = ViewHistory.getInstance(context)
    private val shortsDiscovery = ShortsDiscoveryEngine.getInstance(context)
    
    // In-memory caches — ephemeral, cleared when app process dies
    private val streamInfoCache = LruCache<String, StreamInfo>(50)
    private val playbackStreamsCache = LruCache<String, ShortPlaybackStreams>(50)
    private val shortsCache = LruCache<String, ShortVideo>(100)
    private val streamResolveMutex = Mutex()
    private val playbackStreamsInFlight = mutableMapOf<String, Deferred<ShortPlaybackStreams?>>()
    
    // Track recently shown to prevent immediate repeats within a session
    private val recentlyShownIds = mutableSetOf<String>()
    
    // Cached home/initial feed to avoid duplicate API calls
    @Volatile
    private var cachedInitialFeed: ShortsSequenceResult? = null
    private var cachedFeedTimestamp = 0L
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    
    // Progressive enrichment events — UI observes this to update metadata live 
    private val _enrichmentUpdates = MutableSharedFlow<List<ShortVideo>>(extraBufferCapacity = 16)
    val enrichmentUpdates: SharedFlow<List<ShortVideo>> = _enrichmentUpdates.asSharedFlow()

    // Discovery feed appendages — emitted when background discovery completes after InnerTube fast-path
    private val _discoveryFeedUpdate = MutableSharedFlow<List<ShortVideo>>(replay = 1, extraBufferCapacity = 3)
    val discoveryFeedUpdate: SharedFlow<List<ShortVideo>> = _discoveryFeedUpdate.asSharedFlow()

    // Scope for background work (enrichment, pre-caching) that must outlive any single call
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    companion object {
        private const val TAG = "ShortsRepository"
        private const val INNERTUBE_TIMEOUT_MS = 8_000L
        private const val NEWPIPE_TIMEOUT_MS = 8_000L
        private const val STREAM_RESOLVE_TIMEOUT_MS = 8_000L
        private const val ENRICHMENT_TIMEOUT_MS = 12_000L
        private const val MAX_RECENTLY_SHOWN = 100
        private const val MIN_POOL_SIZE = 10
        
        @Volatile
        private var INSTANCE: ShortsRepository? = null
        
        fun getInstance(context: Context): ShortsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShortsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    suspend fun getShortsFeed(
        seedVideoId: String? = null
    ): ShortsSequenceResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "━━━ Fetching Shorts Feed (seed=$seedVideoId) ━━━")
        if (seedVideoId == null) {
            val cached = cachedInitialFeed
            if (cached != null &&
                System.currentTimeMillis() - cachedFeedTimestamp < CACHE_TTL_MS &&
                cached.shorts.isNotEmpty()
            ) {
                Log.d(TAG, "♻ Using cached feed (${cached.shorts.size} shorts)")
                val filtered = cached.copy(shorts = filterWatchedShorts(cached.shorts))
                if (filtered.shorts.isNotEmpty()) return@withContext filtered
            }
            return@withContext fetchDiscoveryFeed()
        }

        val rawResult = try {
            withTimeoutOrNull(INNERTUBE_TIMEOUT_MS) { fetchFromInnerTubeRaw(seedVideoId) }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube seed feed failed: ${e.message}")
            null
        }

        if (rawResult != null && rawResult.shorts.isNotEmpty()) {
            Log.d(TAG, "✓ InnerTube seed returned ${rawResult.shorts.size} shorts")
            val filtered = rawResult.copy(shorts = filterWatchedShorts(rawResult.shorts))
            filtered.shorts.forEach { shortsCache.put(it.id, it) }
            markAsShown(filtered.shorts.map { it.id })
            return@withContext filtered
        }

        Log.w(TAG, "InnerTube seed failed — falling back to discovery feed")
        fetchDiscoveryFeed()
    }

    private suspend fun fetchDiscoveryFeed(): ShortsSequenceResult {
        val userSubs = subscriptionRepository.getAllSubscriptionIds()

        // Discovery starts immediately in the background — never blocks the return path
        val discJob = repositoryScope.async {
            try {
                shortsDiscovery.getDiscoveryShorts(userSubs = userSubs, trending = emptyList())
            } catch (e: Exception) {
                Log.e(TAG, "ShortsDiscoveryEngine failed", e)
                null
            }
        }

        // InnerTube is the fast path — typically returns in 1–3 s
        val innerTubeResult = try {
            withTimeoutOrNull(INNERTUBE_TIMEOUT_MS) { fetchFromInnerTubeRaw(null) }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube feed failed: ${e.message}")
            null
        }

        if (innerTubeResult != null && innerTubeResult.shorts.isNotEmpty()) {
            val itShorts = filterWatchedShorts(innerTubeResult.shorts)
            if (itShorts.isEmpty()) {
                Log.i(TAG, "InnerTube fast-path contained only watched Shorts")
            } else {
            markAsShown(itShorts.map { it.id })
            itShorts.forEach { shortsCache.put(it.id, it) }

            repositoryScope.launch { preResolveStreams(itShorts.take(2).map { it.id }) }

            val earlyResult = ShortsSequenceResult(itShorts, innerTubeResult.continuation)
            cachedInitialFeed = earlyResult
            cachedFeedTimestamp = System.currentTimeMillis()
            Log.i(TAG, "✓ InnerTube fast-path: ${itShorts.size} shorts — returning immediately")

            repositoryScope.launch {
                val ranked = discJob.await()
                val existingIds = itShorts.map { it.id }.toHashSet()
                val newCandidates = ranked
                    ?.filter { it.id !in recentlyShownIds && it.id !in existingIds }
                    ?.let { deduplicateByTitle(it) }
                    ?.map { it.toShortVideo() }
                    ?.let { filterWatchedShorts(it) }
                    ?.let { orderShortsNewestFirst(it) }
                    .orEmpty()

                if (newCandidates.isNotEmpty()) {
                    markAsShown(newCandidates.map { it.id })
                    newCandidates.forEach { shortsCache.put(it.id, it) }
                    _discoveryFeedUpdate.tryEmit(newCandidates)
                    cachedInitialFeed = ShortsSequenceResult(
                        itShorts + newCandidates, innerTubeResult.continuation
                    )
                }

                val allShorts = cachedInitialFeed?.shorts ?: itShorts
                try {
                    withTimeoutOrNull(ENRICHMENT_TIMEOUT_MS) {
                        val enriched = enrichMissingMetadata(allShorts)
                        enriched.forEach { shortsCache.put(it.id, it) }
                        val withAvatars = enrichAvatarsForShorts(enriched)
                        withAvatars.forEach { shortsCache.put(it.id, it) }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Background enrichment failed: ${e.message}")
                }
            }

            return earlyResult
            }
        }

        // InnerTube unavailable — await discovery or NewPipe fallback
        Log.w(TAG, "InnerTube failed — awaiting discovery result")
        val rawDiscovery = discJob.await()
        val discoveryVideos: List<io.github.aedev.flow.data.model.Video> =
            if (!rawDiscovery.isNullOrEmpty()) rawDiscovery else emptyList()

        if (discoveryVideos.isEmpty()) {
            Log.w(TAG, "⟳ All sources empty — falling back to NewPipe")
            val newPipeResult = try {
                withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) { fetchFromNewPipe() }
            } catch (e: Exception) {
                Log.e(TAG, "NewPipe fallback failed: ${e.message}")
                null
            }
            if (newPipeResult != null && newPipeResult.shorts.isNotEmpty()) {
                val reRanked = orderShortsNewestFirst(reRankWithFlowNeuro(newPipeResult.shorts, userSubs))
                val result = newPipeResult.copy(shorts = reRanked)
                result.shorts.forEach { shortsCache.put(it.id, it) }
                markAsShown(result.shorts.map { it.id })
                cachedInitialFeed = result
                cachedFeedTimestamp = System.currentTimeMillis()
                return result
            }
            Log.e(TAG, "✗ All Shorts sources failed — returning empty")
            return ShortsSequenceResult(emptyList(), null)
        }

        val candidateShorts = discoveryVideos
            .filter { it.id !in recentlyShownIds }
            .let { deduplicateByTitle(it) }
            .map { it.toShortVideo() }
            .let { filterWatchedShorts(it) }
            .let { orderShortsNewestFirst(it) }

        markAsShown(candidateShorts.map { it.id })
        candidateShorts.forEach { shortsCache.put(it.id, it) }

        val result = ShortsSequenceResult(candidateShorts, null)
        cachedInitialFeed = result
        cachedFeedTimestamp = System.currentTimeMillis()
        Log.i(TAG, "✓ Discovery-only feed: ${candidateShorts.size} shorts")

        repositoryScope.launch {
            try {
                withTimeoutOrNull(ENRICHMENT_TIMEOUT_MS) {
                    val enriched = enrichMissingMetadata(candidateShorts)
                    enriched.forEach { shortsCache.put(it.id, it) }
                    val withAvatars = enrichAvatarsForShorts(enriched)
                    withAvatars.forEach { shortsCache.put(it.id, it) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Background enrichment failed: ${e.message}")
            }
        }

        return result
    }
    
    /**
     * Load more shorts using continuation token (pagination).
     * 
     * @param continuation The continuation token from a previous [ShortsSequenceResult].
     * @return Next page of shorts with a new continuation token.
     */
    suspend fun loadMore(
        continuation: String?
    ): ShortsSequenceResult = withContext(Dispatchers.IO) {
        if (continuation == null) {
            Log.d(TAG, "No continuation token — cannot load more")
            return@withContext ShortsSequenceResult(emptyList(), null)
        }
        
        Log.d(TAG, "━━━ Loading More Shorts (continuation) ━━━")

        val userSubs = subscriptionRepository.getAllSubscriptionIds()

        // InnerTube continuation
        val result = try {
            withTimeoutOrNull(INNERTUBE_TIMEOUT_MS) {
                val page = YouTube.shorts(sequenceParams = continuation).getOrNull()
                if (page != null && page.items.isNotEmpty()) {
                    val shorts = page.items
                        .map { it.toShortVideo() }
                        .filter { it.id !in recentlyShownIds }
                        .let { filterWatchedShorts(it) }
                    ShortsSequenceResult(shorts, page.continuation)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube continuation failed: ${e.message}")
            null
        }
        
        if (result != null && result.shorts.isNotEmpty()) {
            Log.d(TAG, "✓ Loaded ${result.shorts.size} more shorts (pre-enrichment)")

            val recentlySeen = try {
                FlowNeuroEngine.getRecentlySeenShorts()
            } catch (e: Exception) { emptySet() }

            val freshShorts = result.shorts.filter { it.id !in recentlySeen }

            if (freshShorts.size < 3 && result.shorts.size > 3) {
                Log.i(TAG, "loadMore: ${result.shorts.size - freshShorts.size} seen Shorts filtered, triggering discovery refresh")
                return@withContext forceRefresh()
            }

            // Enrich metadata OUTSIDE the InnerTube timeout
            val metadataEnriched = try {
                withTimeoutOrNull(ENRICHMENT_TIMEOUT_MS) {
                    enrichMissingMetadata(freshShorts)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Enrichment for continuation failed: ${e.message}")
                null
            } ?: freshShorts

            val enriched = try {
                withTimeoutOrNull(ENRICHMENT_TIMEOUT_MS) {
                    enrichAvatarsForShorts(metadataEnriched)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Avatar enrichment for continuation failed: ${e.message}")
                null
            } ?: metadataEnriched

            val reRanked = orderShortsNewestFirst(reRankWithFlowNeuro(enriched, userSubs))
            val enrichedResult = result.copy(shorts = reRanked)
            enrichedResult.shorts.forEach { shortsCache.put(it.id, it) }
            markAsShown(enrichedResult.shorts.map { it.id })

            try {
                FlowNeuroEngine.recordSeenShorts(reRanked.map { it.id })
            } catch (e: Exception) {
                Log.w(TAG, "Failed to record seen Shorts in loadMore", e)
            }

            return@withContext enrichedResult
        }

        // Fallback: fresh NewPipe fetch
        Log.d(TAG, "⟳ Continuation failed, fetching fresh from NewPipe")
        try {
            val fallback = withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) {
                fetchFromNewPipe()
            }
            if (fallback != null) {
                val reRanked = orderShortsNewestFirst(reRankWithFlowNeuro(fallback.shorts, userSubs))
                val rankedFallback = fallback.copy(shorts = reRanked)
                rankedFallback.shorts.forEach { shortsCache.put(it.id, it) }
                markAsShown(rankedFallback.shorts.map { it.id })
                return@withContext rankedFallback
            }
        } catch (e: Exception) {
            Log.e(TAG, "NewPipe pagination fallback failed", e)
        }
        
        ShortsSequenceResult(emptyList(), null)
    }
    
    /**
     * Removes near-duplicate Shorts based on Jaccard title similarity.
     * Catches re-uploads and stolen content with different IDs but nearly
     * identical titles. Keeps the video with more views (likely the original).
     */
    private fun deduplicateByTitle(
        videos: List<io.github.aedev.flow.data.model.Video>
    ): List<io.github.aedev.flow.data.model.Video> {
        if (videos.size <= 1) return videos

        val result = mutableListOf<io.github.aedev.flow.data.model.Video>()
        val titleTokens = videos.map { video ->
            video to video.title.lowercase()
                .split(Regex("\\s+"))
                .map { it.trim { c -> !c.isLetterOrDigit() } }
                .filter { it.length > 2 }
                .toSet()
        }
        val consumed = mutableSetOf<Int>()

        for (i in titleTokens.indices) {
            if (i in consumed) continue
            var bestVideo = titleTokens[i].first
            val bestTokens = titleTokens[i].second

            for (j in i + 1 until titleTokens.size) {
                if (j in consumed) continue
                val otherTokens = titleTokens[j].second
                if (bestTokens.isEmpty() || otherTokens.isEmpty()) continue

                val intersection = bestTokens.intersect(otherTokens).size
                val union = bestTokens.union(otherTokens).size
                val similarity = if (union > 0) intersection.toDouble() / union else 0.0

                if (similarity > 0.6) {
                    val otherVideo = titleTokens[j].first
                    if (otherVideo.viewCount > bestVideo.viewCount) bestVideo = otherVideo
                    consumed.add(j)
                }
            }
            result.add(bestVideo)
            consumed.add(i)
        }
        return result
    }

    private fun orderShortsNewestFirst(shorts: List<ShortVideo>): List<ShortVideo> =
        shorts.sortedByDescending { it.timestamp }

    // FLOWNEURO RE-RANKING — YouTube algo primary, FlowNeuro personalization    
    /**
     * Re-rank shorts using FlowNeuroEngine.
     * 
     * Strategy: YouTube's algorithm provides the candidate pool (already high-quality),
     * FlowNeuro re-orders based on user's interest profile, watch history vectors,
     * time-of-day context, and curiosity gap scoring.
     * 
     * The first item is pinned (YouTube chose it for a reason), rest are re-ranked.
     */
    private suspend fun reRankWithFlowNeuro(
        shorts: List<ShortVideo>,
        userSubs: Set<String> = emptySet()
    ): List<ShortVideo> {
        if (shorts.size <= 2) return shorts
        return try {
            FlowNeuroEngine.initialize(context)
            val pinned = shorts.first()
            val candidates = shorts.drop(1)
            val videosCandidates = candidates.map { it.toVideo() }
            val ranked = FlowNeuroEngine.rank(
                candidates = videosCandidates,
                userSubs = userSubs
            )
            val rankedIds = ranked.map { it.id }
            val shortById = candidates.associateBy { it.id }
            val reRanked = rankedIds.mapNotNull { shortById[it] }
            FlowNeuroEngine.recordFeedImpressions(listOf(pinned.toVideo()) + ranked)
            Log.d(TAG, "✓ FlowNeuro re-ranked ${reRanked.size} shorts")
            orderShortsNewestFirst(listOf(pinned) + reRanked)
        } catch (e: Exception) {
            Log.w(TAG, "FlowNeuro re-ranking failed, using original order: ${e.message}")
            orderShortsNewestFirst(shorts)
        }
    }
    
    // STREAM RESOLUTION — For Player Setup
    /**
     * Resolve stream info for a Short video.
     * Uses InnerTube ANDROID player endpoint first (better format support),
     * falls back to NewPipe extractor.
     * 
     * Results are cached to avoid re-resolution on swipe-back.
     */
    suspend fun resolveStreamInfo(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
        streamInfoCache.get(videoId)?.let {
            Log.d(TAG, "♻ Stream cache hit: $videoId")
            return@withContext it
        }
        
        Log.d(TAG, "⟳ Resolving stream: $videoId")
        
        val streamInfo = try {
            withTimeoutOrNull(STREAM_RESOLVE_TIMEOUT_MS) {
                youtubeRepository.getVideoStreamInfo(videoId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream resolution failed for $videoId: ${e.message}")
            null
        }
        
        if (streamInfo != null) {
            streamInfoCache.put(videoId, streamInfo)
            Log.d(TAG, "✓ Resolved streams for $videoId")
        } else {
            Log.e(TAG, "✗ Failed to resolve streams for $videoId")
        }
        
        streamInfo
    }
    
    suspend fun resolvePlaybackStreams(
        videoId: String,
        targetHeight: Int,
        preferredAudioLanguage: String
    ): ShortPlaybackStreams? = withContext(Dispatchers.IO) {
        val cacheKey = "$videoId|$targetHeight|$preferredAudioLanguage"
        playbackStreamsCache.get(cacheKey)?.let { return@withContext it }

        val inFlight = streamResolveMutex.withLock {
            playbackStreamsInFlight[cacheKey]?.let { return@withLock it }
            repositoryScope.async {
                resolvePlaybackStreamsUncached(videoId, targetHeight, preferredAudioLanguage)
            }.also { playbackStreamsInFlight[cacheKey] = it }
        }

        try {
            inFlight.await()?.also { playbackStreamsCache.put(cacheKey, it) }
        } finally {
            streamResolveMutex.withLock {
                if (playbackStreamsInFlight[cacheKey] === inFlight) {
                    playbackStreamsInFlight.remove(cacheKey)
                }
            }
        }
    }

    private suspend fun resolvePlaybackStreamsUncached(
        videoId: String,
        targetHeight: Int,
        preferredAudioLanguage: String
    ): ShortPlaybackStreams? {
        resolveFromUnifiedExtractor(videoId, targetHeight, preferredAudioLanguage)?.let { return it }

        resolveFromInnerTubePlayer(videoId, targetHeight, preferredAudioLanguage)?.let { return it }

        val streamInfo = resolveStreamInfo(videoId) ?: return null
        val allVideoStreams = (streamInfo.videoStreams.orEmpty() + streamInfo.videoOnlyStreams.orEmpty())
        fun qualityHeight(stream: org.schabi.newpipe.extractor.stream.VideoStream): Int {
            return QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(stream))
        }
        val videoStream = if (targetHeight == 0) {
            allVideoStreams.maxByOrNull { qualityHeight(it) }
        } else {
            allVideoStreams.filter { qualityHeight(it) <= targetHeight }.maxByOrNull { qualityHeight(it) }
                ?: allVideoStreams.minByOrNull { qualityHeight(it) }
        }

        val audioCandidates = streamInfo.audioStreams
            ?.sortedByDescending { it.averageBitrate } ?: emptyList()
        val audioStream = when (preferredAudioLanguage) {
            "original", "" -> audioCandidates.firstOrNull { stream ->
                stream.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
            } ?: audioCandidates.firstOrNull { stream ->
                stream.audioTrackType != org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED
            } ?: audioCandidates.firstOrNull()
            else -> audioCandidates.firstOrNull { a ->
                val lang = a.audioLocale?.language ?: ""
                lang.startsWith(preferredAudioLanguage, true)
            } ?: audioCandidates.firstOrNull { stream ->
                stream.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
            } ?: audioCandidates.firstOrNull()
        }

        val videoUrl = videoStream?.content ?: videoStream?.url ?: return null
        return ShortPlaybackStreams(
            videoUrl = videoUrl,
            audioUrl = audioStream?.content ?: audioStream?.url,
            durationMs = streamInfo.duration.takeIf { it > 0 }?.let { it * 1000L }
        )
    }

    suspend fun getAvailableVideoQualities(videoId: String): List<ShortVideoQuality> = withContext(Dispatchers.IO) {
        try {
            val result = withTimeoutOrNull(STREAM_RESOLVE_TIMEOUT_MS) {
                InnerTubeVideoStreamExtractor.extract(videoId)
            }
            val formats = result?.videoFormats?.filter { !it.url.isNullOrBlank() }.orEmpty()
            if (formats.isNotEmpty()) {
                return@withContext formats
                    .groupBy { shortsQualityClass(it) }
                    .mapNotNull { (cls, group) ->
                        val best = group.maxByOrNull { it.averageBitrate ?: it.bitrate } ?: return@mapNotNull null
                        val url = best.url ?: return@mapNotNull null
                        ShortVideoQuality(cls, "${cls}p", url, codecLabelFromMime(best.mimeType))
                    }
                    .sortedByDescending { it.heightClass }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getAvailableVideoQualities (unified) failed for $videoId: ${e.message}")
        }

        val streamInfo = resolveStreamInfo(videoId) ?: return@withContext emptyList()
        (streamInfo.videoStreams.orEmpty() + streamInfo.videoOnlyStreams.orEmpty())
            .filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>()
            .groupBy { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) }
            .mapNotNull { (cls, group) ->
                val best = group.maxByOrNull { it.bitrate } ?: return@mapNotNull null
                val url = best.content ?: best.url ?: return@mapNotNull null
                ShortVideoQuality(cls, "${cls}p", url, best.format?.name?.uppercase() ?: "")
            }
            .sortedByDescending { it.heightClass }
    }

    suspend fun getInnerTubeDownloadFormats(
        videoId: String
    ): Pair<List<PlayerResponse.StreamingData.Format>, List<PlayerResponse.StreamingData.Format>> =
        withContext(Dispatchers.IO) {
            try {
                val result = withTimeoutOrNull(STREAM_RESOLVE_TIMEOUT_MS) {
                    InnerTubeVideoStreamExtractor.extract(videoId)
                } ?: return@withContext emptyList<PlayerResponse.StreamingData.Format>() to emptyList<PlayerResponse.StreamingData.Format>()
                val video = result.videoFormats.filter { !it.url.isNullOrBlank() }
                val audio = result.audioFormats.filter { !it.url.isNullOrBlank() }
                video to audio
            } catch (e: Exception) {
                Log.w(TAG, "getInnerTubeDownloadFormats failed for $videoId: ${e.message}")
                emptyList<PlayerResponse.StreamingData.Format>() to emptyList<PlayerResponse.StreamingData.Format>()
            }
        }

    private fun codecLabelFromMime(mime: String): String = when {
        mime.contains("av01", true) -> "AV1"
        mime.contains("vp9", true) || mime.contains("vp09", true) -> "VP9"
        mime.contains("avc", true) || mime.contains("mp4", true) -> "H.264"
        mime.contains("vp8", true) -> "VP8"
        else -> ""
    }

    private fun shortsQualityClass(f: PlayerResponse.StreamingData.Format): Int {
        f.qualityLabel?.let { label ->
            Regex("(\\d+)p").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return it }
        }
        val w = f.width ?: 0
        val h = f.height ?: 0
        return if (w > 0 && h > 0) minOf(w, h) else maxOf(w, h)
    }

    private fun selectVideoForTarget(
        videoFormats: List<PlayerResponse.StreamingData.Format>,
        targetHeight: Int
    ): PlayerResponse.StreamingData.Format? =
        if (targetHeight == 0) {
            videoFormats.maxByOrNull { shortsQualityClass(it) }
        } else {
            videoFormats.filter { shortsQualityClass(it) <= targetHeight }.maxByOrNull { shortsQualityClass(it) }
                ?: videoFormats.minByOrNull { shortsQualityClass(it) }
        }

    private fun selectAudioForLanguage(
        audioFormats: List<PlayerResponse.StreamingData.Format>,
        preferredAudioLanguage: String
    ): PlayerResponse.StreamingData.Format? {
        val sorted = audioFormats.sortedByDescending {
            (it.averageBitrate ?: it.bitrate) + if (it.mimeType.contains("webm", true)) 10_000 else 0
        }
        return when (preferredAudioLanguage) {
            "original", "" -> sorted.firstOrNull { it.isOriginal } ?: sorted.firstOrNull()
            else -> sorted.firstOrNull { format ->
                format.audioTrack?.id?.substringAfterLast(".")?.startsWith(preferredAudioLanguage, true) == true
            } ?: sorted.firstOrNull { it.isOriginal } ?: sorted.firstOrNull()
        }
    }

    private suspend fun resolveFromUnifiedExtractor(
        videoId: String,
        targetHeight: Int,
        preferredAudioLanguage: String
    ): ShortPlaybackStreams? {
        return try {
            val result = withTimeoutOrNull(STREAM_RESOLVE_TIMEOUT_MS) {
                InnerTubeVideoStreamExtractor.extract(videoId)
            } ?: return null

            val videoFormats = result.videoFormats.filter { !it.url.isNullOrBlank() }
            if (videoFormats.isEmpty()) return null

            val selectedVideo = selectVideoForTarget(videoFormats, targetHeight) ?: return null
            val videoUrl = selectedVideo.url ?: return null

            val audioFormats = result.audioFormats.filter { !it.url.isNullOrBlank() }
            val selectedAudio = selectAudioForLanguage(audioFormats, preferredAudioLanguage)

            Log.d(TAG, "✓ Unified extractor resolved $videoId at ${shortsQualityClass(selectedVideo)}p via ${result.usedClient.clientName}")
            ShortPlaybackStreams(
                videoUrl = videoUrl,
                audioUrl = selectedAudio?.url,
                durationMs = result.playerResponse.videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000L)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Unified extractor resolve failed for $videoId: ${e.message}")
            null
        }
    }

    private suspend fun resolveFromInnerTubePlayer(
        videoId: String,
        targetHeight: Int,
        preferredAudioLanguage: String
    ): ShortPlaybackStreams? {
        return try {
            val response = withTimeoutOrNull(3_500L) {
                YouTube.shortsPlayer(videoId).getOrNull()
                    ?: YouTube.player(videoId, client = YouTubeClient.ANDROID).getOrNull()
            } ?: return null

            val streamingData = response.streamingData ?: return null

            val audioFormats = streamingData.adaptiveFormats
                .filter { it.isAudio && (!it.url.isNullOrBlank() || !it.signatureCipher.isNullOrBlank()) }
                .sortedByDescending { (it.averageBitrate ?: it.bitrate) + if (it.mimeType.contains("webm", true)) 10_000 else 0 }

            val adaptiveVideo = streamingData.adaptiveFormats
                .filter { !it.isAudio && it.height != null && (!it.url.isNullOrBlank() || !it.signatureCipher.isNullOrBlank()) }
            val videoFormats = if (adaptiveVideo.isNotEmpty() && audioFormats.isNotEmpty()) {
                adaptiveVideo
            } else {
                (streamingData.formats.orEmpty() + streamingData.adaptiveFormats)
                    .filter { !it.isAudio && (!it.url.isNullOrBlank() || !it.signatureCipher.isNullOrBlank()) }
            }

            val selectedVideo = selectVideoForTarget(videoFormats, targetHeight) ?: return null
            val selectedAudio = selectAudioForLanguage(audioFormats, preferredAudioLanguage)

            val videoUrl = NewPipeExtractor.getStreamUrl(selectedVideo, videoId) ?: return null
            val audioUrl = selectedAudio?.let { NewPipeExtractor.getStreamUrl(it, videoId) }
            ShortPlaybackStreams(
                videoUrl = videoUrl,
                audioUrl = audioUrl,
                durationMs = response.videoDetails?.lengthSeconds?.toLongOrNull()?.times(1000L)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Fast Shorts stream resolve failed for $videoId: ${e.message}")
            null
        }
    }

    /**
     * Pre-resolve streams for multiple video IDs concurrently.
     * Used to pre-buffer adjacent shorts in the pager.
     */
    suspend fun preResolveStreams(videoIds: List<String>) = supervisorScope {
        val uncached = videoIds.filter { streamInfoCache.get(it) == null }
        if (uncached.isEmpty()) return@supervisorScope
        
        Log.d(TAG, "⟳ Pre-resolving ${uncached.size} streams: ${uncached.joinToString()}")
        
        uncached.map { videoId ->
            async(Dispatchers.IO) {
                try {
                    withTimeoutOrNull(STREAM_RESOLVE_TIMEOUT_MS) {
                        resolveStreamInfo(videoId)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Pre-resolve failed for $videoId")
                    null
                }
            }
        }.forEach { it.await() } 
    }
    
    // HOME FEED SHORTS — For the Home screen's Shorts shelf
    /**
     * Get a small batch of shorts for the home screen shelf.
     * Uses cached feed if available, otherwise fetches fresh.
     * Returns up to 20 items for a populated shelf.
     */
    suspend fun getHomeFeedShorts(): List<ShortVideo> = withContext(Dispatchers.IO) {
        try {
            val result = getShortsFeed()
            orderShortsNewestFirst(filterWatchedShorts(result.shorts)).take(20)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get home feed shorts", e)
            emptyList()
        }
    }
    
    // INTERNAL — InnerTube Fetching    
    private suspend fun fetchFromInnerTubeRaw(
        seedVideoId: String? = null
    ): ShortsSequenceResult {
        val page = if (seedVideoId != null) {
            YouTube.shortsFromVideo(seedVideoId).getOrNull()
        } else {
            YouTube.shorts().getOrNull()
        }
        
        if (page == null || page.items.isEmpty()) {
            return ShortsSequenceResult(emptyList(), null)
        }
        
        val shorts = page.items.map { it.toShortVideo() }
        
        return ShortsSequenceResult(shorts, page.continuation)
    }
    
    // INTERNAL — Metadata Enrichment    
    private suspend fun enrichMissingMetadata(shorts: List<ShortVideo>): List<ShortVideo> = supervisorScope {
        val needsEnrichment = shorts.filter { 
            it.title == "Short" || it.channelName == "Unknown" || it.channelName.isBlank() 
        }
        
        if (needsEnrichment.isEmpty()) return@supervisorScope shorts
        
        Log.d(TAG, "⟳ Enriching metadata for ${needsEnrichment.size}/${shorts.size} shorts via player() endpoint")
        
        val enrichedMap = mutableMapOf<String, ShortVideo>()
        
        needsEnrichment.chunked(5).forEach { batch ->
            val batchResults = batch.map { short ->
                async(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(5_000L) {
                            val playerResponse = YouTube.player(
                                videoId = short.id,
                                client = YouTubeClient.ANDROID
                            ).getOrNull()
                            
                            val details = playerResponse?.videoDetails
                            if (details != null) {
                                short.copy(
                                    title = details.title?.takeIf { it.isNotBlank() } ?: short.title,
                                    channelName = details.author?.takeIf { it.isNotBlank() } ?: short.channelName,
                                    channelId = details.channelId.takeIf { it.isNotBlank() } ?: short.channelId,
                                    viewCountText = if (short.viewCountText.isBlank() && details.viewCount != null) {
                                        formatEnrichViewCount(details.viewCount.toLongOrNull() ?: 0L)
                                    } else short.viewCountText
                                )
                            } else short
                        } ?: short
                    } catch (e: Exception) {
                        Log.w(TAG, "Player enrichment failed for ${short.id}: ${e.message}")
                        short
                    }
                }
            }.awaitAll()
            
            batchResults.forEach { enrichedMap[it.id] = it }
            
            val partiallyEnriched = shorts.map { enrichedMap[it.id] ?: it }
            _enrichmentUpdates.tryEmit(partiallyEnriched)
        }
        
        val result = shorts.map { enrichedMap[it.id] ?: it }
        Log.d(TAG, "✓ Enriched ${enrichedMap.size}/${needsEnrichment.size} shorts via player() endpoint")
        result
    }
    
    private fun formatEnrichViewCount(count: Long): String = when {
        count >= 1_000_000_000 -> String.format("%.1fB views", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM views", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK views", count / 1_000.0)
        count > 0 -> "$count views"
        else -> ""
    }

    /**
     * Second-pass enrichment: fills in missing [ShortVideo.channelThumbnailUrl]
     * by looking up channel avatars via NewPipe. Results are emitted via
     * [_enrichmentUpdates] so the UI can refresh thumbnails live.
     */
    private suspend fun enrichAvatarsForShorts(shorts: List<ShortVideo>): List<ShortVideo> = supervisorScope {
        val channelIds = shorts
            .filter { it.channelThumbnailUrl.isEmpty() && it.channelId.isNotEmpty() }
            .map { it.channelId }
            .distinct()

        if (channelIds.isEmpty()) return@supervisorScope shorts

        Log.d(TAG, "⟳ Avatar enrichment for ${channelIds.size} channels in ${shorts.size} shorts")
        val avatarMap = mutableMapOf<String, String>()
        channelIds.chunked(4).forEach { batch ->
            batch.map { id ->
                async(Dispatchers.IO) { withTimeoutOrNull(6_000L) { id to youtubeRepository.fetchChannelAvatarById(id) } }
            }.awaitAll().forEach { pair ->
                pair?.let { (id, url) -> if (url.isNotEmpty()) avatarMap[id] = url }
            }
        }

        if (avatarMap.isEmpty()) return@supervisorScope shorts

        val result = shorts.map { short ->
            if (short.channelThumbnailUrl.isEmpty())
                avatarMap[short.channelId]?.let { short.copy(channelThumbnailUrl = it) } ?: short
            else short
        }
        Log.d(TAG, "✓ Avatar enrichment done: ${avatarMap.size}/${channelIds.size} channels resolved")
        _enrichmentUpdates.tryEmit(result)
        result
    }
    
    // INTERNAL — NewPipe Fallback Fetching    
    private suspend fun fetchFromNewPipe(): ShortsSequenceResult {
        val (videos, _) = youtubeRepository.getShorts()
        val shorts = videos
            .filter { it.duration in 1..60 }
            .map { it.toShortVideo() }
            .filter { it.id !in recentlyShownIds }
            .let { filterWatchedShorts(it) }
            .let { orderShortsNewestFirst(it) }
        
        return ShortsSequenceResult(shorts, null)
    }

    private suspend fun filterWatchedShorts(shorts: List<ShortVideo>): List<ShortVideo> {
        if (shorts.isEmpty()) return shorts
        val watchedIds = runCatching { viewHistory.getWatchedShortIdsAboveThreshold(90f) }
            .getOrDefault(emptySet())
        if (watchedIds.isEmpty()) return shorts
        return shorts.filter { it.id !in watchedIds }
    }
    
    // INTERNAL — Recently Shown Tracking
    private fun markAsShown(ids: List<String>) {
        recentlyShownIds.addAll(ids)
        if (recentlyShownIds.size > MAX_RECENTLY_SHOWN) {
            val excess = recentlyShownIds.size - MAX_RECENTLY_SHOWN
            val iterator = recentlyShownIds.iterator()
            repeat(excess) {
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
        }
    }
    
    // CACHE MANAGEMENT
    /**
     * Get a cached ShortVideo by ID if available.
     */
    fun getCachedShort(videoId: String): ShortVideo? = shortsCache.get(videoId)
    
    /**
     * Clear all caches. Used for debugging or force-refresh.
     */
    fun clearCaches() {
        streamInfoCache.evictAll()
        playbackStreamsCache.evictAll()
        shortsCache.evictAll()
        recentlyShownIds.clear()
        cachedInitialFeed = null
        cachedFeedTimestamp = 0L
        shortsDiscovery.clearCaches()
        Log.d(TAG, "All caches cleared")
    }

    fun evictChannel(channelId: String) {
        shortsDiscovery.evictChannel(channelId)
        val current = cachedInitialFeed
        if (current != null) {
            val filtered = current.shorts.filter { it.channelId != channelId }
            cachedInitialFeed = current.copy(shorts = filtered)
        }
        Log.d(TAG, "Evicted channel $channelId from Shorts caches")
    }
    
    /**
     * Force refresh — clears caches and fetches fresh.
     */
    suspend fun forceRefresh(): ShortsSequenceResult {
        clearCaches()
        return getShortsFeed()
    }
}

data class ShortPlaybackStreams(
    val videoUrl: String,
    val audioUrl: String?,
    val durationMs: Long?
)

data class ShortVideoQuality(
    val heightClass: Int,
    val label: String,
    val videoUrl: String,
    val codecLabel: String
)
