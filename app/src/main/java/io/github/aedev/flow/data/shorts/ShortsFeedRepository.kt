package io.github.aedev.flow.data.shorts

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.ShortsSequenceResult
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.toShortVideo
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.reel.reelPosterUrl
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The algorithmic reel feed: YouTube's own sequence first, the discovery engine's candidates
 * interleaved behind it, watched and recently shown reels held back.
 *
 * Entries arrive as ids and posters; [ShortsStreamResolver] and [ShortsMetadataRepository] fill in
 * the rest as each reel comes on screen.
 */
@Singleton
class ShortsFeedRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val subscriptionRepository: SubscriptionRepository,
        private val viewHistory: ViewHistory,
        private val playerPreferences: PlayerPreferences,
        private val discovery: ShortsDiscoveryEngine,
    ) {
        private val shortsCache =
            object : LinkedHashMap<String, ShortVideo>(SHORTS_CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ShortVideo>?): Boolean = size > SHORTS_CACHE_SIZE
            }
        private val recentlyShownIds = LinkedHashSet<String>()

        @Volatile
        private var cachedInitialFeed: ShortsSequenceResult? = null
        private var cachedFeedTimestamp = 0L

        private val _discoveryFeedUpdate = MutableSharedFlow<List<ShortVideo>>(replay = 1, extraBufferCapacity = 3)

        /** Discovery candidates found after a feed was already delivered; the queue interleaves them. */
        val discoveryFeedUpdate: SharedFlow<List<ShortVideo>> = _discoveryFeedUpdate.asSharedFlow()

        suspend fun getShortsFeed(seedVideoId: String? = null): ShortsSequenceResult =
            withContext(PerformanceDispatcher.networkIO) {
                if (seedVideoId == null) {
                    val cached = cachedInitialFeed
                    if (cached != null && System.currentTimeMillis() - cachedFeedTimestamp < CACHE_TTL_MS && cached.shorts.isNotEmpty()) {
                        val filtered = cached.copy(shorts = filterWatchedShorts(cached.shorts))
                        if (filtered.shorts.isNotEmpty()) return@withContext filtered
                    }
                    return@withContext fetchFeed()
                }

                val sequence =
                    fetchSequence(seedVideoId)
                        ?.takeIf { it.shorts.isNotEmpty() }
                        ?.let { it.copy(shorts = filterWatchedShorts(it.shorts, keepId = seedVideoId)) }
                        ?: fetchFeed()
                val seeded = sequence.copy(shorts = sequence.shorts.openingOn(seedVideoId))
                remember(seeded.shorts)
                seeded
            }

        /**
         * The next page of the sequence. A page whose reels are all watched or seen comes back empty
         * but keeps its continuation, and a failed fetch keeps the token it was given: the sequence
         * itself never ends, so neither must the feed. Nothing here is recorded as seen — that
         * happens when a reel is actually on screen.
         */
        suspend fun loadMore(continuation: String?): ShortsSequenceResult =
            withContext(PerformanceDispatcher.networkIO) {
                if (continuation == null) return@withContext ShortsSequenceResult(emptyList(), null)

                val page = fetchSequence(continuation = continuation) ?: return@withContext ShortsSequenceResult(emptyList(), continuation)
                val fresh = filterWatchedShorts(page.shorts)
                val reRanked = orderNewestFirst(reRankWithFlowNeuro(fresh, subscriptionRepository.getAllSubscriptionIds()))
                remember(reRanked)
                page.copy(shorts = reRanked)
            }

        /**
         * Runs the discovery engine once the user is already watching, and hands what it finds to
         * [discoveryFeedUpdate]. Up to eight channel fetches and three searches, so the caller
         * decides when the network is free for it.
         */
        suspend fun discoverMore() =
            withContext(PerformanceDispatcher.networkIO) {
                val userSubs = subscriptionRepository.getAllSubscriptionIds()
                val ranked = runDiscovery(userSubs) ?: return@withContext
                val known = cachedInitialFeed?.shorts?.mapTo(HashSet()) { it.id }.orEmpty()
                val candidates =
                    ranked
                        .filter { it.id !in recentlyShownIds && it.id !in known }
                        .let(::deduplicateByTitle)
                        .map { it.toShortVideo() }
                        .let { filterWatchedShorts(it) }
                        .let(::orderNewestFirst)
                        .let { spreadChannels(it, ShortVideo::channelId, maxPerChannel = 1) }
                if (candidates.isEmpty()) return@withContext
                remember(candidates)
                _discoveryFeedUpdate.tryEmit(candidates)
                cachedInitialFeed?.let { current ->
                    cachedInitialFeed = current.copy(shorts = current.shorts + candidates)
                }
            }

        suspend fun recordShown(videoId: String) {
            if (videoId.isBlank()) return
            runCatching {
                FlowNeuroEngine.initialize(context)
                FlowNeuroEngine.recordSeenShorts(listOf(videoId))
            }.onFailure { Log.w(TAG, "Failed to record shown Short $videoId", it) }
        }

        fun clearCaches() {
            synchronized(shortsCache) { shortsCache.clear() }
            synchronized(recentlyShownIds) { recentlyShownIds.clear() }
            cachedInitialFeed = null
            cachedFeedTimestamp = 0L
            discovery.clearCaches()
        }

        fun evictChannel(channelId: String) {
            discovery.evictChannel(channelId)
            cachedInitialFeed?.let { current ->
                cachedInitialFeed = current.copy(shorts = current.shorts.filter { it.channelId != channelId })
            }
        }

        suspend fun forceRefresh(): ShortsSequenceResult {
            clearCaches()
            return getShortsFeed()
        }

        /**
         * YouTube's sequence when it answers, else the discovery engine as the critical path. The
         * sequence's continuation survives either way: a first page of watched reels is not the end
         * of the feed.
         */
        private suspend fun fetchFeed(): ShortsSequenceResult {
            val userSubs = subscriptionRepository.getAllSubscriptionIds()
            val sequence = fetchOpeningSequence()
            if (sequence != null && sequence.shorts.isNotEmpty()) {
                val shorts =
                    diversifySubscriptions(
                        items = filterWatchedShorts(sequence.shorts),
                        isSubscribed = { it.channelId in userSubs },
                    )
                if (shorts.isNotEmpty()) {
                    remember(shorts)
                    return ShortsSequenceResult(shorts, sequence.continuation).also(::cacheInitialFeed)
                }
                Log.i(TAG, "Sequence contained only watched Shorts")
            }

            val ranked = runDiscovery(userSubs).orEmpty()
            if (ranked.isEmpty()) {
                Log.w(TAG, "Every Shorts source came back empty")
                return ShortsSequenceResult(emptyList(), sequence?.continuation)
            }
            val candidates =
                ranked
                    .filter { it.id !in recentlyShownIds }
                    .let(::deduplicateByTitle)
                    .map { it.toShortVideo() }
                    .let { filterWatchedShorts(it) }
                    .let(::orderNewestFirst)
                    .let { spreadChannels(it, ShortVideo::channelId, maxPerChannel = 1) }
            remember(candidates)
            return ShortsSequenceResult(candidates, sequence?.continuation).also(::cacheInitialFeed)
        }

        /** The seedless first page is a single reel on every client, so the opening is pages one and two. */
        private suspend fun fetchOpeningSequence(): ShortsSequenceResult? {
            val first = fetchSequence() ?: return null
            val token = first.continuation
            if (first.shorts.size > 1 || token == null) return first
            val second = fetchSequence(continuation = token) ?: return first
            return ShortsSequenceResult(first.shorts + second.shorts, second.continuation)
        }

        private suspend fun fetchSequence(
            seedVideoId: String? = null,
            continuation: String? = null,
        ): ShortsSequenceResult? {
            val page =
                try {
                    withTimeoutOrNull(SEQUENCE_TIMEOUT_MS) {
                        when {
                            continuation != null -> YouTube.shorts(sequenceParams = continuation)
                            seedVideoId != null -> YouTube.shortsFromVideo(seedVideoId)
                            else -> YouTube.shorts()
                        }.getOrNull()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Reel sequence failed: ${e.message}")
                    null
                } ?: return null
            val shorts =
                page.entries
                    .map { it.toShortVideo() }
                    .filter { continuation == null || it.id !in recentlyShownIds }
            return ShortsSequenceResult(shorts, page.continuation)
        }

        private suspend fun runDiscovery(userSubs: Set<String>): List<Video>? =
            try {
                discovery.getDiscoveryShorts(userSubs = userSubs)
            } catch (e: Exception) {
                Log.e(TAG, "ShortsDiscoveryEngine failed", e)
                null
            }

        private fun cacheInitialFeed(result: ShortsSequenceResult) {
            cachedInitialFeed = result
            cachedFeedTimestamp = System.currentTimeMillis()
        }

        /** Anchors the sequence on [videoId]; the reel itself is known from the cache or by id alone. */
        private fun List<ShortVideo>.openingOn(videoId: String): List<ShortVideo> =
            openingOnSeed(
                items = this,
                seed =
                    synchronized(
                        shortsCache,
                    ) { shortsCache[videoId] } ?: ShortVideo(id = videoId, thumbnailUrl = reelPosterUrl(videoId)),
                id = ShortVideo::id,
            )

        private fun remember(shorts: List<ShortVideo>) {
            synchronized(shortsCache) { shorts.forEach { shortsCache[it.id] = it } }
            synchronized(recentlyShownIds) {
                recentlyShownIds.addAll(shorts.map { it.id })
                while (recentlyShownIds.size > MAX_RECENTLY_SHOWN) {
                    recentlyShownIds.remove(recentlyShownIds.first())
                }
            }
        }

        /**
         * Drops Shorts the user has already watched or been shown recently. [keepId] survives
         * regardless: it is the Short the user just asked for by name.
         */
        private suspend fun filterWatchedShorts(
            shorts: List<ShortVideo>,
            keepId: String? = null,
        ): List<ShortVideo> {
            if (shorts.isEmpty()) return shorts
            val threshold = playerPreferences.watchedThreshold.first()
            val watchedIds =
                runCatching {
                    viewHistory.getWatchedShortIdsAboveThreshold(threshold.minPercent, threshold.maxRemainingMs)
                }.getOrDefault(emptySet())
            val recentlySeenIds =
                runCatching {
                    FlowNeuroEngine.initialize(context)
                    FlowNeuroEngine.getRecentlySeenShorts()
                }.getOrDefault(emptySet())
            if (watchedIds.isEmpty() && recentlySeenIds.isEmpty()) return shorts
            return shorts.filter { it.id == keepId || (it.id !in watchedIds && it.id !in recentlySeenIds) }
        }

        /** YouTube's order is the candidate pool; the engine reorders everything after the first reel. */
        private suspend fun reRankWithFlowNeuro(
            shorts: List<ShortVideo>,
            userSubs: Set<String>,
        ): List<ShortVideo> {
            if (shorts.size <= 2) return shorts
            return try {
                FlowNeuroEngine.initialize(context)
                val pinned = shorts.first()
                val candidates = shorts.drop(1)
                val ranked = FlowNeuroEngine.rank(candidates = candidates.map { it.toVideo() }, userSubs = userSubs)
                val byId = candidates.associateBy { it.id }
                listOf(pinned) + ranked.mapNotNull { byId[it.id] }
            } catch (e: Exception) {
                Log.w(TAG, "FlowNeuro re-ranking failed, using original order: ${e.message}")
                orderNewestFirst(shorts)
            }
        }

        private fun orderNewestFirst(shorts: List<ShortVideo>): List<ShortVideo> = shorts.sortedByDescending { it.timestamp }

        /** Re-uploads share a title with different ids; the more-viewed copy is kept as the original. */
        private fun deduplicateByTitle(videos: List<Video>): List<Video> {
            if (videos.size <= 1) return videos
            val tokens =
                videos.map { video ->
                    video to
                        video.title
                            .lowercase()
                            .split(Regex("\\s+"))
                            .map { it.trim { c -> !c.isLetterOrDigit() } }
                            .filter { it.length > 2 }
                            .toSet()
                }
            val consumed = mutableSetOf<Int>()
            val result = mutableListOf<Video>()
            for (i in tokens.indices) {
                if (i in consumed) continue
                var best = tokens[i].first
                val bestTokens = tokens[i].second
                for (j in i + 1 until tokens.size) {
                    if (j in consumed) continue
                    val otherTokens = tokens[j].second
                    if (bestTokens.isEmpty() || otherTokens.isEmpty()) continue
                    val similarity = bestTokens.intersect(otherTokens).size.toDouble() / bestTokens.union(otherTokens).size
                    if (similarity > TITLE_SIMILARITY_THRESHOLD) {
                        val other = tokens[j].first
                        if (other.viewCount > best.viewCount) best = other
                        consumed.add(j)
                    }
                }
                result.add(best)
                consumed.add(i)
            }
            return result
        }

        private companion object {
            const val TAG = "ShortsFeedRepository"
            const val SEQUENCE_TIMEOUT_MS = 8_000L
            const val CACHE_TTL_MS = 5 * 60 * 1000L
            const val SHORTS_CACHE_SIZE = 100
            const val MAX_RECENTLY_SHOWN = 100
            const val TITLE_SIMILARITY_THRESHOLD = 0.6
        }
    }
