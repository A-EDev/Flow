package io.github.aedev.flow.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.Locale

/**
 * PagingSource for loading channel videos with infinite scroll support.
 * Uses NewPipe extractor's pagination mechanism for efficient loading.
 */
class ChannelVideosPagingSource(
    private val channelInfo: ChannelInfo,
    private val videosTab: ListLinkHandler?
) : PagingSource<Page, Video>() {
    
    companion object {
        private const val TAG = "ChannelVideosPaging"
    }
    
    override fun getRefreshKey(state: PagingState<Page, Video>): Page? {
        // Return null to start from the beginning on refresh
        return null
    }
    
    override suspend fun load(params: LoadParams<Page>): LoadResult<Page, Video> {
        return try {
            withContext(Dispatchers.IO) {
                val page = params.key
                
                Log.d(TAG, "Loading page: ${page?.url ?: "initial"}")
                
                val videos = mutableListOf<Video>()
                val nextPage: Page?
                
                if (videosTab == null) {
                    Log.w(TAG, "No videos tab found")
                    return@withContext LoadResult.Page(
                        data = emptyList(),
                        prevKey = null,
                        nextKey = null
                    )
                }
                
                if (page == null) {
                    // Initial load - get from tab info
                    val tabInfo = ChannelTabInfo.getInfo(NewPipe.getService(0), videosTab)
                    nextPage = tabInfo.nextPage
                    
                    // Convert items to videos
                    tabInfo.relatedItems.filterIsInstance<StreamInfoItem>().forEach { item ->
                        videos.add(item.toVideo(channelInfo))
                    }
                    
                    Log.d(TAG, "Initial load: ${videos.size} videos, hasNextPage: ${nextPage != null}")
                } else {
                    // Load more - use the page token
                    val moreItems = ChannelTabInfo.getMoreItems(NewPipe.getService(0), videosTab, page)
                    nextPage = moreItems.nextPage
                    
                    // Convert items to videos
                    moreItems.items.filterIsInstance<StreamInfoItem>().forEach { item ->
                        videos.add(item.toVideo(channelInfo))
                    }
                    
                    Log.d(TAG, "More load: ${videos.size} videos, hasNextPage: ${nextPage != null}")
                }
                
                LoadResult.Page(
                    data = videos,
                    prevKey = null, // Only forward pagination
                    nextKey = nextPage
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading channel videos", e)
            LoadResult.Error(e)
        }
    }
    
    private fun StreamInfoItem.toVideo(channelInfo: ChannelInfo): Video {
        val videoId = extractVideoId(this.url)
        // Use highest resolution thumbnail for better quality
        val thumbnail = ThumbnailUrlResolver.normalizeVideoThumbnail(
            videoId,
            this.thumbnails.maxByOrNull { it.width }?.url
        )
        val absoluteUploadTimestamp = this.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli()
        val textualDate = this.textualUploadDate?.takeIf { it.isNotBlank() }
        val displayUploadDate = textualDate
            ?: io.github.aedev.flow.utils.formatTimeAgo(this.uploadDate?.offsetDateTime()?.toString())
        val uploadTimestamp = absoluteUploadTimestamp
            ?: parseRelativeUploadDate(textualDate)
            ?: 0L
        
        return Video(
            id = videoId,
            title = this.name,
            thumbnailUrl = thumbnail,
            channelName = this.uploaderName ?: channelInfo.name,
            channelId = channelInfo.id,
            channelThumbnailUrl = channelInfo.avatars.maxByOrNull { it.height }?.url
                ?: channelInfo.avatars.firstOrNull()?.url
                ?: "",
            viewCount = this.viewCount,
            duration = this.duration.toInt().coerceAtLeast(0),
            uploadDate = displayUploadDate,
            timestamp = uploadTimestamp,
            description = ""
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

    private fun parseRelativeUploadDate(text: String?): Long? {
        val normalized = text?.lowercase(Locale.US)
            ?.replace("streamed", "")
            ?.replace("premiered", "")
            ?.replace("live", "")
            ?.replace("ago", "")
            ?.trim()
            ?: return null

        if (normalized.isBlank()) return null
        if (normalized.contains("just now") || normalized.contains("today")) return System.currentTimeMillis()
        if (normalized.contains("yesterday")) return System.currentTimeMillis() - 24L * 60L * 60L * 1000L

        val value = Regex("(\\d+)").find(normalized)?.groupValues?.getOrNull(1)?.toLongOrNull()
            ?: return null
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
