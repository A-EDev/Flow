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
}
