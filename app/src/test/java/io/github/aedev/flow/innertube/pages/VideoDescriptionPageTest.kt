package io.github.aedev.flow.innertube.pages

import io.github.aedev.flow.data.model.RichTextTarget
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDescriptionPageTest {
    @Test
    fun `reads the description, its spans and the exact figures`() {
        val page = Json.parseToJsonElement(WATCH_RESPONSE).toVideoDescriptionPage(ownVideoId = "fJ9rUzIMcZQ")

        val description = requireNotNull(page.description)
        assertEquals("Chapters 1:57 and #queen on queenonlinestore.com", description.text)
        assertEquals(2_096_008_503L, page.viewCount)
        assertEquals("2,096,008,503 views", page.viewCountText)
        assertEquals("Aug 1, 2008", page.publishedDateText)
        assertEquals("18 years ago", page.relativeDateText)

        val timestamp = description.spans.first { it.target is RichTextTarget.Timestamp }
        assertEquals(117L, (timestamp.target as RichTextTarget.Timestamp).seconds)
        assertEquals("1:57", description.text.substring(timestamp.start, timestamp.end))

        val hashtag = description.spans.first { it.target is RichTextTarget.Hashtag }
        assertEquals("queen", (hashtag.target as RichTextTarget.Hashtag).tag)

        val url = description.spans.first { it.target is RichTextTarget.Url }
        assertEquals("http://www.queenonlinestore.com/", (url.target as RichTextTarget.Url).url)
    }

    @Test
    fun `reads the header factoids in the order the panel lists them`() {
        val page = Json.parseToJsonElement(WATCH_RESPONSE).toVideoDescriptionPage(ownVideoId = "fJ9rUzIMcZQ")

        assertEquals(listOf("Likes", "Views", "Aug 1, 2008"), page.factoids.map { it.label })
        assertEquals(listOf("14M", "2,096,008,503", "18 years ago"), page.factoids.map { it.value })
        assertEquals("14 million likes", page.factoids.first().accessibilityText)
    }

    @Test
    fun `falls back to the structured panel body when the secondary info carries none`() {
        val withoutSecondary =
            Json.parseToJsonElement(
                WATCH_RESPONSE.replace("\"videoSecondaryInfoRenderer\"", "\"unusedSecondaryInfoRenderer\""),
            )

        val page = withoutSecondary.toVideoDescriptionPage(ownVideoId = "fJ9rUzIMcZQ")

        assertEquals("Panel body", requireNotNull(page.description).text)
    }

    @Test
    fun `an empty response reports itself empty`() {
        val page = Json.parseToJsonElement("""{"contents":{}}""").toVideoDescriptionPage(ownVideoId = "vid")

        assertTrue(page.isEmpty)
        assertNull(page.description)
        assertNull(page.viewCount)
    }

    private companion object {
        val WATCH_RESPONSE =
            """
            {
              "contents": {"twoColumnWatchNextResults": {"results": {"results": {"contents": [
                {"videoPrimaryInfoRenderer": {
                  "viewCount": {"videoViewCountRenderer": {
                    "viewCount": {"simpleText": "2,096,008,503 views"},
                    "originalViewCount": "2096008503"
                  }},
                  "dateText": {"simpleText": "Aug 1, 2008"},
                  "relativeDateText": {"simpleText": "18 years ago"}
                }},
                {"videoSecondaryInfoRenderer": {
                  "attributedDescription": {
                    "content": "Chapters 1:57 and #queen on queenonlinestore.com",
                    "commandRuns": [
                      {"startIndex": 9, "length": 4, "onTap": {"innertubeCommand": {
                        "watchEndpoint": {"videoId": "fJ9rUzIMcZQ", "startTimeSeconds": 117}
                      }}},
                      {"startIndex": 18, "length": 6, "onTap": {"innertubeCommand": {
                        "browseEndpoint": {"browseId": "FEhashtag", "canonicalBaseUrl": "/hashtag/queen"}
                      }}},
                      {"startIndex": 28, "length": 19, "onTap": {"innertubeCommand": {
                        "urlEndpoint": {"url": "https://www.youtube.com/redirect?event=video_description&q=http%3A%2F%2Fwww.queenonlinestore.com%2F"}
                      }}}
                    ]
                  }
                }}
              ]}}}},
              "engagementPanels": [
                {"engagementPanelSectionListRenderer": {
                  "panelIdentifier": "engagement-panel-structured-description",
                  "content": {"structuredDescriptionContentRenderer": {"items": [
                    {"videoDescriptionHeaderRenderer": {
                      "factoid": [
                        {"factoidRenderer": {
                          "value": {"simpleText": "14M"},
                          "label": {"simpleText": "Likes"},
                          "accessibilityText": "14 million likes"
                        }},
                        {"viewCountFactoidRenderer": {"factoid": {"factoidRenderer": {
                          "value": {"simpleText": "2,096,008,503"},
                          "label": {"simpleText": "Views"}
                        }}}},
                        {"factoidRenderer": {
                          "value": {"simpleText": "18 years ago"},
                          "label": {"simpleText": "Aug 1, 2008"}
                        }}
                      ]
                    }},
                    {"expandableVideoDescriptionBodyRenderer": {
                      "attributedDescriptionBodyText": {"content": "Panel body"}
                    }}
                  ]}}
                }}
              ]
            }
            """.trimIndent()
    }
}
