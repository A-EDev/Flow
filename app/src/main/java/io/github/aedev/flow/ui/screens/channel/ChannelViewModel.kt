package io.github.aedev.flow.ui.screens.channel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.local.ChannelSubscription
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.paging.ChannelVideosPagingSource
import io.github.aedev.flow.data.paging.ChannelPlaylistsPagingSource
import io.github.aedev.flow.utils.PerformanceDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val _shortsPagingFlow = MutableStateFlow<Flow<PagingData<Video>>?>(null)
    val shortsPagingFlow: StateFlow<Flow<PagingData<Video>>?> = _shortsPagingFlow.asStateFlow()
    private val _livePagingFlow = MutableStateFlow<Flow<PagingData<Video>>?>(null)
    val livePagingFlow: StateFlow<Flow<PagingData<Video>>?> = _livePagingFlow.asStateFlow()
    private val _playlistsPagingFlow = MutableStateFlow<Flow<PagingData<io.github.aedev.flow.data.model.Playlist>>?>(null)
    val playlistsPagingFlow: StateFlow<Flow<PagingData<io.github.aedev.flow.data.model.Playlist>>?> = _playlistsPagingFlow.asStateFlow()

    // Eagerly loaded full video lists (all pages) for filter support
    private val _videosAll = MutableStateFlow<List<Video>>(emptyList())
    val videosAll: StateFlow<List<Video>> = _videosAll.asStateFlow()

    private val _liveAll = MutableStateFlow<List<Video>>(emptyList())
    val liveAll: StateFlow<List<Video>> = _liveAll.asStateFlow()

    private val _isLoadingAllVideos = MutableStateFlow(false)
    val isLoadingAllVideos: StateFlow<Boolean> = _isLoadingAllVideos.asStateFlow()
    
    var listScrollIndex: Int = 0
        private set
    var listScrollOffset: Int = 0
        private set

    fun saveScrollPosition(index: Int, offset: Int) {
        listScrollIndex = index
        listScrollOffset = offset
    }

    private var subscriptionRepository: SubscriptionRepository? = null
    private var currentVideosTab: ListLinkHandler? = null
    private var currentShortsTab: ListLinkHandler? = null
    private var currentLiveTab: ListLinkHandler? = null
    private var currentPlaylistsTab: ListLinkHandler? = null
    
    companion object {
        private const val TAG = "ChannelViewModel"
        /** Delay between page fetches — keeps request pattern human-like, avoids 429s */
        private const val PAGE_DELAY_MS = 800L
        /** Safety cap: stops loading beyond this many pages (~1500 videos) */
        private const val MAX_PAGES = 50
    }
    
    fun initialize(context: android.content.Context) {
        if (subscriptionRepository == null) {
            subscriptionRepository = SubscriptionRepository.getInstance(context)
        }
    }
    
    /**
     *  PERFORMANCE OPTIMIZED: Load channel with timeout protection
     */
    fun loadChannel(channelUrl: String) {
        if (channelUrl.isBlank()) {
            _uiState.update { it.copy(error = "Invalid channel URL", isLoading = false) }
            return
        }
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            _uiState.update { it.copy(isLoading = true, error = null) }
            
            try {
                Log.d(TAG, "Loading channel: $channelUrl")
                
                // Normalize the URL
                val normalizedUrl = normalizeChannelUrl(channelUrl)
                Log.d(TAG, "Normalized URL: $normalizedUrl")
                
                val channelInfo = withTimeoutOrNull(20_000L) {
                    withContext(PerformanceDispatcher.networkIO) {
                        // Use NewPipe to fetch channel info
                        ChannelInfo.getInfo(NewPipe.getService(0), normalizedUrl)
                    }
                }
                
                if (channelInfo == null) {
                    _uiState.update { 
                        it.copy(
                            error = "Channel loading timed out",
                            isLoading = false
                        )
                    }
                    return@launch
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
                
                // Load channel tabs (Videos, Shorts, Playlists)
                loadChannelTabs(channelInfo)
                
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
    
    /**
     *  PERFORMANCE OPTIMIZED: Load channel tabs with optimized dispatcher
     */
    private fun loadChannelTabs(channelInfo: ChannelInfo) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                _uiState.update { it.copy(isLoadingVideos = true) }
                
                withContext(PerformanceDispatcher.networkIO) {
                    // Find the tabs
                    for (tab in channelInfo.tabs) {
                        try {
                            val tabName = tab.contentFilters.joinToString()
                            val tabUrl = tab.url ?: ""
                            Log.d(TAG, "Checking tab: Name=$tabName, URL=$tabUrl")
                            
                            val isLive = tabName.contains("live", ignoreCase = true) || 
                                         tabUrl.contains("/streams", ignoreCase = true)
                                         
                            val isVideos = (tabName.contains("video", ignoreCase = true) || 
                                         tabName.contains("Videos", ignoreCase = true) ||
                                         tabUrl.contains("/videos", ignoreCase = true)) && !isLive
                                         
                            val isShorts = tabName.contains("shorts", ignoreCase = true) || 
                                         tabUrl.contains("/shorts", ignoreCase = true)
                                         
                            val isPlaylists = tabName.contains("playlist", ignoreCase = true) || 
                                            tabName.contains("Playlists", ignoreCase = true) ||
                                            tabUrl.contains("/playlists", ignoreCase = true)
                            
                            if (isLive) {
                                currentLiveTab = tab
                                Log.d(TAG, "Found live tab")
                            }
                            
                            if (isVideos) {
                                currentVideosTab = tab
                                Log.d(TAG, "Found videos tab")
                            }
                            
                            if (isShorts) {
                                currentShortsTab = tab
                                Log.d(TAG, "Found shorts tab")
                            }
                            
                            if (isPlaylists) {
                                currentPlaylistsTab = tab
                                Log.d(TAG, "Found playlists tab")
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error checking tab", e)
                        }
                    }
                }
                
                // Load all pages for Videos tab (enables full-list filtering)
                val videosTab = currentVideosTab
                if (videosTab != null) {
                    viewModelScope.launch(PerformanceDispatcher.networkIO) {
                        loadAllPages(videosTab, channelInfo, _videosAll)
                    }
                }

                // Create the paging flow for Shorts
                if (currentShortsTab != null) {
                    _shortsPagingFlow.value = Pager(
                        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                        pagingSourceFactory = { ChannelVideosPagingSource(channelInfo, currentShortsTab) }
                    ).flow.cachedIn(viewModelScope)
                }

                val liveTab = currentLiveTab
                if (liveTab != null) {
                    viewModelScope.launch(PerformanceDispatcher.networkIO) {
                        loadAllPages(liveTab, channelInfo, _liveAll)
                    }
                }

                // Create the paging flow for Playlists
                if (currentPlaylistsTab != null) {
                    _playlistsPagingFlow.value = Pager(
                        config = PagingConfig(pageSize = 20, enablePlaceholders = false),
                        pagingSourceFactory = { ChannelPlaylistsPagingSource(channelInfo, currentPlaylistsTab) }
                    ).flow.cachedIn(viewModelScope)
                }
                
                _uiState.update { it.copy(isLoadingVideos = false) }
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load channel tabs", e)
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
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            subscriptionRepository?.getSubscription(channelId)?.collect { subscription ->
                _uiState.update { 
                    it.copy(
                        isSubscribed = subscription != null,
                        isNotificationsEnabled = subscription?.isNotificationEnabled ?: false
                    ) 
                }
            }
        }
    }
    
    fun toggleSubscription() {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
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

    fun unsubscribe() {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            val state = _uiState.value
            val channelId = state.channelId ?: return@launch
            subscriptionRepository?.unsubscribe(channelId)
        }
    }

    fun setNotificationState(enabled: Boolean) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            val state = _uiState.value
            val channelId = state.channelId ?: return@launch
            subscriptionRepository?.updateNotificationState(channelId, enabled)
        }
    }
    
    fun selectTab(tabIndex: Int) {
        _uiState.update { it.copy(selectedTab = tabIndex) }
    }

    /**
     * Fetches all pages for a channel tab sequentially and emits results
     * incrementally into [target]. This allows filters (Popular/Latest/Oldest)
     * to operate on the full video list rather than just the first batch.
     */
    private suspend fun loadAllPages(
        tab: ListLinkHandler,
        channelInfo: ChannelInfo,
        target: MutableStateFlow<List<Video>>
    ) {
        _isLoadingAllVideos.value = true
        try {
            val service = NewPipe.getService(0)
            val accumulated = mutableListOf<Video>()

            // First page — no delay, show content immediately
            val initial = ChannelTabInfo.getInfo(service, tab)
            initial.relatedItems.filterIsInstance<StreamInfoItem>()
                .mapTo(accumulated) { it.toChannelVideo(channelInfo) }
            target.value = accumulated.toList()

            var nextPage = initial.nextPage
            var pagesLoaded = 1
            while (nextPage != null && pagesLoaded < MAX_PAGES) {
                // Throttle subsequent pages — keeps the request pattern human-like
                // and avoids triggering YouTube's burst rate-limiting (429s)
                delay(PAGE_DELAY_MS)
                val more = ChannelTabInfo.getMoreItems(service, tab, nextPage)
                more.items.filterIsInstance<StreamInfoItem>()
                    .mapTo(accumulated) { it.toChannelVideo(channelInfo) }
                target.value = accumulated.toList()
                nextPage = more.nextPage
                pagesLoaded++
            }
        } catch (e: Exception) {
            // Rate-limited or network error — user keeps whatever loaded so far
            Log.w(TAG, "Page loading stopped after rate limit or error", e)
        } finally {
            _isLoadingAllVideos.value = false
        }
    }

    private fun StreamInfoItem.toChannelVideo(channelInfo: ChannelInfo): Video {
        val videoId = when {
            url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
            url.contains("/watch/") -> url.substringAfter("/watch/").substringBefore("?")
            url.contains("/shorts/") -> url.substringAfter("/shorts/").substringBefore("?")
            else -> url.substringAfterLast("/").substringBefore("?")
        }
        val thumbnail = thumbnails.maxByOrNull { it.width }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        return Video(
            id = videoId,
            title = name,
            thumbnailUrl = thumbnail,
            channelName = uploaderName ?: channelInfo.name,
            channelId = channelInfo.id,
            channelThumbnailUrl = channelInfo.avatars.firstOrNull()?.url ?: "",
            viewCount = viewCount,
            duration = duration.toInt(),
            uploadDate = io.github.aedev.flow.utils.formatTimeAgo(uploadDate?.offsetDateTime()?.toString()),
            description = ""
        )
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
    val isNotificationsEnabled: Boolean = false,
    val selectedTab: Int = 0 // 0: Videos, 1: Shorts, 2: Live, 3: Playlists, 4: About
)

