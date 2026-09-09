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

    private fun large(
        isTablet: Boolean,
        isFoldable: Boolean = false,
        smallestScreenWidthDp: Int = 720,
        isSplitLayout: Boolean = false,
        currentSizeScale: Float = 1f,
        cachedTargetX: Float = 0f,
        offsetXFallback: Float = 0f,
        corner: MiniPlayerCorner = MiniPlayerCorner.BottomRight,
    ) = computeDraggablePlayerGeometry(
        screenWidth = 1600f,
        screenHeight = 2560f,
        statusBarHeight = 80f,
        margin = 24f,
        bottomNavPad = 200f,
        topBarPad = 168f,
        isTablet = isTablet,
        isFoldable = isFoldable,
        isSplitLayout = isSplitLayout,
        smallestScreenWidthDp = smallestScreenWidthDp,
        miniPlayerScale = 0.45f,
        videoAspectRatio = 16f / 9f,
        currentSizeScale = currentSizeScale,
        corner = corner,
        isShrinkingToCorner = false,
        cachedTargetX = cachedTargetX,
        offsetXFallback = { offsetXFallback },
    )

    @Test
    fun `tablet mini scale steps down at 720 and 840 smallest width and ignores the user scale`() {
        assertThat(large(isTablet = true, smallestScreenWidthDp = 600).baseMiniWidth).isWithin(0.01f).of(1600f * 0.38f)
        assertThat(large(isTablet = true, smallestScreenWidthDp = 719).baseMiniWidth).isWithin(0.01f).of(1600f * 0.38f)
        assertThat(large(isTablet = true, smallestScreenWidthDp = 720).baseMiniWidth).isWithin(0.01f).of(1600f * 0.35f)
        assertThat(large(isTablet = true, smallestScreenWidthDp = 839).baseMiniWidth).isWithin(0.01f).of(1600f * 0.35f)
        assertThat(large(isTablet = true, smallestScreenWidthDp = 840).baseMiniWidth).isWithin(0.01f).of(1600f * 0.32f)
        assertThat(large(isTablet = true, smallestScreenWidthDp = 1200).baseMiniWidth).isWithin(0.01f).of(1600f * 0.32f)
    }

    @Test
    fun `foldable mini scale is fixed at 0 42 and ignores the smallest width`() {
        assertThat(large(isTablet = false, isFoldable = true, smallestScreenWidthDp = 600).baseMiniWidth).isWithin(0.01f).of(1600f * 0.42f)
        assertThat(large(isTablet = false, isFoldable = true, smallestScreenWidthDp = 840).baseMiniWidth).isWithin(0.01f).of(1600f * 0.42f)
    }

    @Test
    fun `the wide cap is the full width on phones and a fraction on large screens`() {
        assertThat(phone().maxWideWidth).isWithin(0.01f).of(1080f - 48f)
        assertThat(large(isTablet = false, isFoldable = true).maxWideWidth).isWithin(0.01f).of(1600f * 0.55f - 48f)
        assertThat(large(isTablet = true).maxWideWidth).isWithin(0.01f).of(1600f * 0.60f - 48f)
    }

    @Test
    fun `a device flagged both tablet and foldable takes the tablet mini scale but the foldable wide cap`() {
        // Pins current behaviour: the two lookups check the flags in opposite orders.
        val g = large(isTablet = true, isFoldable = true, smallestScreenWidthDp = 720)
        assertThat(g.baseMiniWidth).isWithin(0.01f).of(1600f * 0.35f)
        assertThat(g.maxWideWidth).isWithin(0.01f).of(1600f * 0.55f - 48f)
    }

    @Test
    fun `a split layout narrows the expanded video to 65 percent of the width`() {
        val g = large(isTablet = true, isSplitLayout = true)
        assertThat(g.expandedVideoWidth).isWithin(0.01f).of(1600f * 0.65f)
        assertThat(g.baseVideoHeight).isWithin(0.01f).of(1600f * 0.65f * 9f / 16f)
        assertThat(g.expandedVideoHeight).isWithin(0.01f).of(g.baseVideoHeight)
        assertThat(g.visualMiniScale).isWithin(0.0001f).of(g.miniWidth / (1600f * 0.65f))
        assertThat(large(isTablet = true).expandedVideoWidth).isEqualTo(1600f)
    }

    @Test
    fun `wide mode on a large screen keeps the cached corner x and the wide row`() {
        val g = large(isTablet = true, currentSizeScale = 2.2f, cachedTargetX = 300f, corner = MiniPlayerCorner.BottomLeft)
        assertThat(g.isWideMode).isTrue()
        assertThat(g.miniWidth).isWithin(0.01f).of(g.maxWideWidth)
        assertThat(g.targetMiniX).isEqualTo(300f)
        assertThat(g.targetMiniY).isEqualTo(g.stableWideTargetY)
        assertThat(g.stableWideTargetY).isEqualTo(g.stableWideMaxY)
    }

    @Test
    fun `wide mode on a large screen with no cached x clamps the live offset`() {
        // Pins current behaviour: a cached x of exactly 0 is read as "unknown" and the live offset
        // is used instead, clamped to the wide-mode drag bounds.
        val far = large(isTablet = true, currentSizeScale = 2.2f, cachedTargetX = 0f, offsetXFallback = 5000f)
        assertThat(far.targetMiniX).isEqualTo(far.maxX)
        assertThat(far.maxX).isWithin(0.01f).of(1600f - far.miniWidth - 24f)

        val near = large(isTablet = false, isFoldable = true, currentSizeScale = 2.2f, cachedTargetX = 0f, offsetXFallback = -50f)
        assertThat(near.targetMiniX).isEqualTo(near.minX)
        assertThat(near.minX).isEqualTo(24f)
    }
}
