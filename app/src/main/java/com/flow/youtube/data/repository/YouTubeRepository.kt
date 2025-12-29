package com.flow.youtube.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Deferred
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
            // Update localization based on region
            val country = ContentCountry(region)
            val localization = Localization.fromLocale(java.util.Locale.ENGLISH)
            NewPipe.init(NewPipe.getDownloader(), localization, country)

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
     * Fetch YouTube Shorts specifically
     * Uses search with #shorts and duration filtering
     */
    suspend fun getShorts(
        nextPage: Page? = null
    ): Pair<List<Video>, Page?> = withContext(Dispatchers.IO) {
        try {
            // Search for #shorts which often returns actual shorts
            val searchExtractor = service.getSearchExtractor("#shorts")
            searchExtractor.fetchPage()
            
            val infoItems = if (nextPage != null) {
                searchExtractor.getPage(nextPage)
            } else {
                searchExtractor.initialPage
            }
            
            val shorts = infoItems.items
                .filterIsInstance<StreamInfoItem>()
                .filter { it.duration in 1..60 } // Actual shorts are <= 60s
                .map { it.toVideo() }
            
            Pair(shorts, infoItems.nextPage)
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
     * Search with support for different content types (videos, channels, playlists)
     */
    suspend fun search(
        query: String,
        contentFilters: List<String> = emptyList(),
        nextPage: Page? = null
    ): com.flow.youtube.data.model.SearchResult = withContext(Dispatchers.IO) {
        try {
            val searchExtractor = service.getSearchExtractor(query, contentFilters, "")
            searchExtractor.fetchPage()
            
            val infoItems = if (nextPage != null) {
                searchExtractor.getPage(nextPage)
            } else {
                searchExtractor.initialPage
            }
            
            val videos = mutableListOf<Video>()
            val channels = mutableListOf<com.flow.youtube.data.model.Channel>()
            val playlists = mutableListOf<com.flow.youtube.data.model.Playlist>()
            
            infoItems.items.forEach { item ->
                when (item) {
                    is StreamInfoItem -> {
                        videos.add(item.toVideo())
                    }
                    is org.schabi.newpipe.extractor.channel.ChannelInfoItem -> {
                        channels.add(item.toChannel())
                    }
                    is org.schabi.newpipe.extractor.playlist.PlaylistInfoItem -> {
                        playlists.add(item.toPlaylist())
                    }
                }
            }
            
            com.flow.youtube.data.model.SearchResult(
                videos = videos,
                channels = channels,
                playlists = playlists
            )
        } catch (e: Exception) {
            e.printStackTrace()
            com.flow.youtube.data.model.SearchResult()
        }
    }
    
    /**
     * Get search suggestions from YouTube
     */
    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        try {
            if (query.length < 2) return@withContext emptyList()
            
            val suggestionExtractor = service.suggestionExtractor
            suggestionExtractor.suggestionList(query)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
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
    /**
     * Aggregate uploads from multiple channels, shuffle results and limit total items.
     * Use parallel fetching for speed.
     */
    suspend fun getVideosForChannels(
        channelIdsOrUrls: List<String>,
        perChannelLimit: Int = 5,
        totalLimit: Int = 50
    ): List<Video> = withContext(Dispatchers.IO) {
        try {
            // Use coroutineScope to enable concurrent execution
            kotlinx.coroutines.coroutineScope {
                val deferred = channelIdsOrUrls.map { id ->
                    async { getChannelUploads(id, perChannelLimit) }
                }
                
                val combined = mutableListOf<Video>()
                deferred.forEach { 
                    try {
                        combined.addAll(it.await()) 
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                
                // Shuffle to mix channels and then limit
                val shuffled = combined.shuffled()
                shuffled.take(totalLimit)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    /**
     * Fetch comments for a video
     */
    suspend fun getComments(videoId: String): List<com.flow.youtube.data.model.Comment> = withContext(Dispatchers.IO) {
        try {
            val commentsExtractor = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(service, "https://www.youtube.com/watch?v=$videoId")
            val allComments = mutableListOf<com.flow.youtube.data.model.Comment>()
            
            // Map first page
            allComments.addAll(commentsExtractor.relatedItems.map { item ->
                com.flow.youtube.data.model.Comment(
                    id = item.commentId ?: "",
                    author = item.uploaderName ?: "Unknown",
                    authorThumbnail = item.uploaderAvatars.firstOrNull()?.url ?: "",
                    text = item.commentText?.content ?: "",
                    likeCount = item.likeCount.toInt(),
                    publishedTime = item.textualUploadDate ?: ""
                )
            })
            
            // Try to fetch 3 more pages to get "all" (or at least 80-100)
            var nextPage = commentsExtractor.nextPage
            repeat(4) {
                if (nextPage != null) {
                    val moreItems = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(service, "https://www.youtube.com/watch?v=$videoId", nextPage)
                    allComments.addAll(moreItems.items.map { item ->
                        com.flow.youtube.data.model.Comment(
                            id = item.commentId ?: "",
                            author = item.uploaderName ?: "Unknown",
                            authorThumbnail = item.uploaderAvatars.firstOrNull()?.url ?: "",
                            text = item.commentText?.content ?: "",
                            likeCount = item.likeCount.toInt(),
                            publishedTime = item.textualUploadDate ?: ""
                        )
                    })
                    nextPage = moreItems.nextPage
                }
            }
            
            allComments
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
            uploadDate = run {
                val date = uploadDate
                when {
                    textualUploadDate != null -> textualUploadDate!!
                    date != null -> try {
                        val cal = date.date()
                        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                        sdf.format(cal.time)
                    } catch (e: Exception) {
                        "Unknown"
                    }
                    else -> "Unknown"
                }
            },
            channelThumbnailUrl = bestAvatar
        )
    }
    
    /**
     * Extension function to convert ChannelInfoItem to our Channel model
     */
    private fun org.schabi.newpipe.extractor.channel.ChannelInfoItem.toChannel(): com.flow.youtube.data.model.Channel {
        val bestThumbnail = thumbnails
            .sortedByDescending { it.height }
            .firstOrNull()?.url ?: ""
        
        // Extract the channel ID properly from the URL
        val channelId = when {
            url.contains("/channel/") -> url.substringAfter("/channel/").substringBefore("/").substringBefore("?")
            url.contains("/@") -> url.substringAfter("/@").substringBefore("/").substringBefore("?")
            url.contains("/c/") -> url.substringAfter("/c/").substringBefore("/").substringBefore("?")
            url.contains("/user/") -> url.substringAfter("/user/").substringBefore("/").substringBefore("?")
            else -> url.substringAfterLast("/").substringBefore("?")
        }
        
        return com.flow.youtube.data.model.Channel(
            id = channelId,
            name = name ?: "Unknown Channel",
            thumbnailUrl = bestThumbnail,
            subscriberCount = subscriberCount,
            description = description ?: "",
            url = url // Store the full URL
        )
    }
    
    /**
     * Extension function to convert PlaylistInfoItem to our Playlist model
     */
    private fun org.schabi.newpipe.extractor.playlist.PlaylistInfoItem.toPlaylist(): com.flow.youtube.data.model.Playlist {
        val bestThumbnail = thumbnails
            .sortedByDescending { it.height }
            .firstOrNull()?.url ?: ""
        
        return com.flow.youtube.data.model.Playlist(
            id = url.substringAfterLast("="),
            name = name ?: "Unknown Playlist",
            thumbnailUrl = bestThumbnail,
            videoCount = streamCount.toInt(),
            isLocal = false
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
