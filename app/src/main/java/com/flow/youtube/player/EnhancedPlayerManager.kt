package com.flow.youtube.player

import android.content.Context
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import com.flow.youtube.data.local.PlayerPreferences

// Modular components
import com.flow.youtube.player.audio.AudioFeaturesManager
import com.flow.youtube.player.cache.PlayerCacheManager
import com.flow.youtube.player.config.PlayerConfig
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.error.PlayerErrorHandler
import com.flow.youtube.player.factory.PlayerFactory
import com.flow.youtube.player.media.MediaLoader
import com.flow.youtube.player.quality.QualityManager
import com.flow.youtube.player.service.BackgroundServiceManager
import com.flow.youtube.player.sponsorblock.SponsorBlockHandler
import com.flow.youtube.player.state.EnhancedPlayerState
import com.flow.youtube.player.stream.StreamProcessor
import com.flow.youtube.player.surface.SurfaceManager
import com.flow.youtube.player.tracker.PlaybackTracker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.flow.youtube.data.model.SponsorBlockSegment
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

@UnstableApi
class EnhancedPlayerManager private constructor() {
    companion object {
        private const val TAG = PlayerConfig.TAG
        
        @Volatile
        private var instance: EnhancedPlayerManager? = null
        
        fun getInstance(): EnhancedPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: EnhancedPlayerManager().also { instance = it }
            }
        }
    }
    
    // Core player components
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    
    // State management
    private val _playerState = MutableStateFlow(EnhancedPlayerState())
    val playerState: StateFlow<EnhancedPlayerState> = _playerState.asStateFlow()
    
    // Stream data
    private var currentVideoId: String? = null
    private var availableVideoStreams: List<VideoStream> = emptyList()
    private var availableAudioStreams: List<AudioStream> = emptyList()
    private var availableSubtitles: List<SubtitlesStream> = emptyList()
    private var currentVideoStream: VideoStream? = null
    private var currentAudioStream: AudioStream? = null
    private var selectedSubtitleIndex: Int? = null
    
    // Duration and manifest info
    private var currentDurationSeconds: Long = -1
    private var currentDashManifestUrl: String? = null

    // Queue management
    private var playbackQueue: List<com.flow.youtube.data.model.Video> = emptyList()
    private var currentQueueIndex: Int = -1
    private var queueTitle: String? = null
    
    // Application context
    private var appContext: Context? = null
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Modular components
    private val playerFactory = PlayerFactory()
    private val backgroundServiceManager = BackgroundServiceManager()
    private var cacheManager: PlayerCacheManager? = null
    private var qualityManager: QualityManager? = null
    private var surfaceManager: SurfaceManager? = null
    private var sponsorBlockHandler: SponsorBlockHandler? = null
    private var playbackTracker: PlaybackTracker? = null
    private var errorHandler: PlayerErrorHandler? = null
    private var audioFeaturesManager: AudioFeaturesManager? = null
    private var mediaLoader: MediaLoader? = null
    
    // Public Queue State
    private val _queueVideos = MutableStateFlow<List<Video>>(emptyList())
    val queueVideos: StateFlow<List<Video>> = _queueVideos.asStateFlow()
    
    private val _currentQueueIndex = MutableStateFlow<Int>(-1)
    val currentQueueIndexState: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    // Public surface ready state
    val isSurfaceReady: Boolean
        get() = surfaceManager?.isSurfaceReady ?: false

    // ===== Initialization =====
    
    fun initialize(context: Context) {
        appContext = context.applicationContext
        
        if (player == null) {
            initializeComponents(context)
            initializePlayer(context)
            setupPlayerListener()
            startPlaybackTracker()
            observePreferences(context)
            Log.d(TAG, "Player initialized")
        }
    }
    
    private fun initializeComponents(context: Context) {
        // Initialize cache manager
        cacheManager = PlayerCacheManager(context).also { it.initialize() }
        
        // Initialize surface manager
        surfaceManager = SurfaceManager(context)
        
        // Initialize sponsor block handler
        sponsorBlockHandler = SponsorBlockHandler(scope)
        
        // Initialize audio features manager
        audioFeaturesManager = AudioFeaturesManager(scope, _playerState)
        
        // Initialize bandwidth meter and track selector via factory
        bandwidthMeter = playerFactory.createBandwidthMeter(context)
        trackSelector = playerFactory.createTrackSelector(context)
        
        // Initialize media loader
        mediaLoader = MediaLoader(_playerState, cacheManager, surfaceManager)
        
        // Initialize quality manager
        qualityManager = QualityManager(
            bandwidthMeter = bandwidthMeter,
            trackSelector = trackSelector,
            stateFlow = _playerState,
            onQualitySwitch = { stream, position ->
                currentVideoStream = stream
                loadMediaInternal(stream, currentAudioStream, position)
            }
        )
        
        // Initialize error handler
        errorHandler = PlayerErrorHandler(
            stateFlow = _playerState,
            onReloadStream = { position, reason -> reloadCurrentStream(position, reason) },
            onQualityDowngrade = { attemptQualityDowngrade() },
            onPlaybackShutdown = { onPlaybackShutdown() },
            getFailedStreamUrls = { qualityManager?.let { qm ->
                availableVideoStreams.filter { qm.hasStreamFailed(it.getContent()) }.map { it.getContent() }.toSet()
            } ?: emptySet() },
            markStreamFailed = { url -> qualityManager?.markStreamFailed(url) },
            incrementStreamErrors = { qualityManager?.let { it.streamErrorCount } },
            getStreamErrorCount = { qualityManager?.streamErrorCount ?: 0 },
            isAdaptiveQualityEnabled = { qualityManager?.isAdaptiveQualityEnabled ?: true },
            getManualQualityHeight = { qualityManager?.manualQualityHeight },
            getCurrentVideoStream = { currentVideoStream },
            getCurrentAudioStream = { currentAudioStream },
            getAvailableAudioStreams = { availableAudioStreams },
            setCurrentAudioStream = { audio -> currentAudioStream = audio },
            setRecoveryState = { errorHandler?.setRecovery() },
            reloadPlaybackManager = { reloadPlaybackManager() }
        )
        
        // Initialize playback tracker
        playbackTracker = PlaybackTracker(
            scope = scope,
            stateFlow = _playerState,
            onSponsorBlockCheck = { pos -> sponsorBlockHandler?.checkForSkip(pos) },
            onBufferingDetected = {
                qualityManager?.let { qm ->
                    qm.incrementBufferingCount()
                    if (qm.hasReachedBufferingThreshold()) {
                        qm.checkAdaptiveQualityDowngrade(forceCheck = true, player?.currentPosition ?: 0L)
                        qm.resetBufferingCount()
                    }
                }
            },
            onSmoothPlayback = { qualityManager?.resetBufferingCount() },
            onBandwidthCheckNeeded = {
                qualityManager?.let { qm ->
                    if (qm.shouldCheckBandwidth()) {
                        qm.updateBandwidthCheckTime()
                        qm.checkAdaptiveQualityUpgrade(player?.currentPosition ?: 0L)
                    }
                }
            }
        )
    }
    
    private fun initializePlayer(context: Context) {
        val loadControl = playerFactory.createLoadControl(context)
        val silenceProcessor = audioFeaturesManager?.silenceSkippingProcessor 
            ?: throw IllegalStateException("AudioFeaturesManager not initialized")
        val renderersFactory = playerFactory.createRenderersFactory(context, silenceProcessor)
        
        player = playerFactory.createPlayer(
            context = context,
            trackSelector = trackSelector!!,
            loadControl = loadControl,
            renderersFactory = renderersFactory,
            dataSourceFactory = cacheManager?.getDataSourceFactory()
        )
        
        surfaceManager?.reattachSurfaceIfValid(player)
    }
    
    private fun observePreferences(context: Context) {
        audioFeaturesManager?.observeSkipSilencePreference(context)
        
        scope.launch {
            PlayerPreferences(context).sponsorBlockEnabled.collect { isEnabled ->
                sponsorBlockHandler?.setEnabled(isEnabled)
            }
        }
    }

    // ===== Player Listener =====
    
    private fun setupPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    _playerState.value = _playerState.value.copy(effectiveQuality = videoSize.height)
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playerState.value = _playerState.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    playWhenReady = player?.playWhenReady ?: false,
                    hasEnded = playbackState == Player.STATE_ENDED
                )
                
                if (playbackState == Player.STATE_ENDED) {
                    if (_playerState.value.isLooping) {
                        player?.seekTo(0)
                        player?.play()
                    } else if (hasNext()) {
                        playNext()
                    }
                }
                
                if (playbackState == Player.STATE_BUFFERING) {
                    logBandwidthInfo()
                }
            }
            
            override fun onRenderedFirstFrame() {
                Log.d(TAG, "First frame rendered - video renderer working")
                surfaceManager?.setSurfaceReady(true)
                val rendererAvailable = isVideoRendererAvailable()
                Log.d(TAG, "Video renderer confirmed available after first frame: $rendererAvailable")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
            }
            
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _playerState.value = _playerState.value.copy(playWhenReady = playWhenReady)
            }

            override fun onPlayerError(error: PlaybackException) {
                errorHandler?.handleError(error, player)
            }
        })
    }
    
    private fun startPlaybackTracker() {
        player?.let { playbackTracker?.start(it) }
    }

    // ===== Stream Management =====
    
    suspend fun setStreams(
        videoId: String,
        videoStream: VideoStream?,
        audioStream: AudioStream,
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
        subtitles: List<SubtitlesStream>,
        durationSeconds: Long = -1,
        dashManifestUrl: String? = null,
        localFilePath: String? = null
    ) {
        Log.d(TAG, "setStreams(id=$videoId, videoHeight=${videoStream?.height})")
        resetPlaybackStateForNewVideo(videoId)
        
        // Reset and load SponsorBlock
        sponsorBlockHandler?.reset()
        sponsorBlockHandler?.loadSegments(videoId)
        
        this.currentDurationSeconds = durationSeconds
        this.currentDashManifestUrl = dashManifestUrl
        currentVideoId = videoId
        
        // Process streams using StreamProcessor
        availableVideoStreams = StreamProcessor.processVideoStreams(videoStreams)
        availableAudioStreams = StreamProcessor.processAudioStreams(audioStreams)
        availableSubtitles = subtitles
        
        // Update quality manager with available streams
        qualityManager?.setAvailableStreams(availableVideoStreams)
        
        // Smart initial quality selection
        val smartStream = qualityManager?.selectSmartInitialQuality()
        currentVideoStream = videoStream ?: smartStream ?: availableVideoStreams.firstOrNull()
        qualityManager?.setCurrentStream(currentVideoStream)
        currentAudioStream = audioStream
        
        // Update state with available options
        _playerState.value = _playerState.value.copy(
            currentVideoId = videoId,
            effectiveQuality = currentVideoStream?.height ?: 0,
            availableQualities = qualityManager?.buildQualityOptions() ?: emptyList(),
            availableAudioTracks = StreamProcessor.toAudioTrackOptions(availableAudioStreams),
            availableSubtitles = StreamProcessor.toSubtitleOptions(subtitles),
            currentQuality = 0,
            currentAudioTrack = availableAudioStreams.indexOf(currentAudioStream).coerceAtLeast(0)
        )

        // Wait for surface to be ready
        val timeout = appContext?.let { ctx ->
            kotlinx.coroutines.runBlocking { PlayerPreferences(ctx).surfaceReadyTimeoutMs.first() }
        } ?: PlayerConfig.DEFAULT_SURFACE_TIMEOUT_MS
        
        surfaceManager?.awaitSurfaceReady(timeout)

        // Load media
        when {
            localFilePath != null -> loadMediaInternal(null, audioStream, localFilePath = localFilePath)
            currentVideoStream != null -> loadMediaInternal(currentVideoStream, currentAudioStream)
            else -> loadMediaInternal(null, currentAudioStream ?: audioStream)
        }
    }

    private fun resetPlaybackStateForNewVideo(videoId: String) {
        qualityManager?.resetForNewVideo()
        playbackTracker?.reset()
        currentVideoStream = null
        currentAudioStream = null
        currentDashManifestUrl = null
        selectedSubtitleIndex = null
        
        player?.let { it.stop(); it.clearMediaItems() }
        
        _playerState.value = _playerState.value.copy(
            currentVideoId = videoId, isBuffering = true, error = null,
            hasEnded = false, isPrepared = false, recoveryAttempted = false, currentQuality = 0,
            playWhenReady = player?.playWhenReady ?: true
        )
    }

    private fun loadMediaInternal(
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        preservePosition: Long? = null,
        localFilePath: String? = null
    ) {
        val audio = audioStream ?: availableAudioStreams.firstOrNull() ?: return
        mediaLoader?.loadMedia(
            player = player,
            context = appContext,
            videoStream = videoStream,
            audioStream = audio,
            availableVideoStreams = availableVideoStreams,
            currentVideoStream = currentVideoStream,
            dashManifestUrl = currentDashManifestUrl,
            durationSeconds = -1,
            currentDurationSeconds = currentDurationSeconds,
            preservePosition = preservePosition,
            localFilePath = localFilePath
        )
    }

    // ===== Queue Management =====

    fun setQueue(videos: List<Video>, startIndex: Int, title: String? = null) {
        playbackQueue = videos
        currentQueueIndex = startIndex.coerceIn(0, videos.size - 1)
        queueTitle = title
        
        _queueVideos.value = videos
        _currentQueueIndex.value = currentQueueIndex
        
        updateQueueState()
        
        if (videos.isNotEmpty()) {
            val video = videos[currentQueueIndex]
            startPlaybackFromQueue(video)
        }
    }

    fun playNext() {
        if (currentQueueIndex < playbackQueue.size - 1) {
            currentQueueIndex++
            _currentQueueIndex.value = currentQueueIndex
            startPlaybackFromQueue(playbackQueue[currentQueueIndex])
            updateQueueState()
        }
    }

    fun playPrevious() {
        // If we are more than 3 seconds into the video, restart it
        if ((player?.currentPosition ?: 0) > 3000) {
            player?.seekTo(0)
            return
        }

        if (currentQueueIndex > 0) {
            currentQueueIndex--
            _currentQueueIndex.value = currentQueueIndex
            startPlaybackFromQueue(playbackQueue[currentQueueIndex])
            updateQueueState()
        }
    }

    fun hasNext(): Boolean = currentQueueIndex < playbackQueue.size - 1

    fun hasPrevious(): Boolean = currentQueueIndex > 0 || (player?.currentPosition ?: 0) > 3000
    
    fun playVideoAtIndex(index: Int) {
        if (index in playbackQueue.indices && index != currentQueueIndex) {
            currentQueueIndex = index
            _currentQueueIndex.value = currentQueueIndex
            startPlaybackFromQueue(playbackQueue[currentQueueIndex])
            updateQueueState()
        }
    }

    private fun startPlaybackFromQueue(video: Video) {
        // Reset player state for new video
        resetPlaybackStateForNewVideo(video.id)
        
        _playerState.value = _playerState.value.copy(
            currentVideoId = video.id,
            isPlaying = true,
            playWhenReady = true,
            isBuffering = true
        )
        
        GlobalPlayerState.setCurrentVideo(video)
    }

    private fun updateQueueState() {
        _playerState.value = _playerState.value.copy(
            hasNext = hasNext(),
            hasPrevious = hasPrevious(),
            queueTitle = queueTitle,
            queueSize = playbackQueue.size
        )
    }

    // ===== Playback Controls =====
    
    fun play() = player?.play()
    fun pause() = player?.pause()
    fun seekTo(position: Long) = player?.seekTo(position)
    
    fun toggleLoop(enabled: Boolean) {
        _playerState.value = _playerState.value.copy(isLooping = enabled)
    }
    
    fun stop() {
        playbackTracker?.stop()
        player?.stop()
        player?.clearMediaItems()
        qualityManager?.resetForNewVideo()
        _playerState.value = _playerState.value.copy(isPlaying = false, currentVideoId = null)
    }

    fun getPlayer(): ExoPlayer? = player
    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L
    fun getDuration(): Long = player?.duration ?: 0L
    fun isPlaying(): Boolean = player?.isPlaying ?: false

    // ===== Quality & Audio Management =====
    
    fun switchQualityByHeight(height: Int) = qualityManager?.switchQualityByHeight(height, player?.currentPosition ?: 0L)
    fun switchQuality(height: Int) = switchQualityByHeight(height)
    
    fun switchAudioTrack(index: Int) {
        if (index in availableAudioStreams.indices) {
            currentAudioStream = availableAudioStreams[index]
            val position = player?.currentPosition ?: 0L
            val wasPlaying = player?.isPlaying ?: false
            loadMediaInternal(currentVideoStream, currentAudioStream)
            player?.seekTo(position)
            if (wasPlaying) player?.play()
            _playerState.value = _playerState.value.copy(currentAudioTrack = index)
        }
    }

    fun selectSubtitle(index: Int?) {
        if (selectedSubtitleIndex != index) {
            selectedSubtitleIndex = index
            Log.d(TAG, "Subtitle selected: $index")
        }
    }

    // ===== Audio Features =====
    
    fun setPlaybackSpeed(speed: Float) = audioFeaturesManager?.setPlaybackSpeed(player, speed)
    fun toggleSkipSilence(isEnabled: Boolean) = audioFeaturesManager?.toggleSkipSilence(isEnabled, appContext)
    
    fun toggleSponsorBlock(isEnabled: Boolean) {
        sponsorBlockHandler?.setEnabled(isEnabled)
        appContext?.let { ctx ->
            scope.launch { PlayerPreferences(ctx).setSponsorBlockEnabled(isEnabled) }
        }
    }
    
    val sponsorSegments: StateFlow<List<SponsorBlockSegment>>
        get() = sponsorBlockHandler?.sponsorSegments ?: MutableStateFlow(emptyList())

    val skipEvent: SharedFlow<SponsorBlockSegment>
        get() = sponsorBlockHandler?.skipEvent ?: MutableSharedFlow()

    // ===== Surface Management =====
    
    fun attachVideoSurface(holder: SurfaceHolder?) = surfaceManager?.attachVideoSurface(holder, player)
    fun detachVideoSurface(holder: SurfaceHolder? = null) = surfaceManager?.detachVideoSurface(holder, player, appContext)
    fun clearSurface() = surfaceManager?.clearSurface(player)
    suspend fun awaitSurfaceReady(timeoutMillis: Long = 1000) = surfaceManager?.awaitSurfaceReady(timeoutMillis) ?: false
    
    fun setSurfaceReady(ready: Boolean) {
        surfaceManager?.setSurfaceReady(ready)
        if (ready && player?.currentMediaItem == null && currentVideoStream != null && currentAudioStream != null) {
            loadMediaInternal(currentVideoStream, currentAudioStream)
        }
    }
    
    fun retryLoadMediaIfSurfaceReady() {
        if (isSurfaceReady && currentVideoStream != null && currentAudioStream != null) {
            loadMediaInternal(currentVideoStream, currentAudioStream)
        }
    }

    // ===== Cache & Background Service =====
    
    fun getCacheSize(): Long = cacheManager?.getCacheSize() ?: 0L
    fun clearCache() = cacheManager?.clearCache()
    
    fun startBackgroundService(videoId: String, title: String, channel: String, thumbnail: String) =
        backgroundServiceManager.startService(appContext, videoId, title, channel, thumbnail)
    
    fun stopBackgroundService() = backgroundServiceManager.stopService(appContext)

    // ===== Bandwidth & Renderer Info =====
    
    fun getBandwidthEstimate(): Long = bandwidthMeter?.bitrateEstimate ?: 0L
    
    fun logBandwidthInfo() {
        val mbps = getBandwidthEstimate() / 1_000_000.0
        Log.d(TAG, "Bandwidth: ${"%.2f".format(mbps)} Mbps")
    }
    
    fun isVideoRendererAvailable(): Boolean {
        player?.let { p ->
            if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_BUFFERING) return true
            if (p.currentTracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }) return true
            trackSelector?.currentMappedTrackInfo?.let { info ->
                for (i in 0 until info.rendererCount) {
                    if (info.getTrackGroups(i).length > 0 && p.getRendererType(i) == C.TRACK_TYPE_VIDEO) return true
                }
            }
        }
        return false
    }

    // ===== Clear & Release =====

    fun clearCurrentVideo() {
        player?.stop()
        player?.clearMediaItems()
        qualityManager?.resetForNewVideo()
        currentVideoId = null
        currentVideoStream = null
        currentAudioStream = null
        _playerState.value = _playerState.value.copy(
            isPlaying = false, currentVideoId = null, currentQuality = 0,
            bufferedPercentage = 0f, isBuffering = false, isPrepared = false, hasEnded = false
        )
    }

    fun clearAll() {
        clearCurrentVideo()
        playbackQueue = emptyList()
        currentQueueIndex = -1
        _queueVideos.value = emptyList()
        _currentQueueIndex.value = -1
        queueTitle = null
        _playerState.value = _playerState.value.copy(
            hasNext = false, hasPrevious = false, queueTitle = null, queueSize = 0
        )
    }

    fun isQueueActive(): Boolean = playbackQueue.isNotEmpty()

    fun release() {
        Log.d(TAG, "release() called")
        playbackTracker?.stop()
        surfaceManager?.release(player)
        player?.release()
        player = null
        trackSelector = null
        appContext = null
        cacheManager?.release()
        cacheManager = null
        _playerState.value = EnhancedPlayerState()
        Log.d(TAG, "Player released")
    }

    // ===== Error Recovery =====
    
    private fun reloadCurrentStream(preservePosition: Long?, reason: String) {
        val video = currentVideoStream ?: return
        val audio = currentAudioStream ?: availableAudioStreams.firstOrNull() ?: return
        val pos = preservePosition ?: player?.currentPosition ?: 0L
        Log.d(TAG, "Reloading ${video.height}p at ${pos}ms ($reason)")
        player?.stop()
        player?.clearMediaItems()
        loadMediaInternal(video, audio, pos)
    }
    
    private fun reloadPlaybackManager() {
        try {
            Thread.sleep(PlayerConfig.ERROR_RETRY_DELAY_MS)
            val pos = player?.currentPosition ?: 0L
            player?.stop()
            player?.clearMediaItems()

            if (qualityManager?.isAdaptiveQualityEnabled == false) {
                reloadCurrentStream(pos, "manual-quality-reload")
                return
            }

            currentVideoStream?.let { stream ->
                if (qualityManager?.hasStreamFailed(stream.getContent()) == true) {
                    val working = qualityManager?.getWorkingStreams()?.maxByOrNull { it.height }
                    if (working != null) {
                        currentVideoStream = working
                        qualityManager?.resetStreamErrors()
                    } else {
                        onPlaybackShutdown()
                        return
                    }
                }
            }

            currentVideoStream?.let { loadMediaInternal(it, currentAudioStream, pos) }
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading", e)
            onPlaybackShutdown()
        }
    }
    
    private fun attemptQualityDowngrade() {
        val newStream = qualityManager?.attemptQualityDowngrade()
        if (newStream != null) {
            currentVideoStream = newStream
            loadMediaInternal(newStream, currentAudioStream)
        } else {
            _playerState.value = _playerState.value.copy(
                error = "Unable to play - all quality options failed", isPlaying = false, isBuffering = false
            )
            onPlaybackShutdown()
        }
    }
    
    private fun onPlaybackShutdown() = errorHandler?.handlePlaybackShutdown(player)
}

// Backward compatibility type aliases
typealias EnhancedPlayerState = com.flow.youtube.player.state.EnhancedPlayerState
typealias QualityOption = com.flow.youtube.player.state.QualityOption
typealias AudioTrackOption = com.flow.youtube.player.state.AudioTrackOption
typealias SubtitleOption = com.flow.youtube.player.state.SubtitleOption
