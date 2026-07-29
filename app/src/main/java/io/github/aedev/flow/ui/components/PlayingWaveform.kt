package io.github.aedev.flow.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The "now playing" equaliser bars.
 *
 * Drawn on a single Canvas rather than a Row of Boxes with animated `height` modifiers. Height is a
 * layout property, so the previous implementations recomposed and re-laid out one node per bar on
 * every animation frame, continuously for as long as audio played — including inside list items
 * while the user scrolled. Reading the animation in the draw scope keeps each frame to a redraw.
 *
 * This replaces three near-identical copies that had drifted apart in bar count, size and timing;
 * the call sites keep their own appearance through the parameters below.
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
    staggerMillis: Int = 100
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform")
    val bars = List(barCount) { index ->
        infiniteTransition.animateFloat(
            initialValue = minBarHeight.value,
            targetValue = maxBarHeight.value,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = cycleMillis,
                    delayMillis = index * staggerMillis,
                    easing = FastOutSlowInEasing
                ),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar$index"
        )
    }

    Canvas(
        modifier = modifier.size(
            width = barWidth * barCount + barSpacing * (barCount - 1),
            height = maxBarHeight
        )
    ) {
        val barWidthPx = barWidth.toPx()
        val spacingPx = barSpacing.toPx()
        // Matches the RoundedCornerShape(barWidth / 2) the Box versions used.
        val cornerRadius = CornerRadius(barWidthPx / 2f)
        bars.forEachIndexed { index, bar ->
            val barHeightPx = bar.value.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    x = index * (barWidthPx + spacingPx),
                    y = (size.height - barHeightPx) / 2f
                ),
                size = Size(barWidthPx, barHeightPx),
                cornerRadius = cornerRadius
            )
        }
    }
}
