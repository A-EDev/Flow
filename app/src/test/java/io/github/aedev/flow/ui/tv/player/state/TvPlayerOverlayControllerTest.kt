package io.github.aedev.flow.ui.tv.player.state

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TvPlayerOverlayControllerTest {
    private var now = 0L
    private val controller = TvPlayerOverlayController { now }

    @Test
    fun `starts hidden`() {
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.HIDDEN)
    }

    @Test
    fun `show transport stamps interaction time`() {
        now = 1_234L
        controller.showTransport()
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.TRANSPORT)
        assertThat(controller.state.value.lastInteractionAtMs).isEqualTo(1_234L)
    }

    @Test
    fun `auto hide deadline only exists while playing unscrubbed transport`() {
        now = 1_000L
        controller.showTransport()

        assertThat(controller.autoHideDeadline(isPlaying = true, isScrubbing = false))
            .isEqualTo(1_000L + TvPlayerOverlayController.AUTO_HIDE_DELAY_MS)
        assertThat(controller.autoHideDeadline(isPlaying = false, isScrubbing = false)).isNull()
        assertThat(controller.autoHideDeadline(isPlaying = true, isScrubbing = true)).isNull()

        controller.openPanel()
        assertThat(controller.autoHideDeadline(isPlaying = true, isScrubbing = false)).isNull()

        controller.hide()
        assertThat(controller.autoHideDeadline(isPlaying = true, isScrubbing = false)).isNull()
    }

    @Test
    fun `interaction refreshes the deadline`() {
        now = 1_000L
        controller.showTransport()
        now = 3_000L
        controller.onUserInteraction()
        assertThat(controller.autoHideDeadline(isPlaying = true, isScrubbing = false))
            .isEqualTo(3_000L + TvPlayerOverlayController.AUTO_HIDE_DELAY_MS)
    }

    @Test
    fun `closing a panel returns to the transport`() {
        controller.showTransport()
        controller.openPanel()

        controller.closePanel()
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.TRANSPORT)
    }

    @Test
    fun `closing without a panel open changes nothing`() {
        controller.hide()
        controller.closePanel()
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.HIDDEN)
    }

    @Test
    fun `back walks panel transport hidden then reports unhandled`() {
        controller.showTransport()
        controller.openPanel()

        assertThat(controller.onBack()).isTrue()
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.TRANSPORT)
        assertThat(controller.onBack()).isTrue()
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.HIDDEN)
        assertThat(controller.onBack()).isFalse()
    }
}
