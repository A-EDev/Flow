package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.innertube.models.response.HeatmapMarker
import io.github.aedev.flow.innertube.models.response.VideoHeatmap

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
    color: Color = Color.White,
) {
    if (heatmap.isEmpty || durationMs <= 0L) return
    val markers = heatmap.markers
    val highlight = remember(heatmap) { heatmap.highlights.maxByOrNull { it.endMs - it.startMs } }

    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(SeekHeatmapHeight),
    ) {
        val curve = buildCurve(markers, durationMs, size)
        drawPath(path = curve, color = color.copy(alpha = BASE_ALPHA))
        if (highlight == null) return@Canvas
        val left = (highlight.startMs.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
        val right = (highlight.endMs.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
        if (right <= left) return@Canvas
        clipRect(left = left, right = right) {
            drawPath(path = curve, color = color.copy(alpha = HIGHLIGHT_ALPHA))
        }
    }
}

/**
 * A closed area under the curve.
 *
 * Each marker contributes the point at its own start, and the last one is carried to the far edge
 * so the fill reaches the end of the bar rather than stopping a slice short.
 */
private fun buildCurve(
    markers: List<HeatmapMarker>,
    durationMs: Long,
    size: Size,
): Path {
    val path = Path()
    path.moveTo(0f, size.height)
    markers.forEach { marker ->
        val x = (marker.startMs.toFloat() / durationMs).coerceIn(0f, 1f) * size.width
        val y = size.height - marker.intensity.coerceIn(0f, 1f) * size.height
        path.lineTo(x, y)
    }
    val last = markers.last()
    path.lineTo(size.width, size.height - last.intensity.coerceIn(0f, 1f) * size.height)
    path.lineTo(size.width, size.height)
    path.close()
    return path
}

private const val BASE_ALPHA = 0.30f
private const val HIGHLIGHT_ALPHA = 0.85f

/** The label for the stretch [positionMs] falls in, or null when it is not in a labelled one. */
internal fun VideoHeatmap.highlightLabelAt(positionMs: Long): String? = highlights.firstOrNull { positionMs in it.startMs..it.endMs }?.label
