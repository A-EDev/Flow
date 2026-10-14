package io.github.aedev.flow.ui.screens.player.stage

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PortraitFullscreenLayoutTest {
    private fun layout(
        isFullscreen: Boolean = true,
        dragPortrait: Boolean = false,
        aspect: Float = 16f / 9f,
        ignored: Boolean = false,
        windowPortrait: Boolean = false,
    ) = usesPortraitFullscreenLayout(isFullscreen, dragPortrait, aspect, ignored, windowPortrait)

    @Test
    fun `a phone lays out for the orientation it is about to get`() {
        assertThat(layout(aspect = 9f / 16f)).isTrue()
        assertThat(layout(dragPortrait = true)).isTrue()
        assertThat(layout(windowPortrait = true)).isFalse()
    }

    @Test
    fun `a large screen that ignores the request follows its window`() {
        assertThat(layout(aspect = 9f / 16f, ignored = true)).isFalse()
        assertThat(layout(dragPortrait = true, ignored = true)).isFalse()
        assertThat(layout(ignored = true, windowPortrait = true)).isTrue()
    }

    @Test
    fun `nothing is portrait outside fullscreen`() {
        assertThat(layout(isFullscreen = false, aspect = 9f / 16f, windowPortrait = true)).isFalse()
    }
}
