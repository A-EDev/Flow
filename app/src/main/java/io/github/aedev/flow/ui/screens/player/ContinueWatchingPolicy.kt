package io.github.aedev.flow.ui.screens.player

import io.github.aedev.flow.data.local.entity.WatchHistoryEntity

internal fun shouldRestoreContinueWatching(
    lastVideo: WatchHistoryEntity?,
    dismissedVideoId: String?,
    dismissedTimestamp: Long,
): Boolean {
    if (lastVideo == null) return false
    val isDismissed =
        lastVideo.videoId == dismissedVideoId &&
            lastVideo.timestamp <= dismissedTimestamp
    return !isDismissed
}
