package io.github.aedev.flow.player

/**
 * Decides whether a playlist change opens a new radio session or continues the current one.
 *
 * The queue alone cannot tell the two apart: an in-queue skip and an explicit "Start radio" both
 * arrive as PLAYLIST_CHANGED, and a radio seeded from the playing track always looks like the
 * session it is meant to replace. An explicit request therefore carries its own seed.
 */
internal object MusicRadioPlanner {
    data class QueueContext(
        val reseed: Boolean,
        val explicit: Boolean,
        val knownIds: List<String>,
    )

    fun resolveQueueContext(
        currentId: String,
        queueIds: List<String>,
        previousIds: List<String>?,
        explicitSeedId: String?,
    ): QueueContext {
        if (explicitSeedId == currentId) {
            return QueueContext(reseed = true, explicit = true, knownIds = queueIds)
        }

        val previous =
            previousIds
                ?: return QueueContext(reseed = true, explicit = false, knownIds = queueIds)

        // Same session when the track was already part of the previous queue: skips and queue
        // jumps rebuild the playlist (sometimes with a pruned list), but the user never left
        // their queue — only a track from OUTSIDE it reseeds.
        val sameContext = previous == queueIds || currentId in previous
        if (!sameContext) {
            return QueueContext(reseed = true, explicit = false, knownIds = queueIds)
        }

        // A pruned rebuild (stale mirror) must not shrink the known context.
        val knownIds = if (queueIds.size < previous.size) (previous + queueIds).distinct() else queueIds
        return QueueContext(reseed = false, explicit = false, knownIds = knownIds)
    }
}
