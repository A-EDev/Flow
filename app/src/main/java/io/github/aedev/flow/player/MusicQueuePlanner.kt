package io.github.aedev.flow.player

internal object MusicQueuePlanner {
    const val INDEX_UNSET = -1

    fun currentQueueIndex(
        queueIds: List<String>,
        playerIndex: Int,
        currentTrackId: String?
    ): Int {
        if (playerIndex in queueIds.indices && queueIds[playerIndex] == currentTrackId) {
            return playerIndex
        }

        if (currentTrackId == null) return INDEX_UNSET

        return queueIds.indexOf(currentTrackId)
    }

    fun playNextInsertionIndex(
        queueIds: List<String>,
        playerIndex: Int,
        currentTrackId: String?
    ): Int {
        val currentIndex = currentQueueIndex(queueIds, playerIndex, currentTrackId)
        return if (currentIndex == INDEX_UNSET) {
            queueIds.size
        } else {
            (currentIndex + 1).coerceIn(0, queueIds.size)
        }
    }

    fun shouldForcePendingPlayNext(
        isAutomaticTransition: Boolean,
        pendingMediaId: String?,
        pendingPlayerIndex: Int,
        actualMediaId: String?,
        actualPlayerIndex: Int
    ): Boolean {
        val pendingIndexMissed = pendingPlayerIndex != INDEX_UNSET && pendingPlayerIndex != actualPlayerIndex
        return isAutomaticTransition &&
            pendingMediaId != null &&
            actualMediaId != null &&
            (actualMediaId != pendingMediaId || pendingIndexMissed)
    }
}
