package io.github.aedev.flow.data.shorts.queue

import io.github.aedev.flow.data.model.ShortVideo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsQueueControllerTest {
    private fun short(id: String) =
        ShortVideo(
            id = id,
            title = "t-$id",
            channelName = "c",
            channelId = "ch",
            thumbnailUrl = "https://i.ytimg.com/vi/$id/oar2.jpg",
        )

    private fun shorts(vararg ids: String) = ids.map(::short)

    /** Serves pre-baked pages in order, then reports itself exhausted. */
    private class FakeLoader(
        private val pages: List<List<ShortVideo>>,
    ) : ShortsQueueLoader {
        var initialCalls = 0
            private set
        var moreCalls = 0
            private set

        override suspend fun initial(): ShortsQueuePage {
            initialCalls++
            return page(0)
        }

        override suspend fun more(cursor: String?): ShortsQueuePage {
            moreCalls++
            val next = (cursor?.toIntOrNull() ?: 0) + 1
            return page(next)
        }

        private fun page(index: Int): ShortsQueuePage {
            val items = pages.getOrNull(index).orEmpty()
            val isLast = index >= pages.lastIndex
            return ShortsQueuePage(
                items = items,
                cursor = if (isLast) null else index.toString(),
                exhausted = isLast,
            )
        }
    }

    private fun ids(controller: ShortsQueueController) = controller.items.value.map { it.id }

    // ── Opening position ──

    @Test
    fun `opens at the tapped short without reordering the list`() = runTest {
        // The shelf order has to survive so backward swipes still work.
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c", "d"))))

        controller.loadInitial(startVideoId = "c")

        assertEquals(listOf("a", "b", "c", "d"), ids(controller))
        assertEquals(2, controller.currentIndex.value)
        assertEquals("c", controller.currentItem?.id)
    }

    @Test
    fun `opens at the top when no start short is named`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))

        controller.loadInitial(startVideoId = null)

        assertEquals(0, controller.currentIndex.value)
    }

    @Test
    fun `unknown start short falls back to the top`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))

        controller.loadInitial(startVideoId = "missing")

        assertEquals(0, controller.currentIndex.value)
    }

    @Test
    fun `duplicates in the first page are dropped`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "a"))))

        controller.loadInitial(null)

        assertEquals(listOf("a", "b"), ids(controller))
    }

    // ── Paging and the hand-over to the feed ──

    @Test
    fun `finite source with no continuation stops at the end`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))
        controller.loadInitial(null)

        assertFalse(controller.hasMore)
        controller.loadMore()

        assertEquals(listOf("a", "b"), ids(controller))
    }

    @Test
    fun `exhausted shelf hands over to the feed`() = runTest {
        val shelf = FakeLoader(listOf(shorts("s1", "s2")))
        val feed = FakeLoader(listOf(shorts("f1", "f2"), shorts("f3")))
        val controller = ShortsQueueController(primary = shelf, continuation = feed)

        controller.loadInitial(null)
        assertEquals(listOf("s1", "s2"), ids(controller))
        assertTrue("shelf is finite but a feed follows it", controller.hasMore)

        controller.loadMore()
        assertEquals(listOf("s1", "s2", "f1", "f2"), ids(controller))
        assertEquals("continuation must start with initial(), not more()", 1, feed.initialCalls)

        controller.loadMore()
        assertEquals(listOf("s1", "s2", "f1", "f2", "f3"), ids(controller))
        assertFalse(controller.hasMore)
    }

    @Test
    fun `paginated primary is drained before the continuation is touched`() = runTest {
        val primary = FakeLoader(listOf(shorts("p1"), shorts("p2")))
        val feed = FakeLoader(listOf(shorts("f1")))
        val controller = ShortsQueueController(primary, feed)

        controller.loadInitial(null)
        controller.loadMore()

        assertEquals(listOf("p1", "p2"), ids(controller))
        assertEquals(0, feed.initialCalls)

        controller.loadMore()
        assertEquals(listOf("p1", "p2", "f1"), ids(controller))
    }

    @Test
    fun `append never re-adds an id already in the queue`() = runTest {
        val primary = FakeLoader(listOf(shorts("a"), shorts("a", "b")))
        val controller = ShortsQueueController(primary)

        controller.loadInitial(null)
        controller.loadMore()

        assertEquals(listOf("a", "b"), ids(controller))
    }

    @Test
    fun `an all-duplicate page does not end paging`() = runTest {
        val primary = FakeLoader(listOf(shorts("a"), shorts("a"), shorts("b")))
        val controller = ShortsQueueController(primary)

        controller.loadInitial(null)
        controller.loadMore()

        assertEquals("should have skipped the duplicate page", listOf("a", "b"), ids(controller))
    }

    // ── Removal ──

    @Test
    fun `removing the current short reports that the position now holds another`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c"))))
        controller.loadInitial(null)
        controller.setCurrentIndex(1)

        val change = controller.remove("b")

        assertEquals(ShortsQueueChange.CurrentItemChanged, change)
        assertEquals(listOf("a", "c"), ids(controller))
        assertEquals(1, controller.currentIndex.value)
        assertEquals("c", controller.currentItem?.id)
    }

    @Test
    fun `removing another short leaves the current one alone`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c"))))
        controller.loadInitial(null)
        controller.setCurrentIndex(1)

        val change = controller.remove("c")

        assertEquals(ShortsQueueChange.ListOnly, change)
        assertEquals("b", controller.currentItem?.id)
    }

    @Test
    fun `removing the last short clamps the position`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))
        controller.loadInitial(null)
        controller.setCurrentIndex(1)

        controller.remove("b")

        assertEquals(0, controller.currentIndex.value)
        assertEquals("a", controller.currentItem?.id)
    }

    @Test
    fun `a removed short does not come back on the next append`() = runTest {
        val primary = FakeLoader(listOf(shorts("a", "b"), shorts("b", "c")))
        val controller = ShortsQueueController(primary)
        controller.loadInitial(null)

        controller.remove("b")
        controller.loadMore()

        assertEquals(listOf("a", "c"), ids(controller))
    }

    @Test
    fun `removing an unknown id changes nothing`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a"))))
        controller.loadInitial(null)

        assertEquals(ShortsQueueChange.None, controller.remove("nope"))
    }

    // ── Enrichment and discovery ──

    @Test
    fun `enrichment replaces in place`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))
        controller.loadInitial(null)

        val change = controller.applyEnrichment(listOf(short("a").copy(title = "enriched")))

        assertEquals(ShortsQueueChange.ListOnly, change)
        assertEquals(listOf("a", "b"), ids(controller))
        assertEquals("enriched", controller.items.value.first().title)
    }

    @Test
    fun `enrichment that changes nothing is reported as no change`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a"))))
        controller.loadInitial(null)

        assertEquals(ShortsQueueChange.None, controller.applyEnrichment(listOf(short("a"))))
    }

    @Test
    fun `discovery merges after the current position and never before it`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b", "c"))))
        controller.loadInitial(null)
        controller.setCurrentIndex(1)

        controller.mergeDiscovery(shorts("d"))

        val result = ids(controller)
        assertEquals("watched items must not move", listOf("a", "b"), result.take(2))
        assertTrue("discovery item should be present", "d" in result)
    }

    @Test
    fun `discovery ignores ids already queued`() = runTest {
        val controller = ShortsQueueController(FakeLoader(listOf(shorts("a", "b"))))
        controller.loadInitial(null)

        assertEquals(ShortsQueueChange.None, controller.mergeDiscovery(shorts("a", "b")))
    }
}
