package io.github.aedev.flow.ui.components.musicplayer.sheet

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MiniPlayerBoundsTest {
    private fun bounds(
        width: Float,
        startInset: Float = 0f,
        compact: Boolean,
    ) = miniPlayerBounds(
        containerWidthPx = width,
        startInsetPx = startInset,
        isCompactWidth = compact,
        compactMarginPx = 12f,
        largeMarginPx = 16f,
        maxWidthPx = 480f,
    )

    @Test
    fun `a phone spans the window minus its margins`() {
        assertThat(bounds(411f, compact = true)).isEqualTo(MiniPlayerBounds(start = 12f, width = 387f))
    }

    @Test
    fun `a wide window caps the width and centres it`() {
        assertThat(bounds(1280f, compact = false)).isEqualTo(MiniPlayerBounds(start = 400f, width = 480f))
    }

    @Test
    fun `the rail moves the centre to the content area`() {
        assertThat(bounds(1280f, startInset = 96f, compact = false)).isEqualTo(MiniPlayerBounds(start = 448f, width = 480f))
    }

    @Test
    fun `a medium window narrower than the cap keeps its side margins`() {
        assertThat(bounds(500f, compact = false)).isEqualTo(MiniPlayerBounds(start = 16f, width = 468f))
    }

    @Test
    fun `a window too small for any player gets zero width, never negative`() {
        assertThat(bounds(10f, compact = true).width).isEqualTo(0f)
        assertThat(bounds(20f, startInset = 30f, compact = false).width).isEqualTo(0f)
    }
}
