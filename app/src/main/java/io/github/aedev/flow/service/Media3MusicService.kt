package io.github.aedev.flow.service

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Intent
import androidx.media3.datasource.HttpDataSource
import java.util.Locale
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
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.ParametricEQ
import io.github.aedev.flow.player.audio.CustomEqualizerAudioProcessor
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
import io.github.aedev.flow.data.download.DownloadUtil
import io.github.aedev.flow.extensions.setOffloadEnabled
import io.github.aedev.flow.utils.MusicPlayerUtils
import io.github.aedev.flow.utils.NetworkConnectivityObserver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.newmusic.InnertubeMusicService
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.WatchEndpoint
import android.os.PowerManager
import android.net.wifi.WifiManager
import android.content.Context
import android.os.Process
import kotlin.math.min

@AndroidEntryPoint
class Media3MusicService : MediaLibraryService() {

    companion object {
        private const val TAG = "Media3MusicService"
        private const val ACTION_TOGGLE_SHUFFLE = "ACTION_TOGGLE_SHUFFLE"
        private const val ACTION_TOGGLE_REPEAT = "ACTION_TOGGLE_REPEAT"
        private const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_SET_EQ = "ACTION_SET_EQ"
        
        private const val MAX_RETRY_PER_SONG = 5
        private const val BASE_RETRY_DELAY_MS = 3000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val FAILED_SONGS_CACHE_SIZE = 50
        private const val RECOVERY_SUCCESS_GRACE_MS = 2 * 60 * 1000L
        
        private val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
        private val CommandToggleRepeat = SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY)
        private val CommandStop = SessionCommand(ACTION_STOP, Bundle.EMPTY)
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
    private var automixJob: Job? = null
    
    private val retryCountMap = mutableMapOf<String, Int>()
    private val lastPlaybackErrorAtMap = mutableMapOf<String, Long>()
    
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
            @Suppress("DEPRECATION")
            wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL, "Flow:MusicServiceWifiLock")
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
            io.github.aedev.flow.player.EnhancedMusicPlayerManager.isLiked.collectLatest {
                updateNotification()
            }
        }

        serviceScope.launch {
            val prefs = io.github.aedev.flow.data.local.PlayerPreferences(this@Media3MusicService)
            var lastQuality: io.github.aedev.flow.data.local.MusicAudioQuality? = null
            prefs.musicAudioQuality.collect { quality ->
                val previous = lastQuality
                lastQuality = quality
                if (previous != null && previous != quality) {
                    applyMusicQualityChange()
                }
            }
        }

        initializePlayer()
        initializeSession()
    }

    private fun applyMusicQualityChange() {
        Log.d(TAG, "Music quality changed — clearing resolution caches")
        try {
            downloadUtil.clearUrlCache()
            MusicPlayerUtils.clearPlaybackCache()
            io.github.aedev.flow.player.EnhancedMusicPlayerManager.clearUrlCache()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear caches on quality change: ${e.message}")
        }

        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        if (downloadUtil.isFullyDownloaded(mediaId)) return

        try {
            val position = player.currentPosition
            val wasPlaying = player.playWhenReady
            downloadUtil.performAggressiveCacheClear(mediaId)
            refreshCurrentMediaItem(mediaId, position)
            player.prepare()
            player.playWhenReady = wasPlaying
            Log.d(TAG, "Re-streaming $mediaId at new quality from ${position}ms")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to reload current track on quality change: ${e.message}")
        }
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
                if (
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                ) {
                    player.seekTo(0L)
                }

                if (reason != Player.MEDIA_ITEM_TRANSITION_REASON_SEEK) {
                    retryCountMap.clear()
                    lastPlaybackErrorAtMap.clear()
                }

                mediaItem?.let { item ->
                    val videoId = item.mediaId
                    val title = item.mediaMetadata.title?.toString()
                    val artist = item.mediaMetadata.artist?.toString()
                    
                    if (!videoId.isNullOrBlank()) {
                        resolveAutomix(videoId)
                    }

                    if (!videoId.isNullOrBlank() && !title.isNullOrBlank() && !artist.isNullOrBlank()) {
                        serviceScope.launch(Dispatchers.IO) {
                            try {
                                Log.d(TAG, "Pre-warming lyrics cache in background for: $videoId - \"$title\"")
                                val helper = io.github.aedev.flow.data.lyrics.LyricsHelper(this@Media3MusicService)
                                helper.getLyrics(videoId, title, artist, 180, null, this@Media3MusicService)
                            } catch (e: Exception) {
                                Log.w(TAG, "Lyrics pre-warm background task encountered error: ${e.message}")
                            }
                        }
                    }
                }
            }
            
            override fun onPlaybackStateChanged(playbackState: Int) {
                updateLocks(isPlaybackActive())
                if (playbackState == Player.STATE_READY) {
                    player.currentMediaItem?.mediaId?.let { mediaId ->
                        val lastErrorAt = lastPlaybackErrorAtMap[mediaId] ?: 0L
                        if (System.currentTimeMillis() - lastErrorAt > RECOVERY_SUCCESS_GRACE_MS) {
                            retryCountMap.remove(mediaId)
                            recentlyFailedSongs.remove(mediaId)
                            lastPlaybackErrorAtMap.remove(mediaId)
                        }
                    }
                }
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                updateLocks(isPlaybackActive())
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateLocks(isPlaybackActive())
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
        
        Log.e(TAG, "Playback error for $mediaId: ${error.errorCodeName} (code=${error.errorCode})", error)
        lastPlaybackErrorAtMap[mediaId] = System.currentTimeMillis()
        
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

        performAggressiveCacheClear(mediaId)
        
        when {
            isAudioRendererError(error) -> {
                Log.d(TAG, "AudioTrack error detected (${error.errorCode}), performing safe recovery")
                handleAudioRendererError(mediaId, currentRetry)
            }
            isRangeNotSatisfiableError(error) -> {
                Log.d(TAG, "Range Not Satisfiable (416) detected, performing strict recovery")
                handleRangeNotSatisfiableError(mediaId, currentRetry)
            }
            isPageReloadError(error) -> {
                Log.d(TAG, "Page reload error detected, performing strict recovery")
                handlePageReloadError(mediaId, currentRetry)
            }
            isExpiredUrlError(error) -> {
                Log.d(TAG, "Expired URL (403) detected, refreshing stream URL")
                notifyMusicWarning(getString(R.string.music_playback_warning_forbidden))
                handleExpiredUrlError(mediaId, currentRetry)
            }
            isFileNotFoundError(error) -> {
                Log.d(TAG, "Cache file missing (ENOENT) detected, refreshing stream")
                handleFileNotFoundError(mediaId, currentRetry)
            }
            !connectivityObserver.checkCurrentConnectivity() || isNetworkError(error) -> {
                Log.d(TAG, "Network-related error detected, waiting for connection")
                notifyMusicWarning(getString(R.string.music_playback_warning_network))
                handleNetworkError(mediaId, currentRetry)
            }
            else -> {
                Log.d(TAG, "Generic/IO error detected (${error.errorCode}), attempting recovery")
                handleGenericError(mediaId, currentRetry)
            }
        }
    }

    private fun performAggressiveCacheClear(mediaId: String) {
        Log.d(TAG, "Performing aggressive cache clear for $mediaId")
        try {
            downloadUtil.performAggressiveCacheClear(mediaId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear download cache for $mediaId", e)
        }
        try {
            MusicPlayerUtils.forceRefreshForVideo(mediaId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear decryption cache for $mediaId", e)
        }
        try {
            io.github.aedev.flow.player.EnhancedMusicPlayerManager.invalidateResolvedStream(mediaId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear resolved stream cache for $mediaId", e)
        }
    }

    private fun getHttpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    private fun isExpiredUrlError(error: PlaybackException): Boolean {
        return getHttpResponseCode(error) == 403
    }

    private fun isRangeNotSatisfiableError(error: PlaybackException): Boolean {
        return getHttpResponseCode(error) == 416
    }

    private fun isPageReloadError(error: PlaybackException): Boolean {
        val errorMessage = error.message?.lowercase(Locale.ROOT) ?: ""
        val causeMessage = error.cause?.message?.lowercase(Locale.ROOT) ?: ""
        val reloadKeywords = listOf("page needs to be reloaded", "page must be reloaded", "reload")
        return reloadKeywords.any { errorMessage.contains(it) || causeMessage.contains(it) }
    }

    private fun isFileNotFoundError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
               (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
    }

    private fun isAudioRendererError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
               error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED
    }

    private fun isNetworkError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
               error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT
    }

    private fun handleAudioRendererError(mediaId: String, currentRetry: Int) {
        retryCountMap[mediaId] = currentRetry + 1
        retryJobCancel()
        pendingRetryJob = serviceScope.launch {
            try {
                player.pause()
                delay(BASE_RETRY_DELAY_MS * 3)
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    val currentPosition = player.currentPosition
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioTrack recovery failed", e)
                handleFinalFailure(mediaId)
            }
        }
    }

    private fun handleRangeNotSatisfiableError(mediaId: String, currentRetry: Int) {
        retryCountMap[mediaId] = currentRetry + 1
        retryJobCancel()
        pendingRetryJob = serviceScope.launch {
            delay(BASE_RETRY_DELAY_MS)
            try {
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    player.seekTo(currentIndex, 0L)
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Range retry failed", e)
            }
        }
    }

    private fun handlePageReloadError(mediaId: String, currentRetry: Int) {
        retryCountMap[mediaId] = currentRetry + 1
        retryJobCancel()
        pendingRetryJob = serviceScope.launch {
            delay(BASE_RETRY_DELAY_MS * 2)
            try {
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    val currentPosition = player.currentPosition
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Page reload recovery failed", e)
            }
        }
    }

    private fun handleExpiredUrlError(mediaId: String, currentRetry: Int) {
        retryCountMap[mediaId] = currentRetry + 1
        retryJobCancel()
        pendingRetryJob = serviceScope.launch {
            delay(BASE_RETRY_DELAY_MS)
            try {
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    val currentPosition = player.currentPosition
                    downloadUtil.invalidateUrlCache(mediaId)
                    MusicPlayerUtils.forceRefreshForVideo(mediaId)
                    io.github.aedev.flow.player.EnhancedMusicPlayerManager.invalidateResolvedStream(mediaId)
                    player.stop()
                    refreshCurrentMediaItem(mediaId, currentPosition)
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Expired URL recovery failed", e)
            }
        }
    }

    private fun refreshCurrentMediaItem(mediaId: String, positionMs: Long) {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return

        val currentItem = player.getMediaItemAt(currentIndex)
        val refreshedItem = currentItem.buildUpon()
            .setUri("music://$mediaId")
            .setMediaId(mediaId)
            .setCustomCacheKey(mediaId)
            .build()

        player.replaceMediaItem(currentIndex, refreshedItem)
        player.seekTo(currentIndex, positionMs)
    }

    private fun handleFileNotFoundError(mediaId: String, currentRetry: Int) {
        retryCountMap[mediaId] = currentRetry + 1
        retryJobCancel()
        pendingRetryJob = serviceScope.launch {
            delay(BASE_RETRY_DELAY_MS)
            try {
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    val currentPosition = player.currentPosition
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "File not found recovery failed", e)
            }
        }
    }

    private fun handleNetworkError(mediaId: String, currentRetry: Int) {
        if (!connectivityObserver.checkCurrentConnectivity()) {
            Log.d(TAG, "No network connectivity, waiting for connection...")
            waitingForNetwork = true
            retryCountMap[mediaId] = currentRetry + 1
        } else {
            scheduleRetry(mediaId, currentRetry, delayMultiplier = 2.0)
        }
    }

    private fun handleGenericError(mediaId: String, currentRetry: Int) {
        scheduleRetry(mediaId, currentRetry, delayMultiplier = 1.5)
    }

    private fun retryJobCancel() {
        pendingRetryJob?.cancel()
        pendingRetryJob = null
    }

    private fun scheduleRetry(mediaId: String, currentRetry: Int, delayMultiplier: Double) {
        retryCountMap[mediaId] = currentRetry + 1
        val baseDelay = (BASE_RETRY_DELAY_MS * delayMultiplier).toLong()
        val delay = min(baseDelay * (1L shl currentRetry), MAX_RETRY_DELAY_MS)
        
        Log.d(TAG, "Scheduling retry ${currentRetry + 1}/$MAX_RETRY_PER_SONG for $mediaId in ${delay}ms")
        
        retryJobCancel()
        pendingRetryJob = serviceScope.launch {
            delay(delay)
            try {
                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    val position = player.currentPosition
                    player.seekTo(currentIndex, position)
                    player.prepare()
                    player.play()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Scheduled retry failed for $mediaId", e)
            }
        }
    }

    private fun handleFinalFailure(mediaId: String) {
        Log.w(TAG, "All retries exhausted for $mediaId, marking as failed")
        notifyMusicWarning(getString(R.string.music_playback_warning_final))
        retryCountMap.remove(mediaId)
        lastPlaybackErrorAtMap.remove(mediaId)
        if (recentlyFailedSongs.size >= FAILED_SONGS_CACHE_SIZE) {
            recentlyFailedSongs.iterator().next().let { recentlyFailedSongs.remove(it) }
        }
        recentlyFailedSongs.add(mediaId)
        skipToNext()
    }

    private fun skipToNext() {
        when {
            player.hasNextMediaItem() -> {
                player.seekToNextMediaItem()
                player.prepare()
                player.play()
            }
            player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0 -> {
                player.seekTo(0, 0L)
                player.prepare()
                player.play()
            }
        }
    }

    private fun notifyMusicWarning(message: String) {
        io.github.aedev.flow.player.EnhancedMusicPlayerManager.showPlaybackWarning(message)
    }

    private fun stopPlaybackAndService() {
        retryJobCancel()
        waitingForNetwork = false
        if (::player.isInitialized) {
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
        io.github.aedev.flow.player.EnhancedMusicPlayerManager.clearCurrentTrack()
        releaseLocks()
        stopSelf()
    }

    private fun triggerRetryAfterNetworkRestore() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val currentRetry = retryCountMap.getOrDefault(mediaId, 0)
        
        if (currentRetry < MAX_RETRY_PER_SONG) {
            Log.d(TAG, "Triggering retry after network restore for $mediaId")
            performAggressiveCacheClear(mediaId)
            
            serviceScope.launch {
                delay(1000) 
                try {
                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex != C.INDEX_UNSET) {
                        val position = player.currentPosition
                        player.seekTo(currentIndex, position)
                        player.prepare()
                        player.play()
                    }
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
        stopSelf()
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

    private fun isPlaybackActive(): Boolean {
        if (!::player.isInitialized) return false
        return player.isPlaying ||
            player.playbackState == Player.STATE_BUFFERING ||
            (player.playWhenReady &&
                player.playbackState != Player.STATE_IDLE &&
                player.playbackState != Player.STATE_ENDED)
    }

    private fun updateLocks(isPlaybackActive: Boolean) {
        lockReleaseJob?.cancel()
        lockReleaseJob = null

        if (isPlaybackActive) {
            acquireLocks()
            return
        }

        lockReleaseJob = serviceScope.launch {
            delay(30_000L)
            if (!isPlaybackActive()) {
                releaseLocks()
                if (!isAppInForeground()) {
                    stopSelf()
                }
            }
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun releaseWifiLock() {
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
        }
    }

    private fun releaseLocks() {
        releaseWakeLock()
        releaseWifiLock()
    }

    private fun isAppInForeground(): Boolean {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val runningProcess = activityManager.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }
        return when (runningProcess?.importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND,
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> true
            else -> false
        }
    }

    private fun resolveAutomix(trackId: String) {
        automixJob?.cancel()
        automixJob = serviceScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "Resolving automix for trackId: $trackId")
                val primaryResult = YouTube.next(WatchEndpoint(playlistId = "RDAMVM$trackId"))
                var recommended = primaryResult.getOrNull()?.items.orEmpty()

                primaryResult.getOrNull()?.endpoint?.playlistId
                    ?.takeIf { it.isNotBlank() && recommended.size <= 1 }
                    ?.let { playlistId ->
                        Log.d(TAG, "Tier 1 preview small, resolving nested automix playlist")
                        recommended = YouTube.next(WatchEndpoint(playlistId = playlistId))
                            .getOrNull()
                            ?.items
                            .orEmpty()
                    }
                
                if (recommended.size <= 1) {
                    Log.d(TAG, "Automix playlist empty or small, trying video radio")
                    val radioResult = YouTube.next(WatchEndpoint(videoId = trackId))
                    recommended = radioResult.getOrNull()?.items.orEmpty()
                }
                
                if (recommended.isEmpty()) {
                    Log.d(TAG, "Radio empty, trying related endpoint")
                    val relatedEndpoint = primaryResult.getOrNull()?.relatedEndpoint
                        ?: YouTube.next(WatchEndpoint(videoId = trackId)).getOrNull()?.relatedEndpoint
                    if (relatedEndpoint != null) {
                        val relatedResult = YouTube.related(relatedEndpoint)
                        recommended = relatedResult.getOrNull()?.songs ?: emptyList()
                    }
                }
                
                var mappedTracks = recommended.mapNotNull {
                    InnertubeMusicService.convertToMusicTrack(it)
                }.filterNot { it.videoId == trackId }
                    .distinctBy { it.videoId }

                if (mappedTracks.isEmpty()) {
                    Log.d(TAG, "Innertube automix empty, falling back to related music service")
                    mappedTracks = YouTubeMusicService.getRelatedMusic(trackId, 20, audioOnly = true)
                        .filterNot { it.videoId == trackId }
                        .distinctBy { it.videoId }
                }

                Log.d(TAG, "Successfully resolved ${mappedTracks.size} automix tracks")
                if (mappedTracks.isNotEmpty()) {
                    io.github.aedev.flow.player.EnhancedMusicPlayerManager.updateAutomixItems(mappedTracks)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving automix", e)
            }
        }
    }

    private fun updateNotification() {
        if (!::mediaLibrarySession.isInitialized) return
        
        val isLiked = io.github.aedev.flow.player.EnhancedMusicPlayerManager.isLiked.value
        
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

        val closeButton = CommandButton.Builder()
            .setDisplayName(getString(R.string.close))
            .setIconResId(R.drawable.ic_close)
            .setSessionCommand(CommandStop)
            .setEnabled(true)
            .build()
            
        mediaLibrarySession.setCustomLayout(listOf(likeButton, shuffleButton, repeatButton, closeButton))
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
                .add(CommandStop)
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
                 io.github.aedev.flow.player.EnhancedMusicPlayerManager.emitToggleLikeEvent()
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

             if (customCommand.customAction == ACTION_STOP) {
                 stopPlaybackAndService()
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
            var closeButton: CommandButton? = null
            
            for (button in customLayout) {
                if (button.sessionCommand?.customAction == ACTION_TOGGLE_SHUFFLE) {
                    shuffleButton = button
                } else if (button.sessionCommand?.customAction == ACTION_TOGGLE_REPEAT) {
                    repeatButton = button
                } else if (button.sessionCommand?.customAction == ACTION_TOGGLE_LIKE) {
                    likeButton = button
                } else if (button.sessionCommand?.customAction == ACTION_STOP) {
                    closeButton = button
                }
            }
            
            val builder = ImmutableList.builder<CommandButton>()
            
            likeButton?.let { builder.add(it) }
            shuffleButton?.let { builder.add(it) }
            builder.add(prevButton)
            builder.add(playPauseButton)
            builder.add(nextButton)
            repeatButton?.let { builder.add(it) }
            closeButton?.let { builder.add(it) }
            
            return builder.build()
        }
    }
}
