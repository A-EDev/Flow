package io.github.aedev.flow.ui.tv.player.state

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TvScrubControllerTest {

    private val controller = TvScrubController()

    @Test
    fun `step ladder accelerates with repeat count`() {
        assertThat(TvScrubController.stepSizeFor(0)).isEqualTo(10_000L)
        assertThat(TvScrubController.stepSizeFor(2)).isEqualTo(10_000L)
        assertThat(TvScrubController.stepSizeFor(3)).isEqualTo(30_000L)
        assertThat(TvScrubController.stepSizeFor(7)).isEqualTo(30_000L)
        assertThat(TvScrubController.stepSizeFor(8)).isEqualTo(60_000L)
        assertThat(TvScrubController.stepSizeFor(15)).isEqualTo(60_000L)
        assertThat(TvScrubController.stepSizeFor(16)).isEqualTo(120_000L)
        assertThat(TvScrubController.stepSizeFor(100)).isEqualTo(120_000L)
    }

    @Test
    fun `first step starts from the playhead`() {
        val state = controller.beginOrStep(
            direction = 1,
            repeatCount = 0,
            currentPositionMs = 60_000L,
            durationMs = 600_000L,
        )
        assertThat(state.isScrubbing).isTrue()
        assertThat(state.targetMs).isEqualTo(70_000L)
    }

    @Test
    fun `held scrub advances from the previous target not the playhead`() {
        controller.beginOrStep(direction = 1, repeatCount = 0, currentPositionMs = 0L, durationMs = 600_000L)
        val state = controller.beginOrStep(direction = 1, repeatCount = 1, currentPositionMs = 0L, durationMs = 600_000L)
        assertThat(state.targetMs).isEqualTo(20_000L)
    }

    @Test
    fun `target clamps to the stream bounds`() {
        val end = controller.beginOrStep(direction = 1, repeatCount = 16, currentPositionMs = 590_000L, durationMs = 600_000L)
        assertThat(end.targetMs).isEqualTo(600_000L)

        controller.cancel()
        val start = controller.beginOrStep(direction = -1, repeatCount = 0, currentPositionMs = 5_000L, durationMs = 600_000L)
        assertThat(start.targetMs).isEqualTo(0L)
    }

    @Test
    fun `commit returns the target once and resets`() {
        controller.beginOrStep(direction = 1, repeatCount = 0, currentPositionMs = 30_000L, durationMs = 600_000L)
        assertThat(controller.commit()).isEqualTo(40_000L)
        assertThat(controller.current.isScrubbing).isFalse()
        assertThat(controller.commit()).isNull()
    }

    @Test
    fun `cancel discards the pending target`() {
        controller.beginOrStep(direction = 1, repeatCount = 0, currentPositionMs = 30_000L, durationMs = 600_000L)
        controller.cancel()
        assertThat(controller.commit()).isNull()
    }

    @Test
    fun `zero duration keeps the target at zero`() {
        val state = controller.beginOrStep(direction = 1, repeatCount = 0, currentPositionMs = 0L, durationMs = 0L)
        assertThat(state.targetMs).isEqualTo(0L)
    }
}
