/*
 * Copyright (C) 2025 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package com.flow.youtube.data.shorts

import android.content.Context
import android.util.Log
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.recommendation.FlowNeuroEngine
import com.flow.youtube.data.repository.YouTubeRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * ShortsDiscoveryEngine — Topic-Aware Shorts Candidate Sourcing
 *
 * This engine builds a targeted candidate pool from three sources:
 *
 *   Phase 1 — SUBSCRIPTION SHORTS:
 *     Fetches recent uploads from subscribed channels and filters to <60s videos.
 *     These get the full subscription boost in rank(). High priority.
 *
 *   Phase 2 — TOPIC DISCOVERY SHORTS:
 *     Uses FlowNeuroEngine.generateDiscoveryQueries() to search for Shorts on
 *     topics the user genuinely cares about. Niche-specific query variants
 *     (#shorts suffix, tips/clip/highlights suffixes) broaden the pool.
 *
 *   Phase 3 — TRENDING FALLBACK:
 *     Only appended when phases 1+2 produce fewer than MIN_POOL_SIZE candidates.
 *     Acts as floor, not primary source.
 *
 * The result: instead of 100 videos from random trending Shorts, the pool
 * contains 60–120 videos that are already thematically pre-filtered before
 * rank() orders them. FlowNeuroEngine.rank() then fine-tunes the order.
 */
class ShortsDiscoveryEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "ShortsDiscovery"

        /** Max uploads to pull per subscription channel (filter by ≤60s after) */
        private const val UPLOADS_PER_CHANNEL = 20

        /** Max subscription channels to hit per refresh cycle */
        private const val MAX_SUB_CHANNELS = 15

        /** Max discovery search queries to run per refresh */
        private const val MAX_DISCOVERY_QUERIES = 6

        /** Max results to take from each discovery search */
        private const val SHORTS_PER_SEARCH = 15

        /** Minimum candidate pool before trending is appended as fallback */
        private const val MIN_POOL_SIZE = 10

        /** Cache TTL: per-channel upload results (30 minutes) */
        private const val CHANNEL_CACHE_TTL_MS = 30 * 60 * 1000L

        /** Cache TTL: per-query discovery results (15 minutes — rotate faster) */
        private const val DISCOVERY_CACHE_TTL_MS = 15 * 60 * 1000L

        /** LRU size for per-channel short cache */
        private const val CHANNEL_CACHE_MAX = 50

        /** Concurrent network request cap — avoids YouTube rate limiting */
        private const val MAX_CONCURRENT_REQUESTS = 3

        @Volatile
        private var instance: ShortsDiscoveryEngine? = null

        fun getInstance(context: Context): ShortsDiscoveryEngine =
            instance ?: synchronized(this) {
                instance ?: ShortsDiscoveryEngine(context.applicationContext).also { instance = it }
            }
    }

    // ── Dependencies ──

    private val youtubeRepository = YouTubeRepository.getInstance()
    private val subscriptionRepository = SubscriptionRepository.getInstance(appContext)

    private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

    private data class CachedShorts(val shorts: List<Video>, val timestamp: Long) {
        fun isFresh(ttlMs: Long) = System.currentTimeMillis() - timestamp < ttlMs
    }

    private val channelShortsCache = object : LinkedHashMap<String, CachedShorts>(
        CHANNEL_CACHE_MAX + 10, 0.75f, true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, CachedShorts>?
        ): Boolean = size > CHANNEL_CACHE_MAX
    }

    private val discoveryCache = HashMap<String, CachedShorts>()

    private val recentlyFetchedChannels = mutableSetOf<String>()
    private var lastChannelRotationTime = 0L

    /**
     * Builds a high-quality Shorts candidate pool from subscriptions + topic
     * discovery, then ranks the merged pool with FlowNeuroEngine.rank().
     *
     * @param userSubs  Set of subscribed channel IDs for the rank() sub-boost.
     * @param trending  Pre-fetched trending Shorts from InnerTube (used as
     *                  fallback/floor only — not the primary source).
     * @return          Ranked list ready to hand to ShortsRepository.
     */
    suspend fun getDiscoveryShorts(
        userSubs: Set<String>,
        trending: List<Video> = emptyList()
    ): List<Video> = withContext(Dispatchers.IO) {

        val seenIds = mutableSetOf<String>()
        val allCandidates = mutableListOf<Video>()

        fun addUnique(videos: List<Video>) {
            videos.forEach { v ->
                if (v.id.isNotBlank() && v.id !in seenIds) {
                    seenIds += v.id
                    allCandidates += v
                }
            }
        }

        // ── Phase 1: Subscription Shorts ──
        try {
            val subShorts = fetchSubscriptionShorts(userSubs)
            addUnique(subShorts)
            Log.i(TAG, "Phase 1: ${subShorts.size} Shorts from subscribed channels")
        } catch (e: Exception) {
            Log.e(TAG, "Phase 1 (subscription Shorts) failed", e)
        }

        // ── Phase 2: Topic Discovery Shorts ──
        try {
            val discoveryShorts = fetchDiscoveryShorts()
            addUnique(discoveryShorts)
            Log.i(TAG, "Phase 2: ${discoveryShorts.size} discovery Shorts")
        } catch (e: Exception) {
            Log.e(TAG, "Phase 2 (discovery Shorts) failed", e)
        }

        // ── Phase 3: Trending Fallback ──
        if (allCandidates.size < MIN_POOL_SIZE && trending.isNotEmpty()) {
            addUnique(trending)
            Log.i(TAG, "Phase 3: backfilled with ${trending.size} trending Shorts")
        }

        if (allCandidates.isEmpty()) {
            Log.w(TAG, "No candidates from any source — returning raw trending")
            return@withContext trending
        }

        // ── Rank everything through the engine ──
        val ranked = FlowNeuroEngine.rank(allCandidates, userSubs)
        Log.i(TAG, "Discovery complete: ${ranked.size} ranked from ${allCandidates.size} candidates")
        ranked
    }

    // ── Phase 1: Subscription Shorts ──

    private suspend fun fetchSubscriptionShorts(
        userSubs: Set<String>
    ): List<Video> = coroutineScope {
        if (userSubs.isEmpty()) return@coroutineScope emptyList()

        val now = System.currentTimeMillis()

        if (now - lastChannelRotationTime > CHANNEL_CACHE_TTL_MS) {
            recentlyFetchedChannels.clear()
            lastChannelRotationTime = now
        }

        // Prioritise channels not seen recently in this rotation window
        val channelsToFetch = userSubs
            .sortedBy { if (it in recentlyFetchedChannels) 1 else 0 }
            .take(MAX_SUB_CHANNELS)

        channelsToFetch.map { channelId ->
            async {
                try {
                    val cached = synchronized(channelShortsCache) { channelShortsCache[channelId] }
                    if (cached != null && cached.isFresh(CHANNEL_CACHE_TTL_MS)) {
                        return@async cached.shorts
                    }

                    val shorts = requestSemaphore.withPermit {
                        withTimeoutOrNull(8_000L) {
                            fetchShortsForChannel(channelId)
                        } ?: emptyList()
                    }

                    if (shorts.isNotEmpty()) {
                        synchronized(channelShortsCache) {
                            channelShortsCache[channelId] = CachedShorts(shorts, now)
                        }
                    }
                    recentlyFetchedChannels += channelId
                    shorts
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch Shorts for channel $channelId: ${e.message}")
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    private suspend fun fetchShortsForChannel(channelId: String): List<Video> {
        val uploads = youtubeRepository.getChannelUploads(channelId, UPLOADS_PER_CHANNEL)
        return uploads.filter { v -> v.duration in 1..120 || v.isShort }
    }

    // ── Phase 2: Topic Discovery Shorts ──

    private suspend fun fetchDiscoveryShorts(): List<Video> = coroutineScope {
        val now = System.currentTimeMillis()
        val queries = buildDiscoveryQueries().take(MAX_DISCOVERY_QUERIES)

        synchronized(discoveryCache) {
            discoveryCache.entries.removeAll { (_, v) ->
                now - v.timestamp > DISCOVERY_CACHE_TTL_MS * 4
            }
        }

        queries.map { query ->
            async {
                try {
                    val cached = synchronized(discoveryCache) { discoveryCache[query] }
                    if (cached != null && cached.isFresh(DISCOVERY_CACHE_TTL_MS)) {
                        return@async cached.shorts
                    }

                    val results = requestSemaphore.withPermit {
                        withTimeoutOrNull(8_000L) {
                            searchShorts(query)
                        } ?: emptyList()
                    }

                    if (results.isNotEmpty()) {
                        synchronized(discoveryCache) {
                            discoveryCache[query] = CachedShorts(results, now)
                        }
                    }
                    results
                } catch (e: Exception) {
                    Log.w(TAG, "Discovery search failed for '$query': ${e.message}")
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    /**
     * Builds Shorts-specific discovery queries from the engine's learned interests.
     *
     * Three strategies to maximise niche coverage:
     *  1. Top topics with "#shorts" (YouTube indexes this heavily)
     *  2. Engine's own discovery queries with "shorts" appended
     *  3. Topic-affinity bigrams for cross-niche discovery
     */
    private suspend fun buildDiscoveryQueries(): List<String> {
        val queries = mutableListOf<String>()

        val brain = FlowNeuroEngine.getBrainSnapshot()

        val topTopics = brain.globalVector.topics.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key }

        // Strategy 1: direct topic + #shorts
        topTopics.take(3).forEach { topic ->
            queries += "$topic #shorts"
        }

        // Strategy 2: engine's discovery queries + "shorts" suffix
        val baseQueries = try {
            FlowNeuroEngine.generateDiscoveryQueries()
        } catch (e: Exception) {
            emptyList()
        }
        baseQueries.take(3).forEach { q -> queries += "$q shorts" }

        // Strategy 3: topic-affinity bigrams
        brain.topicAffinities.entries
            .sortedByDescending { it.value }
            .take(3)
            .forEach { (key, _) ->
                val parts = key.split("|")
                if (parts.size == 2) queries += "${parts[0]} ${parts[1]} #shorts"
            }

        // Strategy 4: niche short-form content terms for top topics
        if (topTopics.isNotEmpty()) {
            val shortFormTerms = listOf("tips", "moment", "clip", "highlights", "motivation", "facts")
            topTopics.take(2).forEach { topic ->
                queries += "$topic ${shortFormTerms.random()} shorts"
            }
        }

        // Filter out queries that contain blocked topics
        val blocked = brain.blockedTopics
        return queries
            .distinct()
            .filter { q -> blocked.none { b -> q.lowercase().contains(b.lowercase()) } }
            .shuffled()
    }

    // Searches YouTube for Shorts using NewPipe's search extractor.
    private suspend fun searchShorts(
        query: String,
        maxResults: Int = SHORTS_PER_SEARCH
    ): List<Video> = withContext(Dispatchers.IO) {
        try {
            val service = NewPipe.getService(0)
            val extractor = service.getSearchExtractor(query)
            extractor.fetchPage()

            extractor.initialPage?.items
                ?.filterIsInstance<StreamInfoItem>()
                ?.filter { item ->
                    item.duration in 1..120 ||
                    item.url.contains("/shorts/", ignoreCase = true) ||
                    (item.isShortFormContent == true)
                }
                ?.take(maxResults)
                ?.map { item -> streamInfoItemToVideo(item) }
                ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "Shorts search failed for '$query': ${e.message}")
            emptyList()
        }
    }

    // ── Conversion ──

    private fun streamInfoItemToVideo(item: StreamInfoItem): Video {
        val url = item.url ?: ""
        val videoId = when {
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?").substringBefore("/")
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?")
            else -> url.substringAfterLast("/").substringBefore("?")
        }

        val uploaderUrl = item.uploaderUrl ?: ""
        val channelId = when {
            uploaderUrl.contains("/channel/") ->
                uploaderUrl.substringAfter("/channel/").substringBefore("/").substringBefore("?")
            uploaderUrl.contains("/@") ->
                uploaderUrl.substringAfter("/@").substringBefore("/").substringBefore("?")
            else -> uploaderUrl.substringAfterLast("/").substringBefore("?")
        }

        val isShort = item.isShortFormContent == true ||
                url.contains("/shorts/", ignoreCase = true) ||
                (item.duration in 1..60)

        return Video(
            id = videoId,
            title = item.name ?: "",
            channelName = item.uploaderName ?: "",
            channelId = channelId,
            thumbnailUrl = item.thumbnails?.maxByOrNull { it.height }?.url
                ?: if (videoId.isNotBlank()) "https://i.ytimg.com/vi/$videoId/hq720.jpg" else "",
            duration = item.duration.toInt().coerceAtLeast(0),
            viewCount = if (item.viewCount >= 0) item.viewCount else 0L,
            likeCount = 0L,
            uploadDate = item.textualUploadDate ?: "",
            isShort = isShort,
            isLive = false,
            description = ""
        )
    }

    // ── Cache Management ──
    fun clearCaches() {
        synchronized(channelShortsCache) { channelShortsCache.clear() }
        synchronized(discoveryCache) { discoveryCache.clear() }
        recentlyFetchedChannels.clear()
        Log.d(TAG, "Discovery caches cleared")
    }

    fun evictChannel(channelId: String) {
        synchronized(channelShortsCache) { channelShortsCache.remove(channelId) }
        Log.d(TAG, "Evicted channel $channelId from discovery cache")
    }
}
