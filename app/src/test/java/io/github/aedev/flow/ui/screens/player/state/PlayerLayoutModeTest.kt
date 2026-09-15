package io.github.aedev.flow.ui.screens.player.state

import androidx.window.core.layout.WindowSizeClass
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerLayoutModeTest {
    private fun window(
        widthDp: Int,
        heightDp: Int,
    ) = WindowSizeClass.compute(widthDp.toFloat(), heightDp.toFloat())

    private fun modeFor(
        widthDp: Int,
        heightDp: Int,
    ) = playerLayoutModeFor(
        windowSizeClass = window(widthDp, heightDp),
        isLandscapeWindow = widthDp > heightDp,
        isFullscreen = false,
        isInPipMode = false,
    )

    @Test
    fun `a compact window uses the compact layout in either orientation`() {
        assertThat(modeFor(411, 891)).isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(modeFor(891, 411)).isEqualTo(PlayerLayoutMode.COMPACT)
    }

    @Test
    fun `an expanded window earns the side pane only when it is wider than it is tall`() {
        assertThat(modeFor(1280, 800)).isEqualTo(PlayerLayoutMode.WIDE)
        assertThat(modeFor(840, 1200)).isEqualTo(PlayerLayoutMode.MEDIUM)
    }

    @Test
    fun `a tablet held upright uses the grid and the same tablet turned over uses the pane`() {
        // A Galaxy Tab S10 Lite, the device the split layout was reported broken on.
        assertThat(modeFor(900, 1440)).isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(modeFor(1440, 900)).isEqualTo(PlayerLayoutMode.WIDE)
    }

    @Test
    fun `a three by two tablet is not split upright either`() {
        // A Xiaomi Pad 7 is 2136x3200, which lands around 1068x1600dp — expanded width upright, and
        // the two-pane layout it used to get is what left a narrow column of empty space (#967).
        assertThat(modeFor(1068, 1600)).isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(modeFor(1600, 1068)).isEqualTo(PlayerLayoutMode.WIDE)
    }

    @Test
    fun `a near square window keeps the full width rather than splitting it`() {
        assertThat(modeFor(1000, 1010)).isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(modeFor(1010, 1000)).isEqualTo(PlayerLayoutMode.WIDE)
    }

    @Test
    fun `a medium window uses the grid layout in either orientation`() {
        assertThat(modeFor(600, 960)).isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(modeFor(800, 600)).isEqualTo(PlayerLayoutMode.MEDIUM)
    }

    @Test
    fun `fullscreen and pip collapse every window to compact`() {
        val expandedLandscape = window(1280, 800)
        assertThat(
            playerLayoutModeFor(expandedLandscape, isLandscapeWindow = true, isFullscreen = true, isInPipMode = false),
        ).isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(
            playerLayoutModeFor(expandedLandscape, isLandscapeWindow = true, isFullscreen = false, isInPipMode = true),
        ).isEqualTo(PlayerLayoutMode.COMPACT)
    }

    @Test
    fun `the width breakpoints are inclusive at 600 and 840dp`() {
        assertThat(modeFor(599, 960)).isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(modeFor(600, 960)).isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(modeFor(839, 600)).isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(modeFor(840, 600)).isEqualTo(PlayerLayoutMode.WIDE)
    }

    @Test
    fun `a window shorter than the medium height breakpoint stays compact however wide it is`() {
        assertThat(modeFor(700, 400)).isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(modeFor(840, 470)).isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(modeFor(1280, 479)).isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(modeFor(840, 480)).isEqualTo(PlayerLayoutMode.WIDE)
    }

    @Test
    fun `a tall narrow window is compact whatever its height`() {
        assertThat(modeFor(480, 1200)).isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(modeFor(599, 2000)).isEqualTo(PlayerLayoutMode.COMPACT)
    }

    @Test
    fun `the window mode ignores fullscreen and pip`() {
        assertThat(playerWindowLayoutModeFor(window(1280, 800), isLandscapeWindow = true))
            .isEqualTo(PlayerLayoutMode.WIDE)
        assertThat(playerWindowLayoutModeFor(window(840, 1200), isLandscapeWindow = false))
            .isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(playerWindowLayoutModeFor(window(891, 411), isLandscapeWindow = true))
            .isEqualTo(PlayerLayoutMode.COMPACT)
    }
}
