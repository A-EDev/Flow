package io.github.aedev.flow.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader

class SubscriptionRssParserTest {
    /** Mirrors the namespace-aware parser [SubscriptionRssParser.parse] builds on device. */
    private fun parse(xml: String): List<RssVideo> {
        val parser =
            KXmlParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(StringReader(xml))
            }
        return SubscriptionRssParser.readEntries(parser)
    }

    /** A channel feed shaped like YouTube's, entries newest first. */
    private fun feed(vararg ids: String): String =
        buildString {
            // No leading whitespace: an XML declaration must start at offset 0.
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
            append(
                "<feed xmlns:yt=\"http://www.youtube.com/xml/schemas/2015\" " +
                    "xmlns:media=\"http://search.yahoo.com/mrss/\" " +
                    "xmlns=\"http://www.w3.org/2005/Atom\">",
            )
            append("<title>Channel Name</title>")
            ids.forEach { id ->
                append("<entry>")
                append("<yt:videoId>$id</yt:videoId>")
                append("<title>Title $id</title>")
                append("<media:group>")
                append("<media:title>Title $id</media:title>")
                append("<media:thumbnail url=\"https://i.ytimg.com/vi/$id/hq.jpg\"/>")
                append("</media:group>")
                append("</entry>")
            }
            append("</feed>")
        }

    @Test
    fun `parses every entry newest first`() {
        val videos = parse(feed("aaa", "bbb", "ccc"))

        assertEquals(listOf("aaa", "bbb", "ccc"), videos.map { it.id })
        assertEquals("Title aaa", videos.first().title)
        assertEquals("https://i.ytimg.com/vi/aaa/hq.jpg", videos.first().thumbnailUrl)
    }

    @Test
    fun `entry title wins over the repeated media title`() {
        val videos = parse(feed("aaa"))

        assertEquals("Title aaa", videos.single().title)
    }

    @Test
    fun `channel title is not mistaken for an entry`() {
        val videos = parse(feed("aaa"))

        assertEquals(1, videos.size)
        assertTrue(videos.none { it.title == "Channel Name" })
    }

    @Test
    fun `entry without a video id is skipped`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
              <entry><title>No id here</title></entry>
              <entry><yt:videoId>bbb</yt:videoId><title>Real one</title></entry>
            </feed>
            """.trimIndent()

        assertEquals(listOf("bbb"), parse(xml).map { it.id })
    }

    @Test
    fun `malformed xml yields no entries instead of throwing`() {
        assertEquals(emptyList<RssVideo>(), parse("<feed><entry>"))
    }

    @Test
    fun `missing thumbnail stays null`() {
        val xml =
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015" xmlns="http://www.w3.org/2005/Atom">
              <entry><yt:videoId>aaa</yt:videoId><title>Title aaa</title></entry>
            </feed>
            """.trimIndent()

        assertNull(parse(xml).single().thumbnailUrl)
    }

    @Test
    fun `first ever check announces nothing`() {
        val entries = parse(feed("aaa", "bbb"))

        assertEquals(emptyList<RssVideo>(), SubscriptionRssParser.newEntriesSince(entries, lastVideoId = null))
    }

    @Test
    fun `unchanged feed announces nothing`() {
        val entries = parse(feed("aaa", "bbb"))

        assertEquals(emptyList<RssVideo>(), SubscriptionRssParser.newEntriesSince(entries, lastVideoId = "aaa"))
    }

    @Test
    fun `every video published since the last check is announced`() {
        val entries = parse(feed("new1", "new2", "new3", "known"))

        val fresh = SubscriptionRssParser.newEntriesSince(entries, lastVideoId = "known")

        assertEquals(listOf("new1", "new2", "new3"), fresh.map { it.id })
    }

    @Test
    fun `a burst of new videos is capped`() {
        val ids = (1..12).map { "v$it" }.toTypedArray()
        val entries = parse(feed(*ids, "known"))

        val fresh = SubscriptionRssParser.newEntriesSince(entries, lastVideoId = "known")

        assertEquals(SubscriptionRssParser.MAX_NEW_PER_CHANNEL, fresh.size)
        assertEquals("v1", fresh.first().id)
    }

    @Test
    fun `pointer that aged out of the feed announces only the newest`() {
        val entries = parse(feed("aaa", "bbb", "ccc"))

        val fresh = SubscriptionRssParser.newEntriesSince(entries, lastVideoId = "long-gone")

        assertEquals(listOf("aaa"), fresh.map { it.id })
    }

    @Test
    fun `empty feed announces nothing`() {
        assertEquals(emptyList<RssVideo>(), SubscriptionRssParser.newEntriesSince(emptyList(), lastVideoId = "aaa"))
    }
}
