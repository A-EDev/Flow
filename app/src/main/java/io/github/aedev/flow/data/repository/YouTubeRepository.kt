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
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.flow.first
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeRepository @Inject constructor(
    private val playerPreferences: PlayerPreferences
) {
    private val TAG = "YouTubeRepository"
    private val HOME_SUBS_MIN_CHANNELS = 10
    private val HOME_SUBS_MEDIUM_CHANNELS = 14
    private val HOME_SUBS_MAX_CHANNELS = 18
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
                .sortedByDescending { it.timestamp }
            
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
                .firstOrNull()
                .let { ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, it) }
            
            val bestAvatar = info.uploaderAvatars
                .sortedByDescending { it.height }
                .firstOrNull()?.url ?: ""
            
            Video(
                id = videoId,
                title = info.name ?: "Unknown Title",
                channelName = info.uploaderName ?: "Unknown Channel",
                channelId = extractChannelId(info.uploaderUrl),
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
                            withTimeoutOrNull(8_000L) { // 8 second timeout per channel
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
                
                combined
                    .distinctBy { it.id }
                    .sortedByDescending { it.timestamp }
                    .take(totalLimit)
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
     * Rotates through subscribed channels to improve fresh-upload coverage.
     */
    suspend fun getSubscriptionFeed(
        allChannelIds: List<String>
    ): List<Video> = withContext(Dispatchers.IO) {
        if (allChannelIds.isEmpty()) return@withContext emptyList()

        val channels = allChannelIds
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        if (channels.isEmpty()) return@withContext emptyList()

        val channelsPerRefresh = when {
            channels.size <= HOME_SUBS_MIN_CHANNELS -> channels.size
            channels.size <= 60 -> HOME_SUBS_MEDIUM_CHANNELS
            else -> HOME_SUBS_MAX_CHANNELS
        }

        val cursor = playerPreferences.homeSubsRotationCursor.first()
            .coerceIn(0, (channels.size - 1).coerceAtLeast(0))

        val selectedChannels = takeRotatingWindow(channels, cursor, channelsPerRefresh)

        val newCursor = (cursor + selectedChannels.size) % channels.size
        playerPreferences.setHomeSubsRotationCursor(newCursor)

        Log.d(
            TAG,
            "Home subs fetch total=${channels.size}, selected=${selectedChannels.size}, cursor=$cursor->$newCursor"
        )

        getVideosForChannels(
            channelIdsOrUrls = selectedChannels,
            perChannelLimit = 5,
            totalLimit = (channelsPerRefresh * 5).coerceAtMost(150)
        )
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

            val allVideos = mutableListOf<Video>()
            allVideos += playlistInfo.relatedItems
                .filterIsInstance<StreamInfoItem>()
                .map { it.toVideo() }

            var nextPage = playlistInfo.nextPage
            while (nextPage != null) {
                val page = org.schabi.newpipe.extractor.playlist.PlaylistInfo
                    .getMoreItems(service, playlistUrl, nextPage)
                allVideos += page.items
                    .filterIsInstance<StreamInfoItem>()
                    .map { it.toVideo() }
                nextPage = page.nextPage
            }

            val innertubeVideos = fetchInnertubePlaylistVideos(playlistId)
            val playlistVideos = if (innertubeVideos.size > allVideos.size) {
                Log.i(TAG, "Using Innertube playlist result for $playlistId (${innertubeVideos.size} > ${allVideos.size})")
                innertubeVideos
            } else {
                allVideos
            }

            val bestThumbnail = playlistInfo.thumbnails
                .sortedByDescending { it.height }
                .firstOrNull()?.url ?: playlistVideos.firstOrNull()?.thumbnailUrl ?: ""

            io.github.aedev.flow.data.model.Playlist(
                id = playlistId,
                name = playlistInfo.name ?: "Unknown Playlist",
                thumbnailUrl = bestThumbnail,
                videoCount = playlistVideos.size,
                description = playlistInfo.description?.content ?: "",
                videos = playlistVideos,
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

    private suspend fun fetchInnertubePlaylistVideos(
        playlistId: String,
        maxContinuationPages: Int = 30
    ): List<Video> {
        return try {
            val firstPage = YouTube.playlist(playlistId).getOrNull() ?: return emptyList()
            val songs = mutableListOf<SongItem>()
            val seenContinuations = mutableSetOf<String>()

            songs += firstPage.songs
            var continuation = firstPage.songsContinuation ?: firstPage.continuation
            var requestCount = 0

            while (continuation != null && requestCount < maxContinuationPages) {
                if (!seenContinuations.add(continuation)) break
                val page = YouTube.playlistContinuation(continuation).getOrNull() ?: break
                if (page.songs.isEmpty() && page.continuation == null) break
                songs += page.songs
                continuation = page.continuation
                requestCount++
            }

            songs.map { it.toPlaylistVideo() }
        } catch (e: Exception) {
            Log.w(TAG, "Innertube playlist fallback failed for $playlistId: ${e.message}")
            emptyList()
        }
    }

    private fun SongItem.toPlaylistVideo(): Video {
        val artistNames = artists.joinToString(", ") { it.name }
        val channel = artists.firstOrNull()
        return Video(
            id = id,
            title = title,
            channelName = artistNames,
            channelId = channel?.id ?: "",
            thumbnailUrl = ThumbnailUrlResolver.normalizeVideoThumbnail(id, thumbnail),
            duration = duration ?: 0,
            viewCount = 0,
            uploadDate = "",
            isMusic = false
        )
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
            .firstOrNull()
            .let { ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, it) }
        
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
            channelId = extractChannelId(uploaderUrl),
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
            timestamp = resolveUploadTimestamp(
                uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli(),
                textualUploadDate
            ),
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
        val playlistId = url.substringAfterLast("=")
        val bestThumbnail = thumbnails
            .sortedByDescending { it.height }
            .map { it.url }
            .firstOrNull()
            .let { ThumbnailUrlResolver.normalizeVideoThumbnail(playlistId, it) }
        
        return io.github.aedev.flow.data.model.Playlist(
            id = playlistId,
            name = name ?: "Unknown Playlist",
            thumbnailUrl = bestThumbnail,
            videoCount = streamCount.toInt(),
            isLocal = false
        )
    }

    private fun extractChannelId(uploaderUrl: String?): String {
        if (uploaderUrl.isNullOrBlank()) return ""
        val url = uploaderUrl.trim()
        return when {
            url.contains("/channel/") -> url.substringAfter("/channel/")
                .substringBefore("/")
                .substringBefore("?")
            url.contains("/@") -> url.substringAfter("/@")
                .substringBefore("/")
                .substringBefore("?")
            url.contains("/user/") -> url.substringAfter("/user/")
                .substringBefore("/")
                .substringBefore("?")
            url.contains("/c/") -> url.substringAfter("/c/")
                .substringBefore("/")
                .substringBefore("?")
            else -> url.substringAfterLast("/").substringBefore("?")
        }
    }

    private fun resolveUploadTimestamp(absoluteMillis: Long?, textualDate: String?): Long {
        absoluteMillis?.let { if (it > 0L) return it }
        val parsed = parseRelativeUploadDate(textualDate)
        return parsed ?: System.currentTimeMillis()
    }

    private fun parseRelativeUploadDate(textualDate: String?): Long? {
        val raw = textualDate?.trim().orEmpty()
        if (raw.isBlank()) return null

        val normalized = raw.lowercase(Locale.US)
            .replace("streamed", "")
            .replace("premiered", "")
            .replace("ago", "")
            .trim()

        if (normalized.contains("just now") || normalized.contains("today")) {
            return System.currentTimeMillis()
        }
        if (normalized.contains("yesterday")) {
            return System.currentTimeMillis() - 24L * 60L * 60L * 1000L
        }

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

    private fun <T> takeRotatingWindow(items: List<T>, start: Int, count: Int): List<T> {
        if (items.isEmpty() || count <= 0) return emptyList()
        if (items.size <= count) return items

        val safeStart = start.coerceIn(0, items.lastIndex)
        val result = ArrayList<T>(count)
        for (i in 0 until count) {
            val index = (safeStart + i) % items.size
            result.add(items[index])
        }
        return result
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