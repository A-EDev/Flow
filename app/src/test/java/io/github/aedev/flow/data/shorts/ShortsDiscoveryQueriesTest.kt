package io.github.aedev.flow.data.shorts

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortsDiscoveryQueriesTest {
    @Test
    fun `queries are the learnt interests, deduplicated, blocked topics removed, capped`() {
        val queries =
            discoveryQueriesFrom(
                topics = listOf("cooking", "chess", " cooking ", ""),
                topicPairs = listOf("cooking baking"),
                generated = listOf("chess openings", "gambling tips"),
                blocked = listOf("Gambling"),
                limit = 4,
                order = { it },
            )

        assertEquals(listOf("cooking", "chess", "cooking baking", "chess openings"), queries)
    }

    @Test
    fun `nothing learnt means nothing searched`() {
        assertEquals(emptyList<String>(), discoveryQueriesFrom(emptyList(), emptyList(), emptyList(), emptyList(), limit = 3))
    }
}
