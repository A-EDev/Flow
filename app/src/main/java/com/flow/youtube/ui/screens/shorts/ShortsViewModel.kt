package com.flow.youtube.ui.screens.shorts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.LikedVideosRepository
import com.flow.youtube.data.local.PlaylistRepository
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.model.ShortVideo
import com.flow.youtube.data.model.toShortVideo
import com.flow.youtube.data.model.toVideo
import com.flow.youtube.data.repository.YouTubeRepository
import com.flow.youtube.data.shorts.ShortsRepository
import com.flow.youtube.utils.PerformanceDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * ShortsViewModel — Hilt-injected, InnerTube-first Shorts engine.
 *
 * Architecture:
 * - Uses [ShortsRepository] for InnerTube reel API (primary) + NewPipe (fallback)
 * - [ShortVideo] as the domain model (not generic [Video])
 * - Continuation-based infinite scroll (InnerTube pagination)
 * - Pre-resolves streams for adjacent shorts
 * - Reactive state via StateFlow for like/subscribe/save
 */
@HiltViewModel
class ShortsViewModel @Inject constructor(
    private val repository: YouTubeRepository,
    private val shortsRepository: ShortsRepository,
    private val likedVideosRepository: LikedVideosRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ShortsUiState())
    val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()
    
    private var isLoadingMore = false
    
    private val _commentsState = MutableStateFlow<List<com.flow.youtube.data.model.Comment>>(emptyList())
    val commentsState: StateFlow<List<com.flow.youtube.data.model.Comment>> = _commentsState.asStateFlow()
    
    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

    private val _savedShortIds = MutableStateFlow<Set<String>>(emptySet())

    init {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            playlistRepository.getSavedShortsFlow().collect { savedVideos ->
                _savedShortIds.value = savedVideos.map { it.id }.toSet()
            }
        }

        viewModelScope.launch {
            shortsRepository.enrichmentUpdates.collect { enrichedShorts ->
                val current = _uiState.value.shorts
                if (current.isNotEmpty() && enrichedShorts.isNotEmpty()) {
                    val enrichedMap = enrichedShorts.associateBy { it.id }
                    val updated = current.map { existing ->
                        enrichedMap[existing.id]?.let { enriched ->
                            if (enriched.title != "Short" || enriched.channelName != "Unknown") enriched
                            else existing
                        } ?: existing
                    }
                    _uiState.value = _uiState.value.copy(shorts = updated)
                }
            }
        }
    }

    // REACTIVE STATE — Single Source of Truth

    /**
     * Returns a StateFlow<Boolean> for whether a video is liked.
     * UI should collectAsState() from this directly.
     */
    fun isVideoLikedState(videoId: String): StateFlow<Boolean> {
        val flow = MutableStateFlow(false)
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            likedVideosRepository.getLikeState(videoId).collect { likeState ->
                flow.value = likeState == "LIKED"
            }
        }
        return flow.asStateFlow()
    }

    /**
     * Returns a StateFlow<Boolean> for whether a channel is subscribed.
     */
    fun isChannelSubscribedState(channelId: String): StateFlow<Boolean> {
        val flow = MutableStateFlow(false)
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            subscriptionRepository.isSubscribed(channelId).collect { subscribed ->
                flow.value = subscribed
            }
        }
        return flow.asStateFlow()
    }

    /**
     * Returns a StateFlow<Boolean> for whether a short is saved.
     */
    fun isShortSavedState(videoId: String): StateFlow<Boolean> {
        return _savedShortIds.map { it.contains(videoId) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = _savedShortIds.value.contains(videoId)
            )
    }
    
    // FEED LOADING — InnerTube Primary    
    /**
     * Load the initial Shorts feed from InnerTube reel API.
     * If [startVideoId] is provided, seeds the reel sequence from that video.
     */
    fun loadShorts(startVideoId: String? = null) {
        if (_uiState.value.isLoading) return
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val result = shortsRepository.getShortsFeed(seedVideoId = startVideoId)
                var shorts = result.shorts
                
                // If startVideoId provided but not in results, fetch it separately
                var startIndex = 0
                if (startVideoId != null) {
                    val idx = shorts.indexOfFirst { it.id == startVideoId }
                    if (idx >= 0) {
                        startIndex = idx
                    } else {
                        val startVideo = withTimeoutOrNull(5_000L) {
                            repository.getVideo(startVideoId)
                        }?.toShortVideo()
                        if (startVideo != null) {
                            shorts = listOf(startVideo) + shorts
                            startIndex = 0
                        }
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    shorts = shorts,
                    currentIndex = startIndex,
                    isLoading = false,
                    hasMorePages = result.continuation != null || shorts.size >= 5,
                    continuation = result.continuation
                )
                
                // Pre-resolve streams for the first few shorts
                if (shorts.isNotEmpty()) {
                    val idsToPreload = shorts.take(3).map { it.id }
                    preResolveStreams(idsToPreload)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error loading shorts", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load shorts"
                )
            }
        }
    }
    
    /**
     * Load more shorts using continuation token.
     */
    fun loadMoreShorts() {
        if (isLoadingMore || !_uiState.value.hasMorePages) return
        
        isLoadingMore = true
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val result = withTimeoutOrNull(15_000L) {
                    shortsRepository.loadMore(_uiState.value.continuation)
                }
                
                if (result != null && result.shorts.isNotEmpty()) {
                    val currentShorts = _uiState.value.shorts
                    val updatedShorts = (currentShorts + result.shorts).distinctBy { it.id }
                    
                    _uiState.value = _uiState.value.copy(
                        shorts = updatedShorts,
                        continuation = result.continuation,
                        isLoadingMore = false,
                        hasMorePages = result.continuation != null || result.shorts.isNotEmpty()
                    )
                } else {
                    // Try NewPipe fallback for more content — clear stale continuation
                    val fallback = withTimeoutOrNull(10_000L) {
                        repository.getShorts(_uiState.value.newPipePage)
                    }
                    
                    if (fallback != null) {
                        val (newVideos, nextPage) = fallback
                        val newShorts = newVideos
                            .filter { it.duration in 1..60 }
                            .map { it.toShortVideo() }
                        val currentShorts = _uiState.value.shorts
                        val updatedShorts = (currentShorts + newShorts).distinctBy { it.id }
                        
                        _uiState.value = _uiState.value.copy(
                            shorts = updatedShorts,
                            newPipePage = nextPage,
                            continuation = null, 
                            isLoadingMore = false,
                            hasMorePages = nextPage != null
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isLoadingMore = false,
                            hasMorePages = false
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading more shorts", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.message ?: "Failed to load more shorts"
                )
            } finally {
                isLoadingMore = false
            }
        }
    }
    
    // SAVED SHORTS    
    fun loadSavedShorts(startVideoId: String? = null) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            playlistRepository.getSavedShortsFlow().collect { savedVideos ->
                val shorts = savedVideos.map { it.toShortVideo() }
                var startIndex = if (startVideoId != null) {
                    shorts.indexOfFirst { it.id == startVideoId }.coerceAtLeast(0)
                } else 0
                
                _uiState.value = _uiState.value.copy(
                    shorts = shorts,
                    currentIndex = startIndex,
                    isLoading = false,
                    hasMorePages = false
                )
            }
        }
    }
    
    // PAGE TRACKING & PRE-LOADING 
    fun updateCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
        
        if (index >= _uiState.value.shorts.size - 5) {
            loadMoreShorts()
        }
    }
    
    /**
     * Pre-resolve stream URLs for adjacent shorts to enable instant transitions.
     */
    fun preResolveStreams(videoIds: List<String>) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            shortsRepository.preResolveStreams(videoIds)
        }
    }
    
    // STREAM RESOLUTION 
    /**
     * Get stream info for a specific video. Used by the player.
     */
    suspend fun getVideoStreamInfo(videoId: String) = shortsRepository.resolveStreamInfo(videoId)
    
    // USER ACTIONS
    suspend fun toggleLike(short: ShortVideo) {
        val video = short.toVideo()
        val isLiked = likedVideosRepository.getLikeState(video.id).first() == "LIKED"
        
        if (isLiked) {
            likedVideosRepository.removeLikeState(video.id)
        } else {
            likedVideosRepository.likeVideo(
                com.flow.youtube.data.local.LikedVideoInfo(
                    videoId = video.id,
                    title = video.title,
                    thumbnail = video.thumbnailUrl,
                    channelName = video.channelName
                )
            )
        }
    }
    
    suspend fun toggleSubscription(channelId: String, channelName: String, channelThumbnail: String) {
        val isSubscribed = subscriptionRepository.isSubscribed(channelId).first()
        
        if (isSubscribed) {
            subscriptionRepository.unsubscribe(channelId)
        } else {
            subscriptionRepository.subscribe(
                com.flow.youtube.data.local.ChannelSubscription(
                    channelId = channelId,
                    channelName = channelName,
                    channelThumbnail = channelThumbnail
                )
            )
        }
    }
    
    fun toggleSaveShort(short: ShortVideo) {
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            val video = short.toVideo()
            if (playlistRepository.isInSavedShorts(video.id)) {
                playlistRepository.removeFromSavedShorts(video.id)
            } else {
                playlistRepository.addToSavedShorts(video)
            }
        }
    }
    
    // COMMENTS
    fun loadComments(videoId: String) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            _isLoadingComments.value = true
            _commentsState.value = emptyList()
            try {
                val comments = withTimeoutOrNull(10_000L) {
                    repository.getComments(videoId)
                } ?: emptyList()
                _commentsState.value = comments
            } catch (e: Exception) {
                Log.e(TAG, "Error loading comments", e)
            } finally {
                _isLoadingComments.value = false
            }
        }
    }

    fun loadCommentReplies(comment: com.flow.youtube.data.model.Comment) {
        val currentShort = _uiState.value.shorts.getOrNull(_uiState.value.currentIndex) ?: return
        val repliesPage = comment.repliesPage ?: return
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val url = "https://www.youtube.com/watch?v=${currentShort.id}"
                val (replies, nextPage) = repository.getCommentReplies(url, repliesPage)
                
                _commentsState.value = _commentsState.value.map { c ->
                    if (c.id == comment.id) {
                        c.copy(
                            replies = replies,
                            repliesPage = nextPage
                        )
                    } else c
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading replies", e)
            }
        }
    }
    
    companion object {
        private const val TAG = "ShortsViewModel"
    }
}

/**
 * UI state for the Shorts screen.
 * Uses [ShortVideo] instead of generic [Video] for Shorts-specific data.
 */
data class ShortsUiState(
    val shorts: List<ShortVideo> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val continuation: String? = null, 
    val newPipePage: org.schabi.newpipe.extractor.Page? = null,
    val error: String? = null
)
