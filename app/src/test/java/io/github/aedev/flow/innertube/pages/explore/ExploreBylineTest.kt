package io.github.aedev.flow.innertube.pages.explore

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.channel.toChannelTabContent
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import org.junit.Test

/**
 * An explore grid has no owner to inherit a byline from, and a `gridVideoRenderer` carries neither
 * `ownerText` nor the avatar field a watch-page row uses, so both have to be read from the fields it
 * does ship.
 */
class ExploreBylineTest {
    private fun gamingVideos(): List<Video> =
        ExploreFixture(ExploreFixture.GAMING_TRENDING)
            .toChannelTabContent(ChannelTabKind.Videos, FeedItemOwner())
            .items
            .filterIsInstance<FeedItem.VideoItem>()
            .map { it.video }

    @Test
    fun `a grid row names its creator from the short byline`() {
        val videos = gamingVideos()

        assertThat(videos).isNotEmpty()
        assertThat(videos.all { it.channelName.isNotBlank() }).isTrue()
        assertThat(videos.all { it.channelId.startsWith("UC") }).isTrue()
    }

    @Test
    fun `a grid row carries the creator avatar it shipped`() {
        assertThat(gamingVideos().all { it.channelThumbnailUrl.isNotBlank() }).isTrue()
    }

    @Test
    fun `a grid row keeps its view count and date`() {
        val videos = gamingVideos()

        assertThat(videos.all { it.viewCount > 0L }).isTrue()
        assertThat(videos.all { it.uploadDate.isNotBlank() }).isTrue()
    }

    @Test
    fun `destination rows name their creator too`() {
        val videos =
            ExploreFixture(ExploreFixture.DESTINATION_LIVE)
                .toSettledExploreDestinationPage()
                .shelves
                .flatMap { it.items }
                .filterIsInstance<FeedItem.VideoItem>()
                .map { it.video }

        assertThat(videos).isNotEmpty()
        assertThat(videos.all { it.channelName.isNotBlank() }).isTrue()
        assertThat(videos.all { it.channelThumbnailUrl.isNotBlank() }).isTrue()
    }
}
