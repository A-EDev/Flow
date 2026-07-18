package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.models.response.WatchMetadataResponse
import io.github.aedev.flow.utils.parseToTimestamp
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class WatchMetadataMergeTest {
    @Test
    fun `fresh watch metadata replaces a corrupted legacy release date`() {
        val response = Json { ignoreUnknownKeys = true }
            .decodeFromString<WatchMetadataResponse>(WATCH_METADATA_JSON)
        val legacy = Video(
            id = "video-id",
            title = "Old title",
            channelName = "Old channel",
            channelId = "old-channel-id",
            thumbnailUrl = "thumbnail",
            duration = 120,
            viewCount = 10,
            uploadDate = "50 minutes ago"
        )

        val refreshed = mergeWatchMetadata(legacy, response)

        assertNotNull(refreshed)
        assertEquals("Updated title", refreshed?.title)
        assertEquals("Updated channel", refreshed?.channelName)
        assertEquals("UC-updated", refreshed?.channelId)
        assertEquals(1_234L, refreshed?.viewCount)
        assertEquals("Jun 1, 2024", refreshed?.uploadDate)
        assertEquals(parseToTimestamp("Jun 1, 2024"), refreshed?.timestamp)
        assertEquals("Updated description", refreshed?.description)
        assertEquals("avatar", refreshed?.channelThumbnailUrl)
    }

    private companion object {
        val WATCH_METADATA_JSON = """
            {
              "contents": {
                "twoColumnWatchNextResults": {
                  "results": {
                    "results": {
                      "contents": [
                        {
                          "videoPrimaryInfoRenderer": {
                            "title": {"runs": [{"text": "Updated title"}]},
                            "viewCount": {
                              "videoViewCountRenderer": {
                                "viewCount": {"simpleText": "1,234 views"}
                              }
                            },
                            "dateText": {"simpleText": "Jun 1, 2024"}
                          }
                        },
                        {
                          "videoSecondaryInfoRenderer": {
                            "owner": {
                              "videoOwnerRenderer": {
                                "thumbnail": {
                                  "thumbnails": [{"url": "avatar", "height": 48}]
                                },
                                "title": {"runs": [{"text": "Updated channel"}]},
                                "navigationEndpoint": {
                                  "browseEndpoint": {"browseId": "UC-updated"}
                                }
                              }
                            },
                            "attributedDescription": {"content": "Updated description"}
                          }
                        }
                      ]
                    }
                  }
                }
              }
            }
        """.trimIndent()
    }
}
