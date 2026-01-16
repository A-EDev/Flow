package com.flow.youtube.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.stream.StreamInfoItem

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
        val thumbnail = this.thumbnails.maxByOrNull { it.width }?.url 
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        
        return Video(
            id = videoId,
            title = this.name,
            thumbnailUrl = thumbnail,
            channelName = this.uploaderName ?: channelInfo.name,
            channelId = channelInfo.id,
            channelThumbnailUrl = channelInfo.avatars.firstOrNull()?.url ?: "",
            viewCount = this.viewCount,
            duration = this.duration.toInt(),
            // Format date to human-readable format (e.g., "2 days ago")
            uploadDate = com.flow.youtube.utils.formatTimeAgo(this.uploadDate?.offsetDateTime()?.toString()),
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
}
