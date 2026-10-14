package io.github.aedev.flow.ui.components.shared

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val DefaultGap = 4.dp

/**
 * A row of [count] rounded segments, each filled by [fill] (0..1) and as wide as its share of
 * [weight]. Both lambdas are read while drawing, so a moving fill or width never recomposes.
 */
fun Modifier.drawSegmentedProgress(
    count: Int,
    color: Color,
    trackColor: Color,
    gap: Dp = DefaultGap,
    weight: (Int) -> Float = { 1f },
    fill: (Int) -> Float,
): Modifier =
    drawBehind {
        if (count <= 0) return@drawBehind
        val gapPx = gap.toPx()
        val available = size.width - gapPx * (count - 1)
        var totalWeight = 0f
        repeat(count) { totalWeight += weight(it) }
        val radius = CornerRadius(size.height / 2f)
        var x = 0f
        repeat(count) { index ->
            val width = available * weight(index) / totalWeight
            drawRoundRect(trackColor, Offset(x, 0f), Size(width, size.height), radius)
            val filled = fill(index).coerceIn(0f, 1f)
            if (filled > 0f) drawRoundRect(color, Offset(x, 0f), Size(width * filled, size.height), radius)
            x += width + gapPx
        }
    }
