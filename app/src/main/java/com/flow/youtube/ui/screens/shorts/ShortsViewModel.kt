package com.flow.youtube.ui.screens.shorts

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.LikedVideosRepository
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.repository.YouTubeRepository
import com.flow.youtube.data.shorts.ShortsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random

import com.flow.youtube.data.local.PlaylistRepository

class ShortsViewModel(
    private val repository: YouTubeRepository = YouTubeRepository.getInstance()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ShortsUiState())
    val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()
    
    private var likedVideosRepository: LikedVideosRepository? = null
    private var subscriptionRepository: SubscriptionRepository? = null
    private var shortsRepository: ShortsRepository? = null
    private var playlistRepository: PlaylistRepository? = null
    private var isLoadingMore = false
    
    private val _commentsState = MutableStateFlow<List<com.flow.youtube.data.model.Comment>>(emptyList())
    val commentsState: StateFlow<List<com.flow.youtube.data.model.Comment>> = _commentsState.asStateFlow()
    
    private val _isLoadingComments = MutableStateFlow(false)
    val isLoadingComments: StateFlow<Boolean> = _isLoadingComments.asStateFlow()
    
    fun initialize(context: Context) {
        likedVideosRepository = LikedVideosRepository.getInstance(context)
        subscriptionRepository = SubscriptionRepository.getInstance(context)
        shortsRepository = ShortsRepository.getInstance(context)
        playlistRepository = PlaylistRepository(context)
    }
    
    fun loadShorts(startVideoId: String? = null) {
        if (_uiState.value.isLoading) return
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                // Try to get shorts from ShortsRepository first (better variety)
                val repoShorts = shortsRepository?.getHomeFeedShorts() ?: emptyList()
                
                // If ShortsRepository has content, use it; otherwise fallback
                val shorts = if (repoShorts.isNotEmpty()) {
                    // Apply session-based shuffling for variety
                    val sessionSeed = System.currentTimeMillis() / (15 * 60 * 1000L) // 15 min sessions
                    repoShorts.shuffled(Random(sessionSeed))
                } else {
                    // Fallback to basic YouTube search
                    val (basicShorts, nextPage) = repository.getShorts()
                    _uiState.value = _uiState.value.copy(nextPage = nextPage)
                    basicShorts
                }
                
                var finalShorts = shorts
                // Find start index if startVideoId is provided
                var startIndex = if (startVideoId != null) {
                    shorts.indexOfFirst { it.id == startVideoId }
                } else {
                    0
                }

                // If startVideoId is provided but not in the list, fetch it and add it
                if (startVideoId != null && startIndex == -1) {
                    val startVideo = repository.getVideo(startVideoId)
                    if (startVideo != null) {
                        finalShorts = listOf(startVideo) + shorts
                        startIndex = 0
                    } else {
                        startIndex = 0
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    shorts = finalShorts,
                    currentIndex = startIndex.coerceAtLeast(0),
                    isLoading = false,
                    hasMorePages = finalShorts.size >= 8
                )
            } catch (e: Exception) {
                Log.e("ShortsViewModel", "Error loading shorts", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load shorts"
                )
            }
        }
    }
    
    fun loadMoreShorts() {
        if (isLoadingMore || !_uiState.value.hasMorePages) return
        
        isLoadingMore = true
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        
        viewModelScope.launch {
            try {
                val (newShorts, nextPage) = repository.getShorts(_uiState.value.nextPage)
                
                val currentShorts = _uiState.value.shorts
                val updatedShorts = (currentShorts + newShorts).distinctBy { it.id }
                
                _uiState.value = _uiState.value.copy(
                    shorts = updatedShorts,
                    nextPage = nextPage,
                    isLoadingMore = false,
                    hasMorePages = nextPage != null
                )
            } catch (e: Exception) {
                Log.e("ShortsViewModel", "Error loading more shorts", e)
                _uiState.value = _uiState.value.copy(
                    isLoadingMore = false,
                    error = e.message ?: "Failed to load more shorts"
                )
            } finally {
                isLoadingMore = false
            }
        }
    }
    
    suspend fun isVideoLiked(videoId: String): Boolean {
        val repo = likedVideosRepository ?: return false
        var isLiked = false
        repo.getLikeState(videoId).collect { likeState ->
            isLiked = likeState == "LIKED"
        }
        return isLiked
    }
    
    suspend fun toggleLike(video: Video) {
        val repo = likedVideosRepository ?: return
        var isLiked = false
        repo.getLikeState(video.id).collect { likeState ->
            isLiked = likeState == "LIKED"
        }
        
        if (isLiked) {
            repo.removeLikeState(video.id)
        } else {
            repo.likeVideo(
                com.flow.youtube.data.local.LikedVideoInfo(
                    videoId = video.id,
                    title = video.title,
                    thumbnail = video.thumbnailUrl,
                    channelName = video.channelName
                )
            )
        }
    }
    
    suspend fun isChannelSubscribed(channelId: String): Boolean {
        val repo = subscriptionRepository ?: return false
        return repo.isSubscribed(channelId).first()
    }
    
    suspend fun toggleSubscription(channelId: String, channelName: String, channelThumbnail: String) {
        val repo = subscriptionRepository ?: return
        val isSubscribed = repo.isSubscribed(channelId).first()
        
        if (isSubscribed) {
            repo.unsubscribe(channelId)
        } else {
            repo.subscribe(
                com.flow.youtube.data.local.ChannelSubscription(
                    channelId = channelId,
                    channelName = channelName,
                    channelThumbnail = channelThumbnail
                )
            )
        }
    }
    
    fun updateCurrentIndex(index: Int) {
        _uiState.value = _uiState.value.copy(currentIndex = index)
        
        // Load more when reaching near the end (5 items from end)
        if (index >= _uiState.value.shorts.size - 5) {
            loadMoreShorts()
        }
    }

    fun loadSavedShorts(startVideoId: String? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            playlistRepository?.getSavedShortsFlow()?.collect { savedShorts ->
                var finalShorts = savedShorts
                var startIndex = if (startVideoId != null) {
                    savedShorts.indexOfFirst { it.id == startVideoId }
                } else {
                    0
                }

                if (startVideoId != null && startIndex == -1) {
                    val startVideo = repository.getVideo(startVideoId)
                    if (startVideo != null) {
                        finalShorts = listOf(startVideo) + savedShorts
                        startIndex = 0
                    } else {
                        startIndex = 0
                    }
                }
                
                _uiState.value = _uiState.value.copy(
                    shorts = finalShorts,
                    currentIndex = startIndex.coerceAtLeast(0),
                    isLoading = false,
                    hasMorePages = false // Saved shorts are all loaded at once usually
                )
            }
        }
    }

    suspend fun getVideoStreamInfo(videoId: String) = repository.getVideoStreamInfo(videoId)
    
    fun toggleSaveShort(video: Video) {
        viewModelScope.launch {
            val repo = playlistRepository ?: return@launch
            if (repo.isInSavedShorts(video.id)) {
                repo.removeFromSavedShorts(video.id)
            } else {
                repo.addToSavedShorts(video)
            }
        }
    }

    suspend fun isShortSaved(videoId: String): Boolean {
        return playlistRepository?.isInSavedShorts(videoId) ?: false
    }

    fun loadComments(videoId: String) {
        viewModelScope.launch {
            _isLoadingComments.value = true
            _commentsState.value = emptyList()
            try {
                val comments = repository.getComments(videoId)
                _commentsState.value = comments
            } catch (e: Exception) {
                Log.e("ShortsViewModel", "Error loading comments", e)
            } finally {
                _isLoadingComments.value = false
            }
        }
    }
}

data class ShortsUiState(
    val shorts: List<Video> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val nextPage: org.schabi.newpipe.extractor.Page? = null,
    val error: String? = null
)
