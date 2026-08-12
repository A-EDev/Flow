package io.github.aedev.flow.player.stream

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * Maps NewPipe's `StreamInfo` / `StreamInfoItem` onto the app's [Video] model.
 *
 * Extracted verbatim from `EnhancedPlayerManager`; the logic reads no player state, which is what
 * makes it testable in isolation.
 */
object StreamInfoVideoMapper {
    /**
     * Merges [info] over [fallback] field by field, so a stream response that omits a value keeps
     * whatever the caller already knew rather than blanking it.
     */
    fun videoFromStreamInfo(
        videoId: String,
        info: StreamInfo,
        fallback: Video,
    ): Video {
        val thumbnail =
            info.thumbnails
                .sortedByDescending { it.height }
                .map { it.url }
                .firstOrNull()
                .let { ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, it) }
        return fallback.copy(
            title = info.name?.takeIf { it.isNotBlank() } ?: fallback.title,
            channelName = info.uploaderName?.takeIf { it.isNotBlank() } ?: fallback.channelName,
            channelId = extractChannelId(info.uploaderUrl).ifBlank { fallback.channelId },
            thumbnailUrl = thumbnail.ifBlank { fallback.thumbnailUrl },
            duration = info.duration.toInt().takeIf { it > 0 } ?: fallback.duration,
            viewCount = info.viewCount.takeIf { it > 0L } ?: fallback.viewCount,
            uploadDate = info.textualUploadDate ?: fallback.uploadDate,
            description = info.description?.content ?: fallback.description,
            tags = info.tags ?: fallback.tags,
        )
    }

    /** Related items that fail to map are dropped rather than failing the whole list. */
    fun relatedVideosFromStreamInfo(
        info: StreamInfo,
        unknownTitle: String = "",
        unknownChannel: String = "",
        unknownDate: String = "",
    ): List<Video> =
        info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            runCatching { item.toFlowVideo(unknownTitle, unknownChannel, unknownDate) }.getOrNull()
        }

    /** @throws IllegalArgumentException when no video id can be parsed out of the item's URL. */
    fun StreamInfoItem.toFlowVideo(
        unknownTitle: String = "",
        unknownChannel: String = "",
        unknownDate: String = "",
    ): Video {
        val rawUrl = url ?: ""
        val videoId =
            when {
                rawUrl.contains("watch?v=") -> rawUrl.substringAfter("watch?v=").substringBefore("&")
                rawUrl.contains("youtu.be/") -> rawUrl.substringAfter("youtu.be/").substringBefore("?")
                rawUrl.contains("/shorts/") -> rawUrl.substringAfter("/shorts/").substringBefore("?")
                else -> rawUrl.substringAfterLast("/")
            }.trim()
        if (videoId.isBlank()) throw IllegalArgumentException("Blank related video id")

        val bestThumbnail =
            thumbnails
                .sortedByDescending { it.height }
                .map { it.url }
                .firstOrNull()
                .let { ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, it) }

        val isShortUrl = rawUrl.contains("/shorts/")
        val isLiveStream = streamType == StreamType.LIVE_STREAM
        val durationSecs =
            when {
                isLiveStream -> 0

                duration > 0 -> duration.toInt()

                // Shorts feed items report no duration; 60s is the format's ceiling.
                isShortUrl -> 60

                else -> 0
            }
        val nameLower = name?.lowercase() ?: ""
        val uploaderLower = uploaderName?.lowercase() ?: ""
        val isMusicCandidate =
            uploaderLower.contains("vevo") ||
                uploaderLower.contains(" - topic") ||
                nameLower.contains("official music video") ||
                nameLower.contains("official video") ||
                nameLower.contains("official audio") ||
                nameLower.contains("(official)")

        return Video(
            id = videoId,
            title = name ?: unknownTitle,
            channelName = uploaderName ?: unknownChannel,
            channelId = extractChannelId(uploaderUrl),
            thumbnailUrl = bestThumbnail,
            duration = durationSecs,
            viewCount = viewCount,
            uploadDate = textualUploadDate ?: unknownDate,
            channelThumbnailUrl = uploaderAvatars.sortedByDescending { it.height }.firstOrNull()?.url ?: "",
            isUpcoming = streamType == StreamType.NONE,
            isLive = isLiveStream,
            isShort = isShortUrl,
            isMusic = isMusicCandidate,
        )
    }

    /** Blank when the URL has no trailing segment, so callers can fall back rather than store "". */
    fun extractChannelId(url: String?): String = url?.substringAfterLast("/")?.takeIf { it.isNotBlank() && it != url } ?: ""
}
