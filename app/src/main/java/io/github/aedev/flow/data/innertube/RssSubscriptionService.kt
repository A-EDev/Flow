package io.github.aedev.flow.data.innertube

import android.util.Log
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.subscriptions.ChannelRssClient
import io.github.aedev.flow.data.subscriptions.ChannelRssEntry
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
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
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
)

/**
 * Two-phase subscription extraction.
 *
 * Phase 1 reads every channel's RSS feed: cheap, and the only source with a trustworthy publish
 * timestamp. Phase 2 falls back to the channel tabs for the channels RSS could not satisfy, which
 * is also the only way to see Shorts and livestreams — their upload dates are then back-filled from
 * the Phase 1 timestamps.
 */
@Singleton
class RssSubscriptionService
    @Inject
    constructor(
        private val rssClient: ChannelRssClient,
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

                Log.i(TAG, "Phase 1: Fetching RSS feeds for all ${uniqueChannelIds.size} channels")
                val rssChunks = uniqueChannelIds.chunked(RSS_CHUNK_SIZE)
                for ((chunkIndex, chunk) in rssChunks.withIndex()) {
                    val results =
                        coroutineScope {
                            chunk
                                .map { channelId ->
                                    async(Dispatchers.IO) {
                                        channelId to fetchRssVideos(channelId, minimumDateMillis, knownVideoIds)
                                    }
                                }.awaitAll()
                        }
                    for ((channelId, result) in results) {
                        rssChannelHasRecent[channelId] = result.hasRecent
                        rssNeedsChannelFallback[channelId] = result.needsChannelFallback
                        rssDateMap.putAll(result.videoTimestamps)
                        if (result.failed) unreachableChannelIds += channelId
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
                        // RSS may already have answered for this channel; only a second miss keeps it listed.
                        if (!result.failed) unreachableChannelIds -= channelId
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
                        ),
                    )
                }

                emit(
                    SubscriptionFeedChunk(
                        videos = buildFeed(allRegular, allShorts, maxTotal),
                        failedChannelIds = unreachableChannelIds.toSet(),
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
                ChannelFetchResult(emptyList(), failed = true)
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
            val failed: Boolean = false,
        )

        private data class ChannelFetchResult(
            val videos: List<Video>,
            val failed: Boolean,
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
         * Get videos (including Shorts) from a single channel using NewPipe Extractor.
         *
         * @param rssDateMap Pre-fetched RSS timestamps keyed by video ID. Used to assign accurate
         *   upload dates to Shorts tab items, which carry no date metadata of their own.
         */
        private suspend fun getChannelVideos(
            channelId: String,
            minimumDateMillis: Long,
            rssDateMap: Map<String, Long>,
        ): ChannelFetchResult {
            val channelUrl = "$YOUTUBE_URL/channel/$channelId"
            val service = NewPipe.getService(0)

            try {
                val channelInfo = ChannelInfo.getInfo(service, channelUrl)
                val channelAvatar =
                    channelInfo.avatars
                        .maxByOrNull { it.height }
                        ?.url
                        ?.let { ThumbnailUrlResolver.resolveChannelAvatar(it) }

                val videosTab = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.VIDEOS) }
                val shortsTab = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.SHORTS) }
                val liveTab = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.LIVESTREAMS) }

                if (videosTab == null && shortsTab == null && liveTab == null) {
                    Log.w(TAG, "[$channelId] No VIDEOS/SHORTS/LIVE tab found")
                    return ChannelFetchResult(emptyList(), failed = false)
                }

                val (videoItems, shortsItems, liveItems) =
                    coroutineScope {
                        val videoDeferred = videosTab?.let { async(Dispatchers.IO) { fetchTabItems(it, MAX_VIDEOS_PER_CHANNEL) } }
                        val shortsDeferred = shortsTab?.let { async(Dispatchers.IO) { fetchTabItems(it, MAX_SHORTS_PER_CHANNEL) } }
                        val liveDeferred = liveTab?.let { async(Dispatchers.IO) { fetchTabItems(it, MAX_LIVE_PER_CHANNEL) } }
                        Triple(
                            videoDeferred?.await() ?: emptyList(),
                            shortsDeferred?.await() ?: emptyList(),
                            liveDeferred?.await() ?: emptyList(),
                        )
                    }

                val shortsUrls = shortsItems.map { it.url }.toHashSet()
                val liveUrls = liveItems.map { it.url }.toHashSet()
                val combined = (videoItems + shortsItems + liveItems).distinctBy { it.url }

                val videos =
                    combined.mapNotNull { item ->
                        val videoId = extractVideoId(item.url)
                        if (item.isPaidOrMembersOnly()) return@mapNotNull null

                        val uploadTimeMillis = rssDateMap[videoId] ?: resolveUploadTimestamp(item)
                        when {
                            uploadTimeMillis == null -> {
                                null
                            }

                            uploadTimeMillis <= minimumDateMillis -> {
                                null
                            }

                            else -> {
                                streamInfoItemToVideo(
                                    item = item,
                                    channelId = channelId,
                                    channelAvatar = channelAvatar,
                                    forceShort = item.url in shortsUrls,
                                    forceLive = item.url in liveUrls,
                                    overrideTimestamp = uploadTimeMillis,
                                )
                            }
                        }
                    }

                Log.i(TAG, "[$channelId] RESULT: ${videos.size} videos (${videos.count { it.isShort }} shorts)")
                return ChannelFetchResult(videos, failed = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "[$channelId] ChannelInfo FAILED (${e::class.simpleName}): ${e.message}")
                return ChannelFetchResult(emptyList(), failed = true)
            }
        }

        private fun fetchTabItems(
            tab: ListLinkHandler,
            limit: Int,
        ): List<StreamInfoItem> {
            val service = NewPipe.getService(0)
            val items = mutableListOf<StreamInfoItem>()
            var nextPage: Page? = null

            runCatching {
                val tabInfo = ChannelTabInfo.getInfo(service, tab)
                items += tabInfo.relatedItems.filterIsInstance<StreamInfoItem>()
                nextPage = tabInfo.nextPage
            }.getOrElse {
                Log.w(TAG, "Initial tab fetch failed: ${it::class.simpleName}: ${it.message}")
                return emptyList()
            }

            while (items.size < limit && nextPage != null) {
                val page = nextPage ?: break
                val moreItems =
                    try {
                        ChannelTabInfo.getMoreItems(service, tab, page)
                    } catch (e: Exception) {
                        Log.w(TAG, "Paged tab fetch failed: ${e::class.simpleName}: ${e.message}")
                        break
                    }

                val newItems = moreItems.items.filterIsInstance<StreamInfoItem>()
                if (newItems.isEmpty()) break
                items += newItems
                nextPage = moreItems.nextPage
            }

            return items.distinctBy { it.url }.take(limit)
        }

        /**
         * @param overrideTimestamp If non-null, use this instead of re-resolving from the item. This
         *   allows the caller to inject an RSS-derived timestamp.
         */
        private fun streamInfoItemToVideo(
            item: StreamInfoItem,
            channelId: String,
            channelAvatar: String?,
            forceShort: Boolean = false,
            forceLive: Boolean = false,
            overrideTimestamp: Long? = null,
        ): Video {
            val videoId = extractVideoId(item.url)
            val thumbnail =
                ThumbnailUrlResolver.normalizeVideoThumbnail(
                    videoId,
                    item.thumbnails.maxByOrNull { it.width }?.url,
                )

            val uploadTimeMillis = overrideTimestamp ?: resolveUploadTimestamp(item) ?: 0L

            val now = System.currentTimeMillis()
            val rawDate = item.textualUploadDate
            val upcomingReleaseTimeMs =
                rawDate
                    ?.let(::parsePremiereTimestamp)
                    ?.takeIf { it > now + 60_000L }
                    ?: overrideTimestamp?.takeIf { it > now + 60_000L }
            val isUpcoming = !forceLive && upcomingReleaseTimeMs != null
            val isArchivedLivestream = forceLive && !item.isActiveLiveStream() && !isUpcoming
            val uploadDateStr =
                when {
                    isUpcoming && rawDate != null && !rawDate.contains("T") && !rawDate.contains("+") -> {
                        rawDate
                    }

                    uploadTimeMillis > 0L -> {
                        formatRelativeTime(uploadTimeMillis)
                            .let { if (isArchivedLivestream) "Streamed $it" else it }
                    }

                    rawDate != null && !rawDate.contains("T") && !rawDate.contains("+") -> {
                        if (isArchivedLivestream && !rawDate.startsWith("Streamed", ignoreCase = true)) {
                            "Streamed $rawDate"
                        } else {
                            rawDate
                        }
                    }

                    else -> {
                        ""
                    }
                }

            return Video(
                id = videoId,
                title = item.name ?: UNKNOWN_LABEL,
                channelName = item.uploaderName ?: UNKNOWN_LABEL,
                channelId = channelId,
                thumbnailUrl = thumbnail,
                duration = item.duration.toInt().coerceAtLeast(0),
                viewCount = item.viewCount.coerceAtLeast(0L),
                uploadDate = uploadDateStr,
                timestamp = uploadTimeMillis,
                channelThumbnailUrl =
                    channelAvatar?.takeIf { it.isNotBlank() }
                        ?: item.uploaderAvatars
                            .maxByOrNull { it.height }
                            ?.url
                            ?.let { ThumbnailUrlResolver.resolveChannelAvatar(it) }
                        ?: "",
                isShort = forceShort || item.isLikelyShort(),
                isLive = forceLive || item.isActiveLiveStream(),
                isUpcoming = isUpcoming,
            )
        }

        private fun StreamInfoItem.isActiveLiveStream(): Boolean =
            streamType == StreamType.LIVE_STREAM || streamType == StreamType.AUDIO_LIVE_STREAM

        private fun StreamInfoItem.isLikelyShort(): Boolean = isShortFormContent || url.contains("/shorts/", ignoreCase = true)

        private fun formatRelativeTime(timestampMillis: Long): String = formatYouTubeRelativeTime(timestampMillis)

        private fun extractVideoId(url: String): String =
            when {
                url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
                url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
                url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?").substringBefore("/")
                else -> url.substringAfterLast("/").substringBefore("?")
            }

        private fun resolveUploadTimestamp(item: StreamInfoItem): Long? {
            val absolute =
                item.uploadDate
                    ?.offsetDateTime()
                    ?.toInstant()
                    ?.toEpochMilli()
            if (absolute != null && absolute > 0L) return absolute

            val textual = item.textualUploadDate?.trim().orEmpty()
            if (textual.isBlank()) return null

            return parseRelativeUploadDate(textual)
        }

        private fun StreamInfoItem.isPaidOrMembersOnly(): Boolean {
            if (contentAvailability == ContentAvailability.PAID ||
                contentAvailability == ContentAvailability.MEMBERSHIP
            ) {
                return true
            }
            return containsRestrictionMarker(listOfNotNull(name, shortDescription).joinToString(" "))
        }

        private fun ChannelRssEntry.isPaidOrMembersOnly(): Boolean =
            containsRestrictionMarker(listOfNotNull(title, description).joinToString(" "))

        private fun containsRestrictionMarker(text: String): Boolean {
            val normalized = text.lowercase(Locale.US)
            return RESTRICTION_MARKERS.any { marker -> normalized.contains(marker) }
        }

        private fun parseRelativeUploadDate(text: String): Long? {
            val normalized =
                text
                    .lowercase(Locale.US)
                    .replace("streamed", "")
                    .replace("premiered", "")
                    .replace("live", "")
                    .replace("ago", "")
                    .trim()

            if (normalized.isBlank()) return null
            if (normalized.contains("just now") || normalized.contains("today")) return System.currentTimeMillis()
            if (normalized.contains("yesterday")) return System.currentTimeMillis() - 24L * 60L * 60L * 1000L

            val value =
                Regex("(\\d+)")
                    .find(normalized)
                    ?.groupValues
                    ?.getOrNull(1)
                    ?.toLongOrNull() ?: return null
            val unitMillis =
                when {
                    normalized.contains("second") || normalized.endsWith("s") -> 1_000L
                    normalized.contains("minute") || normalized.endsWith("m") -> 60_000L
                    normalized.contains("hour") || normalized.endsWith("h") -> 3_600_000L
                    normalized.contains("day") || normalized.endsWith("d") -> 86_400_000L
                    normalized.contains("week") || normalized.endsWith("w") -> 7L * 86_400_000L
                    normalized.contains("month") || normalized.endsWith("mo") -> 30L * 86_400_000L
                    normalized.contains("year") || normalized.endsWith("y") -> 365L * 86_400_000L
                    else -> return null
                }

            return System.currentTimeMillis() - (value * unitMillis)
        }

        private companion object {
            const val TAG = "InnertubeSubs"
            const val YOUTUBE_URL = "https://www.youtube.com"
            const val UNKNOWN_LABEL = "Unknown"

            const val RSS_CHUNK_SIZE = 20
            const val CHANNEL_CHUNK_SIZE = 3
            const val CHANNEL_BATCH_SIZE = 50
            val CHANNEL_BATCH_DELAY = (100L..400L)
            const val SUBSCRIPTION_FEED_LOOKBACK_DAYS = 60L

            const val MAX_REGULAR_VIDEOS = 1200
            const val MAX_SHORTS = 300
            const val MAX_VIDEOS_PER_CHANNEL = 60
            const val MAX_SHORTS_PER_CHANNEL = 20
            const val MAX_LIVE_PER_CHANNEL = 20

            val RESTRICTION_MARKERS =
                listOf(
                    "members only",
                    "member-only",
                    "requires membership",
                    "join this channel",
                    "channel members",
                    "premium members",
                    "paid content",
                    "paid video",
                    "rent or buy",
                    "buy or rent",
                    "purchase this",
                )
        }
    }
