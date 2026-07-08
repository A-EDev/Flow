package io.github.aedev.flow.data.paging

import android.util.Log
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.VideoCollaborator
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.utils.avatarImageIdentityKey
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
                        videos.add(item.toVideo(channelInfo).withCollabAvatarStack())
                    }
                    
                    Log.d(TAG, "Initial load: ${videos.size} videos, hasNextPage: ${nextPage != null}")
                } else {
                    // Load more - use the page token
                    val moreItems = ChannelTabInfo.getMoreItems(NewPipe.getService(0), videosTab, page)
                    nextPage = moreItems.nextPage
                    
                    // Convert items to videos
                    moreItems.items.filterIsInstance<StreamInfoItem>().forEach { item ->
                        videos.add(item.toVideo(channelInfo).withCollabAvatarStack())
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
            description = "",
            isUpcoming = this.streamType == org.schabi.newpipe.extractor.stream.StreamType.NONE,
            isLive = this.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
        )
    }

    private suspend fun Video.withCollabAvatarStack(): Video {
        if (collaborators.size > 1) return this
        if (channelThumbnailUrls.size < 2 && !channelName.isLikelyCollaborationByline()) return this
        val collaborators = withTimeoutOrNull(4_000L) {
            YouTube.videoCollaborators(id).getOrNull()
        }.orEmpty()
        val stack = collaborators
            .map { it.thumbnailUrl }
            .filter { it.isNotBlank() }
            .ifEmpty {
                channelThumbnailUrls.takeIf { it.size > 1 }
                    ?: withTimeoutOrNull(4_000L) {
                        YouTube.videoAvatarStack(id).getOrNull()
                    }.orEmpty()
            }
        if (stack.size <= 1 && collaborators.size <= 1) return this
        val merged = (stack + channelThumbnailUrls + channelThumbnailUrl)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.avatarImageIdentityKey() }
            .take(2)
        return if (merged.size > 1 || collaborators.size > 1) {
            copy(
                channelName = collaborators
                    .map { it.name }
                    .filter { it.isNotBlank() }
                    .takeIf { it.size > 1 }
                    ?.joinToString(" and ")
                    ?: channelName,
                channelThumbnailUrl = merged.firstOrNull() ?: channelThumbnailUrl,
                channelThumbnailUrls = merged.ifEmpty { channelThumbnailUrls },
                collaborators = collaborators.ifEmpty { emptyList<VideoCollaborator>() },
            )
        } else {
            this
        }
    }

    private fun String.isLikelyCollaborationByline(): Boolean {
        val normalized = " ${trim().lowercase()} "
        return normalized.contains(" and ") ||
            normalized.contains(" & ") ||
            normalized.contains(" x ") ||
            normalized.contains(" with ")
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
