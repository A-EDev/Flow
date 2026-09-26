package io.github.aedev.flow.ui.screens.player.state

import io.github.aedev.flow.data.model.Video

/** Where the player shows what plays next: floating under the details, atop the side pane, or not at all. */
internal enum class QueueDockPlacement {
    FLOATING,
    IN_PANE,
    HIDDEN,
}

internal fun queueDockPlacement(
    hasQueue: Boolean,
    layoutMode: PlayerLayoutMode,
): QueueDockPlacement =
    when {
        !hasQueue -> QueueDockPlacement.HIDDEN
        layoutMode == PlayerLayoutMode.WIDE -> QueueDockPlacement.IN_PANE
        else -> QueueDockPlacement.FLOATING
    }

/** A named queue shows from its first video; an unnamed one only once something follows the current video. */
internal fun hasVisibleQueue(
    queueTitle: String?,
    queueSize: Int,
): Boolean = if (queueTitle != null) queueSize > 0 else queueSize > 1

/**
 * What plays after [currentIndex], with its index: the next video, the first again when the queue
 * loops, or null at the end.
 */
internal fun nextQueueVideo(
    queue: List<Video>,
    currentIndex: Int,
    isLooping: Boolean,
): IndexedValue<Video>? {
    val index =
        when {
            currentIndex < queue.lastIndex -> currentIndex + 1
            isLooping -> 0
            else -> return null
        }
    return queue.getOrNull(index)?.let { IndexedValue(index, it) }
}
