package io.github.aedev.flow.data.shorts.feed

import io.github.aedev.flow.data.model.ShortVideo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsFeedPagerTest {
    @Test
    fun `opening on a reel puts that reel first and opens its chain`() =
        runBlocking {
            val bench = ShortsFeedBenchmark()
            val pager = ShortsFeedPager(bench.sources, bench.engine, bench.context)

            val page = pager.open("tapped")

            assertEquals("tapped", page.first().id)
            assertTrue(page.any { it.id.startsWith("r-tapped-") })
        }

    @Test
    fun `a page after the opening is served from the pools with a bounded number of requests`() =
        runBlocking {
            val bench = ShortsFeedBenchmark()
            val pager = ShortsFeedPager(bench.sources, bench.engine, bench.context)
            pager.open(null)
            val opening = bench.requests.get()

            val page = pager.nextPage()

            assertEquals(16, page.size)
            assertTrue(bench.requests.get() - opening <= 6)
        }

    @Test
    fun `want more opens a chain from the reel and returns unseen reels only`() =
        runBlocking {
            val bench = ShortsFeedBenchmark()
            val pager = ShortsFeedPager(bench.sources, bench.engine, bench.context)
            val first = pager.open(null)

            val chain = pager.chainFrom(ShortVideo(id = "liked", thumbnailUrl = ""))

            assertTrue(chain.isNotEmpty())
            assertTrue(chain.all { it.id.startsWith("r-liked-") })
            assertTrue(chain.none { it.id in first.map { f -> f.id } })
        }

    @Test
    fun `a channel evicted mid-session leaves every pool`() =
        runBlocking {
            val bench = ShortsFeedBenchmark()
            val pager = ShortsFeedPager(bench.sources, bench.engine, bench.context)
            pager.open(null)
            val doomed = bench.channels.first()

            pager.evictChannel(doomed)

            assertFalse(pager.reserveTail(200).any { it.short.channelId == doomed })
        }

    @Test
    fun `the reserve restores unserved reels and skips ones already used`() =
        runBlocking {
            val bench = ShortsFeedBenchmark()
            val pager = ShortsFeedPager(bench.sources, bench.engine, bench.context)
            val first = pager.open(null)
            val tail = pager.reserveTail(40)
            assertTrue(tail.isNotEmpty())

            val next = ShortsFeedPager(bench.sources, bench.engine, bench.context)
            next.restoreReserve(tail + first.map { ShortsLaneItem(it, ShortsFeedLane.EXPLORE) })

            val restored = next.reserveTail(200).map { it.short.id }
            assertTrue(restored.containsAll(tail.map { it.short.id }))
        }
}
