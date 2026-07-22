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
    isShort = isShort,
)

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
