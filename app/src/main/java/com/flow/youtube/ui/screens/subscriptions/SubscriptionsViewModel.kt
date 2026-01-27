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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
// duplicate import removed

class SubscriptionsViewModel : ViewModel() {
    
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var viewHistory: ViewHistory
    
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()

    // Repository to fetch feeds and video details
    private val ytRepository: YouTubeRepository = YouTubeRepository.getInstance()
    
    fun initialize(context: Context) {
        subscriptionRepository = SubscriptionRepository.getInstance(context)
        viewHistory = ViewHistory.getInstance(context)
        
        // Load subscriptions and then load feeds
        viewModelScope.launch {
            subscriptionRepository.getAllSubscriptions().collect { subscriptions ->
                val channels = subscriptions.map { sub ->
                    Channel(
                        id = sub.channelId,
                        name = sub.channelName,
                        thumbnailUrl = sub.channelThumbnail,
                        subscriberCount = 0L, // We don't store this locally
                        isSubscribed = true
                    )
                }
                _uiState.update { it.copy(subscribedChannels = channels) }

                // Fetch feeds for these channels
                if (channels.isNotEmpty()) {
                    _uiState.update { it.copy(isLoading = true) }
                    val ids = channels.map { it.id }
                    val videos = ytRepository.getVideosForChannels(ids, perChannelLimit = 4, totalLimit = 80)
                    updateVideos(videos)
                    _uiState.update { it.copy(isLoading = false) }
                } else {
                    _uiState.update { it.copy(recentVideos = emptyList(), shorts = emptyList()) }
                }
            }
        }
        
        // Load recent videos from history (as a proxy for subscription feed)
        viewModelScope.launch {
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
                else -> 0L
            }
            
            return now - (value * multiplier)
        } catch (e: Exception) {
            return 0L
        }
    }
    
    fun selectChannel(channelId: String?) {
        _uiState.update { it.copy(selectedChannelId = channelId) }
    }

    /**
     * Manually refresh subscription feed
     */
    fun refreshFeed() {
        viewModelScope.launch {
            val channels = _uiState.value.subscribedChannels
            if (channels.isEmpty()) return@launch
            _uiState.update { it.copy(isLoading = true) }
            val ids = channels.map { it.id }
            val videos = ytRepository.getVideosForChannels(ids, perChannelLimit = 6, totalLimit = 100)
            updateVideos(videos)
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun unsubscribe(channelId: String) {
        viewModelScope.launch {
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
        viewModelScope.launch {
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
