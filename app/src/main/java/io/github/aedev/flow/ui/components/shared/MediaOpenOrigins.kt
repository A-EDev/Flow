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
import coil3.memory.MemoryCache

/** Where a video's thumbnail sits in the window, so the player can grow out of it. */
@Immutable
data class MediaOpenOrigin(
    val windowBounds: Rect,
    val cornerRadiusPx: Float,
    val imageKey: MemoryCache.Key?,
)

/** One thumbnail's entry: where it is laid out and which cached image it drew. */
class MediaOpenOriginRegistration internal constructor() {
    internal val holder = LayoutBoundsHolder()
    var imageKey: MemoryCache.Key? = null
}

/**
 * The video thumbnails currently on screen, by video id. Opening a video asks for the one the user
 * can see best and starts the player there. Bounds are read only when asked, so nothing runs while
 * a list scrolls.
 */
class MediaOpenOrigins {
    private class Entry(
        val registration: MediaOpenOriginRegistration,
        val shape: Shape,
        val density: Density,
    ) {
        val holder: LayoutBoundsHolder get() = registration.holder
    }

    private val entries = HashMap<String, MutableList<Entry>>()
    private var held: Pair<String, MediaOpenOrigin>? = null

    /**
     * Keeps where [videoId]'s thumbnail is now for the next [originFor], for a surface that leaves
     * the screen in the same moment it opens the player, as the background bar does.
     */
    fun holdOriginFor(videoId: String) {
        held = visibleOriginFor(videoId)?.let { videoId to it }
    }

    fun originFor(videoId: String): MediaOpenOrigin? {
        val kept = held
        held = null
        if (kept != null && kept.first == videoId) return kept.second
        return visibleOriginFor(videoId)
    }

    private fun visibleOriginFor(videoId: String): MediaOpenOrigin? {
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
        return MediaOpenOrigin(
            windowBounds = rect,
            cornerRadiusPx = radius.coerceAtMost(rect.minDimension / 2f),
            imageKey = entry.registration.imageKey,
        )
    }

    internal fun register(
        videoId: String,
        registration: MediaOpenOriginRegistration,
        shape: Shape,
        density: Density,
    ): () -> Unit {
        val entry = Entry(registration, shape, density)
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

/**
 * Registers a thumbnail as where [videoId] opens from while it is in the composition, or null when no
 * registry is installed. Its [MediaOpenOriginRegistration.holder] goes on the thumbnail through
 * [mediaOpenOrigin].
 */
@Composable
internal fun rememberMediaOpenOrigin(
    videoId: String,
    shape: Shape,
): MediaOpenOriginRegistration? {
    val origins = LocalMediaOpenOrigins.current ?: return null
    val density = LocalDensity.current
    val registration = remember { MediaOpenOriginRegistration() }
    DisposableEffect(origins, videoId, shape, density) {
        val unregister = origins.register(videoId, registration, shape, density)
        onDispose(unregister)
    }
    return registration
}

internal fun Modifier.mediaOpenOrigin(registration: MediaOpenOriginRegistration?): Modifier =
    if (registration == null) this else layoutBounds(registration.holder)
