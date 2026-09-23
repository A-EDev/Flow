package io.github.aedev.flow.ui.components.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val BarsHeight = 120.dp
private val BarGap = 4.dp
private val LabelGap = 6.dp
private val ClockMaxWidth = 240.dp
private val RingMaxWidth = 180.dp
private val RingStroke = 22.dp
private val CellGap = 4.dp
private const val CLOCK_HOURS = 24
private const val CLOCK_INNER = 0.32f
private const val CLOCK_MIN_BAR = 0.04f
private const val DAYS_PER_WEEK = 7
private const val FULL_CIRCLE = 360f
private const val RING_GAP_DEGREES = 3f
private val HeatSteps = floatArrayOf(0.3f, 0.55f, 0.8f, 1f)
private const val QUIET_BAR_ALPHA = 0.38f

/**
 * Vertical bars in a row, sized to [values] relative to the largest. [labels] sit under each bar;
 * a long series passes [edgeLabels] instead, spread across the width, so no label is squeezed. Bars are plain boxes scaled in
 * the graphics layer during the entrance, so the chart recomposes only when its data does.
 */
@Composable
fun StatBarStrip(
    values: List<Float>,
    entrance: StatEntrance,
    description: String,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    edgeLabels: List<String> = emptyList(),
    highlight: Int? = null,
) {
    val peak = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
    val bar = MaterialTheme.colorScheme.primary.copy(alpha = QUIET_BAR_ALPHA)
    val accent = MaterialTheme.colorScheme.primary
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .playsEntrance(entrance)
                .clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(LabelGap),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(BarsHeight),
            horizontalArrangement = Arrangement.spacedBy(BarGap),
            verticalAlignment = Alignment.Bottom,
        ) {
            values.forEachIndexed { index, value ->
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight((value / peak).coerceIn(CLOCK_MIN_BAR, 1f))
                            .graphicsLayer {
                                transformOrigin = TransformOrigin(0.5f, 1f)
                                scaleY = entrance.progress
                            }.background(if (index == highlight) accent else bar, MaterialTheme.shapes.small),
                )
            }
        }
        if (edgeLabels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                edgeLabels.forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(BarGap)) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Activity around a 24-hour dial, midnight at the top. The dial grows in once, then holds still. */
@Composable
fun StatClock(
    hourCounts: List<Int>,
    entrance: StatEntrance,
    description: String,
    modifier: Modifier = Modifier,
) {
    val face = MaterialTheme.colorScheme.surfaceContainerHighest
    val tick = MaterialTheme.colorScheme.primary
    val peak = hourCounts.maxOrNull()?.takeIf { it > 0 } ?: 1
    Box(
        modifier =
            modifier
                .widthIn(max = ClockMaxWidth)
                .fillMaxWidth()
                .aspectRatio(1f)
                .playsEntrance(entrance)
                .clearAndSetSemantics { contentDescription = description }
                .drawWithCache {
                    val radius = size.minDimension / 2f
                    val inner = radius * CLOCK_INNER
                    val barWidth = (2f * Math.PI.toFloat() * inner / CLOCK_HOURS) * BAR_FILL
                    val lengths = hourCounts.map { (radius - inner) * (it.toFloat() / peak).coerceAtLeast(CLOCK_MIN_BAR) }
                    onDrawBehind {
                        drawCircle(face, radius = inner)
                        scale(entrance.progress, pivot = center) {
                            lengths.forEachIndexed { hour, length ->
                                rotate(hour * FULL_CIRCLE / CLOCK_HOURS, pivot = center) {
                                    drawRoundRect(
                                        color = tick,
                                        topLeft = Offset(center.x - barWidth / 2f, center.y - inner - length),
                                        size = Size(barWidth, length),
                                        cornerRadius = CornerRadius(barWidth / 2f),
                                    )
                                }
                            }
                        }
                    }
                },
    )
}

private const val BAR_FILL = 0.62f

/** Shares of a whole as a ring, largest first. Colours come from the caller's theme roles. */
@Composable
fun StatSplitRing(
    shares: List<Pair<Float, Color>>,
    entrance: StatEntrance,
    description: String,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val total = shares.sumOf { it.first.toDouble() }.toFloat().takeIf { it > 0f } ?: 1f
    Box(
        modifier =
            modifier
                .widthIn(max = RingMaxWidth)
                .fillMaxWidth()
                .aspectRatio(1f)
                .playsEntrance(entrance)
                .clearAndSetSemantics { contentDescription = description }
                .drawWithCache {
                    val stroke = Stroke(width = RingStroke.toPx(), cap = StrokeCap.Round)
                    val inset = stroke.width / 2f
                    val arcSize = Size(size.width - stroke.width, size.height - stroke.width)
                    val sweeps = shares.filter { it.first > 0f }.map { (value, color) -> value / total * FULL_CIRCLE to color }
                    val gap = if (sweeps.size > 1) RING_GAP_DEGREES else 0f
                    onDrawBehind {
                        drawArc(track, 0f, FULL_CIRCLE, useCenter = false, topLeft = Offset(inset, inset), size = arcSize, style = stroke)
                        var start = -FULL_CIRCLE / 4f
                        sweeps.forEach { (sweep, color) ->
                            val drawn = ((sweep - gap) * entrance.progress).coerceAtLeast(0f)
                            drawArc(
                                color,
                                start + gap / 2f,
                                drawn,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = arcSize,
                                style = stroke,
                            )
                            start += sweep
                        }
                    }
                },
    )
}

/**
 * A month as a calendar, one cell per day, darker the more time that day held. Weeks start on
 * [firstDay]; the grid is drawn once per size and data and fades in once.
 */
@Composable
fun StatCalendar(
    month: YearMonth,
    dayMs: Map<LocalDate, Long>,
    entrance: StatEntrance,
    description: String,
    modifier: Modifier = Modifier,
    firstDay: DayOfWeek = DayOfWeek.MONDAY,
    locale: Locale = Locale.getDefault(),
) {
    val empty = MaterialTheme.colorScheme.surfaceContainerHighest
    val fill = MaterialTheme.colorScheme.primary
    val peak =
        dayMs
            .filterKeys { YearMonth.from(it) == month }
            .values
            .maxOrNull()
            ?.takeIf { it > 0L } ?: 1L
    val offset = (month.atDay(1).dayOfWeek.value - firstDay.value + DAYS_PER_WEEK) % DAYS_PER_WEEK
    val rows = (offset + month.lengthOfMonth() + DAYS_PER_WEEK - 1) / DAYS_PER_WEEK
    Column(
        modifier = modifier.fillMaxWidth().playsEntrance(entrance),
        verticalArrangement = Arrangement.spacedBy(LabelGap),
    ) {
        Row(Modifier.fillMaxWidth()) {
            (0 until DAYS_PER_WEEK).forEach { index ->
                Text(
                    text = firstDay.plus(index.toLong()).getDisplayName(TextStyle.NARROW, locale),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(DAYS_PER_WEEK.toFloat() / rows)
                    .graphicsLayer { alpha = entrance.progress }
                    .clearAndSetSemantics { contentDescription = description }
                    .drawWithCache {
                        val gap = CellGap.toPx()
                        val cell = (size.width - gap * (DAYS_PER_WEEK - 1)) / DAYS_PER_WEEK
                        val corner = CornerRadius(cell / 4f)
                        val cells =
                            (1..month.lengthOfMonth()).map { day ->
                                val slot = offset + day - 1
                                val ms = dayMs[month.atDay(day)] ?: 0L
                                val color = if (ms <= 0L) empty else fill.copy(alpha = heatStep(ms.toFloat() / peak))
                                Offset((slot % DAYS_PER_WEEK) * (cell + gap), (slot / DAYS_PER_WEEK) * (cell + gap)) to color
                            }
                        onDrawBehind { cells.forEach { (at, color) -> drawRoundRect(color, at, Size(cell, cell), corner) } }
                    },
        )
    }
}

private fun heatStep(share: Float): Float = HeatSteps.firstOrNull { share <= it } ?: 1f
