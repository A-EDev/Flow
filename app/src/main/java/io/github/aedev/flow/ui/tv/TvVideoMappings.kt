package io.github.aedev.flow.ui.tv

import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video

internal fun VideoHistoryEntry.toTvVideo(): Video = Video(
    id = videoId,
    title = title,
    channelName = channelName,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
    duration = (duration / 1_000L).toInt(),
    viewCount = 0L,
    uploadDate = "",
    timestamp = timestamp,
    isMusic = isMusic,
    isShort = isShort,
)

/**
 * Resume-bar fraction using the same thresholds as the mobile cards:
 * hidden below 3% progress, shown full from 90%.
 */
internal fun VideoHistoryEntry.tvWatchProgress(): Float? = when {
    duration <= 0L -> null
    progressPercentage < 3f -> null
    progressPercentage >= 90f -> 1f
    else -> progressPercentage / 100f
}

// LikedVideoInfo carries no channelId or duration; those stay at their defaults.
internal fun LikedVideoInfo.toTvVideo(): Video = Video(
    id = videoId,
    title = title,
    channelName = channelName,
    channelId = "",
    thumbnailUrl = thumbnail,
    duration = 0,
    viewCount = 0L,
    uploadDate = "",
    timestamp = likedAt,
    isMusic = isMusic,
)
