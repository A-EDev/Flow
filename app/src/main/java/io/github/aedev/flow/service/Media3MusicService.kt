package io.github.aedev.flow.service

import android.app.ActivityManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.PowerManager
import android.os.Process
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import io.github.aedev.flow.MainActivity
import io.github.aedev.flow.R
import io.github.aedev.flow.data.download.DownloadUtil
import io.github.aedev.flow.data.model.ParametricEQ
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.newmusic.InnertubeMusicService
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.extensions.setOffloadEnabled
import io.github.aedev.flow.innertube.YouTube
import io.github.aedev.flow.innertube.models.WatchEndpoint
import io.github.aedev.flow.player.audio.CustomEqualizerAudioProcessor
import io.github.aedev.flow.player.audio.shouldHandleAudioFocus
import io.github.aedev.flow.player.factory.LoadControlFactory
import io.github.aedev.flow.utils.MusicPlayerUtils
import io.github.aedev.flow.utils.NetworkConnectivityObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.Locale
import javax.inject.Inject
import kotlin.math.min

@AndroidEntryPoint
class Media3MusicService : MediaLibraryService() {
    companion object {
        private const val TAG = "Media3MusicService"
        private const val ACTION_TOGGLE_SHUFFLE = "ACTION_TOGGLE_SHUFFLE"
        private const val ACTION_TOGGLE_REPEAT = "ACTION_TOGGLE_REPEAT"
        private const val ACTION_STOP = "ACTION_STOP"
        private const val AUTO_ROOT_ID = "flow_auto_root"
        private const val AUTO_QUEUE_ID = "flow_auto_queue"
        private const val AUTO_CURRENT_ID = "flow_auto_current"
        const val ACTION_SET_EQ = "ACTION_SET_EQ"

        private const val MAX_RETRY_PER_SONG = 5
        private const val BASE_RETRY_DELAY_MS = 3000L
        private const val MAX_RETRY_DELAY_MS = 30000L
        private const val FAILED_SONGS_CACHE_SIZE = 50
        private const val RECOVERY_SUCCESS_GRACE_MS = 2 * 60 * 1000L

        // Endless radio: append to the real queue when this few tracks remain,
        // this many at a time, and refill the suggestion pool below this size.
        // LOW_WATER/BATCH mirror the desktop station (3 / 10).
        private const val RADIO_MIN_UPCOMING = 3
        private const val RADIO_APPEND_BATCH = 10
        private const val RADIO_POOL_LOW_WATER = 15
        private const val LOCAL_MEDIA_PREFIX = "local_"

        private val CommandToggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
        private val CommandToggleRepeat = SessionCommand(ACTION_TOGGLE_REPEAT, Bundle.EMPTY)
        private val CommandStop = SessionCommand(ACTION_STOP, Bundle.EMPTY)
        private val CommandSetEq = SessionCommand(ACTION_SET_EQ, Bundle.EMPTY)

        const val ACTION_TOGGLE_LIKE = "ACTION_TOGGLE_LIKE"
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
    private val musicAudioAttributes =
        AudioAttributes
            .Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()
    private var handlesAudioFocus = true
    private lateinit var connectivityObserver: NetworkConnectivityObserver

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /**
     * Coroutine job that defers WakeLock/WifiLock release by 30 seconds after playback pauses.
     * Prevents the CPU from entering deep sleep during brief buffering/focus-loss events.
     */
    private var lockReleaseJob: Job? = null

    private var automixJob: Job? = null

    // ── Endless radio session (desktop semantics: seeded once per queue, append-only) ──
    private var radioSeedId: String? = null
    private var radioContinuation: String? = null
    private var radioEndpoint: WatchEndpoint? = null
    private var radioTopUpJob: Job? = null
    private var radioAutoplayEnabled = true
    private var lastQueueIds: List<String>? = null

    private val retryCountMap = mutableMapOf<String, Int>()
    private val lastPlaybackErrorAtMap = mutableMapOf<String, Long>()

    private val recentlyFailedSongs = LinkedHashSet<String>()

    private var pendingRetryJob: Job? = null

    private var waitingForNetwork = false

    @Inject
    lateinit var downloadUtil: DownloadUtil

    @Inject
    lateinit var widgetPublisher: io.github.aedev.flow.widget.nowplaying.NowPlayingWidgetPublisher

    @Inject
    lateinit var musicBrain: MusicBrainEngine

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        recordForegroundStartFailures("music-service")

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

        lifecycleScope.launch {
            connectivityObserver.isConnected.collectLatest { isConnected ->
                if (isConnected && waitingForNetwork) {
                    Log.d(TAG, "Network restored, triggering retry")
                    waitingForNetwork = false
                    triggerRetryAfterNetworkRestore()
                }
            }
        }

        lifecycleScope.launch {
            io.github.aedev.flow.player.EnhancedMusicPlayerManager.isLiked.collectLatest {
                updateNotification()
                if (::player.isInitialized) widgetPublisher.publish(player)
            }
        }

        val prefs =
            io.github.aedev.flow.data.local
                .PlayerPreferences(this@Media3MusicService)
        lifecycleScope.launch {
            prefs.musicEndlessRadioEnabled.collect { radioAutoplayEnabled = it }
        }
        lifecycleScope.launch {
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

        lifecycleScope.launch {
            prefs.playDuringCalls
                .distinctUntilChanged()
                .collectLatest(::applyPlayDuringCallsPreference)
        }
    }

    private fun applyPlayDuringCallsPreference(playDuringCalls: Boolean) {
        val handleAudioFocus = shouldHandleAudioFocus(playDuringCalls)
        if (handlesAudioFocus == handleAudioFocus) return

        handlesAudioFocus = handleAudioFocus
        player.setAudioAttributes(musicAudioAttributes, handleAudioFocus)
        Log.i(TAG, "Music audio focus handling enabled: $handleAudioFocus")
    }

    private fun applyMusicQualityChange() {
        Log.d(TAG, "Music quality changed — clearing resolution caches")
        try {
            downloadUtil.clearUrlCache()
            MusicPlayerUtils.clearPlaybackCache()
            io.github.aedev.flow.player.EnhancedMusicPlayerManager
                .clearUrlCache()
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
        val mediaSourceFactory = DefaultMediaSourceFactory(downloadUtil.getPlayerDataSourceFactory())

        val renderersFactory =
            object : androidx.media3.exoplayer.DefaultRenderersFactory(this) {
                override fun buildAudioSink(
                    context: android.content.Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ): androidx.media3.exoplayer.audio.AudioSink? =
                    androidx.media3.exoplayer.audio.DefaultAudioSink
                        .Builder(context)
                        .setAudioProcessors(arrayOf(customEqualizer))
                        .build()
            }.setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val loadControl = LoadControlFactory.forMusic()

        player =
            ExoPlayer
                .Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setRenderersFactory(renderersFactory)
                .setAudioAttributes(musicAudioAttributes, handlesAudioFocus)
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

        player.addListener(
            object : Player.Listener {
                override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                    updateNotification()
                }

                override fun onRepeatModeChanged(repeatMode: Int) {
                    updateNotification()
                }

                override fun onPlayerError(error: PlaybackException) {
                    handlePlayerError(error)
                }

                override fun onMediaItemTransition(
                    mediaItem: androidx.media3.common.MediaItem?,
                    reason: Int,
                ) {
                    finalizeListenSession()
                    startListenSession(mediaItem?.mediaId)

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

                        if (!videoId.isNullOrBlank() && !videoId.startsWith(LOCAL_MEDIA_PREFIX)) {
                            // Desktop radio semantics: only a genuinely NEW queue seeds a
                            // fresh radio. In-app skips also arrive as PLAYLIST_CHANGED
                            // (playTrack rebuilds the playlist), so the discriminator is
                            // whether the queue CONTENTS changed — never the current track.
                            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED) {
                                onQueueContextChanged(videoId)
                            } else {
                                maybeExtendRadio()
                            }
                        }

                        if (!videoId.isNullOrBlank() && !title.isNullOrBlank() && !artist.isNullOrBlank()) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                try {
                                    Log.d(TAG, "Pre-warming lyrics cache in background for: $videoId - \"$title\"")
                                    val helper =
                                        io.github.aedev.flow.data.lyrics
                                            .LyricsHelper(this@Media3MusicService)
                                    helper.getLyrics(videoId, title, artist, 180, null, this@Media3MusicService)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Lyrics pre-warm background task encountered error: ${e.message}")
                                }
                            }
                        }
                    }

                    widgetPublisher.publish(player)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    updateLocks(isPlaybackActive())
                    widgetPublisher.publish(player)
                    if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_IDLE) {
                        // ENDED: the queue ran out — no transition fires for the last track.
                        // IDLE: player.stop() from a dismiss/stop path — same deal.
                        finalizeListenSession()
                    }
                    if (playbackState == Player.STATE_ENDED) {
                        // Radio raced the queue end: append now and keep playing.
                        maybeExtendRadio()
                        if (player.hasNextMediaItem()) {
                            player.seekToNextMediaItem()
                            player.play()
                        }
                    }
                    if (playbackState == Player.STATE_READY) {
                        refreshLearnDuration()
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

                override fun onPlayWhenReadyChanged(
                    playWhenReady: Boolean,
                    reason: Int,
                ) {
                    updateLocks(isPlaybackActive())
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    updateLocks(isPlaybackActive())
                    widgetPublisher.publish(player)
                    if (isPlaying) {
                        if (learnMediaId == null) learnMediaId = player.currentMediaItem?.mediaId
                        if (learnTrack?.videoId != learnMediaId) learnTrack = resolveLearnTrack(learnMediaId)
                        refreshLearnDuration()
                        learnPlayingSinceMs = android.os.SystemClock.elapsedRealtime()
                    } else {
                        closePlayingSegment()
                    }
                }
            },
        )
    }

    // ── Listen-session accounting (feeds MusicBrainEngine) ──
    // Hand-rolled instead of Media3's PlaybackStatsListener, whose internal state
    // machine throws IllegalArgumentException on some transition orders (seen on
    // device with our seekTo(0)-on-transition). Wall-clock time while isPlaying is
    // pause-free and seek-immune; a repeat loop finalizes and restarts a session,
    // so relistens still count once each.

    private var learnMediaId: String? = null
    private var learnTrack: io.github.aedev.flow.ui.screens.music.MusicTrack? = null
    private var learnDurationMs = 0L
    private var learnPlayedMs = 0L
    private var learnPlayingSinceMs = -1L

    private fun closePlayingSegment() {
        if (learnPlayingSinceMs >= 0) {
            learnPlayedMs += android.os.SystemClock.elapsedRealtime() - learnPlayingSinceMs
            learnPlayingSinceMs = -1L
        }
    }

    // Queue metadata often ships duration=0 (related/next payloads omit it), so the
    // player's own duration — valid once READY — is the reliable denominator.
    private fun refreshLearnDuration() {
        if (!::player.isInitialized) return
        if (player.currentMediaItem?.mediaId != learnMediaId) return
        val d = player.duration
        if (d > 0) learnDurationMs = d
    }

    private fun resolveLearnTrack(mediaId: String?): io.github.aedev.flow.ui.screens.music.MusicTrack? {
        if (mediaId.isNullOrBlank()) return null
        val manager = io.github.aedev.flow.player.EnhancedMusicPlayerManager
        return manager.queue.value.firstOrNull { it.videoId == mediaId }
            ?: manager.currentTrack.value?.takeIf { it.videoId == mediaId }
            ?: manager.automixItems.value.firstOrNull { it.videoId == mediaId }
    }

    private fun startListenSession(mediaId: String?) {
        learnMediaId = mediaId
        // Pin the track now: by finalize time a new playlist may have replaced the
        // queue and the outgoing track would no longer resolve.
        learnTrack = resolveLearnTrack(mediaId)
        learnDurationMs = 0L
        learnPlayedMs = 0L
        learnPlayingSinceMs =
            if (::player.isInitialized && player.isPlaying) android.os.SystemClock.elapsedRealtime() else -1L
        refreshLearnDuration()
    }

    private fun finalizeListenSession() {
        closePlayingSegment()
        val mediaId = learnMediaId
        val pinnedTrack = learnTrack
        val pinnedDurationMs = learnDurationMs
        val playedMs = learnPlayedMs
        learnMediaId = null
        learnTrack = null
        learnDurationMs = 0L
        learnPlayedMs = 0L
        if (mediaId.isNullOrBlank() || playedMs <= 0) {
            Log.d(TAG, "listen finalize skipped: id=$mediaId playedMs=$playedMs")
            return
        }

        val track = pinnedTrack?.takeIf { it.videoId == mediaId } ?: resolveLearnTrack(mediaId)
        if (track == null) {
            Log.w(TAG, "listen finalize: no track match for $mediaId")
            return
        }
        val durationMs = if (track.duration > 0) track.duration.toLong() * 1000 else pinnedDurationMs
        if (durationMs <= 0) {
            Log.w(TAG, "listen finalize: no duration for $mediaId")
            return
        }

        Log.d(TAG, "listen finalize: $mediaId playedMs=$playedMs pct=${playedMs.toDouble() / durationMs}")
        // Engine-scoped, NOT lifecycleScope: the finalize from onDestroy runs after
        // this service's scope is already cancelled, and the session must still land.
        musicBrain.onListenSessionAsync(track, playedMs.toDouble() / durationMs)
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
            io.github.aedev.flow.player.EnhancedMusicPlayerManager
                .invalidateResolvedStream(mediaId)
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

    private fun isExpiredUrlError(error: PlaybackException): Boolean = getHttpResponseCode(error) == 403

    private fun isRangeNotSatisfiableError(error: PlaybackException): Boolean = getHttpResponseCode(error) == 416

    private fun isPageReloadError(error: PlaybackException): Boolean {
        val errorMessage = error.message?.lowercase(Locale.ROOT) ?: ""
        val causeMessage = error.cause?.message?.lowercase(Locale.ROOT) ?: ""
        val reloadKeywords = listOf("page needs to be reloaded", "page must be reloaded", "reload")
        return reloadKeywords.any { errorMessage.contains(it) || causeMessage.contains(it) }
    }

    private fun isFileNotFoundError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND

    private fun isAudioRendererError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED

    private fun isNetworkError(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT

    private fun handleAudioRendererError(
        mediaId: String,
        currentRetry: Int,
    ) {
        retryCountMap[mediaId] = currentRetry + 1
        retryJobCancel()
        pendingRetryJob =
            lifecycleScope.launch {
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

    private fun handleRangeNotSatisfiableError(
        mediaId: String,
        currentRetry: Int,
    ) {
        retryCountMap[mediaId] = currentRetry + 1
        retryJobCancel()
        pendingRetryJob =
            lifecycleScope.launch {
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

    private fun handlePageReloadError(
        mediaId: String,
        currentRetry: Int,
    ) {
        retryCountMap[mediaId] = currentRetry + 1
        retryJobCancel()
        pendingRetryJob =
            lifecycleScope.launch {
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

    private fun handleExpiredUrlError(
        mediaId: String,
        currentRetry: Int,
    ) {
        retryCountMap[mediaId] = currentRetry + 1
        retryJobCancel()
        pendingRetryJob =
            lifecycleScope.launch {
                delay(BASE_RETRY_DELAY_MS)
                try {
                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex != C.INDEX_UNSET) {
                        val currentPosition = player.currentPosition
                        downloadUtil.invalidateUrlCache(mediaId)
                        MusicPlayerUtils.forceRefreshForVideo(mediaId)
                        io.github.aedev.flow.player.EnhancedMusicPlayerManager
                            .invalidateResolvedStream(mediaId)
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

    private fun refreshCurrentMediaItem(
        mediaId: String,
        positionMs: Long,
    ) {
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET) return

        val currentItem = player.getMediaItemAt(currentIndex)
        val refreshedItem =
            currentItem
                .buildUpon()
                .setUri("music://$mediaId")
                .setMediaId(mediaId)
                .setCustomCacheKey(mediaId)
                .build()

        player.replaceMediaItem(currentIndex, refreshedItem)
        player.seekTo(currentIndex, positionMs)
    }

    private fun handleFileNotFoundError(
        mediaId: String,
        currentRetry: Int,
    ) {
        retryCountMap[mediaId] = currentRetry + 1
        retryJobCancel()
        pendingRetryJob =
            lifecycleScope.launch {
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

    private fun handleNetworkError(
        mediaId: String,
        currentRetry: Int,
    ) {
        if (!connectivityObserver.checkCurrentConnectivity()) {
            Log.d(TAG, "No network connectivity, waiting for connection...")
            waitingForNetwork = true
            retryCountMap[mediaId] = currentRetry + 1
        } else {
            scheduleRetry(mediaId, currentRetry, delayMultiplier = 2.0)
        }
    }

    private fun handleGenericError(
        mediaId: String,
        currentRetry: Int,
    ) {
        scheduleRetry(mediaId, currentRetry, delayMultiplier = 1.5)
    }

    private fun retryJobCancel() {
        pendingRetryJob?.cancel()
        pendingRetryJob = null
    }

    private fun scheduleRetry(
        mediaId: String,
        currentRetry: Int,
        delayMultiplier: Double,
    ) {
        retryCountMap[mediaId] = currentRetry + 1
        val baseDelay = (BASE_RETRY_DELAY_MS * delayMultiplier).toLong()
        val delay = min(baseDelay * (1L shl currentRetry), MAX_RETRY_DELAY_MS)

        Log.d(TAG, "Scheduling retry ${currentRetry + 1}/$MAX_RETRY_PER_SONG for $mediaId in ${delay}ms")

        retryJobCancel()
        pendingRetryJob =
            lifecycleScope.launch {
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
        io.github.aedev.flow.player.EnhancedMusicPlayerManager
            .showPlaybackWarning(message)
    }

    private fun stopPlaybackAndService() {
        retryJobCancel()
        waitingForNetwork = false
        if (::player.isInitialized) {
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
        io.github.aedev.flow.player.EnhancedMusicPlayerManager
            .clearCurrentTrack()
        releaseLocks()
        stopSelf()
    }

    private fun triggerRetryAfterNetworkRestore() {
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val currentRetry = retryCountMap.getOrDefault(mediaId, 0)

        if (currentRetry < MAX_RETRY_PER_SONG) {
            Log.d(TAG, "Triggering retry after network restore for $mediaId")
            performAggressiveCacheClear(mediaId)

            lifecycleScope.launch {
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
        val intent =
            Intent(this, MainActivity::class.java).apply {
                action = "io.github.aedev.flow.action.OPEN_MUSIC_PLAYER"
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("open_music_player", true)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                1001,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        mediaLibrarySession =
            MediaLibrarySession
                .Builder(this, player, LibrarySessionCallback())
                .setSessionActivity(pendingIntent)
                .build()

        setMediaNotificationProvider(CustomNotificationProvider())

        updateNotification()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaLibrarySession

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
        // Flush the in-flight listen session before the player goes away.
        finalizeListenSession()

        // Clear audio session ID so external processors know we're gone
        currentAudioSessionId = 0
        Log.i(TAG, "Audio session destroyed")

        widgetPublisher.publishStopped()

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
            (
                player.playWhenReady &&
                    player.playbackState != Player.STATE_IDLE &&
                    player.playbackState != Player.STATE_ENDED
            )
    }

    private fun updateLocks(isPlaybackActive: Boolean) {
        lockReleaseJob?.cancel()
        lockReleaseJob = null

        if (isPlaybackActive) {
            acquireLocks()
            return
        }

        lockReleaseJob =
            lifecycleScope.launch {
                delay(12_000L)
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
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE,
            -> true

            else -> false
        }
    }

    /**
     * Decides whether this PLAYLIST_CHANGED is a real new queue (reseed the
     * radio) or just an in-queue skip routed through playTrack (extend only).
     */
    private fun onQueueContextChanged(currentId: String) {
        val manager = io.github.aedev.flow.player.EnhancedMusicPlayerManager
        val queueIds = manager.queue.value.map { it.videoId }
        // Same session when the track was already part of the previous queue: skips
        // and queue jumps rebuild the playlist (sometimes with a pruned list), but
        // the user never left their queue — only a track from OUTSIDE it reseeds.
        val previous = lastQueueIds
        val sameContext = previous != null && (previous == queueIds || currentId in previous)
        lastQueueIds =
            if (sameContext && previous != null && queueIds.size < previous.size) {
                // A pruned rebuild (stale mirror) must not shrink the known context.
                (previous + queueIds).distinct()
            } else {
                queueIds
            }
        if (sameContext) {
            maybeExtendRadio()
            return
        }
        radioSeedId = currentId
        radioContinuation = null
        radioEndpoint = null
        startRadio(currentId)
    }

    private fun startRadio(seedId: String) {
        automixJob?.cancel()
        radioTopUpJob?.cancel()
        automixJob =
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    var page = YouTube.next(WatchEndpoint(playlistId = "RDAMVM$seedId")).getOrNull()
                    val nestedPlaylistId = page?.endpoint?.playlistId
                    if (page != null && page.items.size <= 1 && !nestedPlaylistId.isNullOrBlank()) {
                        page = YouTube.next(WatchEndpoint(playlistId = nestedPlaylistId)).getOrNull() ?: page
                    }
                    if (page == null || page.items.size <= 1) {
                        page = YouTube.next(WatchEndpoint(videoId = seedId)).getOrNull() ?: page
                    }

                    var mapped =
                        page
                            ?.items
                            .orEmpty()
                            .mapNotNull { InnertubeMusicService.convertToMusicTrack(it) }
                            .filterNot { it.videoId == seedId }
                            .distinctBy { it.videoId }

                    if (mapped.isEmpty()) {
                        // Related fallback carries no continuation — the pool later
                        // reseeds from its own tail instead.
                        radioContinuation = null
                        radioEndpoint = null
                        mapped =
                            YouTubeMusicService
                                .getRelatedMusic(seedId, 20, audioOnly = true)
                                .filterNot { it.videoId == seedId }
                                .distinctBy { it.videoId }
                    } else {
                        radioContinuation = page?.continuation
                        radioEndpoint = page?.endpoint
                    }

                    val ranked = musicBrain.rankTracks(mapped, "radio")
                    Log.d(TAG, "Radio seeded from $seedId: ${ranked.size} tracks, continuation=${radioContinuation != null}")
                    if (ranked.isNotEmpty()) {
                        io.github.aedev.flow.player.EnhancedMusicPlayerManager
                            .updateAutomixItems(ranked)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Error seeding radio", e)
                }
            }
    }

    /**
     * Called on ordinary advances (main thread). Moves the next few pool tracks
     * into the REAL queue when it runs short — the queue only ever grows, so
     * nothing the user sees is replaced — and refills the pool in the background.
     */
    private fun maybeExtendRadio() {
        if (!radioAutoplayEnabled) return
        if (!::player.isInitialized) return
        // Repeat and shuffle already produce an endless queue — matching desktop.
        if (player.repeatMode != Player.REPEAT_MODE_OFF) return
        val manager = io.github.aedev.flow.player.EnhancedMusicPlayerManager
        if (manager.shuffleEnabled.value) return
        if (manager.currentTrack.value
                ?.videoId
                ?.startsWith(LOCAL_MEDIA_PREFIX) == true
        ) {
            return
        }

        val remaining = player.mediaItemCount - player.currentMediaItemIndex - 1
        if (remaining > RADIO_MIN_UPCOMING) return

        val queueIds = manager.queue.value.mapTo(HashSet()) { it.videoId }
        val batch =
            manager.automixItems.value
                .filterNot { it.videoId in queueIds }
                .take(RADIO_APPEND_BATCH)
        batch.forEach { track ->
            manager.addToQueue(track)
            manager.removeAutomixItem(track.videoId)
        }
        if (batch.isNotEmpty()) {
            // Our own growth must not read as a new queue on the next skip.
            lastQueueIds = manager.queue.value.map { it.videoId }
            Log.d(TAG, "Radio appended ${batch.size} tracks to the queue")
        }
        if (manager.automixItems.value.size < RADIO_POOL_LOW_WATER) extendRadioPool()
    }

    /** Fetch the next radio page and APPEND it to the pool — never replaces. */
    private fun extendRadioPool() {
        if (radioTopUpJob?.isActive == true) return
        radioTopUpJob =
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val manager = io.github.aedev.flow.player.EnhancedMusicPlayerManager
                    val endpoint = radioEndpoint
                    val continuation = radioContinuation
                    val page =
                        if (endpoint != null && continuation != null) {
                            YouTube.next(endpoint, continuation).getOrNull()
                        } else {
                            // Continuation exhausted: grow the tree from the newest tail.
                            val tailId =
                                (manager.automixItems.value.lastOrNull() ?: manager.queue.value.lastOrNull())
                                    ?.videoId
                                    ?.takeUnless { it.startsWith(LOCAL_MEDIA_PREFIX) }
                                    ?: return@launch
                            YouTube.next(WatchEndpoint(playlistId = "RDAMVM$tailId")).getOrNull()
                        }
                    if (page == null) return@launch
                    radioContinuation = page.continuation
                    radioEndpoint = page.endpoint

                    val mapped =
                        page.items
                            .mapNotNull { InnertubeMusicService.convertToMusicTrack(it) }
                            .distinctBy { it.videoId }
                    val ranked = musicBrain.rankTracks(mapped, "radio")
                    Log.d(TAG, "Radio pool topped up with ${ranked.size} tracks, continuation=${radioContinuation != null}")
                    if (ranked.isNotEmpty()) {
                        manager.appendAutomixItems(ranked)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Radio top-up failed: ${e.message}")
                }
            }
    }

    private fun updateNotification() {
        if (!::mediaLibrarySession.isInitialized) return

        val isLiked = io.github.aedev.flow.player.EnhancedMusicPlayerManager.isLiked.value

        val likeButton =
            CommandButton
                .Builder(if (isLiked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED)
                .setDisplayName(getString(if (isLiked) R.string.unlike else R.string.like))
                .setCustomIconResId(if (isLiked) R.drawable.ic_like_filled else R.drawable.ic_like)
                .setSessionCommand(CommandToggleLike)
                .setEnabled(true)
                .build()

        val shuffleOn = player.shuffleModeEnabled

        val (repeatIcon, repeatIconResId) =
            when (player.repeatMode) {
                Player.REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE to R.drawable.ic_repeat_one_on
                Player.REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL to R.drawable.ic_repeat_on
                else -> CommandButton.ICON_REPEAT_OFF to R.drawable.ic_repeat
            }

        val shuffleButton =
            CommandButton
                .Builder(if (shuffleOn) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF)
                .setDisplayName(getString(R.string.shuffle))
                .setCustomIconResId(if (shuffleOn) R.drawable.ic_shuffle_on else R.drawable.ic_shuffle)
                .setSessionCommand(CommandToggleShuffle)
                .build()

        val repeatButton =
            CommandButton
                .Builder(repeatIcon)
                .setDisplayName(getString(R.string.repeat))
                .setCustomIconResId(repeatIconResId)
                .setSessionCommand(CommandToggleRepeat)
                .build()

        val closeButton =
            CommandButton
                .Builder(CommandButton.ICON_STOP)
                .setDisplayName(getString(R.string.close))
                .setCustomIconResId(R.drawable.ic_close)
                .setSessionCommand(CommandStop)
                .setEnabled(true)
                .build()

        mediaLibrarySession.setCustomLayout(listOf(likeButton, shuffleButton, repeatButton, closeButton))
    }

    @OptIn(UnstableApi::class)
    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> =
            Futures.immediateFuture(
                LibraryResult.ofItem(
                    browsableMediaItem(
                        mediaId = AUTO_ROOT_ID,
                        title = getString(R.string.app_name),
                    ),
                    params,
                ),
            )

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items =
                when (parentId) {
                    AUTO_ROOT_ID -> {
                        listOf(
                            browsableMediaItem(AUTO_QUEUE_ID, "Queue"),
                            browsableMediaItem(AUTO_CURRENT_ID, "Now playing"),
                        )
                    }

                    AUTO_QUEUE_ID -> {
                        io.github.aedev.flow.player.EnhancedMusicPlayerManager.queue.value
                            .map { it.toAutoMediaItem() }
                    }

                    AUTO_CURRENT_ID -> {
                        io.github.aedev.flow.player.EnhancedMusicPlayerManager.currentTrack.value
                            ?.let { listOf(it.toAutoMediaItem()) }
                            ?: emptyList()
                    }

                    else -> {
                        emptyList()
                    }
                }

            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params),
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val track = autoTrackForMediaId(mediaId)
            val item =
                when {
                    mediaId == AUTO_ROOT_ID -> browsableMediaItem(AUTO_ROOT_ID, getString(R.string.app_name))
                    mediaId == AUTO_QUEUE_ID -> browsableMediaItem(AUTO_QUEUE_ID, "Queue")
                    mediaId == AUTO_CURRENT_ID -> browsableMediaItem(AUTO_CURRENT_ID, "Now playing")
                    track != null -> track.toAutoMediaItem()
                    else -> null
                }

            return Futures.immediateFuture(
                item?.let { LibraryResult.ofItem(it, null) }
                    ?: LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE),
            )
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val validCommands =
                MediaSession.ConnectionResult.DEFAULT_SESSION_AND_LIBRARY_COMMANDS
                    .buildUpon()
                    .add(CommandToggleShuffle)
                    .add(CommandToggleRepeat)
                    .add(CommandToggleLike)
                    .add(CommandStop)
                    .add(CommandSetEq)
                    .build()
            return MediaSession.ConnectionResult
                .AcceptedResultBuilder(session)
                .setAvailableSessionCommands(validCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == ACTION_TOGGLE_LIKE) {
                io.github.aedev.flow.player.EnhancedMusicPlayerManager
                    .emitToggleLikeEvent()
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
                val newMode =
                    when (player.repeatMode) {
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

    private fun browsableMediaItem(
        mediaId: String,
        title: String,
    ): MediaItem =
        MediaItem
            .Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                    .build(),
            ).build()

    private fun io.github.aedev.flow.ui.screens.music.MusicTrack.toAutoMediaItem(): MediaItem {
        val artwork =
            highResThumbnailUrl
                .ifBlank { thumbnailUrl }
                .takeIf { it.isNotBlank() }
                ?.let(Uri::parse)

        return MediaItem
            .Builder()
            .setMediaId(videoId)
            .setUri("music://$videoId")
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setArtist(artist)
                    .setArtworkUri(artwork)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .build(),
            ).build()
    }

    private fun autoTrackForMediaId(mediaId: String): io.github.aedev.flow.ui.screens.music.MusicTrack? {
        val manager = io.github.aedev.flow.player.EnhancedMusicPlayerManager
        return manager.queue.value.firstOrNull { it.videoId == mediaId }
            ?: manager.currentTrack.value?.takeIf { it.videoId == mediaId }
    }

    @OptIn(UnstableApi::class)
    private inner class CustomNotificationProvider : DefaultMediaNotificationProvider(this@Media3MusicService) {
        override fun getMediaButtons(
            session: MediaSession,
            playerCommands: Player.Commands,
            customLayout: ImmutableList<CommandButton>,
            showPauseButton: Boolean,
        ): ImmutableList<CommandButton> {
            val playPauseButton =
                CommandButton
                    .Builder(if (showPauseButton) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY)
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .setCustomIconResId(if (showPauseButton) R.drawable.ic_pause else R.drawable.ic_play)
                    .setDisplayName(getString(if (showPauseButton) R.string.pause else R.string.play))
                    .setEnabled(playerCommands.contains(Player.COMMAND_PLAY_PAUSE))
                    .build()

            val prevButton =
                CommandButton
                    .Builder(CommandButton.ICON_PREVIOUS)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .setCustomIconResId(R.drawable.ic_previous)
                    .setDisplayName(getString(R.string.previous))
                    .setEnabled(playerCommands.contains(Player.COMMAND_SEEK_TO_PREVIOUS))
                    .build()

            val nextButton =
                CommandButton
                    .Builder(CommandButton.ICON_NEXT)
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT)
                    .setCustomIconResId(R.drawable.ic_next)
                    .setDisplayName(getString(R.string.next))
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
