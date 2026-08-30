package io.github.aedev.flow.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

/**
 * Draws the now-playing equalizer on one Canvas so each animation frame stays in the draw phase.
 * Reduced-motion mode uses a static bar pattern and does not start an infinite transition.
 */
@Composable
fun PlayingWaveform(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    barCount: Int = 4,
    barWidth: Dp = 3.dp,
    barSpacing: Dp = 2.dp,
    minBarHeight: Dp = 6.dp,
    maxBarHeight: Dp = 16.dp,
    cycleMillis: Int = 350,
    staggerMillis: Int = 100,
) {
    val safeBarCount = barCount.coerceAtLeast(1)
    val reduceMotion = rememberFlowReduceMotion()

    if (reduceMotion) {
        Canvas(
            modifier =
                modifier.size(
                    width = barWidth * safeBarCount + barSpacing * (safeBarCount - 1),
                    height = maxBarHeight,
                ),
        ) {
            drawWaveformBars(
                color = color,
                barCount = safeBarCount,
                barWidth = barWidth,
                barSpacing = barSpacing,
                heightPxAt = { index ->
                    val fraction = STATIC_BAR_FRACTIONS[index % STATIC_BAR_FRACTIONS.size]
                    lerp(minBarHeight.value, maxBarHeight.value, fraction).dp.toPx()
                },
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val bars: List<State<Float>> =
        List(safeBarCount) { index ->
            infiniteTransition.animateFloat(
                initialValue = minBarHeight.value,
                targetValue = maxBarHeight.value,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(
                                durationMillis = cycleMillis,
                                delayMillis = index * staggerMillis,
                                easing = FlowMotion.EnterEasing,
                            ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                label = "bar$index",
            )
        }

    Canvas(
        modifier =
            modifier.size(
                width = barWidth * safeBarCount + barSpacing * (safeBarCount - 1),
                height = maxBarHeight,
            ),
    ) {
        drawWaveformBars(
            color = color,
            barCount = safeBarCount,
            barWidth = barWidth,
            barSpacing = barSpacing,
            heightPxAt = { index -> bars[index].value.dp.toPx() },
        )
    }
}

private fun DrawScope.drawWaveformBars(
    color: Color,
    barCount: Int,
    barWidth: Dp,
    barSpacing: Dp,
    heightPxAt: (Int) -> Float,
) {
    val barWidthPx = barWidth.toPx()
    val spacingPx = barSpacing.toPx()
    val cornerRadius = CornerRadius(barWidthPx / 2f)

    repeat(barCount) { index ->
        val barHeightPx = heightPxAt(index)
        drawRoundRect(
            color = color,
            topLeft =
                Offset(
                    x = index * (barWidthPx + spacingPx),
                    y = (size.height - barHeightPx) / 2f,
                ),
            size = Size(barWidthPx, barHeightPx),
            cornerRadius = cornerRadius,
        )
    }
}

private val STATIC_BAR_FRACTIONS = floatArrayOf(0.35f, 0.75f, 1f, 0.55f)
