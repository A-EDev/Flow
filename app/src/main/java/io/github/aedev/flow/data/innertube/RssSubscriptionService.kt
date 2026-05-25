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
        val fallbackChannelIds = mutableSetOf<String>()

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
                rssDateMap.putAll(result.videoTimestamps)
                result.videos.forEach { if (it.isShort) allShorts.add(it) else allRegular.add(it) }
                if (result.needsFallback || (result.hasRecent && result.videos.isEmpty())) {
                    fallbackChannelIds.add(channelId)
                }
            }
            onProgress?.invoke(((ci + 1) * RSS_CHUNK_SIZE).coerceAtMost(uniqueChannelIds.size), uniqueChannelIds.size)
            emit(buildFeed(allRegular, allShorts, maxTotal))
            if (ci > 0 && ci % (CHANNEL_BATCH_SIZE / RSS_CHUNK_SIZE).coerceAtLeast(1) == 0) {
                delay(CHANNEL_BATCH_DELAY.random())
            }
        }
        Log.i(TAG, "Phase 1 complete: RSS videos=${allRegular.size + allShorts.size}, dates=${rssDateMap.size}, fallbackChannels=${fallbackChannelIds.size}")

        val activeChannelIds = fallbackChannelIds.toList()
        Log.i(TAG, "Phase 2: Fetching full channel tabs for ${activeChannelIds.size} RSS fallback channels")

        var processedChannels = uniqueChannelIds.size
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
            .distinctBy { it.id }
            .take(MAX_REGULAR_VIDEOS)
        val s = shorts
            .sortedByDescending { it.timestamp }
            .distinctBy { it.id }
            .distinctBy { it.channelId.ifBlank { it.id } }
            .take(MAX_SHORTS)
        return (r + s)
            .sortedByDescending { it.timestamp }
            .distinctBy { it.id }
            .take(maxTotal)
    }


    private data class RssResult(
        val hasRecent: Boolean,
        val videoTimestamps: Map<String, Long>,
        val videos: List<Video>,
        val needsFallback: Boolean
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
                return RssResult(
                    hasRecent = true,
                    videoTimestamps = emptyMap(),
                    videos = emptyList(),
                    needsFallback = true
                )
            }

            val timestamps = mutableMapOf<String, Long>()
            val videos = mutableListOf<Video>()
            var newestTimestamp = 0L

            for (item in feedItems) {
                val t = item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: continue
                val videoId = extractVideoId(item.url)
                timestamps[videoId] = t
                if (t > newestTimestamp) {
                    newestTimestamp = t
                }
                if (t > minimumDateMillis && !item.isPaidOrMembersOnly()) {
                    videos += streamInfoItemToVideo(
                        item = item,
                        channelId = channelId,
                        channelAvatar = item.uploaderAvatars.maxByOrNull { it.height }?.url,
                        channelNameOverride = item.uploaderName ?: feedInfo.name,
                        forceShort = item.isLikelyShort(),
                        overrideTimestamp = t
                    )
                }
            }

            if (timestamps.isEmpty()) {
                RssResult(
                    hasRecent = true,
                    videoTimestamps = emptyMap(),
                    videos = emptyList(),
                    needsFallback = true
                )
            } else {
                val hasUnknownRecentUpload = timestamps.any { (videoId, timestamp) ->
                    timestamp > minimumDateMillis && videoId !in knownVideoIds
                }
                RssResult(
                    hasRecent = newestTimestamp > minimumDateMillis || hasUnknownRecentUpload,
                    videoTimestamps = timestamps,
                    videos = videos,
                    needsFallback = false
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "[$channelId] RSS FAILED: ${e::class.simpleName}: ${e.message}")
            RssResult(
                hasRecent = true,
                videoTimestamps = emptyMap(),
                videos = emptyList(),
                needsFallback = true
            )
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

            val (regularVideos, shortsItems, liveItems) = coroutineScope {
                val videoDeferred = videosTab?.let { videosTabHandler ->
                    async(Dispatchers.IO) {
                        fetchDatedTabVideos(
                            tab = videosTabHandler,
                            channelId = channelId,
                            channelAvatar = channelAvatar,
                            channelName = channelInfo.name,
                            minimumDateMillis = minimumDateMillis,
                            rssDateMap = rssDateMap,
                            limit = MAX_VIDEOS_PER_CHANNEL
                        ).ifEmpty {
                            fetchVideosTabInnertube(
                                channelId = channelId,
                                channelName = channelInfo.name,
                                channelAvatar = channelAvatar,
                                limit = MAX_VIDEOS_PER_CHANNEL,
                            ).mapNotNull { video ->
                                video.withAuthoritativeUploadTimestamp(
                                    rssTimestamp = rssDateMap[video.id],
                                    minimumDateMillis = minimumDateMillis
                                )
                            }
                        }
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
            val combined = (shortsItems + liveItems).distinctBy { it.url }

            val tabVideos = combined.mapNotNull { item ->
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
            val videos = (regularVideos.filter { it.timestamp > minimumDateMillis } + tabVideos)
                .distinctBy { it.id }

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

    private fun fetchDatedTabVideos(
        tab: ListLinkHandler,
        channelId: String,
        channelAvatar: String?,
        minimumDateMillis: Long,
        rssDateMap: Map<String, Long>,
        limit: Int,
        channelName: String? = null
    ): List<Video> {
        return fetchTabItems(tab, limit).mapNotNull { item ->
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
                    channelNameOverride = channelName,
                    overrideTimestamp = uploadTimeMillis
                )
            }
        }
    }

    private suspend fun fetchVideosTabInnertube(
        channelId: String,
        channelName: String,
        channelAvatar: String?,
        limit: Int
    ): List<Video> {
        val items = mutableListOf<Video>()
        val initial = io.github.aedev.flow.innertube.YouTube.channelVideos(
            channelId = channelId,
            channelName = channelName,
            channelThumbnailUrl = channelAvatar.orEmpty(),
        ).getOrElse {
            Log.w(TAG, "[$channelId] Innertube videos initial fetch failed: ${it::class.simpleName}: ${it.message}")
            return emptyList()
        }

        items += initial.videos
        var continuation = initial.continuation

        while (items.size < limit && continuation != null) {
            val pageToken = continuation
            val moreResult = io.github.aedev.flow.innertube.YouTube.channelVideosContinuation(
                continuation = pageToken,
                channelId = channelId,
                channelName = channelName,
                channelThumbnailUrl = channelAvatar.orEmpty(),
            )
            if (moreResult.isFailure) {
                val e = moreResult.exceptionOrNull()
                Log.w(TAG, "[$channelId] Innertube videos continuation failed: ${e?.let { it::class.simpleName }}: ${e?.message}")
                break
            }
            val more = moreResult.getOrThrow()
            if (more.videos.isEmpty()) break
            items += more.videos
            continuation = more.continuation
        }

        return items.distinctBy { it.id }.take(limit)
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
            viewCount = item.viewCount,
            uploadDate = uploadDateStr,
            timestamp = uploadTimeMillis,
            channelThumbnailUrl = channelAvatar
                ?: item.uploaderAvatars.maxByOrNull { it.height }?.url
                ?: "",
            isShort = forceShort || item.isLikelyShort(),
            isLive = forceLive || item.isActiveLiveStream(),
            isUpcoming = isUpcoming
        )
    }

    private fun Video.withAuthoritativeUploadTimestamp(
        rssTimestamp: Long?,
        minimumDateMillis: Long
    ): Video? {
        val resolvedTimestamp = rssTimestamp ?: timestamp.takeIf { it > 0L && !looksLikeLoadedNowFallback() }
        if (resolvedTimestamp == null) {
            Log.d(TAG, "Skipping channel-tab item with loaded-time fallback only: $id")
            return null
        }
        if (resolvedTimestamp <= minimumDateMillis && !isUpcoming) return null

        val now = System.currentTimeMillis()
        return copy(
            timestamp = resolvedTimestamp,
            uploadDate = rssTimestamp?.let(::formatRelativeTime) ?: uploadDate,
            isUpcoming = isUpcoming && resolvedTimestamp > now + 60_000L
        )
    }

    private fun Video.looksLikeLoadedNowFallback(): Boolean {
        val text = uploadDate.trim().lowercase(Locale.US)
        return text.isBlank() ||
            text == "unknown" ||
            text == "just now" ||
            text.matches(Regex("""\d+\s*(s|sec|secs|second|seconds|m|min|mins|minute|minutes|h|hr|hrs|hour|hours)(\s+ago)?"""))
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