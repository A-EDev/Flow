package io.github.aedev.flow.ui.screens.player.state

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FullscreenRotationTest {
    private fun awaiting(
        isFullscreen: Boolean,
        isFullscreenPortrait: Boolean = false,
        videoAspectRatio: Float = 16f / 9f,
        windowIsLandscape: Boolean,
        rotationHonoured: Boolean = true,
    ) = isAwaitingRotation(isFullscreen, isFullscreenPortrait, videoAspectRatio, windowIsLandscape, rotationHonoured)

    @Test
    fun `a portrait phone entering landscape fullscreen waits for the turn`() {
        assertThat(awaiting(isFullscreen = true, windowIsLandscape = false)).isTrue()
        assertThat(awaiting(isFullscreen = true, windowIsLandscape = true)).isFalse()
    }

    @Test
    fun `a landscape phone leaving fullscreen waits until it is upright`() {
        assertThat(awaiting(isFullscreen = false, windowIsLandscape = true)).isTrue()
        assertThat(awaiting(isFullscreen = false, windowIsLandscape = false)).isFalse()
    }

    @Test
    fun `portrait fullscreen and vertical videos never turn`() {
        assertThat(awaiting(isFullscreen = true, isFullscreenPortrait = true, windowIsLandscape = false)).isFalse()
        assertThat(awaiting(isFullscreen = true, videoAspectRatio = 9f / 16f, windowIsLandscape = false)).isFalse()
    }

    @Test
    fun `a window that ignores orientation requests never waits`() {
        assertThat(awaiting(isFullscreen = true, windowIsLandscape = false, rotationHonoured = false)).isFalse()
        assertThat(awaiting(isFullscreen = false, windowIsLandscape = true, rotationHonoured = false)).isFalse()
    }
}
