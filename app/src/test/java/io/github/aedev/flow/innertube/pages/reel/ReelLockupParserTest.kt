package io.github.aedev.flow.innertube.pages.reel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelLockupParserTest {
    private fun parse(raw: String) = Json.parseToJsonElement(raw).jsonObject.toReelLockups()

    @Test
    fun `reads the id and the reel tokens off the reel watch endpoint`() {
        val reels =
            parse(
                """
                { "contents": { "items": [ { "shortsLockupViewModel": {
                  "onTap": { "innertubeCommand": {
                    "commandMetadata": { "webCommandMetadata": { "url": "/shorts/wrongidxxxx" } },
                    "reelWatchEndpoint": {
                      "videoId": "abcdefghijk", "playerParams": "PP", "params": "P", "sequenceParams": "SEQ",
                      "thumbnail": { "thumbnails": [ { "url": "https://i.ytimg.test/frame0.jpg", "width": 1080, "height": 1920 } ] }
                    }
                  } },
                  "overlayMetadata": { "primaryText": { "content": "A short title" }, "secondaryText": { "content": "1.2K views" } },
                  "thumbnailViewModel": { "thumbnailViewModel": { "image": { "sources": [
                    { "url": "https://i.ytimg.test/small.jpg", "width": 202, "height": 360 },
                    { "url": "https://i.ytimg.test/large.jpg", "width": 405, "height": 720 }
                  ] } } }
                } } ] } }
                """.trimIndent(),
            )

        val reel = reels.single()
        assertEquals("abcdefghijk", reel.id)
        assertEquals("A short title", reel.title)
        assertEquals(1_200L, reel.viewCount)
        assertEquals("1.2K views", reel.viewsText)
        assertEquals("https://i.ytimg.test/large.jpg", reel.thumbnailUrl)
        assertEquals("https://i.ytimg.test/frame0.jpg", reel.posterUrl)
        assertEquals("PP", reel.playerParams)
        assertEquals("P", reel.params)
        assertEquals("SEQ", reel.sequenceParams)
    }

    @Test
    fun `falls back to the shorts url when the endpoint has no id`() {
        val reels =
            parse(
                """
                { "shortsLockupViewModel": {
                  "onTap": { "innertubeCommand": { "commandMetadata": { "webCommandMetadata": { "url": "/shorts/abcdefghijk?feature=share" } } } },
                  "overlayMetadata": { "primaryText": { "content": "A short title" }, "secondaryText": { "content": "1.2K views" } }
                } }
                """.trimIndent(),
            )

        assertEquals(listOf(ReelLockup("abcdefghijk", "A short title", 1_200L, viewsText = "1.2K views")), reels)
    }

    @Test
    fun `reads legacy reel items and drops duplicates`() {
        val reels =
            parse(
                """
                {
                  "first": { "reelItemRenderer": { "videoId": "12345678901", "headline": { "simpleText": "Legacy short" }, "viewCountText": { "simpleText": "2M views" } } },
                  "duplicate": { "reelItemRenderer": { "videoId": "12345678901", "headline": { "simpleText": "Legacy short" } } }
                }
                """.trimIndent(),
            )

        assertEquals(1, reels.size)
        assertEquals("12345678901", reels.single().id)
        assertEquals(2_000_000L, reels.single().viewCount)
    }

    @Test
    fun `a lockup without any id is skipped`() {
        assertTrue(parse("""{ "shortsLockupViewModel": { "overlayMetadata": { "primaryText": { "content": "x" } } } }""").isEmpty())
    }

    @Test
    fun `channel shorts tab lockups carry a channel-scoped sequence`() {
        val reels = ReelFixture(ReelFixture.CHANNEL_SHORTS).toReelLockups()

        assertTrue(reels.size >= 3)
        reels.forEach { reel ->
            assertEquals(11, reel.id.length)
            assertTrue(reel.title.isNotBlank())
            assertTrue(reel.viewCount > 0)
            assertTrue(reel.thumbnailUrl.startsWith("https://"))
            assertNotNull(reel.posterUrl)
            assertNotNull(reel.playerParams)
            assertNotNull(reel.sequenceParams)
        }
    }

    @Test
    fun `search with the shorts filter returns lockups inside grid shelves`() {
        val reels = ReelFixture(ReelFixture.SEARCH_SHORTS_FILTER).toReelLockups()

        assertTrue(reels.size >= 3)
        assertNull(reels.firstOrNull { it.id.isBlank() })
        assertEquals(reels.size, reels.distinctBy { it.id }.size)
    }
}
