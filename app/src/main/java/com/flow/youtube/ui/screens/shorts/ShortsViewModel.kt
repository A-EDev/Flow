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
import com.flow.youtube.utils.PerformanceDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
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
    
    /**
     *  PERFORMANCE OPTIMIZED: Load shorts with parallel fetching
     * Uses SupervisorScope for error isolation and timeout protection
     */
    fun loadShorts(startVideoId: String? = null) {
        if (_uiState.value.isLoading) return
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                //  PARALLEL FETCH: Try multiple sources simultaneously
                val shorts = supervisorScope {
                    val repoShortsDeferred = async(PerformanceDispatcher.networkIO) {
                        withTimeoutOrNull(12_000L) {
                            shortsRepository?.getHomeFeedShorts() ?: emptyList()
                        } ?: emptyList()
                    }
                    
                    val fallbackShortsDeferred = async(PerformanceDispatcher.networkIO) {
                        withTimeoutOrNull(10_000L) {
                            repository.getShorts().first
                        } ?: emptyList()
                    }
                    
                    val repoShorts = repoShortsDeferred.await()
                    
                    // If ShortsRepository has content, use it; otherwise fallback
                    if (repoShorts.isNotEmpty()) {
                        // Apply session-based shuffling for variety
                        val sessionSeed = System.currentTimeMillis() / (15 * 60 * 1000L) // 15 min sessions
                        repoShorts.shuffled(Random(sessionSeed))
                    } else {
                        // Use fallback result
                        val basicShorts = fallbackShortsDeferred.await()
                        val (_, nextPage) = repository.getShorts()
                        _uiState.value = _uiState.value.copy(nextPage = nextPage)
                        basicShorts
                    }
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
                    val startVideo = withTimeoutOrNull(5_000L) { 
                        repository.getVideo(startVideoId) 
                    }
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
    
    /**
     *  PERFORMANCE OPTIMIZED: Load more shorts with timeout
     */
    fun loadMoreShorts() {
        if (isLoadingMore || !_uiState.value.hasMorePages) return
        
        isLoadingMore = true
        _uiState.value = _uiState.value.copy(isLoadingMore = true)
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val result = withTimeoutOrNull(15_000L) {
                    repository.getShorts(_uiState.value.nextPage)
                }
                
                if (result != null) {
                    val (newShorts, nextPage) = result
                    val currentShorts = _uiState.value.shorts
                    val updatedShorts = (currentShorts + newShorts).distinctBy { it.id }
                    
                    _uiState.value = _uiState.value.copy(
                        shorts = updatedShorts,
                        nextPage = nextPage,
                        isLoadingMore = false,
                        hasMorePages = nextPage != null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoadingMore = false,
                        hasMorePages = false
                    )
                }
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
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
            _uiState.value = _uiState.value.copy(isLoading = true)
            playlistRepository?.getSavedShortsFlow()?.collect { savedShorts ->
                var finalShorts = savedShorts
                var startIndex = if (startVideoId != null) {
                    savedShorts.indexOfFirst { it.id == startVideoId }
                } else {
                    0
                }

                if (startVideoId != null && startIndex == -1) {
                    val startVideo = withTimeoutOrNull(5_000L) {
                        repository.getVideo(startVideoId)
                    }
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
        viewModelScope.launch(PerformanceDispatcher.diskIO) {
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

    /**
     *  PERFORMANCE OPTIMIZED: Load comments with timeout
     */
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
                Log.e("ShortsViewModel", "Error loading comments", e)
            } finally {
                _isLoadingComments.value = false
            }
        }
    }

    fun loadCommentReplies(comment: com.flow.youtube.data.model.Comment) {
        val currentVideo = _uiState.value.shorts.getOrNull(_uiState.value.currentIndex) ?: return
        val repliesPage = comment.repliesPage ?: return
        
        viewModelScope.launch(PerformanceDispatcher.networkIO) {
            try {
                val url = "https://www.youtube.com/watch?v=${currentVideo.id}"
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
                Log.e("ShortsViewModel", "Error loading replies", e)
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

