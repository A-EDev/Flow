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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class SubscriptionsViewModel : ViewModel() {
    
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var viewHistory: ViewHistory
    
    private val _uiState = MutableStateFlow(SubscriptionsUiState())
    val uiState: StateFlow<SubscriptionsUiState> = _uiState.asStateFlow()
    
    fun initialize(context: Context) {
        subscriptionRepository = SubscriptionRepository.getInstance(context)
        viewHistory = ViewHistory.getInstance(context)
        
        // Load subscriptions
        viewModelScope.launch {
            subscriptionRepository.getAllSubscriptions().collect { subscriptions ->
                val channels = subscriptions.map { sub ->
                    Channel(
                        id = sub.channelId,
                        name = sub.channelName,
                        thumbnailUrl = sub.channelThumbnail,
                        subscriberCount = 0L, // We don't store this
                        isSubscribed = true
                    )
                }
                _uiState.update { it.copy(subscribedChannels = channels) }
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
                _uiState.update { it.copy(recentVideos = videos) }
            }
        }
    }
    
    fun selectChannel(channelId: String?) {
        _uiState.update { it.copy(selectedChannelId = channelId) }
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
    val selectedChannelId: String? = null,
    val isLoading: Boolean = false
)
