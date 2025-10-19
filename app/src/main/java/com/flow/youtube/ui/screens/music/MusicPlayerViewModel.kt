package com.flow.youtube.ui.screens.music

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.flow.youtube.data.music.YouTubeMusicService
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.player.RepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MusicPlayerViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MusicPlayerUiState())
    val uiState: StateFlow<MusicPlayerUiState> = _uiState.asStateFlow()
    
    private var isInitialized = false

    fun initialize(context: Context) {
        if (!isInitialized) {
            EnhancedMusicPlayerManager.initialize(context)
            isInitialized = true
            
            // Observe player state
            viewModelScope.launch {
                EnhancedMusicPlayerManager.playerState.collect { playerState ->
                    _uiState.value = _uiState.value.copy(
                        isPlaying = playerState.isPlaying,
                        isBuffering = playerState.isBuffering,
                        duration = playerState.duration
                    )
                }
            }
            
            // Observe current track
            viewModelScope.launch {
                EnhancedMusicPlayerManager.currentTrack.collect { track ->
                    _uiState.value = _uiState.value.copy(currentTrack = track)
                }
            }
            
            // Observe queue
            viewModelScope.launch {
                EnhancedMusicPlayerManager.queue.collect { queue ->
                    _uiState.value = _uiState.value.copy(queue = queue)
                }
            }
            
            // Observe queue index
            viewModelScope.launch {
                EnhancedMusicPlayerManager.currentQueueIndex.collect { index ->
                    _uiState.value = _uiState.value.copy(currentQueueIndex = index)
                }
            }
            
            // Observe shuffle
            viewModelScope.launch {
                EnhancedMusicPlayerManager.shuffleEnabled.collect { enabled ->
                    _uiState.value = _uiState.value.copy(shuffleEnabled = enabled)
                }
            }
            
            // Observe repeat mode
            viewModelScope.launch {
                EnhancedMusicPlayerManager.repeatMode.collect { mode ->
                    _uiState.value = _uiState.value.copy(repeatMode = mode)
                }
            }
        }
    }

    fun loadAndPlayTrack(track: MusicTrack, queue: List<MusicTrack> = emptyList()) {
        viewModelScope.launch {
            // Set track data immediately so UI displays it
            _uiState.value = _uiState.value.copy(
                currentTrack = track,
                isLoading = true, 
                error = null
            )
            
            try {
                // Get audio URL from YouTube
                val audioUrl = YouTubeMusicService.getAudioUrl(track.videoId)
                
                if (audioUrl != null) {
                    // Play track with EnhancedMusicPlayerManager
                    EnhancedMusicPlayerManager.playTrack(
                        track = track,
                        audioUrl = audioUrl,
                        queue = if (queue.isNotEmpty()) queue else listOf(track)
                    )
                    
                    // Get related music tracks for queue if no queue provided
                    if (queue.isEmpty()) {
                        val relatedTracks = YouTubeMusicService.getRelatedMusic(track.videoId, 20)
                        if (relatedTracks.isNotEmpty()) {
                            EnhancedMusicPlayerManager.addToQueue(relatedTracks)
                        }
                    }
                    
                    _uiState.value = _uiState.value.copy(
                        currentTrack = track,
                        isLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Could not load audio stream"
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load track: ${e.message}"
                )
            }
        }
    }

    fun togglePlayPause() {
        EnhancedMusicPlayerManager.togglePlayPause()
    }

    fun play() {
        EnhancedMusicPlayerManager.play()
    }

    fun pause() {
        EnhancedMusicPlayerManager.pause()
    }

    fun seekTo(position: Long) {
        EnhancedMusicPlayerManager.seekTo(position)
        _uiState.value = _uiState.value.copy(currentPosition = position)
    }

    fun skipToNext() {
        viewModelScope.launch {
            val queue = _uiState.value.queue
            val currentIndex = _uiState.value.currentQueueIndex
            
            if (queue.isNotEmpty() && currentIndex < queue.size - 1) {
                val nextTrack = queue[currentIndex + 1]
                loadAndPlayTrack(nextTrack, queue)
            }
        }
    }

    fun skipToPrevious() {
        viewModelScope.launch {
            val currentPosition = EnhancedMusicPlayerManager.getCurrentPosition()
            val queue = _uiState.value.queue
            val currentIndex = _uiState.value.currentQueueIndex
            
            // If more than 3 seconds into track, restart it
            if (currentPosition > 3000) {
                seekTo(0)
            } else if (queue.isNotEmpty() && currentIndex > 0) {
                val prevTrack = queue[currentIndex - 1]
                loadAndPlayTrack(prevTrack, queue)
            }
        }
    }

    fun playFromQueue(index: Int) {
        viewModelScope.launch {
            val queue = _uiState.value.queue
            if (index in queue.indices) {
                val track = queue[index]
                loadAndPlayTrack(track, queue)
            }
        }
    }

    fun removeFromQueue(index: Int) {
        EnhancedMusicPlayerManager.removeFromQueue(index)
    }

    fun toggleShuffle() {
        EnhancedMusicPlayerManager.toggleShuffle()
    }

    fun toggleRepeat() {
        EnhancedMusicPlayerManager.toggleRepeat()
    }

    fun toggleLike() {
        _uiState.value = _uiState.value.copy(
            isLiked = !_uiState.value.isLiked
        )
        // TODO: Implement like/unlike with repository
    }

    fun updateProgress() {
        val position = EnhancedMusicPlayerManager.getCurrentPosition()
        val duration = EnhancedMusicPlayerManager.getDuration()
        
        _uiState.value = _uiState.value.copy(
            currentPosition = position,
            duration = if (duration > 0) duration else _uiState.value.duration
        )
    }

    override fun onCleared() {
        super.onCleared()
        // Don't release the player, it's managed globally
    }
}

data class MusicPlayerUiState(
    val currentTrack: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPosition: Long = 0,
    val duration: Long = 0,
    val queue: List<MusicTrack> = emptyList(),
    val currentQueueIndex: Int = 0,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val isLiked: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)
