package io.github.aedev.flow.ui.screens.music.collection

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Test

class LikedMusicTracksTest {
    @Test
    fun `songs keep the like order and use the richer favorite when there is one`() {
        val liked =
            listOf(
                LikedVideoInfo("b", "Song B", "tb", "Artist B", likedAt = 2L, isMusic = true, durationSeconds = 180),
                LikedVideoInfo("a", "Song A", "ta", "Artist A", likedAt = 1L, isMusic = true),
            )
        val favorite =
            MusicTrack(videoId = "a", title = "Song A", artist = "Artist A", thumbnailUrl = "ta", duration = 200, album = "Album")

        val tracks = likedMusicTracks(liked, listOf(favorite))

        assertThat(tracks.map { it.videoId }).containsExactly("b", "a").inOrder()
        assertThat(tracks[0].duration).isEqualTo(180)
        assertThat(tracks[1].album).isEqualTo("Album")
    }
}
