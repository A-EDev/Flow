package io.github.aedev.flow.data.innertube

import android.util.Log
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import io.github.aedev.flow.utils.parsePremiereTimestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.ContentAvailability
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

object RssSubscriptionService {
    private const val TAG = "InnertubeSubs"
    private const val YOUTUBE_URL = "https://www.youtube.com"

    private const val RSS_CHUNK_SIZE = 24
    private const val CHANNEL_CHUNK_SIZE = 8
    private const val CHANNEL_BATCH_SIZE = 50
    private val CHANNEL_BATCH_DELAY = (100L..400L)
    private const val MAX_FEED_AGE_DAYS = 60L

    private const val MAX_REGULAR_VIDEOS = 500
    private const val MAX_SHORTS = 120
    private const val MAX_VIDEOS_PER_CHANNEL = 60
    private const val MAX_SHORTS_PER_CHANNEL = 20
    private const val MAX_LIVE_PER_CHANNEL = 20

    fun fetchSubscriptionVideos(
        channelIds: List<String>,
        maxTotal: Int = 600,
        knownVideoIds: Set<String> = emptySet(),
        onProgress: ((processedChannels: Int, totalChannels: Int) -> Unit)? = null
    ): Flow<List<Video>> = flow {
        val uniqueChannelIds = channelIds.distinct()
        Log.i(TAG, "======== FEED FETCH START: ${uniqueChannelIds.size} channels (requested=${channelIds.size}) ========")
        if (uniqueChannelIds.isEmpty()) {
            Log.w(TAG, "No channel IDs provided — emitting empty list")
            emit(emptyList())
            return@flow
        }

        val allRegular = mutableListOf<Video>()
        val allShorts = mutableListOf<Video>()
        val channelExtractionCount = AtomicInteger(0)
        val minimumDateMillis = System.currentTimeMillis() - (MAX_FEED_AGE_DAYS * 86400000L)
        Log.i(TAG, "Age cutoff: ${java.util.Date(minimumDateMillis)} (${MAX_FEED_AGE_DAYS}d)")

        // ── Fetch RSS dates for ALL channels upfront ────────────────────────
        val rssDateMap = mutableMapOf<String, Long>()
        val rssChannelHasRecent = mutableMapOf<String, Boolean>()

        Log.i(TAG, "Phase 1: Fetching RSS feeds for all ${uniqueChannelIds.size} channels")
        val rssChunks = uniqueChannelIds.chunked(RSS_CHUNK_SIZE)
        for ((ci, chunk) in rssChunks.withIndex()) {
            val results = coroutineScope {
                chunk.map { channelId ->
                    async(Dispatchers.IO) {
                        channelId to fetchRssDates(channelId, minimumDateMillis, knownVideoIds)
                    }
                }.awaitAll()
            }
            for ((channelId, result) in results) {
                rssChannelHasRecent[channelId] = result.hasRecent
                rssDateMap.putAll(result.videoTimestamps)
            }
            onProgress?.invoke(((ci + 1) * RSS_CHUNK_SIZE).coerceAtMost(uniqueChannelIds.size), uniqueChannelIds.size)
            emit(buildFeed(allRegular, allShorts, maxTotal))
            if (ci > 0 && ci % (CHANNEL_BATCH_SIZE / RSS_CHUNK_SIZE).coerceAtLeast(1) == 0) {
                delay(CHANNEL_BATCH_DELAY.random())
            }
        }
        Log.i(TAG, "Phase 1 complete: RSS dates for ${rssDateMap.size} videos from ${uniqueChannelIds.size} channels")

        val activeChannelIds = uniqueChannelIds.filter { rssChannelHasRecent[it] != false }
        Log.i(TAG, "Phase 2: Fetching NewPipe channel tabs for ${activeChannelIds.size} active channels (${uniqueChannelIds.size - activeChannelIds.size} skipped as stale)")

        var processedChannels = uniqueChannelIds.size - activeChannelIds.size
        if (processedChannels > 0) {
            onProgress?.invoke(processedChannels, uniqueChannelIds.size)
        }
        val chunks = activeChannelIds.chunked(CHANNEL_CHUNK_SIZE)
        for ((chunkIndex, chunk) in chunks.withIndex()) {
            val count = channelExtractionCount.get()
            if (count >= CHANNEL_BATCH_SIZE) {
                Log.i(TAG, "Batch limit reached ($count), throttling...")
                delay(CHANNEL_BATCH_DELAY.random())
                channelExtractionCount.set(0)
            }

            Log.d(TAG, "Chunk ${chunkIndex + 1}/${chunks.size}: fetching ${chunk.size} channels")
            val chunkVideos = coroutineScope {
                chunk.map { channelId ->
                    async(Dispatchers.IO) {
                        try {
                            val videos = getChannelVideos(channelId, minimumDateMillis, rssDateMap)
                            if (videos.isNotEmpty()) channelExtractionCount.incrementAndGet()
                            videos
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Log.e(TAG, "UNCAUGHT in channel $channelId: ${e::class.simpleName}: ${e.message}")
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }

            chunkVideos.forEach { if (it.isShort) allShorts.add(it) else allRegular.add(it) }
            processedChannels = (processedChannels + chunk.size).coerceAtMost(uniqueChannelIds.size)
            onProgress?.invoke(processedChannels, uniqueChannelIds.size)
            Log.d(TAG, "Chunk ${chunkIndex + 1} done: +${chunkVideos.size} (regular=${allRegular.size}, shorts=${allShorts.size})")

            emit(buildFeed(allRegular, allShorts, maxTotal))
        }

        emit(buildFeed(allRegular, allShorts, maxTotal))
        Log.i(TAG, "======== FEED FETCH COMPLETE: regular=${allRegular.size.coerceAtMost(MAX_REGULAR_VIDEOS)} shorts=${allShorts.size.coerceAtMost(MAX_SHORTS)} from ${uniqueChannelIds.size} channels ========")
    }

    /** Merge regular and shorts lists with independent caps, sorted by date. */
    private fun buildFeed(regular: List<Video>, shorts: List<Video>, maxTotal: Int): List<Video> {
        val r = regular
            .sortedByDescending { it.timestamp }
            .mergeDuplicateVideos()
            .take(MAX_REGULAR_VIDEOS)
        val s = shorts
            .sortedByDescending { it.timestamp }
            .mergeDuplicateVideos()
            .distinctBy { it.channelId.ifBlank { it.id } }
            .take(MAX_SHORTS)
        return (r + s)
            .sortedByDescending { it.timestamp }
            .mergeDuplicateVideos()
            .take(maxTotal)
    }

    private fun List<Video>.mergeDuplicateVideos(): List<Video> {
        val now = System.currentTimeMillis()
        return groupBy { it.id }.values.map { candidates ->
            val primary = candidates.first()
            val timestampSource = candidates
                .filter { it.timestamp > 0L }
                .maxByOrNull { it.timestamp }
                ?: primary
            val bestChannelThumbnail = candidates.firstOrNull { it.channelThumbnailUrl.isNotBlank() }?.channelThumbnailUrl
                ?: primary.channelThumbnailUrl
            val bestVideoThumbnail = candidates
                .asSequence()
                .map { ThumbnailUrlResolver.normalizeVideoThumbnail(it.id, it.thumbnailUrl) }
                .firstOrNull { it.isNotBlank() }
                ?: ThumbnailUrlResolver.normalizeVideoThumbnail(primary.id, primary.thumbnailUrl)
            val bestDescription = candidates.firstOrNull { it.description.isNotBlank() }?.description
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
                isUpcoming = candidates.any { it.isUpcoming && it.timestamp > now + 60_000L }
            )
        }.sortedByDescending { it.timestamp }
    }


    private data class RssResult(
        val hasRecent: Boolean,
        val videoTimestamps: Map<String, Long>
    )

    /**
     * Fetch RSS feed for a channel and extract video timestamps.
     * RSS provides accurate dates for ALL recent uploads (including shorts)
     * but doesn't tell us duration or whether something is a short.
     */
    private fun fetchRssDates(
        channelId: String,
        minimumDateMillis: Long,
        knownVideoIds: Set<String>
    ): RssResult {
        val channelUrl = "$YOUTUBE_URL/channel/$channelId"
        return try {
            val feedInfo = FeedInfo.getInfo(channelUrl)
            val feedItems = feedInfo.relatedItems.filterIsInstance<StreamInfoItem>()

            if (feedItems.isEmpty()) {
                return RssResult(hasRecent = true, videoTimestamps = emptyMap())
            }

            val timestamps = mutableMapOf<String, Long>()
            var newestTimestamp = 0L

            for (item in feedItems) {
                val videoId = extractVideoId(item.url)
                val t = item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: continue
                timestamps[videoId] = t
                if (t > newestTimestamp) {
                    newestTimestamp = t
                }
            }

            if (timestamps.isEmpty()) {
                RssResult(hasRecent = true, videoTimestamps = emptyMap())
            } else {
                val hasUnknownRecentUpload = timestamps.any { (videoId, timestamp) ->
                    timestamp > minimumDateMillis && videoId !in knownVideoIds
                }
                RssResult(
                    hasRecent = newestTimestamp > minimumDateMillis || hasUnknownRecentUpload,
                    videoTimestamps = timestamps
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "[$channelId] RSS FAILED: ${e::class.simpleName}: ${e.message}")
            RssResult(hasRecent = true, videoTimestamps = emptyMap())
        }
    }

    /**
     * Get videos (including Shorts) from a single channel using NewPipe Extractor.
     *
     * @param rssDateMap Pre-fetched RSS timestamps keyed by video ID. Used to assign
     *   accurate upload dates to Shorts tab items which lack date metadata.
     */
    private suspend fun getChannelVideos(
        channelId: String,
        minimumDateMillis: Long,
        rssDateMap: Map<String, Long>
    ): List<Video> {
        val channelUrl = "$YOUTUBE_URL/channel/$channelId"
        val service = NewPipe.getService(0)
        Log.d(TAG, "[$channelId] Starting tab fetch")

        try {
            val channelInfo = ChannelInfo.getInfo(service, channelUrl)
            val channelAvatar = channelInfo.avatars.maxByOrNull { it.height }?.url  // null if empty → fallback below kicks in
            val tabNames = channelInfo.tabs.map { it.contentFilters.joinToString() }
            Log.d(TAG, "[$channelId] ChannelInfo: found tabs: $tabNames")

            val videosTab = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.VIDEOS) }
            val shortsTab = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.SHORTS) }
            val liveTab = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.LIVESTREAMS) }

            if (videosTab == null && shortsTab == null && liveTab == null) {
                Log.w(TAG, "[$channelId] No VIDEOS or SHORTS tab found — returning empty")
                return emptyList()
            }

            val (videoItems, shortsItems, liveItems) = coroutineScope {
                val videoDeferred = videosTab?.let {
                    async(Dispatchers.IO) {
                        fetchTabItems(it, MAX_VIDEOS_PER_CHANNEL)
                    }
                }
                val shortsDeferred = shortsTab?.let {
                    async(Dispatchers.IO) {
                        fetchTabItems(it, MAX_SHORTS_PER_CHANNEL)
                    }
                }
                val liveDeferred = liveTab?.let {
                    async(Dispatchers.IO) {
                        fetchTabItems(it, MAX_LIVE_PER_CHANNEL)
                    }
                }
                Triple(
                    videoDeferred?.await() ?: emptyList(),
                    shortsDeferred?.await() ?: emptyList(),
                    liveDeferred?.await() ?: emptyList()
                )
            }

            val shortsUrls = shortsItems.map { it.url }.toHashSet()
            val liveUrls = liveItems.map { it.url }.toHashSet()
            val combined = (videoItems + shortsItems + liveItems).distinctBy { it.url }

            val videos = combined.mapNotNull { item ->
                val videoId = extractVideoId(item.url)
                if (item.isPaidOrMembersOnly()) {
                    Log.d(TAG, "[$channelId] Skipping restricted subscription item: $videoId")
                    return@mapNotNull null
                }

                val uploadTimeMillis = rssDateMap[videoId]
                    ?: resolveUploadTimestamp(item)

                when {
                    uploadTimeMillis == null -> {
                        Log.d(TAG, "[$channelId] Skipping undated subscription item: $videoId")
                        null
                    }
                    uploadTimeMillis <= minimumDateMillis -> null
                    else -> streamInfoItemToVideo(
                        item = item,
                        channelId = channelId,
                        channelAvatar = channelAvatar,
                        forceShort = item.url in shortsUrls,
                        forceLive = item.url in liveUrls,
                        overrideTimestamp = uploadTimeMillis
                    )
                }
            }

            Log.i(TAG, "[$channelId] RESULT: ${videos.size} videos (${videos.count { it.isShort }} shorts, ${videos.count { it.isLive }} live)")
            return videos
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[$channelId] ChannelInfo FAILED (${e::class.simpleName}): ${e.message}")
            return emptyList()
        }
    }

    private fun fetchTabItems(tab: ListLinkHandler, limit: Int): List<StreamInfoItem> {
        val service = NewPipe.getService(0)
        val items = mutableListOf<StreamInfoItem>()
        var nextPage: org.schabi.newpipe.extractor.Page? = null

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
            val moreItems = try {
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
     * Convert NewPipe StreamInfoItem to our Video model.
     *
     * @param overrideTimestamp If non-null, use this instead of re-resolving from the item.
     *   This allows the caller to inject an RSS-derived timestamp.
     */
    private fun streamInfoItemToVideo(
        item: StreamInfoItem,
        channelId: String,
        channelAvatar: String?,
        forceShort: Boolean = false,
        forceLive: Boolean = false,
        channelNameOverride: String? = null,
        overrideTimestamp: Long? = null
    ): Video {
        val videoId = extractVideoId(item.url)
        val thumbnail = ThumbnailUrlResolver.normalizeVideoThumbnail(
            videoId,
            item.thumbnails.maxByOrNull { it.width }?.url
        )

        val uploadTimeMillis = overrideTimestamp
            ?: resolveUploadTimestamp(item)
            ?: 0L

        val now = System.currentTimeMillis()
        val rawDate = item.textualUploadDate
        val upcomingReleaseTimeMs = rawDate
            ?.let(::parsePremiereTimestamp)
            ?.takeIf { it > now + 60_000L }
            ?: overrideTimestamp?.takeIf { it > now + 60_000L }
        val isUpcoming = !forceLive && upcomingReleaseTimeMs != null
        val uploadDateStr = when {
            isUpcoming && rawDate != null && !rawDate.contains("T") && !rawDate.contains("+") -> rawDate
            uploadTimeMillis > 0L -> formatRelativeTime(uploadTimeMillis)
            rawDate != null && !rawDate.contains("T") && !rawDate.contains("+") -> rawDate
            else -> ""
        }

        return Video(
            id = videoId,
            title = item.name ?: "Unknown",
            channelName = channelNameOverride ?: item.uploaderName ?: "Unknown",
            channelId = channelId,
            thumbnailUrl = thumbnail,
            duration = item.duration.toInt().coerceAtLeast(0),
            viewCount = item.viewCount.coerceAtLeast(0L),
            uploadDate = uploadDateStr,
            timestamp = uploadTimeMillis,
            channelThumbnailUrl = channelAvatar?.takeIf { it.isNotBlank() }
                ?: item.uploaderAvatars.maxByOrNull { it.height }?.url
                ?: "",
            isShort = forceShort || item.isLikelyShort(),
            isLive = forceLive || item.isActiveLiveStream(),
            isUpcoming = isUpcoming
        )
    }

    private fun StreamInfoItem.isActiveLiveStream(): Boolean {
        return streamType == StreamType.LIVE_STREAM ||
            streamType == StreamType.AUDIO_LIVE_STREAM
    }

    private fun StreamInfoItem.isLikelyShort(): Boolean {
        return isShortFormContent || url.contains("/shorts/", ignoreCase = true)
    }

    /** Format a millisecond timestamp as a human-readable relative string. */
    private fun formatRelativeTime(timestampMillis: Long): String {
        val diff = System.currentTimeMillis() - timestampMillis
        if (diff < 0) return "Just now"
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val months = days / 30
        val years = days / 365
        return when {
            years > 0 -> "${years}y ago"
            months > 0 -> "${months}mo ago"
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    }

    private fun extractVideoId(url: String): String {
        return when {
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?").substringBefore("/")
            else -> url.substringAfterLast("/").substringBefore("?")
        }
    }

    private fun resolveUploadTimestamp(item: StreamInfoItem): Long? {
        val absolute = item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
        if (absolute != null && absolute > 0L) return absolute

        val textual = item.textualUploadDate?.trim().orEmpty()
        if (textual.isBlank()) return null

        return parseRelativeUploadDate(textual)
    }

    private fun StreamInfoItem.isPaidOrMembersOnly(): Boolean {
        return contentAvailability == ContentAvailability.PAID ||
            contentAvailability == ContentAvailability.MEMBERSHIP
    }

    private fun parseRelativeUploadDate(text: String): Long? {
        val normalized = text.lowercase(Locale.US)
            .replace("streamed", "")
            .replace("premiered", "")
            .replace("live", "")
            .replace("ago", "")
            .trim()

        if (normalized.isBlank()) return null
        if (normalized.contains("just now") || normalized.contains("today")) return System.currentTimeMillis()
        if (normalized.contains("yesterday")) return System.currentTimeMillis() - 24L * 60L * 60L * 1000L

        val value = Regex("(\\d+)").find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return null
        val unitMillis = when {
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
}