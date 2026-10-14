package io.github.aedev.flow.ui.screens.playlists

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.data.playlist.PlaylistListOrder
import io.github.aedev.flow.ui.components.library.PlaylistOwnershipFilter
import org.junit.Test

class PlaylistsOrderingTest {
    private fun playlist(
        id: String,
        createdAt: Long,
        position: Int = 0,
    ) = PlaylistInfo(id, id, "", 0, "", true, createdAt, position)

    private val owned = listOf(playlist("own-old", createdAt = 1, position = 2), playlist("own-new", createdAt = 4, position = 3))
    private val saved = listOf(playlist("saved-old", createdAt = 2, position = 4), playlist("saved-new", createdAt = 3, position = 1))

    @Test
    fun `owned playlists lead saved ones in a regular order`() {
        assertThat(orderedPlaylists(owned, saved, PlaylistOwnershipFilter.All, PlaylistListOrder.NEWEST).map { it.id })
            .containsExactly("own-new", "own-old", "saved-new", "saved-old")
            .inOrder()
    }

    @Test
    fun `custom order mixes owned and saved as arranged`() {
        assertThat(orderedPlaylists(owned, saved, PlaylistOwnershipFilter.All, PlaylistListOrder.CUSTOM).map { it.id })
            .containsExactly("saved-new", "own-old", "own-new", "saved-old")
            .inOrder()
    }

    @Test
    fun `the ownership filter still applies`() {
        assertThat(orderedPlaylists(owned, saved, PlaylistOwnershipFilter.Saved, PlaylistListOrder.OLDEST).map { it.id })
            .containsExactly("saved-old", "saved-new")
            .inOrder()
    }
}
