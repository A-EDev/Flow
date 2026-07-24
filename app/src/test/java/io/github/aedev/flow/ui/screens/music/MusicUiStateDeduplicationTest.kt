package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.recommendation.MusicSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MusicUiStateDeduplicationTest {

    @Test
    fun `music state normalizes keyed tracks collections and nested sections`() {
        val state = MusicUiState(
            trendingSongs = listOf(track("song"), track("song"), track("")),
            featuredPlaylists = listOf(playlist("playlist"), playlist("playlist")),
            dynamicSections = listOf(
                MusicSection(
                    title = "Section",
                    tracks = listOf(track("nested"), track("nested"))
                )
            )
        )

        val result = state.withUniqueLazyContent()

        assertEquals(listOf("song"), result.trendingSongs.map(MusicTrack::videoId))
        assertEquals(listOf("playlist"), result.featuredPlaylists.map(MusicPlaylist::id))
        assertEquals(
            listOf("nested"),
            result.dynamicSections.single().tracks.map(MusicTrack::videoId)
        )
    }

    @Test
    fun `playlist track repetitions remain intact because their keys include occurrence`() {
        val repeatedTracks = listOf(track("song"), track("song"))
        val state = MusicUiState(
            playlistDetails = PlaylistDetails(
                id = "playlist",
                title = "Playlist",
                thumbnailUrl = "thumbnail",
                author = "Author",
                trackCount = repeatedTracks.size,
                tracks = repeatedTracks
            )
        )

        val result = state.withUniqueLazyContent()

        assertEquals(2, result.playlistDetails?.tracks?.size)
    }

    @Test
    fun `already valid music state keeps its identity`() {
        val state = MusicUiState(trendingSongs = listOf(track("song")))

        assertSame(state, state.withUniqueLazyContent())
    }

    private fun track(id: String) = MusicTrack(
        videoId = id,
        title = id,
        artist = "Artist",
        thumbnailUrl = "thumbnail",
        duration = 60
    )

    private fun playlist(id: String) = MusicPlaylist(
        id = id,
        title = id,
        thumbnailUrl = "thumbnail"
    )
}
