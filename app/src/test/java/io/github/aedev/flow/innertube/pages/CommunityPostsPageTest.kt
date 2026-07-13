package io.github.aedev.flow.innertube.pages

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommunityPostsPageTest {
    @Test
    fun `reads every modern entity text shape`() {
        assertEquals(
            "Primitive text",
            Json.parseToJsonElement("\"Primitive text\"").youtubeText(),
        )
        assertEquals(
            "Content text",
            Json.parseToJsonElement("""{"content":"Content text"}""").youtubeText(),
        )
        assertEquals(
            "Mixed runs",
            Json.parseToJsonElement(
                """{"runs":[{"text":"Mixed "},{"content":"runs"}]}"""
            ).youtubeText(),
        )
    }

    @Test
    fun `parses community post and sign-in wrapped comment endpoint`() {
        val response = Json.parseToJsonElement(
            """
            {
              "contents": [{
                "richItemRenderer": {
                  "content": {
                    "backstagePostThreadRenderer": {
                      "post": {
                        "backstagePostRenderer": {
                          "postId": "post-1",
                          "authorText": {"runs": [{"text": "Flow"}]},
                          "authorThumbnail": {"thumbnails": [
                            {"url": "//avatar-small", "width": 40, "height": 40},
                            {"url": "//avatar-large", "width": 80, "height": 80}
                          ]},
                          "contentText": {"runs": [{"text": "Hello "}, {"text": "community"}]},
                          "publishedTimeText": {"simpleText": "2 hours ago"},
                          "voteCount": {"simpleText": "1.2K"},
                          "backstageAttachment": {
                            "backstageImageRenderer": {
                              "image": {"thumbnails": [
                                {"url": "//post-small", "width": 320, "height": 180},
                                {"url": "//post-large", "width": 1280, "height": 720}
                              ]}
                            }
                          },
                          "actionButtons": {
                            "commentActionButtonsRenderer": {
                              "replyButton": {
                                "buttonRenderer": {
                                  "accessibilityData": {"accessibilityData": {"label": "Comment (45)"}},
                                  "navigationEndpoint": {
                                    "signInEndpoint": {
                                      "nextEndpoint": {"browseEndpoint": {"params": "post-params"}}
                                    }
                                  }
                                }
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
              }, {
                "continuationItemRenderer": {
                  "continuationEndpoint": {"continuationCommand": {"token": "posts-next"}}
                }
              }]
            }
            """.trimIndent()
        )

        val page = response.toCommunityPostsPage("Fallback", "fallback-avatar")
        val post = page.posts.single()
        assertEquals("post-1", post.id)
        assertEquals("Flow", post.authorName)
        assertEquals("https://avatar-large", post.authorAvatarUrl)
        assertEquals("Hello community", post.text)
        assertEquals("https://post-large", post.imageUrl)
        assertEquals("1.2K", post.likeCountText)
        assertEquals("45", post.commentCountText)
        assertEquals("post-params", post.commentEndpointParams)
        assertEquals("posts-next", page.continuation)
    }

    @Test
    fun `parses modern entity comments with reply and page continuations`() {
        val response = Json.parseToJsonElement(
            """
            {
              "onResponseReceivedEndpoints": [{
                "appendContinuationItemsAction": {
                  "continuationItems": [{
                    "commentThreadRenderer": {
                      "commentViewModel": {
                        "commentKey": "comment-key",
                        "commentId": "comment-fallback"
                      },
                      "replies": {
                        "commentRepliesRenderer": {
                          "contents": [{
                            "continuationItemRenderer": {
                              "continuationEndpoint": {"continuationCommand": {"token": "replies-next"}}
                            }
                          }]
                        }
                      }
                    }
                  }, {
                    "continuationItemRenderer": {
                      "continuationEndpoint": {"continuationCommand": {"token": "comments-next"}}
                    }
                  }]
                }
              }],
              "frameworkUpdates": {
                "entityBatchUpdate": {
                  "mutations": [{
                    "entityKey": "comment-key",
                    "payload": {
                      "commentEntityPayload": {
                        "properties": {
                          "commentId": "comment-1",
                          "content": "Modern comment",
                          "publishedTime": "5 hours ago"
                        },
                        "author": {
                          "displayName": "Viewer",
                          "channelId": "UCviewer",
                          "avatar": {"image": {"sources": [{"url": "//viewer-avatar"}]}}
                        },
                        "toolbar": {
                          "replyCount": "3 replies",
                          "likeCountNotliked": "42"
                        }
                      }
                    }
                  }]
                }
              }
            }
            """.trimIndent()
        )

        val page = response.toCommunityCommentsPage()
        val comment = page.comments.single()
        assertEquals("comment-1", comment.id)
        assertEquals("Viewer", comment.author)
        assertEquals("https://viewer-avatar", comment.authorThumbnail)
        assertEquals("Modern comment", comment.text)
        assertEquals(42, comment.likeCount)
        assertEquals(3, comment.replyCount)
        assertEquals("UCviewer", comment.authorChannelId)
        assertEquals("replies-next", comment.continuationToken)
        assertEquals("comments-next", page.continuation)
    }

    @Test
    fun `parses legacy comment renderer`() {
        val response = Json.parseToJsonElement(
            """
            {
              "commentThreadRenderer": {
                "comment": {
                  "commentRenderer": {
                    "commentId": "legacy-1",
                    "authorText": {"simpleText": "Legacy viewer"},
                    "authorThumbnail": {"thumbnails": [{"url": "https://legacy-avatar"}]},
                    "contentText": {"simpleText": "Legacy comment"},
                    "publishedTimeText": {"simpleText": "1 day ago"},
                    "voteCount": {"simpleText": "1.5K"},
                    "replyCount": 0,
                    "authorEndpoint": {"browseEndpoint": {"browseId": "UClegacy"}}
                  }
                }
              }
            }
            """.trimIndent()
        )

        val page = response.toCommunityCommentsPage()
        val comment = page.comments.single()
        assertEquals("legacy-1", comment.id)
        assertEquals(1500, comment.likeCount)
        assertEquals("UClegacy", comment.authorChannelId)
        assertNull(page.continuation)
    }
}
