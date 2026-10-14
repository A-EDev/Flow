package io.github.aedev.flow.data.shorts.feed

import io.github.aedev.flow.data.model.ShortVideo
import org.junit.Assert.assertEquals
import org.junit.Test

internal fun reel(
    id: String,
    channel: String = "",
): ShortVideo = ShortVideo(id = id, thumbnailUrl = "", channelId = channel)

internal fun lane(
    lane: ShortsFeedLane,
    vararg reels: ShortVideo,
): Pair<ShortsFeedLane, List<ShortsLaneItem>> = lane to reels.map { ShortsLaneItem(it, lane) }

class ShortsFeedMixerTest {
    @Test
    fun `lanes are blended round robin under their quotas`() {
        val lanes =
            mapOf(
                lane(ShortsFeedLane.RELATED, reel("r1", "a"), reel("r2", "b"), reel("r3", "c")),
                lane(ShortsFeedLane.EXPLORE, reel("e1"), reel("e2"), reel("e3")),
                lane(ShortsFeedLane.DISCOVERY, reel("d1", "d"), reel("d2", "e")),
                lane(ShortsFeedLane.SUBSCRIPTIONS, reel("s1", "f"), reel("s2", "g")),
            )
        val quotas =
            mapOf(
                ShortsFeedLane.RELATED to 2,
                ShortsFeedLane.EXPLORE to 2,
                ShortsFeedLane.DISCOVERY to 1,
                ShortsFeedLane.SUBSCRIPTIONS to 1,
            )

        val mix = blendShortsLanes(lanes, quotas, targetSize = 6)

        assertEquals(listOf("r1", "e1", "d1", "s1", "r2", "e2"), mix.items.map { it.short.id })
        assertEquals(2, mix.laneCounts[ShortsFeedLane.RELATED])
    }

    @Test
    fun `a lane that runs dry gives its slots to the others in scarcity order`() {
        val lanes =
            mapOf(
                lane(ShortsFeedLane.RELATED, reel("r1", "a")),
                lane(ShortsFeedLane.EXPLORE, reel("e1"), reel("e2"), reel("e3"), reel("e4")),
            )
        val quotas =
            mapOf(
                ShortsFeedLane.RELATED to 3,
                ShortsFeedLane.EXPLORE to 1,
                ShortsFeedLane.DISCOVERY to 1,
                ShortsFeedLane.SUBSCRIPTIONS to 1,
            )

        val mix = blendShortsLanes(lanes, quotas, targetSize = 4)

        assertEquals(listOf("r1", "e1", "e2", "e3"), mix.items.map { it.short.id })
    }

    @Test
    fun `a known channel takes one slot per page and unknown channels are exempt`() {
        val lanes =
            mapOf(
                lane(ShortsFeedLane.SUBSCRIPTIONS, reel("s1", "a"), reel("s2", "a"), reel("s3", "b")),
                lane(ShortsFeedLane.EXPLORE, reel("e1"), reel("e2")),
            )
        val quotas =
            mapOf(
                ShortsFeedLane.SUBSCRIPTIONS to 3,
                ShortsFeedLane.EXPLORE to 2,
                ShortsFeedLane.RELATED to 0,
                ShortsFeedLane.DISCOVERY to 0,
            )

        val mix = blendShortsLanes(lanes, quotas, targetSize = 5)

        assertEquals(listOf("e1", "s1", "e2", "s3"), mix.items.map { it.short.id })
    }

    @Test
    fun `same-channel reels are kept two slots apart, across the page boundary too`() {
        val items =
            listOf(reel("1", "a"), reel("2", "a"), reel("3", "b"), reel("4", "c"), reel("5", "a")).map {
                ShortsLaneItem(it, ShortsFeedLane.EXPLORE)
            }

        val spaced = spaceShortsByChannel(items, gap = 2, seedRecent = listOf("b"))

        assertEquals(listOf("1", "4", "3", "2", "5"), spaced.map { it.short.id })
    }
}
