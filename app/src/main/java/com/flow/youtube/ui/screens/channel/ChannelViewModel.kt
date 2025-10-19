package com.flow.youtube.ui.screens.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.local.ChannelSubscription
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

class ChannelViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()
    
    private var subscriptionRepository: SubscriptionRepository? = null
    
    fun initialize(context: android.content.Context) {
        if (subscriptionRepository == null) {
            subscriptionRepository = SubscriptionRepository.getInstance(context)
        }
    }
    
    fun loadChannel(channelUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                // Use NewPipe to fetch channel info
                val service: StreamingService = NewPipe.getService(0) // YouTube service
                val extractor = service.getChannelExtractor(channelUrl)
                extractor.fetchPage()
                
                val channelInfo = ChannelInfo.getInfo(extractor)
                
                // Extract channel ID from URL
                val channelId = extractChannelId(channelUrl)
                
                _uiState.update { 
                    it.copy(
                        channelId = channelId,
                        channelInfo = channelInfo,
                        isLoading = false
                    )
                }
                
                // Load subscription state
                loadSubscriptionState(channelId)
                
            } catch (e: Exception) {
                _uiState.update { 
                    it.copy(
                        error = e.message ?: "Failed to load channel",
                        isLoading = false
                    )
                }
            }
        }
    }
    
    private fun extractChannelId(url: String): String {
        // Extract channel ID from YouTube URL
        // Format: https://youtube.com/channel/UC... or https://youtube.com/c/...
        return when {
            url.contains("/channel/") -> {
                url.substringAfter("/channel/").substringBefore("/").substringBefore("?")
            }
            url.contains("/c/") -> {
                url.substringAfter("/c/").substringBefore("/").substringBefore("?")
            }
            url.contains("/user/") -> {
                url.substringAfter("/user/").substringBefore("/").substringBefore("?")
            }
            url.contains("/@") -> {
                url.substringAfter("/@").substringBefore("/").substringBefore("?")
            }
            else -> url
        }
    }
    
    private fun loadSubscriptionState(channelId: String) {
        viewModelScope.launch {
            subscriptionRepository?.isSubscribed(channelId)?.collect { isSubscribed ->
                _uiState.update { it.copy(isSubscribed = isSubscribed) }
            }
        }
    }
    
    fun toggleSubscription() {
        viewModelScope.launch {
            val state = _uiState.value
            val channelId = state.channelId ?: return@launch
            val channelName = state.channelInfo?.name ?: return@launch
            val channelThumbnail = try { 
                state.channelInfo?.avatars?.firstOrNull()?.url ?: ""
            } catch (e: Exception) { 
                ""
            }
            
            if (state.isSubscribed) {
                // Unsubscribe
                subscriptionRepository?.unsubscribe(channelId)
            } else {
                // Subscribe
                val subscription = ChannelSubscription(
                    channelId = channelId,
                    channelName = channelName,
                    channelThumbnail = channelThumbnail,
                    subscribedAt = System.currentTimeMillis()
                )
                subscriptionRepository?.subscribe(subscription)
            }
        }
    }
    
    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }
}

data class ChannelUiState(
    val channelId: String? = null,
    val channelInfo: ChannelInfo? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isSubscribed: Boolean = false,
    val selectedTab: Int = 0 // 0: Videos, 1: Playlists, 2: About
)
