package com.flow.youtube.data.shorts

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.flow.youtube.data.model.ShortVideo
import com.flow.youtube.data.model.ShortsSequenceResult
import com.flow.youtube.data.model.toShortVideo
import com.flow.youtube.data.model.toVideo
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.data.repository.YouTubeRepository
import com.flow.youtube.innertube.YouTube
import com.flow.youtube.innertube.models.YouTubeClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * Shorts Repository — InnerTube Reel API Primary, NewPipe Fallback
 * 
 * Architecture:
 * 1. PRIMARY: YouTube InnerTube reel/reel_watch_sequence endpoint
 *    - Returns algorithmically-ordered shorts from YouTube's recommendation engine
 *    - Supports continuation-based infinite scroll
 *    - Provides sequenceParams/playerParams for optimal playback
 * 
 * 2. FALLBACK: NewPipe Extractor search-based discovery
 *    - Used when InnerTube fails (rate limits, API changes, network issues)
 *    - Searches "#shorts" and filters by duration ≤ 60s
 * 
 * 3. CACHING:
 *    - Stream URL cache (LRU, 50 entries) — avoids re-resolving on swipe-back
 *    - StreamInfo cache (LRU, 30 entries) — full stream metadata for player setup
 *    - Shorts feed is NOT disk-cached (InnerTube provides fresh algorithmic content)
 */
class ShortsRepository private constructor(private val context: Context) {
    
    private val youtubeRepository = YouTubeRepository.getInstance()
    
    // In-memory caches — ephemeral, cleared when app process dies
    private val streamInfoCache = LruCache<String, StreamInfo>(30)
    private val shortsCache = LruCache<String, ShortVideo>(100)
    
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
    
    companion object {
        private const val TAG = "ShortsRepository"
        private const val INNERTUBE_TIMEOUT_MS = 8_000L
        private const val NEWPIPE_TIMEOUT_MS = 8_000L
        private const val STREAM_RESOLVE_TIMEOUT_MS = 8_000L
        private const val ENRICHMENT_TIMEOUT_MS = 12_000L // Dedicated timeout for metadata enrichment
        private const val MAX_RECENTLY_SHOWN = 100
        
        @Volatile
        private var INSTANCE: ShortsRepository? = null
        
        fun getInstance(context: Context): ShortsRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ShortsRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    // FEED FETCHING — InnerTube Primary, NewPipe Fallback
    /**
     * Fetch the initial Shorts feed.
     * 
     * @param seedVideoId Optional video ID to start the reel sequence from.
     *                    If null, returns YouTube's default algorithmic feed.
     * @return [ShortsSequenceResult] with shorts list and continuation token.
     */
    suspend fun getShortsFeed(
        seedVideoId: String? = null
    ): ShortsSequenceResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "━━━ Fetching Shorts Feed (seed=$seedVideoId) ━━━")
        
        if (seedVideoId == null) {
            val cached = cachedInitialFeed
            if (cached != null && System.currentTimeMillis() - cachedFeedTimestamp < CACHE_TTL_MS && cached.shorts.isNotEmpty()) {
                Log.d(TAG, "♻ Using cached feed (${cached.shorts.size} shorts)")
                return@withContext cached
            }
        }
        
        val rawResult = try {
            withTimeoutOrNull(INNERTUBE_TIMEOUT_MS) {
                fetchFromInnerTubeRaw(seedVideoId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube feed failed: ${e.message}")
            null
        }
        
        if (rawResult != null && rawResult.shorts.isNotEmpty()) {
            Log.d(TAG, "✓ InnerTube returned ${rawResult.shorts.size} shorts (pre-enrichment)")
            // Re-rank with FlowNeuroEngine for personalization
            val reRanked = reRankWithFlowNeuro(rawResult.shorts)
            val result = rawResult.copy(shorts = reRanked)
            
            result.shorts.forEach { shortsCache.put(it.id, it) }
            markAsShown(result.shorts.map { it.id })
            
            val enriched = try {
                withTimeoutOrNull(ENRICHMENT_TIMEOUT_MS) {
                    enrichMissingMetadata(result.shorts)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Enrichment failed, using raw data: ${e.message}")
                null
            } ?: result.shorts
            
            val finalResult = result.copy(shorts = enriched)
            // Update cache with enriched data
            finalResult.shorts.forEach { shortsCache.put(it.id, it) }
            
            if (seedVideoId == null) {
                cachedInitialFeed = finalResult
                cachedFeedTimestamp = System.currentTimeMillis()
            }
            
            return@withContext finalResult
        }
        
        // Fallback to NewPipe
        Log.d(TAG, "⟳ Falling back to NewPipe...")
        val newPipeResult = try {
            withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) {
                fetchFromNewPipe()
            }
        } catch (e: Exception) {
            Log.e(TAG, "NewPipe fallback also failed: ${e.message}")
            null
        }
        
        if (newPipeResult != null && newPipeResult.shorts.isNotEmpty()) {
            Log.d(TAG, "✓ NewPipe returned ${newPipeResult.shorts.size} shorts")
            newPipeResult.shorts.forEach { shortsCache.put(it.id, it) }
            markAsShown(newPipeResult.shorts.map { it.id })
            if (seedVideoId == null) {
                cachedInitialFeed = newPipeResult
                cachedFeedTimestamp = System.currentTimeMillis()
            }
            return@withContext newPipeResult
        }
        
        Log.e(TAG, "✗ Both sources failed — returning empty")
        ShortsSequenceResult(emptyList(), null)
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
        
        // InnerTube continuation
        val result = try {
            withTimeoutOrNull(INNERTUBE_TIMEOUT_MS) {
                val page = YouTube.shorts(sequenceParams = continuation).getOrNull()
                if (page != null && page.items.isNotEmpty()) {
                    val shorts = page.items
                        .map { it.toShortVideo() }
                        .filter { it.id !in recentlyShownIds }
                    ShortsSequenceResult(shorts, page.continuation)
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "InnerTube continuation failed: ${e.message}")
            null
        }
        
        if (result != null && result.shorts.isNotEmpty()) {
            Log.d(TAG, "✓ Loaded ${result.shorts.size} more shorts (pre-enrichment)")
            
            // Enrich metadata OUTSIDE the InnerTube timeout
            val enriched = try {
                withTimeoutOrNull(ENRICHMENT_TIMEOUT_MS) {
                    enrichMissingMetadata(result.shorts)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Enrichment for continuation failed: ${e.message}")
                null
            } ?: result.shorts
            
            val enrichedResult = result.copy(shorts = enriched)
            enrichedResult.shorts.forEach { shortsCache.put(it.id, it) }
            markAsShown(enrichedResult.shorts.map { it.id })
            return@withContext enrichedResult
        }
        
        // Fallback: fresh NewPipe fetch
        Log.d(TAG, "⟳ Continuation failed, fetching fresh from NewPipe")
        try {
            val fallback = withTimeoutOrNull(NEWPIPE_TIMEOUT_MS) {
                fetchFromNewPipe()
            }
            if (fallback != null) {
                fallback.shorts.forEach { shortsCache.put(it.id, it) }
                markAsShown(fallback.shorts.map { it.id })
                return@withContext fallback
            }
        } catch (e: Exception) {
            Log.e(TAG, "NewPipe pagination fallback failed", e)
        }
        
        ShortsSequenceResult(emptyList(), null)
    }
    
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
    private suspend fun reRankWithFlowNeuro(shorts: List<ShortVideo>): List<ShortVideo> {
        if (shorts.size <= 2) return shorts
        
        return try {
            // Initialize engine if needed
            FlowNeuroEngine.initialize(context)
            
            // Pin the first short (YouTube's top pick), re-rank the rest
            val pinned = shorts.first()
            val candidates = shorts.drop(1)
            
            // Convert to Video for FlowNeuro
            val videosCandidates = candidates.map { it.toVideo() }
            
            // Re-rank with FlowNeuro
            val ranked = FlowNeuroEngine.rank(
                candidates = videosCandidates,
                userSubs = emptySet()
            )
            
            val rankedIds = ranked.map { it.id }
            val shortById = candidates.associateBy { it.id }
            val reRanked = rankedIds.mapNotNull { shortById[it] }
            
            val result = listOf(pinned) + reRanked
            Log.d(TAG, "✓ FlowNeuro re-ranked ${reRanked.size} shorts")
            result
        } catch (e: Exception) {
            Log.w(TAG, "FlowNeuro re-ranking failed, using YouTube order: ${e.message}")
            shorts 
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
            result.shorts.take(20)
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
    
    // INTERNAL — NewPipe Fallback Fetching    
    private suspend fun fetchFromNewPipe(): ShortsSequenceResult {
        val (videos, _) = youtubeRepository.getShorts()
        val shorts = videos
            .filter { it.duration in 1..60 }
            .map { it.toShortVideo() }
            .filter { it.id !in recentlyShownIds }
        
        return ShortsSequenceResult(shorts, null)
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
        shortsCache.evictAll()
        recentlyShownIds.clear()
        cachedInitialFeed = null
        cachedFeedTimestamp = 0L
        Log.d(TAG, "All caches cleared")
    }
    
    /**
     * Force refresh — clears caches and fetches fresh.
     */
    suspend fun forceRefresh(): ShortsSequenceResult {
        clearCaches()
        return getShortsFeed()
    }
}
