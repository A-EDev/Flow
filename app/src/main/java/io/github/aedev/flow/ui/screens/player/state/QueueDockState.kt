package io.github.aedev.flow.ui.screens.player.state

import io.github.aedev.flow.data.model.Video

/** A named queue shows from its first video; an unnamed one only once something follows the current video. */
internal fun hasVisibleQueue(
    queueTitle: String?,
    queueSize: Int,
): Boolean = if (queueTitle != null) queueSize > 0 else queueSize > 1

/** What plays after [currentIndex]: the next video, the first again when the queue loops, or null at the end. */
internal fun nextQueueVideo(
    queue: List<Video>,
    currentIndex: Int,
    isLooping: Boolean,
): Video? =
    when {
        currentIndex < queue.lastIndex -> queue.getOrNull(currentIndex + 1)
        isLooping -> queue.firstOrNull()
        else -> null
    }
