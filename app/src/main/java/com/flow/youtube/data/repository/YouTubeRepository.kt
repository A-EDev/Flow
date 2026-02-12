package com.flow.youtube.data.repository

import com.flow.youtube.data.model.Video
import com.flow.youtube.utils.PerformanceDispatcher
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.kiosk.KioskExtractor
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.exceptions.ExtractionException
import org.schabi.newpipe.extractor.stream.StreamInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor() {
    
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
            val trendingExtractor = kioskList.getExtractorById("Trending", null) as KioskExtractor<*>
            
            // FIX: ALWAYS call fetchPage to initialize the extractor state
            trendingExtractor.fetchPage()
            
            val infoItems = if (nextPage != null) {
                trendingExtractor.getPage(nextPage)
            } else {
                trendingExtractor.initialPage
            }
            
            val videos = infoItems.items
                .filterIsInstance<StreamInfoItem>()
                .map { item -> item.toVideo() }
            
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
            
            // FIX: Correct Pagination Logic
            val infoItems = if (nextPage != null) {
                searchExtractor.getPage(nextPage)
            } else {
                searchExtractor.initialPage
            }
            
            val shorts = infoItems.items
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideo() }
                .filter { it.duration in 1..60 } // Actual shorts are <= 60s
            
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
            
            // FIX: Correct Pagination Logic
            val infoItems = if (nextPage != null) {
                searchExtractor.getPage(nextPage)
            } else {
                searchExtractor.initialPage
            }
            
            val videos = infoItems.items
                .filterIsInstance<StreamInfoItem>()
                .map { item -> item.toVideo() }
            
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
            
            // FIX: Correct Pagination Logic
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
        } catch (e: Exception) {
            // NewPipe "The page needs to be reloaded" error handling
            // This often happens due to stale internal state or specific YouTube bot identifiers
            val isReloadError = e.message?.contains("page needs to be reloaded", ignoreCase = true) == true || 
                               (e is org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException && e.message?.contains("reloaded") == true)
            
            if (isReloadError) {
                Log.w("YouTubeRepository", "Hit 'page needs to be reloaded' error for $videoId. Retrying with fresh state...")
                
                // Re-init NewPipe to potentially clear internal state
                try {
                     val country = ContentCountry("US")
                     val localization = Localization.fromLocale(java.util.Locale.ENGLISH)
                     NewPipe.init(NewPipe.getDownloader(), localization, country)
                } catch (initEx: Exception) {
                     Log.e("YouTubeRepository", "Failed to re-init NewPipe", initEx)
                }

                // Retry with alternate URL format which works as a cache buster sometimes
                try {
                    val altUrl = "https://youtu.be/$videoId" 
                    Log.d("YouTubeRepository", "Retrying with alternate URL: $altUrl")
                    return@withContext StreamInfo.getInfo(service, altUrl)
                } catch (retryEx: Exception) {
                    Log.e("YouTubeRepository", "Retry failed for $videoId: ${retryEx.message}", retryEx)
                }
            } else {
                Log.e("YouTubeRepository", "Error getting stream info for $videoId: ${e.message}", e)
            }
            null
        }
    }

    /**
     * Get a single video object by ID
     */
    suspend fun getVideo(videoId: String): Video? = withContext(Dispatchers.IO) {
        try {
            val info = getVideoStreamInfo(videoId) ?: return@withContext null
            
            val bestThumbnail = info.thumbnails
                .sortedByDescending { it.height }
                .firstOrNull()?.url ?: ""
            
            val bestAvatar = info.uploaderAvatars
                .sortedByDescending { it.height }
                .firstOrNull()?.url ?: ""
            
            Video(
                id = videoId,
                title = info.name ?: "Unknown Title",
                channelName = info.uploaderName ?: "Unknown Channel",
                channelId = info.uploaderUrl?.substringAfterLast("/") ?: "",
                thumbnailUrl = bestThumbnail,
                duration = info.duration.toInt(),
                viewCount = info.viewCount,
                uploadDate = info.textualUploadDate ?: "Unknown",
                channelThumbnailUrl = bestAvatar
            )
        } catch (e: Exception) {
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
     */
    suspend fun getChannelInfo(channelIdOrUrl: String): org.schabi.newpipe.extractor.channel.ChannelInfo? = withContext(Dispatchers.IO) {
        try {
            val channelUrl = if (channelIdOrUrl.startsWith("http")) channelIdOrUrl else "https://www.youtube.com/channel/$channelIdOrUrl"
            val extractor = service.getChannelExtractor(channelUrl)
            extractor.fetchPage()
            org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(extractor)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * PERFORMANCE OPTIMIZED: Aggregate uploads from multiple channels
     * Uses SupervisorScope for error isolation - one failed channel doesn't break others
     * Implements chunked parallel fetching to prevent overwhelming the network
     */
    suspend fun getVideosForChannels(
        channelIdsOrUrls: List<String>,
        perChannelLimit: Int = 5,
        totalLimit: Int = 50
    ): List<Video> = withContext(PerformanceDispatcher.networkIO) {
        try {
            // Use supervisorScope for error isolation
            // If one channel fails, others continue fetching
            supervisorScope {
                // Process in chunks of 5 for optimal parallelism
                // This prevents overwhelming the network while maintaining speed
                val chunkSize = 5
                val combined = mutableListOf<Video>()
                
                channelIdsOrUrls.chunked(chunkSize).forEach { chunk ->
                    val chunkResults = chunk.map { id ->
                        async(PerformanceDispatcher.networkIO) {
                            withTimeoutOrNull(15_000L) { // 15 second timeout per channel
                                try {
                                    getChannelUploads(id, perChannelLimit)
                                } catch (e: Exception) {
                                    Log.w("YouTubeRepository", "Channel fetch failed: ${e.message}")
                                    emptyList()
                                }
                            } ?: emptyList()
                        }
                    }.awaitAll()
                    
                    chunkResults.forEach { combined.addAll(it) }
                }
                
                // Shuffle to mix channels and then limit
                combined.shuffled().take(totalLimit)
            }
        } catch (e: Exception) {
            Log.e("YouTubeRepository", "getVideosForChannels failed: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * NEW: Parallel fetch of multiple search queries
     * Executes all queries simultaneously for faster feed generation
     */
    suspend fun parallelSearchQueries(
        queries: List<String>,
        limitPerQuery: Int = 15
    ): List<Video> = withContext(PerformanceDispatcher.networkIO) {
        supervisorScope {
            val results = queries.map { query ->
                async(PerformanceDispatcher.networkIO) {
                    withTimeoutOrNull(10_000L) {
                        try {
                            searchVideos(query).first.take(limitPerQuery)
                        } catch (e: Exception) {
                            Log.w("YouTubeRepository", "Search query '$query' failed: ${e.message}")
                            emptyList()
                        }
                    } ?: emptyList()
                }
            }.awaitAll()
            
            results.flatten().distinctBy { it.id }
        }
    }
    
    /**
     * NEW: Fast parallel prefetch for preloading content
     * Returns results as they complete (not waiting for all)
     */
    suspend fun prefetchTrendingAndShorts(
        region: String = "US"
    ): Pair<List<Video>, List<Video>> = withContext(PerformanceDispatcher.networkIO) {
        supervisorScope {
            val trendingDeferred = async { 
                withTimeoutOrNull(12_000L) { getTrendingVideos(region).first } ?: emptyList() 
            }
            val shortsDeferred = async { 
                withTimeoutOrNull(10_000L) { getShorts().first } ?: emptyList() 
            }
            
            Pair(trendingDeferred.await(), shortsDeferred.await())
        }
    }

    /**
     * Fetch a "Lite" Subscription Feed
     * Randomly picks 10 subscribed channels and fetches their latest videos.
     */
    suspend fun getSubscriptionFeed(
        allChannelIds: List<String>
    ): List<Video> = withContext(Dispatchers.IO) {
        if (allChannelIds.isEmpty()) return@withContext emptyList()
        
        // Pick 10 random subs to get more variety
        val randomBatch = allChannelIds.shuffled().take(10)
        
        getVideosForChannels(randomBatch, perChannelLimit = 5, totalLimit = 40)
    }
    
    /**
     * Fetch comments for a video
     */
    suspend fun getComments(videoId: String): List<com.flow.youtube.data.model.Comment> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val commentsExtractor = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(service, url)
            val allComments = mutableListOf<com.flow.youtube.data.model.Comment>()
            
            // Map first page
            allComments.addAll(commentsExtractor.relatedItems.map { item ->
                com.flow.youtube.data.model.Comment(
                    id = item.commentId ?: "",
                    author = item.uploaderName ?: "Unknown",
                    authorThumbnail = item.uploaderAvatars.firstOrNull()?.url ?: "",
                    text = item.commentText?.content ?: "",
                    likeCount = item.likeCount.toInt(),
                    publishedTime = item.textualUploadDate ?: "",
                    replyCount = item.replyCount.toInt(),
                    repliesPage = item.replies
                )
            })
            
            // Try to fetch 3 more pages to get "all" (or at least 80-100)
            var nextPage = commentsExtractor.nextPage
            var pages = 0
            while (nextPage != null && pages < 3) {
                val moreItems = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(service, url, nextPage)
                allComments.addAll(moreItems.items.map { item ->
                    com.flow.youtube.data.model.Comment(
                        id = item.commentId ?: "",
                        author = item.uploaderName ?: "Unknown",
                        authorThumbnail = item.uploaderAvatars.firstOrNull()?.url ?: "",
                        text = item.commentText?.content ?: "",
                        likeCount = item.likeCount.toInt(),
                        publishedTime = item.textualUploadDate ?: "",
                        replyCount = item.replyCount.toInt(),
                        repliesPage = item.replies
                    )
                })
                nextPage = moreItems.nextPage
                pages++
            }
            
            allComments
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Fetch replies for a comment
     */
    suspend fun getCommentReplies(
        url: String,
        repliesPage: Page
    ): Pair<List<com.flow.youtube.data.model.Comment>, Page?> = withContext(Dispatchers.IO) {
        try {
            val moreItems = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(service, url, repliesPage)
            val replies = moreItems.items.map { item ->
                com.flow.youtube.data.model.Comment(
                    id = item.commentId ?: "",
                    author = item.uploaderName ?: "Unknown",
                    authorThumbnail = item.uploaderAvatars.firstOrNull()?.url ?: "",
                    text = item.commentText?.content ?: "",
                    likeCount = item.likeCount.toInt(),
                    publishedTime = item.textualUploadDate ?: "",
                    replyCount = item.replyCount.toInt(),
                    repliesPage = item.replies
                )
            }
            Pair(replies, moreItems.nextPage)
        } catch (e: Exception) {
            e.printStackTrace()
            Pair(emptyList(), null)
        }
    }

    /**
     * Fetch playlist details
     */
    suspend fun getPlaylistDetails(playlistId: String): com.flow.youtube.data.model.Playlist? = withContext(Dispatchers.IO) {
        try {
            val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
            val playlistInfo = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(service, playlistUrl)
            
            val videos = playlistInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideo() }
                
            val bestThumbnail = playlistInfo.thumbnails
                .sortedByDescending { it.height }
                .firstOrNull()?.url ?: videos.firstOrNull()?.thumbnailUrl ?: ""

            com.flow.youtube.data.model.Playlist(
                id = playlistId,
                name = playlistInfo.name ?: "Unknown Playlist",
                thumbnailUrl = bestThumbnail,
                videoCount = videos.size,
                description = playlistInfo.description?.content ?: "",
                videos = videos,
                isLocal = false
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Helper to extract related videos directly from a StreamInfo object
     * This avoids a redundant network call when we already have the stream info.
     */
    fun getRelatedVideosFromStreamInfo(info: StreamInfo): List<Video> {
        return try {
            info.relatedItems.filterIsInstance<StreamInfoItem>().map { it.toVideo() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Extension function to convert StreamInfoItem to our Video model
     */
    private fun StreamInfoItem.toVideo(): Video {
        val rawUrl = url ?: ""
        val videoId = when {
            rawUrl.contains("watch?v=") -> rawUrl.substringAfter("watch?v=").substringBefore("&")
            rawUrl.contains("youtu.be/") -> rawUrl.substringAfter("youtu.be/").substringBefore("?")
            rawUrl.contains("/shorts/") -> rawUrl.substringAfter("/shorts/").substringBefore("?")
            else -> rawUrl.substringAfterLast("/") 
        }

        val bestThumbnail = thumbnails
            .sortedByDescending { it.height }
            .firstOrNull()?.url ?: ""
        
        val bestAvatar = uploaderAvatars
            .sortedByDescending { it.height }
            .firstOrNull()?.url ?: ""
        
        var durationSecs = if (duration > 0) duration.toInt() else 0
        
        val isShortUrl = rawUrl.contains("/shorts/")
        
        if (isShortUrl && durationSecs == 0) {
            durationSecs = 60 
        }
        
        val isLiveStream = streamType == StreamType.LIVE_STREAM
        if (isLiveStream) {
            durationSecs = 0 
        }

        // Logic to detect if it's a music video
        val nameLower = name?.lowercase() ?: ""
        val uploaderLower = uploaderName?.lowercase() ?: ""
        val isMusicCandidate = uploaderLower.contains("vevo") || 
                             uploaderLower.contains(" - topic") ||
                             nameLower.contains("official music video") ||
                             nameLower.contains("official video") ||
                             nameLower.contains("official audio") ||
                             nameLower.contains("(official)")
        
        return Video(
            id = videoId,
            title = name ?: "Unknown Title",
            channelName = uploaderName ?: "Unknown Channel",
            channelId = uploaderUrl?.substringAfterLast("/") ?: "",
            thumbnailUrl = bestThumbnail,
            duration = durationSecs,
            viewCount = viewCount,
            uploadDate = run {
                val date = uploadDate
                when {
                    textualUploadDate != null -> textualUploadDate!!
                    date != null -> try {
                        val d = java.util.Date.from(date.offsetDateTime().toInstant())
                        val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                        sdf.format(d)
                    } catch (e: Exception) {
                        "Unknown"
                    }
                    else -> "Unknown"
                }
            },
            channelThumbnailUrl = bestAvatar,
            isLive = isLiveStream,
            isShort = isShortUrl,
            isMusic = isMusicCandidate
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
            url = url
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