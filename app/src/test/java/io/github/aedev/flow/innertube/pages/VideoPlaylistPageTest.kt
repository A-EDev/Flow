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
}
