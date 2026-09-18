package io.github.aedev.flow.innertube.pages.explore

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import org.junit.Test

/**
 * The destination live rows carry no `badges` array at all, so the thumbnail overlay style is the
 * only signal that survives a locale where "watching" is not the word. Their counts are concurrent
 * viewers, which a card would otherwise print as views.
 */
class ExploreLiveSignalsTest {
    private fun videos(title: String): List<Video> =
        ExploreFixture(ExploreFixture.DESTINATION_LIVE)
            .toExploreDestinationPage()
            .shelves
            .first { it.title == title }
            .items
            .filterIsInstance<FeedItem.VideoItem>()
            .map { it.video }

    @Test
    fun `a live row is marked live from its overlay, without a badge`() {
        assertThat(videos("Live Now").all { it.isLive }).isTrue()
    }

    @Test
    fun `a live row's watching count is not reported as a view count`() {
        assertThat(videos("Live Now").all { it.viewCount == 0L }).isTrue()
    }

    /** YouTube mixes already-started streams into its own "Upcoming" shelf, so the row decides, not the shelf. */
    @Test
    fun `an upcoming row is marked upcoming and carries its scheduled start`() {
        val upcoming = videos("Upcoming Live Streams").filter { it.isUpcoming }

        assertThat(upcoming).isNotEmpty()
        assertThat(upcoming.all { it.timestamp > 0L }).isTrue()
        assertThat(upcoming.all { it.uploadDate.isNotEmpty() }).isTrue()
    }

    @Test
    fun `a waiting or watching count is never reported as a view count`() {
        assertThat(videos("Upcoming Live Streams").all { it.viewCount == 0L }).isTrue()
    }

    @Test
    fun `a finished stream keeps its duration and its real view count`() {
        val recent = videos("Recent Live Streams")

        assertThat(recent).isNotEmpty()
        assertThat(recent.none { it.isLive || it.isUpcoming }).isTrue()
        assertThat(recent.any { it.duration > 0 }).isTrue()
        assertThat(recent.any { it.viewCount > 0L }).isTrue()
    }
}
