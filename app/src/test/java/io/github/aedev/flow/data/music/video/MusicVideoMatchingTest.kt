package io.github.aedev.flow.data.music.video

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicArtist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.SongItem
import org.junit.Test

/**
 * Signed out, YouTube Music never links a song to its video, so the switch finds the video by
 * search. The cases mirror real video-search results probed on 2026-10-02.
 */
class MusicVideoMatchingTest {
    private val weeknd = "UClYV6hHlupm_S_ObS1W-DYw"
    private val blindingLights =
        MusicTrack(
            videoId = "J7p4bzqLvCw",
            title = "Blinding Lights",
            artist = "The Weeknd",
            thumbnailUrl = "",
            duration = 202,
            channelId = weeknd,
            artists = listOf(MusicArtist("The Weeknd", weeknd)),
        )

    private fun video(
        id: String,
        title: String,
        artist: String,
        artistId: String?,
        seconds: Int?,
        type: String = OMV,
    ) = SongItem(
        id = id,
        title = title,
        artists = listOf(Artist(artist, artistId)),
        duration = seconds,
        musicVideoType = type,
        thumbnail = "",
    )

    @Test
    fun `the artist's official video wins over lyric uploads ranked above it`() {
        val results =
            listOf(
                video("BwX8OS4cegU", "The Weeknd - Blinding Lights (Lyrics)", "7clouds Indie", "UCtDALJw7R", 201, UGC),
                video("4NRXx6U8ABQ", "Blinding Lights (Official Video)", "The Weeknd", weeknd, 263),
            )

        assertThat(MusicVideoMatching.pick(blindingLights, results)?.id).isEqualTo("4NRXx6U8ABQ")
    }

    @Test
    fun `a live performance is a different recording, not the song's video`() {
        val queen = "UCEPMVbUzImPl4p8k4LkGevA"
        val song =
            blindingLights.copy(
                title = "Bohemian Rhapsody",
                channelId = queen,
                artists = listOf(MusicArtist("Queen", queen)),
                duration = 355,
            )
        val results = listOf(video("fJ9rUzIMcZQ", "Bohemian Rhapsody (Live At Milton Keynes Bowl / June 1982)", "Queen", queen, 360))

        assertThat(MusicVideoMatching.pick(song, results)).isNull()
    }

    @Test
    fun `an official audio upload has no picture worth switching to`() {
        val results = listOf(video("fHI8X4OXluQ", "Blinding Lights (Official Audio)", "The Weeknd", weeknd, 202))

        assertThat(MusicVideoMatching.pick(blindingLights, results)).isNull()
    }

    @Test
    fun `a featured artist in brackets is still the same song`() {
        val dua = "UCzVb0SIXp9q9PeKCcFjsBtA"
        val song =
            blindingLights.copy(
                title = "Levitating",
                channelId = dua,
                artists = listOf(MusicArtist("Dua Lipa", dua)),
                duration = 204,
            )
        val results = listOf(video("TUVcZfQe-Kw", "Levitating (feat. DaBaby)", "Dua Lipa", dua, 231))

        assertThat(MusicVideoMatching.pick(song, results)?.id).isEqualTo("TUVcZfQe-Kw")
    }

    @Test
    fun `another channel's upload of the title is not the artist's video`() {
        val results = listOf(video("XwxLwG2_Sxk", "Blinding Lights", "7clouds", "UCNqFDjYTexJDET3rPDrmJKg", 200))

        assertThat(MusicVideoMatching.pick(blindingLights, results)).isNull()
    }

    @Test
    fun `the artist name matches when the result carries no channel id`() {
        val results = listOf(video("4NRXx6U8ABQ", "Blinding Lights (Official Video)", "THE WEEKND", null, 263))

        assertThat(MusicVideoMatching.pick(blindingLights, results)?.id).isEqualTo("4NRXx6U8ABQ")
    }

    @Test
    fun `a video far longer than the song is some other upload`() {
        val results = listOf(video("long", "Blinding Lights (Official Video)", "The Weeknd", weeknd, 202 * 2 + 61))

        assertThat(MusicVideoMatching.pick(blindingLights, results)).isNull()
    }

    private companion object {
        const val OMV = "MUSIC_VIDEO_TYPE_OMV"
        const val UGC = "MUSIC_VIDEO_TYPE_UGC"
    }
}
