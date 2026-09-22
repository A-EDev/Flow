package io.github.aedev.flow.innertube.pages.explore

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.channel.toChannelTabContent
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.innertube.pages.renderer.FeedItemOwner
import org.junit.Test

/**
 * A shelf's "see all" and the Gaming trending tab are flat grids of renderers the channel tabs
 * already parse, so they ride that path rather than a second one. These pin that.
 */
class ExploreShelfGridTest {
    private fun tab(name: String) = ExploreFixture(name).toChannelTabContent(ChannelTabKind.Videos, FeedItemOwner())

    @Test
    fun `a see-all grid parses as a flat page of videos`() {
        val page = tab(ExploreFixture.SHELF_SEE_ALL)

        assertThat(page.items).isNotEmpty()
        assertThat(page.items.all { it is FeedItem.VideoItem }).isTrue()
        assertThat(page.sections).isEmpty()
    }

    @Test
    fun `a see-all grid hands back its continuation token`() {
        assertThat(tab(ExploreFixture.SHELF_SEE_ALL).continuation).isNotEmpty()
    }

    @Test
    fun `the continuation response appends more of the same items`() {
        val page = tab(ExploreFixture.SHELF_SEE_ALL_CONTINUATION)

        assertThat(page.items).isNotEmpty()
        assertThat(page.items.all { it is FeedItem.VideoItem }).isTrue()
    }

    @Test
    fun `the gaming trending tab parses its grid video renderers`() {
        val page = tab(ExploreFixture.GAMING_TRENDING)

        assertThat(page.items).isNotEmpty()
        assertThat(page.items.all { it is FeedItem.VideoItem }).isTrue()
        assertThat(page.continuation).isNull()
    }
}
