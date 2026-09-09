package io.github.aedev.flow.ui.screens.player.state

import android.content.res.Configuration
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerLayoutModeTest {
    private fun configuration(
        smallestWidthDp: Int,
        orientation: Int,
    ) = Configuration().apply {
        smallestScreenWidthDp = smallestWidthDp
        this.orientation = orientation
    }

    private val phonePortrait = configuration(411, Configuration.ORIENTATION_PORTRAIT)
    private val phoneLandscape = configuration(411, Configuration.ORIENTATION_LANDSCAPE)
    private val tabletPortrait = configuration(800, Configuration.ORIENTATION_PORTRAIT)
    private val tabletLandscape = configuration(800, Configuration.ORIENTATION_LANDSCAPE)

    @Test
    fun `phones always use the compact layout`() {
        assertThat(playerLayoutModeFor(phonePortrait, isFullscreen = false, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(playerLayoutModeFor(phoneLandscape, isFullscreen = false, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.COMPACT)
    }

    @Test
    fun `tablet in landscape uses the wide split layout`() {
        assertThat(playerLayoutModeFor(tabletLandscape, isFullscreen = false, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.WIDE)
    }

    @Test
    fun `tablet upright uses the portrait grid layout`() {
        assertThat(playerLayoutModeFor(tabletPortrait, isFullscreen = false, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.TABLET_PORTRAIT)
    }

    @Test
    fun `fullscreen and pip collapse every device to compact`() {
        assertThat(playerLayoutModeFor(tabletLandscape, isFullscreen = true, isInPipMode = false))
            .isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(playerLayoutModeFor(tabletLandscape, isFullscreen = false, isInPipMode = true))
            .isEqualTo(PlayerLayoutMode.COMPACT)
    }

    @Test
    fun `the breakpoint is inclusive at 600dp`() {
        assertThat(playerLayoutModeFor(configuration(599, Configuration.ORIENTATION_LANDSCAPE), false, false))
            .isEqualTo(PlayerLayoutMode.COMPACT)
        assertThat(playerLayoutModeFor(configuration(600, Configuration.ORIENTATION_LANDSCAPE), false, false))
            .isEqualTo(PlayerLayoutMode.WIDE)
    }

    private fun modeFor(
        smallestWidthDp: Int,
        orientation: Int,
    ) = playerLayoutModeFor(configuration(smallestWidthDp, orientation), isFullscreen = false, isInPipMode = false)

    @Test
    fun `below 600dp every orientation is compact`() {
        listOf(411, 599).forEach { smallestWidthDp ->
            assertThat(modeFor(smallestWidthDp, Configuration.ORIENTATION_PORTRAIT)).isEqualTo(PlayerLayoutMode.COMPACT)
            assertThat(modeFor(smallestWidthDp, Configuration.ORIENTATION_LANDSCAPE)).isEqualTo(PlayerLayoutMode.COMPACT)
        }
    }

    @Test
    fun `from 600dp the orientation alone picks between the two tablet layouts`() {
        listOf(600, 839, 840, 1200).forEach { smallestWidthDp ->
            assertThat(modeFor(smallestWidthDp, Configuration.ORIENTATION_PORTRAIT)).isEqualTo(PlayerLayoutMode.TABLET_PORTRAIT)
            assertThat(modeFor(smallestWidthDp, Configuration.ORIENTATION_LANDSCAPE)).isEqualTo(PlayerLayoutMode.WIDE)
        }
    }

    @Test
    fun `the expanded window breakpoint at 840dp adds no further layout`() {
        assertThat(modeFor(839, Configuration.ORIENTATION_LANDSCAPE)).isEqualTo(modeFor(840, Configuration.ORIENTATION_LANDSCAPE))
        assertThat(modeFor(839, Configuration.ORIENTATION_PORTRAIT)).isEqualTo(modeFor(840, Configuration.ORIENTATION_PORTRAIT))
    }

    @Test
    fun `anything but landscape counts as portrait on a tablet`() {
        // Pins current behaviour: only ORIENTATION_LANDSCAPE selects WIDE, so an undefined
        // orientation takes the portrait grid.
        assertThat(modeFor(800, Configuration.ORIENTATION_UNDEFINED)).isEqualTo(PlayerLayoutMode.TABLET_PORTRAIT)
    }
}
