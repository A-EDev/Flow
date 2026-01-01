package com.flow.youtube.player

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.view.SurfaceHolder
import java.io.File
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.video.PlaceholderSurface
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import com.flow.youtube.service.VideoPlayerService
import com.flow.youtube.player.datasource.YouTubeHttpDataSource
import com.flow.youtube.data.local.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.SubtitlesStream
import org.schabi.newpipe.extractor.stream.VideoStream

@UnstableApi
class EnhancedPlayerManager private constructor() {
    companion object {
        private const val TAG = "EnhancedPlayerManager"
        
        @Volatile
        private var instance: EnhancedPlayerManager? = null
        
        fun getInstance(): EnhancedPlayerManager {
            return instance ?: synchronized(this) {
                instance ?: EnhancedPlayerManager().also { instance = it }
            }
        }
    }
    
    private var player: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var bandwidthMeter: DefaultBandwidthMeter? = null
    private var databaseProvider: StandaloneDatabaseProvider? = null
    private var cache: SimpleCache? = null
    private var sharedDataSourceFactory: DataSource.Factory? = null
    private val _playerState = MutableStateFlow(EnhancedPlayerState())
    val playerState: StateFlow<EnhancedPlayerState> = _playerState.asStateFlow()

    // Audio processor for skipping silence
    private val silenceSkippingProcessor = SilenceSkippingAudioProcessor()
    
    private var currentVideoId: String? = null
    private var availableVideoStreams: List<VideoStream> = emptyList()
    private var availableAudioStreams: List<AudioStream> = emptyList()
    private var availableSubtitles: List<SubtitlesStream> = emptyList()
    private var currentVideoStream: VideoStream? = null
    private var currentAudioStream: AudioStream? = null
    private var appContext: Context? = null
    private var selectedSubtitleIndex: Int? = null
    private var surfaceHolder: SurfaceHolder? = null
    private var placeholderSurface: PlaceholderSurface? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    var isSurfaceReady: Boolean = false
        private set
    
    // StateFlow to signal when a real surface (not placeholder) is attached
    private val _surfaceReadyFlow = MutableStateFlow(false)
    
    private var lastPosition: Long = 0L
    private var stuckCounter: Int = 0
    private var isWatchdogActive: Boolean = false
    
    // Stream health tracking
    private var failedStreamUrls = mutableSetOf<String>()  // Track URLs that failed to parse
    private var streamErrorCount = 0  // Track consecutive errors
    private val MAX_STREAM_ERRORS = 2  // Max errors before downgrading quality
    
    // Quality mode tracking
    private var isAdaptiveQualityEnabled = true  // Auto quality by default
    private var manualQualityHeight: Int? = null  // Track manually selected quality

    // Buffering watchdog
    private val bufferingHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val bufferingRunnable = Runnable {
        if (_playerState.value.isBuffering && isAdaptiveQualityEnabled) {
            Log.w(TAG, "Buffering too long (>5s) - attempting quality downgrade")
            downgradeQualityDueToBandwidth()
        }
    }

    private fun setSkipSilenceInternal(isEnabled: Boolean) {
        silenceSkippingProcessor.setEnabled(isEnabled)
        _playerState.value = _playerState.value.copy(
            isSkipSilenceEnabled = isEnabled
        )
    }

    fun toggleSkipSilence(isEnabled: Boolean) {
        setSkipSilenceInternal(isEnabled)
        
        // Persist preference
        appContext?.let { context ->
            scope.launch {
                PlayerPreferences(context).setSkipSilenceEnabled(isEnabled)
            }
        }
    }

    fun initialize(context: Context) {
        appContext = context.applicationContext
        
        // Observe skip silence state
        scope.launch {
            PlayerPreferences(context).skipSilenceEnabled.collect { isEnabled ->
                setSkipSilenceInternal(isEnabled)
            }
        }

        appContext = context.applicationContext
        if (player == null) {
            // Initialize bandwidth meter for ABR
            bandwidthMeter = DefaultBandwidthMeter.Builder(context)
                .setInitialBitrateEstimate(2_000_000) // 2 Mbps initial estimate for faster start
                .build()
            
            trackSelector = DefaultTrackSelector(context).apply {
                // Enable adaptive selections and tune ABR parameters similar to NewPipe
                setParameters(
                    buildUponParameters()
                        // Allow switching between different video MIME types (e.g., VP9 <-> H264)
                        .setAllowVideoMixedMimeTypeAdaptiveness(true)
                        // Allow multiple adaptive selections (e.g., video + audio)
                        .setAllowMultipleAdaptiveSelections(true)
                        // Prefer higher quality but allow adaptation
                        .setForceHighestSupportedBitrate(false)
                        // Viewport constraints for better mobile experience
                        .setViewportSizeToPhysicalDisplaySize(context, true)
                        // Max video dimensions - allow up to 1080p for faster playback
                        .setMaxVideoSize(1920, 1080)
                        // Prefer original audio language
                        .setPreferredAudioLanguage("original")
                        .build()
                )
            }

            // Build a shared DataSource.Factory with optional cache
            val httpFactory = YouTubeHttpDataSource.Factory()
                .setConnectTimeoutMs(8000)      // Increased timeout for better reliability
                .setReadTimeoutMs(10000)        // Increased read timeout
                .setAllowCrossProtocolRedirects(true)
            val upstream = DefaultDataSource.Factory(context, httpFactory)

            try {
                databaseProvider = StandaloneDatabaseProvider(context)
                val cacheDir = File(context.cacheDir, "exoplayer")
                val evictor = LeastRecentlyUsedCacheEvictor(500L * 1024L * 1024L)
                cache = SimpleCache(cacheDir, evictor, databaseProvider!!)
                sharedDataSourceFactory = CacheDataSource.Factory()
                    .setCache(cache!!)
                    .setUpstreamDataSourceFactory(upstream)
            } catch (e: Exception) {
                Log.w("EnhancedPlayerManager", "Cache not available, using upstream only", e)
                sharedDataSourceFactory = upstream
            }

            // Optimized LoadControl for buttery smooth playback with aggressive buffering
            val loadControl = DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 15000,     // Buffer 15s minimum before starting (smooth playback)
                    /* maxBufferMs = */ 50000,     // Buffer up to 50s ahead (good margin)
                    /* bufferForPlaybackMs = */ 1000,  // Start playback after 1s buffered (Super Sonic Speed)
                    /* bufferForPlaybackAfterRebufferMs = */ 5000  // Rebuffer 5s after stall
                )
                .setBackBuffer(
                    /* backBufferDurationMs = */ 10000,  // Keep 10s behind playback position
                    /* retainBackBufferFromKeyframe = */ true
                )
                .setPrioritizeTimeOverSizeThresholds(true)  // Prioritize time for smoother playback
                .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES * 2)  // 2x buffer size
                .build()

            // Create custom RenderersFactory to inject SilenceSkippingAudioProcessor
            val renderersFactory = object : DefaultRenderersFactory(context) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean
                ): AudioSink? {
                    return DefaultAudioSink.Builder()
                        .setAudioProcessors(arrayOf(silenceSkippingProcessor))
                        .build()
                }
            }

            player = ExoPlayer.Builder(context, renderersFactory)
                .setTrackSelector(trackSelector!!)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                        .setUsage(C.USAGE_MEDIA)
                        .build(),
                    true
                )
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(sharedDataSourceFactory ?: upstream)
                )
                .build()

            // Reattach any preserved surface holder so the new player renders immediately
            surfaceHolder?.let { holder ->
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        player?.setVideoSurfaceHolder(holder)
                        Log.d(TAG, "Reattached preserved surface holder after player init")
                    } else {
                        val surface = holder.surface
                        if (surface != null && surface.isValid) {
                            player?.setVideoSurface(surface)
                            Log.d(TAG, "Reattached preserved surface after player init (legacy)")
                        } else {
                            Log.d(TAG, "Legacy surface not yet valid during init")
                        }
                    }
                }.onFailure { e ->
                    Log.d(TAG, "Failed to reattach surface during init: ${e.message}")
                }
            }
            
            setupPlayerListener()
            Log.d("EnhancedPlayerManager", "Player initialized")
        }
    }

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
                    hasEnded = playbackState == Player.STATE_ENDED
                )
                
                // Log bandwidth when buffering to help diagnose issues
                if (playbackState == Player.STATE_BUFFERING) {
                    logBandwidthInfo()
                    startBufferingWatchdog()
                } else {
                    stopBufferingWatchdog()
                }
            }
            
            override fun onRenderedFirstFrame() {
                Log.d("EnhancedPlayerManager", "First frame rendered - video renderer working")
                // Ensure surface is marked as ready when first frame renders
                setSurfaceReady(true)
                // Check renderer availability after first frame - this should now be reliable
                val rendererAvailable = isVideoRendererAvailable()
                Log.d("EnhancedPlayerManager", "Video renderer confirmed available after first frame: $rendererAvailable")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _playerState.value = _playerState.value.copy(isPlaying = isPlaying)

                // Start/stop watchdog when playback state changes
                if (isPlaying) {
                    startPlaybackWatchdog()
                } else {
                    stopPlaybackWatchdog()
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _playerState.value = _playerState.value.copy(isPlaying = playWhenReady)
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer - onPlayerError() called with:", error)

                saveStreamProgressState()
                var isCatchableException = false

                when (error.errorCode) {
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        isCatchableException = true
                        Log.w(TAG, "Behind live window, seeking to live edge")
                        player?.seekToDefaultPosition()
                        player?.prepare()
                        // Inform user that we're reloading the stream
                        _playerState.value = _playerState.value.copy(
                            isBuffering = true,
                            error = null
                        )
                    }
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                        // Source errors - for parser errors with incomplete streams, skip cache and reload
                        Log.e(TAG, "Source validation error: ${error.errorCode} - ${error.message}")
                        
                        // Check for UnrecognizedInputFormatException (format incompatibility, not corruption)
                        val errorMessage = error.message ?: ""
                        val causeMessage = error.cause?.message ?: ""
                        val fullErrorInfo = "$errorMessage $causeMessage"
                        
                        if (fullErrorInfo.contains("UnrecognizedInputFormatException", ignoreCase = true) ||
                            error.cause?.javaClass?.simpleName == "UnrecognizedInputFormatException") {
                            
                            Log.w(TAG, "Unrecognized format error - trying alternative stream format")
                            
                            // Mark this stream as incompatible (not corrupted, just unsupported format)
                            if (currentVideoStream?.url != null) {
                                failedStreamUrls.add(currentVideoStream!!.url!!)
                                Log.d(TAG, "Marking incompatible format stream: ${currentVideoStream!!.format?.mimeType}")
                                
                                // Try alternative quality immediately (don't count errors)
                                attemptQualityDowngrade()
                                return
                            }
                        }
                        
                        // Check for NAL corruption errors (actual stream corruption)
                        if ((fullErrorInfo.contains("NAL", ignoreCase = true) || 
                             error.cause is androidx.media3.common.ParserException) && 
                            currentVideoStream?.url != null) {
                            
                            failedStreamUrls.add(currentVideoStream!!.url!!)
                            streamErrorCount++
                            Log.w(TAG, "Corrupted stream detected (NAL/Parser error): ${currentVideoStream?.url} - Error count: $streamErrorCount of $MAX_STREAM_ERRORS")
                            
                            // If too many errors, try downgrading to lower quality
                            if (streamErrorCount >= MAX_STREAM_ERRORS) {
                                Log.w(TAG, "Max stream errors reached ($streamErrorCount >= $MAX_STREAM_ERRORS)")
                                if (isAdaptiveQualityEnabled) {
                                    Log.w(TAG, "Adaptive mode enabled - attempting quality downgrade")
                                    attemptQualityDowngrade()
                                } else {
                                    Log.w(TAG, "Manual quality locked (${manualQualityHeight}p) - retrying same stream")
                                    streamErrorCount = 0
                                    reloadCurrentStream(reason = "manual-quality-parser-error")
                                }
                                return
                            }
                        }
                        
                        // Skip cache clearing to avoid repeated delays - let ExoPlayer handle partial content
                        setRecovery()
                        reloadPlaybackManager()
                    }
                    PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                    PlaybackException.ERROR_CODE_UNSPECIFIED -> {
                        // Check if this is actually a parser error (NAL length) disguised as IO error
                        // Check both the error message and the cause chain
                        val errorMessage = error.message ?: ""
                        val causeMessage = error.cause?.message ?: ""
                        val fullErrorInfo = "$errorMessage $causeMessage"
                        
                        if (fullErrorInfo.contains("NAL", ignoreCase = true) || 
                            fullErrorInfo.contains("ParserException", ignoreCase = true) ||
                            error.cause is androidx.media3.common.ParserException) {
                            
                            Log.e(TAG, "Parser error detected in IO error: $fullErrorInfo")
                            
                            // Track this stream URL as corrupted
                            if (currentVideoStream?.url != null) {
                                failedStreamUrls.add(currentVideoStream!!.url!!)
                                streamErrorCount++
                                Log.w(TAG, "Corrupted stream detected (NAL/Parser error): ${currentVideoStream?.url} - Error count: $streamErrorCount")
                                
                                // If too many errors, try downgrading to lower quality immediately
                                if (streamErrorCount >= MAX_STREAM_ERRORS) {
                                    Log.w(TAG, "Max stream errors reached ($streamErrorCount >= $MAX_STREAM_ERRORS)")
                                    if (isAdaptiveQualityEnabled) {
                                        Log.w(TAG, "Adaptive mode enabled - attempting quality downgrade")
                                        attemptQualityDowngrade()
                                    } else {
                                        Log.w(TAG, "Manual quality locked - retrying same stream")
                                        streamErrorCount = 0
                                        reloadCurrentStream(reason = "manual-quality-parser-io")
                                    }
                                    return
                                }
                            }
                            
                            // Try once more before giving up
                            setRecovery()
                            reloadPlaybackManager()
                            return
                        }
                        
                        // Network errors: allow multiple retries with exponential backoff
                        Log.w(TAG, "Network error encountered: ${error.errorCode} - ${error.message}")
                        
                        // Track network errors but allow multiple retries (network issues are often transient)
                        if (currentVideoStream?.url != null) {
                            streamErrorCount++
                            Log.w(TAG, "Network error on stream: ${currentVideoStream?.url} - Error count: $streamErrorCount of $MAX_STREAM_ERRORS")
                            
                            // After multiple network errors, try alternative quality
                            if (streamErrorCount >= MAX_STREAM_ERRORS) {
                                Log.w(TAG, "Max network errors reached for stream")
                                if (isAdaptiveQualityEnabled) {
                                    failedStreamUrls.add(currentVideoStream!!.url!!)
                                    Log.w(TAG, "Adaptive mode - marking stream failed and trying alternative quality")
                                    attemptQualityDowngrade()
                                } else {
                                    Log.w(TAG, "Manual quality locked - retrying same stream after network error")
                                    streamErrorCount = 0
                                    reloadCurrentStream(reason = "manual-quality-network")
                                }
                                return
                            }
                        }
                        
                        // Otherwise retry current stream (network might recover)
                        setRecovery()
                        reloadPlaybackManager()
                    }
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
                    PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED,
                    PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> {
                        // Renderer/decoder errors: try alternative audio/video combination
                        Log.e(TAG, "Decoder/renderer error: ${error.errorCode}")
                        
                        // Check if it's an audio-specific error
                        val isAudioError = error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                                         error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                                         error.message?.contains("AudioRenderer", ignoreCase = true) == true
                        
                        if (isAudioError && currentAudioStream?.url != null) {
                            // Mark current audio stream as failed and try alternative
                            failedStreamUrls.add(currentAudioStream!!.url!!)
                            Log.w(TAG, "Audio decoder error - trying alternative audio stream")
                            
                            // Find alternative audio stream
                            val alternativeAudio = availableAudioStreams
                                .filter { it.url != null && !failedStreamUrls.contains(it.url) }
                                .sortedByDescending { it.averageBitrate }
                                .firstOrNull()
                            
                            if (alternativeAudio != null) {
                                currentAudioStream = alternativeAudio
                                Log.d(TAG, "Switching to alternative audio format: ${alternativeAudio.format?.mimeType}")
                                setRecovery()
                                reloadPlaybackManager()
                                return
                            }
                        }
                        
                        // If audio switching failed or it's a video decoder error, terminate playback
                        Log.e(TAG, "Decoder error - no alternatives available, stopping playback")
                        onPlaybackShutdown()
                        _playerState.value = _playerState.value.copy(
                            error = "Playback device error: ${error.message}",
                            isPlaying = false
                        )
                    }
                    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
                    PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED,
                    PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
                    PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
                    PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR,
                    PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> {
                        // DRM errors: terminate playback
                        Log.e(TAG, "DRM error: ${error.errorCode}")
                        onPlaybackShutdown()
                        _playerState.value = _playerState.value.copy(
                            error = "Content protection error: ${error.message}",
                            isPlaying = false
                        )
                    }
                    else -> {
                        // Any other unspecified issue: try to restart playback
                        Log.w(TAG, "Unspecified error, attempting recovery: ${error.errorCode}")
                        setRecovery()
                        reloadPlaybackManager()
                    }
                }

                if (!isCatchableException) {
                    createErrorNotification(error)
                }

                // Update fragment listener if available
                // fragmentListener?.onPlayerError(error, isCatchableException)
            }
        })
    }

    suspend fun setStreams(
        videoId: String,
        videoStream: VideoStream?,
        audioStream: AudioStream,
        videoStreams: List<VideoStream>,
        audioStreams: List<AudioStream>,
        subtitles: List<SubtitlesStream>
    ) {
        Log.d(TAG, "setStreams(id=$videoId, videoHeight=${videoStream?.height}, audioBitrate=${audioStream.averageBitrate}, videoList=${videoStreams.size}, audioList=${audioStreams.size})")
        resetPlaybackStateForNewVideo(videoId)

        currentVideoId = videoId
        // dedupe lists by url and sort
        availableVideoStreams = videoStreams.distinctBy { it.url ?: "" }.sortedByDescending { it.height }
        availableAudioStreams = audioStreams.distinctBy { it.url ?: "" }
        availableSubtitles = subtitles
        // Set defaults: prefer provided videoStream/audioStream when present
        currentVideoStream = videoStream ?: availableVideoStreams.firstOrNull()
        // Prefer the provided audio stream, otherwise pick highest bitrate or preferring English if present
        currentAudioStream = audioStream ?: run {
            availableAudioStreams.firstOrNull { it.audioLocale?.language?.startsWith("en", true) == true }
                ?: availableAudioStreams.maxByOrNull { it.averageBitrate }
        }
        _playerState.value = _playerState.value.copy(
            currentVideoId = videoId,
            effectiveQuality = currentVideoStream?.height ?: 0,
            availableQualities = listOf(
                QualityOption(height = 0, label = "Auto", bitrate = 0L)  // Add Auto option at top
            ) + videoStreams
                .distinctBy { it.height }  // Remove duplicates by height
                .sortedByDescending { it.height }  // Sort highest to lowest
                .map { 
                    QualityOption(
                        height = it.height,
                        label = "${it.height}p",
                        bitrate = it.bitrate.toLong()
                    )
                },
            availableAudioTracks = audioStreams.mapIndexed { index, stream ->
                AudioTrackOption(
                    index = index,
                    label = stream.audioTrackName ?: stream.audioTrackId ?: "Track ${index + 1}",
                    language = stream.audioLocale?.displayLanguage ?: "Unknown",
                    bitrate = stream.averageBitrate.toLong()
                )
            },
            availableSubtitles = subtitles.map {
                SubtitleOption(
                    url = it.url ?: "",
                    language = it.languageTag ?: "Unknown",
                    label = it.displayLanguageName ?: it.languageTag ?: "Unknown",
                    isAutoGenerated = it.isAutoGenerated
                )
            },
            currentQuality = 0,  // Start with Auto quality (adaptive)
            currentAudioTrack = availableAudioStreams.indexOf(currentAudioStream).coerceAtLeast(0)
        )
        
        if (!isAdaptiveQualityEnabled && manualQualityHeight != null) {
            val manualOptionExists = availableVideoStreams.any { it.height == manualQualityHeight }
            if (!manualOptionExists) {
                Log.w(TAG, "Manual quality ${manualQualityHeight}p unavailable for $videoId - falling back to adaptive mode")
                isAdaptiveQualityEnabled = true
                manualQualityHeight = null
                applyAdaptiveTrackSelectorDefaults()
            }
        }

        // CRITICAL: Wait for surface to be ready before loading media
        val surfaceReady = awaitSurfaceReady(timeoutMillis = 1000)
        if (!surfaceReady) {
            Log.w(TAG, "Surface not ready after waiting - media may render to placeholder")
        }

        if (currentVideoStream != null && currentAudioStream != null) {
            loadMedia(currentVideoStream, currentAudioStream!!)
        } else if (currentVideoStream != null) {
            loadMedia(currentVideoStream, currentAudioStream ?: availableAudioStreams.firstOrNull() ?: audioStream)
        } else {
            loadMedia(null, currentAudioStream ?: audioStream)
        }
    }

    private fun resetPlaybackStateForNewVideo(videoId: String) {
        failedStreamUrls.clear()
        streamErrorCount = 0
        currentVideoStream = null
        currentAudioStream = null
        selectedSubtitleIndex = null
        lastPosition = 0L
        
        // Reset quality settings for new video
        isAdaptiveQualityEnabled = true
        manualQualityHeight = null
        
        player?.let { exoPlayer ->
            try {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
            } catch (e: Exception) {
                Log.w(TAG, "Unable to fully reset player for new video $videoId", e)
            }
        }
        applyAdaptiveTrackSelectorDefaults()
        _playerState.value = _playerState.value.copy(
            currentVideoId = videoId,
            isBuffering = true,
            error = null,
            hasEnded = false,
            isPrepared = false,
            recoveryAttempted = false,
            currentQuality = 0 // Reset to Auto
        )
    }

    @UnstableApi
    private fun loadMedia(videoStream: VideoStream?, audioStream: AudioStream, preservePosition: Long? = null) {
        player?.let { exoPlayer ->
            try {
                // CRITICAL: Reattach surface before loading (NewPipe approach)
                // Always call setVideoSurface() to ensure player is using the current surface
                surfaceHolder?.let { holder ->
                    val surface = holder.surface
                    if (surface != null && surface.isValid) {
                        exoPlayer.setVideoSurface(surface)
                        Log.d(TAG, "Reattached surface before media load (NewPipe approach)")
                    } else {
                        Log.w(TAG, "Surface holder exists but surface invalid")
                    }
                } ?: run {
                    Log.w(TAG, "No surface holder - will render to placeholder")
                }

                Log.d(TAG, "Preparing media: video=${videoStream?.height ?: -1}p url=${videoStream?.url?.take(40)} audioUrl=${audioStream.url?.take(40)} surfaceReady=$isSurfaceReady")
                val ctx = appContext ?: throw IllegalStateException("EnhancedPlayerManager not initialized with context")
                val dataSourceFactory = sharedDataSourceFactory
                    ?: DefaultDataSource.Factory(
                        ctx,
                        DefaultHttpDataSource.Factory()
                            .setUserAgent("Mozilla/5.0 (X11; Android) FlowPlayer")
                            .setConnectTimeoutMs(7000)
                            .setReadTimeoutMs(7000)
                            .setAllowCrossProtocolRedirects(true)
                    )
                
                // Normalize lists and dedupe by resolution / bitrate / url
                availableVideoStreams = availableVideoStreams
                    .distinctBy { it.url ?: "" }
                    .sortedByDescending { it.height }

                availableAudioStreams = availableAudioStreams
                    .distinctBy { it.url ?: "" }

                if (videoStream != null && !videoStream.url.isNullOrBlank()) {
                    // If the surface is not ready yet we still build the media source so that
                    // playback can begin buffering. The surface attach callback will hook into
                    // the player as soon as it becomes available.
                    if (!isSurfaceReady) {
                        Log.w(TAG, "Surface not ready yet, preparing media and waiting for attach")
                    }

                    // Decide whether the chosen video stream already contains audio (muxed) or is video-only.
                    val hasMuxedAudio = try {
                        // Best-effort: try reflections to detect muxed/adaptive properties
                        val method = videoStream::class.java.methods.firstOrNull { m ->
                            val n = m.name.lowercase()
                            n.contains("mux") || n.contains("hasaudio") || n.contains("isadaptive")
                        }
                        val r = method?.invoke(videoStream)
                        (r as? Boolean) == true
                    } catch (e: Exception) {
                        false
                    }
                    // Detect adaptive formats (HLS/DASH) by URL/path
                    val url = videoStream.url!!
                    val lower = url.lowercase()
                    
                    // Base video/audio source
                    val baseSource = if (lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains("/hls") || lower.contains("/dash")) {
                        // Adaptive stream
                        DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(MediaItem.fromUri(url))
                    } else if (hasMuxedAudio) {
                        // Single progressive stream including both audio+video
                        ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(url))
                    } else {
                        // Video-only + separate audio - merge progressive sources
                        val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(url))

                        val audioSrc = if (!audioStream.url.isNullOrBlank()) {
                            ProgressiveMediaSource.Factory(dataSourceFactory)
                                .createMediaSource(MediaItem.fromUri(audioStream.url!!))
                        } else null

                        if (audioSrc != null) MergingMediaSource(videoSource, audioSrc) else videoSource
                    }
                    
                    exoPlayer.setMediaSource(baseSource)
                    
                    // Immediately prepare - don't wait for anything
                    exoPlayer.prepare()
                    _playerState.value = _playerState.value.copy(isPrepared = true)
                    
                    // If preserving position, seek to it before playing
                    if (preservePosition != null && preservePosition > 0) {
                        exoPlayer.seekTo(preservePosition)
                        Log.d("EnhancedPlayerManager", "Seeking to preserved position: ${preservePosition}ms")
                    }
                    
                    // Auto-play immediately for faster perceived loading
                    exoPlayer.playWhenReady = true
                    
                    Log.d("EnhancedPlayerManager", "Media loaded successfully - auto-playing")
                } else {
                    // Audio-only mode (for music)
                    val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(audioStream.url ?: ""))

                    exoPlayer.setMediaSource(audioSource)
                    exoPlayer.prepare()
                    _playerState.value = _playerState.value.copy(isPrepared = true)
                    exoPlayer.playWhenReady = true
                    
                    Log.d("EnhancedPlayerManager", "Audio loaded successfully - auto-playing")
                }
            } catch (e: Exception) {
                Log.e("EnhancedPlayerManager", "Error loading media", e)
                _playerState.value = _playerState.value.copy(
                    error = "Failed to load media: ${e.message}"
                )
            }
        }
    }

    fun selectSubtitle(index: Int?) {
        if (selectedSubtitleIndex != index) {
            selectedSubtitleIndex = index
            Log.d(TAG, "Subtitle selected (index=$index) - handled by UI overlay")
            // We no longer reload the stream here because subtitles are handled 
            // by a custom Compose overlay in the UI layer. This avoids the 
            // "endless loading" hang and provides a seamless switching experience.
        }
    }

    /**
     * Set playback speed (0.25x to 2.0x)
     */
    fun setPlaybackSpeed(speed: Float) {
        player?.let { exoPlayer ->
            val params = androidx.media3.common.PlaybackParameters(speed)
            exoPlayer.setPlaybackParameters(params)
            _playerState.value = _playerState.value.copy(playbackSpeed = speed)
            Log.d(TAG, "Playback speed set to: ${speed}x")
        }
    }



    fun switchQualityByHeight(height: Int) {
        // Height 0 means Auto (adaptive quality)
        if (height == 0) {
            enableAdaptiveQuality()
            return
        }
        
        // Disable adaptive and set fixed quality
        isAdaptiveQualityEnabled = false
        manualQualityHeight = height
        
        val stream = availableVideoStreams.find { it.height == height }
        if (stream != null) {
            val currentPosition = player?.currentPosition ?: 0L
            val wasPlaying = player?.isPlaying ?: false
            
            Log.d(TAG, "Switching to FIXED quality: ${height}p at position ${currentPosition}ms")
            
            currentVideoStream = stream
            
            // CRITICAL: Disable ALL adaptive behavior for fixed quality
            trackSelector?.let { selector ->
                val params = selector.buildUponParameters()
                    // Disable adaptation completely
                    .setAllowVideoMixedMimeTypeAdaptiveness(false)
                    .setAllowMultipleAdaptiveSelections(false)
                    // Lock to exact height
                    .setMaxVideoSize(stream.width ?: 9999, height)
                    .setMinVideoSize(stream.width ?: 0, height)
                    // Force this quality only
                    .setForceHighestSupportedBitrate(false)
                    .build()
                selector.setParameters(params)
            }
            
            // Load ONLY this specific stream (no alternatives for ExoPlayer to switch between)
            loadMedia(
                videoStream = currentVideoStream,
                audioStream = currentAudioStream ?: availableAudioStreams.firstOrNull() ?: return,
                preservePosition = currentPosition
            )
            
            // Restore position after a brief delay to ensure media is loaded
            player?.also { p ->
                if (currentPosition > 0) {
                    // Use Handler to delay seek slightly
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        p.seekTo(currentPosition)
                        if (wasPlaying) {
                            p.play()
                        }
                        Log.d(TAG, "Position restored to ${currentPosition}ms after quality switch")
                    }, 100)
                } else if (wasPlaying) {
                    p.play()
                }
            }
            
            _playerState.value = _playerState.value.copy(currentQuality = height, effectiveQuality = height)
            Log.d(TAG, "Locked to fixed quality: ${height}p (adaptive disabled)")
        }
    }
    
    private fun enableAdaptiveQuality() {
        isAdaptiveQualityEnabled = true
        manualQualityHeight = null
        
        val currentPosition = player?.currentPosition ?: 0L
        val wasPlaying = player?.isPlaying ?: false
        
        Log.d(TAG, "Enabling adaptive quality mode at position ${currentPosition}ms")
        
        // Reset track selector to allow adaptive quality
        applyAdaptiveTrackSelectorDefaults()
        
        // Select the best quality stream as starting point for adaptation
        currentVideoStream = availableVideoStreams.maxByOrNull { it.height }
        
        loadMedia(
            videoStream = currentVideoStream,
            audioStream = currentAudioStream ?: availableAudioStreams.firstOrNull() ?: return,
            preservePosition = currentPosition
        )
        
        // Restore position
        player?.also { p ->
            if (currentPosition > 0) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    p.seekTo(currentPosition)
                    if (wasPlaying) {
                        p.play()
                    }
                    Log.d(TAG, "Position restored to ${currentPosition}ms in adaptive mode")
                }, 100)
            } else if (wasPlaying) {
                p.play()
            }
        }
        
        _playerState.value = _playerState.value.copy(
            currentQuality = 0,
            effectiveQuality = currentVideoStream?.height ?: 0
        )  // 0 = Auto
        Log.d(TAG, "Adaptive quality mode enabled")
    }

    private fun applyAdaptiveTrackSelectorDefaults() {
        trackSelector?.let { selector ->
            val params = selector.buildUponParameters()
                .setAllowVideoMixedMimeTypeAdaptiveness(true)
                .setAllowMultipleAdaptiveSelections(true)
                .setMaxVideoSize(1920, 1080)
                .clearVideoSizeConstraints()
                .setForceHighestSupportedBitrate(false)
                .build()
            selector.setParameters(params)
        }
    }

    fun switchQuality(height: Int) {
        // Preserve existing API while routing through the new manual quality pipeline
        switchQualityByHeight(height)
    }

    fun switchAudioTrack(index: Int) {
        if (index in availableAudioStreams.indices) {
            val audioStream = availableAudioStreams[index]
            val videoStream = availableVideoStreams.find { it.height == _playerState.value.currentQuality }
            
            val currentPosition = player?.currentPosition ?: 0L
            val wasPlaying = player?.isPlaying ?: false
            
            loadMedia(videoStream, audioStream)
            
            player?.seekTo(currentPosition)
            if (wasPlaying) {
                player?.play()
            }
            
            _playerState.value = _playerState.value.copy(currentAudioTrack = index)
        }
    }

    fun play() {
        player?.play()
    }

    fun pause() {
        player?.pause()
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    private fun reloadCurrentStream(preservePosition: Long? = null, reason: String = "") {
        val video = currentVideoStream
        val audio = currentAudioStream ?: availableAudioStreams.firstOrNull()

        if (video == null || audio == null) {
            Log.w(TAG, "Cannot reload current stream - video=${video != null}, audio=${audio != null}")
            return
        }

        val resumePosition = preservePosition ?: player?.currentPosition ?: 0L
        Log.d(
            TAG,
            "Reloading current stream ${video.height}p at ${resumePosition}ms " +
                if (reason.isNotBlank()) "($reason)" else ""
        )

        player?.stop()
        player?.clearMediaItems()

        loadMedia(
            videoStream = video,
            audioStream = audio,
            preservePosition = resumePosition
        )
    }

    fun getPlayer(): ExoPlayer? = player

    fun getCurrentPosition(): Long = player?.currentPosition ?: 0L

    fun getDuration(): Long = player?.duration ?: 0L

    fun isPlaying(): Boolean = player?.isPlaying ?: false

    fun stop() {
        player?.stop()
        player?.clearMediaItems()
        failedStreamUrls.clear()
        streamErrorCount = 0
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            currentVideoId = null
        )
    }

    fun attachVideoSurface(holder: SurfaceHolder?) {
        if (holder == null) {
            Log.w(TAG, "attachVideoSurface called with null holder")
            surfaceHolder = null
            return
        }

        surfaceHolder = holder

        // A real surface is back, drop any placeholder we were using
        placeholderSurface?.let { placeholder ->
            runCatching { placeholder.release() }
            placeholderSurface = null
        }

        val playerInstance = player
        if (playerInstance == null) {
            Log.d(TAG, "Player not initialized yet; surface will be attached later")
            return
        }

        runCatching {
            // CRITICAL: Use getSurface() approach like NewPipe, not setVideoSurfaceHolder()
            // This ensures the surface is reattached even when the same holder is reused
            val surface = holder.surface
            if (surface != null && surface.isValid) {
                playerInstance.setVideoSurface(surface)
                Log.d(TAG, "Surface attached to player via getSurface() (NewPipe approach)")
                _surfaceReadyFlow.value = true  // Signal that surface is attached
                setSurfaceReady(true)
            } else {
                Log.d(TAG, "Surface holder not yet valid; awaiting callback")
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to bind surface to player", error)
        }
    }

    fun detachVideoSurface() {
        val playerInstance = player
        try {
            if (playerInstance != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val context = appContext
                if (context != null) {
                    if (placeholderSurface == null || placeholderSurface?.isValid == false) {
                        runCatching { placeholderSurface?.release() }
                        placeholderSurface = PlaceholderSurface.newInstanceV17(context, false)
                    }
                    playerInstance.setVideoSurface(placeholderSurface)
                    Log.d(TAG, "Attached placeholder surface (surface detached temporarily)")
                } else {
                    playerInstance.clearVideoSurface()
                }
            } else {
                playerInstance?.clearVideoSurface()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to attach placeholder surface", e)
        }

        // CRITICAL: DON'T set surfaceHolder = null here!
        // When PlayerView is reused (via remember(context)), the surface callback won't fire again
        // Keep the holder reference so we can reattach it in loadMedia()
        // surfaceHolder = null  // <- REMOVED: This was causing "No surface holder" errors
        
        _surfaceReadyFlow.value = false  // Reset flow - waiting for reattachment
        // DON'T set isSurfaceReady = false here!
        // The placeholder surface keeps the renderer alive, so we're still "ready"
        // This prevents black screens when quickly switching between videos
    }
    
    /**
     * Suspend function that waits for a real surface (not placeholder) to be attached.
     * Returns true if surface became ready within timeout, false otherwise.
     * 
     * With reused PlayerView (via remember(context)), we keep the surfaceHolder reference
     * so we can skip waiting if it's still valid.
     */
    suspend fun awaitSurfaceReady(timeoutMillis: Long = 1000): Boolean {
        // Check if surfaceHolder is valid (not just non-null)
        if (surfaceHolder != null) {
            val validSurface = runCatching { surfaceHolder?.surface?.isValid == true }.getOrDefault(false)
            if (validSurface) {
                Log.d(TAG, "Surface already ready, proceeding immediately")
                _surfaceReadyFlow.value = true  // Ensure flow is updated
                return true
            } else {
                // Surface holder exists but surface is invalid (navigation recreated it)
                Log.d(TAG, "Surface holder exists but surface invalid - waiting for callback")
            }
        }
        
        Log.d(TAG, "Waiting for surface to be ready (timeout: ${timeoutMillis}ms)...")
        
        // Wait for the flow to emit true (surface attached) with timeout
        val result = withTimeoutOrNull(timeoutMillis) {
            _surfaceReadyFlow.first { it }
            true
        }
        
        return if (result == true) {
            Log.d(TAG, "Surface became ready!")
            true
        } else {
            Log.w(TAG, "Timeout waiting for surface after ${timeoutMillis}ms")
            // Last resort: check if surface became valid during timeout
            val nowValid = runCatching { surfaceHolder?.surface?.isValid == true }.getOrDefault(false)
            if (nowValid) {
                Log.d(TAG, "Surface valid now despite timeout - proceeding")
                _surfaceReadyFlow.value = true
                return true
            }
            false
        }
    }
    
    fun clearCurrentVideo() {
        player?.stop()
        player?.clearMediaItems()
        failedStreamUrls.clear()
        streamErrorCount = 0
        currentVideoId = null
        currentVideoStream = null
        currentAudioStream = null
        isAdaptiveQualityEnabled = true  // Reset to auto quality
        manualQualityHeight = null
        applyAdaptiveTrackSelectorDefaults()
        _playerState.value = _playerState.value.copy(
            isPlaying = false,
            currentVideoId = null,
            currentQuality = 0  // Reset to Auto
        )
    }

    fun release() {
        try {
            player?.clearVideoSurface()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear surface during release", e)
        }

        placeholderSurface?.let { surface ->
            runCatching { surface.release() }
        }
        placeholderSurface = null

        player?.release()
        player = null
        trackSelector = null
        isSurfaceReady = false
        _playerState.value = EnhancedPlayerState()
        
        // Release cache resources
        try {
            cache?.release()
            cache = null
            databaseProvider = null
        } catch (e: Exception) {
            Log.w("EnhancedPlayerManager", "Error releasing cache", e)
        }
        
        Log.d("EnhancedPlayerManager", "Player released")
    }
    
    /**
     * Get video renderer index to check availability
     * Returns -1 if video renderer is unavailable
     */
    private fun getVideoRendererIndex(): Int {
        val mappedTrackInfo = trackSelector?.currentMappedTrackInfo ?: return -1
        for (i in 0 until mappedTrackInfo.rendererCount) {
            if (mappedTrackInfo.getTrackGroups(i).isEmpty.not() && 
                player?.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
                return i
            }
        }
        return -1
    }
    
    /**
     * Check if video renderer is available
     * This checks if video tracks are actually available and selected
     */
    fun isVideoRendererAvailable(): Boolean {
        player?.let { exoPlayer ->
            // Check if player is in a state where tracks should be available
            val playbackState = exoPlayer.playbackState
            if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING) {
                return true // Assume available until we can check properly
            }

            // Check if we have video tracks available
            val currentTracks = exoPlayer.currentTracks
            val videoTrackGroups = currentTracks.groups.filter { group ->
                group.type == C.TRACK_TYPE_VIDEO && group.isSelected
            }

            if (videoTrackGroups.isNotEmpty()) {
                return true
            }

            // Fallback: check track selector for video renderer info
            val mappedTrackInfo = trackSelector?.currentMappedTrackInfo
            if (mappedTrackInfo != null) {
                for (i in 0 until mappedTrackInfo.rendererCount) {
                    if (mappedTrackInfo.getTrackGroups(i).length > 0 &&
                        exoPlayer.getRendererType(i) == C.TRACK_TYPE_VIDEO) {
                        return true
                    }
                }
            }
        }

        Log.w("EnhancedPlayerManager", "Video renderer unavailable - may cause black screen")
        return false
    }
    
    /**
     * Get current bandwidth estimate in bits per second
     */
    fun getBandwidthEstimate(): Long {
        return bandwidthMeter?.bitrateEstimate ?: 0L
    }
    
    /**
     * Log bandwidth information for debugging
     */
    fun logBandwidthInfo() {
        val estimate = getBandwidthEstimate()
        val estimateMbps = estimate / 1_000_000.0
        Log.d("EnhancedPlayerManager", "Bandwidth estimate: ${"%.2f".format(estimateMbps)} Mbps")
    }
    
    /**
     * Start background service for persistent playback
     */
    fun startBackgroundService(videoId: String, title: String, channel: String, thumbnail: String) {
        appContext?.let { ctx ->
            val intent = Intent(ctx, VideoPlayerService::class.java).apply {
                putExtra(VideoPlayerService.EXTRA_VIDEO_ID, videoId)
                putExtra(VideoPlayerService.EXTRA_VIDEO_TITLE, title)
                putExtra(VideoPlayerService.EXTRA_VIDEO_CHANNEL, channel)
                putExtra(VideoPlayerService.EXTRA_VIDEO_THUMBNAIL, thumbnail)
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ctx.startForegroundService(intent)
            } else {
                ctx.startService(intent)
            }
        }
    }
    
    /**
     * Stop background service
     */
    fun stopBackgroundService() {
        appContext?.let { ctx ->
            ctx.stopService(Intent(ctx, VideoPlayerService::class.java))
        }
    }
    
    /**
     * Get cache size in bytes
     */
    fun getCacheSize(): Long {
        return cache?.cacheSpace ?: 0L
    }
    
    /**
     * Clear all cached data
     */
    fun clearCache() {
        try {
            cache?.let { c ->
                val keys = c.keys
                for (key in keys) {
                    c.removeResource(key)
                }
            }
            Log.d("EnhancedPlayerManager", "Cache cleared")
        } catch (e: Exception) {
            Log.e("EnhancedPlayerManager", "Error clearing cache", e)
        }
    }

    // Enhanced error handling helper methods (inspired by NewPipe)

    /**
     * Save current stream progress state for recovery
     */
    private fun saveStreamProgressState() {
        player?.let { exoPlayer ->
            val currentPosition = exoPlayer.currentPosition
            val currentVideoId = currentVideoId
            // In a real implementation, this would save to persistent storage
            Log.d(TAG, "Saved progress state: videoId=$currentVideoId, position=$currentPosition")
        }
    }

    /**
     * Set recovery state for stream reloading
     */
    private fun setRecovery() {
        Log.d(TAG, "Setting recovery state")
        _playerState.value = _playerState.value.copy(
            isBuffering = true,
            error = null,
            recoveryAttempted = true
        )
    }

    /**
     * Reload the playback manager after an error
     */
    private fun reloadPlaybackManager() {
        try {
            Log.d(TAG, "Reloading playback manager - waiting briefly before retry")
            
            // Brief delay to prevent rapid reload loops (similar to NewPipe's approach)
            Thread.sleep(1000)
            
            player?.let { exoPlayer ->
                val resumePosition = exoPlayer.currentPosition
                // Stop current playback
                exoPlayer.stop()
                exoPlayer.clearMediaItems()

                // If manual quality is selected, keep current stream
                if (!isAdaptiveQualityEnabled && manualQualityHeight != null) {
                    Log.d(TAG, "Manual quality locked (${manualQualityHeight}p) - retrying same stream")
                    reloadCurrentStream(resumePosition, reason = "manual-quality-reload")
                    return
                }

                // Check if current stream has failed - if so, try a different one
                currentVideoStream?.let { videoStream ->
                    if (failedStreamUrls.contains(videoStream.url)) {
                        Log.w(TAG, "Current stream has failed, attempting to use alternative quality")
                        // Try to find a working stream that hasn't failed yet
                        val workingStream = availableVideoStreams
                            .filter { it.url != null && !failedStreamUrls.contains(it.url) }
                            .sortedByDescending { it.height }  // Try highest quality first among working streams
                            .firstOrNull()
                        
                        if (workingStream != null) {
                            Log.d(TAG, "Switching to alternative stream: ${workingStream.height}p")
                            currentVideoStream = workingStream
                            streamErrorCount = 0  // Reset error count for new stream
                        } else {
                            Log.e(TAG, "No working streams available")
                            onPlaybackShutdown()
                            return
                        }
                    }
                }

                // Reload current media if available
                currentVideoStream?.let { videoStream ->
                    val audioStream = currentAudioStream ?: availableAudioStreams.firstOrNull()
                    if (audioStream != null) {
                        loadMedia(
                            videoStream = videoStream,
                            audioStream = audioStream,
                            preservePosition = resumePosition
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading playback manager", e)
            onPlaybackShutdown()
        }
    }

    /**
     * Shutdown playback completely on critical errors
     */
    private fun onPlaybackShutdown() {
        Log.w(TAG, "Playback shutdown initiated")
        try {
            player?.stop()
            player?.clearMediaItems()
            _playerState.value = _playerState.value.copy(
                isPlaying = false,
                isBuffering = false,
                error = "Playback stopped due to critical error"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error during playback shutdown", e)
        }
    }

    /**
     * Create error notification for user awareness
     */
    private fun createErrorNotification(error: PlaybackException) {
        // In a real implementation, this would create a system notification
        Log.e(TAG, "Player error notification: ${error.message}")

        // For now, just log the error. In production, this would show a notification
        // to inform the user about the playback issue
    }

    /**
     * Set surface readiness state
     */
    fun setSurfaceReady(ready: Boolean) {
        isSurfaceReady = ready
        Log.d("EnhancedPlayerManager", "Surface ready: $ready")
        
        // If surface becomes ready and we have pending media, prepare it
        if (ready && player?.currentMediaItem == null && currentVideoStream != null && currentAudioStream != null) {
            Log.d("EnhancedPlayerManager", "Surface ready, preparing pending media")
            loadMedia(currentVideoStream, currentAudioStream!!)
        }
    }
    

    /**
     * Force retry loading media if surface is now ready
     */
    fun retryLoadMediaIfSurfaceReady() {
        if (isSurfaceReady && currentVideoStream != null && currentAudioStream != null) {
            Log.d("EnhancedPlayerManager", "Retrying media load with ready surface")
            loadMedia(currentVideoStream, currentAudioStream!!)
        }
    }

    /**
     * Start playback watchdog to detect stuck playback
     * DISABLED: Causes threading issues with Media3 ExoPlayer
     */
    private fun startPlaybackWatchdog() {
        // Disabled to avoid "Player is accessed on the wrong thread" errors
        // The watchdog was causing more problems than it was solving
        return
    }

    /**
     * Stop playback watchdog
     */
    private fun stopPlaybackWatchdog() {
        isWatchdogActive = false
        stuckCounter = 0
    }

    private fun startBufferingWatchdog() {
        bufferingHandler.removeCallbacks(bufferingRunnable)
        bufferingHandler.postDelayed(bufferingRunnable, 5000) // 5 seconds timeout
    }

    private fun stopBufferingWatchdog() {
        bufferingHandler.removeCallbacks(bufferingRunnable)
    }

    private fun downgradeQualityDueToBandwidth() {
        if (!isAdaptiveQualityEnabled) return
        
        val currentHeight = currentVideoStream?.height ?: return
        
        // Find next lower quality
        val lowerQualityStream = availableVideoStreams
            .filter { it.height < currentHeight }
            .maxByOrNull { it.height } // Highest of the lower qualities (e.g. 1080 -> 720)
            
        if (lowerQualityStream != null) {
            Log.w(TAG, "Bandwidth adaptation: Downgrading from ${currentHeight}p to ${lowerQualityStream.height}p")
            
            currentVideoStream = lowerQualityStream
            
            // Reload media at current position
            val currentPos = player?.currentPosition ?: 0L
            loadMedia(
                videoStream = currentVideoStream,
                audioStream = currentAudioStream ?: availableAudioStreams.firstOrNull() ?: return,
                preservePosition = currentPos
            )
            
            // Update state
            _playerState.value = _playerState.value.copy(
                effectiveQuality = lowerQualityStream.height
            )
            
            // Restart watchdog in case this one is also too slow
            startBufferingWatchdog()
        } else {
            Log.w(TAG, "Bandwidth adaptation: No lower quality available")
        }
    }
    
    /**
     * Attempt quality downgrade when stream is corrupted
     */
    private fun attemptQualityDowngrade() {
        if (!isAdaptiveQualityEnabled) {
            Log.w(TAG, "Manual quality selected - skipping automatic downgrade")
            reloadCurrentStream(reason = "manual-quality-error")
            return
        }

        // Try to find a working stream by filtering out failed URLs
        val workingStreams = availableVideoStreams.filter { 
            it.url != null && !failedStreamUrls.contains(it.url) 
        }
        
        if (workingStreams.isEmpty()) {
            Log.e(TAG, "No working streams available - all ${failedStreamUrls.size} streams failed for video $currentVideoId")
            _playerState.value = _playerState.value.copy(
                error = "Unable to play this video - all quality options failed",
                isPlaying = false,
                isBuffering = false
            )
            onPlaybackShutdown()
            return
        }
        
        // Check if we've tried too many streams (prevent infinite loop)
        if (failedStreamUrls.size >= availableVideoStreams.size) {
            Log.e(TAG, "All available streams exhausted - stopping playback")
            _playerState.value = _playerState.value.copy(
                error = "This video cannot be played - all formats incompatible",
                isPlaying = false,
                isBuffering = false
            )
            onPlaybackShutdown()
            return
        }
        
        // Prefer MP4 over WebM for better compatibility
        // Sort by: 1) MP4 format first, 2) Height ascending (lowest quality)
        val lowerQualityStream = workingStreams
            .sortedWith(compareBy(
                { it.format?.mimeType?.contains("webm", ignoreCase = true) == true }, // false (MP4) comes first
                { it.height } // Then by height ascending
            ))
            .firstOrNull()
        
        if (lowerQualityStream != null) {
            Log.w(TAG, "Downgrading quality to ${lowerQualityStream.height}p (${lowerQualityStream.format?.mimeType}) - Failed: ${failedStreamUrls.size}/${availableVideoStreams.size}")
            currentVideoStream = lowerQualityStream
            streamErrorCount = 0  // Reset error counter for new stream
            
            _playerState.value = _playerState.value.copy(
                currentQuality = lowerQualityStream.height,
                isBuffering = true
            )
            
            // Reload with lower quality
            loadMedia(lowerQualityStream, currentAudioStream ?: availableAudioStreams.firstOrNull() ?: return)
        } else {
            Log.e(TAG, "No suitable alternative stream found")
            _playerState.value = _playerState.value.copy(
                error = "Unable to play this video",
                isPlaying = false,
                isBuffering = false
            )
            onPlaybackShutdown()
        }
    }
}

data class EnhancedPlayerState(
    val currentVideoId: String? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isPrepared: Boolean = false,
    val hasEnded: Boolean = false,
    val currentQuality: Int = 0,
    val effectiveQuality: Int = 0,
    val currentAudioTrack: Int = 0,
    val availableQualities: List<QualityOption> = emptyList(),
    val availableAudioTracks: List<AudioTrackOption> = emptyList(),
    val availableSubtitles: List<SubtitleOption> = emptyList(),
    val error: String? = null,
    val recoveryAttempted: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val isSkipSilenceEnabled: Boolean = false
)

data class QualityOption(
    val height: Int,
    val label: String,
    val bitrate: Long
)

data class AudioTrackOption(
    val index: Int,
    val label: String,
    val language: String,
    val bitrate: Long
)

data class SubtitleOption(
    val url: String,
    val language: String,
    val label: String,
    val isAutoGenerated: Boolean
)
