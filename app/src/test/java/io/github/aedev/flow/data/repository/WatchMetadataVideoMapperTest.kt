package io.github.aedev.flow.data.repository

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.models.response.WatchMetadataResponse
import org.junit.Test

class WatchMetadataVideoMapperTest {

    @Test
    fun `related mapper preserves byline channel id when compact video exposes one`() {
        val response = watchMetadataResponse(
            compactVideo(
                videoId = "related-video",
                channelId = "UC1234567890",
            )
        )

        val related = WatchMetadataVideoMapper.relatedVideos(response)

        assertThat(related).hasSize(1)
        assertThat(related.single().channelId).isEqualTo("UC1234567890")
    }

    @Test
    fun `related mapper leaves channel id blank when byline browse id is not a channel`() {
        val response = watchMetadataResponse(
            compactVideo(
                videoId = "related-video",
                channelId = "VLPL1234567890",
            )
        )

        val related = WatchMetadataVideoMapper.relatedVideos(response)

        assertThat(related).hasSize(1)
        assertThat(related.single().channelId).isEmpty()
    }

    private fun watchMetadataResponse(
        video: WatchMetadataResponse.CompactVideo
    ) = WatchMetadataResponse(
        contents = WatchMetadataResponse.Contents(
            twoColumnWatchNextResults = WatchMetadataResponse.TwoColumn(
                secondaryResults = WatchMetadataResponse.SecondaryWrap(
                    secondaryResults = WatchMetadataResponse.SecondaryInner(
                        results = listOf(
                            WatchMetadataResponse.SecondaryItem(compactVideoRenderer = video)
                        )
                    )
                )
            )
        )
    )

    private fun compactVideo(
        videoId: String,
        channelId: String,
    ) = WatchMetadataResponse.CompactVideo(
        videoId = videoId,
        title = WatchMetadataResponse.SimpleText(simpleText = "Related Video"),
        longBylineText = WatchMetadataResponse.Runs(
            runs = listOf(
                WatchMetadataResponse.Runs.Run(
                    text = "Flow Channel",
                    navigationEndpoint = WatchMetadataResponse.NavEndpoint(
                        browseEndpoint = WatchMetadataResponse.NavEndpoint.BrowseEndpoint(
                            browseId = channelId
                        )
                    )
                )
            )
        ),
    )
}
