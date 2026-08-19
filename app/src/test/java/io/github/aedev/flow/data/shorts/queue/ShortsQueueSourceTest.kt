package io.github.aedev.flow.data.shorts.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortsQueueSourceTest {
    private fun roundTrip(source: ShortsQueueSource) = ShortsQueueSource.decode(source.encode())

    @Test
    fun `feed round trips`() {
        assertEquals(ShortsQueueSource.Feed, roundTrip(ShortsQueueSource.Feed))
    }

    @Test
    fun `saved round trips`() {
        assertEquals(ShortsQueueSource.Saved(), roundTrip(ShortsQueueSource.Saved()))
    }

    @Test
    fun `saved anchored on a short round trips`() {
        // Tapping a specific saved Short must open on it, not at the top of the collection.
        val source = ShortsQueueSource.Saved("abcdefghijk")
        assertEquals(source, roundTrip(source))
        assertEquals("abcdefghijk", source.openAtVideoId)
    }

    @Test
    fun `seeded feed round trips`() {
        val source = ShortsQueueSource.SeededFeed("abcdefghijk")
        assertEquals(source, roundTrip(source))
    }

    @Test
    fun `snapshot round trips`() {
        val source = ShortsQueueSource.Snapshot(token = "tok123", startVideoId = "abcdefghijk")
        assertEquals(source, roundTrip(source))
    }

    @Test
    fun `channel round trips despite colons in the url`() {
        // The channel URL carries its own scheme colon, so decoding must only consume the separators
        // it owns.
        val source =
            ShortsQueueSource.Channel(
                channelUrl = "https://www.youtube.com/channel/UCabcdefg",
                startVideoId = "abcdefghijk",
            )

        assertEquals(source, roundTrip(source))
    }

    // ── Degradation: a bad descriptor must never fail navigation ──

    @Test
    fun `null and blank decode to the feed`() {
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode(null))
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode(""))
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode("   "))
    }

    @Test
    fun `unknown kind decodes to the feed`() {
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode("wat:xyz"))
    }

    @Test
    fun `truncated snapshot decodes to the feed`() {
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode("snap:"))
    }

    @Test
    fun `channel without a url decodes to the feed`() {
        assertEquals(ShortsQueueSource.Feed, ShortsQueueSource.decode("channel:abcdefghijk"))
    }

    // ── Behavioural properties the controller depends on ──

    @Test
    fun `only anchored sources report a start short`() {
        assertEquals("abcdefghijk", ShortsQueueSource.SeededFeed("abcdefghijk").openAtVideoId)
        assertEquals("abcdefghijk", ShortsQueueSource.Snapshot("t", "abcdefghijk").openAtVideoId)
        assertEquals("abcdefghijk", ShortsQueueSource.Channel("u", "abcdefghijk").openAtVideoId)
        assertEquals(null, ShortsQueueSource.Feed.openAtVideoId)
        assertEquals(null, ShortsQueueSource.Saved().openAtVideoId)
    }

    @Test
    fun `blank start short reads as absent`() {
        assertEquals(null, ShortsQueueSource.Snapshot("t", "").openAtVideoId)
    }

    @Test
    fun `shelves and channel tabs continue into the feed, saved does not`() {
        assertTrue(ShortsQueueSource.Snapshot("t", "v").continuesIntoFeed)
        assertTrue(
            "the channel hands over only once it is exhausted, so a swipe never dead-ends",
            ShortsQueueSource.Channel("u", "v").continuesIntoFeed,
        )
        assertFalse("saved Shorts is a deliberate collection", ShortsQueueSource.Saved().continuesIntoFeed)
        assertFalse("the feed is already the feed", ShortsQueueSource.Feed.continuesIntoFeed)
    }

    // ── Channel sort ──

    @Test
    fun `channel carries the chosen sort through the route`() {
        val source =
            ShortsQueueSource.Channel(
                channelUrl = "https://www.youtube.com/channel/UCabcdefg",
                startVideoId = "abcdefghijk",
                sortIndex = 2,
            )

        assertEquals(source, roundTrip(source))
    }

    @Test
    fun `channel defaults to the first sort`() {
        assertEquals(0, ShortsQueueSource.Channel("https://youtube.com/channel/UCx", "vid").sortIndex)
    }

    // A handle URL has no scheme colon to confuse the split, but it also has no sort segment if an
    // older descriptor somehow survives; falling back to 0 beats failing the navigation.
    @Test
    fun `a channel descriptor with an unparseable sort opens at the default order`() {
        val decoded = ShortsQueueSource.decode("channel:vid:notanumber:https://www.youtube.com/@handle")

        assertEquals(
            ShortsQueueSource.Channel("https://www.youtube.com/@handle", "vid", 0),
            decoded,
        )
    }
}
