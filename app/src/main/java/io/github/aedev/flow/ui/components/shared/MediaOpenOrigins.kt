package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.LayoutBoundsHolder
import androidx.compose.ui.layout.layoutBounds
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/** Where a video's thumbnail sits in the window, so the player can grow out of it. */
@Immutable
data class MediaOpenOrigin(
    val windowBounds: Rect,
    val cornerRadiusPx: Float,
)

/**
 * The video thumbnails currently on screen, by video id. Opening a video asks for the one the user
 * can see best and starts the player there. Bounds are read only when asked, so nothing runs while
 * a list scrolls.
 */
class MediaOpenOrigins {
    private class Entry(
        val holder: LayoutBoundsHolder,
        val shape: Shape,
        val density: Density,
    )

    private val entries = HashMap<String, MutableList<Entry>>()

    fun originFor(videoId: String): MediaOpenOrigin? {
        var best: Entry? = null
        var bestVisible = MIN_VISIBLE_FRACTION
        entries[videoId]?.forEach { entry ->
            val visible = entry.holder.bounds?.fractionVisibleInWindow() ?: return@forEach
            if (visible >= bestVisible) {
                best = entry
                bestVisible = visible
            }
        }
        val entry = best ?: return null
        val bounds = entry.holder.bounds?.boundsInWindow ?: return null
        val rect = Rect(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
        val radius = (entry.shape as? CornerBasedShape)?.topStart?.toPx(rect.size, entry.density) ?: 0f
        return MediaOpenOrigin(windowBounds = rect, cornerRadiusPx = radius.coerceAtMost(rect.minDimension / 2f))
    }

    internal fun register(
        videoId: String,
        holder: LayoutBoundsHolder,
        shape: Shape,
        density: Density,
    ): () -> Unit {
        val entry = Entry(holder, shape, density)
        entries.getOrPut(videoId) { mutableListOf() }.add(entry)
        return {
            entries[videoId]?.let { list ->
                list.remove(entry)
                if (list.isEmpty()) entries.remove(videoId)
            }
        }
    }

    private companion object {
        const val MIN_VISIBLE_FRACTION = 0.5f
    }
}

val LocalMediaOpenOrigins = staticCompositionLocalOf<MediaOpenOrigins?> { null }

/** Registers this thumbnail as where [videoId] opens from while it is in the composition. */
@Composable
internal fun Modifier.mediaOpenOrigin(
    videoId: String,
    shape: Shape,
): Modifier {
    val origins = LocalMediaOpenOrigins.current ?: return this
    val density = LocalDensity.current
    val holder = remember { LayoutBoundsHolder() }
    DisposableEffect(origins, videoId, shape, density) {
        val unregister = origins.register(videoId, holder, shape, density)
        onDispose(unregister)
    }
    return layoutBounds(holder)
}
