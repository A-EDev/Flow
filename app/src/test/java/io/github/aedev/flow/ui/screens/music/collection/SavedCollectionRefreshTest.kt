package io.github.aedev.flow.ui.screens.music.collection

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import org.junit.Test

class SavedCollectionRefreshTest {
    private val now = 1_000_000_000L
    private val longAgo = now - SavedCopyTtl.toMillis() - 1
    private val recently = now - SavedCopyTtl.toMillis() + 1

    private fun track(id: String) = MusicTrack(videoId = id, title = id, artist = "Artist", thumbnailUrl = "t", duration = 200)

    private fun remote(
        ids: List<String>,
        continuation: String? = null,
        total: Int? = null,
    ) = PlaylistDetails(
        id = "PL1",
        title = "Remote",
        thumbnailUrl = "",
        author = "Artist",
        trackCount = ids.size,
        tracks = ids.map(::track),
        continuation = continuation,
        totalTrackCount = total,
    )

    @Test
    fun `an unchanged first page loads no further pages`() {
        assertThat(needsFullRefresh(listOf("a", "b", "c"), remote(listOf("a", "b"), "next"), null, now)).isFalse()
    }

    @Test
    fun `a changed first page loads every page`() {
        assertThat(needsFullRefresh(listOf("a", "b", "c"), remote(listOf("z", "a"), "next"), longAgo, now)).isTrue()
    }

    @Test
    fun `a changed song count loads every page even when the first page matches`() {
        assertThat(needsFullRefresh(listOf("a", "b", "c"), remote(listOf("a", "b"), "next", total = 4), null, now)).isTrue()
        assertThat(needsFullRefresh(listOf("a", "b", "c"), remote(listOf("a", "b"), "next", total = 3), null, now)).isFalse()
    }

    @Test
    fun `a copy checked within the ttl is not loaded in full again`() {
        assertThat(needsFullRefresh(listOf("a"), remote(listOf("z"), "next"), recently, now)).isFalse()
    }

    @Test
    fun `a single page playlist never needs further pages`() {
        assertThat(needsFullRefresh(listOf("a"), remote(listOf("z")), null, now)).isFalse()
    }

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
