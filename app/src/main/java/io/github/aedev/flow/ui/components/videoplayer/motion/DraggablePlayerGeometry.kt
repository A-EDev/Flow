package io.github.aedev.flow.ui.components.videoplayer.motion

import io.github.aedev.flow.player.sanitizeDisplayAspectRatio
import io.github.aedev.flow.ui.components.videoplayer.MiniPlayerCorner

private const val WIDE_MODE_SCALE_THRESHOLD = 1.5f

/** Every px value the layout, the effects and the gesture handlers derive from one composition. */
internal data class DraggablePlayerGeometry(
    val screenWidth: Float,
    val screenHeight: Float,
    val statusBarHeight: Float,
    val margin: Float,
    val bottomNavPad: Float,
    val clampedAspect: Float,
    val baseMiniWidth: Float,
    val maxWideWidth: Float,
    val miniWidth: Float,
    val miniHeight: Float,
    val isWideMode: Boolean,
    val expandedVideoWidth: Float,
    val baseVideoHeight: Float,
    val expandedVideoHeight: Float,
    val visualMiniScale: Float,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
    val normalTargetX: Float,
    val normalTargetY: Float,
    val stablePhoneCenteredX: Float,
    val stableWideMaxY: Float,
    val stableWideTargetY: Float,
    val targetMiniX: Float,
    val targetMiniY: Float,
) {
    fun miniBoxWidth(envelopeSide: Float): Float = miniBoxWidthFor(envelopeSide, clampedAspect)
}

internal fun miniBoxWidthFor(
    envelopeSide: Float,
    clampedAspect: Float,
): Float = if (clampedAspect >= 1f) envelopeSide else envelopeSide * clampedAspect

/**
 * @param isLargeWindow the window can host one of the detail layouts, so the mini player is sized
 *   from the window instead of the user's scale and is free to rest anywhere along the edge.
 * @param isTwoPaneWindow the body puts the detail pane beside the video, so the expanded video
 *   only takes the leading part of the width.
 * @param detailPaneWidth the width the detail pane and its spacer take from the video's row in a
 *   two-pane window; the expanded video gets what is left.
 */
internal fun computeDraggablePlayerGeometry(
    screenWidth: Float,
    screenHeight: Float,
    statusBarHeight: Float,
    margin: Float,
    bottomNavPad: Float,
    topBarPad: Float,
    isLargeWindow: Boolean,
    isTwoPaneWindow: Boolean,
    miniPlayerScale: Float,
    startInset: Float = 0f,
    detailPaneWidth: Float = 0f,
    videoAspectRatio: Float,
    currentSizeScale: Float,
    corner: MiniPlayerCorner,
    isShrinkingToCorner: Boolean,
    cachedTargetX: Float,
    offsetXFallback: () -> Float,
): DraggablePlayerGeometry {
    val effectiveMiniScale =
        when {
            isTwoPaneWindow -> 0.32f
            isLargeWindow -> 0.35f
            else -> miniPlayerScale
        }
    val baseMiniWidth = screenWidth * effectiveMiniScale
    val maxWideFraction = if (isLargeWindow) 0.60f else 1.00f
    val maxWideWidth = ((screenWidth * maxWideFraction) - (margin * 2f)).coerceAtLeast(baseMiniWidth)
    val clampedAspect = sanitizeDisplayAspectRatio(videoAspectRatio)
    val miniWidth = miniBoxWidthFor(baseMiniWidth * currentSizeScale, clampedAspect).coerceAtMost(maxWideWidth)
    val miniHeight = miniWidth / clampedAspect
    val isWideMode = currentSizeScale > WIDE_MODE_SCALE_THRESHOLD

    val expandedVideoWidth = if (isTwoPaneWindow) screenWidth - detailPaneWidth else screenWidth
    val baseVideoHeight = expandedVideoWidth * (9f / 16f)
    val expandedVideoHeight = expandedVideoWidth / clampedAspect
    val visualMiniScale = (miniWidth / expandedVideoWidth.coerceAtLeast(1f)).coerceIn(0.01f, 1f)

    val minX = margin + startInset
    val maxX = (screenWidth - miniWidth - margin).coerceAtLeast(minX)
    val minY = statusBarHeight + topBarPad + margin
    val maxY = (screenHeight - miniHeight - bottomNavPad - margin).coerceAtLeast(minY)

    val normalMiniWidth = miniBoxWidthFor(baseMiniWidth, clampedAspect)
    val normalMiniHeight = normalMiniWidth / clampedAspect
    val normalMaxX = (screenWidth - normalMiniWidth - margin).coerceAtLeast(margin)
    val normalMaxY = (screenHeight - normalMiniHeight - bottomNavPad - margin).coerceAtLeast(minY)
    val normalTargetX = cornerTargetX(corner, minX = minX, maxX = normalMaxX)
    val normalTargetY = cornerTargetY(corner, minY = minY, maxY = normalMaxY)

    val stableWideWidth = miniBoxWidthFor(maxWideWidth, clampedAspect)
    val stablePhoneCenteredX = ((screenWidth - stableWideWidth) / 2f).coerceAtLeast(margin)
    val stableWideHeight = stableWideWidth / clampedAspect
    val stableWideMaxY = (screenHeight - stableWideHeight - bottomNavPad - margin).coerceAtLeast(minY)
    val stableWideTargetY = cornerTargetY(corner, minY = minY, maxY = stableWideMaxY)

    val targetMiniX =
        when {
            isShrinkingToCorner -> normalTargetX
            isWideMode && !isLargeWindow -> stablePhoneCenteredX
            isWideMode && isLargeWindow -> cachedTargetX.takeIf { it != 0f } ?: offsetXFallback().coerceIn(minX, maxX)
            else -> normalTargetX
        }
    val targetMiniY = if (isWideMode && !isShrinkingToCorner) stableWideTargetY else normalTargetY

    return DraggablePlayerGeometry(
        screenWidth = screenWidth,
        screenHeight = screenHeight,
        statusBarHeight = statusBarHeight,
        margin = margin,
        bottomNavPad = bottomNavPad,
        clampedAspect = clampedAspect,
        baseMiniWidth = baseMiniWidth,
        maxWideWidth = maxWideWidth,
        miniWidth = miniWidth,
        miniHeight = miniHeight,
        isWideMode = isWideMode,
        expandedVideoWidth = expandedVideoWidth,
        baseVideoHeight = baseVideoHeight,
        expandedVideoHeight = expandedVideoHeight,
        visualMiniScale = visualMiniScale,
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        normalTargetX = normalTargetX,
        normalTargetY = normalTargetY,
        stablePhoneCenteredX = stablePhoneCenteredX,
        stableWideMaxY = stableWideMaxY,
        stableWideTargetY = stableWideTargetY,
        targetMiniX = targetMiniX,
        targetMiniY = targetMiniY,
    )
}

internal fun DraggablePlayerGestureMetrics.update(
    geometry: DraggablePlayerGeometry,
    isLargeWindow: Boolean,
    isLandscape: Boolean,
    isFullscreen: Boolean,
    tapToExpand: Boolean,
    onFullscreenGesture: (() -> Unit)?,
    onCollapseGesture: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    minX = geometry.minX
    maxX = geometry.maxX
    minY = geometry.minY
    maxY = geometry.maxY
    statusBarHeight = geometry.statusBarHeight
    targetMiniX = geometry.targetMiniX
    targetMiniY = geometry.targetMiniY
    screenWidth = geometry.screenWidth
    screenHeight = geometry.screenHeight
    miniWidth = geometry.miniWidth
    baseMiniWidth = geometry.baseMiniWidth
    maxWideWidth = geometry.maxWideWidth
    expandedVideoWidth = geometry.expandedVideoWidth
    clampedAspect = geometry.clampedAspect
    margin = geometry.margin
    bottomNavPad = geometry.bottomNavPad
    stablePhoneCenteredX = geometry.stablePhoneCenteredX
    isLargeScreen = isLargeWindow
    this.isLandscape = isLandscape
    this.isFullscreen = isFullscreen
    this.tapToExpand = tapToExpand
    this.onFullscreenGesture = onFullscreenGesture
    this.onCollapseGesture = onCollapseGesture
    this.onDismiss = onDismiss
}
