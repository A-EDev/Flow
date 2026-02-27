package com.flow.youtube.data.innertube

import android.util.Log
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.atomic.AtomicInteger

/**
 * Service for fetching YouTube subscription feeds using NewPipe Extractor.
 * Modeled after LibreTube's LocalFeedRepository:
 * 1. Uses FeedInfo (RSS) for a quick check of recent uploads per channel.
 * 2. If newer uploads exist, fetches full ChannelTabInfo for detailed video data.
 * 3. Processes channels in parallel chunks with delays to avoid throttling.
 */
object RssSubscriptionService {
    private const val TAG = "InnertubeSubs"
    private const val YOUTUBE_URL = "https://www.youtube.com"
    
    private const val CHANNEL_CHUNK_SIZE = 5
    private const val CHANNEL_BATCH_SIZE = 50
    private val CHANNEL_BATCH_DELAY = (500L..1500L)
    private const val MAX_FEED_AGE_DAYS = 90L

    /**
     * Fetch latest videos from subscribed channels using NewPipe Extractor.
     * Progressive loading: emits partial results as channel chunks complete.
     */
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
        val allShorts  = mutableListOf<Video>()
        val channelExtractionCount = AtomicInteger(0)
        val minimumDateMillis = System.currentTimeMillis() - (MAX_FEED_AGE_DAYS * 86400000L)
        Log.i(TAG, "Age cutoff: ${java.util.Date(minimumDateMillis)} (${MAX_FEED_AGE_DAYS}d)")

        val chunks = channelIds.chunked(CHANNEL_CHUNK_SIZE)
        Log.i(TAG, "Processing ${chunks.size} chunks of max $CHANNEL_CHUNK_SIZE channels each")

        for ((chunkIndex, chunk) in chunks.withIndex()) {
            val count = channelExtractionCount.get()
            if (count >= CHANNEL_BATCH_SIZE) {
                Log.i(TAG, "Batch limit reached ($count), throttling...")
                delay(CHANNEL_BATCH_DELAY.random())
                channelExtractionCount.set(0)
            }

            Log.d(TAG, "Chunk ${chunkIndex + 1}/${chunks.size}: fetching ${chunk.size} channels: $chunk")
            val chunkVideos = coroutineScope {
                chunk.map { channelId ->
                    async(Dispatchers.IO) {
                        try {
                            val videos = getChannelVideos(channelId, minimumDateMillis)
                            if (videos.isNotEmpty()) channelExtractionCount.incrementAndGet()
                            videos
                        } catch (e: kotlinx.coroutines.CancellationException) {
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
        val s = shorts.sortedByDescending  { it.timestamp }.take(MAX_SHORTS)
        return (r + s).sortedByDescending { it.timestamp }
    }

    /**
     * Get videos (including Shorts) from a single channel using NewPipe Extractor.
     *
     * Strategy:
     * 1. Use FeedInfo (RSS) ONLY to quickly check if the channel has recent uploads.
     *    RSS does NOT provide duration, isShortFormContent, or proper textualUploadDate.
     * 2. If recent uploads exist, fetch FULL data from ChannelTabInfo for both the
     *    VIDEOS tab and the SHORTS tab (in parallel when both are available).
     * 3. Items from the dedicated SHORTS tab are always marked isShort=true regardless
     *    of whether the extractor sets isShortFormContent.
     * 4. If RSS fails entirely, fall back to ChannelTabInfo directly.
     */
    private suspend fun getChannelVideos(channelId: String, minimumDateMillis: Long): List<Video> {
        val channelUrl = "$YOUTUBE_URL/channel/$channelId"
        val service = NewPipe.getService(0)
        Log.d(TAG, "[$channelId] Starting fetch")

        var hasRecentUploads = true
        try {
            Log.d(TAG, "[$channelId] RSS: requesting FeedInfo...")
            val feedInfo = FeedInfo.getInfo(channelUrl)
            val feedItems = feedInfo.relatedItems.filterIsInstance<StreamInfoItem>()
            Log.d(TAG, "[$channelId] RSS: got ${feedItems.size} items")

            if (feedItems.isNotEmpty()) {
                val parsedTimes = feedItems.mapNotNull { item ->
                    val t = item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
                    Log.d(TAG, "[$channelId] RSS item '${item.name?.take(40)}': rawDate='${item.uploadDate}' → parsed=$t")
                    t
                }
                Log.d(TAG, "[$channelId] RSS: ${parsedTimes.size}/${feedItems.size} dates parsed successfully")

                hasRecentUploads = if (parsedTimes.isEmpty()) {
                    Log.w(TAG, "[$channelId] RSS: ALL dates unparseable — treating as recent (fail-open)")
                    true
                } else {
                    val newest = parsedTimes.max()
                    val isRecent = newest > minimumDateMillis
                    Log.d(TAG, "[$channelId] RSS: newest=${java.util.Date(newest)} cutoff=${java.util.Date(minimumDateMillis)} isRecent=$isRecent")
                    isRecent
                }
            } else {
                Log.w(TAG, "[$channelId] RSS: feed returned 0 items — treating as recent (fail-open)")
            }

            if (!hasRecentUploads) {
                Log.i(TAG, "[$channelId] SKIPPED: no uploads in last ${MAX_FEED_AGE_DAYS}d")
                return emptyList()
            }
        } catch (e: Exception) {
            Log.w(TAG, "[$channelId] RSS FAILED (${e::class.simpleName}): ${e.message} — falling back to ChannelTabInfo")
        }

        try {
            Log.d(TAG, "[$channelId] ChannelInfo: requesting...")
            val channelInfo = ChannelInfo.getInfo(service, channelUrl)
            val channelAvatar = channelInfo.avatars.maxByOrNull { it.height }?.url ?: ""
            val tabNames = channelInfo.tabs.map { it.contentFilters.joinToString() }
            Log.d(TAG, "[$channelId] ChannelInfo: found tabs: $tabNames")

            val videosTab = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.VIDEOS) }
            val shortsTab = channelInfo.tabs.find { it.contentFilters.contains(ChannelTabs.SHORTS) }
            Log.d(TAG, "[$channelId] videosTab=${videosTab != null} shortsTab=${shortsTab != null}")

            if (videosTab == null && shortsTab == null) {
                Log.w(TAG, "[$channelId] No VIDEOS or SHORTS tab found — returning empty")
                return emptyList()
            }

            val (videoItems, shortsItems) = coroutineScope {
                val videoDeferred = videosTab?.let {
                    async(Dispatchers.IO) {
                        runCatching {
                            val items = ChannelTabInfo.getInfo(service, it)
                                .relatedItems.filterIsInstance<StreamInfoItem>().take(15)
                            Log.d(TAG, "[$channelId] VIDEOS tab: ${items.size} items")
                            items
                        }.getOrElse { ex ->
                            Log.e(TAG, "[$channelId] VIDEOS tab FAILED (${ex::class.simpleName}): ${ex.message}")
                            emptyList()
                        }
                    }
                }
                val shortsDeferred = shortsTab?.let {
                    async(Dispatchers.IO) {
                        runCatching {
                            val items = ChannelTabInfo.getInfo(service, it)
                                .relatedItems.filterIsInstance<StreamInfoItem>().take(10)
                            Log.d(TAG, "[$channelId] SHORTS tab: ${items.size} items")
                            items
                        }.getOrElse { ex ->
                            Log.e(TAG, "[$channelId] SHORTS tab FAILED (${ex::class.simpleName}): ${ex.message}")
                            emptyList()
                        }
                    }
                }
                (videoDeferred?.await() ?: emptyList<StreamInfoItem>()) to
                        (shortsDeferred?.await() ?: emptyList())
            }

            val shortsUrls = shortsItems.map { it.url }.toHashSet()
            val combined = (videoItems + shortsItems).distinctBy { it.url }
            Log.d(TAG, "[$channelId] Combined: ${combined.size} unique items (${videoItems.size} videos + ${shortsItems.size} shorts before dedup)")

            val videos = combined.mapNotNull { item ->
                val uploadTimeMillis = item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
                val isOld = uploadTimeMillis != null && uploadTimeMillis <= minimumDateMillis
                if (isOld) {
                    Log.d(TAG, "[$channelId] FILTERED OUT '${item.name?.take(40)}': uploadTime=${java.util.Date(uploadTimeMillis!!)} is older than cutoff")
                    null
                } else {
                    streamInfoItemToVideo(item, channelId, channelAvatar, forceShort = item.url in shortsUrls)
                }
            }

            Log.i(TAG, "[$channelId] RESULT: ${videos.size} videos (${videos.count { it.isShort }} shorts, ${videos.count { !it.isShort }} regular)")
            return videos
        } catch (e: kotlinx.coroutines.CancellationException) {
            Log.w(TAG, "[$channelId] Cancelled — propagating")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "[$channelId] ChannelInfo FAILED (${e::class.simpleName}): ${e.message}")
            return emptyList()
        }
    }

    /**
     * Convert NewPipe StreamInfoItem to our Video model.
     * ChannelTabInfo provides proper duration, textualUploadDate, and isShortFormContent.
     *
     * @param forceShort When true, the item is unconditionally treated as a Short
     *   (e.g. because it came directly from the channel's Shorts tab).
     */
    private fun streamInfoItemToVideo(
        item: StreamInfoItem,
        channelId: String,
        channelAvatar: String?,
        forceShort: Boolean = false
    ): Video {
        val videoId = extractVideoId(item.url)
        val thumbnail = item.thumbnails.maxByOrNull { it.width }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

        val uploadTimeMillis = item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
            ?: System.currentTimeMillis()

        val rawDate = item.textualUploadDate
        val uploadDateStr = when {
            rawDate != null && !rawDate.contains("T") && !rawDate.contains("+") -> rawDate
            else -> {
                val diff = System.currentTimeMillis() - uploadTimeMillis
                val seconds = diff / 1000
                val minutes = seconds / 60
                val hours = minutes / 60
                val days = hours / 24
                val months = days / 30
                val years = days / 365
                when {
                    years > 0 -> "${years}y ago"
                    months > 0 -> "${months}mo ago"
                    days > 0 -> "${days}d ago"
                    hours > 0 -> "${hours}h ago"
                    minutes > 0 -> "${minutes}m ago"
                    else -> "Just now"
                }
            }
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

    private fun extractVideoId(url: String): String {
        return when {
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            else -> url.substringAfterLast("/").substringBefore("?")
        }
    }
}
