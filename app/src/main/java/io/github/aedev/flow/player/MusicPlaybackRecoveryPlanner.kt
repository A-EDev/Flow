package io.github.aedev.flow.player

/**
 * Works out which playlist item a playback error belongs to.
 *
 * The failing item is not always the one playing: a next-item preload can fail while the previous
 * track is still audible, and the player then stops on the older item. Recovering against whatever
 * happens to be current at that moment re-prepares the wrong track — it replays the song that just
 * played and never retries the one that broke.
 */
internal object MusicPlaybackRecoveryPlanner {
    data class FailedItem(
        val index: Int,
        val mediaId: String,
        val resumePositionMs: Long,
    )

    fun resolveFailedItem(
        errorWindowIndex: Int,
        currentIndex: Int,
        currentPositionMs: Long,
        mediaIds: List<String>,
    ): FailedItem? {
        val index =
            errorWindowIndex.takeIf { it in mediaIds.indices }
                ?: currentIndex.takeIf { it in mediaIds.indices }
                ?: return null

        // Only the track that was actually playing has a position worth resuming from; anything
        // else failed before it ever started.
        val resumePositionMs = if (index == currentIndex) currentPositionMs.coerceAtLeast(0L) else 0L
        return FailedItem(index = index, mediaId = mediaIds[index], resumePositionMs = resumePositionMs)
    }
}

/**
 * Songs that failed in a row. A queue YouTube will not serve here (a country block, a dead network)
 * would otherwise be skipped through song by song, each skip walking the whole client ladder again,
 * and with Repeat all it never ends (#1378). Removed songs still skip, but only for one pass.
 */
internal class MusicFailureStreak {
    private var unplayableInARow = 0
    private var failedInARow = 0

    /** Records a failed song: true to skip to the next one, false to stop on it. */
    fun skipAfter(
        gone: Boolean,
        queueSize: Int,
    ): Boolean {
        failedInARow++
        if (!gone) unplayableInARow++
        return unplayableInARow < MAX_UNPLAYABLE_IN_A_ROW && failedInARow < queueSize
    }

    fun reset() {
        unplayableInARow = 0
        failedInARow = 0
    }

    companion object {
        const val MAX_UNPLAYABLE_IN_A_ROW = 3
    }
}
