package io.github.aedev.flow.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FrameStepPolicyTest {
    @Test
    fun `a frame lasts the reciprocal of the frame rate`() {
        assertThat(FrameStepPolicy.frameDurationMs(30f)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(60f)).isEqualTo(16L)
        assertThat(FrameStepPolicy.frameDurationMs(24f)).isEqualTo(41L)
    }

    @Test
    fun `an unusable frame rate falls back rather than dividing by nothing`() {
        assertThat(FrameStepPolicy.frameDurationMs(null)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(0f)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(-30f)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(Float.NaN)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(Float.POSITIVE_INFINITY)).isEqualTo(33L)
        assertThat(FrameStepPolicy.frameDurationMs(10_000f)).isEqualTo(33L)
    }

    @Test
    fun `stepping forward advances one frame`() {
        val target = FrameStepPolicy.stepTarget(positionMs = 1_000L, durationMs = 10_000L, frameRate = 30f, forward = true)

        assertThat(target).isEqualTo(1_033L)
    }

    @Test
    fun `stepping back retreats one frame`() {
        val target = FrameStepPolicy.stepTarget(positionMs = 1_000L, durationMs = 10_000L, frameRate = 30f, forward = false)

        assertThat(target).isEqualTo(967L)
    }

    @Test
    fun `stepping back from the start stays put`() {
        val target = FrameStepPolicy.stepTarget(positionMs = 0L, durationMs = 10_000L, frameRate = 30f, forward = false)

        assertThat(target).isNull()
    }

    @Test
    fun `stepping forward from the end stays put`() {
        val target =
            FrameStepPolicy.stepTarget(positionMs = 10_000L, durationMs = 10_000L, frameRate = 30f, forward = true)

        assertThat(target).isNull()
    }

    @Test
    fun `a step near the end clamps to the duration`() {
        val target = FrameStepPolicy.stepTarget(positionMs = 9_990L, durationMs = 10_000L, frameRate = 30f, forward = true)

        assertThat(target).isEqualTo(10_000L)
    }

    @Test
    fun `a stream with no known duration cannot be stepped`() {
        assertThat(FrameStepPolicy.stepTarget(1_000L, durationMs = 0L, frameRate = 30f, forward = true)).isNull()
        assertThat(FrameStepPolicy.stepTarget(1_000L, durationMs = -1L, frameRate = 30f, forward = true)).isNull()
    }

    @Test
    fun `a high frame rate still moves the playhead`() {
        val target = FrameStepPolicy.stepTarget(positionMs = 1_000L, durationMs = 10_000L, frameRate = 240f, forward = true)

        assertThat(target).isEqualTo(1_004L)
    }
}
