package io.github.aedev.flow.innertube.pages.channel

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shapes taken from live `browse` responses for Linus Tech Tips and Hyperplexed. The header is the
 * one part of the channel payload that exists in three generations at once, so every test here is
 * about a field surviving when its preferred source is missing.
 */
class ChannelHeaderParserTest {
    private fun parse(
        raw: String,
        requestedId: String = "UCrequested",
    ) = Json.parseToJsonElement(raw).toChannelHeader(requestedId)

    private val modernHeader =
        """
        {
          "header": { "pageHeaderRenderer": { "content": { "pageHeaderViewModel": {
            "title": { "dynamicTextViewModel": {
              "text": { "content": "Linus Tech Tips" },
              "rendererContext": { "accessibilityContext": { "label": "Linus Tech Tips, Verified" } }
            } },
            "banner": { "imageBannerViewModel": { "image": { "sources": [
              { "url": "https://yt3.test/banner-small.jpg", "width": 1060, "height": 175 },
              { "url": "https://yt3.test/banner-large.jpg", "width": 2560, "height": 424 }
            ] } } },
            "image": { "decoratedAvatarViewModel": { "avatar": { "avatarViewModel": { "image": { "sources": [
              { "url": "//yt3.test/avatar.jpg", "width": 160, "height": 160 }
            ] } } } } },
            "metadata": { "contentMetadataViewModel": { "metadataRows": [
              { "metadataParts": [ { "text": { "content": "@LinusTechTips" } } ] },
              { "metadataParts": [
                { "text": { "content": "16.9M subscribers" } },
                { "text": { "content": "7.9K videos" } }
              ] }
            ] } },
            "description": { "descriptionPreviewViewModel": {
              "description": { "content": "Professionally curious." }
            } }
          } } } },
          "metadata": { "channelMetadataRenderer": {
            "externalId": "UCXuqSBlHAE6Xw-yeJA0Tunw",
            "title": "Linus Tech Tips",
            "vanityChannelUrl": "http://www.youtube.com/@LinusTechTips"
          } }
        }
        """.trimIndent()

    @Test
    fun `reads every field of a modern page header`() {
        val header = parse(modernHeader)

        assertEquals("UCXuqSBlHAE6Xw-yeJA0Tunw", header.id)
        assertEquals("Linus Tech Tips", header.title)
        assertEquals("@LinusTechTips", header.handle)
        assertEquals("16.9M subscribers", header.subscriberCountText)
        assertEquals("7.9K videos", header.videoCountText)
        assertEquals("Professionally curious.", header.description)
        assertTrue(header.isVerified)
    }

    @Test
    fun `takes the widest banner and avatar source`() {
        val header = parse(modernHeader)

        assertEquals("https://yt3.test/banner-large.jpg", header.bannerUrl)
        assertEquals("https://yt3.test/avatar.jpg", header.avatarUrl)
    }

    @Test
    fun `parses a display count back to a number without losing the display text`() {
        val header = parse(modernHeader)

        assertEquals("16.9M subscribers", header.subscriberCountText)
        assertEquals(16_900_000L, header.subscriberCount)
    }

    @Test
    fun `falls back to the legacy header when no page header is present`() {
        val header =
            parse(
                """
                {
                  "header": { "c4TabbedHeaderRenderer": {
                    "channelId": "UClegacy",
                    "title": "Legacy Channel",
                    "subscriberCountText": { "simpleText": "653K subscribers" },
                    "videosCountText": { "runs": [ { "text": "91 videos" } ] },
                    "avatar": { "thumbnails": [ { "url": "https://yt3.test/legacy.jpg", "width": 176, "height": 176 } ] },
                    "banner": { "thumbnails": [ { "url": "https://yt3.test/legacy-banner.jpg", "width": 1060, "height": 175 } ] },
                    "badges": [ { "metadataBadgeRenderer": { "style": "BADGE_STYLE_TYPE_VERIFIED" } } ]
                  } }
                }
                """.trimIndent(),
            )

        assertEquals("UClegacy", header.id)
        assertEquals("Legacy Channel", header.title)
        assertEquals("653K subscribers", header.subscriberCountText)
        assertEquals("91 videos", header.videoCountText)
        assertEquals("https://yt3.test/legacy.jpg", header.avatarUrl)
        assertEquals("https://yt3.test/legacy-banner.jpg", header.bannerUrl)
        assertTrue(header.isVerified)
    }

    @Test
    fun `leaves absent fields null so the screen can hide their rows`() {
        val header =
            parse(
                """
                {
                  "metadata": { "channelMetadataRenderer": { "externalId": "UCbare", "title": "Bare" } }
                }
                """.trimIndent(),
            )

        assertEquals("UCbare", header.id)
        assertEquals("Bare", header.title)
        assertNull(header.bannerUrl)
        assertNull(header.handle)
        assertNull(header.subscriberCountText)
        assertNull(header.subscriberCount)
        assertNull(header.videoCountText)
        assertNull(header.joinedDateText)
        assertTrue(header.links.isEmpty())
    }

    @Test
    fun `keeps the requested id when the response carries none`() {
        assertEquals("UCrequested", parse("""{"contents":{}}""").id)
    }
}
