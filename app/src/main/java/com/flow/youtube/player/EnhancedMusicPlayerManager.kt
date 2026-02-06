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
import com.flow.youtube.data.local.QueuePersistence
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

import androidx.media3.common.PlaybackParameters
import kotlin.math.pow
import kotlinx.serialization.json.Json
import android.os.Bundle
import androidx.media3.session.SessionCommand
import com.flow.youtube.data.local.AudioSettingsPersistence
import kotlinx.coroutines.flow.first

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
    
    private var retryCount = 0
    private const val MAX_RETRIES = 3
    
    // Persistence
    private var queuePersistence: QueuePersistence? = null
    private var audioSettingsPersistence: AudioSettingsPersistence? = null
    
    // Audio Settings State
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(0.0f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private val _currentEqProfile = MutableStateFlow("Flat")
    val currentEqProfile: StateFlow<String> = _currentEqProfile.asStateFlow()
    
    private val _bassBoostLevel = MutableStateFlow(0f)
    val bassBoostLevel: StateFlow<Float> = _bassBoostLevel.asStateFlow()

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
        
        queuePersistence = QueuePersistence.getInstance(context)
        audioSettingsPersistence = AudioSettingsPersistence.getInstance(context)
        
        val sessionToken = SessionToken(context, ComponentName(context, Media3MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        
        controllerFuture?.addListener({
            try {
                val controller = controllerFuture?.get()
                player = controller
                if (controller != null) {
                    setupPlayerListener(controller)
                    
                    scope.launch {
                        restoreSavedQueue()
                        restoreAudioSettings()
                    }
                    
                    queuePersistence?.startAutoSave {
                        val currentQ = _queue.value
                        if (currentQ.isNotEmpty()) {
                            QueuePersistence.QueueState(
                                queue = currentQ,
                                currentIndex = _currentQueueIndex.value,
                                currentPosition = _currentPosition.value, // Use StateFlow, not player directly
                                currentTrackId = _currentTrack.value?.videoId,
                                shuffleEnabled = _shuffleEnabled.value,
                                repeatMode = when (_repeatMode.value) {
                                    RepeatMode.OFF -> 0
                                    RepeatMode.ALL -> 1
                                    RepeatMode.ONE -> 2
                                },
                                savedAt = System.currentTimeMillis()
                            )
                        } else null
                    }
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
            
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("EnhancedMusicPlayer", "Player error: ${error.errorCodeName} (${error.errorCode})", error)
                
                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    Log.i("EnhancedMusicPlayer", "Retrying... Attempt $retryCount/$MAX_RETRIES")
                    scope.launch {
                        kotlinx.coroutines.delay(1000)
                        if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED || 
                            error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT) {
                             kotlinx.coroutines.delay(2000) 
                        }
                        player?.prepare()
                        player?.play()
                    }
                } else {
                    Log.w("EnhancedMusicPlayer", "Max retries reached. Skipping to next track.")
                    retryCount = 0
                    playNext()
                }
            }
        })
    }
   
    private fun updatePlayerState() {
        player?.let { p ->
            if (p.playbackState == Player.STATE_READY && p.isPlaying) {
                retryCount = 0
            }
            
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
            val uri = if (t.videoId == track.videoId && audioUrl.isNotEmpty()) {
                Uri.parse(audioUrl)
            } else {
                Uri.parse("music://${t.videoId}")
            }
            
            MediaItem.Builder()
                .setUri(uri)
                .setMediaId(t.videoId)
                .setCustomCacheKey(t.videoId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                    .setTitle(t.title)
                    .setArtist(t.artist)
                    .setArtworkUri(Uri.parse(t.thumbnailUrl))
                    .build()
                )
                .build()
        }
        
        Log.d("EnhancedMusicPlayer", "Playing ${track.title}. Queue size: ${mediaItems.size}. Start index: ${startIndex}")
        
        val startIdx = if (startIndex >= 0) startIndex else activeQueue.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0)
        
        player?.setMediaItems(mediaItems, startIdx, 0)
        player?.prepare()
        player?.play()
    }
    
    fun updateQueue(newQueue: List<MusicTrack>) {
        if (newQueue.isEmpty()) return
        
        _queue.value = newQueue
        triggerQueueSave()
        
        scope.launch {
            val currentMediaId = player?.currentMediaItem?.mediaId
            val currentPosition = player?.currentPosition ?: 0L
            
            val mediaItems = newQueue.map { track ->
                MediaItem.Builder()
                    .setUri(Uri.parse("music://${track.videoId}"))
                    .setMediaId(track.videoId)
                    .setCustomCacheKey(track.videoId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.title)
                            .setArtist(track.artist)
                            .setArtworkUri(Uri.parse(track.thumbnailUrl))
                            .build()
                    )
                    .build()
            }
            
            val newIndex = newQueue.indexOfFirst { it.videoId == currentMediaId }.coerceAtLeast(0)
            
            player?.let { p ->
                 if (p.mediaItemCount != mediaItems.size || p.currentMediaItem?.mediaId != currentMediaId) {
                     p.setMediaItems(mediaItems, newIndex, currentPosition)
                 }
            }
        }
    }

    private fun triggerQueueSave() {
        val currentQ = _queue.value
        if (currentQ.isNotEmpty()) {
            queuePersistence?.saveQueueDebounced(
                queue = currentQ,
                currentIndex = _currentQueueIndex.value,
                currentPosition = _currentPosition.value, // Use StateFlow for thread safety
                currentTrackId = _currentTrack.value?.videoId,
                shuffleEnabled = _shuffleEnabled.value,
                repeatMode = when (_repeatMode.value) {
                    RepeatMode.OFF -> 0
                    RepeatMode.ALL -> 1
                    RepeatMode.ONE -> 2
                }
            )
        }
    }
    
    /**
     * Restore queue from persistent storage
     */
    private suspend fun restoreSavedQueue() {
        try {
            val savedState = queuePersistence?.restoreQueue() ?: return
            
            if (savedState.queue.isEmpty()) return
            
            if ((player?.mediaItemCount ?: 0) > 0) return
            
            Log.d("EnhancedMusicPlayer", "Restoring saved queue: ${savedState.queue.size} tracks")
            
            _queue.value = savedState.queue
            _currentQueueIndex.value = savedState.currentIndex.coerceIn(0, savedState.queue.size - 1)
            _shuffleEnabled.value = savedState.shuffleEnabled
            _repeatMode.value = when (savedState.repeatMode) {
                1 -> RepeatMode.ALL
                2 -> RepeatMode.ONE
                else -> RepeatMode.OFF
            }
            
            val currentTrack = savedState.currentTrackId?.let { id ->
                savedState.queue.find { it.videoId == id }
            } ?: savedState.queue.getOrNull(savedState.currentIndex)
            
            currentTrack?.let { _currentTrack.value = it }
            
        } catch (e: Exception) {
            Log.e("EnhancedMusicPlayer", "Failed to restore queue", e)
        }
    }
    
    /**
     * Force save current queue immediately.
     * Uses cached position from StateFlow for thread safety.
     */
    fun saveQueueNow() {
        scope.launch(Dispatchers.IO) {
            val currentQ = _queue.value
            if (currentQ.isNotEmpty()) {
                queuePersistence?.saveQueueImmediate(
                    queue = currentQ,
                    currentIndex = _currentQueueIndex.value,
                    currentPosition = _currentPosition.value, // Use StateFlow for thread safety
                    currentTrackId = _currentTrack.value?.videoId,
                    shuffleEnabled = _shuffleEnabled.value,
                    repeatMode = when (_repeatMode.value) {
                        RepeatMode.OFF -> 0
                        RepeatMode.ALL -> 1
                        RepeatMode.ONE -> 2
                    }
                )
            }
        }
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
        triggerQueueSave()
    }

    fun addToQueue(track: MusicTrack) {
        val currentQ = _queue.value.toMutableList()
        currentQ.add(track)
        _queue.value = currentQ
        triggerQueueSave()
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
        emitToggleLikeEvent()
    }

    fun emitToggleLikeEvent() {
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

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        scope.launch { audioSettingsPersistence?.saveSpeed(speed) }
        
        player?.let { p ->
            val currentPitch = p.playbackParameters.pitch
            p.playbackParameters = PlaybackParameters(speed, currentPitch)
        }
    }

    fun setPlaybackPitch(semitones: Float) {
        _playbackPitch.value = semitones
        scope.launch { audioSettingsPersistence?.savePitch(semitones) }
        
        player?.let { p ->
            val pitch = 2.0.pow(semitones.toDouble() / 12.0).toFloat()
            val currentSpeed = p.playbackParameters.speed
            p.playbackParameters = PlaybackParameters(currentSpeed, pitch)
        }
    }

    fun setBassBoost(strength: Float) {
        _bassBoostLevel.value = strength
        scope.launch { audioSettingsPersistence?.saveBassBoost(strength) }
        
        val currentProfileName = _currentEqProfile.value
        setEqProfile(currentProfileName)
    }

    fun setEqProfile(profileName: String) {
        _currentEqProfile.value = profileName
        scope.launch { audioSettingsPersistence?.saveEqProfile(profileName) }
        
        val baseProfile = com.flow.youtube.data.model.EqPresets.presets[profileName] 
            ?: com.flow.youtube.data.model.EqPresets.presets["Flat"]
            ?: com.flow.youtube.data.model.ParametricEQ.createFlat()
            
        val boost = _bassBoostLevel.value
        val finalProfile = if (boost > 0) {
            val existingBandIndex = baseProfile.bands.indexOfFirst { 
                it.filterType == com.flow.youtube.data.model.FilterType.LSC && 
                it.frequency in 40.0..80.0 
            }
            
            val newBands = if (existingBandIndex >= 0) {
                val mutableBands = baseProfile.bands.toMutableList()
                val existing = mutableBands[existingBandIndex]
                mutableBands[existingBandIndex] = existing.copy(gain = existing.gain + boost.toDouble())
                mutableBands.toList()
            } else {
                 val boostBand = com.flow.youtube.data.model.ParametricEQBand(
                    60.0, 
                    boost.toDouble(), 
                    0.7, 
                    com.flow.youtube.data.model.FilterType.LSC
                )
                listOf(boostBand) + baseProfile.bands
            }
            baseProfile.copy(bands = newBands)
        } else {
            baseProfile
        }
        
        applyEqProfile(finalProfile)
    }

    fun applyEqProfile(profile: com.flow.youtube.data.model.ParametricEQ) {
        try {
            val json = Json.encodeToString(com.flow.youtube.data.model.ParametricEQ.serializer(), profile)
            val bundle = Bundle().apply {
                putString("EQ_PROFILE", json)
            }
            val command = SessionCommand(Media3MusicService.ACTION_SET_EQ, Bundle.EMPTY)
            
            if (controllerFuture?.isDone == true) {
                 controllerFuture?.get()?.sendCustomCommand(command, bundle)
            }
        } catch (e: Exception) {
             Log.e("EnhancedMusicPlayer", "Error sending EQ profile", e)
        }
    }
    
    private suspend fun restoreAudioSettings() {
        try {
            val settings = audioSettingsPersistence?.settingsFlow?.first() ?: return
            
            Log.d("EnhancedMusicPlayer", "Restoring audio settings: $settings")
            
            _playbackSpeed.value = settings.speed
            _playbackPitch.value = settings.pitch
            _currentEqProfile.value = settings.eqProfile
            _bassBoostLevel.value = settings.bassBoost
            
            player?.let { p ->
                val pitch = 2.0.pow(settings.pitch.toDouble() / 12.0).toFloat()
                p.playbackParameters = PlaybackParameters(settings.speed, pitch)
            }
            
            setEqProfile(settings.eqProfile)
            
        } catch (e: Exception) {
            Log.e("EnhancedMusicPlayer", "Failed to restore audio settings", e)
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
             triggerQueueSave()
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
