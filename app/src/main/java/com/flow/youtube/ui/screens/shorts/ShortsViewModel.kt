package com.flow.youtube.ui.screens.shorts

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.local.LikedVideosRepository
import com.flow.youtube.data.local.SubscriptionRepository
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.repository.YouTubeRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ShortsViewModel(
    private val repository: YouTubeRepository = YouTubeRepository.getInstance()
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(ShortsUiState())
    val uiState: StateFlow<ShortsUiState> = _uiState.asStateFlow()
    
    private var likedVideosRepository: LikedVideosRepository? = null
    private var subscriptionRepository: SubscriptionRepository? = null
    private var isLoadingMore = false
    
    fun initialize(context: Context) {
        likedVideosRepository = LikedVideosRepository.getInstance(context)
        subscriptionRepository = SubscriptionRepository.getInstance(context)
    }
    
    fun loadShorts() {
        if (_uiState.value.isLoading) return
        
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        
        viewModelScope.launch {
            try {
                // Fetch trending videos as shorts (filter by duration < 60 seconds)
                val (allVideos, _) = repository.getTrendingVideos()
                
                // Filter for short-form content (under 60 seconds)
                val shorts = allVideos.filter { it.duration in 1..60 }
                
                _uiState.value = _uiState.value.copy(
                    shorts = shorts,
                    isLoading = false,
                    hasMorePages = true
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
                // In a real implementation, you would pass the nextPage token
                val (allVideos, _) = repository.getTrendingVideos()
                val newShorts = allVideos.filter { it.duration in 1..60 }
                
                val currentShorts = _uiState.value.shorts
                val updatedShorts = (currentShorts + newShorts).distinctBy { it.id }
                
                _uiState.value = _uiState.value.copy(
                    shorts = updatedShorts,
                    isLoadingMore = false,
                    hasMorePages = true
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
}

data class ShortsUiState(
    val shorts: List<Video> = emptyList(),
    val currentIndex: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = true,
    val error: String? = null
)
