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
    private const val MAX_FEED_AGE_DAYS = 30L

    /**
     * Fetch latest videos from subscribed channels using NewPipe Extractor.
     * Progressive loading: emits partial results as channel chunks complete.
     */
    fun fetchSubscriptionVideos(channelIds: List<String>, maxTotal: Int = 200): Flow<List<Video>> = flow {
        if (channelIds.isEmpty()) {
            emit(emptyList())
            return@flow
        }

        val allVideos = mutableListOf<Video>()
        val channelExtractionCount = AtomicInteger(0)
        val minimumDateMillis = System.currentTimeMillis() - (MAX_FEED_AGE_DAYS * 86400000L)

        val chunks = channelIds.chunked(CHANNEL_CHUNK_SIZE)

        for (chunk in chunks) {
            val count = channelExtractionCount.get()
            if (count >= CHANNEL_BATCH_SIZE) {
                delay(CHANNEL_BATCH_DELAY.random())
                channelExtractionCount.set(0)
            }

            val chunkVideos = coroutineScope {
                chunk.map { channelId ->
                    async(Dispatchers.IO) {
                        try {
                            val videos = getChannelVideos(channelId, minimumDateMillis)
                            if (videos.isNotEmpty()) {
                                channelExtractionCount.incrementAndGet()
                            }
                            videos
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to fetch channel $channelId: ${e.message}")
                            emptyList()
                        }
                    }
                }.awaitAll().flatten()
            }

            allVideos.addAll(chunkVideos)

            val sorted = allVideos.sortedByDescending { it.timestamp }.take(maxTotal)
            emit(sorted.toList())
        }

        val finalSorted = allVideos.sortedByDescending { it.timestamp }.take(maxTotal)
        emit(finalSorted)
        Log.d(TAG, "Feed complete: ${finalSorted.size} videos from ${channelIds.size} channels")
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

        var hasRecentUploads = true
        try {
            val feedInfo = FeedInfo.getInfo(channelUrl)
            val feedItems = feedInfo.relatedItems.filterIsInstance<StreamInfoItem>()

            if (feedItems.isNotEmpty()) {
                val mostRecentTime = feedItems.maxOf {
                    it.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: 0
                }
                hasRecentUploads = mostRecentTime > minimumDateMillis
            }

            if (!hasRecentUploads) {
                Log.d(TAG, "No recent uploads for $channelId, skipping")
                return emptyList()
            }
        } catch (e: Exception) {
            Log.d(TAG, "RSS check failed for $channelId: ${e.message}")
        }

        try {
            val channelInfo = ChannelInfo.getInfo(service, channelUrl)
            val channelAvatar = channelInfo.avatars.maxByOrNull { it.height }?.url ?: ""

            val videosTab = channelInfo.tabs.find { tab ->
                tab.contentFilters.contains(ChannelTabs.VIDEOS)
            }
            val shortsTab = channelInfo.tabs.find { tab ->
                tab.contentFilters.contains(ChannelTabs.SHORTS)
            }

            if (videosTab == null && shortsTab == null) return emptyList()

            val (videoItems, shortsItems) = coroutineScope {
                val videoDeferred = videosTab?.let {
                    async(Dispatchers.IO) {
                        runCatching {
                            ChannelTabInfo.getInfo(service, it)
                                .relatedItems
                                .filterIsInstance<StreamInfoItem>()
                                .take(15)
                        }.getOrElse { emptyList() }
                    }
                }
                val shortsDeferred = shortsTab?.let {
                    async(Dispatchers.IO) {
                        runCatching {
                            ChannelTabInfo.getInfo(service, it)
                                .relatedItems
                                .filterIsInstance<StreamInfoItem>()
                                .take(10)
                        }.getOrElse { emptyList() }
                    }
                }
                (videoDeferred?.await() ?: emptyList<StreamInfoItem>()) to
                        (shortsDeferred?.await() ?: emptyList())
            }

            val shortsUrls = shortsItems.map { it.url }.toHashSet()

            val videos = (videoItems + shortsItems)
                .distinctBy { it.url }
                .mapNotNull { item ->
                    val uploadTimeMillis =
                        item.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
                    if (uploadTimeMillis == null || uploadTimeMillis > minimumDateMillis) {
                        streamInfoItemToVideo(
                            item, channelId, channelAvatar,
                            forceShort = item.url in shortsUrls
                        )
                    } else null
                }

            Log.d(TAG, "Fetched ${videos.size} videos from $channelId (${videos.count { it.isShort }} shorts)")
            return videos
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch $channelId: ${e.message}")
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
