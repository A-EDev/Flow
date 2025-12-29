package com.flow.youtube.player

import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.flow.youtube.service.MusicPlaybackService
import com.flow.youtube.ui.screens.music.MusicTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Singleton manager for enhanced music playback across the app
 * Manages ExoPlayer instance, queue, shuffle, repeat modes
 */
object EnhancedMusicPlayerManager {
    private var player: ExoPlayer? = null
    private var isInitialized = false
    private var appContext: Context? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Player state
    private val _playerState = MutableStateFlow(MusicPlayerState())
    val playerState: StateFlow<MusicPlayerState> = _playerState.asStateFlow()

    // Events
    sealed class PlayerEvent {
        data class RequestPlayTrack(val track: MusicTrack) : PlayerEvent()
    }
    
    private val _playerEvents = MutableSharedFlow<PlayerEvent>()
    val playerEvents: SharedFlow<PlayerEvent> = _playerEvents.asSharedFlow()
    
    // Queue management
    private val _queue = MutableStateFlow<List<MusicTrack>>(emptyList())
    val queue: StateFlow<List<MusicTrack>> = _queue.asStateFlow()
    
    private val _originalQueue = mutableListOf<MusicTrack>()
    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()
    
    // Playback modes
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()
    
    // Current track
    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()
    
    /**
     * Initialize the player
     */
    fun initialize(context: Context) {
        if (!isInitialized) {
            appContext = context.applicationContext
            player = ExoPlayer.Builder(appContext!!).build()
            setupPlayerListener()
            isInitialized = true
        }
    }
    
    /**
     * Start foreground service for notifications
     */
    private fun startMusicService() {
        appContext?.let { context ->
            val intent = Intent(context, MusicPlaybackService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
    
    /**
     * Setup player event listener
     */
    private fun setupPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _playerState.value = _playerState.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isReady = playbackState == Player.STATE_READY
                )
                
                // Handle song end
                if (playbackState == Player.STATE_ENDED) {
                    handleTrackEnd()
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            }
            
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _playerState.value = _playerState.value.copy(playWhenReady = playWhenReady)
            }
        })
    }
    
    /**
     * Play a track with optional queue
     */
    fun playTrack(track: MusicTrack, audioUrl: String, queue: List<MusicTrack> = emptyList(), startIndex: Int = -1) {
        _currentTrack.value = track
        
        if (queue.isNotEmpty()) {
            val isSameQueue = _originalQueue.size == queue.size && 
                             _originalQueue.zip(queue).all { it.first.videoId == it.second.videoId }
            
            if (!isSameQueue) {
                _originalQueue.clear()
                _originalQueue.addAll(queue)
                _queue.value = if (_shuffleEnabled.value) {
                    queue.shuffled()
                } else {
                    queue
                }
            }
            
            // Update current index
            if (startIndex >= 0) {
                _currentQueueIndex.value = startIndex
            } else {
                val indexInQueue = _queue.value.indexOfFirst { it.videoId == track.videoId }
                if (indexInQueue >= 0) {
                    _currentQueueIndex.value = indexInQueue
                }
            }
        }
        
        // Prepare media source
        player?.let { exoPlayer ->
            val dataSourceFactory = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0")
                .setAllowCrossProtocolRedirects(true)
            
            val mediaItem = MediaItem.fromUri(audioUrl)
            val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem)
            
            exoPlayer.setMediaSource(mediaSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            
            _playerState.value = _playerState.value.copy(
                isPlaying = true,
                duration = track.duration.toLong() * 1000
            )
        }
        
        // Start notification service
        startMusicService()
    }
    
    /**
     * Add tracks to queue
     */
    fun addToQueue(tracks: List<MusicTrack>) {
        val currentQueue = _queue.value.toMutableList()
        currentQueue.addAll(tracks)
        _queue.value = currentQueue
        _originalQueue.addAll(tracks)
    }
    
    /**
     * Remove track from queue
     */
    fun removeFromQueue(index: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            _queue.value = currentQueue
        }
    }
    
    /**
     * Play next track in queue
     */
    fun playNext() {
        val queue = _queue.value
        val currentIndex = _currentQueueIndex.value
        
        if (queue.isNotEmpty() && currentIndex < queue.size - 1) {
            val nextIndex = currentIndex + 1
            val nextTrack = queue[nextIndex]
            _currentQueueIndex.value = nextIndex
            _currentTrack.value = nextTrack
            
            scope.launch {
                _playerEvents.emit(PlayerEvent.RequestPlayTrack(nextTrack))
            }
        }
    }
    
    /**
     * Play previous track
     */
    fun playPrevious() {
        val currentPosition = player?.currentPosition ?: 0
        val queue = _queue.value
        val currentIndex = _currentQueueIndex.value
        
        // If more than 3 seconds into track or it's the first track, restart it
        if (currentPosition > 3000 || currentIndex == 0) {
            player?.seekTo(0)
        } else if (queue.isNotEmpty() && currentIndex > 0) {
            val prevIndex = currentIndex - 1
            val prevTrack = queue[prevIndex]
            _currentQueueIndex.value = prevIndex
            _currentTrack.value = prevTrack
            
            scope.launch {
                _playerEvents.emit(PlayerEvent.RequestPlayTrack(prevTrack))
            }
        }
    }
    
    /**
     * Play track from queue at index
     */
    fun playFromQueue(index: Int) {
        val queue = _queue.value
        if (index in queue.indices) {
            val track = queue[index]
            _currentQueueIndex.value = index
            _currentTrack.value = track
            
            scope.launch {
                _playerEvents.emit(PlayerEvent.RequestPlayTrack(track))
            }
        }
    }
    
    /**
     * Toggle shuffle mode
     */
    fun toggleShuffle() {
        _shuffleEnabled.value = !_shuffleEnabled.value
        
        if (_shuffleEnabled.value) {
            // Shuffle queue
            val currentTrack = _currentTrack.value
            val shuffled = _originalQueue.shuffled().toMutableList()
            
            // Keep current track at current position
            currentTrack?.let { track ->
                shuffled.remove(track)
                shuffled.add(_currentQueueIndex.value, track)
            }
            
            _queue.value = shuffled
        } else {
            // Restore original order
            _queue.value = _originalQueue.toList()
            
            // Update current index
            _currentTrack.value?.let { track ->
                val newIndex = _originalQueue.indexOf(track)
                if (newIndex != -1) {
                    _currentQueueIndex.value = newIndex
                }
            }
        }
    }
    
    /**
     * Cycle through repeat modes: OFF -> ALL -> ONE -> OFF
     */
    fun toggleRepeat() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
    }
    
    /**
     * Handle track end based on repeat mode
     */
    private fun handleTrackEnd() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                // Repeat current track
                player?.seekTo(0)
                player?.play()
            }
            RepeatMode.ALL -> {
                // Play next, or loop to start
                val queue = _queue.value
                val currentIndex = _currentQueueIndex.value
                
                if (currentIndex < queue.size - 1) {
                    playNext()
                } else if (queue.isNotEmpty()) {
                    // Loop back to first track
                    val firstTrack = queue.first()
                    _currentQueueIndex.value = 0
                    _currentTrack.value = firstTrack
                    scope.launch {
                        _playerEvents.emit(PlayerEvent.RequestPlayTrack(firstTrack))
                    }
                }
            }
            RepeatMode.OFF -> {
                // Play next if available, otherwise stop
                val queue = _queue.value
                val currentIndex = _currentQueueIndex.value
                
                if (currentIndex < queue.size - 1) {
                    playNext()
                } else {
                    _playerState.value = _playerState.value.copy(isPlaying = false)
                }
            }
        }
    }
    
    /**
     * Play/pause toggle
     */
    fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) {
                it.pause()
            } else {
                it.play()
            }
        }
    }
    
    fun play() {
        player?.play()
    }
    
    fun pause() {
        player?.pause()
    }
    
    fun stop() {
        player?.stop()
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }
    
    /**
     * Seek to position in milliseconds
     */
    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }
    
    /**
     * Get current position in milliseconds
     */
    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0
    }
    
    /**
     * Get duration in milliseconds
     */
    fun getDuration(): Long {
        return player?.duration ?: 0
    }
    
    /**
     * Check if player is playing
     */
    fun isPlaying(): Boolean {
        return player?.isPlaying ?: false
    }
    
    /**
     * Clear current track (for dismiss functionality)
     */
    fun clearCurrentTrack() {
        _currentTrack.value = null
        _playerState.value = _playerState.value.copy(isPlaying = false)
    }
}

/**
 * Music player state
 */
data class MusicPlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isReady: Boolean = false,
    val playWhenReady: Boolean = false,
    val duration: Long = 0,
    val position: Long = 0
)

/**
 * Repeat modes
 */
enum class RepeatMode {
    OFF,    // No repeat
    ALL,    // Repeat all tracks in queue
    ONE     // Repeat current track
}
