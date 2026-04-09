/*
 * Copyright (C) 2025 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.shorts

import android.content.Context
import android.util.Log
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.data.repository.YouTubeRepository
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
        private const val UPLOADS_PER_CHANNEL = 15

        /** Max subscription channels to hit per refresh cycle */
        private const val MAX_SUB_CHANNELS = 8

        /** Max discovery search queries to run per refresh (3 = exactly one Semaphore(3) round) */
        private const val MAX_DISCOVERY_QUERIES = 3

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

        val recentlySeen = try {
            FlowNeuroEngine.getRecentlySeenShorts()
        } catch (e: Exception) {
            emptySet()
        }

        val seenIds = mutableSetOf<String>()
        seenIds.addAll(recentlySeen)
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
            val subSeenIds = mutableSetOf<String>()
            subShorts.forEach { v ->
                if (v.id.isNotBlank() && v.id !in subSeenIds) {
                    subSeenIds += v.id
                    allCandidates += v
                    seenIds += v.id
                }
            }
            Log.i(TAG, "Phase 1: ${subShorts.size} Shorts from subscribed channels")
        } catch (e: Exception) {
            Log.e(TAG, "Phase 1 (subscription Shorts) failed", e)
        }

        // ── Phase 2: Topic Discovery Shorts ──
        try {
            val rawDiscoveryShorts = fetchDiscoveryShorts()
            val qualityFiltered = filterLowQuality(rawDiscoveryShorts)
            addUnique(qualityFiltered)
            Log.i(TAG, "Phase 2: ${rawDiscoveryShorts.size} raw → "
                + "${qualityFiltered.size} after quality filter → "
                + "${allCandidates.size} total after dedup")
        } catch (e: Exception) {
            Log.e(TAG, "Phase 2 (discovery Shorts) failed", e)
        }

        // ── Phase 3: Trending Fallback ──
        if (allCandidates.size < MIN_POOL_SIZE && trending.isNotEmpty()) {
            addUnique(trending)
            Log.i(TAG, "Phase 3: backfilled to ${allCandidates.size}")
        }

        if (allCandidates.isEmpty()) {
            Log.w(TAG, "No candidates from any source — returning raw trending")
            return@withContext trending
        }

        // Rank everything through the engine
        val ranked = FlowNeuroEngine.rank(allCandidates, userSubs)

        try {
            FlowNeuroEngine.recordSeenShorts(ranked.map { it.id })
        } catch (e: Exception) {
            Log.w(TAG, "Failed to record seen Shorts", e)
        }

        Log.i(TAG, "Discovery complete: ${ranked.size} ranked from ${allCandidates.size} candidates "
            + "(${recentlySeen.size} excluded as seen)")
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
                        withTimeoutOrNull(4_000L) {
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
                        withTimeoutOrNull(4_000L) {
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
     * Filters out obvious low-quality Shorts before they enter the ranking engine.
     * Catches spam titles, placeholder titles, and emoji-only bots.
     */
    private fun filterLowQuality(shorts: List<Video>): List<Video> {
        return shorts.filter { video ->
            val titleLower = video.title.lowercase()

            if (video.title.isBlank() || video.title == "Short" ||
                video.title == "Shorts" || video.title.length < 3
            ) return@filter false

            val spamPatterns = listOf(
                "subscribe for more", "follow for more", "like and subscribe",
                "free v-bucks", "free robux", "link in bio", "dm for", "check bio"
            )
            if (spamPatterns.any { titleLower.contains(it) }) return@filter false

            val emojiCount = video.title.count { Character.getType(it) == Character.OTHER_SYMBOL.toInt() }
            val letterCount = video.title.count { it.isLetter() }
            if (emojiCount > letterCount && letterCount < 5) return@filter false

            true
        }
    }

    /**
     * Builds Shorts-specific discovery queries from the engine's learned interests.
     *
     * 8 diversification strategies to maximise niche coverage and freshness:
     *  1. Top 2 topics with "#shorts"
     *  2. Mid-tier topics (positions 3-5) for breadth
     *  3. Shorts-native phrasing variants per topic
     *  4. Cross-topic combinations
     *  5. Topic-affinity bigrams
     *  6. Topics the user hasn't explored in Shorts yet
     *  7. Persona-aware query suffix
     *  8. Time-rotated trending variants
     */
    private suspend fun buildDiscoveryQueries(): List<String> {
        val queries = mutableListOf<String>()
        val brain = FlowNeuroEngine.getBrainSnapshot()

        val topTopics = brain.globalVector.topics.entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key }

        val primaryTopics = topTopics

        // Strategy 1: Top 2 topics with #shorts
        primaryTopics.take(2).forEach { topic -> queries += "$topic #shorts" }

        // Strategy 2: Mid-tier topics (positions 3-5) — different results from dominant topics
        primaryTopics.drop(2).take(2).forEach { topic -> queries += "$topic #shorts" }

        // Strategy 3: Shorts-native phrasing — different pattern per topic for variety
        val shortsPhrasing = listOf(
            "POV %s", "%s motivation", "%s be like", "%s in 60 seconds",
            "day in the life %s", "%s tips you need", "%s transformation",
            "%s challenge", "things about %s", "%s moment"
        )
        primaryTopics.take(3).forEachIndexed { index, topic ->
            val phrasing = shortsPhrasing[(index * 3) % shortsPhrasing.size]
            queries += String.format(phrasing, topic)
        }

        // Strategy 4: Cross-topic combinations
        if (primaryTopics.size >= 2) queries += "${primaryTopics[0]} ${primaryTopics[1]} shorts"
        if (primaryTopics.size >= 4) queries += "${primaryTopics[2]} ${primaryTopics[3]} shorts"

        // Strategy 5: Topic-affinity bigrams
        brain.topicAffinities.entries
            .sortedByDescending { it.value }
            .take(2)
            .forEach { (key, _) ->
                val parts = key.split("|")
                if (parts.size == 2) queries += "${parts[0]} ${parts[1]} #shorts"
            }

        // Strategy 6: Topics unexplored in Shorts
        val baseQueries = try {
            FlowNeuroEngine.generateDiscoveryQueries()
        } catch (e: Exception) { emptyList() }
        baseQueries.take(2).forEach { q -> queries += "$q shorts" }

        // Strategy 7: Persona-aware query suffix
        val persona = try {
            FlowNeuroEngine.getPersona(brain)
        } catch (e: Exception) { null }
        val personaSuffix = when (persona) {
            FlowPersona.AUDIOPHILE -> "music edit"
            FlowPersona.SCHOLAR -> "explained quick"
            FlowPersona.DEEP_DIVER -> "documentary clip"
            FlowPersona.SKIMMER -> "satisfying"
            FlowPersona.BINGER -> "series part"
            FlowPersona.SPECIALIST -> "deep dive"
            else -> null
        }
        if (personaSuffix != null && primaryTopics.isNotEmpty()) {
            queries += "${primaryTopics[0]} $personaSuffix #shorts"
        }

        // Strategy 8: Time-rotated queries (guard against empty topic list for new users)
        if (primaryTopics.isNotEmpty()) {
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val timeRotation = hour / 6
            val rotatedTopic = primaryTopics[timeRotation % primaryTopics.size]
            val timeSuffixes = listOf("trending", "viral", "new", "best")
            queries += "$rotatedTopic ${timeSuffixes[timeRotation]} shorts"
        }

        val blocked = brain.blockedTopics
        return queries
            .distinct()
            .filter { q -> blocked.none { b -> q.lowercase().contains(b.lowercase()) } }
            .shuffled()
            .take(MAX_DISCOVERY_QUERIES)
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
                url.contains("/shorts/", ignoreCase = true)

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
