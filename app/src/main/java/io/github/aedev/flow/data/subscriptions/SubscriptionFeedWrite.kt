package io.github.aedev.flow.data.subscriptions

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.subscriptions.SubscriptionFeedMerger.preservingEnrichedMetadata

/**
 * What one refresh writes back to the feed cache.
 *
 * [rows] replaces the whole table on a full refresh and the [SubscriptionRefreshPlan.channelIds]
 * slice otherwise. [fetchedChannelIds] are the channels that actually answered, the only ones that
 * may be stamped fresh.
 */
internal data class SubscriptionFeedWrite(
    val rows: List<Video>,
    val fetchedChannelIds: List<String>,
)

/**
 * A channel that failed this pass keeps its earlier rows, even on a full refresh, and is not
 * stamped fresh, so the next pass retries it instead of showing it empty for the whole TTL (#1094).
 * A channel unsubscribed while the refresh ran is dropped, fetched rows and all.
 */
internal fun subscriptionFeedWrite(
    plan: SubscriptionRefreshPlan,
    freshVideos: List<Video>,
    cachedSlice: List<Video>,
    failedChannelIds: Set<String>,
    subscribedChannelIds: Set<String>,
    now: Long,
    windowMs: Long,
    maxItems: Int,
): SubscriptionFeedWrite {
    val priorById =
        cachedSlice
            .filter { it.id.isNotBlank() }
            .groupBy { it.id }
            .mapValues { (_, candidates) -> SubscriptionFeedMerger.mergeDuplicates(candidates, now) }
    val carried =
        if (plan.isFullRefresh) {
            cachedSlice.filter { it.channelId in failedChannelIds }
        } else {
            cachedSlice
        }
    val rows =
        SubscriptionFeedMerger
            .mergeSubscriptionFeed(
                freshVideos = freshVideos.map { fresh -> fresh.preservingEnrichedMetadata(priorById[fresh.id]) },
                cachedVideos = carried,
                now = now,
                windowMs = windowMs,
                maxItems = maxItems,
            ).filter { it.channelId.isBlank() || it.channelId in subscribedChannelIds }
            .withHighQualityThumbnails()
    return SubscriptionFeedWrite(
        rows = rows,
        fetchedChannelIds = plan.channelIds.filter { it !in failedChannelIds },
    )
}
