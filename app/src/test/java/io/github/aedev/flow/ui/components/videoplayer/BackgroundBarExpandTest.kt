package io.github.aedev.flow.ui.components.videoplayer

import androidx.compose.ui.geometry.Rect
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BackgroundBarExpandTest {
    private val bar = Rect(36f, 2300f, 1184f, 2492f)
    private val thumbnail = Rect(30f, 30f, 264f, 162f)

    private fun frame(fraction: Float) =
        backgroundBarExpandFrame(
            fraction = fraction,
            bar = bar,
            containerWidth = 1220f,
            containerHeight = 2712f,
            statusBarHeight = 110f,
            barCornerRadiusPx = 96f,
            thumbnailAtRest = thumbnail,
        )

    @Test
    fun `at rest it is the bar itself`() {
        val rest = frame(0f)
        assertThat(rest.card).isEqualTo(bar)
        assertThat(rest.thumbnail.left).isEqualTo(30f)
        assertThat(rest.cornerRadiusPx).isEqualTo(96f)
        assertThat(rest.chromeAlpha).isEqualTo(1f)
    }

    @Test
    fun `fully grown it is the window with the video under the status bar`() {
        val grown = frame(1f)
        assertThat(grown.card).isEqualTo(Rect(0f, 0f, 1220f, 2712f))
        assertThat(grown.thumbnail.left).isEqualTo(0f)
        assertThat(grown.thumbnail.top).isEqualTo(110f)
        assertThat(grown.thumbnail.width).isEqualTo(1220f)
        assertThat(grown.thumbnail.height).isWithin(0.01f).of(1220f * 9f / 16f)
        assertThat(grown.cornerRadiusPx).isEqualTo(0f)
    }

    @Test
    fun `the bar's text and buttons are gone halfway and the thumbnail keeps sixteen by nine`() {
        assertThat(frame(0.5f).chromeAlpha).isEqualTo(0f)
        val mid = frame(0.3f).thumbnail
        assertThat(mid.width / mid.height).isWithin(0.001f).of(16f / 9f)
    }

    @Test
    fun `an overshooting spring never grows the card past the window`() {
        assertThat(frame(1.08f).card).isEqualTo(Rect(0f, 0f, 1220f, 2712f))
    }

    @Test
    fun `a fling decides the release, otherwise any real upward travel opens`() {
        assertThat(shouldExpandBackgroundBar(fraction = 0.02f, velocityY = -900f)).isTrue()
        assertThat(shouldExpandBackgroundBar(fraction = 0.6f, velocityY = 900f)).isFalse()
        assertThat(shouldExpandBackgroundBar(fraction = 0.05f, velocityY = 0f)).isTrue()
        assertThat(shouldExpandBackgroundBar(fraction = 0.01f, velocityY = 0f)).isFalse()
        assertThat(shouldExpandBackgroundBar(fraction = 0.3f, velocityY = 400f)).isFalse()
    }
}
