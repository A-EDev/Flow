package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection

/** The page fades in between these expansion fractions while an open grows out of a card. */
private const val OPEN_BODY_FADE_START = 0.2f
private const val OPEN_BODY_FADE_END = 0.75f

/** The ground of the page while an open grows: the origin's rectangle at 1, the whole layout at 0. */
internal fun openGroundRect(
    origin: OpenOriginRect,
    fraction: Float,
    width: Float,
    height: Float,
): Rect {
    val progress = 1f - fraction.coerceIn(0f, 1f)
    return Rect(
        left = lerpClamped(origin.left, 0f, progress),
        top = lerpClamped(origin.top, 0f, progress),
        right = lerpClamped(origin.right, width, progress),
        bottom = lerpClamped(origin.bottom, height, progress),
    )
}

internal fun openGroundCornerRadius(
    origin: OpenOriginRect,
    fraction: Float,
): Float = origin.cornerRadius * fraction.coerceIn(0f, 1f)

internal fun openBodyAlpha(fraction: Float): Float {
    val t = ((OPEN_BODY_FADE_END - fraction) / (OPEN_BODY_FADE_END - OPEN_BODY_FADE_START)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/**
 * The video box's corner radius in its own, pre-scale px. During an open it follows the card's
 * radius down to square; otherwise the mini player's radius applies once the box is past 10%.
 */
internal fun morphCornerRadiusPx(
    fraction: Float,
    origin: OpenOriginRect?,
    expandedVideoWidth: Float,
    miniCornerRadiusPx: Float,
    visualMiniScale: Float,
): Float {
    if (origin != null) {
        val scale = lerpClamped(1f, origin.width / expandedVideoWidth.coerceAtLeast(1f), fraction)
        return openGroundCornerRadius(origin, fraction) / scale.coerceAtLeast(0.01f)
    }
    return if (fraction > 0.1f) miniCornerRadiusPx / visualMiniScale.coerceAtLeast(0.01f) else 0f
}

/** Clips to [rect], given in the clipped layer's own coordinates. */
internal class RoundRectClipShape(
    private val rect: Rect,
    private val cornerRadius: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Rounded(RoundRect(rect, CornerRadius(cornerRadius)))
}
