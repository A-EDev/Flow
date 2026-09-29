package io.github.aedev.flow.ui.screens.player.state

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import com.google.common.truth.Truth.assertThat
import org.junit.Test

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class PlayerDetailPanesTest {
    private fun reserveFor(
        widthDp: Float,
        heightDp: Float,
    ) = calculatePaneScaffoldDirective(WindowAdaptiveInfo(WindowSizeClass.compute(widthDp, heightDp), Posture()))
        .supportingPaneReserve()

    @Test
    fun `a single-pane directive reserves nothing`() {
        assertThat(reserveFor(411f, 891f)).isEqualTo(0.dp)
        assertThat(reserveFor(800f, 1280f)).isEqualTo(0.dp)
    }

    /** The default breakpoints stop at 840 dp, so every expanded window gets the same two-pane directive. */
    @Test
    fun `an expanded window reserves the supporting pane and its spacer`() {
        assertThat(reserveFor(840f, 1200f)).isEqualTo(384.dp)
        assertThat(reserveFor(1000f, 800f)).isEqualTo(384.dp)
        assertThat(reserveFor(1280f, 800f)).isEqualTo(384.dp)
    }
}
