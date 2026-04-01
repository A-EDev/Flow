package io.github.aedev.flow.ui.screens.shorts

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.SubscriptionRepository
import io.github.aedev.flow.data.model.ShortVideo
import io.github.aedev.flow.data.model.toShortVideo
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.data.repository.YouTubeRepository
import io.github.aedev.flow.data.shorts.ShortsRepository
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import io.github.aedev.flow.utils.PerformanceDispatcher
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import io.github.aedev.flow.data.recommendation.FlowNeuroEngine
import io.github.aedev.flow.data.recommendation.InteractionType
import io.github.aedev.flow.ui.components.FeedInvalidationBus

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
    
    private val _commentsState = MutableStateFlow<List<io.github.aedev.flow.data.model.Comment>>(emptyList())
    val commentsState: StateFlow<List<io.github.aedev.flow.data.model.Comment>> = _commentsState.asStateFlow()
    
    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()

    private val _savedShortIds = MutableStateFlow<Set<String>>(emptySet())

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }
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

        // Append discovery-ranked items when background discovery finishes after InnerTube fast-path
        viewModelScope.launch {
            shortsRepository.discoveryFeedUpdate.collect { newShorts ->
                val current = _uiState.value.shorts
                if (newShorts.isEmpty() || current.isEmpty()) return@collect
                val existingIds = current.map { it.id }.toHashSet()
                val toAppend = newShorts.filter { it.id !in existingIds }
                if (toAppend.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(shorts = current + toAppend)
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
                
                var startIndex = 0
                if (startVideoId != null) {
                    val idx = shorts.indexOfFirst { it.id == startVideoId }
                    when {
                        idx == 0 -> {
                            // Already at front — nothing to do
                        }
                        idx > 0 -> {
                            // Found further down the list — move it to front
                            shorts = listOf(shorts[idx]) + shorts.filterIndexed { i, _ -> i != idx }
                        }
                        else -> {
                            // Not in the list at all — fetch separately and prepend
                            val startVideo = withTimeoutOrNull(5_000L) {
                                repository.getVideo(startVideoId)
                            }?.toShortVideo()
                            if (startVideo != null) {
                                shorts = listOf(startVideo) + shorts
                            }
                        }
                    }
                }
                
                if (shorts.isNotEmpty()) {
                    val idsToPreload = shorts.take(3).map { it.id }
                    viewModelScope.launch(PerformanceDispatcher.networkIO) {
                        preResolveStreams(idsToPreload)
                    }
                }

                _uiState.value = _uiState.value.copy(
                    shorts = shorts,
                    currentIndex = startIndex,
                    isLoading = false,
                    hasMorePages = result.continuation != null || shorts.size >= 5,
                    continuation = result.continuation
                )
                
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
                    val fresh = withTimeoutOrNull(12_000L) {
                        shortsRepository.forceRefresh()
                    }

                    if (fresh != null && fresh.shorts.isNotEmpty()) {
                        val currentShorts = _uiState.value.shorts
                        val updatedShorts = (currentShorts + fresh.shorts).distinctBy { it.id }
                        _uiState.value = _uiState.value.copy(
                            shorts = updatedShorts,
                            continuation = fresh.continuation,
                            isLoadingMore = false,
                            hasMorePages = fresh.continuation != null || fresh.shorts.isNotEmpty()
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
                io.github.aedev.flow.data.local.LikedVideoInfo(
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
                io.github.aedev.flow.data.local.ChannelSubscription(
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
                val result = withTimeoutOrNull(10_000L) {
                    repository.getComments(videoId)
                }
                _commentsState.value = result?.first ?: emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Error loading comments", e)
            } finally {
                _isLoadingComments.value = false
            }
        }
    }

    fun loadCommentReplies(comment: io.github.aedev.flow.data.model.Comment) {
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

    fun wantMoreLikeThis(short: ShortVideo) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val video = short.toVideo()
                FlowNeuroEngine.onVideoInteraction(
                    video,
                    InteractionType.LIKED
                )
                _snackbarMessage.value = "We'll show more like this"
                Log.d(TAG, "Want more like this: ${short.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error signaling want more", e)
            }
        }
    }

    fun notInterested(short: ShortVideo) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val video = short.toVideo()
                FlowNeuroEngine.markNotInterested(video)
                FeedInvalidationBus.emit(FeedInvalidationBus.Event.NotInterested(video.id, video.channelId))

                val currentShorts = _uiState.value.shorts
                val updatedShorts = currentShorts.filter { it.id != short.id }

                _uiState.value = _uiState.value.copy(
                    shorts = updatedShorts,
                    currentIndex = _uiState.value.currentIndex.coerceAtMost(
                        (updatedShorts.size - 1).coerceAtLeast(0)
                    )
                )

                _snackbarMessage.value = "Got it, showing less of this"
                Log.d(TAG, "Not interested: ${short.title}")
            } catch (e: Exception) {
                Log.e(TAG, "Error marking not interested", e)
            }
        }
    }
    
    /**
     * Fetch stream sizes in bytes for all video formats of a Short.
     * Uses InnerTube MOBILE player endpoint — same approach as VideoPlayerViewModel.
     */
    suspend fun fetchStreamSizes(videoId: String): Map<String, Long> = withContext(PerformanceDispatcher.networkIO) {
        try {
            val playerResult = YouTube.player(videoId, client = YouTubeClient.MOBILE)
            playerResult.getOrNull()?.let { playerResponse ->
                val sizes = mutableMapOf<String, Long>()

                val audioFormats = playerResponse.streamingData
                    ?.adaptiveFormats?.filter { it.isAudio } ?: emptyList()
                val bestAacSize = audioFormats
                    .filter { it.mimeType.contains("mp4", ignoreCase = true) }
                    .maxByOrNull { it.bitrate }?.contentLength ?: 0L
                val bestOpusSize = audioFormats
                    .filter { it.mimeType.contains("webm", ignoreCase = true) }
                    .maxByOrNull { it.bitrate }?.contentLength ?: 0L
                val bestAnyAudioSize = audioFormats
                    .maxByOrNull { it.bitrate }?.contentLength ?: 0L

                playerResponse.streamingData?.formats?.forEach { format ->
                    if (format.height != null && format.contentLength != null) {
                        val codecKey = VideoPlayerUtils.codecKeyFromMimeType(format.mimeType)
                        val key = VideoPlayerUtils.streamSizeKey(format.height, codecKey)
                        sizes[key] = format.contentLength
                    }
                }
                playerResponse.streamingData?.adaptiveFormats?.forEach { format ->
                    if (format.height != null && format.contentLength != null && !format.isAudio) {
                        val codecKey = VideoPlayerUtils.codecKeyFromMimeType(format.mimeType)
                        val isMp4Video = format.mimeType.contains("mp4", ignoreCase = true)
                        val audioSize = when {
                            isMp4Video && bestAacSize > 0 -> bestAacSize
                            !isMp4Video && bestOpusSize > 0 -> bestOpusSize
                            else -> bestAnyAudioSize
                        }
                        val totalSize = format.contentLength + audioSize
                        val key = VideoPlayerUtils.streamSizeKey(format.height, codecKey)
                        val currentSize = sizes[key] ?: 0L
                        if (totalSize > currentSize) sizes[key] = totalSize
                    }
                }
                sizes
            } ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch stream sizes for $videoId: ${e.message}")
            emptyMap()
        }
    }

    /**
     * Load detailed metadata (description, upload date, like count) for a Short from its StreamInfo.
     * The StreamInfo is typically already cached from playback setup — so this is usually instant.
     * Triggers a UI state update so FlowDescriptionBottomSheet always shows accurate data.
     */
    fun loadShortDetails(videoId: String) {
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val streamInfo = shortsRepository.resolveStreamInfo(videoId) ?: return@launch
                val uploadDate = streamInfo.textualUploadDate?.takeIf { it.isNotBlank() } ?: ""
                val description = streamInfo.description?.content?.takeIf { it.isNotBlank() } ?: ""
                val likeCountText = if (streamInfo.likeCount > 0) formatLikeText(streamInfo.likeCount) else null

                val current = _uiState.value.shorts
                val updated = current.map { short ->
                    if (short.id == videoId) {
                        short.copy(
                            uploadDate = uploadDate,
                            description = description.ifBlank { short.description },
                            likeCountText = likeCountText ?: short.likeCountText
                        )
                    } else short
                }
                if (updated != current) {
                    _uiState.value = _uiState.value.copy(shorts = updated)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load short details for $videoId: ${e.message}")
            }
        }
    }

    private fun formatLikeText(count: Long): String = when {
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        count > 0 -> count.toString()
        else -> ""
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
