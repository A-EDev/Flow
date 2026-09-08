package io.github.aedev.flow.ui.components.videoplayer.motion

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.components.videoplayer.MiniPlayerCorner
import org.junit.Test

class DraggablePlayerDragMathTest {
    private val bounds = MiniPlayerBounds(minX = 24f, maxX = 624f, minY = 200f, maxY = 1400f)

    @Test
    fun `expand drag zoom rests at one and never reaches its ceiling`() {
        assertThat(expandDragZoomFor(0f)).isEqualTo(1f)
        assertThat(expandDragZoomFor(-10f)).isEqualTo(1f)
        assertThat(expandDragZoomFor(60f)).isWithin(0.0001f).of(1.03f)
        assertThat(expandDragZoomFor(10_000f)).isLessThan(1.06f)
        assertThat(expandDragZoomFor(300f)).isGreaterThan(expandDragZoomFor(100f))
    }

    @Test
    fun `upward swipe commits to fullscreen on travel or on a fast fling`() {
        assertThat(shouldEnterFullscreenFromSwipe(totalUpwardDragPx = 81f, scaledVelocityY = 0f)).isTrue()
        assertThat(shouldEnterFullscreenFromSwipe(totalUpwardDragPx = 10f, scaledVelocityY = -900f)).isTrue()
        assertThat(shouldEnterFullscreenFromSwipe(totalUpwardDragPx = 40f, scaledVelocityY = -200f)).isFalse()
    }

    @Test
    fun `collapse release needs travel or a downward fling`() {
        assertThat(shouldCollapseOnRelease(fraction = 0.2f, scaledVelocityY = 0f)).isTrue()
        assertThat(shouldCollapseOnRelease(fraction = 0.02f, scaledVelocityY = 350f)).isTrue()
        assertThat(shouldCollapseOnRelease(fraction = 0.06f, scaledVelocityY = 250f)).isTrue()
        assertThat(shouldCollapseOnRelease(fraction = 0.04f, scaledVelocityY = 250f)).isFalse()
        assertThat(shouldCollapseOnRelease(fraction = 0.05f, scaledVelocityY = -100f)).isFalse()
    }

    @Test
    fun `corner targets follow the corner`() {
        assertThat(cornerTargetX(MiniPlayerCorner.TopLeft, minX = 24f, maxX = 624f)).isEqualTo(24f)
        assertThat(cornerTargetX(MiniPlayerCorner.BottomRight, minX = 24f, maxX = 624f)).isEqualTo(624f)
        assertThat(cornerTargetY(MiniPlayerCorner.TopRight, minY = 200f, maxY = 1400f)).isEqualTo(200f)
        assertThat(cornerTargetY(MiniPlayerCorner.BottomLeft, minY = 200f, maxY = 1400f)).isEqualTo(1400f)
    }

    @Test
    fun `a dominant horizontal fling picks the side regardless of position`() {
        val corner =
            resolveMiniPlayerCorner(
                current = MiniPlayerCorner.BottomRight,
                currentX = 600f,
                currentY = 1380f,
                bounds = bounds,
                scaledVelocityX = -1500f,
                scaledVelocityY = 100f,
            )
        assertThat(corner).isEqualTo(MiniPlayerCorner.BottomLeft)
    }

    @Test
    fun `a dominant vertical fling flips top and bottom`() {
        val corner =
            resolveMiniPlayerCorner(
                current = MiniPlayerCorner.BottomRight,
                currentX = 600f,
                currentY = 1380f,
                bounds = bounds,
                scaledVelocityX = 50f,
                scaledVelocityY = -1200f,
            )
        assertThat(corner).isEqualTo(MiniPlayerCorner.TopRight)
    }

    @Test
    fun `a slow release switches only past fifteen percent of the travel`() {
        val stays =
            resolveMiniPlayerCorner(
                current = MiniPlayerCorner.BottomRight,
                currentX = 560f,
                currentY = 1400f,
                bounds = bounds,
                scaledVelocityX = 0f,
                scaledVelocityY = 0f,
            )
        assertThat(stays).isEqualTo(MiniPlayerCorner.BottomRight)

        val switches =
            resolveMiniPlayerCorner(
                current = MiniPlayerCorner.BottomRight,
                currentX = 500f,
                currentY = 1400f,
                bounds = bounds,
                scaledVelocityX = 0f,
                scaledVelocityY = 0f,
            )
        assertThat(switches).isEqualTo(MiniPlayerCorner.BottomLeft)
    }

    @Test
    fun `velocity projection can switch a corner the position alone would not`() {
        val corner =
            resolveMiniPlayerCorner(
                current = MiniPlayerCorner.BottomRight,
                currentX = 600f,
                currentY = 1400f,
                bounds = bounds,
                scaledVelocityX = -350f,
                scaledVelocityY = 0f,
            )
        assertThat(corner).isEqualTo(MiniPlayerCorner.BottomLeft)
    }

    @Test
    fun `a fast horizontal fling from the matching half dismisses`() {
        val right =
            resolveMiniPlayerDismissOffset(
                targetCorner = MiniPlayerCorner.BottomRight,
                currentX = 500f,
                bounds = bounds,
                scaledVelocityX = 2500f,
                scaledVelocityY = 100f,
                screenWidth = 1080f,
                miniWidth = 486f,
                margin = 24f,
            )
        assertThat(right).isEqualTo(1080f + 486f)

        val left =
            resolveMiniPlayerDismissOffset(
                targetCorner = MiniPlayerCorner.TopLeft,
                currentX = 100f,
                bounds = bounds,
                scaledVelocityX = -2500f,
                scaledVelocityY = -100f,
                screenWidth = 1080f,
                miniWidth = 486f,
                margin = 24f,
            )
        assertThat(left).isEqualTo(-(486f + 24f))
    }

    @Test
    fun `dismiss needs a horizontal fling from the side it is heading to`() {
        val wrongHalf =
            resolveMiniPlayerDismissOffset(
                targetCorner = MiniPlayerCorner.BottomRight,
                currentX = 100f,
                bounds = bounds,
                scaledVelocityX = 2500f,
                scaledVelocityY = 0f,
                screenWidth = 1080f,
                miniWidth = 486f,
                margin = 24f,
            )
        assertThat(wrongHalf).isNull()

        val tooDiagonal =
            resolveMiniPlayerDismissOffset(
                targetCorner = MiniPlayerCorner.BottomRight,
                currentX = 500f,
                bounds = bounds,
                scaledVelocityX = 2500f,
                scaledVelocityY = 1000f,
                screenWidth = 1080f,
                miniWidth = 486f,
                margin = 24f,
            )
        assertThat(tooDiagonal).isNull()

        val tooSlow =
            resolveMiniPlayerDismissOffset(
                targetCorner = MiniPlayerCorner.BottomRight,
                currentX = 500f,
                bounds = bounds,
                scaledVelocityX = 1500f,
                scaledVelocityY = 0f,
                screenWidth = 1080f,
                miniWidth = 486f,
                margin = 24f,
            )
        assertThat(tooSlow).isNull()
    }
}
