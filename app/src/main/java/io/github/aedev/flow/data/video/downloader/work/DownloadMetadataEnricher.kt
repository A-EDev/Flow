package io.github.aedev.flow.data.video.downloader.work

import android.util.Log
import io.github.aedev.flow.data.local.dao.DownloadDao
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.video.downloader.request.DownloadRequest
import io.github.aedev.flow.data.video.downloader.tags.displayArtist
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills a video download's tags from its watch page: date, description, counts and channel id,
 * which a card rarely carries. One cached `/next` per download, stored with the request so a retry
 * never asks again. Songs are left as they are: their album and artists come from the music pages.
 */
@Singleton
class DownloadMetadataEnricher
    @Inject
    constructor(
        private val repository: YouTubeRepository,
        private val downloadDao: DownloadDao,
    ) {
        suspend fun enrich(request: DownloadRequest): DownloadRequest {
            val tags = request.tags
            if (request.isMusic || (tags.releaseDate != null && tags.description != null && tags.channelId != null)) return request
            val base =
                Video(
                    id = request.videoId,
                    title = tags.title,
                    channelName = tags.channelName.orEmpty(),
                    channelId = tags.channelId.orEmpty(),
                    thumbnailUrl = tags.thumbnailUrl.orEmpty(),
                    duration = request.durationSeconds,
                    viewCount = tags.viewCount ?: 0L,
                    likeCount = tags.likeCount ?: 0L,
                    uploadDate = "",
                    description = tags.description.orEmpty(),
                )
            val watched =
                try {
                    withTimeoutOrNull(WATCH_PAGE_TIMEOUT_MS) { repository.enrichFromWatchMetadata(base) }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "${request.videoId}: watch page unavailable", e)
                    null
                } ?: return request
            val enriched =
                request.copy(
                    tags =
                        tags.copy(
                            title = tags.title.ifBlank { watched.title },
                            channelId = tags.channelId ?: watched.channelId.takeIf { it.isNotBlank() },
                            channelName = tags.channelName ?: watched.channelName.takeIf { it.isNotBlank() },
                            description = watched.description.takeIf { it.isNotBlank() } ?: tags.description,
                            viewCount = watched.viewCount.takeIf { it > 0 } ?: tags.viewCount,
                            likeCount = watched.likeCount.takeIf { it > 0 } ?: tags.likeCount,
                            releaseDate = tags.releaseDate ?: isoDate(watched.timestamp),
                        ),
                )
            val enrichedTags = enriched.tags
            downloadDao.updateMetadata(
                videoId = request.videoId,
                title = enrichedTags.title,
                uploader = enrichedTags.displayArtist().orEmpty(),
                channelId = enrichedTags.channelId.orEmpty(),
                description = enrichedTags.description.orEmpty(),
                releaseDate = enrichedTags.releaseDate,
                viewCount = enrichedTags.viewCount ?: 0L,
                likeCount = enrichedTags.likeCount ?: 0L,
                requestJson = enriched.encode(),
            )
            return enriched
        }

        internal companion object {
            private const val TAG = "DownloadMetadataEnricher"
            private const val WATCH_PAGE_TIMEOUT_MS = 8_000L

            fun isoDate(
                epochMillis: Long,
                zone: ZoneId = ZoneId.systemDefault(),
            ): String? =
                epochMillis.takeIf { it > 0 }?.let {
                    Instant
                        .ofEpochMilli(it)
                        .atZone(zone)
                        .toLocalDate()
                        .toString()
                }
        }
    }
