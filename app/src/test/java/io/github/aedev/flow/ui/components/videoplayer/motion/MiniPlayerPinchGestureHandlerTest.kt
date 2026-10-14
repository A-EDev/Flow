package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.ui.geometry.Offset
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.components.videoplayer.MiniPlayerCorner
import org.junit.Test

class MiniPlayerPinchGestureHandlerTest {
    private fun metrics(largeScreen: Boolean) =
        DraggablePlayerGestureMetrics().apply {
            screenWidth = 1080f
            screenHeight = 2400f
            margin = 24f
            minX = 24f
            minY = 200f
            bottomNavPad = 200f
            baseMiniWidth = 480f
            maxWideWidth = if (largeScreen) 600f else 1032f
            clampedAspect = 16f / 9f
            stablePhoneCenteredX = 24f
            isLargeScreen = largeScreen
        }

    @Test
    fun `growing from a bottom right corner keeps the right and bottom edges where they were`() {
        val metrics = metrics(largeScreen = true)
        val anchor = metrics.miniPinchAnchor(1.2f, MiniPlayerCorner.BottomRight, maxScale = 1.25f)

        assertThat(anchor.x + 576f).isWithin(0.5f).of(1080f - 24f)
        assertThat(anchor.y + 324f).isWithin(0.5f).of(2400f - 200f - 24f)
    }

    @Test
    fun `a phone player drifts to the centre only as it reaches the wide size`() {
        val metrics = metrics(largeScreen = false)
        val maxScale = 1032f / 480f

        assertThat(metrics.miniPinchAnchor(1f, MiniPlayerCorner.TopRight, maxScale))
            .isEqualTo(Offset(1080f - 480f - 24f, 200f))
        assertThat(metrics.miniPinchAnchor(maxScale, MiniPlayerCorner.TopRight, maxScale).x)
            .isWithin(0.5f)
            .of(24f)
    }

    @Test
    fun `a pinch starts exactly where the player was and follows its corner from there`() {
        val metrics = metrics(largeScreen = true)
        val start = Offset(500f, 1700f)
        val pin = MiniPinchPin(MiniPlayerCorner.BottomRight, start, startScale = 1f, maxScale = 1.25f)

        assertThat(metrics.pinchedMiniOffset(1f, pin)).isEqualTo(start)
        val grown = metrics.pinchedMiniOffset(1.2f, pin)
        assertThat(grown.x).isWithin(0.5f).of(500f - 96f)
        assertThat(grown.y).isWithin(0.5f).of(1700f - 54f)
    }
}
