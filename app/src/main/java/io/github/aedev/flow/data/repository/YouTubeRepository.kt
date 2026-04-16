package io.github.aedev.flow.data.repository

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.utils.PerformanceDispatcher
import android.util.Log
import android.util.LruCache
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
import io.github.aedev.flow.data.local.PlayerPreferences
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val playerPreferences: PlayerPreferences
) {
    private val TAG = "YouTubeRepository"
    private val service = ServiceList.YouTube

    // Cache for channel avatar URLs to avoid redundant network calls
    private val channelAvatarCache = LruCache<String, String>(300)

    /**
     * Fetch channel avatar by channelId, with in-memory caching.
     * Returns empty string on failure.
     */
    suspend fun fetchChannelAvatarById(channelId: String): String = withContext(Dispatchers.IO) {
        if (channelId.isBlank()) return@withContext ""
        channelAvatarCache[channelId]?.let { return@withContext it }
        val info = getChannelInfo(channelId) ?: return@withContext ""
        val url = info.avatars.maxByOrNull { it.height }?.url ?: ""
        if (url.isNotEmpty()) channelAvatarCache.put(channelId, url)
        url
    }

    /**
     * Enrich a list of [Video] objects that are missing [Video.channelThumbnailUrl]
     * by fetching avatar URLs in parallel (max 5 concurrent channel fetches).
     */
    suspend fun enrichVideosWithAvatars(videos: List<Video>): List<Video> = supervisorScope {
        val channelIds = videos
            .filter { it.channelThumbnailUrl.isEmpty() && it.channelId.isNotEmpty() }
            .map { it.channelId }
            .distinct()

        if (channelIds.isEmpty()) return@supervisorScope videos

        Log.d(TAG, "enrichVideosWithAvatars: fetching avatars for ${channelIds.size} channels")
        val avatarMap = mutableMapOf<String, String>()
        channelIds.chunked(5).forEach { batch ->
            batch.map { id ->
                async(Dispatchers.IO) { withTimeoutOrNull(6_000L) { id to fetchChannelAvatarById(id) } }
            }.awaitAll().forEach { pair ->
                pair?.let { (id, url) -> if (url.isNotEmpty()) avatarMap[id] = url }
            }
        }
        Log.d(TAG, "enrichVideosWithAvatars: resolved ${avatarMap.size}/${channelIds.size} avatars")
        if (avatarMap.isEmpty()) return@supervisorScope videos
        videos.map { video ->
            if (video.channelThumbnailUrl.isEmpty())
                avatarMap[video.channelId]?.let { video.copy(channelThumbnailUrl = it) } ?: video
            else video
        }
    }

    /**
     * Fetch trending videos
     */
    suspend fun getTrendingVideos(
        region: String = "",
        nextPage: Page? = null
    ): Pair<List<Video>, Page?> = withContext(Dispatchers.IO) {
        try {
            val effectiveRegion = region.ifBlank { playerPreferences.trendingRegion.first() }
            // Update localization based on region
            val country = ContentCountry(effectiveRegion)
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
            Log.w(TAG, "Trending unavailable: ${e.message}")
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
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
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
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
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
    ): io.github.aedev.flow.data.model.SearchResult = withContext(Dispatchers.IO) {
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
            val channels = mutableListOf<io.github.aedev.flow.data.model.Channel>()
            val playlists = mutableListOf<io.github.aedev.flow.data.model.Playlist>()
            
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
            
            io.github.aedev.flow.data.model.SearchResult(
                videos = videos,
                channels = channels,
                playlists = playlists
            )
        } catch (e: Exception) {
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
            io.github.aedev.flow.data.model.SearchResult()
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
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Get video stream info for playback.
     *
     * Throws the original exception on failure so callers can display specific, accurate
     * error messages (age restriction, geo-block, private video, etc.) instead of a
     * generic "unknown error".  Callers that want null-on-failure should wrap in
     * try/catch themselves.
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
                    throw retryEx
                }
            } else {
                Log.e("YouTubeRepository", "Error getting stream info for $videoId: ${e.message}", e)
                throw e
            }
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
                .map { it.url }
                .firstOrNull()?.let { url ->
                    if (url.contains("i.ytimg.com/vi/") || url.contains("img.youtube.com/vi/")) {
                        when {
                            url.endsWith("/mqdefault.jpg") -> url.replace("/mqdefault.jpg", "/hq720.jpg")
                            url.endsWith("/default.jpg") -> url.replace("/default.jpg", "/hq720.jpg")
                            url.endsWith("/hqdefault.jpg") -> url.replace("/hqdefault.jpg", "/hq720.jpg")
                            else -> url
                        }
                    } else url
                } ?: ""
            
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
                timestamp = System.currentTimeMillis(), // Best effort for single video fetch
                channelThumbnailUrl = bestAvatar
            )
        } catch (e: Exception) {
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
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
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
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
                Log.w(TAG, "${e::class.simpleName}: ${e.message}")
                emptyList()
            }

            pageItems.take(limitPerChannel).map { it.toVideo() }
        } catch (e: Exception) {
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch channel info (best-effort) using NewPipe's channel extractor.
     */
    suspend fun getChannelInfo(channelIdOrUrl: String): org.schabi.newpipe.extractor.channel.ChannelInfo? = withContext(Dispatchers.IO) {
        try {
            val channelUrl = if (channelIdOrUrl.startsWith("http")) channelIdOrUrl else "https://www.youtube.com/channel/$channelIdOrUrl"
            org.schabi.newpipe.extractor.channel.ChannelInfo.getInfo(service, channelUrl)
        } catch (e: Exception) {
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
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
     * Fetch trending videos for a specific category.
     * Categories map to YouTube kiosk IDs used by NewPipe.
     * For ALL, fetches from all non-live categories in parallel and interleaves them.
     */
    suspend fun getTrendingByCategory(
        category: TrendingCategory,
        region: String = ""
    ): List<Video> = withContext(Dispatchers.IO) {
        val effectiveRegion = region.ifBlank { playerPreferences.trendingRegion.first() }
        val country = ContentCountry(effectiveRegion)
        val localization = Localization.fromLocale(java.util.Locale.ENGLISH)
        NewPipe.init(NewPipe.getDownloader(), localization, country)

        when (category) {
            TrendingCategory.ALL -> {
                supervisorScope {
                    val deferreds = listOf(
                        TrendingCategory.TRENDING,
                        TrendingCategory.GAMING,
                        TrendingCategory.MUSIC,
                        TrendingCategory.MOVIES,
                    ).map { cat ->
                        async {
                            try {
                                fetchKiosk(cat.kioskId, country)
                            } catch (e: Exception) {
                                emptyList()
                            }
                        }
                    }
                    val results = deferreds.map { it.await() }
                    interleaveRoundRobin(results)
                }
            }
            else -> fetchKiosk(category.kioskId, country)
        }
    }

    private fun fetchKiosk(kioskId: String, country: ContentCountry): List<Video> {
        val kioskList = service.kioskList
        kioskList.forceContentCountry(country)
        val extractor = kioskList.getExtractorById(kioskId, null) as KioskExtractor<*>
        extractor.fetchPage()
        return extractor.initialPage.items
            .filterIsInstance<StreamInfoItem>()
            .map { it.toVideo() }
    }

    private fun <T> interleaveRoundRobin(lists: List<List<T>>): List<T> {
        val result = mutableListOf<T>()
        val iterators = lists.map { it.iterator() }.toMutableList()
        while (iterators.any { it.hasNext() }) {
            val iter = iterators.iterator()
            while (iter.hasNext()) {
                val it = iter.next()
                if (it.hasNext()) result.add(it.next()) else iter.remove()
            }
        }
        return result
    }

    /**
     * Trending categories supported by NewPipe kiosk extractors.
     */
    enum class TrendingCategory(val kioskId: String, val displayName: String) {
        ALL("Trending", "All"),
        TRENDING("Trending", "Trending"),
        GAMING("trending_gaming", "Gaming"),
        MUSIC("trending_music", "Music"),
        MOVIES("trending_movies_and_shows", "Movies"),
        LIVE("live", "Live")
    }
    
    suspend fun prefetchTrendingAndShorts(
        region: String = ""
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
     * Fetch the first page of comments for a video.
     * Returns the comments and a next-page token (null if no more pages).
     */
    suspend fun getComments(videoId: String): Pair<List<io.github.aedev.flow.data.model.Comment>, org.schabi.newpipe.extractor.Page?> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val commentsInfo = org.schabi.newpipe.extractor.comments.CommentsInfo.getInfo(service, url)
            val comments = commentsInfo.relatedItems.map { item ->
                io.github.aedev.flow.data.model.Comment(
                    id = item.commentId ?: "",
                    author = item.uploaderName ?: "Unknown",
                    authorThumbnail = item.uploaderAvatars.firstOrNull()?.url ?: "",
                    text = item.commentText?.content ?: "",
                    likeCount = item.likeCount.toInt(),
                    publishedTime = item.textualUploadDate ?: "",
                    replyCount = item.replyCount.toInt(),
                    repliesPage = item.replies,
                    isPinned = item.isPinned
                )
            }
            Pair(comments, commentsInfo.nextPage)
        } catch (e: Exception) {
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
            Pair(emptyList(), null)
        }
    }

    /**
     * Fetch the next page of top-level comments for a video.
     * Returns the new comments and an updated next-page token.
     */
    suspend fun getMoreComments(
        videoId: String,
        nextPage: org.schabi.newpipe.extractor.Page
    ): Pair<List<io.github.aedev.flow.data.model.Comment>, org.schabi.newpipe.extractor.Page?> = withContext(Dispatchers.IO) {
        try {
            val url = "https://www.youtube.com/watch?v=$videoId"
            val moreItems = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(service, url, nextPage)
            val comments = moreItems.items.map { item ->
                io.github.aedev.flow.data.model.Comment(
                    id = item.commentId ?: "",
                    author = item.uploaderName ?: "Unknown",
                    authorThumbnail = item.uploaderAvatars.firstOrNull()?.url ?: "",
                    text = item.commentText?.content ?: "",
                    likeCount = item.likeCount.toInt(),
                    publishedTime = item.textualUploadDate ?: "",
                    replyCount = item.replyCount.toInt(),
                    repliesPage = item.replies,
                    isPinned = item.isPinned
                )
            }
            Pair(comments, moreItems.nextPage)
        } catch (e: Exception) {
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
            Pair(emptyList(), null)
        }
    }

    /**
     * Fetch replies for a comment
     */
    suspend fun getCommentReplies(
        url: String,
        repliesPage: Page
    ): Pair<List<io.github.aedev.flow.data.model.Comment>, Page?> = withContext(Dispatchers.IO) {
        try {
            val moreItems = org.schabi.newpipe.extractor.comments.CommentsInfo.getMoreItems(service, url, repliesPage)
            val replies = moreItems.items.map { item ->
                io.github.aedev.flow.data.model.Comment(
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
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
            Pair(emptyList(), null)
        }
    }

    /**
     * Fetch playlist details
     */
    suspend fun getPlaylistDetails(playlistId: String): io.github.aedev.flow.data.model.Playlist? = withContext(Dispatchers.IO) {
        try {
            val playlistUrl = "https://www.youtube.com/playlist?list=$playlistId"
            val playlistInfo = org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(service, playlistUrl)
            
            val videos = playlistInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideo() }
                
            val bestThumbnail = playlistInfo.thumbnails
                .sortedByDescending { it.height }
                .firstOrNull()?.url ?: videos.firstOrNull()?.thumbnailUrl ?: ""

            io.github.aedev.flow.data.model.Playlist(
                id = playlistId,
                name = playlistInfo.name ?: "Unknown Playlist",
                thumbnailUrl = bestThumbnail,
                videoCount = videos.size,
                description = playlistInfo.description?.content ?: "",
                videos = videos,
                isLocal = false
            )
        } catch (e: Exception) {
            Log.w(TAG, "${e::class.simpleName}: ${e.message}")
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
            .map { it.url }
            .firstOrNull()?.let { url ->
                // YouTube thumbnail quality promotion logic
                if (url.contains("i.ytimg.com/vi/") || url.contains("img.youtube.com/vi/")) {
                    when {
                        url.endsWith("/mqdefault.jpg") -> url.replace("/mqdefault.jpg", "/hq720.jpg")
                        url.endsWith("/default.jpg") -> url.replace("/default.jpg", "/hq720.jpg")
                        url.endsWith("/hqdefault.jpg") -> url.replace("/hqdefault.jpg", "/hq720.jpg")
                        else -> url
                    }
                } else url
            } ?: ""
        
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
            timestamp = System.currentTimeMillis(), // Best effort, refined by parser if needed
            channelThumbnailUrl = bestAvatar,
            isUpcoming = streamType == StreamType.NONE,
            isLive = isLiveStream,
            isShort = isShortUrl,
            isMusic = isMusicCandidate
        )
    }
    
    /**
     * Extension function to convert ChannelInfoItem to our Channel model
     */
    private fun org.schabi.newpipe.extractor.channel.ChannelInfoItem.toChannel(): io.github.aedev.flow.data.model.Channel {
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
        
        return io.github.aedev.flow.data.model.Channel(
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
    private fun org.schabi.newpipe.extractor.playlist.PlaylistInfoItem.toPlaylist(): io.github.aedev.flow.data.model.Playlist {
        val bestThumbnail = thumbnails
            .sortedByDescending { it.height }
            .map { it.url }
            .firstOrNull()?.let { url ->
                if (url.contains("i.ytimg.com/vi/") || url.contains("img.youtube.com/vi/")) {
                    when {
                        url.endsWith("/mqdefault.jpg") -> url.replace("/mqdefault.jpg", "/hq720.jpg")
                        url.endsWith("/default.jpg") -> url.replace("/default.jpg", "/hq720.jpg")
                        url.endsWith("/hqdefault.jpg") -> url.replace("/hqdefault.jpg", "/hq720.jpg")
                        else -> url
                    }
                } else url
            } ?: ""
        
        return io.github.aedev.flow.data.model.Playlist(
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

        fun getInstance(playerPreferences: io.github.aedev.flow.data.local.PlayerPreferences): YouTubeRepository {
            return instance ?: synchronized(this) {
                instance ?: YouTubeRepository(playerPreferences).also { instance = it }
            }
        }

        fun getInstance(): YouTubeRepository {
            return instance ?: error("YouTubeRepository not initialized. Call getInstance(playerPreferences) first.")
        }
    }
}