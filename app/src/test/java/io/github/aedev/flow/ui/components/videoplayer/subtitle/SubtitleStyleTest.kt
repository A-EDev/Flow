package io.github.aedev.flow.ui.components.videoplayer.subtitle

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SubtitleStyleTest {
    private val style = SubtitleStyle(bottomPadding = 40f, fullscreenBottomPadding = 120f, verticalFullscreenBottomPadding = 260f)

    @Test
    fun `each layout has its own position`() {
        assertThat(style.bottomPaddingFor(isFullscreen = false, isVertical = true)).isEqualTo(40f)
        assertThat(style.bottomPaddingFor(isFullscreen = true, isVertical = false)).isEqualTo(120f)
        assertThat(style.bottomPaddingFor(isFullscreen = true, isVertical = true)).isEqualTo(260f)
    }
}
