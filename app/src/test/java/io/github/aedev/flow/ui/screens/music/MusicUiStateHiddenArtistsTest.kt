package io.github.aedev.flow.ui.screens.music

import io.github.aedev.flow.data.recommendation.MusicSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicUiStateHiddenArtistsTest {
    @Test
    fun `hidden artist is removed from every shelf and section`() {
        val state =
            MusicUiState(
                forYouTracks = listOf(track("a", artist = "Bad Artist"), track("b", artist = "Good Artist")),
                trendingSongs = listOf(track("c", artist = "Bad Artist")),
                listenAgain = listOf(track("d", artist = "Good Artist")),
                onRepeatTracks = listOf(track("e", artist = "Bad Artist")),
                genreTracks = mapOf("Pop" to listOf(track("f", artist = "Bad Artist"), track("g", artist = "Good Artist"))),
                dynamicSections = listOf(MusicSection(title = "Section", tracks = listOf(track("h", artist = "Bad Artist")))),
                similarToSections =
                    listOf(
                        MusicSection(
                            title = "Similar",
                            tracks = listOf(track("i", artist = "Bad Artist"), track("j", artist = "Good Artist")),
                        ),
                    ),
            )

        val result = state.withHiddenArtists(setOf("bad artist"))

        assertEquals(listOf("b"), result.forYouTracks.map(MusicTrack::videoId))
        assertTrue(result.trendingSongs.isEmpty())
        assertEquals(listOf("d"), result.listenAgain.map(MusicTrack::videoId))
        assertTrue(result.onRepeatTracks.isEmpty())
        assertEquals(listOf("g"), result.genreTracks.getValue("Pop").map(MusicTrack::videoId))
        assertTrue(result.dynamicSections.isEmpty())
        assertEquals(
            listOf("j"),
            result.similarToSections
                .single()
                .tracks
                .map(MusicTrack::videoId),
        )
    }

    @Test
    fun `id-keyed hidden artist matches tracks that carry the browse id`() {
        val hiddenById =
            track("a", artist = "Some Name").copy(artists = listOf(MusicArtist(name = "Some Name", id = "UCbad")))
        val state = MusicUiState(forYouTracks = listOf(hiddenById, track("b", artist = "Other")))

        val result = state.withHiddenArtists(setOf("UCbad"))

        assertEquals(listOf("b"), result.forYouTracks.map(MusicTrack::videoId))
    }

    @Test
    fun `name form catches the same artist on tracks without an id`() {
        val nameOnly = track("a", artist = "Some Name")
        val state = MusicUiState(forYouTracks = listOf(nameOnly))

        // Feedback given on an id-keyed track: the hidden set carries both forms.
        val result = state.withHiddenArtists(setOf("UCbad", "some name"))

        assertTrue(result.forYouTracks.isEmpty())
    }

    @Test
    fun `daily mixes below four tracks are dropped entirely`() {
        val mix =
            MusicSection(
                title = "Mix",
                tracks = listOf(track("a", artist = "Bad"), track("b"), track("c"), track("d")),
            )
        val state = MusicUiState(dailyMixSections = listOf(mix))

        val result = state.withHiddenArtists(setOf("bad"))

        assertTrue(result.dailyMixSections.isEmpty())
    }

    @Test
    fun `empty hidden set and untouched shelves keep state identity`() {
        val state = MusicUiState(forYouTracks = listOf(track("a")))

        assertSame(state, state.withHiddenArtists(emptySet()))
        assertSame(state, state.withHiddenArtists(setOf("someone else")))
    }

    private fun track(
        id: String,
        artist: String = "Artist",
    ) = MusicTrack(
        videoId = id,
        title = id,
        artist = artist,
        thumbnailUrl = "thumbnail",
        duration = 60,
    )
}
