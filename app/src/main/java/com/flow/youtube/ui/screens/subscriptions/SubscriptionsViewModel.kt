package com.flow.youtube.ui.screens.subscriptions

import android.content.Context
import android.util.Log
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
import com.flow.youtube.data.local.PlayerPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

class SubscriptionsViewModel : ViewModel() {
    companion object { private const val TAG = "SubsViewModel" }

    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var viewHistory: ViewHistory
    
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    private val ytRepository: YouTubeRepository = YouTubeRepository.getInstance()
    private lateinit var cacheDao: com.flow.youtube.data.local.dao.CacheDao
    private lateinit var playerPreferences: PlayerPreferences
    
    fun initialize(context: Context) {
        subscriptionRepository = SubscriptionRepository.getInstance(context)
        playerPreferences = PlayerPreferences(context)
        viewHistory = ViewHistory.getInstance(context)
        cacheDao = com.flow.youtube.data.local.AppDatabase.getDatabase(context).cacheDao()
        
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            playerPreferences.shortsShelfEnabled.collect { enabled ->
                _uiState.update { it.copy(isShortsShelfEnabled = enabled) }
            }
        }
        
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            cacheDao.getSubscriptionFeed().collect { cachedFeed ->
                Log.d(TAG, "Cache observer: ${cachedFeed.size} entries in DB")
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
                            timestamp = entity.timestamp,
                            channelThumbnailUrl = entity.channelThumbnailUrl,
                            isShort = entity.isShort
                        )
                    }
                    Log.d(TAG, "Cache observer: calling updateVideos with ${videos.size} videos")
                    updateVideos(videos)
                }
            }
        }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            subscriptionRepository.getAllSubscriptions()
                .map { subs -> subs.map { it.channelId }.sorted() }
                .distinctUntilChanged()
                .collect { channelIds ->
                    Log.i(TAG, "Channel IDs changed: ${channelIds.size} channels \u2014 triggering fetch")

                    val allSubs = subscriptionRepository.getAllSubscriptions().first()
                    val channels = allSubs.map { sub ->
                        Channel(
                            id = sub.channelId,
                            name = sub.channelName,
                            thumbnailUrl = sub.channelThumbnail,
                            subscriberCount = 0L,
                            isSubscribed = true
                        )
                    }
                    _uiState.update { it.copy(subscribedChannels = channels) }

                    if (channels.isNotEmpty()) {
                        if (_uiState.value.recentVideos.isEmpty()) {
                            _uiState.update { it.copy(isLoading = true) }
                        }
                        Log.i(TAG, "Starting network fetch for ${channels.size} channels")

                        com.flow.youtube.data.innertube.RssSubscriptionService.fetchSubscriptionVideos(
                            channelIds = channels.map { it.id }
                        ).collect { videos ->
                            Log.i(TAG, "Network emit received: ${videos.size} videos (shorts=${videos.count { it.isShort }}, regular=${videos.count { !it.isShort }})")
                            if (videos.isNotEmpty()) {
                                updateVideos(videos)
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
                                        timestamp = video.timestamp,
                                        channelThumbnailUrl = video.channelThumbnailUrl,
                                        isShort = video.isShort,
                                        cachedAt = System.currentTimeMillis()
                                    )
                                }
                                launch(PerformanceDispatcher.diskIO) {
                                    cacheDao.clearSubscriptionFeed()
                                    cacheDao.insertSubscriptionFeed(entities)
                                }
                            } else {
                                Log.w(TAG, "Network emit was empty!")
                            }
                            _uiState.update { it.copy(isLoading = false) }
                        }
                    } else {
                        Log.w(TAG, "No channels \u2014 skipping fetch")
                        _uiState.update { it.copy(isLoading = false) }
                    }
                }
        }

    }



    private fun updateVideos(videos: List<Video>) {
        val sortedVideos = videos.sortedByDescending { it.timestamp }

        val (shorts, regular) = sortedVideos.partition { video ->
            video.isShort || (video.duration in 1..120 && !video.isLive)
        }
        Log.i(TAG, "updateVideos: total=${sortedVideos.size} → regular=${regular.size}, shorts=${shorts.size}")
        _uiState.update { it.copy(recentVideos = regular, shorts = shorts) }
    }
    
    private fun parseRelativeTime(dateString: String): Long {
        try {
            val now = System.currentTimeMillis()
            val text = dateString.lowercase().trim()
            
            if (text.contains("scheduled") || text.contains("premiere")) return now + 86400000L
            if (text.contains("live")) return now + 3600000L // Boost live streams
            
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
            return System.currentTimeMillis() 
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
                            val url = item.optString("url")
                            val name = item.optString("name")
                            
                            if (url.isNotEmpty() && name.isNotEmpty()) {
                                var channelId = ""
                                if (url.contains("/channel/")) {
                                    channelId = url.substringAfter("/channel/")
                                } else if (url.contains("/user/")) {
                                    channelId = url.substringAfter("/user/")
                                }
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

    
    fun refreshFeed() {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            val channels = _uiState.value.subscribedChannels
            if (channels.isEmpty()) return@launch
            _uiState.update { it.copy(isLoading = true) }
            
            supervisorScope {
                com.flow.youtube.data.innertube.RssSubscriptionService.fetchSubscriptionVideos(
                    channelIds = channels.map { it.id },
                    maxTotal = 200
                ).collect { videos ->
                    if (videos.isNotEmpty()) {
                        updateVideos(videos)
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
                                timestamp = video.timestamp,
                                channelThumbnailUrl = video.channelThumbnailUrl,
                                isShort = video.isShort,
                                cachedAt = System.currentTimeMillis()
                            )
                        }
                        launch(PerformanceDispatcher.diskIO) {
                            cacheDao.clearSubscriptionFeed()
                            cacheDao.insertSubscriptionFeed(entities)
                        }
                    }
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun unsubscribe(channelId: String) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            subscriptionRepository.unsubscribe(channelId)
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
    val isFullWidthView: Boolean = false,
    val isShortsShelfEnabled: Boolean = true
)

