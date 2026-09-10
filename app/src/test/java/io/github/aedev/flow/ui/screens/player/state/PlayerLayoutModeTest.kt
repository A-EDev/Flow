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
    ) = playerLayoutModeFor(window(widthDp, heightDp), isFullscreen = false, isInPipMode = false)

    private val phonePortrait = window(411, 891)
    private val phoneLandscape = window(891, 411)
    private val mediumPortrait = window(600, 960)
    private val expandedPortrait = window(840, 1200)
    private val expandedLandscape = window(1280, 800)

    @Test
    fun `a compact window uses the compact layout in either orientation`() {
        assertThat(playerLayoutModeFor(phonePortrait, isFullscreen = false, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(playerLayoutModeFor(phoneLandscape, isFullscreen = false, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.COMPACT)
    }

    @Test
    fun `an expanded window uses the wide split layout in either orientation`() {
        assertThat(playerLayoutModeFor(expandedLandscape, isFullscreen = false, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.WIDE)
        assertThat(playerLayoutModeFor(expandedPortrait, isFullscreen = false, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.WIDE)
    }

    @Test
    fun `a medium window uses the grid layout in either orientation`() {
        assertThat(playerLayoutModeFor(mediumPortrait, isFullscreen = false, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(modeFor(800, 600)).isEqualTo(PlayerLayoutMode.MEDIUM)
    }

    @Test
    fun `fullscreen and pip collapse every window to compact`() {
        assertThat(playerLayoutModeFor(expandedLandscape, isFullscreen = true, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(playerLayoutModeFor(expandedLandscape, isFullscreen = false, isInPipMode = true))
            .isEqualTo(PlayerLayoutMode.COMPACT)
    }

    @Test
    fun `the width breakpoints are inclusive at 600 and 840dp`() {
        assertThat(modeFor(599, 960)).isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(modeFor(600, 960)).isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(modeFor(839, 960)).isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(modeFor(840, 960)).isEqualTo(PlayerLayoutMode.WIDE)
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
        assertThat(playerWindowLayoutModeFor(expandedLandscape)).isEqualTo(PlayerLayoutMode.WIDE)
        assertThat(playerWindowLayoutModeFor(mediumPortrait)).isEqualTo(PlayerLayoutMode.MEDIUM)
        assertThat(playerWindowLayoutModeFor(phoneLandscape)).isEqualTo(PlayerLayoutMode.COMPACT)
    }
}
