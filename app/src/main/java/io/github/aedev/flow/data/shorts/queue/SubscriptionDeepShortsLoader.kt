package io.github.aedev.flow.data.shorts.queue

import android.util.Log
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.toShortVideo
import io.github.aedev.flow.data.shorts.ChannelShortsFeed
import io.github.aedev.flow.data.shorts.ChannelShortsFeedPage
import io.github.aedev.flow.data.shorts.ChannelShortsOwner
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionWatchedVideos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/** How many channels the deep tier will walk at most, however long the user keeps swiping. */
internal const val MAX_DEEP_SUBSCRIPTION_CHANNELS = 100

/**
 * The order the deep tier walks the user's subscriptions in.
 *
 * Channels that posted a reel recently come first, newest reel first: they are the ones whose Shorts
 * tab is worth a request, and starting anywhere else makes the hand-over from the recent reels read
 * as a jump to strangers. Every other subscribed channel follows in subscription order, because a
 * channel that stopped posting reels a year ago is invisible to a sixty-day feed and its back
 * catalogue is exactly what this tier exists to reach.
 */
internal fun subscriptionReelChannelOrder(
    feed: List<Video>,
    subscribedChannelIds: List<String>,
    limit: Int = MAX_DEEP_SUBSCRIPTION_CHANNELS,
): List<String> {
    val newestReelAt = HashMap<String, Long>()
    feed.forEach { video ->
        if (!video.isShort || video.channelId.isBlank()) return@forEach
        val seen = newestReelAt[video.channelId]
        if (seen == null || video.timestamp > seen) newestReelAt[video.channelId] = video.timestamp
    }
    val reelPosters = newestReelAt.entries.sortedByDescending { it.value }.map { it.key }
    return (reelPosters + subscribedChannelIds.filter { it.isNotBlank() })
        .distinct()
        .take(limit)
}

/**
 * Older reels, taken from the subscribed channels' own Shorts tabs.
 *
 * The subscription feed is built from channel RSS, which carries only each channel's last fifteen
 * uploads inside a sixty-day window — so [SubscriptionShortsLoader] runs dry after the recent reels,
 * and with watched reels hidden it runs dry sooner every session. This tier walks the channels
 * themselves instead, and only as far as the user actually swipes: nothing here is fetched to build
 * a queue, only to extend one.
 *
 * Channels are worked four at a time and drained round-robin, so a page reads like a subscription
 * feed rather than one channel's back catalogue. A channel's Shorts tab carries no upload dates at
 * all, so real date order is not available here — each tab's own newest-first order, interleaved, is
 * the closest honest approximation.
 */
class SubscriptionDeepShortsLoader(
    private val subscriptionFeedRepository: SubscriptionFeedRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val playerPreferences: PlayerPreferences,
    private val watchedVideos: SubscriptionWatchedVideos,
    private val fetchFirstPage: suspend (String) -> ChannelShortsFeedPage? = { ChannelShortsFeed.initial(it) },
    private val fetchNextPage: suspend (String, ChannelShortsOwner) -> ChannelShortsFeedPage? =
        { continuation, owner -> ChannelShortsFeed.more(continuation, owner) },
) : ShortsQueueLoader {
    /** One channel's Shorts tab, mid-walk. */
    private class ChannelPager(
        val channelId: String,
    ) {
        var owner = ChannelShortsOwner()
        var continuation: String? = null
        var started = false
        val buffered = ArrayDeque<ShortVideo>()

        val hasMorePages: Boolean
            get() = !started || continuation != null

        val isSpent: Boolean
            get() = buffered.isEmpty() && !hasMorePages
    }

    private val pending = ArrayDeque<String>()
    private val active = mutableListOf<ChannelPager>()

    /**
     * Reels this tier must not bother offering: already watched, or already served by the tier in
     * front of it.
     *
     * A channel's Shorts tab opens on its newest reels — the very ones the subscription feed already
     * put in the queue. Dropping them here rather than letting the queue de-duplicate them means a
     * page of thirty reels arrives as new reels instead of as a page the queue discards, which is
     * what would otherwise send it back for more pages it did not need.
     */
    private var skipIds: Set<String> = emptySet()
    private var prepared = false

    override suspend fun initial(): ShortsQueuePage {
        prepare()
        return nextPage()
    }

    override suspend fun more(cursor: String?): ShortsQueuePage {
        prepare()
        return nextPage()
    }

    /**
     * Reads everything this tier needs, once, off the main thread — the caller already runs paging on
     * a background dispatcher. Each source is sampled with [first] rather than collected: an open
     * collector here would rebuild the walk every time history or the feed cache changed mid-watch.
     */
    private suspend fun prepare() {
        if (prepared) return
        prepared = true
        val excluded = playerPreferences.subscriptionShortsExcludedChannels.first()
        val feed = subscriptionFeedRepository.observeFeed().first()
        skipIds =
            buildSet {
                addAll(watchedVideos.ids.first())
                feed.forEach { video -> if (video.isShort && video.id.isNotBlank()) add(video.id) }
            }
        subscriptionReelChannelOrder(
            feed = feed,
            subscribedChannelIds = subscriptionRepository.getAllSubscriptions().first().map { it.channelId },
        ).forEach { channelId -> if (channelId !in excluded) pending.addLast(channelId) }
        Log.d(TAG, "Deep subscription tier ready with ${pending.size} channels")
    }

    private suspend fun nextPage(): ShortsQueuePage {
        fill()
        val items = drain()
        active.removeAll { it.isSpent }
        // A channel still in the working set is holding either buffered reels or another page, so
        // an empty working set with nothing pending is the end — no extra round trip to discover it.
        val exhausted = active.isEmpty() && pending.isEmpty()
        return ShortsQueuePage(
            items = items,
            cursor = if (exhausted) null else MORE,
            exhausted = exhausted,
        )
    }

    /**
     * Brings the working set back up to strength and tops up whoever ran dry.
     *
     * Retried a bounded number of times because a channel can answer with nothing at all — no Shorts
     * tab, a failed browse, every reel already watched — and stopping at the first such channel would
     * end the queue while the rest of the subscriptions still had reels to give.
     */
    private suspend fun fill() {
        repeat(MAX_FILL_ROUNDS) {
            while (active.size < ACTIVE_CHANNELS && pending.isNotEmpty()) {
                active += ChannelPager(pending.removeFirst())
            }
            val hungry = active.filter { it.buffered.isEmpty() && it.hasMorePages }
            if (hungry.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    hungry.map { pager -> async { advance(pager) } }.awaitAll()
                }
            }
            active.removeAll { it.isSpent }
            if (active.any { it.buffered.isNotEmpty() } || pending.isEmpty()) return
        }
    }

    private suspend fun advance(pager: ChannelPager) {
        val page =
            if (!pager.started) {
                pager.started = true
                fetchFirstPage(pager.channelId)
            } else {
                pager.continuation?.let { fetchNextPage(it, pager.owner) }
            }
        if (page == null) {
            // Nothing more can come from this channel: a browse that failed is not worth a retry
            // while there are other subscriptions still to walk.
            pager.continuation = null
            return
        }
        pager.owner = page.owner
        pager.continuation = page.continuation
        page.videos.forEach { video ->
            if (video.id.isNotBlank() && video.id !in skipIds) pager.buffered.addLast(video.toShortVideo())
        }
    }

    /** Takes one reel from each channel in turn, so a page alternates between them. */
    private fun drain(): List<ShortVideo> {
        val items = mutableListOf<ShortVideo>()
        while (items.size < PAGE_SIZE) {
            var took = false
            for (pager in active) {
                if (items.size >= PAGE_SIZE) break
                val next = pager.buffered.removeFirstOrNull() ?: continue
                items += next
                took = true
            }
            if (!took) break
        }
        return items
    }

    private companion object {
        const val TAG = "DeepSubsShorts"

        /** Channels held open at once: enough to interleave, few enough to keep a page cheap. */
        const val ACTIVE_CHANNELS = 4
        const val PAGE_SIZE = 20
        const val MAX_FILL_ROUNDS = 3
        const val MORE = "more"
    }
}
