package io.github.aedev.flow.innertube.pages.reel

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReelSequencePageTest {
    private fun parse(raw: String) = Json.parseToJsonElement(raw).jsonObject.toReelSequencePage()

    @Test
    fun `reads entries and a bare continuation`() {
        val page =
            parse(
                """
                { "entries": [
                    { "command": { "reelWatchEndpoint": {
                        "videoId": "aaaaaaaaaaa", "playerParams": "PP", "params": "P", "softRefreshContinuation": "SOFT",
                        "thumbnail": { "thumbnails": [
                          { "url": "https://i.ytimg.test/small.jpg", "width": 270, "height": 480 },
                          { "url": "https://i.ytimg.test/frame0.jpg", "width": 1080, "height": 1920 } ] }
                    } } },
                    { "command": { "reelWatchEndpoint": { "playerParams": "no id" } } },
                    { "command": { "reelWatchEndpoint": { "videoId": "bbbbbbbbbbb" } } }
                  ],
                  "continuation": "NEXT" }
                """.trimIndent(),
            )

        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), page.entries.map { it.videoId })
        assertEquals("NEXT", page.continuation)
        val first = page.entries.first()
        assertEquals("PP", first.playerParams)
        assertEquals("P", first.params)
        assertEquals("SOFT", first.softRefreshContinuation)
        assertEquals("https://i.ytimg.test/frame0.jpg", first.posterUrl)
        assertNull(page.entries.last().posterUrl)
    }

    @Test
    fun `ad entries are dropped on every client shape`() {
        val page =
            parse(
                """
                { "entries": [
                    { "command": { "reelWatchEndpoint": { "videoId": "aaaaaaaaaaa", "videoType": "REEL_VIDEO_TYPE_VIDEO" } } },
                    { "command": { "reelWatchEndpoint": { "videoId": "ad1ad1ad1ad", "videoType": "REEL_VIDEO_TYPE_AD",
                        "adClientParams": { "isAd": true } } } },
                    { "command": { "reelWatchEndpoint": { "videoId": "ad2ad2ad2ad", "adClientParams": { "isAd": true } } } },
                    { "command": { "reelWatchEndpoint": { "videoId": "bbbbbbbbbbb", "adClientParams": { "isAd": false } } } }
                  ],
                  "continuation": "NEXT" }
                """.trimIndent(),
            )

        assertEquals(listOf("aaaaaaaaaaa", "bbbbbbbbbbb"), page.entries.map { it.videoId })
    }

    @Test
    fun `reads the web continuation endpoint`() {
        val page =
            parse(
                """
                { "entries": [ { "command": { "reelWatchEndpoint": { "videoId": "aaaaaaaaaaa" } } } ],
                  "continuationEndpoint": { "continuationCommand": { "token": "WEB_NEXT", "request": "CONTINUATION_REQUEST_TYPE_REEL_WATCH_SEQUENCE" } } }
                """.trimIndent(),
            )

        assertEquals("WEB_NEXT", page.continuation)
    }

    @Test
    fun `an empty response is an empty exhausted page`() {
        val page = parse("""{ "responseContext": {} }""")

        assertTrue(page.entries.isEmpty())
        assertNull(page.continuation)
    }

    @Test
    fun `ios seeded page lists reels with full-size posters and never the seed`() {
        val page = ReelFixture(ReelFixture.SEQUENCE_SEEDED_IOS).toReelSequencePage()

        assertTrue(page.entries.size >= 3)
        assertNotNull(page.continuation)
        assertTrue(page.entries.none { it.videoId == "pJWqvVnDT0A" })
        page.entries.forEach { entry ->
            assertEquals(11, entry.videoId.length)
            assertNotNull(entry.playerParams)
            assertTrue(entry.posterUrl.orEmpty().contains("frame0"))
        }
    }

    @Test
    fun `web seeded page carries its continuation inside an endpoint`() {
        val page = ReelFixture(ReelFixture.SEQUENCE_SEEDED_WEB).toReelSequencePage()

        assertTrue(page.entries.size >= 3)
        assertNotNull(page.continuation)
    }

    @Test
    fun `the seedless first page holds one entry and the continuation of the rest`() {
        val page = ReelFixture(ReelFixture.SEQUENCE_SEEDLESS_ANDROID).toReelSequencePage()

        assertEquals(1, page.entries.size)
        assertNotNull(page.continuation)
    }

    @Test
    fun `an android continuation page parses without its prefetch payloads`() {
        val page = ReelFixture(ReelFixture.SEQUENCE_CONTINUATION_ANDROID).toReelSequencePage()

        assertTrue(page.entries.size >= 3)
        assertNotNull(page.continuation)
    }
}
