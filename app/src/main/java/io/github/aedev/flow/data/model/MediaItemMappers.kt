package io.github.aedev.flow.data.model

import io.github.aedev.flow.data.local.LikedVideoInfo
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.music.model.MusicTrack

private const val MILLIS_PER_SECOND = 1000L

fun VideoHistoryEntry.toMusicTrack(): MusicTrack =
    MusicTrack(
        videoId = videoId,
        title = title,
        artist = channelName,
        thumbnailUrl = thumbnailUrl,
        duration = (duration / MILLIS_PER_SECOND).toInt(),
        channelId = channelId,
    )

fun VideoHistoryEntry.toVideo(): Video =
    Video(
        id = videoId,
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        duration = (duration / MILLIS_PER_SECOND).toInt(),
        viewCount = -1L,
        uploadDate = "",
        timestamp = timestamp,
        isShort = isShort,
    )

fun LikedVideoInfo.toMusicTrack(): MusicTrack =
    MusicTrack(
        videoId = videoId,
        title = title,
        artist = channelName,
        thumbnailUrl = thumbnail,
        duration = durationSeconds,
        channelId = channelId.orEmpty(),
    )

fun LikedVideoInfo.toVideo(): Video =
    Video(
        id = videoId,
        title = title,
        channelName = channelName,
        channelId = channelId.orEmpty(),
        thumbnailUrl = thumbnail,
        duration = durationSeconds,
        viewCount = -1L,
        uploadDate = "",
        timestamp = likedAt,
        isMusic = isMusic,
    )
