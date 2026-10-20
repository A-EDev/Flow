package io.github.aedev.flow.innertube.pages

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

class VideoPlaylistPageTest {
    private fun lockup(
        id: String,
        title: String,
        channel: String,
        channelId: String,
    ) = """
        {"lockupViewModel": {
          "contentId": "$id",
          "contentType": "LOCKUP_CONTENT_TYPE_VIDEO",
          "contentImage": {"thumbnailViewModel": {
            "image": {"sources": [{"url": "https://i.ytimg.com/vi/$id/hqdefault.jpg", "width": 336, "height": 188}]},
            "overlays": [{"thumbnailBottomOverlayViewModel": {"badges": [{"thumbnailBadgeViewModel": {"text": "4:57"}}]}}]
          }},
          "metadata": {"lockupMetadataViewModel": {
            "title": {"content": "$title"},
            "metadata": {"contentMetadataViewModel": {"metadataRows": [{"metadataParts": [
              {"text": {"content": "$channel", "commandRuns": [{"onTap": {"innertubeCommand": {"browseEndpoint": {"browseId": "$channelId"}}}}]}},
              {"text": {"content": "3.6M views"}},
              {"text": {"content": "2 years ago"}}
            ]}]}}
          }}
        }}
        """

    private val continuationItem =
        """{"continuationItemRenderer": {"continuationEndpoint": {"continuationCommand": {"token": "NEXT"}}}}"""

    @Test
    fun `reads the header, each video's own channel and the next token`() {
        val page =
            Json
                .parseToJsonElement(
                    """
                    {
                      "header": {"pageHeaderRenderer": {"content": {"pageHeaderViewModel": {
                        "title": {"dynamicTextViewModel": {"text": {"content": "Weekend builds"}}},
                        "metadata": {"contentMetadataViewModel": {"metadataRows": [{"metadataParts": [
                          {"avatarStack": {"avatarStackViewModel": {"text": {"content": "by Bench Notes",
                            "commandRuns": [{"onTap": {"innertubeCommand": {"browseEndpoint": {"browseId": "UCowner"}}}}]}}}}
                        ]}]}},
                        "description": {"descriptionPreviewViewModel": {"description": {"content": "Things to watch"}}}
                      }}}},
                      "contents": {"twoColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
                        "sectionListRenderer": {"contents": [{"itemSectionRenderer": {"contents": [
                          ${lockup("vid1", "Restoring an amp", "Bench Notes", "UCbench")},
                          ${lockup("vid2", "Cutting dovetails", "Oak and Iron", "UCoak")},
                          $continuationItem
                        ]}}]}
                      }}}]}}
                    }
                    """,
                ).toVideoPlaylistPage()

        assertThat(page.title).isEqualTo("Weekend builds")
        assertThat(page.ownerName).isEqualTo("Bench Notes")
        assertThat(page.ownerId).isEqualTo("UCowner")
        assertThat(page.description).isEqualTo("Things to watch")
        assertThat(page.videos.map { it.id }).containsExactly("vid1", "vid2").inOrder()
        assertThat(page.videos[1].channelName).isEqualTo("Oak and Iron")
        assertThat(page.videos[1].channelId).isEqualTo("UCoak")
        assertThat(page.videos[0].uploadDate).isEqualTo("2 years ago")
        assertThat(page.videos[0].duration).isEqualTo(297)
        assertThat(page.continuation).isEqualTo("NEXT")
        assertThat(page.isCourse).isFalse()
    }

    @Test
    fun `reads the token from the view model that now closes a page of 100 (#1299)`() {
        val page =
            Json
                .parseToJsonElement(
                    """
                    {"contents": {"twoColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
                      "sectionListRenderer": {"contents": [{"itemSectionRenderer": {"contents": [
                        ${lockup("vid1", "Lesson one", "Telusko", "UCtelusko")},
                        {"continuationItemViewModel": {
                          "trigger": "CONTINUATION_TRIGGER_ON_ITEM_SHOWN",
                          "continuationCommand": {"innertubeCommand": {
                            "commandMetadata": {"webCommandMetadata": {"sendPost": true, "apiUrl": "/youtubei/v1/browse"}},
                            "continuationCommand": {"token": "PAGE2", "request": "CONTINUATION_REQUEST_TYPE_BROWSE"}
                          }}
                        }}
                      ]}}]}
                    }}}]}}}
                    """,
                ).toVideoPlaylistPage()

        assertThat(page.videos.map { it.id }).containsExactly("vid1")
        assertThat(page.continuation).isEqualTo("PAGE2")
    }

    @Test
    fun `a course page reads its title and channel from the older playlist header`() {
        val page =
            Json
                .parseToJsonElement(
                    """
                    {
                      "header": {"playlistHeaderRenderer": {
                        "playlistId": "PLcourse",
                        "title": {"simpleText": "Python for Beginners (Full Course)"},
                        "ownerText": {"runs": [{"text": "Telusko",
                          "navigationEndpoint": {"browseEndpoint": {"browseId": "UCtelusko", "canonicalBaseUrl": "/@Telusko"}}}]},
                        "playlistBadges": [{"metadataBadgeRenderer": {"label": "Course"}}]
                      }},
                      "contents": {"twoColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
                        "sectionListRenderer": {"contents": [
                          {"courseProgressViewModel": {"progressText": {"content": "0 of 124 lessons complete"}}},
                          {"itemSectionRenderer": {"contents": [
                            ${lockup("vid1", "Lesson one", "Telusko", "UCtelusko")}
                          ]}}
                        ]}
                      }}}]}}
                    }
                    """,
                ).toVideoPlaylistPage()

        assertThat(page.title).isEqualTo("Python for Beginners (Full Course)")
        assertThat(page.ownerName).isEqualTo("Telusko")
        assertThat(page.ownerId).isEqualTo("UCtelusko")
        assertThat(page.isCourse).isTrue()
    }

    @Test
    fun `a continuation carries only items and ends without a token`() {
        val page =
            Json
                .parseToJsonElement(
                    """
                    {"onResponseReceivedActions": [{"appendContinuationItemsAction": {"continuationItems": [
                      ${lockup("vid3", "Casting aluminium", "Foundry Friday", "UCfoundry")}
                    ]}}]}
                    """,
                ).toVideoPlaylistPage()

        assertThat(page.videos.single().id).isEqualTo("vid3")
        assertThat(page.continuation).isNull()
        assertThat(page.title).isNull()
    }

    private fun shortsLockup(
        id: String,
        title: String,
    ) = """
        {"richItemRenderer": {"content": {"shortsLockupViewModel": {
          "entityId": "e-$id",
          "onTap": {"innertubeCommand": {
            "commandMetadata": {"webCommandMetadata": {"url": "/shorts/$id"}},
            "reelWatchEndpoint": {"videoId": "$id", "playlistId": "PLshorts"}
          }},
          "overlayMetadata": {"primaryText": {"content": "$title"}, "secondaryText": {"content": "407 views"}}
        }}}}
        """

    @Test
    fun `a Shorts-only playlist reads its grid and the header count (#1358)`() {
        val page =
            Json
                .parseToJsonElement(
                    """
                    {
                      "contents": {"twoColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
                        "sectionListRenderer": {"contents": [{"itemSectionRenderer": {"contents": [
                          {"richGridRenderer": {"contents": [
                            ${shortsLockup("short1", "Say yes")},
                            ${shortsLockup("short2", "Night drive")}
                          ]}}
                        ]}}]}
                      }}}]}},
                      "sidebar": {"playlistSidebarRenderer": {"items": [{"playlistSidebarPrimaryInfoRenderer": {
                        "title": {"runs": [{"text": "Lyrics"}]},
                        "stats": [{"runs": [{"text": "1,167"}, {"text": " videos"}]}, {"simpleText": "3,691 views"}]
                      }}]}}
                    }
                    """,
                ).toVideoPlaylistPage()

        assertThat(page.isShortsGrid).isTrue()
        assertThat(page.videos.map { it.id }).containsExactly("short1", "short2").inOrder()
        assertThat(page.videos.first().isShort).isTrue()
        assertThat(page.continuation).isNull()
        assertThat(page.videoCount).isEqualTo(1167)
    }

    @Test
    fun `a list page is not a grid`() {
        val page =
            Json
                .parseToJsonElement(
                    """
                    {"contents": {"twoColumnBrowseResultsRenderer": {"tabs": [{"tabRenderer": {"content": {
                      "sectionListRenderer": {"contents": [{"itemSectionRenderer": {"contents": [
                        ${lockup("vid1", "Lesson one", "Telusko", "UCtelusko")}
                      ]}}]}
                    }}}]}}}
                    """,
                ).toVideoPlaylistPage()

        assertThat(page.isShortsGrid).isFalse()
        assertThat(page.videoCount).isNull()
    }
}
