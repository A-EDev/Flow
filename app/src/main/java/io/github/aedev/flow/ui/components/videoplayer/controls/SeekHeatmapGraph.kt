package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.innertube.models.response.HeatmapMarker
import io.github.aedev.flow.innertube.models.response.VideoHeatmap
import io.github.aedev.flow.ui.theme.PlayerHeatmapCurve
import io.github.aedev.flow.ui.theme.PlayerHeatmapCurveEdge
import io.github.aedev.flow.ui.theme.PlayerHeatmapCurvePeak

/** Height of the rewatch curve above the bar. YouTube's own spec tops out at 40dp; 28 suits a phone. */
internal val SeekHeatmapHeight: Dp = 28.dp

/**
 * The rewatch curve: how often each moment of the video gets replayed.
 *
 * Drawn only while a scrub is in flight, which is both what YouTube does and what keeps a
 * hundred-point path off the frame clock during ordinary playback.
 *
 * The peak stretch YouTube labels "Most replayed" is filled more strongly than the rest so it reads
 * at a glance without needing its own marker on an already busy bar.
 */
@Composable
internal fun SeekHeatmapGraph(
    heatmap: VideoHeatmap,
    durationMs: Long,
    modifier: Modifier = Modifier,
    curveColor: Color = PlayerHeatmapCurve,
    peakColor: Color = PlayerHeatmapCurvePeak,
    edgeColor: Color = PlayerHeatmapCurveEdge,
) {
    if (heatmap.isEmpty || durationMs <= 0L) return
    val markers = heatmap.markers
    val highlight = remember(heatmap) { heatmap.highlights.maxByOrNull { it.endMs - it.startMs } }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(SeekHeatmapHeight)
                .padding(bottom = SeekHeatmapGap),
    ) {
        val curve = buildCurve(markers, durationMs, size)
        drawPath(path = curve, color = curveColor)
        // The fill alone disappears over bright footage, so the silhouette is what carries the
        // shape; the highlighted stretch is filled harder instead of getting a marker of its own.
        drawPath(path = curve, color = edgeColor, style = Stroke(width = EDGE_STROKE_PX))
        val left = highlight?.let { (it.startMs.toFloat() / durationMs).coerceIn(0f, 1f) * size.width }
        val right = highlight?.let { (it.endMs.toFloat() / durationMs).coerceIn(0f, 1f) * size.width }
        if (left == null || right == null || right <= left) return@Canvas
        clipRect(left = left, right = right) {
            drawPath(path = curve, color = peakColor)
        }
    }
}

/**
 * A closed area under the curve, smoothed.
 *
 * Joined with quadratic segments through the midpoints between samples rather than straight lines:
 * a hundred points across a phone-width bar puts several per pixel column, and drawing those
 * literally gives a jagged comb instead of a curve.
 *
 * Every sample is floored at [MIN_INTENSITY] — YouTube's own spec keeps a 4dp sliver against a 40dp
 * peak — so a quiet stretch still reads as part of the graph rather than a gap in it.
 */
private fun buildCurve(
    markers: List<HeatmapMarker>,
    durationMs: Long,
    size: Size,
): Path {
    fun xOf(marker: HeatmapMarker) = (marker.startMs.toFloat() / durationMs).coerceIn(0f, 1f) * size.width

    fun yOf(marker: HeatmapMarker) = size.height - marker.intensity.coerceIn(MIN_INTENSITY, 1f) * size.height

    val path = Path()
    path.moveTo(0f, size.height)
    path.lineTo(0f, yOf(markers.first()))
    for (index in 1 until markers.size) {
        val previous = markers[index - 1]
        val current = markers[index]
        path.quadraticTo(
            x1 = xOf(previous),
            y1 = yOf(previous),
            x2 = (xOf(previous) + xOf(current)) / 2f,
            y2 = (yOf(previous) + yOf(current)) / 2f,
        )
    }
    path.lineTo(size.width, yOf(markers.last()))
    path.lineTo(size.width, size.height)
    path.close()
    return path
}

private const val MIN_INTENSITY = 0.10f
private const val EDGE_STROKE_PX = 2f

/** Keeps the curve off the bar so the two do not read as one thick band. */
private val SeekHeatmapGap = 3.dp

/** The label for the stretch [positionMs] falls in, or null when it is not in a labelled one. */
internal fun VideoHeatmap.highlightLabelAt(positionMs: Long): String? = highlights.firstOrNull { positionMs in it.startMs..it.endMs }?.label
