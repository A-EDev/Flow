package io.github.aedev.flow.data.innertube

import android.util.Log
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.ChannelReelIndex
import io.github.aedev.flow.data.subscriptions.ChannelRssClient
import io.github.aedev.flow.data.subscriptions.ChannelRssEntry
import io.github.aedev.flow.data.subscriptions.ChannelUploadsClient
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.formatYouTubeRelativeTime
import io.github.aedev.flow.utils.parsePremiereTimestamp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One progressive slice of a subscription refresh.
 *
 * [failedChannelIds] holds channels that yielded nothing at all — neither RSS nor the channel tabs
 * answered — so the UI can say part of the feed is missing instead of silently showing less.
 */
data class SubscriptionFeedChunk(
    val videos: List<Video>,
    val failedChannelIds: Set<String>,
    /** Why each failed channel could not be read, keyed by channel id. */
    val failedChannelReasons: Map<String, String> = emptyMap(),
)

/**
 * Two-phase subscription extraction.
 *
 * Phase 1 reads every channel's RSS feed: cheap, and the only source with a trustworthy publish
 * timestamp. RSS lists Shorts alongside ordinary uploads without marking either, so each channel's
 * slice is run past [ChannelReelIndex] before it is used. Phase 2 falls back to the channel tabs for
 * the channels RSS could not satisfy, which is also the only way to see livestreams — their upload
 * dates are then back-filled from the Phase 1 timestamps.
 */
@Singleton
class RssSubscriptionService
    @Inject
    constructor(
        private val rssClient: ChannelRssClient,
        private val channelReelIndex: ChannelReelIndex,
        private val channelUploads: ChannelUploadsClient,
    ) {
        fun fetchSubscriptionVideos(
            channelIds: List<String>,
            maxTotal: Int = 1500,
            knownVideoIds: Set<String> = emptySet(),
            onProgress: ((processedChannels: Int, totalChannels: Int) -> Unit)? = null,
        ): Flow<SubscriptionFeedChunk> =
            flow {
                val uniqueChannelIds = channelIds.distinct()
                Log.i(TAG, "======== FEED FETCH START: ${uniqueChannelIds.size} channels ========")
                if (uniqueChannelIds.isEmpty()) {
                    Log.w(TAG, "No channel IDs provided — emitting empty list")
                    emit(SubscriptionFeedChunk(emptyList(), emptySet()))
                    return@flow
                }

                val allRegular = mutableListOf<Video>()
                val allShorts = mutableListOf<Video>()
                val channelExtractionCount = AtomicInteger(0)
                val minimumDateMillis = System.currentTimeMillis() - (SUBSCRIPTION_FEED_LOOKBACK_DAYS * 86400000L)
                Log.i(TAG, "Age cutoff: ${Date(minimumDateMillis)} (${SUBSCRIPTION_FEED_LOOKBACK_DAYS}d)")

                val rssDateMap = mutableMapOf<String, Long>()
                val rssChannelHasRecent = mutableMapOf<String, Boolean>()
                val rssNeedsChannelFallback = mutableMapOf<String, Boolean>()

                // Seeded by Phase 1 and cleared per channel as soon as Phase 2 answers for it.
                val unreachableChannelIds = mutableSetOf<String>()
                val failureReasons = mutableMapOf<String, String>()

                Log.i(TAG, "Phase 1: Fetching RSS feeds for all ${uniqueChannelIds.size} channels")
                val rssChunks = uniqueChannelIds.chunked(RSS_CHUNK_SIZE)
                for ((chunkIndex, chunk) in rssChunks.withIndex()) {
                    val results =
                        coroutineScope {
                            chunk
                                .map { channelId ->
                                    async(Dispatchers.IO) {
                                        val result = fetchRssVideos(channelId, minimumDateMillis, knownVideoIds)
                                        channelId to result.copy(videos = channelReelIndex.markReels(channelId, result.videos))
                                    }
                                }.awaitAll()
                        }
                    for ((channelId, result) in results) {
                        rssChannelHasRecent[channelId] = result.hasRecent
                        // RSS carries only the last fifteen uploads, so a Shorts-heavy channel can fill
                        // all of them with reels and hide its latest long-form upload entirely.
                        rssNeedsChannelFallback[channelId] =
                            result.needsChannelFallback || (result.hasRecentEntries && result.videos.none { !it.isShort })
                        rssDateMap.putAll(result.videoTimestamps)
                        if (result.failed) {
                            unreachableChannelIds += channelId
                            result.failureReason?.let { failureReasons[channelId] = it }
                        }
                        result.videos.forEach { video ->
                            if (video.isShort) allShorts.add(video) else allRegular.add(video)
                        }
                    }
                    compactAccumulator(allRegular, MAX_REGULAR_VIDEOS)
                    compactAccumulator(allShorts, MAX_SHORTS)
                    val processed = ((chunkIndex + 1) * RSS_CHUNK_SIZE).coerceAtMost(uniqueChannelIds.size)
                    onProgress?.invoke(processed, uniqueChannelIds.size)
                    emit(
                        SubscriptionFeedChunk(
                            videos = buildFeed(allRegular, allShorts, maxTotal),
                            failedChannelIds = unreachableChannelIds.toSet(),
                            failedChannelReasons = failureReasons.toMap(),
                        ),
                    )
                    if (chunkIndex > 0 && chunkIndex % (CHANNEL_BATCH_SIZE / RSS_CHUNK_SIZE).coerceAtLeast(1) == 0) {
                        delay(CHANNEL_BATCH_DELAY.random())
                    }
                }
                Log.i(TAG, "Phase 1 complete: RSS dates for ${rssDateMap.size} videos")

                val activeChannelIds =
                    uniqueChannelIds.filter {
                        rssNeedsChannelFallback[it] == true && rssChannelHasRecent[it] != false
                    }
                Log.i(TAG, "Phase 2: channel tabs for ${activeChannelIds.size} RSS fallback channels")

                var processedChannels = uniqueChannelIds.size - activeChannelIds.size
                if (processedChannels > 0) {
                    onProgress?.invoke(processedChannels, uniqueChannelIds.size)
                }
                val chunks = activeChannelIds.chunked(CHANNEL_CHUNK_SIZE)
                for ((chunkIndex, chunk) in chunks.withIndex()) {
                    if (channelExtractionCount.get() >= CHANNEL_BATCH_SIZE) {
                        Log.i(TAG, "Batch limit reached, throttling...")
                        delay(CHANNEL_BATCH_DELAY.random())
                        channelExtractionCount.set(0)
                    }

                    val chunkResults =
                        coroutineScope {
                            chunk
                                .map { channelId ->
                                    async(Dispatchers.IO) {
                                        channelId to runChannelTabFetch(channelId, minimumDateMillis, rssDateMap, channelExtractionCount)
                                    }
                                }.awaitAll()
                        }

                    for ((channelId, result) in chunkResults) {
                        if (result.failed) {
                            // Even when RSS answered, a failed tab pass leaves this channel's slice
                            // incomplete, and only a listed failure keeps its earlier rows alive.
                            unreachableChannelIds += channelId
                            result.failureReason?.let { failureReasons[channelId] = it }
                        } else {
                            unreachableChannelIds -= channelId
                            failureReasons -= channelId
                        }
                        result.videos.forEach { if (it.isShort) allShorts.add(it) else allRegular.add(it) }
                    }
                    compactAccumulator(allRegular, MAX_REGULAR_VIDEOS)
                    compactAccumulator(allShorts, MAX_SHORTS)
                    processedChannels = (processedChannels + chunk.size).coerceAtMost(uniqueChannelIds.size)
                    onProgress?.invoke(processedChannels, uniqueChannelIds.size)
                    Log.d(TAG, "Chunk ${chunkIndex + 1}/${chunks.size}: +${chunkResults.sumOf { it.second.videos.size }}")

                    emit(
                        SubscriptionFeedChunk(
                            videos = buildFeed(allRegular, allShorts, maxTotal),
                            failedChannelIds = unreachableChannelIds.toSet(),
                            failedChannelReasons = failureReasons.toMap(),
                        ),
                    )
                }

                emit(
                    SubscriptionFeedChunk(
                        videos = buildFeed(allRegular, allShorts, maxTotal),
                        failedChannelIds = unreachableChannelIds.toSet(),
                        failedChannelReasons = failureReasons.toMap(),
                    ),
                )
                Log.i(
                    TAG,
                    "======== FEED FETCH COMPLETE: regular=${allRegular.size.coerceAtMost(MAX_REGULAR_VIDEOS)} " +
                        "shorts=${allShorts.size.coerceAtMost(MAX_SHORTS)} unreachable=${unreachableChannelIds.size} ========",
                )
            }

        private suspend fun runChannelTabFetch(
            channelId: String,
            minimumDateMillis: Long,
            rssDateMap: Map<String, Long>,
            channelExtractionCount: AtomicInteger,
        ): ChannelFetchResult =
            try {
                getChannelVideos(channelId, minimumDateMillis, rssDateMap).also {
                    if (it.videos.isNotEmpty()) channelExtractionCount.incrementAndGet()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "UNCAUGHT in channel $channelId: ${e::class.simpleName}: ${e.message}")
                ChannelFetchResult(emptyList(), failed = true, failureReason = "${e::class.simpleName}: ${e.message}")
            }

        /** Merge regular and shorts lists with independent caps, sorted by date. */
        private fun buildFeed(
            regular: List<Video>,
            shorts: List<Video>,
            maxTotal: Int,
        ): List<Video> {
            val mergedRegular =
                regular
                    .sortedByDescending { it.timestamp }
                    .mergeDuplicateVideos()
                    .take(MAX_REGULAR_VIDEOS)
            val mergedShorts =
                shorts
                    .sortedByDescending { it.timestamp }
                    .mergeDuplicateVideos()
                    .distinctBy { it.channelId.ifBlank { it.id } }
                    .take(MAX_SHORTS)
            return (mergedRegular + mergedShorts)
                .sortedByDescending { it.timestamp }
                .mergeDuplicateVideos()
                .take(maxTotal)
        }

        private fun List<Video>.mergeDuplicateVideos(): List<Video> {
            val now = System.currentTimeMillis()
            return groupBy { it.id }
                .values
                .map { candidates ->
                    val primary = candidates.first()
                    val timestampSource =
                        candidates
                            .filter { it.timestamp > 0L }
                            .maxByOrNull { it.timestamp }
                            ?: primary
                    val bestChannelThumbnail =
                        candidates.firstOrNull { it.channelThumbnailUrl.isNotBlank() }?.channelThumbnailUrl
                            ?: primary.channelThumbnailUrl
                    val bestVideoThumbnail =
                        candidates
                            .asSequence()
                            .map { ThumbnailUrlResolver.normalizeVideoThumbnail(it.id, it.thumbnailUrl) }
                            .firstOrNull { it.isNotBlank() }
                            ?: ThumbnailUrlResolver.normalizeVideoThumbnail(primary.id, primary.thumbnailUrl)
                    val bestDescription =
                        candidates.firstOrNull { it.description.isNotBlank() }?.description
                            ?: primary.description

                    primary.copy(
                        duration = candidates.maxOf { it.duration },
                        viewCount = candidates.maxOf { it.viewCount },
                        thumbnailUrl = bestVideoThumbnail,
                        uploadDate = timestampSource.uploadDate,
                        timestamp = timestampSource.timestamp,
                        description = bestDescription,
                        channelThumbnailUrl = bestChannelThumbnail,
                        isShort = candidates.any { it.isShort },
                        isLive = candidates.any { it.isLive },
                        isUpcoming = candidates.any { it.isUpcoming && it.timestamp > now + 60_000L },
                    )
                }.sortedByDescending { it.timestamp }
        }

        private fun compactAccumulator(
            videos: MutableList<Video>,
            maxSize: Int,
        ) {
            if (videos.size <= maxSize * 2) return
            val compacted = videos.mergeDuplicateVideos().take(maxSize)
            videos.clear()
            videos.addAll(compacted)
        }

        private data class RssResult(
            val hasRecent: Boolean,
            val videoTimestamps: Map<String, Long>,
            val videos: List<Video>,
            val needsChannelFallback: Boolean,
            /** The feed lists an upload inside the window, whether or not it survived the filters. */
            val hasRecentEntries: Boolean = false,
            val failed: Boolean = false,
            val failureReason: String? = null,
        )

        private data class ChannelFetchResult(
            val videos: List<Video>,
            val failed: Boolean,
            val failureReason: String? = null,
        )

        /**
         * RSS gives accurate dates for all recent uploads (including Shorts) but never a duration or a
         * Shorts/live flag, so its timestamps double as the date source for the Phase 2 tab items.
         */
        private suspend fun fetchRssVideos(
            channelId: String,
            minimumDateMillis: Long,
            knownVideoIds: Set<String>,
        ): RssResult {
            val feed =
                rssClient.fetch(channelId).getOrElse { error ->
                    Log.w(TAG, "[$channelId] RSS FAILED: ${error::class.simpleName}: ${error.message}")
                    return RssResult(
                        hasRecent = true,
                        videoTimestamps = emptyMap(),
                        videos = emptyList(),
                        needsChannelFallback = true,
                        failed = true,
                        failureReason = "RSS: ${error::class.simpleName}: ${error.message}",
                    )
                }

            val timestamps = mutableMapOf<String, Long>()
            val videos = mutableListOf<Video>()
            var newestTimestamp = 0L

            for (entry in feed.entries) {
                val publishedAt = entry.publishedAtMillis
                if (publishedAt <= 0L) continue
                timestamps[entry.videoId] = publishedAt
                if (publishedAt > newestTimestamp) newestTimestamp = publishedAt
                if (publishedAt > minimumDateMillis && !entry.isPaidOrMembersOnly()) {
                    videos += entry.toVideo(channelId = channelId, channelName = feed.channelName)
                }
            }

            if (timestamps.isEmpty()) {
                return RssResult(
                    hasRecent = true,
                    videoTimestamps = emptyMap(),
                    videos = emptyList(),
                    needsChannelFallback = true,
                )
            }

            val hasUnknownRecentUpload =
                timestamps.any { (videoId, timestamp) ->
                    timestamp > minimumDateMillis && videoId !in knownVideoIds
                }
            return RssResult(
                hasRecent = newestTimestamp > minimumDateMillis || hasUnknownRecentUpload,
                videoTimestamps = timestamps,
                videos = videos,
                needsChannelFallback = videos.isEmpty() && newestTimestamp > minimumDateMillis,
                hasRecentEntries = newestTimestamp > minimumDateMillis,
            )
        }

        private fun ChannelRssEntry.toVideo(
            channelId: String,
            channelName: String?,
        ): Video {
            val now = System.currentTimeMillis()
            val isUpcoming = publishedAtMillis > now + 60_000L
            return Video(
                id = videoId,
                title = title,
                channelName = channelName.orEmpty().ifBlank { UNKNOWN_LABEL },
                channelId = channelId,
                thumbnailUrl = ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, thumbnailUrl),
                duration = 0,
                viewCount = viewCount,
                uploadDate = if (isUpcoming) "" else formatRelativeTime(publishedAtMillis),
                timestamp = publishedAtMillis,
                channelThumbnailUrl = "",
                description = description.orEmpty(),
                isShort = false,
                isLive = false,
                isUpcoming = isUpcoming,
            )
        }

        /**
         * A channel's own Videos, Shorts and Live tabs, through the native InnerTube browse.
         *
         * @param rssDateMap Pre-fetched RSS timestamps keyed by video ID. Used to assign accurate
         *   upload dates to Shorts tab items, which carry no date metadata of their own.
         */
        private suspend fun getChannelVideos(
            channelId: String,
            minimumDateMillis: Long,
            rssDateMap: Map<String, Long>,
        ): ChannelFetchResult {
            val uploads =
                channelUploads.fetch(channelId, minimumDateMillis).getOrElse { error ->
                    if (error is CancellationException) throw error
                    Log.e(TAG, "[$channelId] Channel tabs FAILED (${error::class.simpleName}): ${error.message}")
                    return ChannelFetchResult(
                        videos = emptyList(),
                        failed = true,
                        failureReason = "Channel: ${error::class.simpleName}: ${error.message}",
                    )
                }
            val channelAvatar =
                uploads.owner.avatarUrl
                    .takeIf { it.isNotBlank() }
                    ?.let { ThumbnailUrlResolver.resolveChannelAvatar(it) }
                    .orEmpty()
            val shortIds = uploads.shorts.mapTo(HashSet()) { it.id }
            val liveIds = uploads.live.mapTo(HashSet()) { it.id }
            val videos =
                (uploads.videos + uploads.shorts + uploads.live)
                    .distinctBy { it.id }
                    .mapNotNull { video ->
                        video.toFeedVideo(
                            channelId = channelId,
                            channelName = uploads.owner.name,
                            channelAvatar = channelAvatar,
                            fromShortsTab = video.id in shortIds,
                            fromLiveTab = video.id in liveIds,
                            rssUploadTimeMillis = rssDateMap[video.id],
                            minimumDateMillis = minimumDateMillis,
                        )
                    }

            Log.i(TAG, "[$channelId] RESULT: ${videos.size} videos (${videos.count { it.isShort }} shorts)")
            return ChannelFetchResult(videos, failed = false)
        }

        private fun Video.toFeedVideo(
            channelId: String,
            channelName: String,
            channelAvatar: String,
            fromShortsTab: Boolean,
            fromLiveTab: Boolean,
            rssUploadTimeMillis: Long?,
            minimumDateMillis: Long,
        ): Video? {
            if (isPaidOrMembersOnly()) return null
            val premiereAt = if (isUpcoming) parsePremiereTimestamp(uploadDate) else null
            val uploadedAt = rssUploadTimeMillis ?: timestamp.takeIf { it > 0L } ?: premiereAt ?: return null
            if (uploadedAt <= minimumDateMillis) return null

            val upcoming = isUpcoming || uploadedAt > System.currentTimeMillis() + 60_000L
            val isArchivedLivestream = fromLiveTab && !isLive && !upcoming
            return copy(
                channelName = this.channelName.ifBlank { channelName }.ifBlank { UNKNOWN_LABEL },
                channelId = channelId,
                thumbnailUrl = ThumbnailUrlResolver.normalizeVideoThumbnail(id, thumbnailUrl),
                uploadDate =
                    if (upcoming && uploadDate.isNotBlank()) {
                        uploadDate
                    } else {
                        formatRelativeTime(uploadedAt).let { if (isArchivedLivestream) "Streamed $it" else it }
                    },
                timestamp = uploadedAt,
                channelThumbnailUrl = channelAvatar.ifBlank { channelThumbnailUrl },
                isShort = isShort || fromShortsTab,
                isLive = !upcoming && (isLive || fromLiveTab),
                isUpcoming = upcoming,
            )
        }

        private fun formatRelativeTime(timestampMillis: Long): String = formatYouTubeRelativeTime(timestampMillis)

        /** The structured members-only badge, or a strong marker in the title itself. */
        private fun Video.isPaidOrMembersOnly(): Boolean = membersOnlyText != null || hasRestrictionMarker(title)

        /**
         * Title only: "join this channel" and "channel members" are YouTube's stock membership call
         * to action in ordinary descriptions, so matching descriptions dropped normal uploads (#1094).
         */
        private fun ChannelRssEntry.isPaidOrMembersOnly(): Boolean = hasRestrictionMarker(title)

        private fun hasRestrictionMarker(title: String): Boolean {
            val normalized = title.lowercase(Locale.US)
            return RESTRICTION_MARKERS.any { marker -> normalized.contains(marker) }
        }

        private companion object {
            const val TAG = "InnertubeSubs"
            const val UNKNOWN_LABEL = "Unknown"

            const val RSS_CHUNK_SIZE = 8
            const val CHANNEL_CHUNK_SIZE = 3
            const val CHANNEL_BATCH_SIZE = 50
            val CHANNEL_BATCH_DELAY = (100L..400L)
            const val SUBSCRIPTION_FEED_LOOKBACK_DAYS = 60L

            const val MAX_REGULAR_VIDEOS = 1200
            const val MAX_SHORTS = 300

            val RESTRICTION_MARKERS =
                listOf(
                    "members only",
                    "members-only",
                    "member only",
                    "member-only",
                    "membership only",
                    "requires membership",
                )
        }
    }
