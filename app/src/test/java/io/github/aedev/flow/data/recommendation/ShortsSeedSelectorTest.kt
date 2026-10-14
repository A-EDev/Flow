package io.github.aedev.flow.data.recommendation

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortsSeedSelectorTest {
    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private fun seed(
        id: String,
        channel: String,
        source: ShortsSeedSource,
        ageDays: Int = 0,
        percent: Double = 100.0,
    ) = ShortsSeedInput(id, channel, source, now - ageDays * day, percent)

    @Test
    fun `stronger and fresher signals win, one seed per channel first`() {
        val picked =
            ShortsSeedSelector.select(
                listOf(
                    seed("old-like", "UCa", ShortsSeedSource.LIKED, ageDays = 40),
                    seed("want", "UCa", ShortsSeedSource.WANT_MORE),
                    seed("saved", "UCb", ShortsSeedSource.SAVED),
                    seed("dwell", "UCc", ShortsSeedSource.FEED),
                ),
                maxSeeds = 3,
                now = now,
            )

        assertEquals(listOf("want", "saved", "dwell"), picked)
    }

    @Test
    fun `a half-watched reel is no seed and an excluded channel is skipped`() {
        val picked =
            ShortsSeedSelector.select(
                listOf(
                    seed("skimmed", "UCa", ShortsSeedSource.WATCHED, percent = 30.0),
                    seed("blocked", "UCbad", ShortsSeedSource.LIKED),
                    seed("finished", "UCb", ShortsSeedSource.WATCHED, percent = 95.0),
                ),
                maxSeeds = 3,
                now = now,
                excludedChannelIds = setOf("UCbad"),
            )

        assertEquals(listOf("finished"), picked)
    }

    @Test
    fun `cooled seeds rotate out unless that would starve the pick`() {
        val candidates = listOf(seed("a", "UCa", ShortsSeedSource.LIKED), seed("b", "UCb", ShortsSeedSource.LIKED))

        assertEquals(listOf("b"), ShortsSeedSelector.select(candidates, maxSeeds = 1, now = now, cooldownIds = setOf("a")))
        assertEquals(listOf("a", "b"), ShortsSeedSelector.select(candidates, maxSeeds = 2, now = now, cooldownIds = setOf("a")))
    }
}
