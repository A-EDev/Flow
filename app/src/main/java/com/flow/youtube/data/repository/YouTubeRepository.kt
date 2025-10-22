package com.flow.youtube.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.kiosk.KioskList
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import com.flow.youtube.data.model.Video
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfo

class YouTubeRepository {
    
    private val service = ServiceList.YouTube
    
    /**
     * Fetch trending videos
     */
    suspend fun getTrendingVideos(
        region: String = "US",
        nextPage: Page? = null
    ): Pair<List<Video>, Page?> = withContext(Dispatchers.IO) {
        try {
            val kioskList = service.kioskList
            val trendingExtractor = kioskList.getExtractorById("Trending", null)
            trendingExtractor.fetchPage()
            
            val infoItems = if (nextPage != null) {
                trendingExtractor.getPage(nextPage)
            } else {
                trendingExtractor.initialPage
            }
            
            val videos = infoItems.items.filterIsInstance<StreamInfoItem>().map { item ->
                item.toVideo()
            }
            
            Pair(videos, infoItems.nextPage)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), null)
        }
    }
    
    /**
     * Search for videos
     */
    suspend fun searchVideos(
        query: String,
        nextPage: Page? = null
    ): Pair<List<Video>, Page?> = withContext(Dispatchers.IO) {
        try {
            val searchExtractor = service.getSearchExtractor(query)
            searchExtractor.fetchPage()
            
            val infoItems = if (nextPage != null) {
                searchExtractor.getPage(nextPage)
            } else {
                searchExtractor.initialPage
            }
            
            val videos = infoItems.items.filterIsInstance<StreamInfoItem>().map { item ->
                item.toVideo()
            }
            
            Pair(videos, infoItems.nextPage)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), null)
        }
    }
    
    /**
     * Get video stream info for playback
     */
    suspend fun getVideoStreamInfo(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            StreamInfo.getInfo(service, url)
        } catch (e: ExtractionException) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get related videos
     */
    suspend fun getRelatedVideos(videoId: String): List<Video> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val streamInfo = StreamInfo.getInfo(service, url)
            
            streamInfo.relatedItems.filterIsInstance<StreamInfoItem>().map { item ->
                item.toVideo()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch recent uploads for a single channel (by channelId or channel URL).
     * Limits to `limitPerChannel` videos per channel to avoid OOM and long runs.
     */
    suspend fun getChannelUploads(
        channelIdOrUrl: String,
        limitPerChannel: Int = 6
    ): List<Video> = withContext(Dispatchers.IO) {
        try {
            // Try to extract a channelId (UC...) from the input
            val channelId = when {
                channelIdOrUrl.startsWith("UC") -> channelIdOrUrl
                channelIdOrUrl.contains("/channel/") -> channelIdOrUrl.substringAfter("/channel/").substringBefore("/").substringBefore("?")
                else -> null
            }

            // If we have a channelId (starts with UC) we can use the uploads playlist which is more reliable
            if (channelId != null && channelId.startsWith("UC")) {
                val uploadsId = "UU" + channelId.removePrefix("UC")
                val playlistUrl = "https://www.youtube.com/playlist?list=$uploadsId"
                val playlistExtractor = service.getPlaylistExtractor(playlistUrl)
                playlistExtractor.fetchPage()
                val page = playlistExtractor.initialPage
                val items = page.items.filterIsInstance<StreamInfoItem>()
                    .take(limitPerChannel)
                    .map { it.toVideo() }
                return@withContext items
            }

            // Fallback: attempt to use channel extractor directly (best-effort)
            val channelUrl = if (channelIdOrUrl.startsWith("http")) channelIdOrUrl else "https://www.youtube.com/channel/$channelIdOrUrl"
            val extractor = service.getChannelExtractor(channelUrl)
            extractor.fetchPage()
            // Many ChannelExtractor implementations expose page items via getPage/getInitialPage; try to access a first page safely
            val pageItems = try {
                // Use reflection-safe approach: call getPage on extractor with null if available
                val method = extractor::class.java.methods.firstOrNull { it.name == "getInitialPage" || it.name == "getInitialItems" }
                if (method != null) {
                    val result = method.invoke(extractor)
                    // Best-effort: if result is a Page-like object with 'items' field
                    val itemsField = result!!::class.java.getMethod("getItems")
                    @Suppress("UNCHECKED_CAST")
                    (itemsField.invoke(result) as? List<*>)?.filterIsInstance<StreamInfoItem>() ?: emptyList()
                } else {
                    emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }

            pageItems.take(limitPerChannel).map { it.toVideo() }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch channel info (best-effort) using NewPipe's channel extractor.
     * Returns org.schabi.newpipe.extractor.channel.ChannelInfo or null on failure.
     */
    suspend fun getChannelInfo(channelIdOrUrl: String): org.schabi.newpipe.extractor.channel.ChannelInfo? = withContext(Dispatchers.IO) {
        try {
            val channelUrl = if (channelIdOrUrl.startsWith("http")) channelIdOrUrl else "https://www.youtube.com/channel/$channelIdOrUrl"
            val extractor = service.getChannelExtractor(channelUrl)
            extractor.fetchPage()
            val channelInfo = org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(extractor)
            channelInfo
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Aggregate uploads from multiple channels, shuffle results and limit total items.
     */
    suspend fun getVideosForChannels(
        channelIdsOrUrls: List<String>,
        perChannelLimit: Int = 5,
        totalLimit: Int = 50
    ): List<Video> = withContext(Dispatchers.IO) {
        try {
            val deferred = channelIdsOrUrls.map { id ->
                // fetch sequentially to avoid high concurrency in CI; it's still IO dispatcher
                getChannelUploads(id, perChannelLimit)
            }

            val combined = deferred.flatten()
            // Shuffle to mix channels and then limit
            val shuffled = combined.shuffled()
            shuffled.take(totalLimit)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Extension function to convert StreamInfoItem to our Video model
     */
    private fun StreamInfoItem.toVideo(): Video {
        // Get highest quality thumbnail (maxresdefault > hqdefault > mqdefault)
        val bestThumbnail = thumbnails
            .sortedByDescending { it.height }
            .firstOrNull()?.url ?: ""
        
        // Get highest quality channel avatar
        val bestAvatar = uploaderAvatars
            .sortedByDescending { it.height }
            .firstOrNull()?.url ?: ""
        
        return Video(
            id = url.substringAfter("watch?v=").substringBefore("&"),
            title = name ?: "Unknown Title",
            channelName = uploaderName ?: "Unknown Channel",
            channelId = uploaderUrl?.substringAfterLast("/") ?: "",
            thumbnailUrl = bestThumbnail,
            duration = duration.toInt(),
            viewCount = viewCount,
            uploadDate = when {
                textualUploadDate != null -> textualUploadDate!!
                uploadDate != null -> uploadDate.toString()
                else -> "Unknown"
            },
            channelThumbnailUrl = bestAvatar
        )
    }
    
    companion object {
        @Volatile
        private var instance: YouTubeRepository? = null
        
        fun getInstance(): YouTubeRepository {
            return instance ?: synchronized(this) {
                instance ?: YouTubeRepository().also { instance = it }
            }
        }
    }
}
