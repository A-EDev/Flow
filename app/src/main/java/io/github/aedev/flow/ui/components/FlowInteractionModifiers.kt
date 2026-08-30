package io.github.aedev.flow.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

@Composable
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource,
    pressedScale: Float = FlowMotion.PRESSED_SCALE,
    reduceMotion: Boolean? = null,
): Modifier {
    val isPressed by interactionSource.collectIsPressedAsState()
    val systemReduceMotion = rememberFlowReduceMotion()
    val shouldReduceMotion = reduceMotion ?: systemReduceMotion
    val scale =
        animateFloatAsState(
            targetValue = FlowMotion.scaleFor(isPressed, shouldReduceMotion, pressedScale),
            animationSpec =
                androidx.compose.animation.core.tween(
                    durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, shouldReduceMotion),
                    easing = FlowMotion.ExitEasing,
                ),
            label = "pressScale",
        )
    return this.graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Keeps the old modifier name while shared card call sites move away from gradient overlays. */
@Composable
fun Modifier.thumbnailGradientOverlay(
    alpha: Float = 0.22f,
    startFraction: Float = 0.72f,
): Modifier {
    val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = alpha)
    return this.drawWithCache {
        val top = size.height * startFraction.coerceIn(0f, 1f)
        onDrawWithContent {
            drawContent()
            drawRect(
                color = scrimColor,
                topLeft = Offset(0f, top),
                size = Size(size.width, (size.height - top).coerceAtLeast(0f)),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberFlowSheetState(skipPartiallyExpanded: Boolean = true): SheetState =
    rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
    )
