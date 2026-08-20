package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shapes taken from a live `browse` for a channel's Shorts tab. The sort bar is the reason this
 * parser exists (#547), so the tests hold it to the two things the feature depends on: the chips
 * come back in YouTube's own order with their tokens, and the grid's own continuation is not
 * mistaken for one of them.
 */
class ChannelShortsPageTest {
    private fun parse(raw: String): ChannelShortsPage = Json.parseToJsonElement(raw).let { it as JsonObject }.toChannelShortsPage()

    private val initialBrowse =
        """
        {
          "metadata": {
            "channelMetadataRenderer": {
              "title": "Some Creator",
              "externalId": "UCabcdefghijklmnopqrstu",
              "avatar": { "thumbnails": [
                { "url": "https://yt3.example/small.jpg", "width": 48 },
                { "url": "https://yt3.example/large.jpg", "width": 900 }
              ] }
            }
          },
          "contents": { "twoColumnBrowseResultsRenderer": { "tabs": [ { "tabRenderer": {
            "title": "Shorts",
            "selected": true,
            "content": { "richGridRenderer": {
              "header": { "chipBarViewModel": { "chips": [
                { "chipViewModel": {
                  "text": "Latest", "selected": true,
                  "tapCommand": { "innertubeCommand": { "continuationCommand": { "token": "TOK_LATEST" } } }
                } },
                { "chipViewModel": {
                  "text": "Popular", "selected": false,
                  "tapCommand": { "innertubeCommand": { "continuationCommand": { "token": "TOK_POPULAR" } } }
                } },
                { "chipViewModel": {
                  "text": "Oldest", "selected": false,
                  "tapCommand": { "innertubeCommand": { "continuationCommand": { "token": "TOK_OLDEST" } } }
                } }
              ] } },
              "contents": [
                { "richItemRenderer": { "content": { "shortsLockupViewModel": {
                  "onTap": { "innertubeCommand": { "commandMetadata": { "webCommandMetadata": {
                    "url": "/shorts/aaaaaaaaaaa"
                  } } } },
                  "overlayMetadata": {
                    "primaryText": { "content": "First short" },
                    "secondaryText": { "content": "35M views" }
                  },
                  "thumbnailViewModel": { "thumbnailViewModel": { "image": { "sources": [
                    { "url": "https://i.ytimg.test/small.jpg", "width": 405 },
                    { "url": "https://i.ytimg.test/big.jpg", "width": 720 }
                  ] } } }
                } } } },
                { "continuationItemRenderer": {
                  "continuationEndpoint": { "continuationCommand": { "token": "TOK_NEXT_PAGE" } }
                } }
              ]
            } }
          } } ] } }
        }
        """.trimIndent()

    @Test
    fun `reads the sort bar in the order youtube sent it`() {
        val page = parse(initialBrowse)

        assertEquals(listOf("Latest", "Popular", "Oldest"), page.sorts.map { it.label })
        assertEquals(listOf("TOK_LATEST", "TOK_POPULAR", "TOK_OLDEST"), page.sorts.map { it.token })
        assertEquals(listOf(true, false, false), page.sorts.map { it.selected })
    }

    // Both the chips and the grid hand back a `continuationCommand`; only the grid's sits under a
    // continuationItemRenderer. Picking the wrong one silently pages into a re-sorted list.
    @Test
    fun `takes the grid continuation, not a sort chip token`() {
        assertEquals("TOK_NEXT_PAGE", parse(initialBrowse).continuation)
    }

    @Test
    fun `reads the shorts and the channel they belong to`() {
        val page = parse(initialBrowse)

        assertEquals(1, page.shorts.size)
        assertEquals("aaaaaaaaaaa", page.shorts.single().id)
        assertEquals("First short", page.shorts.single().title)
        assertEquals(35_000_000L, page.shorts.single().viewCount)
        assertTrue(
            "widest source wins",
            page.shorts
                .single()
                .thumbnailUrl
                .endsWith("big.jpg"),
        )
        assertEquals("UCabcdefghijklmnopqrstu", page.channelId)
        assertEquals("Some Creator", page.channelName)
        assertEquals("https://yt3.example/large.jpg", page.channelAvatarUrl)
    }

    // Switching sort and paging both return this shape: items appended, no channel header.
    @Test
    fun `parses a continuation response`() {
        val page =
            parse(
                """
                {
                  "onResponseReceivedActions": [ { "appendContinuationItemsAction": { "continuationItems": [
                    { "richItemRenderer": { "content": { "shortsLockupViewModel": {
                      "onTap": { "innertubeCommand": { "commandMetadata": { "webCommandMetadata": {
                        "url": "/shorts/bbbbbbbbbbb"
                      } } } },
                      "overlayMetadata": {
                        "primaryText": { "content": "Second short" },
                        "secondaryText": { "content": "1.2K views" }
                      }
                    } } } }
                  ] } } ]
                }
                """.trimIndent(),
            )

        assertEquals(listOf("bbbbbbbbbbb"), page.shorts.map { it.id })
        assertEquals(1_200L, page.shorts.single().viewCount)
        assertNull("nothing more to page", page.continuation)
        assertTrue("a continuation carries no sort bar", page.sorts.isEmpty())
        assertEquals("", page.channelName)
    }

    // A channel with too few Shorts to sort, or a future layout change, must not crash the tab.
    @Test
    fun `survives a response with no sort bar and no items`() {
        val page = parse("""{ "contents": {} }""")

        assertTrue(page.shorts.isEmpty())
        assertTrue(page.sorts.isEmpty())
        assertNull(page.continuation)
    }

    // The Videos and Live tabs ship the same bar, which is why the parser is shared with them.
    @Test
    fun `reads a sort bar nested under a reload command`() {
        val sorts =
            Json
                .parseToJsonElement(
                    """
                    {
                      "onResponseReceivedActions": [ { "reloadContinuationItemsCommand": {
                        "continuationItems": [ { "richGridRenderer": { "header": { "chipBarViewModel": { "chips": [
                          { "chipViewModel": {
                            "text": "Popular", "selected": true,
                            "tapCommand": { "innertubeCommand": { "continuationCommand": { "token": "T1" } } }
                          } }
                        ] } } } } ]
                      } } ]
                    }
                    """.trimIndent(),
                ).channelSortOptions()

        assertEquals(listOf("Popular"), sorts.map { it.label })
        assertEquals("T1", sorts.single().token)
        assertTrue(sorts.single().selected)
    }

    // A real channel page carries three continuationItemRenderers: the grid's, and one each under
    // the header's description and attribution. Taking the first in document order finds the right
    // one only by accident of key ordering, and paging would silently follow the channel blurb.
    @Test
    fun `takes the grid continuation, not one buried in the page header`() {
        val page =
            parse(
                """
                {
                  "header": { "pageHeaderRenderer": { "content": { "pageHeaderViewModel": {
                    "description": { "descriptionPreviewViewModel": { "rendererContext": {
                      "continuationItemRenderer": {
                        "continuationEndpoint": { "continuationCommand": { "token": "HEADER_TOKEN" } }
                      }
                    } } }
                  } } } },
                  "contents": { "twoColumnBrowseResultsRenderer": { "tabs": [ { "tabRenderer": {
                    "selected": true,
                    "content": { "richGridRenderer": { "contents": [
                      { "continuationItemRenderer": {
                        "continuationEndpoint": { "continuationCommand": { "token": "GRID_TOKEN" } }
                      } }
                    ] } }
                  } } ] } }
                }
                """.trimIndent(),
            )

        assertEquals("GRID_TOKEN", page.continuation)
    }

    // A chip with no token cannot be acted on, so offering it would be a dead control.
    @Test
    fun `skips a chip that carries no continuation`() {
        val sorts =
            Json
                .parseToJsonElement(
                    """
                    { "chipBarViewModel": { "chips": [
                      { "chipViewModel": { "text": "Latest", "selected": true } },
                      { "chipViewModel": {
                        "text": "Oldest", "selected": false,
                        "tapCommand": { "innertubeCommand": { "continuationCommand": { "token": "T2" } } }
                      } }
                    ] } }
                    """.trimIndent(),
                ).channelSortOptions()

        assertEquals(listOf("Oldest"), sorts.map { it.label })
    }
}
