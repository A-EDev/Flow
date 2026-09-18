package io.github.aedev.flow.ui.components.videoplayer.controls

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.layout
import coil3.compose.rememberAsyncImagePainter
import kotlin.math.roundToInt

internal fun scrubThumbFraction(
    positionMs: Long,
    durationMs: Long,
): Float = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

/**
 * Centres a child over the seek thumb, clamped to the track.
 *
 * The node itself spans the track and only the child moves, so nothing above the bar changes width
 * as the thumb travels. The position is read while placing rather than in composition: a scrub tick
 * then re-places the child without rebuilding anything.
 */
internal fun Modifier.anchoredToThumb(
    positionProvider: () -> Long,
    durationMs: Long,
): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints.copy(minWidth = 0))
        layout(constraints.maxWidth, placeable.height) {
            val thumbX = scrubThumbFraction(positionProvider(), durationMs) * constraints.maxWidth
            val limit = (constraints.maxWidth - placeable.width).coerceAtLeast(0)
            placeable.place(x = (thumbX - placeable.width / 2f).roundToInt().coerceIn(0, limit), y = 0)
        }
    }

/**
 * Painters for the storyboard sheets currently on screen, kept across the frames that share one.
 *
 * A sprite sheet holds 25-100 frames, so a scrub stays on the same one for tens of seconds. Keying
 * by URL means the painter — and the bitmap behind it — survives every frame change that does not
 * cross a sheet boundary.
 */
@Composable
internal fun rememberStoryboardSheets(urls: List<String>): StoryboardSheets =
    StoryboardSheets(urls, urls.map { url -> key(url) { rememberAsyncImagePainter(url) } })

internal class StoryboardSheets(
    private val urls: List<String>,
    private val painters: List<Painter>,
) {
    fun painterFor(url: String): Painter? = painters.getOrNull(urls.indexOf(url))
}
