package com.flow.youtube.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.flow.youtube.MainActivity
import com.flow.youtube.R
import com.flow.youtube.data.model.ParametricEQ
import com.flow.youtube.player.audio.CustomEqualizerAudioProcessor
import dagger.hilt.android.AndroidEntryPoint
import androidx.media3.session.DefaultMediaNotificationProvider
import com.google.common.collect.ImmutableList
import androidx.media3.session.SessionCommand
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionResult
import androidx.media3.common.Player
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.os.Bundle
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.flow.youtube.data.download.DownloadUtil
import com.flow.youtube.extensions.setOffloadEnabled
import com.flow.youtube.utils.MusicPlayerUtils
import com.flow.youtube.utils.NetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.content.Context
import kotlin.math.min

@AndroidEntryPoint
class Media3MusicService : MediaLibraryService() {

    companion object {
        private const val TAG = "Media3MusicService"
        private const val ACTION_TOGGLE_SHUFFLE = "ACTION_TOGGLE_SHUFFLE"
        private const val ACTION_TOGGLE_REPEAT = "ACTION_TOGGLE_REPEAT"
        const val ACTION_SET_EQ = "ACTION_SET_EQ"
        
        private const val MAX_RETRY_PER_SONG = 5
        private const val BASE_RETRY_DELAY_MS = 3000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val FAILED_SONGS_CACHE_SIZE = 50
        
        private val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
        private val CommandToggleRepeat = SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY)
        private val CommandSetEq = SessionCommand(ACTION_SET_EQ, Bundle.EMPTY)
        
        private const val ACTION_TOGGLE_LIKE = "ACTION_TOGGLE_LIKE"
        private val CommandToggleLike = SessionCommand(ACTION_TOGGLE_LIKE, Bundle.EMPTY)
        
        /**
         * Current audio session ID for the music player.
         * External audio processors (like James DSP) can use this to apply effects.
         * Value is 0 when no active session exists.
         */
        @Volatile
        var currentAudioSessionId: Int = 0
            private set
    }

    private lateinit var mediaLibrarySession: MediaLibrarySession
    private lateinit var player: ExoPlayer
    private val customEqualizer = CustomEqualizerAudioProcessor()
    private lateinit var connectivityObserver: NetworkConnectivityObserver
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * Coroutine job that defers WakeLock/WifiLock release by 30 seconds after playback pauses.
     * Prevents the CPU from entering deep sleep during brief buffering/focus-loss events.
     */
    private var lockReleaseJob: Job? = null
    
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val retryCountMap = mutableMapOf<String, Int>()
    
    private val recentlyFailedSongs = LinkedHashSet<String>()
    
    private var pendingRetryJob: Job? = null
    
    private var waitingForNetwork = false

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        
        connectivityObserver = NetworkConnectivityObserver(this)
        connectivityObserver.startObserving()
        
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Flow:MusicServiceWakeLock")
            wakeLock?.setReferenceCounted(false)
            
            val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Flow:MusicServiceWifiLock")
            wifiLock?.setReferenceCounted(false)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire locks", e)
        }
        
        serviceScope.launch {
            connectivityObserver.isConnected.collectLatest { isConnected ->
                if (isConnected && waitingForNetwork) {
                    Log.d(TAG, "Network restored, triggering retry")
                    waitingForNetwork = false
                    triggerRetryAfterNetworkRestore()
                }
            }
        }
        
        serviceScope.launch {
            com.flow.youtube.player.EnhancedMusicPlayerManager.isLiked.collectLatest { 
                updateNotification()
            }
        }
        
        initializePlayer()
        initializeSession()
    }

    private fun initializePlayer() {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
            
        val mediaSourceFactory = DefaultMediaSourceFactory(downloadUtil.getPlayerDataSourceFactory())
        
        val renderersFactory = object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: android.content.Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): androidx.media3.exoplayer.audio.AudioSink? {
                return androidx.media3.exoplayer.audio.DefaultAudioSink.Builder(context)
                    .setAudioProcessors(arrayOf(customEqualizer))
                    .build()
            }
        }.setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        // OPTIMIZED: Aggressive buffering for faster music startup
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2500,  // Min buffer (2.5s)
                30000, // Max buffer (30s)
                1000,  // Buffer for playback (1s) - faster start
                1500   // Buffer for rebuffer (1.5s)
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
            
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setRenderersFactory(renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .build()
        
        // Expose audio session ID for external audio processors (James DSP, etc.)
        currentAudioSessionId = player.audioSessionId
        Log.i(TAG, "Audio session initialized - Session ID: $currentAudioSessionId")
        Log.i(TAG, "External audio processors can target this session for effects")
        
        player.setOffloadEnabled(true)
            
        player.addListener(object : Player.Listener {
            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateNotification()
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                updateNotification()
            }
            
            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(error)
            }
            
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    retryCountMap.clear()
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    player.currentMediaItem?.mediaId?.let { mediaId ->
                        retryCountMap.remove(mediaId)
                        recentlyFailedSongs.remove(mediaId)
                    }
                }
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    lockReleaseJob?.cancel()
                    lockReleaseJob = null
                    acquireLocks()
                } else {
                    lockReleaseJob?.cancel()
                    lockReleaseJob = serviceScope.launch {
                        delay(30_000L)
                        releaseLocks()
                    }
                }
            }
        })
    }

    /**
     * Main error handling logic with error-type-specific handlers.
     */
    private fun handlePlayerError(error: PlaybackException) {
        val mediaId = player.currentMediaItem?.mediaId
        if (mediaId == null) {
            Log.e(TAG, "Player error with no current media item", error)
            return
        }
        
        Log.e(TAG, "Playback error for $mediaId: ${error.errorCodeName}", error)
        
        if (recentlyFailedSongs.contains(mediaId)) {
            Log.w(TAG, "$mediaId is in recently failed list, skipping to next")
            skipToNext()
            return
        }
        
        val currentRetry = retryCountMap.getOrDefault(mediaId, 0)
        
        if (currentRetry >= MAX_RETRY_PER_SONG) {
            handleFinalFailure(mediaId)
            return
        }
        
        when {
            isExpiredUrlError(error) -> handleExpiredUrlError(mediaId, currentRetry)
            isRangeNotSatisfiableError(error) -> handleRangeError(mediaId, currentRetry)
            isNetworkError(error) -> handleNetworkError(mediaId, currentRetry)
            else -> handleGenericError(mediaId, currentRetry)
        }
    }

    /**
     * Check if error is due to expired URL (HTTP 403)
     */
    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        val cause = error.cause?.toString() ?: ""
        return error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
               (cause.contains("403") || cause.contains("Forbidden"))
    }

    /**
     * Check if error is Range Not Satisfiable (HTTP 416)
     */
    private fun isRangeNotSatisfiableError(error: PlaybackException): Boolean {
        val cause = error.cause?.toString() ?: ""
        return error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS &&
               (cause.contains("416") || cause.contains("Range Not Satisfiable"))
    }

    /**
     * Check if error is network-related
     */
    private fun isNetworkError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
               error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
               error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    }

    /**
     * Handle expired URL error (HTTP 403) - aggressive cache clear needed
     */
    private fun handleExpiredUrlError(mediaId: String, currentRetry: Int) {
        Log.d(TAG, "Handling expired URL error for $mediaId (retry $currentRetry)")
        
        downloadUtil.performAggressiveCacheClear(mediaId)
        
        scheduleRetry(mediaId, currentRetry, delayMultiplier = 1.0)
    }

    /**
     * Handle Range Not Satisfiable error (HTTP 416) - seek position issue
     */
    private fun handleRangeError(mediaId: String, currentRetry: Int) {
        Log.d(TAG, "Handling range error for $mediaId (retry $currentRetry)")
        
        downloadUtil.performAggressiveCacheClear(mediaId)
        
        retryCountMap[mediaId] = currentRetry + 1
        
        serviceScope.launch {
            delay(BASE_RETRY_DELAY_MS)
            try {
                player.seekTo(0)
                player.prepare()
                player.play()
            } catch (e: Exception) {
                Log.e(TAG, "Range error retry failed for $mediaId", e)
            }
        }
    }

    /**
     * Handle network-related errors - wait for connectivity
     */
    private fun handleNetworkError(mediaId: String, currentRetry: Int) {
        Log.d(TAG, "Handling network error for $mediaId (retry $currentRetry)")
        
        if (!connectivityObserver.checkCurrentConnectivity()) {
            Log.d(TAG, "No network connectivity, waiting...")
            waitingForNetwork = true
            retryCountMap[mediaId] = currentRetry + 1
        } else {
            downloadUtil.invalidateUrlCache(mediaId)
            scheduleRetry(mediaId, currentRetry, delayMultiplier = 2.0)
        }
    }

    /**
     * Handle generic errors with exponential backoff
     */
    private fun handleGenericError(mediaId: String, currentRetry: Int) {
        Log.d(TAG, "Handling generic error for $mediaId (retry $currentRetry)")
        
        downloadUtil.invalidateUrlCache(mediaId)
        scheduleRetry(mediaId, currentRetry, delayMultiplier = 1.5)
    }

    /**
     * Schedule a retry with exponential backoff
     */
    private fun scheduleRetry(mediaId: String, currentRetry: Int, delayMultiplier: Double) {
        retryCountMap[mediaId] = currentRetry + 1
        
        val baseDelay = (BASE_RETRY_DELAY_MS * delayMultiplier).toLong()
        val delay = min(baseDelay * (1L shl currentRetry), MAX_RETRY_DELAY_MS)
        
        Log.d(TAG, "Scheduling retry ${currentRetry + 1}/$MAX_RETRY_PER_SONG for $mediaId in ${delay}ms")
        
        pendingRetryJob?.cancel()
        pendingRetryJob = serviceScope.launch {
            delay(delay)
            try {
                val position = player.currentPosition
                player.prepare()
                player.seekTo(position)
                player.play()
            } catch (e: Exception) {
                Log.e(TAG, "Scheduled retry failed for $mediaId", e)
            }
        }
    }

    /**
     * Handle final failure after all retries exhausted
     */
    private fun handleFinalFailure(mediaId: String) {
        Log.w(TAG, "All retries exhausted for $mediaId, marking as failed")
        
        retryCountMap.remove(mediaId)
        
        if (recentlyFailedSongs.size >= FAILED_SONGS_CACHE_SIZE) {
            recentlyFailedSongs.iterator().next().let { recentlyFailedSongs.remove(it) }
        }
        recentlyFailedSongs.add(mediaId)
        
        skipToNext()
    }

    /**
     * Skip to next track
     */
    private fun skipToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            player.prepare()
            player.play()
        }
    }

    /**
     * Trigger retry after network is restored
     */
    private fun triggerRetryAfterNetworkRestore() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val currentRetry = retryCountMap.getOrDefault(mediaId, 0)
        
        if (currentRetry < MAX_RETRY_PER_SONG) {
            Log.d(TAG, "Triggering retry after network restore for $mediaId")
            downloadUtil.invalidateUrlCache(mediaId)
            
            serviceScope.launch {
                delay(1000) 
                try {
                    val position = player.currentPosition
                    player.prepare()
                    player.seekTo(position)
                    player.play()
                } catch (e: Exception) {
                    Log.e(TAG, "Network restore retry failed for $mediaId", e)
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun initializeSession() {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, LibrarySessionCallback())
            .setSessionActivity(pendingIntent)
            .build()
            
        setMediaNotificationProvider(CustomNotificationProvider())
            
        updateNotification()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    /**
     * Prevent aggressive OEM ROMs (Xiaomi MIUI, Samsung OneUI, Huawei EMUI, CRDroid)
     * from killing the music service when the app task is swiped from recents.
     *
     * Without this override Android calls stopSelf() via the default onTaskRemoved,
     * which destroys the foreground service and stops background music playback.
     * Overriding without calling super keeps the service alive.
     */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (::player.isInitialized && player.isPlaying) {
            return
        }
        try {
            if (::mediaLibrarySession.isInitialized) {
            }
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        // Clear audio session ID so external processors know we're gone
        currentAudioSessionId = 0
        Log.i(TAG, "Audio session destroyed")
        
        lockReleaseJob?.cancel()
        lockReleaseJob = null

        if (::connectivityObserver.isInitialized) {
            connectivityObserver.stopObserving()
        }
        
        pendingRetryJob?.cancel()
        
        if (::mediaLibrarySession.isInitialized) {
            mediaLibrarySession.release()
        }
        if (::player.isInitialized) {
            player.release()
        }
        releaseLocks()
        super.onDestroy()
    }
    
    private fun acquireLocks() {
        if (wakeLock?.isHeld != true) {
            wakeLock?.acquire()
        }
        if (wifiLock?.isHeld != true) {
            wifiLock?.acquire()
        }
    }
    
    private fun releaseLocks() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }

    private fun updateNotification() {
        if (!::mediaLibrarySession.isInitialized) return
        
        val isLiked = com.flow.youtube.player.EnhancedMusicPlayerManager.isLiked.value
        
        val likeButton = CommandButton.Builder()
            .setDisplayName(if (isLiked) "Unlike" else "Like")
            .setIconResId(if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like)
            .setSessionCommand(CommandToggleLike)
            .setEnabled(true)
            .build()
        
        val shuffleIcon = if (player.shuffleModeEnabled) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle
        
        val repeatIcon = when (player.repeatMode) {
             Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one_on
             Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat_on
             else -> R.drawable.ic_repeat
        }

        val shuffleButton = CommandButton.Builder()
            .setDisplayName("Shuffle")
            .setIconResId(shuffleIcon)
            .setSessionCommand(CommandToggleShuffle)
            .build()

        val repeatButton = CommandButton.Builder()
            .setDisplayName("Repeat")
            .setIconResId(repeatIcon)
            .setSessionCommand(CommandToggleRepeat)
            .build()
            
        mediaLibrarySession.setCustomLayout(listOf(likeButton, shuffleButton, repeatButton))
    }

    @OptIn(UnstableApi::class)
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val validCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS.buildUpon()
                .add(CommandToggleShuffle)
                .add(CommandToggleRepeat)
                .add(CommandToggleLike)
                .add(CommandSetEq)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(validCommands)
                .build()
        }
        
        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
             if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                 com.flow.youtube.player.EnhancedMusicPlayerManager.emitToggleLikeEvent()
                 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
             }
             
             if (customCommand.customAction == ACTION_SET_EQ) {
                 val eqJson = args.getString("EQ_PROFILE")
                 if (eqJson != null) {
                     try {
                         val profile = Json.decodeFromString<ParametricEQ>(eqJson)
                         customEqualizer.applyProfile(profile)
                     } catch (e: Exception) {
                         android.util.Log.e(TAG, "Failed to apply EQ profile", e)
                     }
                 }
                 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
             }

             if (customCommand.customAction == ACTION_TOGGLE_SHUFFLE) {
                 player.shuffleModeEnabled = !player.shuffleModeEnabled
                 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
             }

             if (customCommand.customAction == ACTION_TOGGLE_REPEAT) {
                 val newMode = when (player.repeatMode) {
                     Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                     Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                     else -> Player.REPEAT_MODE_OFF
                 }
                 player.repeatMode = newMode
                 return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
             }
             
             return super.onCustomCommand(session, controller, customCommand, args)
        }
    }
    
    @OptIn(UnstableApi::class)
    private inner class CustomNotificationProvider : DefaultMediaNotificationProvider(this@Media3MusicService) {
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean
        ): ImmutableList<CommandButton> {
            val playPauseButton = CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                .setIconResId(if (showPauseButton) R.drawable.ic_pause else R.drawable.ic_play)
                .setDisplayName(if (showPauseButton) "Pause" else "Play")
                .setEnabled(playerCommands.contains(Player.COMMAND_PLAY_PAUSE))
                .build()
            
            val prevButton = CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                .setIconResId(R.drawable.ic_previous)
                .setDisplayName("Previous")
                .setEnabled(playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
                .build()
                
            val nextButton = CommandButton.Builder()
                .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                .setIconResId(R.drawable.ic_next)
                .setDisplayName("Next")
                .setEnabled(playerCommands.contains(Player.COMMAND_SEEK_TO_NEXT))
                .build()

            var shuffleButton: CommandButton? = null
            var repeatButton: CommandButton? = null
            var likeButton: CommandButton? = null
            
            for (button in customLayout) {
                if (button.sessionCommand?.customAction == ACTION_TOGGLE_SHUFFLE) {
                    shuffleButton = button
                } else if (button.sessionCommand?.customAction == ACTION_TOGGLE_REPEAT) {
                    repeatButton = button
                } else if (button.sessionCommand?.customAction == ACTION_TOGGLE_LIKE) {
                    likeButton = button
                }
            }
            
            val builder = ImmutableList.builder<CommandButton>()
            
            likeButton?.let { builder.add(it) }
            shuffleButton?.let { builder.add(it) }
            builder.add(prevButton)
            builder.add(playPauseButton)
            builder.add(nextButton)
            repeatButton?.let { builder.add(it) }
            
            return builder.build()
        }
    }
}
