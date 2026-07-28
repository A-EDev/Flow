package io.github.aedev.flow.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Adds a subtle press-scale animation to any composable.
 * When the user presses down, the element shrinks to [pressedScale] with a bouncy spring,
 * giving tactile depth feedback without any layout shift.
 *
 * Usage: Modifier.pressScale(interactionSource)
 * The interactionSource should be the same one passed to clickable/combinedClickable.
 *
 * Composable rather than `composed {}`: the latter is opaque to Modifier equality, so every card
 * in a list re-materialised its whole chain on each recomposition. The spring is read inside the
 * [graphicsLayer] block instead of the composition, which keeps its per-frame updates off the
 * composition and layout phases entirely — only the layer block re-runs.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = 0.97f
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "pressScale"
    )
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/**
 * Scrim over the bottom of a thumbnail so the duration pill stays legible on light frames.
 *
 * Built in [drawWithCache] because the brush depends only on the layout size: created per draw it
 * allocated a Brush and its colour list for every visible card on every frame of a scroll.
 */
fun Modifier.thumbnailGradientOverlay(
    color: Color = Color.Black,
    alpha: Float = 0.25f,
    startFraction: Float = 0.6f
): Modifier = this.drawWithCache {
    val brush = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            color.copy(alpha = alpha)
        ),
        startY = size.height * startFraction,
        endY = size.height
    )
    onDrawWithContent {
        drawContent()
        drawRect(brush = brush)
    }
}

/**
 * Returns a [SheetState] configured for a smooth, polished bottom sheet experience:
 * - [skipPartiallyExpanded] defaults to true, so sheets always open fully without
 *   stopping at the awkward half-expanded state.
 *
 * Usage: `sheetState = rememberFlowSheetState()`
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFlowSheetState(
    skipPartiallyExpanded: Boolean = true
): SheetState = rememberModalBottomSheetState(
    skipPartiallyExpanded = skipPartiallyExpanded
)
