package com.flow.youtube.data.model

import com.flow.youtube.innertube.pages.ShortsItem

/**
 * Dedicated data model for YouTube Shorts.
 * 
 * Separate from [Video] because Shorts have unique fields (musicInfo, sequenceParams,
 * playerParams) and different lifecycle (pre-buffered, looped, vertical-only).
 */
data class ShortVideo(
    val id: String,
    val title: String,
    val channelName: String,
    val channelId: String,
    val thumbnailUrl: String,
    val channelThumbnailUrl: String = "",
    val viewCountText: String = "",
    val likeCountText: String = "",
    val description: String = "",
    
    // Shorts-specific InnerTube fields
    val musicInfo: ShortMusicInfo? = null,
    val params: String? = null,
    val playerParams: String? = null,
    val sequenceParams: String? = null,
    
    val streamUrl: String? = null
)

/**
 * Music attribution for Shorts ("Original sound - ChannelName" or actual song info).
 */
data class ShortMusicInfo(
    val title: String,
    val artist: String,
    val albumArtUrl: String? = null
)

/**
 * Paginated response from reel sequence endpoint.
 */
data class ShortsSequenceResult(
    val shorts: List<ShortVideo>,
    val continuation: String?
)

// Mapping Extensions
/**
 * Convert InnerTube [ShortsItem] to our domain [ShortVideo].
 */
fun ShortsItem.toShortVideo(): ShortVideo = ShortVideo(
    id = id,
    title = title,
    channelName = channelName,
    channelId = channelId ?: "",
    thumbnailUrl = thumbnail.ifEmpty { "https://i.ytimg.com/vi/$id/oar2.jpg" },
    channelThumbnailUrl = channelThumbnailUrl ?: "",
    viewCountText = viewCountText ?: "",
    likeCountText = likeCountText ?: "",
    params = params,
    playerParams = playerParams,
    sequenceParams = sequenceParams
)

/**
 * Convert a generic [Video] (from NewPipe fallback) to [ShortVideo].
 */
fun Video.toShortVideo(): ShortVideo = ShortVideo(
    id = id,
    title = title,
    channelName = channelName,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
    channelThumbnailUrl = channelThumbnailUrl,
    viewCountText = formatShortViewCount(viewCount),
    likeCountText = formatMetric(likeCount),
    description = description
)

/**
 * Convert [ShortVideo] back to [Video] for compatibility with existing components
 * (like FlowDescriptionBottomSheet, comments, etc.)
 */
fun ShortVideo.toVideo(): Video = Video(
    id = id,
    title = title,
    channelName = channelName,
    channelId = channelId,
    thumbnailUrl = thumbnailUrl,
    duration = 60, 
    viewCount = parseViewCount(viewCountText),
    likeCount = parseMetric(likeCountText),
    uploadDate = "",
    description = description,
    channelThumbnailUrl = channelThumbnailUrl,
    isShort = true
)

/**
 * Format metric (likes, subs) to compact string (e.g. 1.2M).
 */
private fun formatMetric(count: Long): String = when {
    count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
    count > 0 -> count.toString()
    else -> ""
}

/**
 * Parse a metric string back to Long.
 */
private fun parseMetric(text: String): Long {
    if (text.isBlank()) return 0
    val cleaned = text.trim()
    return try {
        when {
            cleaned.endsWith("B", ignoreCase = true) ->
                (cleaned.dropLast(1).toDouble() * 1_000_000_000).toLong()
            cleaned.endsWith("M", ignoreCase = true) ->
                (cleaned.dropLast(1).toDouble() * 1_000_000).toLong()
            cleaned.endsWith("K", ignoreCase = true) ->
                (cleaned.dropLast(1).toDouble() * 1_000).toLong()
            else -> cleaned.toLongOrNull() ?: 0L
        }
    } catch (_: Exception) { 0L }
}

/**
 * Format view count to human-readable short form.
 */
private fun formatShortViewCount(count: Long): String = when {
    count >= 1_000_000_000 -> String.format("%.1fB views", count / 1_000_000_000.0)
    count >= 1_000_000 -> String.format("%.1fM views", count / 1_000_000.0)
    count >= 1_000 -> String.format("%.1fK views", count / 1_000.0)
    count > 0 -> "$count views"
    else -> ""
}

/**
 * Parse a view count text like "1.2M views" back to a Long approximation.
 */
private fun parseViewCount(text: String): Long {
    if (text.isBlank()) return 0
    val cleaned = text.replace(",", "").replace(" views", "").replace(" view", "").trim()
    return try {
        when {
            cleaned.endsWith("B", ignoreCase = true) ->
                (cleaned.dropLast(1).toDouble() * 1_000_000_000).toLong()
            cleaned.endsWith("M", ignoreCase = true) ->
                (cleaned.dropLast(1).toDouble() * 1_000_000).toLong()
            cleaned.endsWith("K", ignoreCase = true) ->
                (cleaned.dropLast(1).toDouble() * 1_000).toLong()
            else -> cleaned.toLongOrNull() ?: 0L
        }
    } catch (_: Exception) { 0L }
}
