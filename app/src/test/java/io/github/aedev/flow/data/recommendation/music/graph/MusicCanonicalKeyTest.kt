package io.github.aedev.flow.data.recommendation.music.graph

import io.github.aedev.flow.data.music.model.MusicArtist
import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MusicCanonicalKeyTest {
    @Test
    fun `remaster live and parenthetical variants collapse onto the studio key`() {
        val studio = canonicalTrackKey("UCqueen", "Bohemian Rhapsody", 355)
        assertEquals(studio, canonicalTrackKey("UCqueen", "Bohemian Rhapsody (Remastered 2011)", 357))
        assertEquals(studio, canonicalTrackKey("UCqueen", "Bohemian Rhapsody [Live]", 351))
        assertEquals(studio, canonicalTrackKey("UCqueen", "Bohemian Rhapsody - 2011 Remaster", 354))
    }

    @Test
    fun `different artists or clearly different lengths keep separate keys`() {
        val studio = canonicalTrackKey("UCqueen", "Bohemian Rhapsody", 355)
        assertNotEquals(studio, canonicalTrackKey("UCmuppets", "Bohemian Rhapsody", 355))
        assertNotEquals(studio, canonicalTrackKey("UCqueen", "Bohemian Rhapsody", 420))
    }

    @Test
    fun `track key prefers the artist browse id and falls back to the lowercased name`() {
        val withId =
            MusicTrack(
                videoId = "a",
                title = "Song",
                artist = "Queen",
                thumbnailUrl = "",
                duration = 200,
                artists = listOf(MusicArtist("Queen", "UCqueen")),
            )
        val withoutId = withId.copy(artists = emptyList(), channelId = "")
        assertEquals("ucqueen|song|20", withId.canonicalKey())
        assertEquals("queen|song|20", withoutId.canonicalKey())
    }
}
