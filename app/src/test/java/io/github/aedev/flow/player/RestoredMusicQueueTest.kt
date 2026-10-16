package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.QueuePersistence
import io.github.aedev.flow.data.music.model.MusicTrack
import org.junit.Test

class RestoredMusicQueueTest {
    private fun track(
        id: String,
        seconds: Int = 200,
    ) = MusicTrack(videoId = id, title = id, artist = "", thumbnailUrl = "", duration = seconds)

    private fun saved(
        queue: List<MusicTrack> = listOf(track("a"), track("b"), track("c")),
        index: Int = 1,
        positionMs: Long = 61_000L,
        trackId: String? = "b",
    ) = QueuePersistence.QueueState(
        queue = queue,
        currentIndex = index,
        currentPosition = positionMs,
        currentTrackId = trackId,
        shuffleEnabled = false,
        repeatMode = 0,
        savedAt = 0L,
    )

    @Test
    fun `the saved track resumes where it stopped`() {
        val restored = restoredMusicQueue(saved())!!

        assertThat(restored.track?.videoId).isEqualTo("b")
        assertThat(restored.index).isEqualTo(1)
        assertThat(restored.resumePositionMs).isEqualTo(61_000L)
    }

    @Test
    fun `the track id wins over a stale index`() {
        val restored = restoredMusicQueue(saved(index = 0, trackId = "c"))!!

        assertThat(restored.track?.videoId).isEqualTo("c")
    }

    @Test
    fun `a song stopped in its last seconds starts over`() {
        assertThat(restoredMusicQueue(saved(positionMs = 197_000L))!!.resumePositionMs).isEqualTo(0L)
        assertThat(restoredMusicQueue(saved(positionMs = 194_000L))!!.resumePositionMs).isEqualTo(194_000L)
    }

    @Test
    fun `a song without a known length still resumes`() {
        val restored = restoredMusicQueue(saved(queue = listOf(track("a", seconds = 0)), index = 0, trackId = "a"))!!

        assertThat(restored.resumePositionMs).isEqualTo(61_000L)
    }

    @Test
    fun `an index past the end is clamped and an empty queue restores nothing`() {
        assertThat(restoredMusicQueue(saved(index = 9, trackId = null))!!.index).isEqualTo(2)
        assertThat(restoredMusicQueue(saved(queue = emptyList()))).isNull()
    }
}
