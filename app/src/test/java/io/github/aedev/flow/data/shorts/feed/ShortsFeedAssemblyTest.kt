package io.github.aedev.flow.data.shorts.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsFeedAssemblyTest {
    private val quotas =
        mapOf(
            ShortsFeedLane.RELATED to 2,
            ShortsFeedLane.EXPLORE to 2,
            ShortsFeedLane.DISCOVERY to 1,
            ShortsFeedLane.SUBSCRIPTIONS to 1,
        )

    @Test
    fun `watched, suppressed and excluded reels are dropped and consumed`() {
        val pools =
            mapOf(
                lane(ShortsFeedLane.EXPLORE, reel("watched"), reel("suppressed"), reel("blocked", "UCbad"), reel("ok")),
            )
        val filters =
            ShortsFeedFilters(watchedIds = setOf("watched"), suppressedIds = setOf("suppressed"), excludedChannelIds = setOf("UCbad"))

        val assembly =
            assembleShortsPage(pools, quotas, targetSize = 4, filters = filters, usedIds = emptySet(), recentChannels = emptyList())

        assertEquals(listOf("ok"), assembly.page.map { it.short.id })
        assertEquals(setOf("watched", "suppressed", "blocked", "ok"), assembly.consumed)
    }

    @Test
    fun `a titled reel that matches a blocked topic is dropped and an untitled one is left for resolve time`() {
        val pools =
            mapOf(
                lane(
                    ShortsFeedLane.DISCOVERY,
                    reel("cats").copy(title = "Cats being cats"),
                    reel("dogs").copy(title = "Dogs at the park"),
                    reel("untitled"),
                ),
            )
        val filters = ShortsFeedFilters(isBlockedText = { title, _ -> "cats" in title.lowercase() })

        val assembly =
            assembleShortsPage(pools, quotas, targetSize = 3, filters = filters, usedIds = emptySet(), recentChannels = emptyList())

        assertEquals(listOf("dogs", "untitled"), assembly.page.map { it.short.id })
        assertTrue("cats" in assembly.consumed)
    }

    @Test
    fun `the seen gate is skipped while the pools are thin`() {
        val pools = mapOf(lane(ShortsFeedLane.EXPLORE, reel("seen1"), reel("seen2"), reel("fresh")))
        val filters = ShortsFeedFilters(seenIds = setOf("seen1", "seen2"))

        val assembly =
            assembleShortsPage(pools, quotas, targetSize = 3, filters = filters, usedIds = emptySet(), recentChannels = emptyList())

        assertEquals(3, assembly.page.size)
    }

    @Test
    fun `the seen gate applies once the pools are deep and leaves enough`() {
        val reels = (1..30).map { reel("e$it") }
        val pools = mapOf(ShortsFeedLane.EXPLORE to reels.map { ShortsLaneItem(it, ShortsFeedLane.EXPLORE) })
        val filters = ShortsFeedFilters(seenIds = (1..12).map { "e$it" }.toSet())

        val assembly =
            assembleShortsPage(pools, quotas, targetSize = 6, filters = filters, usedIds = emptySet(), recentChannels = emptyList())

        assertTrue(assembly.page.none { it.short.id in filters.seenIds })
        assertTrue(assembly.consumed.containsAll(filters.seenIds))
    }

    @Test
    fun `reels already used never come back`() {
        val pools = mapOf(lane(ShortsFeedLane.EXPLORE, reel("old"), reel("new")))

        val assembly =
            assembleShortsPage(
                pools,
                quotas,
                targetSize = 2,
                filters = ShortsFeedFilters(),
                usedIds = setOf("old"),
                recentChannels = emptyList(),
            )

        assertEquals(listOf("new"), assembly.page.map { it.short.id })
        assertFalse("old" in assembly.page.map { it.short.id })
    }
}
