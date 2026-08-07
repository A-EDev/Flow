package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.data.local.ChannelSubscription

/**
 * Which channels the next subscription-feed refresh should actually hit.
 *
 * [isFullRefresh] means every subscribed channel is included, so the caller may replace the whole
 * cache instead of splicing the fetched channels back into it.
 */
data class SubscriptionRefreshPlan(
    val channelIds: List<String>,
    val isFullRefresh: Boolean,
) {
    val isEmpty: Boolean get() = channelIds.isEmpty()

    companion object {
        val NOTHING_TO_DO = SubscriptionRefreshPlan(channelIds = emptyList(), isFullRefresh = false)
    }
}

/**
 * Decides staleness per channel rather than for the feed as a whole.
 *
 * Previously any trigger re-fetched every subscription, so the periodic in-screen refresh cost a
 * full sweep of the subscription list. Tracking the last fetch per channel keeps a routine refresh
 * proportional to what has actually aged out.
 */
object SubscriptionRefreshPlanner {
    /** How long a single channel's slice of the feed is considered fresh. */
    const val CHANNEL_FEED_TTL_MS = 30 * 60 * 1000L

    fun plan(
        subscriptions: List<ChannelSubscription>,
        now: Long,
        ttlMs: Long = CHANNEL_FEED_TTL_MS,
        force: Boolean = false,
    ): SubscriptionRefreshPlan {
        if (subscriptions.isEmpty()) return SubscriptionRefreshPlan.NOTHING_TO_DO
        if (force) {
            return SubscriptionRefreshPlan(
                channelIds = subscriptions.map { it.channelId },
                isFullRefresh = true,
            )
        }

        val stale = subscriptions.filter { it.needsFeedRefresh(now, ttlMs) }
        return SubscriptionRefreshPlan(
            channelIds = stale.map { it.channelId },
            isFullRefresh = stale.size == subscriptions.size,
        )
    }

    private fun ChannelSubscription.needsFeedRefresh(
        now: Long,
        ttlMs: Long,
    ): Boolean =
        when {
            // Never fetched, including a channel subscribed to since the last refresh.
            lastFeedFetchAt <= 0L -> true

            // A clock change backwards would otherwise pin a channel as fresh indefinitely.
            lastFeedFetchAt > now -> true

            now - lastFeedFetchAt >= ttlMs -> true

            // The background notification check already saw an upload this refresh has not.
            else -> lastCheckTime > lastFeedFetchAt
        }
}
