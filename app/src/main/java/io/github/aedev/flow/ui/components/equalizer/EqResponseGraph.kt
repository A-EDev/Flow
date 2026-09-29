package io.github.aedev.flow.ui.components.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.audio.eq.EqBand
import io.github.aedev.flow.data.audio.eq.EqFilterMath
import io.github.aedev.flow.data.audio.eq.EqFilterType
import io.github.aedev.flow.data.audio.eq.EqLimits
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val CURVE_POINTS = 128
private const val NARROW_RANGE_DB = 12.0
private const val AREA_ALPHA = 0.16f
private val GRID_FREQUENCIES = listOf(100.0, 1_000.0, 10_000.0)
private val InnerPadding = 12.dp
private val PointRadius = 12.dp
private val PointTouchRadius = 24.dp
private val CurveWidth = 2.5.dp
private val PointStroke = 2.dp
private val DashOn = 4.dp
private val DashOff = 4.dp

/**
 * The equalizer's frequency response on a log axis from 20 Hz to 20 kHz, drawn from the same
 * coefficients the audio runs.
 *
 * When [editable], a point drags sideways for frequency and up or down for gain, a tap on a point
 * opens it and a tap on empty space adds a band there. While a point moves, only this canvas redraws:
 * the dragged band lives in draw-phase state and reaches the sound through [onBandPreview], and the
 * rest of the page hears about it once, from [onBandCommit].
 */
@Composable
internal fun EqResponseGraph(
    bands: List<EqBand>,
    modifier: Modifier = Modifier,
    bassBoost: Double = 0.0,
    selectedIndex: Int? = null,
    showPoints: Boolean = true,
    showLabels: Boolean = true,
    editable: Boolean = false,
    onBandClick: (Int) -> Unit = {},
    onAddBand: (frequency: Double, gain: Double) -> Unit = { _, _ -> },
    onBandPreview: (Int, EqBand) -> Unit = { _, _ -> },
    onBandCommit: (Int, EqBand) -> Unit = { _, _ -> },
) {
    val colors = MaterialTheme.colorScheme
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val pointLabelStyle = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
    val shape = MaterialTheme.shapes.large
    val measurer = rememberTextMeasurer()
    val haptics = LocalHapticFeedback.current
    val description = stringResource(R.string.eq_graph_description, bands.size)

    val currentBands by rememberUpdatedState(bands)
    var draft by remember { mutableStateOf<Pair<Int, EqBand>?>(null) }
    val rangeDb =
        if (bands.any { abs(it.gain) > NARROW_RANGE_DB } || bassBoost > NARROW_RANGE_DB) EqLimits.MAX_GAIN else NARROW_RANGE_DB

    Canvas(
        modifier =
            modifier
                .clip(shape)
                .background(colors.surfaceContainerHigh, shape)
                .semantics { contentDescription = description }
                .pointerInput(editable, rangeDb) {
                    if (!editable) return@pointerInput
                    val geometry = { GraphGeometry(size.width.toFloat(), size.height.toFloat(), InnerPadding.toPx(), rangeDb) }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val g = geometry()
                        val hit = nearestPoint(currentBands, g, down.position, PointTouchRadius.toPx())
                        if (hit == null) {
                            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                            if (currentBands.size < EqLimits.MAX_BANDS &&
                                (up.position - down.position).getDistance() < viewConfiguration.touchSlop
                            ) {
                                onAddBand(g.frequencyAt(up.position.x), g.gainAt(up.position.y))
                            }
                            return@awaitEachGesture
                        }
                        down.consume()
                        val start = currentBands[hit]
                        val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                        if (slop == null) {
                            onBandClick(hit)
                            return@awaitEachGesture
                        }
                        var band = start

                        fun follow(position: Offset) {
                            val moved =
                                start.copy(
                                    frequency = g.frequencyAt(position.x).roundToInt().toDouble(),
                                    gain = if (start.type.hasGain) g.gainAt(position.y) else start.gain,
                                )
                            if (start.type.hasGain && (moved.gain > 0.0) != (band.gain > 0.0)) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            }
                            band = moved
                            draft = hit to moved
                            onBandPreview(hit, moved)
                        }
                        follow(slop.position)
                        drag(slop.id) { change ->
                            change.consume()
                            follow(change.position)
                        }
                        onBandCommit(hit, band)
                        draft = null
                    }
                },
    ) {
        val g = GraphGeometry(size.width, size.height, InnerPadding.toPx(), rangeDb)
        val live = draft?.let { (index, band) -> bands.toMutableList().also { if (index in it.indices) it[index] = band } } ?: bands

        GRID_FREQUENCIES.forEach { frequency ->
            val x = g.x(frequency)
            drawLine(colors.outlineVariant, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
        }
        drawLine(
            colors.outline,
            Offset(0f, g.y(0.0)),
            Offset(size.width, g.y(0.0)),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx())),
        )
        if (showLabels) {
            GRID_FREQUENCIES.forEach { frequency ->
                val layout = measurer.measure(formatFrequencyShort(frequency), labelStyle)
                drawText(layout, topLeft = Offset(g.x(frequency) + 4.dp.toPx(), size.height - layout.size.height - 4.dp.toPx()))
            }
            val zero = measurer.measure("0", labelStyle)
            drawText(zero, topLeft = Offset(6.dp.toPx(), g.y(0.0) - zero.size.height - 2.dp.toPx()))
            val top = measurer.measure(signedNumber(rangeDb, 0), labelStyle)
            drawText(top, topLeft = Offset(6.dp.toPx(), 4.dp.toPx()))
        }

        val curve = Path()
        for (i in 0..CURVE_POINTS) {
            val frequency = EqFilterMath.frequencyAt(i.toDouble() / CURVE_POINTS)
            val x = g.x(frequency)
            val y = g.y(EqFilterMath.magnitudeDb(live, frequency))
            if (i == 0) curve.moveTo(x, y) else curve.lineTo(x, y)
        }
        val area =
            Path().apply {
                addPath(curve)
                lineTo(g.x(EqLimits.MAX_FREQUENCY), g.y(0.0))
                lineTo(g.x(EqLimits.MIN_FREQUENCY), g.y(0.0))
                close()
            }
        drawPath(area, colors.primary.copy(alpha = AREA_ALPHA))

        if (bassBoost > 0.0) drawBassBoost(g, bassBoost, colors.onSurfaceVariant)

        drawPath(curve, colors.primary, style = Stroke(width = CurveWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        if (showPoints) {
            live.forEachIndexed { index, band ->
                val center = Offset(g.x(band.frequency), g.y(EqFilterMath.magnitudeDb(live, band.frequency)))
                val selected = index == selectedIndex || draft?.first == index
                drawCircle(if (selected) colors.primary else colors.surfaceContainerHighest, PointRadius.toPx(), center)
                drawCircle(
                    color = if (band.enabled) colors.primary else colors.outline,
                    radius = PointRadius.toPx(),
                    center = center,
                    style =
                        Stroke(
                            width = PointStroke.toPx(),
                            pathEffect =
                                if (band.enabled) {
                                    null
                                } else {
                                    PathEffect.dashPathEffect(
                                        floatArrayOf(
                                            DashOn.toPx() / 2,
                                            DashOff.toPx() / 2,
                                        ),
                                    )
                                },
                        ),
                )
                val label =
                    measurer.measure(
                        (index + 1).toString(),
                        pointLabelStyle.copy(
                            color =
                                when {
                                    selected -> colors.onPrimary
                                    band.enabled -> colors.primary
                                    else -> colors.onSurfaceVariant
                                },
                        ),
                    )
                drawText(label, topLeft = center - Offset(label.size.width / 2f, label.size.height / 2f))
            }
        }
    }
}

private fun DrawScope.drawBassBoost(
    g: GraphGeometry,
    boost: Double,
    color: Color,
) {
    val shelf = listOf(EqBand(EqLimits.BASS_BOOST_FREQUENCY, boost, EqLimits.DEFAULT_SHELF_Q, EqFilterType.LOW_SHELF))
    val path = Path()
    for (i in 0..CURVE_POINTS / 2) {
        val frequency = EqFilterMath.frequencyAt(i.toDouble() / CURVE_POINTS)
        val x = g.x(frequency)
        val y = g.y(EqFilterMath.magnitudeDb(shelf, frequency))
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(
        path,
        color,
        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(DashOn.toPx(), DashOff.toPx()))),
    )
}

private fun nearestPoint(
    bands: List<EqBand>,
    g: GraphGeometry,
    position: Offset,
    maxDistance: Float,
): Int? {
    var best: Int? = null
    var bestDistance = maxDistance
    bands.forEachIndexed { index, band ->
        val x = g.x(band.frequency)
        val y = g.y(EqFilterMath.magnitudeDb(bands, band.frequency))
        val distance = hypot(position.x - x, position.y - y)
        if (distance <= bestDistance) {
            bestDistance = distance
            best = index
        }
    }
    return best
}

/** Pixel mapping for a graph of [width] by [height]: log frequency across, dB up and down. */
private class GraphGeometry(
    val width: Float,
    val height: Float,
    val padding: Float,
    val rangeDb: Double,
) {
    fun x(frequency: Double): Float = padding + (EqFilterMath.fractionOf(frequency) * (width - 2 * padding)).toFloat()

    fun y(db: Double): Float = (height / 2f - (db.coerceIn(-rangeDb, rangeDb) / rangeDb) * (height / 2f - padding)).toFloat()

    fun frequencyAt(x: Float): Double = EqFilterMath.frequencyAt(((x - padding) / (width - 2 * padding)).toDouble())

    fun gainAt(y: Float): Double {
        val db = (height / 2f - y) / (height / 2f - padding) * rangeDb
        return (db.coerceIn(-rangeDb, rangeDb) * 10).roundToInt() / 10.0
    }
}
