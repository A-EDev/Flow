package com.flow.youtube.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.flow.youtube.service.Media3MusicService
import com.flow.youtube.ui.screens.music.MusicTrack
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
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
import org.schabi.newpipe.extractor.stream.AudioStream
import java.util.concurrent.ExecutionException
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler

@OptIn(UnstableApi::class)
object EnhancedMusicPlayerManager {
    
    var player: Player? = null
        private set
        
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var isInitialized = false
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("EnhancedMusicPlayer", "Error in player scope: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)
    
    // Player state flows
    private val _playerState = MutableStateFlow(MusicPlayerState())
    val playerState: StateFlow<MusicPlayerState> = _playerState.asStateFlow()
    
    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    // Events
    sealed class PlayerEvent {
        data class RequestPlayTrack(val track: MusicTrack) : PlayerEvent()
        object RequestToggleLike : PlayerEvent()
    }
    
    private val _playerEvents = MutableSharedFlow<PlayerEvent>()
    val playerEvents: SharedFlow<PlayerEvent> = _playerEvents.asSharedFlow()
    
    // Queue
    private val _queue = MutableStateFlow<List<MusicTrack>>(emptyList())
    val queue: StateFlow<List<MusicTrack>> = _queue.asStateFlow()
    
    private val _currentQueueIndex = MutableStateFlow(0)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()
    
    private val _currentTrack = MutableStateFlow<MusicTrack?>(null)
    val currentTrack: StateFlow<MusicTrack?> = _currentTrack.asStateFlow()
    
    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()
    
    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()
    
    private val _playingFrom = MutableStateFlow("Flow Music")
    val playingFrom: StateFlow<String> = _playingFrom.asStateFlow()
    
    private val _isLiked = MutableStateFlow(false)
    val isLiked: StateFlow<Boolean> = _isLiked.asStateFlow()

    fun initialize(context: Context) {
        if (isInitialized) return
        isInitialized = true
        
        val sessionToken = SessionToken(context, ComponentName(context, Media3MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                player = controller
                if (controller != null) {
                    setupPlayerListener(controller)
                }
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
        
        startPositionUpdates()
    }
    
    private fun setupPlayerListener(controller: Player) {
        controller.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                updatePlayerState()
                if (playbackState == Player.STATE_ENDED) {
                }
            }
            
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updatePlayerState()
                if (isPlaying) startPositionUpdates()
            }
            
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (mediaItem == null) return

                mediaItem.let { item ->
                    val trackId = item.mediaId
                    val currentQ = _queue.value
                    val track = currentQ.find { it.videoId == trackId }
                    if (track != null) {
                        _currentTrack.value = track
                        _currentQueueIndex.value = currentQ.indexOf(track)
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _shuffleEnabled.value = shuffleModeEnabled
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                 _repeatMode.value = when(repeatMode) {
                     Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                     Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                     else -> RepeatMode.OFF
                 }
            }
        })
    }
    
    private fun updatePlayerState() {
        player?.let { p ->
            _playerState.value = _playerState.value.copy(
                isPlaying = p.isPlaying,
                isBuffering = p.playbackState == Player.STATE_BUFFERING,
                duration = if (p.duration > 0) p.duration else _playerState.value.duration,
                position = p.currentPosition
            )
        }
    }
    
    private fun startPositionUpdates() {
        scope.launch {
            while (true) {
                player?.let { p ->
                    if (p.isPlaying) {
                        _currentPosition.value = p.currentPosition
                        _playerState.value = _playerState.value.copy(position = p.currentPosition)
                    }
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    // --- Playback Control Methods ---

    fun setPendingTrack(track: MusicTrack, sourceName: String? = null) {

        player?.stop()
        player?.clearMediaItems()
        
        _currentTrack.value = track
        sourceName?.let { _playingFrom.value = it }
        _playerState.value = _playerState.value.copy(
            isPlaying = false, 
            isBuffering = false,
            isPreparing = true,  
            position = 0
        )
    }
    
    fun setCurrentTrack(track: MusicTrack, sourceName: String?) {
         _currentTrack.value = track
         sourceName?.let { _playingFrom.value = it }
    }

    fun playTrack(track: MusicTrack, audioStream: AudioStream, durationSeconds: Long, queue: List<MusicTrack> = emptyList(), startIndex: Int = -1) {
        playTrack(track, audioStream.content, queue, startIndex)
    }

    fun playTrack(track: MusicTrack, audioUrl: String, queue: List<MusicTrack> = emptyList(), startIndex: Int = -1) {
        player?.stop()
        player?.clearMediaItems()

        _playerState.value = _playerState.value.copy(isPreparing = false)
        
        val activeQueue = if (queue.isNotEmpty()) queue else listOf(track)
        _queue.value = activeQueue
        _currentTrack.value = track
        
        val mediaItems = activeQueue.map { t ->
            val uri = if (t.videoId == track.videoId) Uri.parse(audioUrl) else Uri.EMPTY 
            
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(t.videoId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setArtworkUri(Uri.parse(t.thumbnailUrl))
                    .build()
                )
                .build()
        }
        
        val startIdx = if (startIndex >= 0) startIndex else activeQueue.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0)
        
        player?.setMediaItems(mediaItems, startIdx, 0)
        player?.prepare()
        player?.play()
    }
    
    fun updateQueue(newQueue: List<MusicTrack>) {
        _queue.value = newQueue
    }

    fun togglePlayPause() {
        scope.launch {
            player?.let {
                if (it.isPlaying) it.pause() else it.play()
            }
        }
    }
    
    fun playNext(track: MusicTrack) {
        val currentQ = _queue.value.toMutableList()
        val currentId = _currentTrack.value?.videoId
        val idx = currentQ.indexOfFirst { it.videoId == currentId }
        
        if (idx != -1) {
            currentQ.add(idx + 1, track)
        } else {
            currentQ.add(track)
        }
        _queue.value = currentQ
    }

    fun addToQueue(track: MusicTrack) {
        val currentQ = _queue.value.toMutableList()
        currentQ.add(track)
        _queue.value = currentQ
    }
    
    fun playNext() {
        val queue = _queue.value
        val currentId = _currentTrack.value?.videoId
        val idx = queue.indexOfFirst { it.videoId == currentId }
        
        if (idx != -1 && idx < queue.size - 1) {
             val nextTrack = queue[idx + 1]
             setPendingTrack(nextTrack) 
             scope.launch { _playerEvents.emit(PlayerEvent.RequestPlayTrack(nextTrack)) }
        }
    }
    
    fun playPrevious() {
        scope.launch {
            val queue = _queue.value
            val currentId = _currentTrack.value?.videoId
            val idx = queue.indexOfFirst { it.videoId == currentId }
            
             if ((player?.currentPosition ?: 0) > 3000) {
                 player?.seekTo(0)
                 return@launch
             }

            if (idx > 0) {
                 val prevTrack = queue[idx - 1]
                 setPendingTrack(prevTrack)
                 _playerEvents.emit(PlayerEvent.RequestPlayTrack(prevTrack))
            }
        }
    }
    
    fun playFromQueue(index: Int) {
        val queue = _queue.value
        if (index in queue.indices) {
            val track = queue[index]
            setPendingTrack(track)
            scope.launch { _playerEvents.emit(PlayerEvent.RequestPlayTrack(track)) }
        }
    }

    fun toggleShuffle() {
        scope.launch {
            player?.let {
                it.shuffleModeEnabled = !it.shuffleModeEnabled
            }
        }
    }
    
    fun toggleRepeat() {
        scope.launch {
            player?.let {
                 val newMode = when(it.repeatMode) {
                     Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                     Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                     else -> Player.REPEAT_MODE_OFF
                 }
                 it.repeatMode = newMode
            }
        }
    }
    
    fun seekTo(position: Long) {
        scope.launch {
            player?.seekTo(position)
        }
    }

    fun switchMode(url: String) {
        scope.launch {
            player?.let { p ->
                 val currentPos = p.currentPosition
                 val wasPlaying = p.isPlaying
                 
                 val currentItem = p.currentMediaItem ?: return@let
                 val newItem = currentItem.buildUpon()
                     .setUri(Uri.parse(url))
                     .build()
                 
                 val currentIndex = p.currentMediaItemIndex
                 if (currentIndex >= 0 && currentIndex < p.mediaItemCount) {
                      p.replaceMediaItem(currentIndex, newItem)
                      p.seekTo(currentPos)
                      if (wasPlaying) p.play()
                 }
            }
        }
    }
    
    fun getCurrentPosition(): Long = _currentPosition.value // Use state flow value
    fun getDuration(): Long = _playerState.value.duration
    
    fun toggleLike() {
        _isLiked.value = !_isLiked.value
        scope.launch { _playerEvents.emit(PlayerEvent.RequestToggleLike) }
    }
    fun setLiked(liked: Boolean) { _isLiked.value = liked }
    
    fun play() { scope.launch { player?.play() } }
    fun pause() { scope.launch { player?.pause() } }
    
    fun stop() {
        scope.launch {
            player?.stop()
            _playerState.value = _playerState.value.copy(isPlaying = false)
        }
    }

    fun isPlaying(): Boolean = _playerState.value.isPlaying

    fun clearCurrentTrack() {
        scope.launch {
            player?.stop()
            player?.clearMediaItems()
            _currentTrack.value = null
        }
    }
    
    fun removeFromQueue(index: Int) {
         val currentQ = _queue.value.toMutableList()
         if (index in currentQ.indices) {
             currentQ.removeAt(index)
             _queue.value = currentQ
         }
    }
}

data class MusicPlayerState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isPreparing: Boolean = false,  
    val isReady: Boolean = false,
    val playWhenReady: Boolean = false,
    val duration: Long = 0,
    val position: Long = 0
)

enum class RepeatMode {
    OFF,    
    ALL,    
    ONE     
}
