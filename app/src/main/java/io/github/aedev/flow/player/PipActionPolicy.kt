package io.github.aedev.flow.player

/**
 * Pure policy for the YouTube-style PiP action row.
 *
 * Visual order is always headphones -> play/pause -> next. Because the list is already ordered
 * by priority, truncating to [maxActions] automatically keeps headphones and play/pause ahead
 * of next on devices that support fewer than three actions.
 */
enum class PipAction {
    BACKGROUND_AUDIO,
    PLAY,
    PAUSE,
    NEXT,
}

object PipActionPolicy {
    const val MAX_PIP_ACTIONS = 3

    fun hasPlayableNext(
        hasQueueNext: Boolean,
        hasRelatedFallback: Boolean,
    ): Boolean = hasQueueNext || hasRelatedFallback

    fun selectActions(
        isPlaying: Boolean,
        hasNext: Boolean,
        maxActions: Int = MAX_PIP_ACTIONS,
        includeBackgroundAction: Boolean = true,
    ): List<PipAction> {
        if (maxActions <= 0) return emptyList()
        val ordered =
            buildList {
                if (includeBackgroundAction) add(PipAction.BACKGROUND_AUDIO)
                add(if (isPlaying) PipAction.PAUSE else PipAction.PLAY)
                if (hasNext) add(PipAction.NEXT)
            }
        return ordered.take(maxActions.coerceAtMost(MAX_PIP_ACTIONS))
    }
}
