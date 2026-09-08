package io.github.aedev.flow.ui.components.videoplayer.motion

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MiniPlayerTapDeciderTest {
    @Test
    fun `a lone tap is a pending single tap`() {
        val decider = MiniPlayerTapDecider()
        assertThat(decider.onTap(uptimeMillis = 1_000L, doubleTapTimeoutMillis = 300L)).isEqualTo(MiniPlayerTap.SINGLE_PENDING)
    }

    @Test
    fun `a second tap inside the timeout is a double tap and resets the window`() {
        val decider = MiniPlayerTapDecider()
        decider.onTap(uptimeMillis = 1_000L, doubleTapTimeoutMillis = 300L)
        assertThat(decider.onTap(uptimeMillis = 1_200L, doubleTapTimeoutMillis = 300L)).isEqualTo(MiniPlayerTap.DOUBLE)
        assertThat(decider.onTap(uptimeMillis = 1_300L, doubleTapTimeoutMillis = 300L)).isEqualTo(MiniPlayerTap.SINGLE_PENDING)
    }

    @Test
    fun `a second tap after the timeout starts a new single tap`() {
        val decider = MiniPlayerTapDecider()
        decider.onTap(uptimeMillis = 1_000L, doubleTapTimeoutMillis = 300L)
        assertThat(decider.onTap(uptimeMillis = 1_400L, doubleTapTimeoutMillis = 300L)).isEqualTo(MiniPlayerTap.SINGLE_PENDING)
        assertThat(decider.onTap(uptimeMillis = 1_500L, doubleTapTimeoutMillis = 300L)).isEqualTo(MiniPlayerTap.DOUBLE)
    }
}
