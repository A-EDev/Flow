/*
 * Copyright (C) 2025 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.shorts

import android.util.Log
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.reel.ReelLockup
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The reel candidate pool the algorithmic feed leans on when YouTube's own sequence is not enough.
 *
 * Two sources, merged and ranked by [FlowNeuroEngine.rank]: the Shorts tabs of a rotating handful
 * of subscribed channels, and Shorts searches on the topics the engine has learnt. A search result
 * knows its id, title, views and poster only; the reel's channel arrives when it is opened.
 */
@Singleton
class ShortsDiscoveryEngine
    @Inject
    constructor() {
        private val requestSemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)

        /** A cached page; each refresh takes the next few reels, so no channel repeats the same ones. */
        private class CachedShorts(
            val shorts: List<Video>,
            val timestamp: Long,
        ) {
            private var cursor = 0

            fun isFresh(ttlMs: Long) = System.currentTimeMillis() - timestamp < ttlMs

            @Synchronized
            fun nextSlice(count: Int): List<Video> {
                val slice = shorts.sliceFrom(cursor, count)
                cursor = (cursor + count).mod(shorts.size.coerceAtLeast(1))
                return slice
            }
        }

        private val channelShortsCache =
            object : LinkedHashMap<String, CachedShorts>(CHANNEL_CACHE_MAX + 10, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedShorts>?): Boolean = size > CHANNEL_CACHE_MAX
            }

        private val discoveryCache = HashMap<String, CachedShorts>()

        private val recentlyFetchedChannels = mutableSetOf<String>()
        private var lastChannelRotationTime = 0L

        suspend fun getDiscoveryShorts(userSubs: Set<String>): List<Video> =
            withContext(PerformanceDispatcher.networkIO) {
                val recentlySeen = runCatching { FlowNeuroEngine.getRecentlySeenShorts() }.getOrDefault(emptySet())
                val seenIds = recentlySeen.toMutableSet()
                val candidates = mutableListOf<Video>()

                fun addUnique(videos: List<Video>) {
                    videos.forEach { video ->
                        if (video.id.isNotBlank() && seenIds.add(video.id)) candidates += video
                    }
                }

                try {
                    val subscribed = fetchSubscriptionShorts(userSubs)
                    addUnique(subscribed)
                    Log.i(TAG, "Phase 1: ${subscribed.size} Shorts from subscribed channels")
                } catch (e: Exception) {
                    Log.e(TAG, "Phase 1 (subscription Shorts) failed", e)
                }

                try {
                    val searched = fetchDiscoveryShorts()
                    val kept = filterLowQuality(searched)
                    addUnique(kept)
                    Log.i(TAG, "Phase 2: ${searched.size} raw, ${kept.size} after quality filter, ${candidates.size} total")
                } catch (e: Exception) {
                    Log.e(TAG, "Phase 2 (discovery Shorts) failed", e)
                }

                if (candidates.isEmpty()) {
                    Log.w(TAG, "No candidates from any source")
                    return@withContext emptyList()
                }

                val ranked =
                    diversifySubscriptions(
                        items = FlowNeuroEngine.rank(candidates, userSubs),
                        isSubscribed = { it.channelId in userSubs },
                    )
                Log.i(TAG, "Discovery complete: ${ranked.size} ranked from ${candidates.size} candidates (${recentlySeen.size} seen)")
                ranked
            }

        private suspend fun fetchSubscriptionShorts(userSubs: Set<String>): List<Video> =
            coroutineScope {
                if (userSubs.isEmpty()) return@coroutineScope emptyList()

                val now = System.currentTimeMillis()
                if (now - lastChannelRotationTime > CHANNEL_CACHE_TTL_MS) {
                    recentlyFetchedChannels.clear()
                    lastChannelRotationTime = now
                }

                userSubs
                    .sortedBy { if (it in recentlyFetchedChannels) 1 else 0 }
                    .take(MAX_SUB_CHANNELS)
                    .map { channelId ->
                        async {
                            try {
                                val cached = synchronized(channelShortsCache) { channelShortsCache[channelId] }
                                val tab =
                                    cached?.takeIf { it.isFresh(CHANNEL_CACHE_TTL_MS) }
                                        ?: requestSemaphore
                                            .withPermit {
                                                withTimeoutOrNull(REQUEST_TIMEOUT_MS) { fetchShortsForChannel(channelId) }.orEmpty()
                                            }.takeIf { it.isNotEmpty() }
                                            ?.let { CachedShorts(it, now) }
                                            ?.also { synchronized(channelShortsCache) { channelShortsCache[channelId] = it } }
                                recentlyFetchedChannels += channelId
                                tab?.nextSlice(REELS_PER_CHANNEL_PER_REFRESH).orEmpty()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to fetch Shorts for channel $channelId: ${e.message}")
                                emptyList()
                            }
                        }
                    }.awaitAll()
                    .flatten()
            }

        /** The channel's own Shorts tab, newest first: the reels themselves, not uploads sieved by length. */
        private suspend fun fetchShortsForChannel(channelId: String): List<Video> =
            ChannelShortsFeed
                .initial(channelId)
                ?.videos
                ?.take(CHANNEL_TAB_REELS_CACHED)
                .orEmpty()

        private suspend fun fetchDiscoveryShorts(): List<Video> =
            coroutineScope {
                val now = System.currentTimeMillis()
                val queries = buildDiscoveryQueries()

                synchronized(discoveryCache) {
                    discoveryCache.entries.removeAll { (_, cached) -> now - cached.timestamp > DISCOVERY_CACHE_TTL_MS * 4 }
                }

                queries
                    .map { query ->
                        async {
                            try {
                                val cached = synchronized(discoveryCache) { discoveryCache[query] }
                                if (cached != null && cached.isFresh(DISCOVERY_CACHE_TTL_MS)) return@async cached.shorts

                                val results =
                                    requestSemaphore.withPermit {
                                        withTimeoutOrNull(REQUEST_TIMEOUT_MS) { searchShorts(query) } ?: emptyList()
                                    }
                                if (results.isNotEmpty()) {
                                    synchronized(discoveryCache) { discoveryCache[query] = CachedShorts(results, now) }
                                }
                                results
                            } catch (e: Exception) {
                                Log.w(TAG, "Discovery search failed for '$query': ${e.message}")
                                emptyList()
                            }
                        }
                    }.awaitAll()
                    .flatten()
            }

        /** Drops the obvious spam before it reaches the ranker: placeholder titles, bait, emoji-only bots. */
        private fun filterLowQuality(shorts: List<Video>): List<Video> =
            shorts.filter { video ->
                val title = video.title
                if (title.isBlank() || title == "Short" || title == "Shorts" || title.length < 3) return@filter false
                val lower = title.lowercase()
                if (SPAM_PATTERNS.any { lower.contains(it) }) return@filter false

                val emojiCount = title.count { Character.getType(it) == Character.OTHER_SYMBOL.toInt() }
                val letterCount = title.count { it.isLetter() }
                !(emojiCount > letterCount && letterCount < 5)
            }

        private suspend fun buildDiscoveryQueries(): List<String> {
            val brain = FlowNeuroEngine.getBrainSnapshot()
            return discoveryQueriesFrom(
                topics =
                    brain.globalVector.topics.entries
                        .sortedByDescending { it.value }
                        .take(TOP_TOPICS)
                        .map { it.key },
                topicPairs =
                    brain.topicAffinities.entries
                        .sortedByDescending { it.value }
                        .take(TOP_TOPIC_PAIRS)
                        .mapNotNull { (key, _) -> key.split("|").takeIf { it.size == 2 }?.joinToString(" ") },
                generated = runCatching { FlowNeuroEngine.generateDiscoveryQueries() }.getOrDefault(emptyList()).take(GENERATED_QUERIES),
                blocked = brain.blockedTopics,
                limit = MAX_DISCOVERY_QUERIES,
            )
        }

        private suspend fun searchShorts(query: String): List<Video> =
            YouTube
                .searchShorts(query)
                .getOrDefault(emptyList())
                .take(SHORTS_PER_SEARCH)
                .map { it.toVideo() }

        private fun ReelLockup.toVideo(): Video =
            Video(
                id = id,
                title = title,
                channelName = "",
                channelId = "",
                thumbnailUrl = ThumbnailUrlResolver.normalizeVideoThumbnail(id, thumbnailUrl.ifBlank { posterUrl }),
                duration = 0,
                viewCount = viewCount,
                uploadDate = "",
                timestamp = System.currentTimeMillis(),
                description = "",
                isShort = true,
            )

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

        private companion object {
            const val TAG = "ShortsDiscovery"

            /** How much of a channel's Shorts tab is kept; [REELS_PER_CHANNEL_PER_REFRESH] of it per refresh. */
            const val CHANNEL_TAB_REELS_CACHED = 30
            const val REELS_PER_CHANNEL_PER_REFRESH = 3

            /** Subscribed channels visited per refresh; the rotation reaches the rest over time. */
            const val MAX_SUB_CHANNELS = 8

            /** Searches per refresh: exactly one round of the request semaphore. */
            const val MAX_DISCOVERY_QUERIES = 3
            const val TOP_TOPICS = 8
            const val TOP_TOPIC_PAIRS = 2
            const val GENERATED_QUERIES = 2
            const val SHORTS_PER_SEARCH = 15
            const val CHANNEL_CACHE_TTL_MS = 30 * 60 * 1000L
            const val DISCOVERY_CACHE_TTL_MS = 15 * 60 * 1000L
            const val CHANNEL_CACHE_MAX = 50
            const val MAX_CONCURRENT_REQUESTS = 3
            const val REQUEST_TIMEOUT_MS = 4_000L

            val SPAM_PATTERNS =
                listOf(
                    "subscribe for more",
                    "follow for more",
                    "like and subscribe",
                    "free v-bucks",
                    "free robux",
                    "link in bio",
                    "dm for",
                    "check bio",
                )
        }
    }
