package io.github.aedev.flow.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.SurfaceHolder
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
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
import io.github.aedev.flow.player.analytics.PlaybackAnalyticsLogger
import io.github.aedev.flow.player.cache.PlayerCacheManager
import io.github.aedev.flow.player.config.PlayerConfig
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.error.PlayerErrorHandler
import io.github.aedev.flow.player.factory.PlayerFactory
import io.github.aedev.flow.player.media.MediaLoader
import io.github.aedev.flow.player.quality.QualityManager
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.YouTubeClient
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.player.sabr.integration.SabrStreamInfo
import io.github.aedev.flow.player.sabr.integration.SabrUrlResolver
import io.github.aedev.flow.player.service.BackgroundServiceManager
import io.github.aedev.flow.player.sponsorblock.SponsorBlockHandler
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.player.state.QualityOption
import io.github.aedev.flow.player.stream.StreamMergeUtils
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
import kotlinx.coroutines.coroutineScope
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
        private const val SABR_QUALITY_KEY_PREFIX = "sabr:"
        private const val LIVE_QUALITY_KEY_PREFIX = "live:"
        private const val PRELOAD_RETRY_DELAY_MS = 10_000L
        private const val MAX_PRELOAD_RETRIES = 3
        private const val AUTO_NEXT_TAG = "FlowVideoAutoNext"
        private val QUALITY_HEIGHT_REGEX = Regex("""(\d+)p""")
        
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
    private var liveQualityHeights: List<Int> = emptyList()

    private var pendingLiveQualityHeight: Int = 0
    private var lastLiveEdgeRecoveryMs = 0L
    private var preLivePlaybackSpeed: Float? = null
    private var pendingLiveDisplaySeekPositionMs: Long? = null
    private var pendingLiveDisplaySeekAtMs: Long = 0L
    private var pendingInitialLiveEdgeSeek = false
    
    private var currentSabrInfo: SabrStreamInfo? = null
    private var sabrPreferred = false

    private var isAudioOnlyMode = false
    private var videoTracksDisabled = false
    @Volatile private var videoSurfaceRestorePending = false
    @Volatile private var videoReprimePending = false

    // Queue management
    private var playbackQueue: List<io.github.aedev.flow.data.model.Video> = emptyList()
    private var currentQueueIndex: Int = -1
    private var queueTitle: String? = null
    private var manualLoopEnabled: Boolean = false
    private var globalLoopEnabled: Boolean = false
    @Volatile private var autoplayEnabled: Boolean = true
    @Volatile private var queueAutoplayEnabled: Boolean = true
    private var autoplayCandidates: List<Video> = emptyList()
    private var autoplaySourceVideoId: String? = null
    private var autoplayJob: Job? = null

    @Volatile private var autoplayCountdownSeconds: Int = 0
    private var autoplayCountdownJob: Job? = null
    private val _autoplayCountdown = MutableStateFlow(AutoplayCountdownState())
    val autoplayCountdown: StateFlow<AutoplayCountdownState> = _autoplayCountdown.asStateFlow()
    
    // Application context
    private var appContext: Context? = null
    
    // Coroutine scope
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingReloadJob: Job? = null
    private var advanceWakeLock: PowerManager.WakeLock? = null

    private fun isOnMainThread(): Boolean = Looper.myLooper() == Looper.getMainLooper()

    private fun isDisplayInteractive(): Boolean =
        appContext?.let { context ->
            runCatching {
                val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                pm.isInteractive
            }.getOrDefault(true)
        } ?: true

    private fun acquireAdvanceWakeLock() {
        val ctx = appContext ?: return
        try {
            if (advanceWakeLock == null) {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
                advanceWakeLock = pm.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "Flow:AutoAdvanceWakeLock"
                ).apply { setReferenceCounted(false) }
            }
            if (advanceWakeLock?.isHeld != true) {
                advanceWakeLock?.acquire(60_000L)
                Log.d(TAG, "Advance wake lock acquired")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to acquire advance wake lock", e)
        }
    }

    private fun releaseAdvanceWakeLock() {
        try {
            if (advanceWakeLock?.isHeld == true) {
                advanceWakeLock?.release()
                Log.d(TAG, "Advance wake lock released")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release advance wake lock", e)
        }
    }

    private fun playerStateName(state: Int?): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        null -> "NO_PLAYER"
        else -> "UNKNOWN($state)"
    }

    private fun autoNextSnapshot(): String {
        if (!isOnMainThread()) {
            val nextTarget = runCatching { nextPreloadTarget()?.first?.id }.getOrNull()
            return "thread=${Thread.currentThread().name} main=false playerSnapshot=skipped " +
                "stateVideo=${_playerState.value.currentVideoId} managerVideo=$currentVideoId " +
                "queue=$currentQueueIndex/${playbackQueue.size} hasNext=${hasNext()} " +
                "autoplay=$autoplayEnabled candidates=${autoplayCandidates.size} target=$nextTarget " +
                "preloaded=${preloadedNext?.data?.enrichedVideo?.id} " +
                "attempt=$preloadAttemptVideoId->$preloadAttemptNextVideoId retry=$preloadRetryCount"
        }
        val p = player
        val nextTarget = runCatching { nextPreloadTarget()?.first?.id }.getOrNull()
        return "video=$currentVideoId exo=${playerStateName(p?.playbackState)} " +
            "pwr=${p?.playWhenReady} playing=${p?.isPlaying} " +
            "pos=${p?.currentPosition}/${p?.duration} idx=${p?.currentMediaItemIndex} count=${p?.mediaItemCount} " +
            "queue=$currentQueueIndex/${playbackQueue.size} hasNext=${hasNext()} " +
            "autoplay=$autoplayEnabled candidates=${autoplayCandidates.size} target=$nextTarget " +
            "preloaded=${preloadedNext?.data?.enrichedVideo?.id} " +
            "attempt=$preloadAttemptVideoId->$preloadAttemptNextVideoId retry=$preloadRetryCount " +
            "audioOnly=$isAudioOnlyMode tracksDisabled=$videoTracksDisabled surface=$isSurfaceReady live=$currentIsLiveStream"
    }

    private fun autoNextLog(message: String) {
        val full = "$message | ${autoNextSnapshot()}"
        Log.w(AUTO_NEXT_TAG, full)
        PlayerDiagnostics.logWarning(AUTO_NEXT_TAG, full)
    }

    @Volatile private var videoMediaSession: MediaSession? = null

    fun getVideoMediaSession(): MediaSession? = videoMediaSession

    private fun initializeVideoMediaSession(context: Context) {
        if (videoMediaSession != null) return
        val realPlayer = player ?: return
        try {
            val appCtx = context.applicationContext
            val launchIntent = appCtx.packageManager
                .getLaunchIntentForPackage(appCtx.packageName)
                ?.apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("open_video_player", true)
                }
            val sessionActivity = launchIntent?.let {
                PendingIntent.getActivity(
                    appCtx, 1002, it,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }

            val sessionPlayer = object : ForwardingPlayer(realPlayer) {
                override fun getMediaMetadata(): MediaMetadata {
                    val v = GlobalPlayerState.currentVideo.value ?: return super.getMediaMetadata()
                    return MediaMetadata.Builder()
                        .setTitle(v.title)
                        .setArtist(v.channelName)
                        .also { b ->
                            v.thumbnailUrl.takeIf { it.isNotEmpty() }
                                ?.let { b.setArtworkUri(Uri.parse(it)) }
                        }
                        .build()
                }

                override fun getAvailableCommands(): Player.Commands =
                    super.getAvailableCommands().buildUpon()
                        .add(Player.COMMAND_SEEK_TO_NEXT)
                        .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                        .build()

                override fun isCommandAvailable(command: Int): Boolean =
                    availableCommands.contains(command)

                override fun seekToNext() {
                    autoNextLog("MediaSession seekToNext")
                    this@EnhancedPlayerManager.skipToNextFromSession()
                }
                override fun seekToNextMediaItem() {
                    autoNextLog("MediaSession seekToNextMediaItem")
                    this@EnhancedPlayerManager.skipToNextFromSession()
                }
                override fun seekToPrevious() { this@EnhancedPlayerManager.playPrevious() }
                override fun seekToPreviousMediaItem() { this@EnhancedPlayerManager.playPrevious() }
                override fun hasNextMediaItem(): Boolean = this@EnhancedPlayerManager.hasNextForSession()
                override fun hasPreviousMediaItem(): Boolean = this@EnhancedPlayerManager.hasPrevious()
            }

            val builder = MediaSession.Builder(appCtx, sessionPlayer).setId("flow_video_session")
            if (sessionActivity != null) builder.setSessionActivity(sessionActivity)
            videoMediaSession = builder.build()
            Log.d(TAG, "Video MediaSession created")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create video MediaSession", e)
        }
    }

    private fun releaseVideoMediaSession() {
        try {
            videoMediaSession?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release video MediaSession", e)
        }
        videoMediaSession = null
    }

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

    private val _playbackAbandonedEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val playbackAbandonedEvent: SharedFlow<Unit> = _playbackAbandonedEvent.asSharedFlow()

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
            initializeVideoMediaSession(context)
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
            onPlaybackAbandoned = { scope.launch { _playbackAbandonedEvent.emit(Unit) } },
            onGatedCodecFallback = { position -> qualityManager?.fallbackToAlternateCodec(position) ?: false },
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
        player?.addAnalyticsListener(PlaybackAnalyticsLogger(TAG) { currentVideoId })
        
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

        scope.launch {
            prefs.queueAutoplayEnabled.collect { isEnabled ->
                queueAutoplayEnabled = isEnabled
            }
        }

        scope.launch {
            prefs.autoplayCountdownSeconds.collect { seconds ->
                autoplayCountdownSeconds = seconds
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
                val exoLive = player?.isCurrentMediaItemLive == true
                _playerState.value = _playerState.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    playWhenReady = player?.playWhenReady ?: false,
                    hasEnded = playbackState == Player.STATE_ENDED && !isRecoveringFromBackground && !exoLive
                )
                if (playbackState == Player.STATE_READY ||
                    playbackState == Player.STATE_ENDED ||
                    playbackState == Player.STATE_BUFFERING
                ) {
                    autoNextLog("onPlaybackStateChanged ${playerStateName(playbackState)} recovering=$isRecoveringFromBackground live=$exoLive")
                }

                if (playbackState == Player.STATE_ENDED && !isRecoveringFromBackground && exoLive) {
                    val now = System.currentTimeMillis()
                    if (now - lastLiveEdgeRecoveryMs < 2500L) {
                        autoNextLog("STATE_ENDED on live again too soon -> stop recovering")
                        _playerState.value = _playerState.value.copy(hasEnded = true)
                    } else {
                        lastLiveEdgeRecoveryMs = now
                        autoNextLog("STATE_ENDED on live -> seekToLiveEdge")
                        seekToLiveEdge(resetSpeed = false)
                        player?.play()
                    }
                    return
                }

                if (playbackState == Player.STATE_ENDED && !isRecoveringFromBackground) {
                    acquireAdvanceWakeLock()
                    autoNextLog("STATE_ENDED branch entered")
                    if (_playerState.value.isLooping) {
                        autoNextLog("STATE_ENDED loop replay")
                        player?.seekTo(0)
                        player?.play()
                    } else {
                        maybeStartAutoplayCountdownOrAdvance()
                    }
                }
                
                if (playbackState == Player.STATE_BUFFERING) {
                    logBandwidthInfo()
                }

                if (playbackState == Player.STATE_READY) {
                    releaseAdvanceWakeLock()
                    autoNextLog("STATE_READY scheduling preload")
                    schedulePreloadNext()
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
            
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                autoNextLog("onMediaItemTransition reason=$reason mediaId=${mediaItem?.mediaId}")
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                ) {
                    val idx = player?.currentMediaItemIndex ?: 0
                    if (idx >= 1 && preloadedNext != null) {
                        releaseAdvanceWakeLock()
                        promotePreloadedItem()
                    }
                }
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                autoNextLog("onTimelineChanged reason=$reason windows=${timeline.windowCount}")
            }

            override fun onRenderedFirstFrame() {
                Log.d(TAG, "First frame rendered - video renderer working")
                surfaceManager?.setSurfaceReady(true)
                val rendererAvailable = isVideoRendererAvailable()
                Log.d(TAG, "Video renderer confirmed available after first frame: $rendererAvailable")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)
                autoNextLog("onIsPlayingChanged isPlaying=$isPlaying")
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _playerState.value = _playerState.value.copy(playWhenReady = playWhenReady)
                autoNextLog("onPlayWhenReadyChanged playWhenReady=$playWhenReady reason=$reason")
            }

            override fun onPlayerError(error: PlaybackException) {
                errorHandler?.handleError(error, player)
            }

            override fun onTracksChanged(tracks: Tracks) {
                applySubtitleTrackSelection()
                if (currentIsLiveStream) updateLiveQualityOptions(tracks)
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
        itAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format> = emptyList(),
        preferredVideoCodec: String = "auto",
        keepAudioOnly: Boolean = false,
        preferSabr: Boolean = false,
        preferredLiveQualityHeight: Int = 0
    ) {
        if (!isOnMainThread()) {
            autoNextLog("setStreams switching to main id=$videoId from=${Thread.currentThread().name}")
            withContext(Dispatchers.Main) {
                setStreams(
                    videoId = videoId,
                    videoStream = videoStream,
                    audioStream = audioStream,
                    videoStreams = videoStreams,
                    audioStreams = audioStreams,
                    subtitles = subtitles,
                    durationSeconds = durationSeconds,
                    dashManifestUrl = dashManifestUrl,
                    localFilePath = localFilePath,
                    hlsUrl = hlsUrl,
                    streamType = streamType,
                    startPosition = startPosition,
                    sabrInfo = sabrInfo,
                    itVideoFormats = itVideoFormats,
                    itAudioFormats = itAudioFormats,
                    preferredVideoCodec = preferredVideoCodec,
                    keepAudioOnly = keepAudioOnly,
                    preferSabr = preferSabr,
                    preferredLiveQualityHeight = preferredLiveQualityHeight
                )
            }
            return
        }
        Log.d(TAG, "setStreams(id=$videoId, videoHeight=${videoStream?.let(VideoCodecUtils::qualityHeightFromStream)}, sabr=${sabrInfo != null}, preferSabr=$preferSabr, itVideo=${itVideoFormats.size}, itAudio=${itAudioFormats.size}, keepAudioOnly=$keepAudioOnly)")
        resetPlaybackStateForNewVideo(videoId)
        currentSabrInfo = sabrInfo
        sabrPreferred = preferSabr
        innerTubeVideoFormats = itVideoFormats
        innerTubeAudioFormats = itAudioFormats
        isAudioOnlyMode = keepAudioOnly
        setVideoTracksDisabled(keepAudioOnly)

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
        val isLiveStream = useLiveManifest &&
            (!currentHlsUrl.isNullOrEmpty() || !currentDashManifestUrl.isNullOrEmpty())
        pendingLiveQualityHeight = if (isLiveStream) preferredLiveQualityHeight else 0
        updateLivePlaybackMode(isLive = isLiveStream, forceLiveSpeedReset = true)
        pendingInitialLiveEdgeSeek = streamType == StreamType.LIVE_STREAM
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
        qualityManager?.preferredCodecKey = preferredVideoCodec
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
            availableQualities = buildAvailableQualityOptions(),
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
        clearAutoplayCountdownInternal()
        currentVideoId = videoId
        liveQualityHeights = emptyList()
        pendingLiveQualityHeight = 0
        lastLiveEdgeRecoveryMs = 0L
        qualityManager?.resetForNewVideo()
        playbackTracker?.reset()
        errorHandler?.resetExpiryCounter()
        mediaLoader?.releaseSabr()
        currentSabrInfo = null
        sabrPreferred = false
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
        autoNextLog("loadMediaInternal audioOnly=$audioOnly preserve=$preservePosition local=${localFilePath != null}")
        clearPreload()
        if (audioOnly || isAudioOnlyMode) {
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
                isLiveStream = false,
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
        val result = mediaLoader?.loadMedia(
            player = player,
            context = appContext,
            videoStream = videoStream,
            audioStream = audio,
            availableVideoStreams = availableVideoStreams,
            currentVideoStream = currentVideoStream,
            dashManifestUrl = currentDashManifestUrl,
            hlsUrl = currentHlsUrl,
            isLiveStream = currentIsLiveStream,
            durationSeconds = currentDurationSeconds,
            currentDurationSeconds = currentDurationSeconds,
            preservePosition = preservePosition,
            localFilePath = localFilePath,
            audioOnly = audioOnly,
            subtitleStreams = availableSubtitles,
            sabrInfo = currentSabrInfo,
            sabrVideoId = currentVideoId,
            sabrPreferred = sabrPreferred,
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
        autoNextLog("setVideoTracksDisabled disabled=$disabled")
        videoTracksDisabled = disabled
        trackSelector?.let { selector ->
            selector.setParameters(
                selector.buildUponParameters()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, disabled)
                    .build()
            )
        }
        mediaLoader?.getActiveSabrOrchestrator()?.setAudioOnly(disabled)
    }

    // ===== Queue Management =====

    fun setQueue(videos: List<Video>, startIndex: Int, title: String? = null) {
        if (!isOnMainThread()) {
            autoNextLog("setQueue posted to main size=${videos.size} start=$startIndex from=${Thread.currentThread().name}")
            mainHandler.post { setQueue(videos, startIndex, title) }
            return
        }
        playbackQueue = videos
        currentQueueIndex = startIndex.coerceIn(0, videos.size - 1)
        queueTitle = title
        autoNextLog("setQueue size=${videos.size} start=$currentQueueIndex title=$title")
        
        _queueVideos.value = videos
        _currentQueueIndex.value = currentQueueIndex
        
        updateQueueState()
        
        if (videos.isNotEmpty()) {
            val video = videos[currentQueueIndex]
            startPlaybackFromQueue(video, loadStreamsInPlayer = false)
            requestPreloadNext("queue-set")
        }
    }

    fun playNext(loadStreamsInPlayer: Boolean = true): Boolean {
        if (currentQueueIndex < playbackQueue.size - 1) {
            autoNextLog("playNext queue loadStreams=$loadStreamsInPlayer")
            currentQueueIndex++
            _currentQueueIndex.value = currentQueueIndex
            startPlaybackFromQueue(playbackQueue[currentQueueIndex], loadStreamsInPlayer)
            updateQueueState()
            return true
        }
        autoNextLog("playNext queue unavailable")
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
        if (!isOnMainThread()) {
            autoNextLog("addVideoToQueueNext posted to main video=${video.id} from=${Thread.currentThread().name}")
            mainHandler.post { addVideoToQueueNext(video) }
            return
        }
        autoNextLog("addVideoToQueueNext ${video.id}")
        if (playbackQueue.isEmpty()) {
            val current = GlobalPlayerState.currentVideo.value
            if (current != null) {
                playbackQueue = listOf(current, video)
                currentQueueIndex = 0
                _queueVideos.value = playbackQueue
                _currentQueueIndex.value = 0
                updateQueueState()
                requestPreloadNext("queue-created-play-next")
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
        requestPreloadNext("queue-play-next")
    }

    /**
     * Append [video] to the end of the current queue.
     * If no queue is active but a video is currently playing, silently build a
     * [currentVideo, video] queue starting at index 0 — no playback interruption.
     * If nothing is playing at all, start the video immediately.
     */
    fun addVideoToQueue(video: Video) {
        if (!isOnMainThread()) {
            autoNextLog("addVideoToQueue posted to main video=${video.id} from=${Thread.currentThread().name}")
            mainHandler.post { addVideoToQueue(video) }
            return
        }
        autoNextLog("addVideoToQueue ${video.id}")
        if (playbackQueue.isEmpty()) {
            val current = GlobalPlayerState.currentVideo.value
            if (current != null) {
                playbackQueue = listOf(current, video)
                currentQueueIndex = 0
                _queueVideos.value = playbackQueue
                _currentQueueIndex.value = 0
                updateQueueState()
                requestPreloadNext("queue-created-add")
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
        requestPreloadNext("queue-add")
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
        if (!isOnMainThread()) {
            autoNextLog("setAutoplayCandidates posted to main source=$sourceVideoId from=${Thread.currentThread().name}")
            mainHandler.post { setAutoplayCandidates(sourceVideoId, videos, enabled) }
            return
        }
        autoplayEnabled = enabled
        autoplaySourceVideoId = sourceVideoId
        autoplayCandidates = videos
            .filter { it.id.isNotBlank() && it.id != sourceVideoId && !it.isLive && !it.isUpcoming }
            .distinctBy { it.id }
        Log.d(TAG, "Autoplay candidates for $sourceVideoId: ${autoplayCandidates.size}, enabled=$enabled")
        autoNextLog("setAutoplayCandidates source=$sourceVideoId input=${videos.size} filtered=${autoplayCandidates.size} enabled=$enabled")
        if (sourceVideoId == currentVideoId) {
            requestPreloadNext("autoplay-candidates")
        }
    }

    private fun hasNextForSession(): Boolean =
        hasNext() || (autoplayEnabled && autoplayCandidates.isNotEmpty())

    private fun skipToNextFromSession(): Boolean {
        autoNextLog("skipToNextFromSession")
        val p = player
        if (p != null && preloadedNext != null && p.currentMediaItemIndex < p.mediaItemCount - 1) {
            autoNextLog("skipToNextFromSession using preloaded window")
            p.seekToNextMediaItem()
            p.playWhenReady = true
            p.play()
            return true
        }
        return playNext(loadStreamsInPlayer = true) || playNextAutoplayCandidate()
    }

    private fun playNextAutoplayCandidate(): Boolean {
        if (!autoplayEnabled || autoplayJob?.isActive == true || _playerState.value.isLooping) {
            autoNextLog("playNextAutoplayCandidate blocked")
            return false
        }

        val nextVideo = autoplayCandidates.firstOrNull() ?: run {
            autoNextLog("playNextAutoplayCandidate no candidate")
            return false
        }

        autoNextLog("playNextAutoplayCandidate starting ${nextVideo.id}")
        autoplayCandidates = autoplayCandidates.drop(1)
        playVideoFromServiceLayer(nextVideo, reason = "related-autoplay")
        return true
    }

    // ===== Autoplay countdown (delay before switching to the next video) =====

    private fun nextSessionVideo(): Video? = when {
        hasNext() -> if (queueAutoplayEnabled) playbackQueue.getOrNull(currentQueueIndex + 1) else null
        autoplayEnabled -> autoplayCandidates.firstOrNull()
        else -> null
    }

    private fun maybeStartAutoplayCountdownOrAdvance() {
        val delaySeconds = autoplayCountdownSeconds
        val nextVideo = nextSessionVideo()
        if (delaySeconds > 0 && nextVideo != null) {
            startAutoplayCountdown(delaySeconds, nextVideo)
        } else {
            performAutoAdvance()
        }
    }

    private fun performAutoAdvance() {
        if (hasNext()) {
            // Queue autoplay can be disabled independently; manual next still works.
            if (!queueAutoplayEnabled) {
                autoNextLog("auto-advance queue disabled")
                return
            }
            autoNextLog("auto-advance queue playNext")
            _queueAutoAdvanceEvent.tryEmit(Unit)
            playNext(loadStreamsInPlayer = true)
        } else {
            autoNextLog("auto-advance related-autoplay")
            playNextAutoplayCandidate()
        }
    }

    private fun startAutoplayCountdown(totalSeconds: Int, nextVideo: Video) {
        autoplayCountdownJob?.cancel()
        autoplayCountdownJob = scope.launch {
            var remaining = totalSeconds
            _autoplayCountdown.value = AutoplayCountdownState(
                isActive = true,
                secondsRemaining = remaining,
                totalSeconds = totalSeconds,
                nextVideoTitle = nextVideo.title,
                nextVideoChannel = nextVideo.channelName,
                nextVideoThumbnailUrl = nextVideo.thumbnailUrl,
            )
            autoNextLog("autoplay countdown start ${totalSeconds}s next=${nextVideo.id}")
            while (remaining > 0) {
                delay(1000L)
                remaining--
                _autoplayCountdown.value = _autoplayCountdown.value.copy(secondsRemaining = remaining)
            }
            _autoplayCountdown.value = AutoplayCountdownState()
            autoplayCountdownJob = null
            autoNextLog("autoplay countdown elapsed -> advance")
            performAutoAdvance()
        }
    }

    fun skipAutoplayCountdown() {
        if (!_autoplayCountdown.value.isActive) return
        autoplayCountdownJob?.cancel()
        autoplayCountdownJob = null
        _autoplayCountdown.value = AutoplayCountdownState()
        autoNextLog("autoplay countdown skipped -> advance now")
        performAutoAdvance()
    }

    fun cancelAutoplayCountdown() {
        if (!_autoplayCountdown.value.isActive) return
        autoplayCountdownJob?.cancel()
        autoplayCountdownJob = null
        _autoplayCountdown.value = AutoplayCountdownState()
        releaseAdvanceWakeLock()
        autoNextLog("autoplay countdown cancelled")
    }

    fun restartFromAutoplayCountdown() {
        autoplayCountdownJob?.cancel()
        autoplayCountdownJob = null
        _autoplayCountdown.value = AutoplayCountdownState()
        releaseAdvanceWakeLock()
        player?.seekTo(0)
        player?.play()
        autoNextLog("autoplay countdown -> restart current")
    }

    private fun clearAutoplayCountdownInternal() {
        if (autoplayCountdownJob == null && !_autoplayCountdown.value.isActive) return
        autoplayCountdownJob?.cancel()
        autoplayCountdownJob = null
        if (_autoplayCountdown.value.isActive) {
            _autoplayCountdown.value = AutoplayCountdownState()
        }
    }

    private fun playVideoFromServiceLayer(video: Video, reason: String) {
        val context = appContext ?: return
        val resumeInAudioOnly = isAudioOnlyMode
        acquireAdvanceWakeLock()
        clearPreload()
        autoNextLog("playVideoFromServiceLayer start video=${video.id} reason=$reason resumeAudioOnly=$resumeInAudioOnly")
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
                    autoNextLog("playVideoFromServiceLayer streamInfo failed video=${video.id}")
                    _playerState.value = _playerState.value.copy(
                        isBuffering = false,
                        error = "Unable to load next video"
                    )
                    releaseAdvanceWakeLock()
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
                val preferredCodecKey = prefs.defaultVideoCodec.first().codecKey
                val innerTubeVideoStreams = extraction
                    ?.let { io.github.aedev.flow.player.stream.InnerTubeStreamBridge.convertVideoFormats(it.videoFormats) }
                    .orEmpty()
                val innerTubeAudioStreams = extraction
                    ?.let { io.github.aedev.flow.player.stream.InnerTubeStreamBridge.convertAudioFormats(it.audioFormats) }
                    .orEmpty()
                val extractorVideoStreams = (streamInfo.videoStreams + (streamInfo.videoOnlyStreams ?: emptyList()))
                    .filterIsInstance<VideoStream>()
                val mergedVideoStreams = StreamMergeUtils.mergeVideoStreams(extractorVideoStreams, innerTubeVideoStreams)
                val mergedAudioStreams = StreamMergeUtils.mergeAudioStreams(streamInfo.audioStreams, innerTubeAudioStreams)
                if (extractorVideoStreams.isNotEmpty()) {
                    Log.d(TAG, "Queue advance using NewPipe streams: ${extractorVideoStreams.size} video (merged=${mergedVideoStreams.size}, innerTube=${innerTubeVideoStreams.size})")
                } else if (innerTubeVideoStreams.isNotEmpty()) {
                    Log.d(TAG, "Queue advance using InnerTube streams: ${innerTubeVideoStreams.size} video, ${innerTubeAudioStreams.size} audio (merged=${mergedVideoStreams.size})")
                }

                val selected = selectStreamsForServicePlayback(mergedVideoStreams, mergedAudioStreams, preferredQuality, preferredAudioLanguage, preferredCodecKey)
                setStreams(
                    videoId = enrichedVideo.id,
                    videoStream = selected.first,
                    audioStream = selected.second,
                    videoStreams = mergedVideoStreams,
                    audioStreams = mergedAudioStreams,
                    subtitles = streamInfo.subtitles ?: emptyList(),
                    durationSeconds = streamInfo.duration,
                    dashManifestUrl = streamInfo.dashMpdUrl,
                    hlsUrl = streamInfo.hlsUrl,
                    streamType = streamInfo.streamType,
                    startPosition = 0L,
                    sabrInfo = sabrInfo,
                    itVideoFormats = extraction?.videoFormats ?: emptyList(),
                    itAudioFormats = extraction?.audioFormats ?: emptyList(),
                    preferredVideoCodec = preferredCodecKey,
                    keepAudioOnly = resumeInAudioOnly
                )
                play()
                autoNextLog("playVideoFromServiceLayer loaded video=${video.id} reason=$reason")
            } catch (e: CancellationException) {
                Log.d(TAG, "Service-layer playback cancelled for ${video.id} ($reason)")
                autoNextLog("playVideoFromServiceLayer cancelled video=${video.id} reason=$reason")
                releaseAdvanceWakeLock()
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Service-layer playback failed for ${video.id}", e)
                autoNextLog("playVideoFromServiceLayer failed video=${video.id} reason=$reason error=${e.javaClass.simpleName}:${e.message}")
                _playerState.value = _playerState.value.copy(
                    isBuffering = false,
                    error = e.message ?: "Unable to load next video"
                )
                releaseAdvanceWakeLock()
            } finally {
                autoplayJob = null
            }
        }
    }

    private data class ResolvedStreamData(
        val enrichedVideo: Video,
        val videoStream: VideoStream?,
        val audioStream: AudioStream?,
        val videoStreams: List<VideoStream>,
        val audioStreams: List<AudioStream>,
        val subtitles: List<SubtitlesStream>,
        val durationSeconds: Long,
        val dashManifestUrl: String?,
        val streamType: StreamType?,
        val relatedVideos: List<Video>,
        val preferredCodec: String,
        val itVideoFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format>,
        val itAudioFormats: List<io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format>
    )

    private data class PreloadedNext(
        val data: ResolvedStreamData,
        val fromQueue: Boolean
    )

    @Volatile private var preloadedNext: PreloadedNext? = null
    private var preloadJob: Job? = null
    private var preloadAttemptVideoId: String? = null
    private var preloadAttemptNextVideoId: String? = null
    private var preloadRetryJob: Job? = null
    private var preloadRetryCount: Int = 0

    private suspend fun resolveStreamsForVideo(video: Video, context: Context): ResolvedStreamData? = coroutineScope {
        val extractionDeferred = async(Dispatchers.IO) {
            try {
                withTimeoutOrNull(25000L) {
                    io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor.extract(video.id)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) { null }
        }
        val streamInfo = fetchStreamInfoForPlayback(video.id) ?: run {
            extractionDeferred.cancel()
            return@coroutineScope null
        }
        val extraction = extractionDeferred.await()
        val enrichedVideo = videoFromStreamInfo(video.id, streamInfo, fallback = video)
        val prefs = PlayerPreferences(context)
        val preferredQuality = if (isOnWifi(context)) prefs.defaultQualityWifi.first() else prefs.defaultQualityCellular.first()
        val preferredAudioLanguage = prefs.preferredAudioLanguage.first()
        val preferredCodecKey = prefs.defaultVideoCodec.first().codecKey
        val innerTubeVideoStreams = extraction
            ?.let { io.github.aedev.flow.player.stream.InnerTubeStreamBridge.convertVideoFormats(it.videoFormats) }
            .orEmpty()
        val innerTubeAudioStreams = extraction
            ?.let { io.github.aedev.flow.player.stream.InnerTubeStreamBridge.convertAudioFormats(it.audioFormats) }
            .orEmpty()
        val extractorVideoStreams = (streamInfo.videoStreams + (streamInfo.videoOnlyStreams ?: emptyList()))
            .filterIsInstance<VideoStream>()
        val mergedVideoStreams = StreamMergeUtils.mergeVideoStreams(innerTubeVideoStreams, extractorVideoStreams)
        val mergedAudioStreams = StreamMergeUtils.mergeAudioStreams(innerTubeAudioStreams, streamInfo.audioStreams)
        val selected = selectStreamsForServicePlayback(mergedVideoStreams, mergedAudioStreams, preferredQuality, preferredAudioLanguage, preferredCodecKey)
        ResolvedStreamData(
            enrichedVideo = enrichedVideo,
            videoStream = selected.first,
            audioStream = selected.second,
            videoStreams = mergedVideoStreams,
            audioStreams = mergedAudioStreams,
            subtitles = streamInfo.subtitles ?: emptyList(),
            durationSeconds = streamInfo.duration,
            dashManifestUrl = streamInfo.dashMpdUrl,
            streamType = streamInfo.streamType,
            relatedVideos = relatedVideosFromStreamInfo(streamInfo),
            preferredCodec = preferredCodecKey,
            itVideoFormats = extraction?.videoFormats ?: emptyList(),
            itAudioFormats = extraction?.audioFormats ?: emptyList()
        )
    }

    private fun nextPreloadTarget(): Pair<Video, Boolean>? {
        val fromQueue = hasNext()
        val nextVideo = when {
            fromQueue -> if (queueAutoplayEnabled) playbackQueue.getOrNull(currentQueueIndex + 1) else null
            autoplayEnabled && autoplayCandidates.isNotEmpty() -> autoplayCandidates.first()
            else -> null
        } ?: return null
        if (nextVideo.isLive || nextVideo.isUpcoming) return null
        return nextVideo to fromQueue
    }

    private fun requestPreloadNext(reason: String) {
        val (targetVideo, fromQueue) = nextPreloadTarget() ?: run {
            autoNextLog("requestPreloadNext no target reason=$reason")
            return
        }
        autoNextLog("requestPreloadNext reason=$reason target=${targetVideo.id} fromQueue=$fromQueue")

        val existing = preloadedNext
        if (existing != null && existing.data.enrichedVideo.id != targetVideo.id) {
            Log.d(TAG, "Gapless: replacing stale preload ${existing.data.enrichedVideo.id} with ${targetVideo.id} ($reason)")
            clearPreload()
        }

        if (preloadJob?.isActive == true && preloadAttemptNextVideoId != targetVideo.id) {
            Log.d(TAG, "Gapless: cancelling stale preload attempt for $preloadAttemptNextVideoId; next is ${targetVideo.id} ($reason)")
            preloadJob?.cancel()
            preloadJob = null
            preloadAttemptVideoId = null
            preloadAttemptNextVideoId = null
            preloadRetryCount = 0
        }

        schedulePreloadNext()
    }

    private fun schedulePreloadNext() {
        if (_playerState.value.isLooping) {
            autoNextLog("schedulePreloadNext skipped looping")
            return
        }
        val p = player ?: run {
            autoNextLog("schedulePreloadNext skipped no player")
            return
        }
        if (currentIsLiveStream) {
            autoNextLog("schedulePreloadNext skipped live")
            return
        }
        if (preloadedNext != null || preloadJob?.isActive == true) {
            autoNextLog("schedulePreloadNext skipped already busy")
            return
        }
        if (p.currentMediaItem == null || p.mediaItemCount == 0) {
            autoNextLog("schedulePreloadNext skipped no current media item")
            return
        }
        if (p.mediaItemCount > 1) {
            autoNextLog("schedulePreloadNext skipped mediaItemCount=${p.mediaItemCount}")
            return
        }
        val currentId = currentVideoId ?: run {
            autoNextLog("schedulePreloadNext skipped no currentId")
            return
        }
        val (nextVideo, fromQueue) = nextPreloadTarget() ?: run {
            autoNextLog("schedulePreloadNext skipped no target")
            return
        }
        if (preloadAttemptVideoId == currentId && preloadAttemptNextVideoId == nextVideo.id) {
            autoNextLog("schedulePreloadNext skipped duplicate attempt next=${nextVideo.id}")
            return
        }

        preloadAttemptVideoId = currentId
        preloadAttemptNextVideoId = nextVideo.id
        preloadRetryJob?.cancel()
        preloadRetryJob = null
        autoNextLog("schedulePreloadNext start next=${nextVideo.id} fromQueue=$fromQueue")
        preloadJob = scope.launch {
            var success = false
            var shouldRetry = false
            try {
                val ctx = appContext ?: return@launch
                val resolved = resolveStreamsForVideo(nextVideo, ctx) ?: run {
                    shouldRetry = true
                    autoNextLog("schedulePreloadNext resolve failed next=${nextVideo.id}")
                    return@launch
                }
                if (resolved.streamType == StreamType.LIVE_STREAM) {
                    autoNextLog("schedulePreloadNext resolved live next=${nextVideo.id}; skip")
                    return@launch
                }
                val pl = player ?: return@launch
                val latestTarget = nextPreloadTarget()
                if (currentVideoId != currentId ||
                    latestTarget == null ||
                    latestTarget.first.id != nextVideo.id ||
                    latestTarget.second != fromQueue ||
                    pl.mediaItemCount > 1 ||
                    _playerState.value.isLooping
                ) {
                    autoNextLog("schedulePreloadNext stale before append next=${nextVideo.id}")
                    return@launch
                }
                val source = mediaLoader?.buildPreloadMediaSource(
                    context = ctx,
                    videoStream = resolved.videoStream,
                    audioStream = resolved.audioStream,
                    availableVideoStreams = StreamProcessor.processVideoStreams(resolved.videoStreams),
                    dashManifestUrl = resolved.dashManifestUrl,
                    durationSeconds = resolved.durationSeconds,
                    subtitleStreams = StreamProcessor.processSubtitleStreams(resolved.subtitles)
                ) ?: run {
                    shouldRetry = true
                    autoNextLog("schedulePreloadNext mediaSource failed next=${nextVideo.id}")
                    return@launch
                }
                pl.addMediaSource(source)
                preloadedNext = PreloadedNext(resolved, fromQueue)
                preloadRetryCount = 0
                success = true
                autoNextLog("schedulePreloadNext appended next=${resolved.enrichedVideo.id} fromQueue=$fromQueue")
                Log.d(TAG, "Gapless: preloaded next ${resolved.enrichedVideo.id} (fromQueue=$fromQueue) as window ${pl.mediaItemCount - 1}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                shouldRetry = true
                Log.w(TAG, "Gapless preload failed", e)
            } finally {
                preloadJob = null
                if (!success && currentVideoId == currentId && preloadedNext == null) {
                    preloadAttemptVideoId = null
                    preloadAttemptNextVideoId = null
                    if (shouldRetry) schedulePreloadRetry(currentId, nextVideo.id)
                }
            }
        }
    }

    private fun schedulePreloadRetry(anchorVideoId: String, nextVideoId: String) {
        if (preloadRetryCount >= MAX_PRELOAD_RETRIES) {
            autoNextLog("schedulePreloadRetry giving up next=$nextVideoId")
            Log.w(TAG, "Gapless: giving up preloading $nextVideoId after $preloadRetryCount retries")
            return
        }
        preloadRetryCount++
        autoNextLog("schedulePreloadRetry scheduled next=$nextVideoId count=$preloadRetryCount")
        preloadRetryJob?.cancel()
        preloadRetryJob = scope.launch {
            delay(PRELOAD_RETRY_DELAY_MS)
            if (currentVideoId == anchorVideoId &&
                preloadedNext == null &&
                preloadJob?.isActive != true &&
                nextPreloadTarget()?.first?.id == nextVideoId
            ) {
                autoNextLog("schedulePreloadRetry firing next=$nextVideoId count=$preloadRetryCount")
                schedulePreloadNext()
            } else {
                autoNextLog("schedulePreloadRetry stale next=$nextVideoId")
            }
        }
    }

    private fun clearPreload() {
        autoNextLog("clearPreload")
        preloadJob?.cancel()
        preloadJob = null
        preloadRetryJob?.cancel()
        preloadRetryJob = null
        preloadAttemptVideoId = null
        preloadAttemptNextVideoId = null
        preloadRetryCount = 0
        if (preloadedNext != null) {
            preloadedNext = null
            val p = player
            if (p != null && p.mediaItemCount > 1) {
                val current = p.currentMediaItemIndex
                for (i in p.mediaItemCount - 1 downTo current + 1) {
                    runCatching { p.removeMediaItem(i) }
                }
            }
        }
    }

    private fun promotePreloadedItem() {
        val pre = preloadedNext ?: return
        autoNextLog("promotePreloadedItem ${pre.data.enrichedVideo.id} fromQueue=${pre.fromQueue}")
        preloadedNext = null
        preloadJob?.cancel(); preloadJob = null
        preloadRetryJob?.cancel(); preloadRetryJob = null
        val data = pre.data
        Log.d(TAG, "Gapless: promoting auto-advanced item ${data.enrichedVideo.id} (fromQueue=${pre.fromQueue})")

        mediaLoader?.releaseSabr()
        currentSabrInfo = null
        sabrPreferred = false

        if (pre.fromQueue) {
            if (currentQueueIndex < playbackQueue.size - 1) {
                currentQueueIndex++
                _currentQueueIndex.value = currentQueueIndex
            }
        } else {
            autoplayCandidates = autoplayCandidates.filter { it.id != data.enrichedVideo.id }
        }

        innerTubeVideoFormats = data.itVideoFormats
        innerTubeAudioFormats = data.itAudioFormats
        currentDurationSeconds = data.durationSeconds
        currentDashManifestUrl = data.dashManifestUrl
        currentHlsUrl = null
        currentIsLiveStream = false
        pendingInitialLiveEdgeSeek = false
        currentVideoId = data.enrichedVideo.id

        availableVideoStreams = StreamProcessor.processVideoStreams(data.videoStreams)
        availableAudioStreams = StreamProcessor.processAudioStreams(data.audioStreams)
        availableSubtitles = StreamProcessor.processSubtitleStreams(data.subtitles)
        currentVideoStream = data.videoStream ?: availableVideoStreams.firstOrNull()
        currentAudioStream = data.audioStream
        selectedSubtitleIndex = null
        disableTextTracks()

        qualityManager?.resetForNewVideo()
        qualityManager?.setAvailableStreams(availableVideoStreams)
        qualityManager?.preferredCodecKey = data.preferredCodec
        qualityManager?.isDashSource = !currentDashManifestUrl.isNullOrEmpty()
        qualityManager?.setCurrentStream(currentVideoStream)
        if (data.videoStream != null) {
            qualityManager?.setManualMode(VideoCodecUtils.qualityHeightFromStream(data.videoStream))
        }

        playbackTracker?.reset()
        startPlaybackTracker()

        sponsorBlockHandler?.reset()
        sponsorBlockHandler?.loadSegments(data.enrichedVideo.id)

        GlobalPlayerState.setCurrentVideo(data.enrichedVideo)
        startBackgroundService(
            videoId = data.enrichedVideo.id,
            title = data.enrichedVideo.title,
            channel = data.enrichedVideo.channelName,
            thumbnail = data.enrichedVideo.thumbnailUrl
        )
        setAutoplayCandidates(data.enrichedVideo.id, data.relatedVideos, autoplayEnabled)

        val isAutoMode = data.videoStream == null
        _playerState.value = _playerState.value.copy(
            currentVideoId = data.enrichedVideo.id,
            isBuffering = false,
            isPlaying = player?.isPlaying ?: false,
            playWhenReady = player?.playWhenReady ?: true,
            hasEnded = false,
            isPrepared = true,
            error = null,
            effectiveQuality = currentVideoStream?.let { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) } ?: 0,
            availableQualities = buildAvailableQualityOptions(),
            availableAudioTracks = StreamProcessor.toAudioTrackOptions(availableAudioStreams),
            availableSubtitles = StreamProcessor.toSubtitleOptions(availableSubtitles),
            currentQuality = if (isAutoMode) 0 else (currentVideoStream?.let { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) } ?: 0),
            currentQualityKey = if (isAutoMode) null else currentVideoStream?.getContent()?.takeIf { it.isNotBlank() },
            currentAudioTrack = currentAudioStream?.let { availableAudioStreams.indexOf(it).coerceAtLeast(0) } ?: 0,
            isLive = false,
            isAtLiveEdge = false,
            liveDurationMs = 0L
        )
        updateQueueState()
        _queueAutoAdvanceEvent.tryEmit(Unit)

        player?.let { p ->
            val idx = p.currentMediaItemIndex
            for (i in idx - 1 downTo 0) {
                runCatching { p.removeMediaItem(i) }
            }
        }

        preloadAttemptVideoId = null
        preloadAttemptNextVideoId = null
        preloadRetryCount = 0
        schedulePreloadNext()
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
        videoCandidates: List<VideoStream>,
        audioCandidatesAll: List<AudioStream>,
        preferredQuality: VideoQuality,
        preferredAudioLanguage: String,
        preferredCodecKey: String = "auto"
    ): Pair<VideoStream?, AudioStream?> {
        val audioCandidates = audioCandidatesAll
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

        val videoStreams = videoCandidates
            .filter {
                val mime = it.format?.mimeType
                mime?.contains("mp4", ignoreCase = true) == true ||
                    mime?.contains("webm", ignoreCase = true) == true
            }

        val selectedVideoStream = when (preferredQuality) {
            VideoQuality.AUTO -> null
            else -> videoStreams
                .sortedWith(
                    compareBy<VideoStream> {
                        kotlin.math.abs(
                            QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) - preferredQuality.height
                        )
                    }
                        .thenBy { VideoCodecUtils.codecRankWithPreference(it, preferredCodecKey) }
                        .thenByDescending { it.bitrate }
                )
                .firstOrNull()
        }
        val videoStream = if (audioStream == null && selectedVideoStream == null) {
            videoStreams
                .sortedWith(
                    compareBy<VideoStream> { if (it.isVideoOnly) 1 else 0 }
                        .thenByDescending { QualityManager.normalizeQualityHeight(VideoCodecUtils.qualityHeightFromStream(it)) }
                        .thenBy { VideoCodecUtils.codecRankWithPreference(it, preferredCodecKey) }
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
    
    fun play() {
        if (isAudioOnlyMode || videoSurfaceRestorePending) {
            restoreVideoOutput()
        }
        player?.play()
    }
    fun pause() = player?.pause()
    fun seekTo(position: Long) {
        val p = player ?: return
        val isLive = currentIsLiveStream || p.isCurrentMediaItemLive
        if (!isLive && mediaLoader?.getActiveSabrOrchestrator() != null) {
            sabrSeekTo(resolveSeekTarget(p, position))
            return
        }
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

    fun skipToSegmentEnd(endPositionMs: Long) {
        val p = player ?: return
        val isLive = currentIsLiveStream || p.isCurrentMediaItemLive
        if (!isLive && mediaLoader?.getActiveSabrOrchestrator() != null) {
            sabrSeekTo(resolveSeekTarget(p, endPositionMs))
            return
        }
        val target = resolveSeekTarget(p, endPositionMs)
        p.setSeekParameters(SeekParameters.EXACT)
        if (isLive) {
            markLiveDisplaySeek(target)
        }
        p.seekTo(target)
        if (isLive) {
            updateLiveEdgeState(p)
        } else {
            p.setSeekParameters(SeekParameters.CLOSEST_SYNC)
        }
    }

    private fun sabrSeekTo(positionMs: Long) {
        val shouldPlay = player?.playWhenReady ?: true
        Log.d(TAG, "SABR seek: rebuilding session at ${positionMs}ms")
        _playerState.value = _playerState.value.copy(isBuffering = true)
        scope.launch {
            mediaLoader?.releaseSabr()
            player?.stop()
            player?.clearMediaItems()
            val loaded = loadMediaInternal(currentVideoStream, currentAudioStream, positionMs)
            if (loaded) {
                player?.playWhenReady = shouldPlay
            }
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
        releaseAdvanceWakeLock()
        clearPreload()
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

    fun hasAbandonedPlayback(): Boolean = errorHandler?.hasGivenUp() == true

    private fun buildAvailableQualityOptions(): List<QualityOption> {
        val directOptions = qualityManager?.buildQualityOptions().orEmpty()
        val autoOptions = directOptions.filter { it.height == 0 }
        val playableOptions = directOptions.filter { it.height != 0 }
        val existingKeys = playableOptions
            .map { "${it.height}_${it.codecKey}" }
            .toHashSet()

        val sabrOptions = if (currentSabrInfo != null) {
            innerTubeVideoFormats
                .filter { !it.isAudio && it.itag > 0 }
                .groupBy { "${qualityHeightFromFormat(it)}_${VideoCodecUtils.codecKeyFromMimeType(it.mimeType)}" }
                .values
                .mapNotNull { formats ->
                    val best = formats.maxByOrNull { it.averageBitrate ?: it.bitrate } ?: return@mapNotNull null
                    val height = qualityHeightFromFormat(best)
                    val codecKey = VideoCodecUtils.codecKeyFromMimeType(best.mimeType)
                    if ("${height}_${codecKey}" in existingKeys) return@mapNotNull null
                    QualityOption(
                        height = height,
                        label = "${qualityLabelFromFormat(best)} ${VideoCodecUtils.codecLabelFromKey(codecKey)}",
                        bitrate = (best.averageBitrate ?: best.bitrate).toLong(),
                        codecKey = codecKey,
                        streamKey = "$SABR_QUALITY_KEY_PREFIX${best.itag}"
                    )
                }
        } else {
            emptyList()
        }

        val auto = autoOptions.ifEmpty { listOf(QualityOption(height = 0, label = "Auto", bitrate = 0L)) }
        val sortedOptions = (playableOptions + sabrOptions).sortedWith(
            compareByDescending<QualityOption> { it.height }
                .thenBy { VideoCodecUtils.playbackCodecRank(it.codecKey) }
                .thenByDescending { it.bitrate }
        )
        return auto + sortedOptions
    }

    private fun switchSabrQuality(option: QualityOption): Boolean {
        val streamKey = option.streamKey ?: return false
        val itag = streamKey.removePrefix(SABR_QUALITY_KEY_PREFIX).toIntOrNull() ?: return false
        val baseSabr = currentSabrInfo ?: return false
        val format = innerTubeVideoFormats.firstOrNull { it.itag == itag && !it.isAudio }
        if (format == null) {
            Log.w(TAG, "No SABR video format found for itag=$itag")
            return false
        }

        val position = player?.currentPosition ?: 0L
        val shouldPlay = player?.playWhenReady ?: true
        currentSabrInfo = baseSabr.copy(
            videoItag = format.itag,
            videoLmt = format.lastModified ?: 0L,
            videoMimeType = format.mimeType,
            durationMs = format.approxDurationMs?.toLongOrNull() ?: baseSabr.durationMs
        )

        _playerState.value = _playerState.value.copy(
            currentQuality = option.height,
            effectiveQuality = option.height,
            currentQualityKey = streamKey,
            isBuffering = true
        )

        Log.d(TAG, "Switching SABR quality to ${option.label} (itag=$itag)")
        scope.launch {
            mediaLoader?.releaseSabr()
            player?.stop()
            player?.clearMediaItems()
            val loaded = loadMediaInternal(currentVideoStream, currentAudioStream, position)
            if (loaded) {
                player?.playWhenReady = shouldPlay
            }
        }
        return true
    }

    private fun qualityHeightFromFormat(format: PlayerResponse.StreamingData.Format): Int {
        format.qualityLabel
            ?.let { QUALITY_HEIGHT_REGEX.find(it)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            ?.let { return it }
        return VideoCodecUtils.normalizeQualityHeight(format.height ?: format.width ?: 0)
    }

    private fun qualityLabelFromFormat(format: PlayerResponse.StreamingData.Format): String {
        return format.qualityLabel
            ?.takeIf { it.isNotBlank() }
            ?: "${qualityHeightFromFormat(format)}p"
    }

    // ===== Quality & Audio Management =====
    
    fun switchQualityByHeight(height: Int) =
        if (currentIsLiveStream) switchLiveQuality(height)
        else qualityManager?.switchQualityByHeight(height, player?.currentPosition ?: 0L)
    fun switchQuality(height: Int) = switchQualityByHeight(height)
    fun switchQuality(option: QualityOption): Boolean? {
        if (option.streamKey?.startsWith(SABR_QUALITY_KEY_PREFIX) == true) {
            return switchSabrQuality(option)
        }
        if (currentIsLiveStream) return switchLiveQuality(option.height)
        return qualityManager?.switchQuality(option, player?.currentPosition ?: 0L)
    }

    private fun updateLiveQualityOptions(tracks: Tracks) {
        val heightToFps = HashMap<Int, Int>()
        tracks.groups.asSequence()
            .filter { it.type == C.TRACK_TYPE_VIDEO }
            .forEach { group ->
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    val h = format.height.takeIf { it > 0 } ?: continue
                    val height = VideoCodecUtils.normalizeQualityHeight(h)
                    val fps = if (format.frameRate > 0f) format.frameRate.toInt() else 0
                    heightToFps[height] = maxOf(heightToFps[height] ?: 0, fps)
                }
            }
        val heights = heightToFps.keys.sortedDescending()
        if (heights.isEmpty() || heights == liveQualityHeights) return
        liveQualityHeights = heights

        val options = listOf(QualityOption(height = 0, label = "Auto", bitrate = 0L)) +
            heights.map { h ->
                val fps = heightToFps[h] ?: 0
                val label = if (fps >= 50) "${h}p$fps" else "${h}p"
                QualityOption(height = h, label = label, bitrate = 0L, streamKey = "$LIVE_QUALITY_KEY_PREFIX$h")
            }
        val manualHeight = _playerState.value.currentQualityKey
            ?.removePrefix(LIVE_QUALITY_KEY_PREFIX)?.toIntOrNull()
        _playerState.value = _playerState.value.copy(
            availableQualities = options,
            effectiveQuality = manualHeight ?: heights.first()
        )

        if (pendingLiveQualityHeight > 0 && manualHeight == null) {
            val target = heights.firstOrNull { it <= pendingLiveQualityHeight } ?: heights.last()
            pendingLiveQualityHeight = 0
            Log.d(TAG, "Applying default live quality: ${target}p")
            switchLiveQuality(target)
        }
    }

    private fun switchLiveQuality(height: Int): Boolean {
        val selector = trackSelector ?: return false
        val builder = selector.buildUponParameters()
            .setPreferredVideoMimeTypes(*PlayerConfig.PREFERRED_VIDEO_MIME_TYPES)
        if (height <= 0) {
            builder.clearVideoSizeConstraints()
                .setMaxVideoSize(PlayerConfig.MAX_VIDEO_WIDTH, PlayerConfig.MAX_VIDEO_HEIGHT)
                .setForceHighestSupportedBitrate(false)
        } else {
            builder.setMinVideoSize(0, 0)
                .setMaxVideoSize(Int.MAX_VALUE, height)
                .setForceHighestSupportedBitrate(true)
        }
        selector.setParameters(builder.build())
        _playerState.value = _playerState.value.copy(
            currentQuality = if (height <= 0) 0 else height,
            effectiveQuality = if (height <= 0) (liveQualityHeights.firstOrNull() ?: 0) else height,
            currentQualityKey = if (height <= 0) null else "$LIVE_QUALITY_KEY_PREFIX$height"
        )
        return true
    }
    
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
        autoNextLog("continueVideoPlaybackInBackground")
        switchToAudioOnly()
        val p = player
        val current = GlobalPlayerState.currentVideo.value
        val state = _playerState.value
        if (current != null &&
            state.currentVideoId == current.id &&
            (p?.currentMediaItem == null || p.playbackState == Player.STATE_IDLE || !state.isPrepared)
        ) {
            autoNextLog("continueVideoPlaybackInBackground service-load current=${current.id}")
            playVideoFromServiceLayer(current, reason = "background-handoff-unprepared")
            return
        }
        if (p?.playWhenReady == true && !p.isPlaying && p.playbackState != Player.STATE_ENDED) {
            p.play()
        }
    }

    fun handleCriticalMemoryPressure() {
        Log.w(TAG, "Critical memory pressure; releasing video-heavy player state")
        mediaLoader?.releaseSabr()
        val p = player ?: return
        val shouldKeepPlaying = p.playWhenReady || p.isPlaying
        if (shouldKeepPlaying) {
            switchToAudioOnly()
            clearSurface()
        } else {
            p.stop()
            p.clearMediaItems()
            markVideoReprimePending()
        }
    }

    
    fun switchToAudioOnly() {
        val p = player ?: return
        autoNextLog("switchToAudioOnly")
        isAudioOnlyMode = true
        setVideoTracksDisabled(true)
        p.setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
        if (p.playWhenReady && !p.isPlaying && p.playbackState != Player.STATE_ENDED) {
            p.play()
        }
        schedulePreloadNext()
    }

    fun restoreVideoOutput() {
        val p = player ?: return
        if (!isDisplayInteractive()) {
            autoNextLog("restoreVideoOutput skipped display not interactive")
            videoSurfaceRestorePending = true
            return
        }
        autoNextLog("restoreVideoOutput")
        val wasAudioOnly = isAudioOnlyMode
        isAudioOnlyMode = false
        videoSurfaceRestorePending = false
        videoReprimePending = false
        setVideoTracksDisabled(false)
        p.setWakeMode(androidx.media3.common.C.WAKE_MODE_LOCAL)
        if (wasAudioOnly &&
            !currentIsLiveStream &&
            !p.isCurrentMediaItemLive &&
            p.playbackState != Player.STATE_IDLE
        ) {
            p.seekTo(p.currentPosition)
        }
        if (p.playWhenReady && !p.isPlaying && p.playbackState != Player.STATE_ENDED) {
            p.play()
        }
    }

    fun isInAudioOnlyMode(): Boolean = isAudioOnlyMode

    fun isVideoSurfaceRestorePending(): Boolean = videoSurfaceRestorePending

    fun markVideoSurfaceRestored() {
        videoSurfaceRestorePending = false
    }

    fun markVideoReprimePending() {
        val p = player ?: return
        if (currentIsLiveStream || p.isCurrentMediaItemLive) return
        videoReprimePending = true
    }

    fun reprimeVideoOutputIfPending() {
        if (!videoReprimePending) return
        videoReprimePending = false
        val p = player ?: return
        if (isAudioOnlyMode) return
        if (currentIsLiveStream || p.isCurrentMediaItemLive) return
        if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) return
        autoNextLog("reprimeVideoOutputIfPending seek")
        p.seekTo(p.currentPosition)
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
        clearPreload()
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
        releaseAdvanceWakeLock()
        advanceWakeLock = null
        clearPreload()
        releaseVideoMediaSession()
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
