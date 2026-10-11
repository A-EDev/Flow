package io.github.aedev.flow.ui.components.videoplayer

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.util.lerp

private const val VIDEO_ASPECT_RATIO = 16f / 9f
private const val EXPAND_COMMIT_FRACTION = 0.03f
private const val EXPAND_FLING_VELOCITY = 300f

/**
 * One frame of the background bar growing into the player, in the bar's container: the card
 * stretching from the bar to the whole window, the thumbnail moving into the video's place at the
 * top, and how much of the bar's own text and buttons still shows.
 */
@Immutable
internal data class BackgroundBarExpandFrame(
    val card: Rect,
    val cornerRadiusPx: Float,
    val thumbnail: Rect,
    val chromeAlpha: Float,
)

/**
 * [thumbnailAtRest] is relative to the bar, and so is the returned [BackgroundBarExpandFrame.thumbnail]
 * relative to the card; at a [fraction] of one the card is the window and the thumbnail is the video's
 * full-width box under the status bar, where the player puts it.
 */
internal fun backgroundBarExpandFrame(
    fraction: Float,
    bar: Rect,
    containerWidth: Float,
    containerHeight: Float,
    statusBarHeight: Float,
    barCornerRadiusPx: Float,
    thumbnailAtRest: Rect,
): BackgroundBarExpandFrame {
    val f = fraction.coerceIn(0f, 1f)
    val card =
        Rect(
            left = lerp(bar.left, 0f, f),
            top = lerp(bar.top, 0f, f),
            right = lerp(bar.right, containerWidth, f),
            bottom = lerp(bar.bottom, containerHeight, f),
        )
    val thumbnailWidth = lerp(thumbnailAtRest.width, containerWidth, f)
    val thumbnailLeft = lerp(thumbnailAtRest.left, 0f, f)
    val thumbnailTop = lerp(thumbnailAtRest.top, statusBarHeight, f)
    return BackgroundBarExpandFrame(
        card = card,
        cornerRadiusPx = lerp(barCornerRadiusPx, 0f, f),
        thumbnail =
            Rect(
                left = thumbnailLeft,
                top = thumbnailTop,
                right = thumbnailLeft + thumbnailWidth,
                bottom = thumbnailTop + thumbnailWidth / VIDEO_ASPECT_RATIO,
            ),
        chromeAlpha = (1f - 2f * f).coerceIn(0f, 1f),
    )
}

/**
 * Whether a released swipe up opens the player, as the music bar decides it: a fling either way
 * wins, and otherwise any real upward travel opens it, so a quick flick the finger barely reported
 * still counts.
 */
internal fun shouldExpandBackgroundBar(
    fraction: Float,
    velocityY: Float,
): Boolean =
    when {
        velocityY < -EXPAND_FLING_VELOCITY -> true
        velocityY > EXPAND_FLING_VELOCITY -> false
        else -> fraction > EXPAND_COMMIT_FRACTION
    }
