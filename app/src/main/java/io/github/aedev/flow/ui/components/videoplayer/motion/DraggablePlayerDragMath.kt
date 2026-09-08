package io.github.aedev.flow.ui.components.videoplayer.motion

import io.github.aedev.flow.ui.components.videoplayer.MiniPlayerCorner
import kotlin.math.abs

/** Upward travel that commits to fullscreen; mirrors the release check. */
private const val EXPAND_DRAG_COMMIT_PX = 80f

/** How hard the zoom resists — smaller reaches the ceiling sooner. */
private const val EXPAND_DRAG_SOFTNESS_PX = 60f

/** Ceiling the zoom approaches but never reaches, so the drag always has somewhere to go. */
private const val EXPAND_DRAG_MAX_ZOOM = 1.06f

private const val FULLSCREEN_SWIPE_VELOCITY = -800f
private const val COLLAPSE_COMMIT_FRACTION = 0.1f
private const val COLLAPSE_FLING_VELOCITY = 300f
private const val COLLAPSE_SLOW_FLING_VELOCITY = 200f
private const val COLLAPSE_SLOW_FLING_FRACTION = 0.05f
private const val CORNER_FLING_VELOCITY = 400f
private const val CORNER_FLING_AXIS_DOMINANCE = 0.8f
private const val CORNER_SWITCH_TRAVEL_FRACTION = 0.15f
private const val CORNER_VELOCITY_PROJECTION_S = 0.3f
private const val DISMISS_FLING_VELOCITY = 2000f
private const val DISMISS_AXIS_DOMINANCE = 3f

internal fun lerpClamped(
    start: Float,
    stop: Float,
    fraction: Float,
): Float = start + (stop - start) * fraction.coerceIn(0f, 1f)

internal fun expandDragZoomFor(travelPx: Float): Float {
    if (travelPx <= 0f) return 1f
    val progress = travelPx / (travelPx + EXPAND_DRAG_SOFTNESS_PX)
    return 1f + (EXPAND_DRAG_MAX_ZOOM - 1f) * progress
}

internal fun shouldEnterFullscreenFromSwipe(
    totalUpwardDragPx: Float,
    scaledVelocityY: Float,
): Boolean = totalUpwardDragPx > EXPAND_DRAG_COMMIT_PX || scaledVelocityY < FULLSCREEN_SWIPE_VELOCITY

internal fun shouldCollapseOnRelease(
    fraction: Float,
    scaledVelocityY: Float,
): Boolean =
    fraction > COLLAPSE_COMMIT_FRACTION ||
        scaledVelocityY > COLLAPSE_FLING_VELOCITY ||
        (scaledVelocityY > COLLAPSE_SLOW_FLING_VELOCITY && fraction > COLLAPSE_SLOW_FLING_FRACTION)

internal data class MiniPlayerBounds(
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val maxY: Float,
)

internal val MiniPlayerCorner.isLeft: Boolean
    get() = this == MiniPlayerCorner.TopLeft || this == MiniPlayerCorner.BottomLeft

internal val MiniPlayerCorner.isTop: Boolean
    get() = this == MiniPlayerCorner.TopLeft || this == MiniPlayerCorner.TopRight

internal fun cornerFor(
    left: Boolean,
    top: Boolean,
): MiniPlayerCorner =
    when {
        left && top -> MiniPlayerCorner.TopLeft
        left -> MiniPlayerCorner.BottomLeft
        top -> MiniPlayerCorner.TopRight
        else -> MiniPlayerCorner.BottomRight
    }

internal fun cornerTargetX(
    corner: MiniPlayerCorner,
    minX: Float,
    maxX: Float,
): Float = if (corner.isLeft) minX else maxX

internal fun cornerTargetY(
    corner: MiniPlayerCorner,
    minY: Float,
    maxY: Float,
): Float = if (corner.isTop) minY else maxY

/**
 * Picks the corner a released mini player settles into: a dominant fling wins outright, otherwise
 * the position projected 300 ms along the velocity has to cross 15% of the travel to switch.
 */
internal fun resolveMiniPlayerCorner(
    current: MiniPlayerCorner,
    currentX: Float,
    currentY: Float,
    bounds: MiniPlayerBounds,
    scaledVelocityX: Float,
    scaledVelocityY: Float,
): MiniPlayerCorner {
    val originX = cornerTargetX(current, bounds.minX, bounds.maxX)
    val originY = cornerTargetY(current, bounds.minY, bounds.maxY)
    val totalTravelX = (bounds.maxX - bounds.minX).coerceAtLeast(1f)
    val totalTravelY = (bounds.maxY - bounds.minY).coerceAtLeast(1f)
    val switchThresholdX = totalTravelX * CORNER_SWITCH_TRAVEL_FRACTION
    val switchThresholdY = totalTravelY * CORNER_SWITCH_TRAVEL_FRACTION
    val projectedDeltaX = (currentX - originX) + scaledVelocityX * CORNER_VELOCITY_PROJECTION_S
    val projectedDeltaY = (currentY - originY) + scaledVelocityY * CORNER_VELOCITY_PROJECTION_S
    val wasLeft = current.isLeft
    val wasTop = current.isTop

    val goLeft =
        when {
            abs(scaledVelocityX) > CORNER_FLING_VELOCITY &&
                abs(scaledVelocityX) > abs(scaledVelocityY) * CORNER_FLING_AXIS_DOMINANCE -> {
                scaledVelocityX < 0
            }

            wasLeft && projectedDeltaX > switchThresholdX -> {
                false
            }

            !wasLeft && projectedDeltaX < -switchThresholdX -> {
                true
            }

            else -> {
                wasLeft
            }
        }
    val goTop =
        when {
            abs(scaledVelocityY) > CORNER_FLING_VELOCITY &&
                abs(scaledVelocityY) > abs(scaledVelocityX) * CORNER_FLING_AXIS_DOMINANCE -> {
                scaledVelocityY < 0
            }

            wasTop && projectedDeltaY > switchThresholdY -> {
                false
            }

            !wasTop && projectedDeltaY < -switchThresholdY -> {
                true
            }

            else -> {
                wasTop
            }
        }
    return cornerFor(left = goLeft, top = goTop)
}

/**
 * The off-screen x a horizontal fling should throw the mini player to, or null when the release
 * is not a dismiss: it needs a fast, clearly horizontal fling from the half of the screen it is
 * heading towards.
 */
internal fun resolveMiniPlayerDismissOffset(
    targetCorner: MiniPlayerCorner,
    currentX: Float,
    bounds: MiniPlayerBounds,
    scaledVelocityX: Float,
    scaledVelocityY: Float,
    screenWidth: Float,
    miniWidth: Float,
    margin: Float,
): Float? {
    val centerX = (bounds.minX + bounds.maxX) / 2f
    val isHorizontalFling = abs(scaledVelocityX) > abs(scaledVelocityY) * DISMISS_AXIS_DOMINANCE
    if (!isHorizontalFling) return null
    val goLeft = targetCorner.isLeft
    val canDismissRight = !goLeft && scaledVelocityX > DISMISS_FLING_VELOCITY && currentX > centerX
    val canDismissLeft = goLeft && scaledVelocityX < -DISMISS_FLING_VELOCITY && currentX < centerX
    return when {
        canDismissRight -> screenWidth + miniWidth
        canDismissLeft -> -(miniWidth + margin)
        else -> null
    }
}
