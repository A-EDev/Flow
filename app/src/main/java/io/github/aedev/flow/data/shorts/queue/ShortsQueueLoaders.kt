package io.github.aedev.flow.data.shorts.queue

import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.toShortVideo
import io.github.aedev.flow.data.shorts.ShortsRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedRepository
import io.github.aedev.flow.data.subscriptions.SubscriptionWatchedVideos
import kotlinx.coroutines.flow.first

/**
 * The algorithmic reel feed, optionally seeded from one short.
 *
 * Reports `exhausted` strictly from the continuation token. The pre-queue feed instead inferred
 * "there is more" from having at least five items, which on the discovery path (always a null
 * continuation) sent it into a cache-clearing refresh the moment the user neared the end.
 */
class AlgorithmicFeedLoader(
    private val repository: ShortsRepository,
    private val seedVideoId: String? = null,
) : ShortsQueueLoader {
    override suspend fun initial(): ShortsQueuePage {
        val result = repository.getShortsFeed(seedVideoId = seedVideoId)
        return ShortsQueuePage(
            items = result.shorts,
            cursor = result.continuation,
            exhausted = result.continuation == null,
        )
    }

    override suspend fun more(cursor: String?): ShortsQueuePage {
        if (cursor == null) return exhaustedPage()
        val result = repository.loadMore(cursor)
        return ShortsQueuePage(
            items = result.shorts,
            cursor = result.continuation,
            exhausted = result.continuation == null,
        )
    }
}

/**
 * The user's saved Shorts, in saved order.
 *
 * Reads the flow's current value **once** with [first]. The pre-queue version held an open `collect`,
 * so bookmarking or un-bookmarking anything rebuilt the list and reset the pager position mid-watch.
 */
class SavedShortsLoader(
    private val playlistRepository: PlaylistRepository,
) : ShortsQueueLoader {
    override suspend fun initial(): ShortsQueuePage {
        val saved = playlistRepository.getSavedShortsFlow().first()
        return ShortsQueuePage(
            items = saved.map { it.toShortVideo() },
            cursor = null,
            exhausted = true,
        )
    }

    override suspend fun more(cursor: String?): ShortsQueuePage = exhaustedPage()
}

class SubscriptionShortsLoader(
    private val subscriptionFeedRepository: SubscriptionFeedRepository,
    private val playerPreferences: PlayerPreferences,
    private val watchedVideos: SubscriptionWatchedVideos,
    private val anchorVideoId: String?,
) : ShortsQueueLoader {
    override suspend fun initial(): ShortsQueuePage {
        val excludedChannelIds = playerPreferences.subscriptionShortsExcludedChannels.first()
        val watchedIds = watchedVideos.ids.first()
        val items =
            subscriptionFeedRepository
                .observeFeed()
                .first()
                .asSequence()
                .filter { it.isShort && it.id.isNotBlank() }
                .filter { it.channelId !in excludedChannelIds }
                .filter { it.id == anchorVideoId || it.id !in watchedIds }
                .sortedByDescending { it.timestamp }
                .map { it.toShortVideo() }
                .toList()
        return ShortsQueuePage(items, cursor = null, exhausted = true)
    }

    override suspend fun more(cursor: String?): ShortsQueuePage = exhaustedPage()
}

/**
 * A list a shelf already had in memory, handed over via [ShortsQueueHandoff].
 *
 * Finite by nature — the shelf holds what it holds. Continuing past it is the controller's job.
 */
class SnapshotLoader(
    private val items: List<ShortVideo>,
) : ShortsQueueLoader {
    override suspend fun initial(): ShortsQueuePage = ShortsQueuePage(items, cursor = null, exhausted = true)

    override suspend fun more(cursor: String?): ShortsQueuePage = exhaustedPage()
}

/**
 * A loader the user can switch off — today, the algorithmic feed that trails a finished queue.
 *
 * [enabled] is read when the hand-over would actually happen rather than when the queue is built, so
 * flipping the setting mid-watch decides what the *next* swipe past the end does. Switched off it
 * reports itself exhausted, which is the queue's existing way of saying "this is the end".
 */
class GatedShortsLoader(
    private val enabled: suspend () -> Boolean,
    private val delegate: ShortsQueueLoader,
) : ShortsQueueLoader {
    override suspend fun initial(): ShortsQueuePage = if (enabled()) delegate.initial() else exhaustedPage()

    override suspend fun more(cursor: String?): ShortsQueuePage = if (enabled()) delegate.more(cursor) else exhaustedPage()
}

private fun exhaustedPage() = ShortsQueuePage(emptyList(), cursor = null, exhausted = true)
