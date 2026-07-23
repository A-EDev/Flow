package io.github.aedev.flow.ui.tv.player.state

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TvPlayerOverlayControllerTest {

    private var now = 0L
    private val controller = TvPlayerOverlayController { now }

    @Test
    fun `starts hidden with no panel`() {
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.HIDDEN)
        assertThat(controller.state.value.activePanel).isNull()
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

        controller.openPanel(TvPlayerPanel.SETTINGS)
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
    fun `settings sub pages pop back to settings then transport`() {
        controller.showTransport()
        controller.openPanel(TvPlayerPanel.SETTINGS)
        controller.openPanel(TvPlayerPanel.QUALITY)

        assertThat(controller.onBack()).isTrue()
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.PANEL)
        assertThat(controller.state.value.activePanel).isEqualTo(TvPlayerPanel.SETTINGS)

        assertThat(controller.onBack()).isTrue()
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.TRANSPORT)
        assertThat(controller.state.value.activePanel).isNull()
    }

    @Test
    fun `root panels return straight to transport`() {
        controller.showTransport()
        controller.openPanel(TvPlayerPanel.QUEUE)

        assertThat(controller.onBack()).isTrue()
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.TRANSPORT)
    }

    @Test
    fun `back walks panel transport hidden then reports unhandled`() {
        controller.showTransport()
        controller.openPanel(TvPlayerPanel.DESCRIPTION)

        assertThat(controller.onBack()).isTrue()   // panel -> transport
        assertThat(controller.onBack()).isTrue()   // transport -> hidden
        assertThat(controller.state.value.mode).isEqualTo(TvOverlayMode.HIDDEN)
        assertThat(controller.onBack()).isFalse()  // hidden -> close the player
    }

    @Test
    fun `every settings sub page declares settings as parent`() {
        listOf(
            TvPlayerPanel.QUALITY,
            TvPlayerPanel.SPEED,
            TvPlayerPanel.AUDIO,
            TvPlayerPanel.SUBTITLES,
        ).forEach { panel ->
            assertThat(panel.parent).isEqualTo(TvPlayerPanel.SETTINGS)
        }
        listOf(
            TvPlayerPanel.SETTINGS,
            TvPlayerPanel.QUEUE,
            TvPlayerPanel.COMMENTS,
            TvPlayerPanel.LIVE_CHAT,
            TvPlayerPanel.DESCRIPTION,
            TvPlayerPanel.SLEEP_TIMER,
        ).forEach { panel ->
            assertThat(panel.parent).isNull()
        }
    }
}
