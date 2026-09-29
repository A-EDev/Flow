package io.github.aedev.flow.innertube.pages.explore

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedShelf
import org.junit.Test

class ExploreDestinationParserTest {
    private fun page(name: String) = ExploreFixture(name).toSettledExploreDestinationPage()

    private fun shelfTitled(
        name: String,
        title: String,
    ): FeedShelf = page(name).shelves.first { it.title == title }

    @Test
    fun `reads the live destination's shelves out of rich section renderers`() {
        val page = page(ExploreFixture.DESTINATION_LIVE)

        assertThat(page.title).isEqualTo("Live")
        assertThat(page.shelves.map { it.title })
            .containsAtLeast("Live Now", "Recent Live Streams", "Upcoming Live Streams")
            .inOrder()
    }

    @Test
    fun `a shelf carries the see-all params the response shipped`() {
        val shelf = shelfTitled(ExploreFixture.DESTINATION_LIVE, "Live Now")

        assertThat(shelf.moreParams).isEqualTo("EgdsaXZldGFikgEDCKEK")
    }

    @Test
    fun `live rows parse as videos with their own byline`() {
        val items = shelfTitled(ExploreFixture.DESTINATION_LIVE, "Live Now").items

        assertThat(items).isNotEmpty()
        assertThat(items.all { it is FeedItem.VideoItem }).isTrue()
        val video = (items.first() as FeedItem.VideoItem).video
        assertThat(video.channelName).isNotEmpty()
        assertThat(video.channelId).startsWith("UC")
    }

    @Test
    fun `the hero carousel is skipped rather than rendered as an empty shelf`() {
        val page = page(ExploreFixture.DESTINATION_LIVE)

        assertThat(page.shelves.none { it.items.isEmpty() }).isTrue()
    }

    @Test
    fun `shelf ids are position-qualified so same-titled shelves never collide`() {
        val ids = page(ExploreFixture.DESTINATION_NEWS).shelves.map { it.id }

        assertThat(ids).containsNoDuplicates()
    }

    @Test
    fun `the news destination publishes its category tabs with their params`() {
        val tabs = page(ExploreFixture.DESTINATION_NEWS).tabs

        assertThat(tabs.map { it.title }).containsAtLeast("Top stories", "Sports", "World")
        assertThat(tabs.first().selected).isTrue()
        assertThat(tabs.first { it.title == "Sports" }.params).isEqualTo("EgZzcG9ydHPSBwIIAg%3D%3D")
        assertThat(tabs.all { it.browseId == "FEnews_destination" }).isTrue()
    }

    @Test
    fun `the news destination carries a shelf of community posts`() {
        val posts =
            page(ExploreFixture.DESTINATION_NEWS)
                .shelves
                .flatMap { it.items }
                .filterIsInstance<FeedItem.PostItem>()

        assertThat(posts).isNotEmpty()
    }

    @Test
    fun `the music destination's album lockups parse as playlists`() {
        val shelf = shelfTitled(ExploreFixture.DESTINATION_MUSIC, "New & Trending Songs")

        assertThat(shelf.items).isNotEmpty()
        assertThat(shelf.items.all { it is FeedItem.PlaylistItem }).isTrue()
    }

    @Test
    fun `a learning shelf whose see-all is a playlist exposes it as one`() {
        val withPlaylist = page(ExploreFixture.DESTINATION_LEARNING).shelves.firstOrNull { it.morePlaylistId != null }

        assertThat(withPlaylist).isNotNull()
        assertThat(withPlaylist!!.morePlaylistId).startsWith("OL")
    }

    /**
     * Sports heads its page with a `carouselHeaderRenderer` and publishes no page title at all, so
     * the screen labels a tab from its own string rather than from the response.
     */
    @Test
    fun `the sports destination reads the same way as live, without a page title`() {
        val page = page(ExploreFixture.DESTINATION_SPORTS)

        assertThat(page.title).isNull()
        assertThat(page.shelves.map { it.title }).contains("Highlights")
        assertThat(page.shelves.all { it.moreParams != null }).isTrue()
    }

    @Test
    fun `an unavailable destination yields no shelves instead of throwing`() {
        val page = page(ExploreFixture.DESTINATION_MOVIES_UNAVAILABLE)

        assertThat(page.shelves).isEmpty()
        assertThat(page.title).isEqualTo("Movies")
    }

    /**
     * The shelf id is position-qualified, and the position counts shelves that parsed rather than
     * entries walked — streaming them one at a time has to keep that counter, or two shelves
     * sharing a title would collide and crash the lazy list.
     */
    @Test
    fun `streamed shelves match the settled page, in order and with unique ids`() {
        val fixture = ExploreFixture(ExploreFixture.DESTINATION_LIVE)
        val streamed = fixture.exploreShelves().toList()
        val settled = fixture.toSettledExploreDestinationPage().shelves

        assertThat(streamed.map { it.id }).isEqualTo(settled.map { it.id })
        assertThat(streamed.map { it.id }.toSet()).hasSize(streamed.size)
    }
}
