package io.github.aedev.flow.data.model

import io.github.aedev.flow.innertube.pages.parseYouTubeViewCount
import io.github.aedev.flow.innertube.pages.reel.ReelEntry
import io.github.aedev.flow.innertube.pages.reel.ReelLockup
import io.github.aedev.flow.innertube.pages.reel.reelPosterUrl
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.formatViewCount

/**
 * One reel in a Shorts queue. A reel arrives from the sequence as an id and a poster; everything
 * else is filled in as the reel resolves, so every field but those two has an empty default.
 */
data class ShortVideo(
    val id: String,
    val thumbnailUrl: String,
    val title: String = "",
    val channelName: String = "",
    val channelId: String = "",
    val channelThumbnailUrl: String = "",
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val commentCount: Long = 0L,
    val durationMs: Long = 0L,
    val description: String = "",
    val uploadDate: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val soundTitle: String = "",
    val soundThumbnailUrl: String = "",
    val playerParams: String? = null,
)

data class ShortsSequenceResult(
    val shorts: List<ShortVideo>,
    val continuation: String?,
)

fun ReelEntry.toShortVideo(): ShortVideo =
    ShortVideo(
        id = videoId,
        thumbnailUrl = posterUrl ?: reelPosterUrl(videoId),
        playerParams = playerParams,
    )

/** A lockup names the reel and counts its views; its channel arrives when the reel resolves. */
fun ReelLockup.toShortVideo(): ShortVideo =
    ShortVideo(
        id = id,
        thumbnailUrl = ThumbnailUrlResolver.normalizeVideoThumbnail(id, thumbnailUrl.ifBlank { posterUrl }),
        title = title,
        viewCount = viewCount,
        playerParams = playerParams,
    )

fun Video.toShortVideo(): ShortVideo =
    ShortVideo(
        id = id,
        thumbnailUrl = thumbnailUrl,
        title = title,
        channelName = channelName,
        channelId = channelId,
        channelThumbnailUrl = channelThumbnailUrl,
        viewCount = viewCount,
        likeCount = likeCount,
        commentCount = parseYouTubeViewCount(commentCountText),
        durationMs = duration.coerceAtLeast(0) * 1_000L,
        description = description,
        uploadDate = uploadDate,
        timestamp = timestamp,
    )

/** The shape the shared sheets, cards and history take. */
fun ShortVideo.toVideo(): Video =
    Video(
        id = id,
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        duration = (durationMs / 1_000L).toInt(),
        viewCount = viewCount,
        likeCount = likeCount,
        uploadDate = uploadDate,
        timestamp = timestamp,
        description = description,
        channelThumbnailUrl = channelThumbnailUrl,
        isShort = true,
        commentCountText = commentCount.takeIf { it > 0L }?.let(::formatViewCount).orEmpty(),
    )
