package com.flow.youtube.ui.screens.subscriptions

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.ChannelSubscription
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.local.VideoHistoryEntry
import com.flow.youtube.data.local.ViewHistory
import com.flow.youtube.data.model.Channel
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.repository.YouTubeRepository
import com.flow.youtube.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

class SubscriptionsViewModel : ViewModel() {
    
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var viewHistory: ViewHistory
    
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    // Repository to fetch feeds and video details
    private val ytRepository: YouTubeRepository = YouTubeRepository.getInstance()
    private lateinit var cacheDao: com.flow.youtube.data.local.dao.CacheDao
    
    fun initialize(context: Context) {
        subscriptionRepository = SubscriptionRepository.getInstance(context)
        viewHistory = ViewHistory.getInstance(context)
        cacheDao = com.flow.youtube.data.local.AppDatabase.getDatabase(context).cacheDao()
        
        //  INSTANT LOAD: Observe the DB cache immediately
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            cacheDao.getSubscriptionFeed().collect { cachedFeed ->
                if (cachedFeed.isNotEmpty()) {
                    val videos = cachedFeed.map { entity ->
                         Video(
                            id = entity.videoId,
                            title = entity.title,
                            channelName = entity.channelName,
                            channelId = entity.channelId,
                            thumbnailUrl = entity.thumbnailUrl,
                            duration = entity.duration,
                            viewCount = entity.viewCount,
                            uploadDate = entity.uploadDate,
                            channelThumbnailUrl = entity.channelThumbnailUrl
                        )
                    }
                    updateVideos(videos)
                }
            }
        }
        
        //  BACKGROUND UPDATE: Smart network fetch
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            subscriptionRepository.getAllSubscriptions().collect { subscriptions ->
                val channels = subscriptions.map { sub ->
                    Channel(
                        id = sub.channelId,
                        name = sub.channelName,
                        thumbnailUrl = sub.channelThumbnail,
                        subscriberCount = 0L, 
                        isSubscribed = true
                    )
                }
                _uiState.update { it.copy(subscribedChannels = channels) }

                // Fetch feeds in background - Only fetch a random subset ("Smart Feed") to be fast
                if (channels.isNotEmpty()) {
                    // Only show loading if we have NO cached data? 
                    // No, let's show loading indicator non-intrusively or only if cache is empty
                    // For now, we keep existing behavior but it finishes faster.
                    
                    supervisorScope {
                         // Shuffle and take 15 channels for fast update
                        val targetChannels = channels.shuffled().take(15).map { it.id }
                        
                        // We do this in background without blocking UI
                        // If cache is empty, we might want to show loading
                        if (_uiState.value.recentVideos.isEmpty()) {
                             _uiState.update { it.copy(isLoading = true) }
                        }
                        
                        val videos = withTimeoutOrNull(45_000L) {
                            ytRepository.getVideosForChannels(targetChannels, perChannelLimit = 3, totalLimit = 40)
                        } ?: emptyList()
                        
                        // Cache the results
                        if (videos.isNotEmpty()) {
                            val entities = videos.map { video ->
                                com.flow.youtube.data.local.entity.SubscriptionFeedEntity(
                                    videoId = video.id,
                                    title = video.title,
                                    channelName = video.channelName,
                                    channelId = video.channelId,
                                    thumbnailUrl = video.thumbnailUrl,
                                    duration = video.duration,
                                    viewCount = video.viewCount,
                                    uploadDate = video.uploadDate,
                                    channelThumbnailUrl = video.channelThumbnailUrl,
                                    cachedAt = System.currentTimeMillis()
                                )
                            }
                            launch(PerformanceDispatcher.diskIO) {
                                cacheDao.insertSubscriptionFeed(entities)
                            }
                        }
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
        
        //  Load recent videos from history in parallel
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            viewHistory.getAllHistory().collect { history ->
                val videos = history.take(20).map { entry ->
                    Video(
                        id = entry.videoId,
                        title = entry.title,
                        channelName = entry.channelName,
                        channelId = entry.channelId,
                        thumbnailUrl = entry.thumbnailUrl,
                        duration = (entry.duration / 1000).toInt(),
                        viewCount = 0L,
                        uploadDate = formatTimestamp(entry.timestamp),
                        channelThumbnailUrl = ""
                    )
                }
                updateVideos(videos)
            }
        }
    }

    private fun updateVideos(videos: List<Video>) {
        // Sort videos by date (latest first) before partitioning
        val sortedVideos = videos.sortedByDescending { parseRelativeTime(it.uploadDate) }
        val (shorts, regular) = sortedVideos.partition { it.duration > 0 && it.duration <= 80 }
        _uiState.update { it.copy(recentVideos = regular, shorts = shorts) }
    }
    
    private fun parseRelativeTime(dateString: String): Long {
        try {
            val now = System.currentTimeMillis()
            val text = dateString.lowercase().trim()
            
            // Handle special cases or future streams which we consider "new"
            if (text.contains("scheduled") || text.contains("premiere")) return now + 86400000L
            if (text.contains("live")) return now + 3600000L // Boost live streams
            
            // Example: "2 days ago", "1 day ago", "3 years ago"
            val parts = text.split(" ")
            val valueLine = parts.firstOrNull { it.any { c -> c.isDigit() } } 
            val value = valueLine?.filter { it.isDigit() }?.toLongOrNull() ?: 1L
            
            val multiplier = when {
                text.contains("second") -> 1000L
                text.contains("minute") -> 60000L
                text.contains("hour") -> 3600000L
                text.contains("day") -> 86400000L
                text.contains("week") -> 604800000L
                text.contains("month") -> 2592000000L
                text.contains("year") -> 31536000000L
                else -> 86400000L
            }
            
            return now - (value * multiplier)
        } catch (e: Exception) {
            return System.currentTimeMillis() // Fallback
        }
    }
    
    fun importNewPipeBackup(uri: android.net.Uri, context: Context) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val jsonString = inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = org.json.JSONObject(jsonString)
                    
                    if (jsonObject.has("subscriptions")) {
                        val subscriptionsArray = jsonObject.getJSONArray("subscriptions")
                        var importedCount = 0
                        
                        for (i in 0 until subscriptionsArray.length()) {
                            val item = subscriptionsArray.getJSONObject(i)
                            // NewPipe Export Format: service_id, url, name
                            val url = item.optString("url")
                            val name = item.optString("name")
                            
                            // Extract ID from URL (e.g. https://www.youtube.com/channel/UCxxxx)
                            // Or https://www.youtube.com/user/xxxx
                            if (url.isNotEmpty() && name.isNotEmpty()) {
                                var channelId = ""
                                if (url.contains("/channel/")) {
                                    channelId = url.substringAfter("/channel/")
                                } else if (url.contains("/user/")) {
                                    channelId = url.substringAfter("/user/")
                                    // Note: This might not be the unique ID, but we try best effort. 
                                    // NewPipe usually exports /channel/ URLs for subscriptions.
                                }
                                // Clean up any trailing slash or query params
                                if (channelId.contains("/")) channelId = channelId.substringBefore("/")
                                if (channelId.contains("?")) channelId = channelId.substringBefore("?")
                                
                                if (channelId.isNotEmpty()) {
                                    val subscription = ChannelSubscription(
                                        channelId = channelId,
                                        channelName = name,
                                        channelThumbnail = "", // Will load lazily or show placeholder
                                        subscribedAt = System.currentTimeMillis()
                                    )
                                    subscriptionRepository.subscribe(subscription)
                                    importedCount++
                                }
                            }
                        }
                        // Refresh subs
                        if (importedCount > 0) {
                             // The existing flow collection in initialize will auto-update because subscriptionRepository.getAllSubscriptions produces a flow
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    fun selectChannel(channelId: String?) {
        _uiState.update { it.copy(selectedChannelId = channelId) }
    }

    /**
     *  PERFORMANCE OPTIMIZED: Manually refresh subscription feed
     * Updates cache on completion
     */
    fun refreshFeed() {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            val channels = _uiState.value.subscribedChannels
            if (channels.isEmpty()) return@launch
            _uiState.update { it.copy(isLoading = true) }
            
            supervisorScope {
                // Refresh different set or all? Let's refresh a larger set on manual refresh
                val targetChannels = channels.shuffled().take(30).map { it.id }
                
                val videos = withTimeoutOrNull(45_000L) {
                    ytRepository.getVideosForChannels(targetChannels, perChannelLimit = 4, totalLimit = 80)
                } ?: emptyList()
                
                if (videos.isNotEmpty()) {
                    val entities = videos.map { video ->
                        com.flow.youtube.data.local.entity.SubscriptionFeedEntity(
                            videoId = video.id,
                            title = video.title,
                            channelName = video.channelName,
                            channelId = video.channelId,
                            thumbnailUrl = video.thumbnailUrl,
                            duration = video.duration,
                            viewCount = video.viewCount,
                            uploadDate = video.uploadDate,
                            channelThumbnailUrl = video.channelThumbnailUrl,
                            cachedAt = System.currentTimeMillis()
                        )
                    }
                    launch(PerformanceDispatcher.diskIO) {
                        cacheDao.insertSubscriptionFeed(entities)
                    }
                }
                
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    fun unsubscribe(channelId: String) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            subscriptionRepository.getSubscription(channelId).firstOrNull()?.let {
                subscriptionRepository.unsubscribe(channelId)
                // trigger refresh
                refreshFeed()
            }
        }
    }

    fun toggleViewMode() {
        _uiState.update { it.copy(isFullWidthView = !it.isFullWidthView) }
    }

    /**
     * Get a single subscription snapshot (suspend) - useful before removing so we can undo
     */
    suspend fun getSubscriptionOnce(channelId: String): ChannelSubscription? {
        return subscriptionRepository.getSubscription(channelId).firstOrNull()
    }

    /**
     * Subscribe a channel (used for undo)
     */
    fun subscribeChannel(channel: ChannelSubscription) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            subscriptionRepository.subscribe(channel)
            // trigger refresh
            refreshFeed()
        }
    }
    
    private fun formatTimestamp(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        return when {
            days > 0 -> "$days day${if (days > 1) "s" else ""} ago"
            hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
            minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
            else -> "Just now"
        }
    }
}

data class SubscriptionsUiState(
    val subscribedChannels: List<Channel> = emptyList(),
    val recentVideos: List<Video> = emptyList(),
    val shorts: List<Video> = emptyList(),
    val selectedChannelId: String? = null,
    val isLoading: Boolean = false,
    val isFullWidthView: Boolean = false
)

