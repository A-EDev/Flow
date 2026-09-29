package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Attributing a failure to the wrong item is not a logging nicety: recovery re-prepares whatever it
 * blames, so blaming the playing track replays it and leaves the track that actually broke untried.
 */
class MusicPlaybackRecoveryPlannerTest {
    private val queue = listOf("a", "b", "c")

    @Test
    fun `a failure in the playing track keeps its position`() {
        val failed =
            MusicPlaybackRecoveryPlanner.resolveFailedItem(
                errorWindowIndex = 1,
                currentIndex = 1,
                currentPositionMs = 42_000L,
                mediaIds = queue,
            )

        assertThat(failed).isEqualTo(MusicPlaybackRecoveryPlanner.FailedItem(1, "b", 42_000L))
    }

    @Test
    fun `a failed preload is blamed on the next item, not the one playing`() {
        val failed =
            MusicPlaybackRecoveryPlanner.resolveFailedItem(
                errorWindowIndex = 2,
                currentIndex = 1,
                currentPositionMs = 42_000L,
                mediaIds = queue,
            )

        assertThat(failed?.mediaId).isEqualTo("c")
        assertThat(failed?.index).isEqualTo(2)
    }

    @Test
    fun `an item that never started resumes from zero`() {
        val failed =
            MusicPlaybackRecoveryPlanner.resolveFailedItem(
                errorWindowIndex = 2,
                currentIndex = 1,
                currentPositionMs = 42_000L,
                mediaIds = queue,
            )

        assertThat(failed?.resumePositionMs).isEqualTo(0L)
    }

    @Test
    fun `an unusable window index falls back to the current item`() {
        val failed =
            MusicPlaybackRecoveryPlanner.resolveFailedItem(
                errorWindowIndex = -1,
                currentIndex = 0,
                currentPositionMs = 5_000L,
                mediaIds = queue,
            )

        assertThat(failed).isEqualTo(MusicPlaybackRecoveryPlanner.FailedItem(0, "a", 5_000L))
    }

    @Test
    fun `a window index past the playlist falls back to the current item`() {
        val failed =
            MusicPlaybackRecoveryPlanner.resolveFailedItem(
                errorWindowIndex = 9,
                currentIndex = 2,
                currentPositionMs = 1_000L,
                mediaIds = queue,
            )

        assertThat(failed?.mediaId).isEqualTo("c")
    }

    @Test
    fun `an empty playlist resolves to nothing`() {
        val failed =
            MusicPlaybackRecoveryPlanner.resolveFailedItem(
                errorWindowIndex = 0,
                currentIndex = 0,
                currentPositionMs = 0L,
                mediaIds = emptyList(),
            )

        assertThat(failed).isNull()
    }

    @Test
    fun `a negative position is not carried into the retry`() {
        val failed =
            MusicPlaybackRecoveryPlanner.resolveFailedItem(
                errorWindowIndex = 0,
                currentIndex = 0,
                currentPositionMs = -1L,
                mediaIds = queue,
            )

        assertThat(failed?.resumePositionMs).isEqualTo(0L)
    }
}
