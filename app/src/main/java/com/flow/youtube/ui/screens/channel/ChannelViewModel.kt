package com.flow.youtube.ui.screens.channel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.local.ChannelSubscription
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.paging.ChannelVideosPagingSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

class ChannelViewModel : ViewModel() {
    
    private val _uiState = MutableStateFlow(ChannelUiState())
    val uiState: StateFlow<ChannelUiState> = _uiState.asStateFlow()
    
    // Paging flow for channel videos with infinite scroll
    private val _videosPagingFlow = MutableStateFlow<Flow<PagingData<Video>>?>(null)
    val videosPagingFlow: StateFlow<Flow<PagingData<Video>>?> = _videosPagingFlow.asStateFlow()
    
    private var subscriptionRepository: SubscriptionRepository? = null
    private var currentVideosTab: ListLinkHandler? = null
    
    companion object {
        private const val TAG = "ChannelViewModel"
    }
    
    fun initialize(context: android.content.Context) {
        if (subscriptionRepository == null) {
            subscriptionRepository = SubscriptionRepository.getInstance(context)
        }
    }
    
    fun loadChannel(channelUrl: String) {
        if (channelUrl.isBlank()) {
            _uiState.update { it.copy(error = "Invalid channel URL", isLoading = false) }
            return
        }
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                Log.d(TAG, "Loading channel: $channelUrl")
                
                // Normalize the URL
                val normalizedUrl = normalizeChannelUrl(channelUrl)
                Log.d(TAG, "Normalized URL: $normalizedUrl")
                
                val channelInfo = withContext(Dispatchers.IO) {
                    // Use NewPipe to fetch channel info
                    ChannelInfo.getInfo(NewPipe.getService(0), normalizedUrl)
                }
                
                Log.d(TAG, "Channel loaded: ${channelInfo.name}")
                
                // Extract channel ID from URL
                val channelId = extractChannelId(normalizedUrl)
                
                _uiState.update { 
                    it.copy(
                        channelId = channelId,
                        channelInfo = channelInfo,
                        isLoading = false
                    )
                }
                
                // Load subscription state
                loadSubscriptionState(channelId)
                
                // Load channel videos
                loadChannelVideos(channelInfo)
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load channel", e)
                _uiState.update { 
                    it.copy(
                        error = e.message ?: "Failed to load channel",
                        isLoading = false
                    )
                }
            }
        }
    }
    
    private fun normalizeChannelUrl(url: String): String {
        // If already a full URL, return as is
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url
        }
        
        // If it looks like a channel ID (starts with UC), construct URL
        if (url.startsWith("UC") && url.length >= 24) {
            return "https://www.youtube.com/channel/$url"
        }
        
        // If it's a handle (starts with @), construct URL
        if (url.startsWith("@")) {
            return "https://www.youtube.com/$url"
        }
        
        // Default: assume it's a channel ID
        return "https://www.youtube.com/channel/$url"
    }
    
    private fun loadChannelVideos(channelInfo: ChannelInfo) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isLoadingVideos = true) }
                
                withContext(Dispatchers.IO) {
                    // Find the videos tab
                    for (tab in channelInfo.tabs) {
                        try {
                            val tabName = tab.contentFilters.joinToString()
                            Log.d(TAG, "Checking tab: $tabName")
                            
                            if (tabName.contains("video", ignoreCase = true) || 
                                tabName.contains("Videos", ignoreCase = true)) {
                                
                                currentVideosTab = tab
                                Log.d(TAG, "Found videos tab: $tabName")
                                break
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error checking tab", e)
                        }
                    }
                }
                
                // Create the paging flow
                if (currentVideosTab != null) {
                    val pagingFlow = Pager(
                        config = PagingConfig(
                            pageSize = 20,
                            enablePlaceholders = false,
                            prefetchDistance = 5,
                            initialLoadSize = 20
                        ),
                        pagingSourceFactory = {
                            ChannelVideosPagingSource(channelInfo, currentVideosTab)
                        }
                    ).flow.cachedIn(viewModelScope)
                    
                    _videosPagingFlow.value = pagingFlow
                    Log.d(TAG, "Paging flow created for channel videos")
                } else {
                    Log.w(TAG, "No videos tab found, falling back to empty list")
                }
                
                _uiState.update { it.copy(isLoadingVideos = false) }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load channel videos", e)
                _uiState.update { 
                    it.copy(
                        isLoadingVideos = false,
                        videosError = e.message
                    )
                }
            }
        }
    }
    
    private fun extractVideoId(url: String): String {
        return when {
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            else -> url.substringAfterLast("/").substringBefore("?")
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
    val channelVideos: List<Video> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingVideos: Boolean = false,
    val error: String? = null,
    val videosError: String? = null,
    val isSubscribed: Boolean = false,
    val selectedTab: Int = 0 // 0: Videos, 1: Playlists, 2: About
)
