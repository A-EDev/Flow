package io.github.aedev.flow.ui.components.musicplayer.motion

import androidx.compose.animation.core.Spring
import androidx.compose.ui.util.lerp
import kotlin.math.abs

internal data class MusicSheetDragFrame(
    val translationY: Float,
    val expansionFraction: Float,
)

internal fun computeMusicSheetDragFrame(
    currentTranslationY: Float,
    dragAmount: Float,
    expandedY: Float,
    collapsedY: Float,
    miniHeightPx: Float,
    initialFractionOnDragStart: Float,
    initialYOnDragStart: Float,
): MusicSheetDragFrame {
    val newY =
        (currentTranslationY + dragAmount)
            .coerceIn(
                expandedY - miniHeightPx * 0.2f,
                collapsedY + miniHeightPx * 0.2f,
            )
    val denominator = (collapsedY - expandedY).coerceAtLeast(1f)
    val dragRatio = (initialYOnDragStart - newY) / denominator
    val newFraction = (initialFractionOnDragStart + dragRatio).coerceIn(0f, 1f)
    return MusicSheetDragFrame(
        translationY = newY,
        expansionFraction = newFraction,
    )
}

internal fun resolveMusicSheetDragTarget(
    isExpanded: Boolean,
    accumulatedDragY: Float,
    minDragThresholdPx: Float,
    verticalVelocity: Float,
    velocityThreshold: Float,
    currentFraction: Float,
): Boolean =
    when {
        isExpanded && accumulatedDragY <= 0f -> true
        abs(accumulatedDragY) > minDragThresholdPx -> accumulatedDragY < 0
        abs(verticalVelocity) > velocityThreshold -> verticalVelocity < 0
        else -> currentFraction > 0.5f
    }

internal fun collapseSpringDampingForFraction(currentFraction: Float): Float =
    lerp(
        start = Spring.DampingRatioNoBouncy,
        stop = Spring.DampingRatioLowBouncy,
        fraction = currentFraction,
    )

internal fun collapseInitialSquashForFraction(currentFraction: Float): Float = lerp(1.0f, 0.97f, currentFraction)
