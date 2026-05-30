package io.github.aedev.flow.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.utils.ThumbnailUrlResolver

// Modular components
import io.github.aedev.flow.player.audio.AudioFeaturesManager
import io.github.aedev.flow.player.cache.PlayerCacheManager
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.error.PlayerErrorHandler
import io.github.aedev.flow.player.factory.PlayerFactory
import io.github.aedev.flow.player.media.MediaLoader
import io.github.aedev.flow.player.quality.QualityManager
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import io.github.aedev.flow.player.service.BackgroundServiceManager
import io.github.aedev.flow.player.sponsorblock.SponsorBlockHandler
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.player.state.QualityOption
import io.github.aedev.flow.player.stream.StreamProcessor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.player.surface.SurfaceManager
import io.github.aedev.flow.player.tracker.PlaybackTracker

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import io.github.aedev.flow.data.model.SponsorBlockSegment
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

@UnstableApi
class EnhancedPlayerManager private constructor() {
    companion object {
        private const val TAG = PlayerConfig.TAG
        private const val LIVE_EDGE_THRESHOLD_MS = 700L
        
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
    private var innerTubeVideoFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList()
    private var innerTubeAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList()
    
    // Duration and manifest info
    private var currentDurationSeconds: Long = -1
    private var currentDashManifestUrl: String? = null
    private var currentHlsUrl: String? = null
    private var currentIsLiveStream = false
    private var preLivePlaybackSpeed: Float? = null
    private var pendingLiveDisplaySeekPositionMs: Long? = null
    private var pendingLiveDisplaySeekAtMs: Long = 0L
    private var pendingInitialLiveEdgeSeek = false
    
    private var currentSabrInfo: SabrStreamInfo? = null

    private var isAudioOnlyMode = false
    private var videoTracksDisabled = false
    @Volatile private var videoSurfaceRestorePending = false

    // Queue management
    private var playbackQueue: List<io.github.aedev.flow.data.model.Video> = emptyList()
    private var currentQueueIndex: Int = -1
    private var queueTitle: String? = null
    private var manualLoopEnabled: Boolean = false
    private var globalLoopEnabled: Boolean = false
    @Volatile private var autoplayEnabled: Boolean = true
    private var autoplayCandidates: List<Video> = emptyList()
    private var autoplaySourceVideoId: String? = null
    private var autoplayJob: Job? = null
    
    // Application context
    private var appContext: Context? = null
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pendingReloadJob: Job? = null

    /**
     * Set to true while PlaybackRefocusEffect is recovering from a screen-off/on cycle.
     * Prevents onPlaybackStateChanged(STATE_ENDED) from skipping to the next video or
     * seeking to 0 during the transient states that ExoPlayer goes through during recovery.
     */
    @Volatile private var isRecoveringFromBackground = false

    /** Call at the start of a screen-off recovery sequence (before prepare()). */
    fun beginBackgroundRecovery() {
        isRecoveringFromBackground = true
    }

    /** Call after the recovery sequence completes or is abandoned. */
    fun endBackgroundRecovery() {
        isRecoveringFromBackground = false
    }
    
    // Modular components
    private val playerFactory = PlayerFactory()
    private val backgroundServiceManager = BackgroundServiceManager()
    private var cacheManager: PlayerCacheManager? = null
    private var qualityManager: QualityManager? = null
    private var surfaceManager: SurfaceManager? = null
    private var sponsorBlockHandler: SponsorBlockHandler? = null
    private var playbackTracker: PlaybackTracker? = null
    private var errorHandler: PlayerErrorHandler? = null

    private val _streamExpiredEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val streamExpiredEvent: SharedFlow<Unit> = _streamExpiredEvent.asSharedFlow()

    private val _queueAutoAdvanceEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val queueAutoAdvanceEvent: SharedFlow<Unit> = _queueAutoAdvanceEvent.asSharedFlow()

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
        mediaLoader = MediaLoader(_playerState, cacheManager, surfaceManager).also { loader ->
            loader.onSabrFallbackNeeded = {
                scope.launch {
                    Log.w(TAG, "SABR fallback triggered — requesting full re-extraction")
                    currentSabrInfo = null
                    loader.releaseSabr()
                    player?.stop()
                    player?.clearMediaItems()
                    _streamExpiredEvent.emit(Unit)
                }
            }
        }
        
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
            onStreamExpired = { scope.launch { _streamExpiredEvent.emit(Unit) } },
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
            },
            onLivePlaybackTick = { exoPlayer ->
                mediaLoader?.getActiveSabrOrchestrator()?.updatePlayhead(exoPlayer.currentPosition)
                updateLiveEdgeState(exoPlayer)
                if (currentIsLiveStream &&
                    _playerState.value.playbackSpeed > 1.0f &&
                    isPlayerAtLiveEdge(exoPlayer)
                ) {
                    setPlaybackSpeed(1.0f)
                }
            }
        )
    }
    
    private fun initializePlayer(context: Context) {
        val loadControl = playerFactory.createLoadControl(context)
        val renderersFactory = playerFactory.createRenderersFactory(context)
        
        player = playerFactory.createPlayer(
            context = context,
            trackSelector = trackSelector!!,
            loadControl = loadControl,
            renderersFactory = renderersFactory,
            dataSourceFactory = cacheManager?.getDataSourceFactory()
        )
        
        audioFeaturesManager?.setPlayer(player!!)
        
        // Apply initial loop preference + restore remembered playback speed
        scope.launch {
            val prefs = PlayerPreferences(context)
            val loopEnabled = prefs.videoLoopEnabled.first()
            player?.repeatMode = if (loopEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            if (prefs.rememberPlaybackSpeed.first()) {
                val savedSpeed = prefs.playbackSpeed.first()
                if (savedSpeed != 1.0f) setPlaybackSpeed(savedSpeed)
            }
        }
        
        surfaceManager?.reattachSurfaceIfValid(player)
    }

    fun setVolumeBoost(volume: Float) {
        audioFeaturesManager?.setVolumeBoost(player, volume)
    }
    
    private fun observePreferences(context: Context) {
        audioFeaturesManager?.observeSkipSilencePreference(context)
        audioFeaturesManager?.observeStableVolumePreference(context)
        
        val prefs = PlayerPreferences(context)
        scope.launch {
            prefs.sponsorBlockEnabled.collect { isEnabled ->
                sponsorBlockHandler?.setEnabled(isEnabled)
            }
        }

        scope.launch {
            prefs.videoLoopEnabled.collect { isEnabled ->
                globalLoopEnabled = isEnabled
                player?.repeatMode = if (isEnabled) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                updateEffectiveLoopState()
            }
        }

        scope.launch {
            prefs.autoplayEnabled.collect { isEnabled ->
                autoplayEnabled = isEnabled
            }
        }

        // Collect per-category SponsorBlock actions and update handler
        val sbCategories = listOf("sponsor", "intro", "outro", "selfpromo", "interaction", "music_offtopic")
        sbCategories.forEach { category ->
            scope.launch {
                prefs.sbActionForCategory(category).collect { action ->
                    val current = sponsorBlockHandler?.categoryActions?.toMutableMap() ?: mutableMapOf()
                    current[category] = action
                    sponsorBlockHandler?.categoryActions = current
                }
            }
        }
    }

    // ===== Player Listener =====
    
    private fun setupPlayerListener() {
        player?.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.height > 0) {
                    _playerState.value = _playerState.value.copy(
                        effectiveQuality = QualityManager.normalizeQualityHeight(videoSize.height)
                    )
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playerState.value = _playerState.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    playWhenReady = player?.playWhenReady ?: false,
                    hasEnded = playbackState == Player.STATE_ENDED && !isRecoveringFromBackground
                )
                
                if (playbackState == Player.STATE_ENDED && !isRecoveringFromBackground) {
                    if (_playerState.value.isLooping) {
                        player?.seekTo(0)
                        player?.play()
                    } else if (hasNext()) {
                        _queueAutoAdvanceEvent.tryEmit(Unit)
                        playNext(loadStreamsInPlayer = true)
                    } else {
                        playNextAutoplayCandidate()
                    }
                }
                
                if (playbackState == Player.STATE_BUFFERING) {
                    logBandwidthInfo()
                }

                if (playbackState == Player.STATE_READY && pendingInitialLiveEdgeSeek) {
                    player?.let { exoPlayer ->
                        if (currentIsLiveStream || exoPlayer.isCurrentMediaItemLive) {
                            pendingInitialLiveEdgeSeek = false
                            if (exoPlayer.currentMediaItem != null) {
                                exoPlayer.seekToDefaultPosition(exoPlayer.currentMediaItemIndex)
                            } else {
                                exoPlayer.seekToDefaultPosition()
                            }
                            val liveEdgePosition = exoPlayer.duration
                                .takeIf { it > 0L && it != C.TIME_UNSET }
                                ?: exoPlayer.currentPosition.coerceAtLeast(0L)
                            markLiveDisplaySeek(liveEdgePosition)
                            updateLiveEdgeState(exoPlayer)
                        }
                    }
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

            override fun onTracksChanged(tracks: Tracks) {
                applySubtitleTrackSelection()
            }
        })
    }
    
    private fun startPlaybackTracker() {
        player?.let { playbackTracker?.start(it) }
    }

    // ===== Offline / Local File Playback =====

    /**
     * Play a local (downloaded) file directly, bypassing all stream requirements.
     * Muxed MP4 files are self-contained with both audio and video tracks.
     *
     * @param savedSegments Optional SponsorBlock segments loaded from local DB.
     *   When non-empty, they are applied directly without a network call, enabling
     *   offline sponsor-skip. Pass null to fall back to fetching from the API.
     */
    fun playLocalFile(
        videoId: String,
        filePath: String,
        savedSegments: List<SponsorBlockSegment>? = null,
        preservePosition: Long? = null
    ) {
        Log.d(TAG, "playLocalFile: videoId=$videoId, path=$filePath, offlineSegments=${savedSegments?.size}, resumePos=$preservePosition")
        resetPlaybackStateForNewVideo(videoId)
        updateLivePlaybackMode(isLive = false)
        currentVideoId = videoId
        startPlaybackTracker()

        // Apply SponsorBlock: use offline-saved segments if present, otherwise fall back to API.
        sponsorBlockHandler?.reset()
        if (!savedSegments.isNullOrEmpty()) {
            sponsorBlockHandler?.loadSegmentsFromList(videoId, savedSegments)
        } else {
            sponsorBlockHandler?.loadSegments(videoId)
        }

        loadMediaInternal(
            videoStream = null,
            audioStream = null,
            localFilePath = filePath,
            preservePosition = preservePosition
        )
    }

    // ===== Stream Management =====
    
    suspend fun setStreams(
        videoId: String,
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
        subtitles: List<SubtitlesStream>,
        durationSeconds: Long = -1,
        dashManifestUrl: String? = null,
        localFilePath: String? = null,
        hlsUrl: String? = null,
        streamType: StreamType? = null,
        startPosition: Long = 0L,
        sabrInfo: SabrStreamInfo? = null,
        itVideoFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
        itAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList()
    ) {
        Log.d(TAG, "setStreams(id=$videoId, videoHeight=${videoStream?.let(VideoCodecUtils::qualityHeightFromStream)}, sabr=${sabrInfo != null}, itVideo=${itVideoFormats.size}, itAudio=${itAudioFormats.size})")
        resetPlaybackStateForNewVideo(videoId)
        currentSabrInfo = sabrInfo
        innerTubeVideoFormats = itVideoFormats
        innerTubeAudioFormats = itAudioFormats
        isAudioOnlyMode = false
        setVideoTracksDisabled(false)

        // Reset and load SponsorBlock
        sponsorBlockHandler?.reset()
        sponsorBlockHandler?.loadSegments(videoId)
        
        this.currentDurationSeconds = durationSeconds
        this.currentDashManifestUrl = dashManifestUrl
        val useLiveManifest = streamType == StreamType.LIVE_STREAM ||
            streamType == StreamType.POST_LIVE_STREAM
        this.currentHlsUrl = hlsUrl.takeIf { useLiveManifest }
        val liveDurationMs = if (!currentHlsUrl.isNullOrEmpty() && durationSeconds > 0) {
            durationSeconds * 1000L
        } else {
            0L
        }
        val isLiveStream = !currentHlsUrl.isNullOrEmpty()
        updateLivePlaybackMode(isLive = isLiveStream, forceLiveSpeedReset = true)
        pendingInitialLiveEdgeSeek = isLiveStream && startPosition <= 0L
        currentVideoId = videoId
        
        // Process streams using StreamProcessor
        availableVideoStreams = StreamProcessor.processVideoStreams(videoStreams)
        availableAudioStreams = StreamProcessor.processAudioStreams(audioStreams)
        availableSubtitles = StreamProcessor.processSubtitleStreams(subtitles)
        if (audioStream == null && availableAudioStreams.isEmpty()) {
            Log.w(TAG, "setStreams: no separate audio stream for $videoId; attempting video-only/muxed playback")
        }
        
        // Ensure playback tracker is running
        startPlaybackTracker()
        
        // Update quality manager with available streams
        qualityManager?.setAvailableStreams(availableVideoStreams)
        qualityManager?.isDashSource = !currentDashManifestUrl.isNullOrEmpty()

        // Quality selection: respect user preference
        if (videoStream != null) {
            currentVideoStream = videoStream
            qualityManager?.setCurrentStream(currentVideoStream)
            qualityManager?.setManualMode(VideoCodecUtils.qualityHeightFromStream(videoStream))
        } else {
            val smartStream = qualityManager?.selectSmartInitialQuality()
            currentVideoStream = smartStream ?: availableVideoStreams.firstOrNull()
            qualityManager?.setCurrentStream(currentVideoStream)
        }
        currentAudioStream = audioStream
        
        val isAutoMode = (videoStream == null)
        
        // Update state with available options
        _playerState.value = _playerState.value.copy(
            currentVideoId = videoId,
            effectiveQuality = currentVideoStream?.let { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) } ?: 0,
            availableQualities = qualityManager?.buildQualityOptions() ?: emptyList(),
            availableAudioTracks = StreamProcessor.toAudioTrackOptions(availableAudioStreams),
            availableSubtitles = StreamProcessor.toSubtitleOptions(availableSubtitles),
            currentQuality = if (isAutoMode) 0 else (currentVideoStream?.let { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) } ?: 0),
            currentQualityKey = if (isAutoMode) null else currentVideoStream?.getContent()?.takeIf { it.isNotBlank() },
            currentAudioTrack = currentAudioStream?.let { availableAudioStreams.indexOf(it).coerceAtLeast(0) } ?: 0,
            isLive = currentIsLiveStream,
            isAtLiveEdge = false,
            liveDurationMs = liveDurationMs
        )

        val resumePos = startPosition.takeIf { it > 0L }
        when {
            localFilePath != null -> loadMediaInternal(null, audioStream, localFilePath = localFilePath, preservePosition = resumePos)
            currentVideoStream != null -> loadMediaInternal(currentVideoStream, currentAudioStream, preservePosition = resumePos)
            else -> loadMediaInternal(null, currentAudioStream ?: audioStream, preservePosition = resumePos)
        }
    }

    private fun resetPlaybackStateForNewVideo(videoId: String) {
        qualityManager?.resetForNewVideo()
        playbackTracker?.reset()
        errorHandler?.resetExpiryCounter()
        mediaLoader?.releaseSabr()
        currentSabrInfo = null
        innerTubeVideoFormats = emptyList()
        innerTubeAudioFormats = emptyList()
        currentVideoStream = null
        currentAudioStream = null
        currentDashManifestUrl = null
        currentHlsUrl = null
        selectedSubtitleIndex = null
        disableTextTracks()
        pendingLiveDisplaySeekPositionMs = null
        pendingLiveDisplaySeekAtMs = 0L
        pendingInitialLiveEdgeSeek = false
        
        player?.let { it.stop(); it.clearMediaItems() }
        
        _playerState.value = _playerState.value.copy(
            currentVideoId = videoId, isBuffering = true, error = null,
            hasEnded = false, isPrepared = false, recoveryAttempted = false, currentQuality = 0,
            currentQualityKey = null,
            playWhenReady = player?.playWhenReady ?: true,
            isAtLiveEdge = false,
            liveDurationMs = 0L
        )
    }

    private fun updateLivePlaybackMode(isLive: Boolean, forceLiveSpeedReset: Boolean = false) {
        player?.setSeekParameters(if (isLive) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC)

        if (isLive == currentIsLiveStream) {
            if (isLive && forceLiveSpeedReset) {
                setPlaybackSpeed(1.0f)
            }
            _playerState.value = _playerState.value.copy(
                isLive = isLive,
                liveDurationMs = if (isLive) _playerState.value.liveDurationMs else 0L
            )
            return
        }

        if (isLive) {
            val currentSpeed = _playerState.value.playbackSpeed
            if (currentSpeed != 1.0f) {
                preLivePlaybackSpeed = currentSpeed
            }
            setPlaybackSpeed(1.0f)
        } else {
            preLivePlaybackSpeed?.let { speed ->
                if (speed != 1.0f) {
                    setPlaybackSpeed(speed)
                }
            }
            preLivePlaybackSpeed = null
        }

        currentIsLiveStream = isLive
        _playerState.value = _playerState.value.copy(
            isLive = isLive,
            isAtLiveEdge = false,
            liveDurationMs = if (isLive) _playerState.value.liveDurationMs else 0L
        )
    }

    private fun loadMediaInternal(
        videoStream: VideoStream?,
        audioStream: AudioStream?,
        preservePosition: Long? = null,
        localFilePath: String? = null,
        audioOnly: Boolean = false
    ): Boolean {
        if (audioOnly) {
            setVideoTracksDisabled(true)
        } else if (videoStream != null || localFilePath != null) {
            setVideoTracksDisabled(false)
        }

        if (localFilePath != null) {
            Log.d(TAG, "loadMediaInternal: Playing local file: $localFilePath")
            return mediaLoader?.loadMedia(
                player = player,
                context = appContext,
                videoStream = videoStream,
                audioStream = audioStream ?: availableAudioStreams.firstOrNull(),
                availableVideoStreams = availableVideoStreams,
                currentVideoStream = currentVideoStream,
                dashManifestUrl = null,
                hlsUrl = null,
                durationSeconds = currentDurationSeconds,
                currentDurationSeconds = currentDurationSeconds,
                preservePosition = preservePosition,
                localFilePath = localFilePath,
                audioOnly = false,
                subtitleStreams = availableSubtitles
            ) ?: false
        }

        val audio = audioStream ?: availableAudioStreams.firstOrNull()
        if (audioOnly && audio == null) {
            Log.w(TAG, "loadMediaInternal: audio-only load requested without an audio stream")
            return false
        }
        val hasPlayableVideo = videoStream != null ||
            currentVideoStream != null ||
            availableVideoStreams.isNotEmpty() ||
            !currentDashManifestUrl.isNullOrEmpty() ||
            !currentHlsUrl.isNullOrEmpty()
        if (audio == null && !hasPlayableVideo) {
            Log.w(TAG, "loadMediaInternal: no playable audio/video streams")
            return false
        }
        val sabr = currentSabrInfo
        val result = mediaLoader?.loadMedia(
            player = player,
            context = appContext,
            videoStream = videoStream,
            audioStream = audio,
            availableVideoStreams = availableVideoStreams,
            currentVideoStream = currentVideoStream,
            dashManifestUrl = currentDashManifestUrl,
            hlsUrl = currentHlsUrl,
            durationSeconds = currentDurationSeconds,
            currentDurationSeconds = currentDurationSeconds,
            preservePosition = preservePosition,
            localFilePath = localFilePath,
            audioOnly = audioOnly,
            subtitleStreams = availableSubtitles,
            sabrStreamingUrl = sabr?.streamingUrl,
            sabrVideoId = currentVideoId,
            sabrAudioItag = sabr?.audioItag ?: 0,
            sabrAudioLmt = sabr?.audioLmt ?: 0,
            sabrVideoItag = sabr?.videoItag ?: 0,
            sabrVideoLmt = sabr?.videoLmt ?: 0,
            sabrPoToken = sabr?.poToken.orEmpty(),
            sabrVisitorId = sabr?.visitorId.orEmpty(),
            sabrUstreamerConfig = sabr?.ustreamerConfig ?: ByteArray(0),
            sabrAudioMimeType = sabr?.audioMimeType.orEmpty(),
            sabrVideoMimeType = sabr?.videoMimeType.orEmpty(),
            innerTubeVideoFormats = innerTubeVideoFormats,
            innerTubeAudioFormats = innerTubeAudioFormats
        ) ?: false
        if (result) {
            qualityManager?.isDashSource = !currentDashManifestUrl.isNullOrEmpty()
        }
        return result
    }

    private fun setVideoTracksDisabled(disabled: Boolean) {
        if (videoTracksDisabled == disabled) return
        videoTracksDisabled = disabled
        trackSelector?.let { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disabled)
                    .build()
            )
        }
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
            startPlaybackFromQueue(video, loadStreamsInPlayer = false)
        }
    }

    fun playNext(loadStreamsInPlayer: Boolean = true): Boolean {
        if (currentQueueIndex < playbackQueue.size - 1) {
            currentQueueIndex++
            _currentQueueIndex.value = currentQueueIndex
            startPlaybackFromQueue(playbackQueue[currentQueueIndex], loadStreamsInPlayer)
            updateQueueState()
            return true
        }
        return false
    }

    fun playPrevious(loadStreamsInPlayer: Boolean = true): Boolean {
        if (currentQueueIndex > 0) {
            currentQueueIndex--
            _currentQueueIndex.value = currentQueueIndex
            startPlaybackFromQueue(playbackQueue[currentQueueIndex], loadStreamsInPlayer)
            updateQueueState()
            return true
        }
        return false
    }

    fun hasNext(): Boolean = currentQueueIndex < playbackQueue.size - 1

    fun hasPrevious(): Boolean = currentQueueIndex > 0 || (player?.currentPosition ?: 0) > 3000

    /**
     * Returns true if there is an active queue with at least one video.
     */
    fun hasActiveQueue(): Boolean = playbackQueue.isNotEmpty()

    /**
     * Insert [video] immediately after the current position (Play Next).
     * If no queue is active but a video is currently playing, silently build a
     * [currentVideo, video] queue starting at index 0 — no playback interruption.
     * If nothing is playing at all, start the video immediately.
     */
    fun addVideoToQueueNext(video: Video) {
        if (playbackQueue.isEmpty()) {
            val current = GlobalPlayerState.currentVideo.value
            if (current != null) {
                playbackQueue = listOf(current, video)
                currentQueueIndex = 0
                _queueVideos.value = playbackQueue
                _currentQueueIndex.value = 0
                updateQueueState()
            } else {
                setQueue(listOf(video), 0)
            }
            return
        }
        val insertAt = currentQueueIndex + 1
        val mutableQueue = playbackQueue.toMutableList()
        mutableQueue.add(insertAt, video)
        playbackQueue = mutableQueue
        _queueVideos.value = mutableQueue
        updateQueueState()
    }

    /**
     * Append [video] to the end of the current queue.
     * If no queue is active but a video is currently playing, silently build a
     * [currentVideo, video] queue starting at index 0 — no playback interruption.
     * If nothing is playing at all, start the video immediately.
     */
    fun addVideoToQueue(video: Video) {
        if (playbackQueue.isEmpty()) {
            val current = GlobalPlayerState.currentVideo.value
            if (current != null) {
                playbackQueue = listOf(current, video)
                currentQueueIndex = 0
                _queueVideos.value = playbackQueue
                _currentQueueIndex.value = 0
                updateQueueState()
            } else {
                setQueue(listOf(video), 0)
            }
            return
        }
        val mutableQueue = playbackQueue.toMutableList()
        mutableQueue.add(video)
        playbackQueue = mutableQueue
        _queueVideos.value = mutableQueue
        updateQueueState()
    }
    
    fun playVideoAtIndex(index: Int) {
        if (index in playbackQueue.indices && index != currentQueueIndex) {
            currentQueueIndex = index
            _currentQueueIndex.value = currentQueueIndex
            startPlaybackFromQueue(playbackQueue[currentQueueIndex], loadStreamsInPlayer = true)
            updateQueueState()
        }
    }

    private fun startPlaybackFromQueue(video: Video, loadStreamsInPlayer: Boolean) {
        // Reset player state for new video
        resetPlaybackStateForNewVideo(video.id)
        
        _playerState.value = _playerState.value.copy(
            currentVideoId = video.id,
            isPlaying = true,
            playWhenReady = true,
            isBuffering = true
        )
        
        GlobalPlayerState.setCurrentVideo(video)
        startBackgroundService(
            videoId = video.id,
            title = video.title,
            channel = video.channelName,
            thumbnail = video.thumbnailUrl
        )
        if (loadStreamsInPlayer) {
            playVideoFromServiceLayer(video, reason = "queue-advance")
        }
    }

    private fun updateQueueState() {
        _playerState.value = _playerState.value.copy(
            hasNext = hasNext(),
            hasPrevious = hasPrevious(),
            queueTitle = queueTitle,
            queueSize = playbackQueue.size
        )
    }

    fun setAutoplayCandidates(sourceVideoId: String, videos: List<Video>, enabled: Boolean = autoplayEnabled) {
        autoplayEnabled = enabled
        autoplaySourceVideoId = sourceVideoId
        autoplayCandidates = videos
            .filter { it.id.isNotBlank() && it.id != sourceVideoId && !it.isLive && !it.isUpcoming }
            .distinctBy { it.id }
        Log.d(TAG, "Autoplay candidates for $sourceVideoId: ${autoplayCandidates.size}, enabled=$enabled")
    }

    private fun playNextAutoplayCandidate(): Boolean {
        if (!autoplayEnabled || autoplayJob?.isActive == true || _playerState.value.isLooping) return false

        val nextVideo = autoplayCandidates.firstOrNull() ?: return false

        autoplayCandidates = autoplayCandidates.drop(1)
        playVideoFromServiceLayer(nextVideo, reason = "related-autoplay")
        return true
    }

    private fun playVideoFromServiceLayer(video: Video, reason: String) {
        val context = appContext ?: return
        autoplayJob?.cancel()
        autoplayJob = scope.launch {
            Log.d(TAG, "Service-layer playback start: ${video.id} ($reason)")
            try {
                initialize(context)
                GlobalPlayerState.setCurrentVideo(video)
                startBackgroundService(
                    videoId = video.id,
                    title = video.title,
                    channel = video.channelName,
                    thumbnail = video.thumbnailUrl
                )
                _playerState.value = _playerState.value.copy(
                    currentVideoId = video.id,
                    isBuffering = true,
                    isPlaying = false,
                    playWhenReady = true,
                    hasEnded = false,
                    error = null
                )

                val extractionDeferred = async(Dispatchers.IO) {
                    try {
                        withTimeoutOrNull(25000L) {
                            io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.extract(video.id)
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (_: Exception) { null }
                }

                val streamInfo = fetchStreamInfoForPlayback(video.id) ?: run {
                    _playerState.value = _playerState.value.copy(
                        isBuffering = false,
                        error = "Unable to load next video"
                    )
                    return@launch
                }

                val extraction = extractionDeferred.await()
                val sabrInfo = extraction?.sabrInfo
                val enrichedVideo = videoFromStreamInfo(video.id, streamInfo, fallback = video)
                GlobalPlayerState.setCurrentVideo(enrichedVideo)
                startBackgroundService(
                    videoId = enrichedVideo.id,
                    title = enrichedVideo.title,
                    channel = enrichedVideo.channelName,
                    thumbnail = enrichedVideo.thumbnailUrl
                )
                setAutoplayCandidates(
                    sourceVideoId = enrichedVideo.id,
                    videos = relatedVideosFromStreamInfo(streamInfo),
                    enabled = autoplayEnabled
                )

                val prefs = PlayerPreferences(context)
                val preferredQuality = if (isOnWifi(context)) {
                    prefs.defaultQualityWifi.first()
                } else {
                    prefs.defaultQualityCellular.first()
                }
                val preferredAudioLanguage = prefs.preferredAudioLanguage.first()
                val selected = selectStreamsForServicePlayback(streamInfo, preferredQuality, preferredAudioLanguage)
                setStreams(
                    videoId = enrichedVideo.id,
                    videoStream = selected.first,
                    audioStream = selected.second,
                    videoStreams = (streamInfo.videoStreams + (streamInfo.videoOnlyStreams ?: emptyList()))
                        .filterIsInstance<VideoStream>(),
                    audioStreams = streamInfo.audioStreams,
                    subtitles = streamInfo.subtitles ?: emptyList(),
                    durationSeconds = streamInfo.duration,
                    dashManifestUrl = streamInfo.dashMpdUrl,
                    hlsUrl = streamInfo.hlsUrl,
                    streamType = streamInfo.streamType,
                    startPosition = 0L,
                    sabrInfo = sabrInfo,
                    itVideoFormats = extraction?.videoFormats ?: emptyList(),
                    itAudioFormats = extraction?.audioFormats ?: emptyList()
                )
                play()
            } catch (e: CancellationException) {
                Log.d(TAG, "Service-layer playback cancelled for ${video.id} ($reason)")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Service-layer playback failed for ${video.id}", e)
                _playerState.value = _playerState.value.copy(
                    isBuffering = false,
                    error = e.message ?: "Unable to load next video"
                )
            } finally {
                autoplayJob = null
            }
        }
    }

    private suspend fun fetchStreamInfoForPlayback(videoId: String): StreamInfo? = withContext(Dispatchers.IO) {
        var lastError: Throwable? = null
        repeat(3) { attempt ->
            val info = try {
                val url = if (attempt == 1) {
                    "https://youtu.be/$videoId"
                } else {
                    "https://www.youtube.com/watch?v=$videoId"
                }
                withTimeoutOrNull(12_000L) {
                    StreamInfo.getInfo(ServiceList.YouTube, url)
                }
            } catch (e: Exception) {
                lastError = e
                null
            }
            if (info != null) return@withContext info
            if (attempt < 2) delay((attempt + 1) * 300L)
        }
        Log.e(TAG, "Failed to fetch stream info for $videoId", lastError)
        null
    }

    private fun selectStreamsForServicePlayback(
        streamInfo: StreamInfo,
        preferredQuality: VideoQuality,
        preferredAudioLanguage: String
    ): Pair<VideoStream?, AudioStream?> {
        val audioCandidates = streamInfo.audioStreams
            .distinctBy { it.url ?: it.content }
            .sortedByDescending { it.bitrate }

        val audioStream = when (preferredAudioLanguage) {
            "original", "" -> audioCandidates.firstOrNull {
                it.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
            } ?: audioCandidates.firstOrNull {
                it.audioTrackType != org.schabi.newpipe.extractor.stream.AudioTrackType.DUBBED
            } ?: audioCandidates.firstOrNull()
            else -> audioCandidates.firstOrNull { audio ->
                val lang = audio.audioLocale?.language ?: ""
                lang.startsWith(preferredAudioLanguage, ignoreCase = true)
            } ?: audioCandidates.firstOrNull {
                it.audioTrackType == org.schabi.newpipe.extractor.stream.AudioTrackType.ORIGINAL
            } ?: audioCandidates.firstOrNull()
        }

        val videoStreams = (streamInfo.videoStreams + (streamInfo.videoOnlyStreams ?: emptyList()))
            .filterIsInstance<VideoStream>()
            .filter {
                val mime = it.format?.mimeType
                mime?.contains("mp4", ignoreCase = true) == true ||
                    mime?.contains("webm", ignoreCase = true) == true
            }

        val selectedVideoStream = when (preferredQuality) {
            VideoQuality.AUTO -> null
            else -> videoStreams.minByOrNull {
                kotlin.math.abs(
                    QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) - preferredQuality.height
                )
            }
        }
        val videoStream = if (audioStream == null && selectedVideoStream == null) {
            videoStreams
                .sortedWith(
                    compareBy<VideoStream> { if (it.isVideoOnly) 1 else 0 }
                        .thenByDescending { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) }
                        .thenBy { VideoCodecUtils.playbackCodecRank(it) }
                        .thenByDescending { it.bitrate }
                )
                .firstOrNull()
        } else {
            selectedVideoStream
        }
        return videoStream to audioStream
    }

    private fun relatedVideosFromStreamInfo(info: StreamInfo): List<Video> =
        info.relatedItems.filterIsInstance<StreamInfoItem>().mapNotNull { item ->
            runCatching { item.toFlowVideo() }.getOrNull()
        }

    private fun videoFromStreamInfo(videoId: String, info: StreamInfo, fallback: Video): Video {
        val thumbnail = info.thumbnails
            .sortedByDescending { it.height }
            .map { it.url }
            .firstOrNull()
            .let { ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, it) }
        return fallback.copy(
            title = info.name?.takeIf { it.isNotBlank() } ?: fallback.title,
            channelName = info.uploaderName?.takeIf { it.isNotBlank() } ?: fallback.channelName,
            channelId = extractChannelId(info.uploaderUrl).ifBlank { fallback.channelId },
            thumbnailUrl = thumbnail.ifBlank { fallback.thumbnailUrl },
            duration = info.duration.toInt().takeIf { it > 0 } ?: fallback.duration,
            viewCount = info.viewCount.takeIf { it > 0L } ?: fallback.viewCount,
            uploadDate = info.textualUploadDate ?: fallback.uploadDate,
            description = info.description?.content ?: fallback.description,
            tags = info.tags ?: fallback.tags
        )
    }

    private fun StreamInfoItem.toFlowVideo(): Video {
        val rawUrl = url ?: ""
        val videoId = when {
            rawUrl.contains("watch?v=") -> rawUrl.substringAfter("watch?v=").substringBefore("&")
            rawUrl.contains("youtu.be/") -> rawUrl.substringAfter("youtu.be/").substringBefore("?")
            rawUrl.contains("/shorts/") -> rawUrl.substringAfter("/shorts/").substringBefore("?")
            else -> rawUrl.substringAfterLast("/")
        }.trim()
        if (videoId.isBlank()) throw IllegalArgumentException("Blank related video id")

        val bestThumbnail = thumbnails
            .sortedByDescending { it.height }
            .map { it.url }
            .firstOrNull()
            .let { ThumbnailUrlResolver.normalizeVideoThumbnail(videoId, it) }

        val isShortUrl = rawUrl.contains("/shorts/")
        val isLiveStream = streamType == StreamType.LIVE_STREAM
        val durationSecs = when {
            isLiveStream -> 0
            duration > 0 -> duration.toInt()
            isShortUrl -> 60
            else -> 0
        }
        val nameLower = name?.lowercase() ?: ""
        val uploaderLower = uploaderName?.lowercase() ?: ""
        val isMusicCandidate = uploaderLower.contains("vevo") ||
            uploaderLower.contains(" - topic") ||
            nameLower.contains("official music video") ||
            nameLower.contains("official video") ||
            nameLower.contains("official audio") ||
            nameLower.contains("(official)")

        return Video(
            id = videoId,
            title = name ?: "Unknown Title",
            channelName = uploaderName ?: "Unknown Channel",
            channelId = extractChannelId(uploaderUrl),
            thumbnailUrl = bestThumbnail,
            duration = durationSecs,
            viewCount = viewCount,
            uploadDate = textualUploadDate ?: "Unknown",
            channelThumbnailUrl = uploaderAvatars.sortedByDescending { it.height }.firstOrNull()?.url ?: "",
            isUpcoming = streamType == StreamType.NONE,
            isLive = isLiveStream,
            isShort = isShortUrl,
            isMusic = isMusicCandidate
        )
    }

    private fun extractChannelId(url: String?): String =
        url?.substringAfterLast("/")?.takeIf { it.isNotBlank() && it != url } ?: ""

    private fun isOnWifi(context: Context): Boolean {
        return try {
            val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val caps = manager.getNetworkCapabilities(manager.activeNetwork)
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: true
        } catch (e: Exception) {
            true
        }
    }

    // ===== Playback Controls =====
    
    fun play() = player?.play()
    fun pause() = player?.pause()
    fun seekTo(position: Long) {
        val p = player ?: return
        val isLive = currentIsLiveStream || p.isCurrentMediaItemLive
        val target = resolveSeekTarget(p, position)
        if (isLive) {
            p.setSeekParameters(SeekParameters.EXACT)
            markLiveDisplaySeek(target)
        }
        p.seekTo(target)
        if (isLive) {
            updateLiveEdgeState(p)
        }
    }

    fun seekToLiveTimeline(position: Long) {
        val p = player ?: return
        val isLive = currentIsLiveStream || p.isCurrentMediaItemLive
        val target = resolveSeekTarget(p, position)
        if (isLive) {
            p.setSeekParameters(SeekParameters.EXACT)
            markLiveDisplaySeek(target)
        }
        p.seekTo(target)
        if (isLive) {
            updateLiveEdgeState(p)
        }
    }

    private fun resolveSeekTarget(player: ExoPlayer, requestedPositionMs: Long): Long {
        val playerDuration = player.duration
            .takeIf { it > 0L && it != C.TIME_UNSET }
            ?: return requestedPositionMs.coerceAtLeast(0L)

        return requestedPositionMs.coerceIn(0L, playerDuration)
    }

    fun seekToLiveEdge(resetSpeed: Boolean = true) {
        val p = player ?: return
        if (resetSpeed) {
            setPlaybackSpeed(1.0f)
        }
        if (p.currentMediaItem != null) {
            p.seekToDefaultPosition(p.currentMediaItemIndex)
        } else {
            p.seekToDefaultPosition()
        }
        val liveEdgePosition = p.duration
            .takeIf { it > 0L && it != C.TIME_UNSET }
            ?: p.currentPosition.coerceAtLeast(0L)
        markLiveDisplaySeek(liveEdgePosition)
        updateLiveEdgeState(p)
    }

    private fun markLiveDisplaySeek(positionMs: Long) {
        pendingLiveDisplaySeekPositionMs = positionMs.coerceAtLeast(0L)
        pendingLiveDisplaySeekAtMs = SystemClock.elapsedRealtime()
    }

    fun consumeRecentLiveDisplaySeek(maxAgeMs: Long = 2_000L): Long? {
        val position = pendingLiveDisplaySeekPositionMs ?: return null
        if (SystemClock.elapsedRealtime() - pendingLiveDisplaySeekAtMs > maxAgeMs) {
            pendingLiveDisplaySeekPositionMs = null
            return null
        }
        pendingLiveDisplaySeekPositionMs = null
        return position
    }

    fun setScrubbingModeEnabled(enabled: Boolean) {
        player?.let { p ->
            val isLive = currentIsLiveStream || p.isCurrentMediaItemLive
            p.setSeekParameters(
                if (enabled || isLive) SeekParameters.EXACT else SeekParameters.CLOSEST_SYNC
            )
        }
    }

    fun replay() {
        player?.let { exoPlayer ->
            if (exoPlayer.currentMediaItem != null) {
                exoPlayer.seekToDefaultPosition(exoPlayer.currentMediaItemIndex)
            } else {
                exoPlayer.seekTo(0L)
            }
            _playerState.value = _playerState.value.copy(hasEnded = false)
            exoPlayer.play()
        }
    }
    
    fun toggleLoop(enabled: Boolean) {
        manualLoopEnabled = enabled
        updateEffectiveLoopState()
    }

    private fun updateEffectiveLoopState() {
        _playerState.value = _playerState.value.copy(isLooping = manualLoopEnabled || globalLoopEnabled)
    }
    
    fun stop() {
        autoplayJob?.cancel()
        autoplayJob = null
        isAudioOnlyMode = false
        pendingInitialLiveEdgeSeek = false
        setVideoTracksDisabled(false)
        updateLivePlaybackMode(isLive = false)
        playbackTracker?.stop()
        player?.stop()
        player?.clearMediaItems()
        qualityManager?.resetForNewVideo()
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            playWhenReady = false,
            isBuffering = false,
            isPrepared = false,
            hasEnded = false,
            currentVideoId = null,
            liveDurationMs = 0L
        )
    }

    fun getPlayer(): ExoPlayer? = player
    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L
    fun getDuration(): Long = player?.duration ?: 0L
    fun isPlaying(): Boolean = player?.isPlaying ?: false
    fun isPreparedForPlayback(videoId: String): Boolean {
        val p = player ?: return false
        val state = _playerState.value
        return state.currentVideoId == videoId &&
            state.isPrepared &&
            p.currentMediaItem != null &&
            p.playbackState != Player.STATE_IDLE
    }

    // ===== Quality & Audio Management =====
    
    fun switchQualityByHeight(height: Int) = qualityManager?.switchQualityByHeight(height, player?.currentPosition ?: 0L)
    fun switchQuality(height: Int) = switchQualityByHeight(height)
    fun switchQuality(option: QualityOption) = qualityManager?.switchQuality(option, player?.currentPosition ?: 0L)
    
    fun switchAudioTrack(index: Int) {
        if (index in availableAudioStreams.indices) {
            currentAudioStream = availableAudioStreams[index]
            val position = player?.currentPosition ?: 0L
            val wasPlaying = player?.isPlaying ?: false
            if (isAudioOnlyMode) {
                loadMediaInternal(null, currentAudioStream, audioOnly = true)
            } else {
                loadMediaInternal(currentVideoStream, currentAudioStream)
            }
            player?.seekTo(position)
            if (wasPlaying) player?.play()
            _playerState.value = _playerState.value.copy(currentAudioTrack = index)
        }
    }

    fun selectSubtitle(index: Int?) {
        val resolvedIndex = index?.takeIf { it in availableSubtitles.indices }
        if (selectedSubtitleIndex != resolvedIndex) {
            selectedSubtitleIndex = resolvedIndex
            Log.d(TAG, "Subtitle selected: $resolvedIndex")
            applySubtitleTrackSelection()
        }
    }

    private fun applySubtitleTrackSelection() {
        val selector = trackSelector ?: return
        val index = selectedSubtitleIndex
        if (index == null) {
            disableTextTracks()
            return
        }

        val subtitleId = MediaLoader.subtitleTrackId(index)
        val textTrackGroup = player?.currentTracks?.groups
            ?.asSequence()
            ?.filter { it.type == C.TRACK_TYPE_TEXT }
            ?.firstOrNull { group ->
                (0 until group.length).any { trackIndex ->
                    group.getTrackFormat(trackIndex).id == subtitleId
                }
            }

        if (textTrackGroup == null) {
            val subtitle = availableSubtitles.getOrNull(index)
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setPreferredTextLanguage(subtitle?.languageTag ?: subtitle?.locale?.toLanguageTag())
                    .build()
            )
            return
        }

        val mediaTrackGroup = textTrackGroup.getMediaTrackGroup()
        val trackIndex = (0 until textTrackGroup.length).firstOrNull { groupTrackIndex ->
            textTrackGroup.getTrackFormat(groupTrackIndex).id == subtitleId
        } ?: 0

        selector.setParameters(
            selector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .setOverrideForType(TrackSelectionOverride(mediaTrackGroup, trackIndex))
                .build()
        )
    }

    private fun disableTextTracks() {
        trackSelector?.let { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setPreferredTextLanguage(null)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            )
        }
    }

    // ===== Audio Features =====
    
    fun setPlaybackSpeed(speed: Float) = audioFeaturesManager?.setPlaybackSpeed(player, speed)
    fun toggleSkipSilence(isEnabled: Boolean) = audioFeaturesManager?.toggleSkipSilence(isEnabled, appContext)

    fun toggleStableVolume(isEnabled: Boolean) = audioFeaturesManager?.toggleStableVolume(isEnabled, appContext)
    
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

    val sbMuteEvent: SharedFlow<Boolean>
        get() = sponsorBlockHandler?.muteEvent ?: MutableSharedFlow()

    val sbToastEvent: SharedFlow<SponsorBlockSegment>
        get() = sponsorBlockHandler?.toastEvent ?: MutableSharedFlow()

    val sbCategoryActions: Map<String, SponsorBlockAction>
        get() = sponsorBlockHandler?.categoryActions ?: emptyMap()

    // ===== Surface Management =====

    fun attachVideoSurface(holder: SurfaceHolder?, forceAttach: Boolean = false): Boolean? {
        val attached = surfaceManager?.attachVideoSurface(holder, player, forceAttach)
        if (attached == true) {
            val p = player
            if (p != null && currentVideoStream != null) {
                if (isAudioOnlyMode) {
                    Log.d(TAG, "attachVideoSurface: was in audio-only mode — restoring video stream")
                    restoreVideoOutput()
                } else if (p.currentMediaItem == null) {
                    Log.d(TAG, "attachVideoSurface: no media item — loading media now")
                    loadMediaInternal(currentVideoStream, currentAudioStream)
                } else if (p.playbackState == Player.STATE_IDLE) {
                    Log.d(TAG, "attachVideoSurface: surface back and player IDLE — calling prepare()")
                    p.prepare()
                    if (p.playWhenReady) p.play()
                }
            }
        }
        return attached
    }
    fun detachVideoSurface(holder: SurfaceHolder? = null) = surfaceManager?.detachVideoSurface(holder, player, appContext)
    fun clearSurface() = surfaceManager?.clearSurface(player)
    suspend fun awaitSurfaceReady(timeoutMillis: Long = 1000) = surfaceManager?.awaitSurfaceReady(timeoutMillis) ?: false

    
    fun continueVideoPlaybackInBackground() {
        switchToAudioOnly()
        val p = player
        if (p?.playWhenReady == true && !p.isPlaying && p.playbackState != Player.STATE_ENDED) {
            p.play()
        }
    }

    
    fun switchToAudioOnly() {
        val p = player ?: return
        isAudioOnlyMode = true
        setVideoTracksDisabled(true)
        if (p.playWhenReady && !p.isPlaying && p.playbackState != Player.STATE_ENDED) {
            p.play()
        }
    }

    fun restoreVideoOutput() {
        val p = player ?: return
        isAudioOnlyMode = false
        setVideoTracksDisabled(false)
        if (p.playWhenReady && !p.isPlaying && p.playbackState != Player.STATE_ENDED) {
            p.play()
        }
    }

    fun isInAudioOnlyMode(): Boolean = isAudioOnlyMode

    fun isVideoSurfaceRestorePending(): Boolean = videoSurfaceRestorePending

    fun markVideoSurfaceRestored() {
        videoSurfaceRestorePending = false
    }
    
    fun setSurfaceReady(ready: Boolean) {
        surfaceManager?.setSurfaceReady(ready)
        if (ready) {
            val p = player
            if (p != null && currentVideoStream != null) {
                when {
                    p.currentMediaItem == null -> {
                        Log.d(TAG, "setSurfaceReady: no media item yet, loading media")
                        loadMediaInternal(currentVideoStream, currentAudioStream)
                    }
                    p.playbackState == Player.STATE_IDLE -> {
                        Log.d(TAG, "setSurfaceReady: player idle, calling prepare() to recover")
                        p.prepare()
                        if (p.playWhenReady) p.play()
                    }
                }
            }
        }
    }
    
    fun retryLoadMediaIfSurfaceReady() {
        if (isSurfaceReady && currentVideoStream != null) {
            loadMediaInternal(currentVideoStream, currentAudioStream)
        }
    }

    // ===== Cache & Background Service =====
    
    fun getCacheSize(): Long = cacheManager?.getCacheSize() ?: 0L
    fun clearCache() = cacheManager?.clearCache()
    fun clearCacheForCurrentVideo() {
        Log.d(TAG, "Clearing media cache due to persistent stream errors")
        cacheManager?.clearCache()
    }
    
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
        isAudioOnlyMode = false
        setVideoTracksDisabled(false)
        updateLivePlaybackMode(isLive = false)
        mediaLoader?.releaseSabr()
        player?.stop()
        player?.clearMediaItems()
        qualityManager?.resetForNewVideo()
        currentVideoId = null
        currentVideoStream = null
        currentAudioStream = null
        _playerState.value = _playerState.value.copy(
            isPlaying = false, currentVideoId = null, currentQuality = 0,
            currentQualityKey = null,
            bufferedPercentage = 0f, isBuffering = false, isPrepared = false, hasEnded = false,
            isLive = false, isAtLiveEdge = false, liveDurationMs = 0L
        )
    }

    fun clearAll() {
        clearCurrentVideo()
        manualLoopEnabled = false
        autoplayCandidates = emptyList()
        autoplaySourceVideoId = null
        playbackQueue = emptyList()
        currentQueueIndex = -1
        _queueVideos.value = emptyList()
        _currentQueueIndex.value = -1
        queueTitle = null
        _playerState.value = _playerState.value.copy(
            hasNext = false, hasPrevious = false, queueTitle = null, queueSize = 0,
            isLooping = globalLoopEnabled
        )
    }

    fun isQueueActive(): Boolean = playbackQueue.isNotEmpty()

    private fun updateLiveEdgeState(player: ExoPlayer) {
        if (!currentIsLiveStream && !player.isCurrentMediaItemLive) return
        val atLiveEdge = isPlayerAtLiveEdge(player)
        if (_playerState.value.isAtLiveEdge != atLiveEdge || !_playerState.value.isLive) {
            _playerState.value = _playerState.value.copy(
                isLive = true,
                isAtLiveEdge = atLiveEdge
            )
        }
    }

    private fun isPlayerAtLiveEdge(player: ExoPlayer): Boolean {
        if (!currentIsLiveStream && !player.isCurrentMediaItemLive) return false

        val liveOffset = player.currentLiveOffset
        if (liveOffset != C.TIME_UNSET && liveOffset > 0L) {
            return liveOffset <= PlayerConfig.LIVE_EDGE_GAP_MS + LIVE_EDGE_THRESHOLD_MS
        }

        val timeline = player.currentTimeline
        val windowIndex = player.currentMediaItemIndex
        if (!timeline.isEmpty && windowIndex >= 0 && windowIndex < timeline.windowCount) {
            val window = Timeline.Window()
            timeline.getWindow(windowIndex, window)
            val defaultPosition = window.defaultPositionMs
            if (defaultPosition != C.TIME_UNSET) {
                return player.currentPosition + LIVE_EDGE_THRESHOLD_MS >= defaultPosition
            }
        }

        val duration = player.duration
        return duration > 0L && duration != C.TIME_UNSET &&
            duration - player.currentPosition <= LIVE_EDGE_THRESHOLD_MS
    }

    fun release() {
        Log.d(TAG, "release() called")
        pendingReloadJob?.cancel()
        pendingReloadJob = null
        mediaLoader?.releaseSabr()
        playbackTracker?.stop()
        audioFeaturesManager?.clearPlayer()
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
        val audio = currentAudioStream ?: availableAudioStreams.firstOrNull()
        val pos = preservePosition ?: player?.currentPosition ?: 0L
        Log.d(TAG, "Reloading ${VideoCodecUtils.qualityHeightFromStream(video)}p at ${pos}ms ($reason)")
        player?.stop()
        player?.clearMediaItems()
        loadMediaInternal(video, audio, pos)
    }
    
    private fun reloadPlaybackManager() {
        pendingReloadJob?.cancel()
        pendingReloadJob = scope.launch {
            try {
                delay(PlayerConfig.ERROR_RETRY_DELAY_MS)

                val pos = player?.currentPosition ?: 0L
                player?.stop()
                player?.clearMediaItems()

                if (qualityManager?.isAdaptiveQualityEnabled == false) {
                    reloadCurrentStream(pos, "manual-quality-reload")
                    return@launch
                }

                currentVideoStream?.let { stream ->
                    if (qualityManager?.hasStreamFailed(stream.getContent()) == true) {
                        val working = qualityManager?.getWorkingStreams()?.maxByOrNull {
                            VideoCodecUtils.qualityHeightFromStream(it)
                        }
                        if (working != null) {
                            currentVideoStream = working
                            qualityManager?.resetStreamErrors()
                        } else {
                            onPlaybackShutdown()
                            return@launch
                        }
                    }
                }

                currentVideoStream?.let { loadMediaInternal(it, currentAudioStream, pos) }
            } catch (e: Exception) {
                Log.e(TAG, "Error reloading", e)
                onPlaybackShutdown()
            } finally {
                pendingReloadJob = null
            }
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

    /**
     * Called by [PlaybackRefocusEffect] when the player is stuck in an unrecoverable state
     * after a screen-off/on cycle (duration still 0 after all poll attempts).
     */
    fun handleRefocusStuck(videoId: String?) {
        val p = player ?: return
        errorHandler?.handleRefocusStuck(p, videoId)
    }
}

// Backward compatibility type aliases
typealias EnhancedPlayerState = io.github.aedev.flow.player.state.EnhancedPlayerState
typealias QualityOption = io.github.aedev.flow.player.state.QualityOption
typealias AudioTrackOption = io.github.aedev.flow.player.state.AudioTrackOption
typealias SubtitleOption = io.github.aedev.flow.player.state.SubtitleOption
