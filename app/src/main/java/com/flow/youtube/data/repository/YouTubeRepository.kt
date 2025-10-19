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
