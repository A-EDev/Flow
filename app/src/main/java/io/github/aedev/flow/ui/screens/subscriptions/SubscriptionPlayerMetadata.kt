package io.github.aedev.flow.ui.screens.subscriptions

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.premiereDateText
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The duration, live state and scheduled start RSS cannot carry, read from the player endpoint.
 * Null when the lookup times out or tells nothing new.
 */
internal suspend fun fetchSubscriptionPlayerMetadata(
    video: Video,
    timeoutMs: Long,
): Video? =
    withTimeoutOrNull(timeoutMs) {
        val response =
            YouTube.player(video.id, client = YouTubeClient.ANDROID).getOrNull()
                ?: YouTube.player(video.id, client = YouTubeClient.MOBILE).getOrNull()
                ?: return@withTimeoutOrNull null
        val details = response.videoDetails ?: return@withTimeoutOrNull null
        // The feed only knows the day the stream was announced; the player endpoint knows
        // the day it starts, and until then "live content" is a scheduled stream, not a live one.
        val scheduledStartMs =
            response.playabilityStatus.liveStreamability
                ?.liveStreamabilityRenderer
                ?.offlineSlate
                ?.liveStreamOfflineSlateRenderer
                ?.scheduledStartTime
                ?.toLongOrNull()
                ?.times(1000L)
                ?.takeIf { it > System.currentTimeMillis() }
        val isUpcoming =
            scheduledStartMs != null ||
                response.playabilityStatus.status.equals("LIVE_STREAM_OFFLINE", ignoreCase = true)
        val isLive = !isUpcoming && (details.isLive == true || details.isLiveContent == true)
        val duration = details.lengthSeconds.toIntOrNull()?.takeIf { it > 0 } ?: 0
        if (!isUpcoming && !isLive && duration <= 0) return@withTimeoutOrNull null

        val bestThumbnail =
            details.thumbnail
                ?.thumbnails
                ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }
                ?.url
                ?.let { ThumbnailUrlResolver.normalizeVideoThumbnail(video.id, it) }
                ?: ThumbnailUrlResolver.normalizeVideoThumbnail(video.id, video.thumbnailUrl)

        video.copy(
            title = details.title?.takeIf { it.isNotBlank() } ?: video.title,
            channelName = details.author?.takeIf { it.isNotBlank() } ?: video.channelName,
            channelId = details.channelId.takeIf { it.isNotBlank() } ?: video.channelId,
            thumbnailUrl = bestThumbnail,
            duration = if (isLive || isUpcoming) 0 else duration,
            viewCount = maxOf(video.viewCount, details.viewCount?.toLongOrNull() ?: 0L),
            isLive = isLive || (!isUpcoming && video.isLive),
            isUpcoming = isUpcoming,
            isScheduledLive = isUpcoming && details.isLiveContent == true,
            timestamp = scheduledStartMs ?: video.timestamp,
            uploadDate = scheduledStartMs?.let(::premiereDateText) ?: video.uploadDate,
        )
    }

/** Folds a player lookup into the feed row it was made for, keeping what the lookup left blank. */
internal fun Video.withPlayerMetadata(enriched: Video): Video =
    copy(
        title = enriched.title.takeIf { it.isNotBlank() } ?: title,
        channelName = enriched.channelName.takeIf { it.isNotBlank() } ?: channelName,
        channelId = enriched.channelId.takeIf { it.isNotBlank() } ?: channelId,
        thumbnailUrl = enriched.thumbnailUrl.takeIf { it.isNotBlank() } ?: thumbnailUrl,
        duration = enriched.duration.takeIf { it > 0 } ?: duration,
        viewCount = maxOf(viewCount, enriched.viewCount),
        isLive = enriched.isLive || (!enriched.isUpcoming && isLive),
        isUpcoming = enriched.isUpcoming,
        isScheduledLive = enriched.isScheduledLive,
        timestamp = if (enriched.isUpcoming) enriched.timestamp else timestamp,
        uploadDate = if (enriched.isUpcoming) enriched.uploadDate else uploadDate,
    )
