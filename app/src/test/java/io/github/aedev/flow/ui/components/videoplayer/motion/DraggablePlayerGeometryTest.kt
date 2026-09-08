package io.github.aedev.flow.ui.components.videoplayer.motion

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.components.videoplayer.MiniPlayerCorner
import org.junit.Test

class DraggablePlayerGeometryTest {
    private fun phone(
        currentSizeScale: Float = 1f,
        corner: MiniPlayerCorner = MiniPlayerCorner.BottomRight,
        isShrinkingToCorner: Boolean = false,
        videoAspectRatio: Float = 16f / 9f,
    ) = computeDraggablePlayerGeometry(
        screenWidth = 1080f,
        screenHeight = 2400f,
        statusBarHeight = 80f,
        margin = 24f,
        bottomNavPad = 200f,
        topBarPad = 168f,
        isTablet = false,
        isFoldable = false,
        isSplitLayout = false,
        smallestScreenWidthDp = 411,
        miniPlayerScale = 0.45f,
        videoAspectRatio = videoAspectRatio,
        currentSizeScale = currentSizeScale,
        corner = corner,
        isShrinkingToCorner = isShrinkingToCorner,
        cachedTargetX = 0f,
        offsetXFallback = { 0f },
    )

    @Test
    fun `phone mini player rests in the bottom right by default`() {
        val g = phone()
        assertThat(g.baseMiniWidth).isWithin(0.01f).of(486f)
        assertThat(g.miniWidth).isWithin(0.01f).of(486f)
        assertThat(g.miniHeight).isWithin(0.01f).of(486f * 9f / 16f)
        assertThat(g.minX).isEqualTo(24f)
        assertThat(g.maxX).isWithin(0.01f).of(1080f - 486f - 24f)
        assertThat(g.minY).isEqualTo(80f + 168f + 24f)
        assertThat(g.maxY).isWithin(0.01f).of(2400f - g.miniHeight - 200f - 24f)
        assertThat(g.targetMiniX).isEqualTo(g.maxX)
        assertThat(g.targetMiniY).isEqualTo(g.maxY)
        assertThat(g.isWideMode).isFalse()
    }

    @Test
    fun `wide mode on a phone centres horizontally and keeps the corner row`() {
        val g = phone(currentSizeScale = 2.2f, corner = MiniPlayerCorner.TopLeft)
        assertThat(g.isWideMode).isTrue()
        assertThat(g.miniWidth).isWithin(0.01f).of(g.maxWideWidth)
        assertThat(g.targetMiniX).isEqualTo(g.stablePhoneCenteredX)
        assertThat(g.targetMiniY).isEqualTo(g.minY)
    }

    @Test
    fun `shrinking back to a corner targets the normal corner even while still wide`() {
        val g = phone(currentSizeScale = 2.2f, corner = MiniPlayerCorner.BottomLeft, isShrinkingToCorner = true)
        assertThat(g.targetMiniX).isEqualTo(g.normalTargetX)
        assertThat(g.targetMiniY).isEqualTo(g.normalTargetY)
        assertThat(g.normalTargetX).isEqualTo(24f)
    }

    @Test
    fun `a portrait video keeps the mini envelope inside the landscape box`() {
        val g = phone(videoAspectRatio = 9f / 16f)
        assertThat(g.clampedAspect).isLessThan(1f)
        assertThat(g.miniWidth).isWithin(0.01f).of(g.baseMiniWidth * g.clampedAspect)
        assertThat(g.expandedVideoHeight).isGreaterThan(g.baseVideoHeight)
        assertThat(g.visualMiniScale).isWithin(0.0001f).of(g.miniWidth / 1080f)
    }
}
