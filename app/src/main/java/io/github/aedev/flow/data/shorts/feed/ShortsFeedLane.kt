package io.github.aedev.flow.data.shorts.feed

import io.github.aedev.flow.data.model.ShortVideo

/** Where a reel in the algorithmic feed came from. The order is the blend's quota order. */
enum class ShortsFeedLane {
    /** YouTube's reel graph around a reel the user chose: a seeded `reel_watch_sequence` chain. */
    RELATED,

    /** YouTube's anonymous reel feed: the seedless chain, which never ends. */
    EXPLORE,

    /** Shorts search results for the queries the engine generates from learnt topics. */
    DISCOVERY,

    /** Subscribed channels: the RSS feed's reels first, then their Shorts tabs. */
    SUBSCRIPTIONS,
}

data class ShortsLaneItem(
    val short: ShortVideo,
    val lane: ShortsFeedLane,
    val seedId: String? = null,
)

/** Who the feed is for, which decides how the lanes share a page. */
data class ShortsFeedProfile(
    val subscribedChannelIds: Set<String>,
    val isColdStart: Boolean,
) {
    val hasSubscriptions: Boolean
        get() = subscribedChannelIds.isNotEmpty()
}

/** Everything a reel must not be to reach the page. Read once per page; all of it is by id. */
data class ShortsFeedFilters(
    val watchedIds: Set<String> = emptySet(),
    val seenIds: Set<String> = emptySet(),
    val suppressedIds: Set<String> = emptySet(),
    val excludedChannelIds: Set<String> = emptySet(),
)
