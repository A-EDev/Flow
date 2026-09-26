package io.github.aedev.flow.ui.screens.music.collection

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import org.junit.Test

class SavedCollectionRefreshTest {
    private fun track(id: String) = MusicTrack(videoId = id, title = id, artist = "Artist", thumbnailUrl = "t", duration = 200)

    private fun remote(
        ids: List<String>,
        continuation: String? = null,
    ) = PlaylistDetails(
        id = "PL1",
        title = "Remote",
        thumbnailUrl = "",
        author = "Artist",
        trackCount = ids.size,
        tracks = ids.map(::track),
        continuation = continuation,
    )

    @Test
    fun `a complete load that differs replaces the saved songs`() {
        val tracks = savedCopyRefresh(listOf("a"), remote(listOf("a", "b")))

        assertThat(tracks?.map { it.videoId }).containsExactly("a", "b").inOrder()
    }

    @Test
    fun `a load with pages still to come never replaces the saved songs`() {
        assertThat(savedCopyRefresh(listOf("a", "b", "c"), remote(listOf("a"), continuation = "next"))).isNull()
    }

    @Test
    fun `an empty load never wipes the saved songs`() {
        assertThat(savedCopyRefresh(listOf("a"), remote(emptyList()))).isNull()
    }

    @Test
    fun `an unchanged playlist is not written again`() {
        assertThat(savedCopyRefresh(listOf("a", "b"), remote(listOf("a", "b")))).isNull()
    }

    @Test
    fun `a reorder counts as a change`() {
        assertThat(savedCopyRefresh(listOf("b", "a"), remote(listOf("a", "b")))).isNotNull()
    }
}
