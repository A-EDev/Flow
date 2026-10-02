package io.github.aedev.flow.player.musicvideo

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Test

class MusicVideoPlannerTest {
    private val song = MusicTrack("song", "Anti-Hero", "Taylor Swift", "", 201)
    private val video = song.copy(videoId = "video", duration = 310, isVideoSong = true)

    @Test
    fun `Video mode swaps a song for its video and shows the picture`() {
        val change = MusicVideoPlanner.replacement(song, showsVideo = false, song = null, video = video, wantsVideo = true)

        assertThat(change).isEqualTo(MusicVideoPlanner.Replacement(video, showsVideo = true))
    }

    @Test
    fun `Video mode leaves a song without a video, and an entry already showing one, alone`() {
        assertThat(MusicVideoPlanner.replacement(song, showsVideo = false, song = null, video = null, wantsVideo = true)).isNull()
        assertThat(MusicVideoPlanner.replacement(video, showsVideo = true, song = song, video = null, wantsVideo = true)).isNull()
    }

    @Test
    fun `Song mode puts the song back in place of its video`() {
        val change = MusicVideoPlanner.replacement(video, showsVideo = true, song = song, video = null, wantsVideo = false)

        assertThat(change).isEqualTo(MusicVideoPlanner.Replacement(song, showsVideo = false))
    }

    @Test
    fun `Song mode only hides the picture of a track that is a video itself`() {
        val musicVideo = song.copy(isVideoSong = true)

        assertThat(MusicVideoPlanner.replacement(musicVideo, showsVideo = true, song = null, video = null, wantsVideo = false))
            .isEqualTo(MusicVideoPlanner.Replacement(musicVideo, showsVideo = false))
        assertThat(MusicVideoPlanner.replacement(song, showsVideo = false, song = null, video = null, wantsVideo = false)).isNull()
    }

    @Test
    fun `the place in the song carries over, short of the end of a shorter version`() {
        assertThat(MusicVideoPlanner.positionWithin(90_000, durationSeconds = 310)).isEqualTo(90_000)
        assertThat(MusicVideoPlanner.positionWithin(300_000, durationSeconds = 201)).isEqualTo(199_000)
        assertThat(MusicVideoPlanner.positionWithin(300_000, durationSeconds = 0)).isEqualTo(300_000)
        assertThat(MusicVideoPlanner.positionWithin(-5, durationSeconds = 201)).isEqualTo(0)
    }
}
