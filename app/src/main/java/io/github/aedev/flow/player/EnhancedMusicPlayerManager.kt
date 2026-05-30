package io.github.aedev.flow.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import io.github.aedev.flow.data.local.QueuePersistence
import io.github.aedev.flow.service.Media3MusicService
import io.github.aedev.flow.ui.screens.music.MusicTrack
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
import io.github.aedev.flow.data.local.AudioSettingsPersistence
import kotlinx.coroutines.flow.first

@OptIn(UnstableApi::class)
object EnhancedMusicPlayerManager {
    
    var player: Player? = null
        private set
    private var appContext: Context? = null
        
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var isInitialized = false
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Log.e("EnhancedMusicPlayer", "Error in player scope: ${throwable.message}", throwable)
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob() + exceptionHandler)
    
    private var retryCount = 0
    private const val MAX_RETRIES = 3
    private var positionUpdateJob: kotlinx.coroutines.Job? = null
    
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

    private val _playbackWarnings = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val playbackWarnings: SharedFlow<String> = _playbackWarnings.asSharedFlow()
    
    // Queue
    private val _queue = MutableStateFlow<List<MusicTrack>>(emptyList())
    val queue: StateFlow<List<MusicTrack>> = _queue.asStateFlow()
    
    private val _automixItems = MutableStateFlow<List<MusicTrack>>(emptyList())
    val automixItems: StateFlow<List<MusicTrack>> = _automixItems.asStateFlow()
    
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

    // OPTIMIZED: LRU Cache for resolved stream URLs to avoid re-fetching
    private val urlCache = android.util.LruCache<String, String>(50)
    private var pendingPlayNextMediaId: String? = null
    private var pendingPlayNextMediaIndex: Int = MusicQueuePlanner.INDEX_UNSET

    // OPTIMIZED: Pre-fetch next track to reduce gap
    private fun prefetchNextTrack() {
        val queue = _queue.value
        val idx = currentPlaybackQueueIndex()
        
        if (idx != -1 && idx < queue.size - 1) {
            val nextTrack = queue[idx + 1]
            if (urlCache.get(nextTrack.videoId) == null) {
                scope.launch(Dispatchers.IO) {
                    try {
                        resolveStreamUrl(nextTrack.videoId)
                        Log.d("EnhancedMusicPlayer", "Pre-fetched URL for next track: ${nextTrack.title}")
                    } catch (e: Exception) {
                        Log.e("EnhancedMusicPlayer", "Failed to pre-fetch next track", e)
                    }
                }
            }
        }
    }

    /**
     * Resolves stream URL with caching
     */
    suspend fun resolveStreamUrl(videoId: String): String? {
        // 1. Check cache
        urlCache.get(videoId)?.let { 
             Log.d("EnhancedMusicPlayer", "Cache hit for $videoId")
             return it 
        }
        
        // 2. Resolve (using MusicPlayerUtils)
        return try {
             val playbackData = io.github.aedev.flow.utils.MusicPlayerUtils.playerResponseForPlayback(videoId).getOrNull()
             val url = playbackData?.streamUrl
             
             if (url != null) {
                 urlCache.put(videoId, url)
             }
             url
        } catch (e: Exception) {
             Log.e("EnhancedMusicPlayer", "Error resolving URL for $videoId", e)
             null
        }
    }

    fun invalidateResolvedStream(videoId: String) {
        urlCache.remove(videoId)
        Log.d("EnhancedMusicPlayer", "Invalidated resolved URL cache for $videoId")
    }

    fun clearUrlCache() {
        urlCache.evictAll()
        Log.d("EnhancedMusicPlayer", "Cleared all resolved URL cache entries")
    }

    fun initialize(context: Context) {
        if (isInitialized) return
        appContext = context.applicationContext
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
                                savedAt = System.currentTimeMillis(),
                                automix = _automixItems.value
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

                val isAutomaticTransition = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
                if (enforcePendingPlayNext(controller, mediaItem, isAutomaticTransition)) {
                    return
                }

                syncCurrentTrackFromMediaItem(controller, mediaItem)
                if (
                    isAutomaticTransition ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    _currentPosition.value = 0L
                    _playerState.value = _playerState.value.copy(position = 0L)
                }
                prefetchNextTrack()
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
                showPlaybackWarning(
                    appContext?.getString(io.github.aedev.flow.R.string.music_playback_warning_generic)
                        ?: "Music playback failed. Try again or switch networks."
                )
                retryCount = 0
            }
        })
    }

    private fun syncCurrentTrackFromMediaItem(controller: Player, mediaItem: MediaItem) {
        val trackId = mediaItem.mediaId
        val currentQ = _queue.value
        val queueIndex = MusicQueuePlanner.currentQueueIndex(
            queueIds = currentQ.map { it.videoId },
            playerIndex = controller.currentMediaItemIndex,
            currentTrackId = trackId
        )
        val track = currentQ.getOrNull(queueIndex) ?: currentQ.find { it.videoId == trackId }
        if (track != null) {
            val resolvedIndex = if (queueIndex != MusicQueuePlanner.INDEX_UNSET) {
                queueIndex
            } else {
                currentQ.indexOf(track)
            }
            _currentTrack.value = track
            _currentQueueIndex.value = resolvedIndex.coerceAtLeast(0)
        }
        if (
            pendingPlayNextMediaId == trackId &&
            (pendingPlayNextMediaIndex == MusicQueuePlanner.INDEX_UNSET ||
                pendingPlayNextMediaIndex == controller.currentMediaItemIndex)
        ) {
            clearPendingPlayNext()
        }
    }

    private fun enforcePendingPlayNext(
        controller: Player,
        mediaItem: MediaItem,
        isAutomaticTransition: Boolean
    ): Boolean {
        val expectedMediaId = pendingPlayNextMediaId
        val actualMediaId = mediaItem.mediaId
        if (!MusicQueuePlanner.shouldForcePendingPlayNext(
                isAutomaticTransition = isAutomaticTransition,
                pendingMediaId = expectedMediaId,
                pendingPlayerIndex = pendingPlayNextMediaIndex,
                actualMediaId = actualMediaId,
                actualPlayerIndex = controller.currentMediaItemIndex
            )
        ) {
            return false
        }

        val targetIndex = findPlayerMediaItemIndex(
            controller = controller,
            mediaId = expectedMediaId ?: return false,
            preferredIndex = pendingPlayNextMediaIndex
        )
        if (targetIndex == MusicQueuePlanner.INDEX_UNSET) {
            Log.w("EnhancedMusicPlayer", "Pending play-next item $expectedMediaId is missing from player queue")
            clearPendingPlayNext()
            return false
        }

        Log.w(
            "EnhancedMusicPlayer",
            "Auto transition landed on $actualMediaId; forcing queued play-next item $expectedMediaId"
        )
        controller.seekTo(targetIndex, 0L)
        controller.play()
        return true
    }

    private fun findPlayerMediaItemIndex(
        controller: Player,
        mediaId: String,
        preferredIndex: Int = MusicQueuePlanner.INDEX_UNSET
    ): Int {
        if (
            preferredIndex in 0 until controller.mediaItemCount &&
            controller.getMediaItemAt(preferredIndex).mediaId == mediaId
        ) {
            return preferredIndex
        }

        for (index in 0 until controller.mediaItemCount) {
            if (controller.getMediaItemAt(index).mediaId == mediaId) {
                return index
            }
        }
        return MusicQueuePlanner.INDEX_UNSET
    }

    private fun currentPlaybackQueueIndex(): Int {
        val queue = _queue.value
        return MusicQueuePlanner.currentQueueIndex(
            queueIds = queue.map { it.videoId },
            playerIndex = player?.currentMediaItemIndex ?: MusicQueuePlanner.INDEX_UNSET,
            currentTrackId = _currentTrack.value?.videoId
        )
    }

    private fun buildMediaItem(
        track: MusicTrack,
        uri: Uri = Uri.parse("music://${track.videoId}"),
        useCacheKey: Boolean = true
    ): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.videoId)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(Uri.parse(track.highResThumbnailUrl))
                    .build()
            )

        if (useCacheKey) {
            builder.setCustomCacheKey(track.videoId)
        }

        return builder
            .build()
    }

    private fun clearPendingPlayNext() {
        pendingPlayNextMediaId = null
        pendingPlayNextMediaIndex = MusicQueuePlanner.INDEX_UNSET
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
        positionUpdateJob?.cancel()
        positionUpdateJob = scope.launch {
            while (true) {
                player?.let { p ->
                    if (p.isPlaying) {
                        _currentPosition.value = p.currentPosition
                        _playerState.value = _playerState.value.copy(position = p.currentPosition)
                    }
                }
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    // --- Playback Control Methods ---

    fun setPendingTrack(track: MusicTrack, sourceName: String? = null) {
        clearPendingPlayNext()
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
         clearPendingPlayNext()
         _currentTrack.value = track
         sourceName?.let { _playingFrom.value = it }
    }

    fun showPlaybackWarning(message: String) {
        _playbackWarnings.tryEmit(message)
    }

    fun playTrack(track: MusicTrack, audioStream: AudioStream, durationSeconds: Long, queue: List<MusicTrack> = emptyList(), startIndex: Int = -1, sourceName: String? = null) {
        playTrack(track, audioStream.content, queue, startIndex, sourceName = sourceName)
    }

    fun playTrack(
        track: MusicTrack,
        audioUrl: String,
        queue: List<MusicTrack> = emptyList(),
        startIndex: Int = -1,
        startPositionMs: Long = 0,
        sourceName: String? = null,
        localUriOverrides: Map<String, Uri> = emptyMap()
    ) {
        player?.stop()
        player?.clearMediaItems()
        clearPendingPlayNext()

        _playerState.value = _playerState.value.copy(isPreparing = false)
        
        val activeQueue = if (queue.isNotEmpty()) queue else listOf(track)
        _queue.value = activeQueue
        _currentTrack.value = track
        sourceName?.let { _playingFrom.value = it }
        
        val mediaItems = activeQueue.map { t ->
            val localUri = localUriOverrides[t.videoId]
            val uri = localUri ?: if (t.videoId == track.videoId && audioUrl.isNotEmpty()) {
                    Uri.parse(audioUrl)
                } else {
                    Uri.parse("music://${t.videoId}")
                }

            buildMediaItem(t, uri, useCacheKey = localUri == null)
        }
        
        val startIdx = if (startIndex >= 0) startIndex else activeQueue.indexOfFirst { it.videoId == track.videoId }.coerceAtLeast(0)
        
        player?.setMediaItems(mediaItems, startIdx, startPositionMs)
        player?.prepare()
        player?.play()
        
        prefetchNextTrack()
    }
    
    fun updateQueue(newQueue: List<MusicTrack>) {
        if (newQueue.isEmpty()) return
        
        _queue.value = newQueue
        if (pendingPlayNextMediaId != null && newQueue.none { it.videoId == pendingPlayNextMediaId }) {
            clearPendingPlayNext()
        }
        triggerQueueSave()
        
        scope.launch {
            val currentMediaId = player?.currentMediaItem?.mediaId
            val currentPosition = player?.currentPosition ?: 0L
            
            val mediaItems = newQueue.map { track -> buildMediaItem(track) }
            
            val newIndex = MusicQueuePlanner.currentQueueIndex(
                queueIds = newQueue.map { it.videoId },
                playerIndex = player?.currentMediaItemIndex ?: _currentQueueIndex.value,
                currentTrackId = currentMediaId
            ).coerceAtLeast(0)
            
            player?.let { p ->
                 if (p.mediaItemCount != mediaItems.size || p.currentMediaItem?.mediaId != currentMediaId) {
                     p.setMediaItems(mediaItems, newIndex, currentPosition)
                 }
            }
        }
    }

    fun updateAutomixItems(items: List<MusicTrack>) {
        val currentId = _currentTrack.value?.videoId
        _automixItems.value = items
            .filterNot { it.videoId == currentId }
            .distinctBy { it.videoId }
        triggerQueueSave()
    }

    fun removeAutomixItem(videoId: String) {
        val updated = _automixItems.value.filterNot { it.videoId == videoId }
        if (updated.size != _automixItems.value.size) {
            _automixItems.value = updated
            triggerQueueSave()
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
                },
                automix = _automixItems.value
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
            _automixItems.value = savedState.automix
            
            val currentTrack = savedState.currentTrackId?.let { id ->
                savedState.queue.find { it.videoId == id }
            } ?: savedState.queue.getOrNull(savedState.currentIndex)
            
            currentTrack?.let {
                _currentTrack.value = it
                if (it.duration > 0) {
                    _playerState.value = _playerState.value.copy(duration = it.duration * 1000L)
                }
            }
            
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
                    },
                    automix = _automixItems.value
                )
            }
        }
    }

    fun togglePlayPause() {
        scope.launch {
            player?.let { p ->
                if (p.mediaItemCount == 0 && _currentTrack.value != null) {
                    _currentTrack.value?.let { track ->
                        _playerEvents.emit(PlayerEvent.RequestPlayTrack(track))
                    }
                } else if (p.isPlaying) {
                    p.pause()
                } else {
                    p.play()
                }
            }
        }
    }
    
    fun playNext(track: MusicTrack) {
        val currentQ = _queue.value.toMutableList()
        val insertIdx = MusicQueuePlanner.playNextInsertionIndex(
            queueIds = currentQ.map { it.videoId },
            playerIndex = player?.currentMediaItemIndex ?: MusicQueuePlanner.INDEX_UNSET,
            currentTrackId = _currentTrack.value?.videoId
        )

        currentQ.add(insertIdx, track)
        _queue.value = currentQ
        pendingPlayNextMediaId = track.videoId
        pendingPlayNextMediaIndex = insertIdx
        
        player?.let { p ->
            val playerInsertIdx = when {
                p.currentMediaItemIndex in 0 until p.mediaItemCount -> p.currentMediaItemIndex + 1
                else -> insertIdx
            }.coerceIn(0, p.mediaItemCount)

            if (playerInsertIdx <= p.mediaItemCount) {
                p.addMediaItem(playerInsertIdx, buildMediaItem(track))
                pendingPlayNextMediaIndex = playerInsertIdx
            }
        }
        
        triggerQueueSave()
    }

    fun addToQueue(track: MusicTrack) {
        val currentQ = _queue.value.toMutableList()
        currentQ.add(track)
        _queue.value = currentQ
        
        player?.let { p ->
            p.addMediaItem(buildMediaItem(track))
        }
        
        triggerQueueSave()
    }
    
    fun playNext() {
        val queue = _queue.value
        val idx = currentPlaybackQueueIndex()
        
        if (idx != -1 && idx < queue.size - 1) {
             val nextTrack = queue[idx + 1]
             setPendingTrack(nextTrack) 
             scope.launch { _playerEvents.emit(PlayerEvent.RequestPlayTrack(nextTrack)) }
        }
    }
    
    fun playPrevious() {
        scope.launch {
            val queue = _queue.value
            val idx = currentPlaybackQueueIndex()
            
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
            clearPendingPlayNext()
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
            val duration = player?.duration?.takeIf { it > 0 } ?: _playerState.value.duration.takeIf { it > 0 }
            val target = duration?.let { position.coerceIn(0L, it) } ?: position.coerceAtLeast(0L)

            _currentPosition.value = target
            _playerState.value = _playerState.value.copy(position = target)
            player?.seekTo(target)
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
    
    fun getCurrentPosition(): Long {
        return try {
            if (player?.isPlaying == true) {
                player?.currentPosition ?: _currentPosition.value
            } else {
                _currentPosition.value
            }
        } catch (e: Exception) {
            _currentPosition.value
        }
    }
    fun getDuration(): Long = _playerState.value.duration
    
    fun toggleLike() {
        _isLiked.value = !_isLiked.value
        emitToggleLikeEvent()
    }

    fun setLiked(liked: Boolean) {
        _isLiked.value = liked
    }

    fun emitToggleLikeEvent() {
        scope.launch { _playerEvents.emit(PlayerEvent.RequestToggleLike) }
    }
    
    fun play() { scope.launch { player?.play() } }
    
    fun pause() { scope.launch { player?.pause() } }
    
    fun stop() {
        scope.launch {
            player?.stop()
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                isBuffering = false,
                isPreparing = false,
                position = 0L
            )
            _currentPosition.value = 0L
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
        
        val baseProfile = io.github.aedev.flow.data.model.EqPresets.presets[profileName] 
            ?: io.github.aedev.flow.data.model.EqPresets.presets["Flat"]
            ?: io.github.aedev.flow.data.model.ParametricEQ.createFlat()
            
        val boost = _bassBoostLevel.value
        val finalProfile = if (boost > 0) {
            val existingBandIndex = baseProfile.bands.indexOfFirst { 
                it.filterType == io.github.aedev.flow.data.model.FilterType.LSC && 
                it.frequency in 40.0..80.0 
            }
            
            val newBands = if (existingBandIndex >= 0) {
                val mutableBands = baseProfile.bands.toMutableList()
                val existing = mutableBands[existingBandIndex]
                mutableBands[existingBandIndex] = existing.copy(gain = existing.gain + boost.toDouble())
                mutableBands.toList()
            } else {
                 val boostBand = io.github.aedev.flow.data.model.ParametricEQBand(
                    60.0, 
                    boost.toDouble(), 
                    0.7, 
                    io.github.aedev.flow.data.model.FilterType.LSC
                )
                listOf(boostBand) + baseProfile.bands
            }
            baseProfile.copy(bands = newBands)
        } else {
            baseProfile
        }
        
        applyEqProfile(finalProfile)
    }

    fun applyEqProfile(profile: io.github.aedev.flow.data.model.ParametricEQ) {
        try {
            val json = Json.encodeToString(io.github.aedev.flow.data.model.ParametricEQ.serializer(), profile)
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
            player?.pause()
            player?.stop()
            player?.clearMediaItems()
            _currentTrack.value = null
            _queue.value = emptyList()
            _automixItems.value = emptyList()
            _currentQueueIndex.value = 0
            clearPendingPlayNext()
            _currentPosition.value = 0L
            _playingFrom.value = "Flow Music"
            _playerState.value = MusicPlayerState()
            appContext?.let { context ->
                context.stopService(Intent(context, Media3MusicService::class.java))
            }
        }
    }
    
    fun removeFromQueue(index: Int) {
         removeMediaItem(index)
    }

    fun removeMediaItem(index: Int) {
         scope.launch {
             val currentQ = _queue.value.toMutableList()
             if (index in currentQ.indices) {
                 currentQ.removeAt(index)
                 _queue.value = currentQ
                 when {
                     pendingPlayNextMediaIndex == index -> clearPendingPlayNext()
                     pendingPlayNextMediaIndex > index -> pendingPlayNextMediaIndex--
                 }
                 
                 player?.let { p ->
                     if (index < p.mediaItemCount) {
                         p.removeMediaItem(index)
                     }
                 }
                 triggerQueueSave()
             }
         }
    }

    fun moveMediaItem(fromIndex: Int, toIndex: Int) {
         scope.launch {
             val currentQ = _queue.value.toMutableList()
             if (fromIndex in currentQ.indices && toIndex in currentQ.indices) {
                 val item = currentQ.removeAt(fromIndex)
                 currentQ.add(toIndex, item)
                 _queue.value = currentQ
                 clearPendingPlayNext()
                 
                 player?.let { p ->
                     if (fromIndex < p.mediaItemCount && toIndex < p.mediaItemCount) {
                         p.moveMediaItem(fromIndex, toIndex)
                     }
                 }
                 triggerQueueSave()
             }
         }
    }

    fun insertMediaItem(index: Int, track: MusicTrack) {
         scope.launch {
             val currentQ = _queue.value.toMutableList()
             val insertIndex = index.coerceIn(0, currentQ.size)
             currentQ.add(insertIndex, track)
             _queue.value = currentQ
             if (pendingPlayNextMediaIndex >= insertIndex) {
                 pendingPlayNextMediaIndex++
             }
             
             player?.let { p ->
                 if (insertIndex <= p.mediaItemCount) {
                     p.addMediaItem(insertIndex, buildMediaItem(track))
                 }
             }
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
