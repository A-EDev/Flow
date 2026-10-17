package io.github.aedev.flow.service.auto

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AutoBrowseTreeTest {
    @Test
    fun `a song id names the list it was picked from`() {
        val id = AutoBrowseTree.song(AutoBrowseTree.LIKED, "dQw4w9WgXcQ")

        assertThat(AutoBrowseTree.songOf(id)).isEqualTo(AutoBrowseTree.LIKED to "dQw4w9WgXcQ")
    }

    @Test
    fun `a playlist or search list survives inside a song id`() {
        val playlist = AutoBrowseTree.playlist("PL:abc")
        val search = AutoBrowseTree.search(" daft punk ")

        assertThat(AutoBrowseTree.songOf(AutoBrowseTree.song(playlist, "v1"))?.first?.let(AutoBrowseTree::playlistIdOf)).isEqualTo("PL:abc")
        assertThat(
            AutoBrowseTree.songOf(AutoBrowseTree.song(search, "v2"))?.first?.let(AutoBrowseTree::searchQueryOf),
        ).isEqualTo("daft punk")
    }

    @Test
    fun `a folder or bare video id is not a song in a list`() {
        assertThat(AutoBrowseTree.songOf(AutoBrowseTree.FOR_YOU)).isNull()
        assertThat(AutoBrowseTree.songOf("dQw4w9WgXcQ")).isNull()
        assertThat(AutoBrowseTree.songOf("auto:liked|")).isNull()
    }

    @Test
    fun `the root shows four tabs, or fewer when the car asks for fewer`() {
        assertThat(AutoBrowseTree.rootTabs(4))
            .containsExactly(AutoBrowseTree.FOR_YOU, AutoBrowseTree.LIBRARY, AutoBrowseTree.PLAYLISTS, AutoBrowseTree.QUEUE)
            .inOrder()
        assertThat(AutoBrowseTree.rootTabs(2)).containsExactly(AutoBrowseTree.FOR_YOU, AutoBrowseTree.LIBRARY).inOrder()
        assertThat(AutoBrowseTree.rootTabs(0)).hasSize(4)
    }

    @Test
    fun `the artwork provider serves YouTube image hosts only`() {
        assertThat(AutoArtworkProvider.isAllowedArtworkUrl("https://i.ytimg.com/vi/x/hqdefault.jpg")).isTrue()
        assertThat(AutoArtworkProvider.isAllowedArtworkUrl("https://lh3.googleusercontent.com/abc=w544-h544")).isTrue()
        assertThat(AutoArtworkProvider.isAllowedArtworkUrl("https://yt3.ggpht.com/abc=s88")).isTrue()
        assertThat(AutoArtworkProvider.isAllowedArtworkUrl("http://i.ytimg.com/vi/x/hqdefault.jpg")).isFalse()
        assertThat(AutoArtworkProvider.isAllowedArtworkUrl("https://example.com/i.ytimg.com.jpg")).isFalse()
        assertThat(AutoArtworkProvider.isAllowedArtworkUrl("https://ytimg.com.evil.example/x.jpg")).isFalse()
        assertThat(AutoArtworkProvider.isAllowedArtworkUrl("not a url")).isFalse()
    }
}
