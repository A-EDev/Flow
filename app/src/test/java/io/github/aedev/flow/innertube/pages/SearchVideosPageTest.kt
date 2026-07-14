package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchVideosPageTest {
    @Test
    fun parsesServerOrderedVideosAndContinuation() {
        val response = Json.parseToJsonElement(
            """
            {
              "contents": {
                "sectionListRenderer": {
                  "contents": [
                    {
                      "itemSectionRenderer": {
                        "contents": [
                          {
                            "videoRenderer": {
                              "videoId": "LXb3EKWsInQ",
                              "title": { "runs": [{ "text": "Costa Rica in 4K" }] },
                              "ownerText": {
                                "runs": [{
                                  "text": "Jacob + Katie Schwarz",
                                  "navigationEndpoint": { "browseEndpoint": { "browseId": "UC123" } }
                                }]
                              },
                              "thumbnail": {
                                "thumbnails": [
                                  { "url": "small.jpg", "width": 320 },
                                  { "url": "large.jpg", "width": 1280 }
                                ]
                              },
                              "lengthText": { "simpleText": "5:14" },
                              "viewCountText": { "simpleText": "331,224,211 views" },
                              "publishedTimeText": { "simpleText": "7 years ago" },
                              "channelThumbnailSupportedRenderers": {
                                "channelThumbnailWithLinkRenderer": {
                                  "thumbnail": { "thumbnails": [{ "url": "avatar.jpg", "width": 88 }] }
                                }
                              }
                            }
                          },
                          {
                            "videoRenderer": {
                              "videoId": "RK1K2bCg4J8",
                              "title": { "simpleText": "Breathtaking Colors of Nature" },
                              "shortViewCountText": { "simpleText": "68M views" }
                            }
                          }
                        ]
                      }
                    },
                    {
                      "continuationItemRenderer": {
                        "continuationEndpoint": {
                          "continuationCommand": { "token": "next-page-token" }
                        }
                      }
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        ).jsonObject

        val page = response.toSearchVideosPage()

        assertEquals(listOf("LXb3EKWsInQ", "RK1K2bCg4J8"), page.videos.map { it.id })
        assertEquals(331_224_211L, page.videos.first().viewCount)
        assertEquals("UC123", page.videos.first().channelId)
        assertEquals("large.jpg", page.videos.first().thumbnailUrl)
        assertEquals(314, page.videos.first().duration)
        assertEquals(listOf("avatar.jpg"), page.videos.first().channelThumbnailUrls)
        assertFalse(page.videos.first().isLive)
        assertEquals(68_000_000L, page.videos.last().viewCount)
        assertEquals("next-page-token", page.continuation)
    }

    @Test
    fun detectsLiveStyleBeyondOtherOverlayStyles() {
        val response = Json.parseToJsonElement(
            """
            {
              "videoRenderer": {
                "videoId": "livevideo01",
                "title": { "simpleText": "Live nature" },
                "thumbnailOverlays": [
                  { "thumbnailOverlayNowPlayingRenderer": { "style": "DEFAULT" } },
                  { "thumbnailOverlayTimeStatusRenderer": { "style": "LIVE" } }
                ]
              }
            }
            """.trimIndent()
        ).jsonObject

        assertEquals(true, response.toSearchVideosPage().videos.single().isLive)
    }

    @Test
    fun preservesVideosChannelsAndPlaylistsInServerOrder() {
        val response = Json.parseToJsonElement(
            """
            {
              "contents": [
                {
                  "channelRenderer": {
                    "channelId": "UC123",
                    "title": { "simpleText": "Nature Channel" },
                    "thumbnail": {
                      "thumbnails": [{ "url": "channel.jpg", "width": 512 }]
                    },
                    "subscriberCountText": { "simpleText": "1.2M subscribers" },
                    "descriptionSnippet": { "runs": [{ "text": "Nature films" }] },
                    "navigationEndpoint": {
                      "browseEndpoint": { "canonicalBaseUrl": "/@nature" }
                    }
                  }
                },
                {
                  "videoRenderer": {
                    "videoId": "LXb3EKWsInQ",
                    "title": { "simpleText": "Costa Rica in 4K" }
                  }
                },
                {
                  "playlistRenderer": {
                    "playlistId": "PL123",
                    "title": { "simpleText": "Nature playlist" },
                    "thumbnails": [{
                      "thumbnails": [{ "url": "playlist.jpg", "width": 640 }]
                    }],
                    "videoCount": "42"
                  }
                }
              ]
            }
            """.trimIndent()
        ).jsonObject

        val page = response.toSearchVideosPage()

        assertTrue(page.items[0] is SearchChannelItem)
        assertTrue(page.items[1] is SearchVideoItem)
        assertTrue(page.items[2] is SearchPlaylistItem)
        assertEquals(1_200_000L, (page.items[0] as SearchChannelItem).subscriberCount)
        assertEquals("https://www.youtube.com/@nature", (page.items[0] as SearchChannelItem).url)
        assertEquals("playlist.jpg", (page.items[2] as SearchPlaylistItem).thumbnailUrl)
        assertEquals(42, (page.items[2] as SearchPlaylistItem).videoCount)
    }

    @Test
    fun parsesCurrentPlaylistLockupRenderer() {
        val response = Json.parseToJsonElement(
            """
            {
              "lockupViewModel": {
                "contentImage": {
                  "collectionThumbnailViewModel": {
                    "primaryThumbnail": {
                      "thumbnailViewModel": {
                        "image": {
                          "sources": [
                            { "url": "small.jpg", "width": 360 },
                            { "url": "large.jpg", "width": 720 }
                          ]
                        },
                        "overlays": [{
                          "thumbnailOverlayBadgeViewModel": {
                            "thumbnailBadges": [{
                              "thumbnailBadgeViewModel": { "text": "39 videos" }
                            }]
                          }
                        }]
                      }
                    }
                  }
                },
                "metadata": {
                  "lockupMetadataViewModel": {
                    "title": { "content": "Nature playlist" }
                  }
                },
                "contentId": "PL123",
                "contentType": "LOCKUP_CONTENT_TYPE_PLAYLIST"
              }
            }
            """.trimIndent()
        ).jsonObject

        val playlist = response.toSearchVideosPage().items.single() as SearchPlaylistItem

        assertEquals("PL123", playlist.id)
        assertEquals("Nature playlist", playlist.name)
        assertEquals("large.jpg", playlist.thumbnailUrl)
        assertEquals(39, playlist.videoCount)
    }
}
