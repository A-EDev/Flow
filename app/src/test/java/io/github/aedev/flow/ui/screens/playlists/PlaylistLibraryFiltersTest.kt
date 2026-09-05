package io.github.aedev.flow.ui.screens.playlists

import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.ui.components.library.PlaylistOwnershipFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistLibraryFiltersTest {
    private val owned = listOf(playlist("owned"), playlist("shared"))
    private val saved = listOf(playlist("saved"), playlist("shared"))

    @Test
    fun allCombinesOwnedAndSavedWithoutDuplicates() {
        val result = PlaylistOwnershipFilter.All.select(owned, saved)

        assertEquals(listOf("owned", "shared", "saved"), result.map(PlaylistInfo::id))
    }

    @Test
    fun ownedReturnsOnlyOwnedPlaylists() {
        assertEquals(owned, PlaylistOwnershipFilter.Owned.select(owned, saved))
    }

    @Test
    fun savedReturnsOnlySavedPlaylists() {
        assertEquals(saved, PlaylistOwnershipFilter.Saved.select(owned, saved))
    }

    private fun playlist(id: String) =
        PlaylistInfo(
            id = id,
            name = id,
            description = "",
            videoCount = 0,
            thumbnailUrl = "",
            isPrivate = true,
            createdAt = 0L,
        )
}
