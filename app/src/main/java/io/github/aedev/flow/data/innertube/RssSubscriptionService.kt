package io.github.aedev.flow.data.innertube

import android.util.Log
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

object RssSubscriptionService {
    private const val TAG = "InnertubeSubs"
    private const val YOUTUBE_URL = "https://www.youtube.com"

    private const val CHANNEL_CHUNK_SIZE = 8
    private const val CHANNEL_BATCH_SIZE = 50
    private val CHANNEL_BATCH_DELAY = (100L..400L)
    private const val MAX_FEED_AGE_DAYS = 90L

    private const val MAX_REGULAR_VIDEOS = 150
    private const val MAX_SHORTS = 60

    fun fetchSubscriptionVideos(channelIds: List<String>, maxTotal: Int = 200): Flow<List<Video>> = flow {
        Log.i(TAG, "======== FEED FETCH START: ${channelIds.size} channels ========")
        if (channelIds.isEmpty()) {
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

        Log.i(TAG, "Phase 1: Fetching RSS dates for all ${channelIds.size} channels")
        val rssChunks = channelIds.chunked(CHANNEL_CHUNK_SIZE)
        for ((ci, chunk) in rssChunks.withIndex()) {
            val results = coroutineScope {
                chunk.map { channelId ->
                    async(Dispatchers.IO) {
                        channelId to fetchRssDates(channelId, minimumDateMillis)
                    }
                }.awaitAll()
            }
            for ((channelId, result) in results) {
                rssChannelHasRecent[channelId] = result.hasRecent
                rssDateMap.putAll(result.videoTimestamps)
            }
            if (ci > 0 && ci % (CHANNEL_BATCH_SIZE / CHANNEL_CHUNK_SIZE) == 0) {
                delay(CHANNEL_BATCH_DELAY.random())
            }
        }
        Log.i(TAG, "Phase 1 complete: RSS dates for ${rssDateMap.size} videos from ${channelIds.size} channels")

        val activeChannelIds = channelIds.filter { rssChannelHasRecent[it] != false }
        Log.i(TAG, "Phase 2: Fetching tabs for ${activeChannelIds.size} active channels (${channelIds.size - activeChannelIds.size} skipped as stale)")

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
            Log.d(TAG, "Chunk ${chunkIndex + 1} done: +${chunkVideos.size} (regular=${allRegular.size}, shorts=${allShorts.size})")

            emit(buildFeed(allRegular, allShorts))
        }

        emit(buildFeed(allRegular, allShorts))
        Log.i(TAG, "======== FEED FETCH COMPLETE: regular=${allRegular.size.coerceAtMost(MAX_REGULAR_VIDEOS)} shorts=${allShorts.size.coerceAtMost(MAX_SHORTS)} from ${channelIds.size} channels ========")
    }

    /** Merge regular and shorts lists with independent caps, sorted by date. */
    private fun buildFeed(regular: List<Video>, shorts: List<Video>): List<Video> {
        val r = regular.sortedByDescending { it.timestamp }.take(MAX_REGULAR_VIDEOS)
        val s = shorts.sortedByDescending { it.timestamp }.take(MAX_SHORTS)
        return (r + s).sortedByDescending { it.timestamp }
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
    private fun fetchRssDates(channelId: String, minimumDateMillis: Long): RssResult {
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
                val t = item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: continue
                val videoId = extractVideoId(item.url)
                timestamps[videoId] = t
                if (t > newestTimestamp) newestTimestamp = t
            }

            if (timestamps.isEmpty()) {
                RssResult(hasRecent = true, videoTimestamps = emptyMap())
            } else {
                RssResult(
                    hasRecent = newestTimestamp > minimumDateMillis,
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

            if (videosTab == null && shortsTab == null) {
                Log.w(TAG, "[$channelId] No VIDEOS or SHORTS tab found — returning empty")
                return emptyList()
            }

            val (videoItems, shortsItems) = coroutineScope {
                val videoDeferred = videosTab?.let {
                    async(Dispatchers.IO) {
                        runCatching {
                            ChannelTabInfo.getInfo(service, it)
                                .relatedItems.filterIsInstance<StreamInfoItem>().take(15)
                        }.getOrElse { emptyList() }
                    }
                }
                val shortsDeferred = shortsTab?.let {
                    async(Dispatchers.IO) {
                        runCatching {
                            ChannelTabInfo.getInfo(service, it)
                                .relatedItems.filterIsInstance<StreamInfoItem>().take(10)
                        }.getOrElse { emptyList() }
                    }
                }
                (videoDeferred?.await() ?: emptyList<StreamInfoItem>()) to
                        (shortsDeferred?.await() ?: emptyList())
            }

            val shortsUrls = shortsItems.map { it.url }.toHashSet()
            val combined = (videoItems + shortsItems).distinctBy { it.url }

            val videos = combined.mapNotNull { item ->
                val videoId = extractVideoId(item.url)

                val uploadTimeMillis = resolveUploadTimestamp(item)
                    ?: rssDateMap[videoId]

                val isOld = uploadTimeMillis != null && uploadTimeMillis <= minimumDateMillis
                if (isOld) {
                    null
                } else {
                    streamInfoItemToVideo(
                        item = item,
                        channelId = channelId,
                        channelAvatar = channelAvatar,
                        forceShort = item.url in shortsUrls,
                        overrideTimestamp = uploadTimeMillis
                    )
                }
            }

            Log.i(TAG, "[$channelId] RESULT: ${videos.size} videos (${videos.count { it.isShort }} shorts, ${videos.count { !it.isShort }} regular)")
            return videos
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[$channelId] ChannelInfo FAILED (${e::class.simpleName}): ${e.message}")
            return emptyList()
        }
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
        overrideTimestamp: Long? = null
    ): Video {
        val videoId = extractVideoId(item.url)
        val thumbnail = item.thumbnails.maxByOrNull { it.width }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val uploadTimeMillis = overrideTimestamp
            ?: resolveUploadTimestamp(item)
            ?: System.currentTimeMillis()

        val rawDate = item.textualUploadDate
        val uploadDateStr = when {
            rawDate != null && !rawDate.contains("T") && !rawDate.contains("+") -> rawDate
            else -> formatRelativeTime(uploadTimeMillis)
        }

        return Video(
            id = videoId,
            title = item.name ?: "Unknown",
            channelName = item.uploaderName ?: "Unknown",
            channelId = channelId,
            thumbnailUrl = thumbnail,
            duration = item.duration.toInt().coerceAtLeast(0),
            viewCount = item.viewCount,
            uploadDate = uploadDateStr,
            timestamp = uploadTimeMillis,
            channelThumbnailUrl = channelAvatar
                ?: item.uploaderAvatars?.maxByOrNull { it.height }?.url
                ?: "",
            isShort = forceShort || item.isShortFormContent,
            isLive = item.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
        )
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
            normalized.contains("second") -> 1_000L
            normalized.contains("minute") -> 60_000L
            normalized.contains("hour") -> 3_600_000L
            normalized.contains("day") -> 86_400_000L
            normalized.contains("week") -> 7L * 86_400_000L
            normalized.contains("month") -> 30L * 86_400_000L
            normalized.contains("year") -> 365L * 86_400_000L
            else -> return null
        }

        return System.currentTimeMillis() - (value * unitMillis)
    }
}