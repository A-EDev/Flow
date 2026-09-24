package io.github.aedev.flow.ui.components.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlaylistSortingTest {
    @Test
    fun `a YouTube playlist offers no date added orders`() {
        val remote = PlaylistSortOrder.availableFor(isLocalPlaylist = false)

        assertThat(remote).doesNotContain(PlaylistSortOrder.DATE_ADDED_NEWEST)
        assertThat(remote).doesNotContain(PlaylistSortOrder.DATE_ADDED_OLDEST)
        assertThat(remote).contains(PlaylistSortOrder.MANUAL)
    }

    @Test
    fun `a playlist of your own offers every order`() {
        assertThat(PlaylistSortOrder.availableFor(isLocalPlaylist = true)).containsExactlyElementsIn(PlaylistSortOrder.entries)
    }
}
