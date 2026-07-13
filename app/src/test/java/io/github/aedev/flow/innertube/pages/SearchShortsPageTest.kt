package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchShortsPageTest {
    @Test
    fun parsesShortsRegardlessOfRendererNesting() {
        val response = Json.parseToJsonElement(
            """
            {
              "contents": {
                "unexpectedRenderer": {
                  "items": [{
                    "shortsLockupViewModel": {
                      "onTap": {
                        "innertubeCommand": {
                          "commandMetadata": {
                            "webCommandMetadata": { "url": "/shorts/abcdefghijk?feature=share" }
                          }
                        }
                      },
                      "overlayMetadata": {
                        "primaryText": { "content": "A short title" },
                        "secondaryText": { "content": "1.2K views" }
                      }
                    }
                  }]
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        assertEquals(
            listOf(SearchShortItem("abcdefghijk", "A short title", 1_200L)),
            response.toSearchShorts()
        )
    }

    @Test
    fun parsesLegacyReelItemsAndRemovesDuplicates() {
        val response = Json.parseToJsonElement(
            """
            {
              "first": {
                "reelItemRenderer": {
                  "videoId": "12345678901",
                  "headline": { "simpleText": "Legacy short" },
                  "viewCountText": { "simpleText": "2M views" }
                }
              },
              "duplicate": {
                "reelItemRenderer": {
                  "videoId": "12345678901",
                  "headline": { "simpleText": "Legacy short" }
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        assertEquals(
            listOf(SearchShortItem("12345678901", "Legacy short", 2_000_000L)),
            response.toSearchShorts()
        )
    }
}
