package io.github.aedev.flow.data.shorts.feed

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.toShortVideo
import io.github.aedev.flow.data.shorts.ChannelShortsFeed
import io.github.aedev.flow.data.shorts.queue.subscriptionReelChannelOrder
import io.github.aedev.flow.data.shorts.sliceFrom
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.pages.reel.ReelSequencePage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

data class ShortsSourceBatch(
    val items: List<ShortVideo>,
    val continuation: String?,
)

/** The four network legs of the reel feed, behind one interface so the pager can be run offline. */
interface ShortsFeedSources {
    /** A null [continuation] opens the seedless chain. Null result = the request failed. */
    suspend fun explore(continuation: String?): ShortsSourceBatch?

    /** A null [continuation] opens the chain seeded from [seedId]. Null result = the request failed. */
    suspend fun related(
        seedId: String,
        continuation: String?,
    ): ShortsSourceBatch?

    suspend fun discovery(query: String): List<ShortVideo>

    /** Reels the subscription feed already holds; no request. */
    suspend fun subscriptionReels(): List<ShortVideo>

    /** The next few reels of [channelId]'s Shorts tab; empty once the tab is spent. */
    suspend fun channelReels(channelId: String): List<ShortVideo>

    /** Subscribed channels in the order their tabs should be walked, exclusions removed. */
    suspend fun subscribedChannels(): List<String>
}

@Singleton
class InnerTubeShortsFeedSources
    @Inject
    constructor(
        private val subscriptionFeedRepository: SubscriptionFeedRepository,
        private val subscriptionRepository: SubscriptionRepository,
        private val playerPreferences: PlayerPreferences,
    ) : ShortsFeedSources {
        private val gate = Semaphore(MAX_CONCURRENT_REQUESTS)

        /** One channel's Shorts tab; every visit takes the next few reels through the cursor. */
        private class CachedTab(
            val reels: List<ShortVideo>,
            val fetchedAtMillis: Long,
        ) {
            private var cursor = 0
            private var served = 0

            val isSpent: Boolean
                get() = served >= reels.size

            @Synchronized
            fun next(count: Int): List<ShortVideo> {
                if (isSpent) return emptyList()
                val slice = reels.sliceFrom(cursor, minOf(count, reels.size - served))
                cursor += slice.size
                served += slice.size
                return slice
            }
        }

        private val tabs =
            object : LinkedHashMap<String, CachedTab>(TAB_CACHE_MAX, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedTab>?): Boolean = size > TAB_CACHE_MAX
            }

        override suspend fun explore(continuation: String?): ShortsSourceBatch? {
            val first = sequence { YouTube.shorts(continuation) } ?: return null
            // The seedless first page is a single reel on every client; the opening is pages one and two.
            if (continuation != null || first.items.size > 1 || first.continuation == null) return first
            val second = sequence { YouTube.shorts(first.continuation) } ?: return first
            return ShortsSourceBatch(first.items + second.items, second.continuation)
        }

        override suspend fun related(
            seedId: String,
            continuation: String?,
        ): ShortsSourceBatch? = sequence { if (continuation == null) YouTube.shortsFromVideo(seedId) else YouTube.shorts(continuation) }

        private suspend fun sequence(call: suspend () -> Result<ReelSequencePage>): ShortsSourceBatch? =
            gate
                .withPermit { withTimeoutOrNull(SEQUENCE_TIMEOUT_MS) { call().getOrNull() } }
                ?.let { page -> ShortsSourceBatch(page.entries.map { it.toShortVideo() }, page.continuation) }

        override suspend fun discovery(query: String): List<ShortVideo> =
            gate
                .withPermit { withTimeoutOrNull(SEARCH_TIMEOUT_MS) { YouTube.searchShorts(query).getOrNull() } }
                .orEmpty()
                .map { it.toShortVideo() }

        override suspend fun subscriptionReels(): List<ShortVideo> {
            val excluded = playerPreferences.subscriptionShortsExcludedChannels.first()
            return subscriptionFeedRepository
                .observeFeed()
                .first()
                .filter { it.isShort && it.id.isNotBlank() && it.channelId !in excluded }
                .sortedByDescending { it.timestamp }
                .map { it.toShortVideo() }
        }

        override suspend fun channelReels(channelId: String): List<ShortVideo> {
            val now = System.currentTimeMillis()
            val cached = synchronized(tabs) { tabs[channelId] }?.takeIf { now - it.fetchedAtMillis < TAB_TTL_MS }
            val tab =
                cached
                    ?: gate
                        .withPermit { withTimeoutOrNull(SEARCH_TIMEOUT_MS) { ChannelShortsFeed.initial(channelId) } }
                        ?.videos
                        ?.take(TAB_REELS_CACHED)
                        ?.map { it.toShortVideo() }
                        ?.let { CachedTab(it, now) }
                        ?.also { synchronized(tabs) { tabs[channelId] = it } }
                    ?: return emptyList()
            return tab.next(REELS_PER_CHANNEL_VISIT)
        }

        override suspend fun subscribedChannels(): List<String> {
            val excluded = playerPreferences.subscriptionShortsExcludedChannels.first()
            return subscriptionReelChannelOrder(
                feed = subscriptionFeedRepository.observeFeed().first(),
                subscribedChannelIds = subscriptionRepository.getAllSubscriptions().first().map { it.channelId },
            ).filterNot { it in excluded }
        }

        private companion object {
            const val MAX_CONCURRENT_REQUESTS = 3
            const val SEQUENCE_TIMEOUT_MS = 8_000L
            const val SEARCH_TIMEOUT_MS = 4_000L
            const val TAB_CACHE_MAX = 60
            const val TAB_TTL_MS = 30L * 60L * 1000L
            const val TAB_REELS_CACHED = 30
            const val REELS_PER_CHANNEL_VISIT = 3
        }
    }
