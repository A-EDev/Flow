package io.github.aedev.flow.ui.screens.player.effects

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.delay
import android.view.OrientationEventListener
import android.widget.Toast
import android.provider.Settings
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import io.github.aedev.flow.player.sponsorblock.SponsorBlockHandler
import kotlin.math.roundToLong

private const val TAG = "PlayerEffects"
private const val LIVE_DISPLAY_BACKWARD_DRIFT_TOLERANCE_MS = 1_500L
private const val LIVE_DISPLAY_FORWARD_JUMP_THRESHOLD_MS = 5_000L
private const val LIVE_DISPLAY_MAX_TICK_MS = 1_000L
private const val LIVE_DISPLAY_RECENT_SEEK_MS = 2_000L
private const val STARTUP_RECOVERY_DELAY_MS = 5_000L
private const val STARTUP_BUFFERING_GRACE_MS = 4_000L

private var liveDisplayVideoId: String? = null
private var liveDisplayRawPositionMs: Long = 0L
private var liveDisplayUpdatedAtMs: Long = 0L
private var liveDisplayLastSeekAtMs: Long = 0L

private data class StartupRecoverySnapshot(
    val belongsToVideo: Boolean,
    val hasMedia: Boolean,
    val isIdle: Boolean,
    val hasDuration: Boolean,
    val hasStarted: Boolean,
    val isActivelyBuffering: Boolean,
    val playbackState: Int?,
    val position: Long,
    val duration: Long,
    val bufferedPosition: Long
)

private fun captureStartupRecoverySnapshot(
    manager: EnhancedPlayerManager,
    videoId: String,
    uiState: VideoPlayerUiState,
    screenState: PlayerScreenState
): StartupRecoverySnapshot {
    val player = manager.getPlayer()
    val playerState = manager.playerState.value
    val playerDuration = player?.duration ?: 0L
    val playerPosition = player?.currentPosition ?: 0L

    return StartupRecoverySnapshot(
        belongsToVideo = playerState.currentVideoId == videoId || uiState.cachedVideo?.id == videoId,
        hasMedia = player?.currentMediaItem != null,
        isIdle = player == null || player.playbackState == Player.STATE_IDLE,
        hasDuration = screenState.duration > 0L || playerDuration > 0L,
        hasStarted = playerPosition > 500L || playerState.isPlaying,
        isActivelyBuffering = (player?.playbackState == Player.STATE_BUFFERING &&
            player.playWhenReady) || playerState.isBuffering,
        playbackState = player?.playbackState,
        position = playerPosition,
        duration = playerDuration,
        bufferedPosition = player?.bufferedPosition ?: 0L
    )
}

private fun updateScreenPositionFromPlayer(player: Player, screenState: PlayerScreenState) {
    val managerState = EnhancedPlayerManager.getInstance().playerState.value
    if (managerState.isLive || player.isCurrentMediaItemLive) {
        updateLiveScreenPosition(player, screenState)
        return
    }

    screenState.currentPosition = player.currentPosition.coerceAtLeast(0L)
    screenState.bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)

    val playerDuration = player.duration
    if (playerDuration > 0L && playerDuration != C.TIME_UNSET) {
        screenState.duration = playerDuration
    }
}

private fun updateLiveScreenPosition(player: Player, screenState: PlayerScreenState) {
    val manager = EnhancedPlayerManager.getInstance()
    val managerState = manager.playerState.value
    val videoId = managerState.currentVideoId ?: player.currentMediaItem?.mediaId
    val sameLiveItem = liveDisplayVideoId == videoId
    val now = SystemClock.elapsedRealtime()
    val elapsedMs = if (sameLiveItem) {
        (now - liveDisplayUpdatedAtMs).coerceIn(0L, LIVE_DISPLAY_MAX_TICK_MS)
    } else {
        0L
    }
    val rawPosition = player.currentPosition.coerceAtLeast(0L)
    val pendingSeekPosition = manager.consumeRecentLiveDisplaySeek()
    if (pendingSeekPosition != null) {
        liveDisplayLastSeekAtMs = now
    }
    val recentlySeeked = now - liveDisplayLastSeekAtMs <= LIVE_DISPLAY_RECENT_SEEK_MS
    val previousDisplayPosition = if (sameLiveItem) {
        screenState.currentPosition.takeIf { it > 0L } ?: liveDisplayRawPositionMs
    } else {
        rawPosition
    }
    val displayPosition = when {
        pendingSeekPosition != null -> pendingSeekPosition
        !sameLiveItem -> rawPosition
        !player.isPlaying -> previousDisplayPosition
        recentlySeeked -> rawPosition
        else -> {
            val rawDelta = rawPosition - previousDisplayPosition
            when {
                rawDelta > LIVE_DISPLAY_FORWARD_JUMP_THRESHOLD_MS -> rawPosition
                rawDelta < -LIVE_DISPLAY_BACKWARD_DRIFT_TOLERANCE_MS -> {
                    val speed = managerState.playbackSpeed.coerceAtLeast(0.1f)
                    previousDisplayPosition + (elapsedMs * speed).roundToLong()
                }
                else -> {
                    val speed = managerState.playbackSpeed.coerceAtLeast(0.1f)
                    maxOf(rawPosition, previousDisplayPosition + (elapsedMs * speed).roundToLong())
                }
            }
        }
    }.coerceAtLeast(0L)

    val resolvedDuration = resolveLiveTimelineDuration(player) ?: 0L
    val liveDuration = maxOf(
        resolvedDuration,
        managerState.liveDurationMs,
        if (sameLiveItem) screenState.duration else 0L,
        displayPosition
    )

    screenState.duration = liveDuration
    screenState.currentPosition = displayPosition.coerceAtMost(liveDuration.takeIf { it > 0L } ?: displayPosition)
    screenState.bufferedPosition = maxOf(
        player.bufferedPosition.coerceAtLeast(0L),
        screenState.currentPosition
    ).let { buffered ->
        if (liveDuration > 0L) buffered.coerceAtMost(liveDuration) else buffered
    }

    liveDisplayVideoId = videoId
    liveDisplayRawPositionMs = rawPosition
    liveDisplayUpdatedAtMs = now
}

private fun resolveLiveTimelineDuration(player: Player): Long? {
    var liveDuration = 0L

    val timeline = player.currentTimeline
    val windowIndex = player.currentMediaItemIndex
    if (!timeline.isEmpty && windowIndex >= 0 && windowIndex < timeline.windowCount) {
        val window = Timeline.Window()
        timeline.getWindow(windowIndex, window)
        if (window.durationMs != C.TIME_UNSET && window.durationMs > 0L) {
            liveDuration = maxOf(liveDuration, window.durationMs)
        }
        if (window.defaultPositionMs != C.TIME_UNSET && window.defaultPositionMs > 0L) {
            liveDuration = maxOf(liveDuration, window.defaultPositionMs)
        }
    }

    val playerDuration = player.duration
    if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
        liveDuration = maxOf(liveDuration, playerDuration)
    }

    return liveDuration.takeIf { it > 0L }
}

@Composable
fun PositionTrackingEffect(
    isPlaying: Boolean,
    screenState: PlayerScreenState
) {
    // High-frequency active tracking (only while playing)
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            EnhancedPlayerManager.getInstance().getPlayer()?.let { player ->
                if (player.playbackState != Player.STATE_IDLE) {
                    updateScreenPositionFromPlayer(player, screenState)
                }
            }
            delay(50)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500L)
            EnhancedPlayerManager.getInstance().getPlayer()?.let { player ->
                if (player.playbackState != Player.STATE_IDLE) {
                    updateScreenPositionFromPlayer(player, screenState)
                }
            }
        }
    }
}

/**
 * Observes lifecycle ON_RESUME events and recovers player state after screen-off/on.
 *
 * On some devices (notably Samsung running Android 16), the activity goes through
 * onStop()/onStart() when the screen is turned off and back on. This causes:
 *  - collectAsStateWithLifecycle() to briefly stop, then resume with potentially stale state
 *  - ExoPlayer to reset its reported duration to TIME_UNSET during re-buffering
 *  - The UI to display 0:00 / 0:00 even though playback is still live
 *
 * This effect detects ON_RESUME, waits for ExoPlayer to report a valid duration,
 * then restores the screenState and re-triggers playback if needed.
 */
@Composable
fun PlaybackRefocusEffect(
    screenState: PlayerScreenState,
    lifecycleOwner: LifecycleOwner
) {
    var resumeTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                resumeTrigger++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(resumeTrigger) {
        if (resumeTrigger == 0) return@LaunchedEffect

        delay(150L)

        val mgr = EnhancedPlayerManager.getInstance()
        val player = mgr.getPlayer() ?: return@LaunchedEffect
        val playerMgrState = mgr.playerState.value

        if (!playerMgrState.hasEnded &&
            player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING) &&
            player.duration > 0L
        ) {
            screenState.duration = player.duration
            screenState.currentPosition = player.currentPosition.coerceAtLeast(0L)
            if (playerMgrState.playWhenReady && !player.isPlaying) {
                player.play()
            }
            return@LaunchedEffect
        }

        if (!playerMgrState.hasEnded &&
            player.playbackState == Player.STATE_BUFFERING
        ) {
            if (playerMgrState.playWhenReady && !player.isPlaying) {
                player.play()
            }
            return@LaunchedEffect
        }

        if (playerMgrState.currentVideoId != null) {
            mgr.beginBackgroundRecovery()
            try {
                val savedPosition = player.currentPosition.takeIf { it > 500L }
                    ?: screenState.currentPosition.takeIf { it > 500L }

                var attempts = 0
                while (attempts < 25 && player.duration <= 0L) {
                    delay(100L)
                    attempts++
                }

                val validDuration = player.duration
                if (validDuration > 0L) {
                    screenState.duration = validDuration
                    screenState.currentPosition = player.currentPosition.coerceAtLeast(0L)
                } else {
                    PlayerDiagnostics.logRefocusGlitch(
                        TAG,
                        "No valid duration after $attempts polls; state=${player.playbackState} pos=${player.currentPosition}"
                    )
                    mgr.handleRefocusStuck(playerMgrState.currentVideoId)
                    return@LaunchedEffect
                }

                if (player.playbackState == Player.STATE_IDLE && playerMgrState.currentVideoId != null) {
                    Log.d(TAG, "PlaybackRefocusEffect: player in IDLE after resume, calling prepare()")
                    player.prepare()
                    if (savedPosition != null && savedPosition > 500L) {
                        player.seekTo(savedPosition)
                    }
                    delay(300L)
                }

                if (playerMgrState.playWhenReady && !player.isPlaying &&
                    player.playbackState != Player.STATE_ENDED
                ) {
                    player.play()
                }
            } finally {
                mgr.endBackgroundRecovery()
            }
        }
    }
}

@Composable
fun WatchProgressSaveEffect(
    videoId: String,
    video: Video,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel
) {
    val currentPos by rememberUpdatedState(currentPosition)
    val currentDur by rememberUpdatedState(duration)
    val currentUi by rememberUpdatedState(uiState)

    LaunchedEffect(videoId) {
        delay(3000)
        val streamInfo = currentUi.streamInfo
        val channelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
        val channelName = streamInfo?.uploaderName ?: video.channelName
        val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url
            ?: video.thumbnailUrl.takeIf { it.isNotEmpty() }
            ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        val title = streamInfo?.name ?: video.title
        if (title.isNotEmpty() && currentDur > 0) {
            viewModel.savePlaybackPosition(
                videoId = videoId,
                position = currentPos,
                duration = currentDur,
                title = title,
                thumbnailUrl = thumbnailUrl,
                channelName = channelName,
                channelId = channelId
            )
        }
    }

    LaunchedEffect(videoId, isPlaying) {
        while (isPlaying) {
            delay(10000)
            val streamInfo = currentUi.streamInfo
            val channelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
            val channelName = streamInfo?.uploaderName ?: video.channelName
            val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url
                ?: video.thumbnailUrl.takeIf { it.isNotEmpty() }
                ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
            val title = streamInfo?.name ?: video.title
            if (currentDur > 0 && title.isNotEmpty()) {
                viewModel.savePlaybackPosition(
                    videoId = videoId,
                    position = currentPos,
                    duration = currentDur,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    channelName = channelName,
                    channelId = channelId
                )
            }
        }
    }
}

@Composable
fun AutoHideControlsEffect(
    showControls: Boolean,
    isPlaying: Boolean,
    hasEnded: Boolean,
    lastInteractionTimestamp: Long,
    isTouchLocked: Boolean = false,
    onHideControls: () -> Unit
) {
    LaunchedEffect(showControls, isPlaying, hasEnded, lastInteractionTimestamp, isTouchLocked) {
        if (showControls && isPlaying && !hasEnded && !isTouchLocked) {
            delay(3000)
            onHideControls()
        }
    }
}

@Composable
fun AutoPlayNextEffect(
    hasEnded: Boolean,
    autoplayEnabled: Boolean,
    isLooping: Boolean,
    hasNextInQueue: Boolean,
    relatedVideos: List<Video>,
    onVideoClick: (Video) -> Unit
) {
    LaunchedEffect(hasEnded, autoplayEnabled, isLooping, hasNextInQueue) {
        if (hasEnded && autoplayEnabled && !isLooping && !hasNextInQueue) {
            relatedVideos.firstOrNull()?.let { nextVideo ->
                onVideoClick(nextVideo)
            }
        }
    }
}

@Composable
fun GestureOverlayAutoHideEffect(
    screenState: PlayerScreenState
) {
    // Brightness overlay auto-hide
    LaunchedEffect(screenState.showBrightnessOverlay) {
        if (screenState.showBrightnessOverlay) {
            delay(1000)
            screenState.showBrightnessOverlay = false
        }
    }
    
    // Volume overlay auto-hide
    LaunchedEffect(screenState.showVolumeOverlay) {
        if (screenState.showVolumeOverlay) {
            delay(1000)
            screenState.showVolumeOverlay = false
        }
    }
    
    LaunchedEffect(screenState.seekAccumulation, screenState.showSeekForwardAnimation) {
        if (screenState.showSeekForwardAnimation) {
            delay(800)
            screenState.showSeekForwardAnimation = false
            delay(400)
            screenState.seekAccumulation = 10
        }
    }

    LaunchedEffect(screenState.seekAccumulation, screenState.showSeekBackAnimation) {
        if (screenState.showSeekBackAnimation) {
            delay(800)
            screenState.showSeekBackAnimation = false
            delay(400)
            screenState.seekAccumulation = 10
        }
    }
}

@Composable
fun FullscreenEffect(
    isFullscreen: Boolean,
    activity: Activity?,
    videoAspectRatio: Float = 16f / 9f,
    lifecycleOwner: LifecycleOwner,
    fullscreenBrightnessLevel: Float? = null,
    suppressFullscreenRequest: Boolean = false
) {
    var resumeTrigger by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isFullscreen, videoAspectRatio, resumeTrigger, suppressFullscreenRequest) {
        activity?.let { act ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && act.isInPictureInPictureMode) return@let
            if (suppressFullscreenRequest && isFullscreen) return@let
            if (isFullscreen) {
                val orientation = if (videoAspectRatio < 1f) {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                act.requestedOrientation = orientation

                WindowCompat.setDecorFitsSystemWindows(act.window, false)
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                fullscreenBrightnessLevel?.let { brightnessLevel ->
                    val layoutParams = act.window.attributes
                    layoutParams.screenBrightness = if (brightnessLevel < 0f) {
                        WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                    } else {
                        brightnessLevel.coerceIn(0f, 1f)
                    }
                    act.window.attributes = layoutParams
                }
            } else {
                // Return to unspecified mode when exiting fullscreen
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                
                // Reset screen brightness to default when exiting fullscreen
                val layoutParams = act.window.attributes
                layoutParams.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                act.window.attributes = layoutParams

                WindowCompat.setDecorFitsSystemWindows(act.window, false)
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
fun KeepScreenOnEffect(
    isPlaying: Boolean,
    activity: Activity?,
    lifecycleOwner: LifecycleOwner? = null
) {
    DisposableEffect(activity, isPlaying, lifecycleOwner) {
        val clearScreenOn = {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            clearScreenOn()
        }

        val observer = lifecycleOwner?.let {
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_STOP -> clearScreenOn()
                    Lifecycle.Event.ON_START -> if (isPlaying) {
                        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    else -> Unit
                }
            }.also(it.lifecycle::addObserver)
        }

        onDispose {
            if (observer != null && lifecycleOwner != null) {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
            clearScreenOn()
        }
    }
}

@Composable
fun OrientationResetEffect(activity: Activity?) {
    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isInPictureInPictureMode == false) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
fun VideoLoadEffect(
    videoId: String,
    context: Context,
    screenState: PlayerScreenState,
    viewModel: VideoPlayerViewModel
) {
    LaunchedEffect(videoId) {
        screenState.resetForNewVideo()

        // Detect if on Wifi for preferred quality
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: true
        viewModel.loadVideoInfo(videoId, isWifi)
    }
}

@Composable
fun PlaybackStartupRecoveryEffect(
    videoId: String,
    uiState: VideoPlayerUiState,
    screenState: PlayerScreenState,
    viewModel: VideoPlayerViewModel
) {
    var recoveredVideoId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoId) {
        recoveredVideoId = null
    }

    LaunchedEffect(videoId, uiState.isLoading, uiState.streamInfo, uiState.localFilePath, uiState.error) {
        if (uiState.isLoading || uiState.error != null || uiState.isRestoredSession) return@LaunchedEffect
        if (uiState.streamInfo == null && uiState.localFilePath == null) return@LaunchedEffect

        delay(STARTUP_RECOVERY_DELAY_MS)

        val manager = EnhancedPlayerManager.getInstance()
        val player = manager.getPlayer()
        var snapshot = captureStartupRecoverySnapshot(manager, videoId, uiState, screenState)
        if (!snapshot.belongsToVideo) return@LaunchedEffect

        if (snapshot.hasMedia && snapshot.isIdle) {
            Log.w(TAG, "Startup recovery: player idle for $videoId, preparing again")
            player?.prepare()
            player?.play()
            delay(2_000L)
            snapshot = captureStartupRecoverySnapshot(manager, videoId, uiState, screenState)
        }

        if (snapshot.isActivelyBuffering && !snapshot.hasDuration && !snapshot.hasStarted) {
            delay(STARTUP_BUFFERING_GRACE_MS)
            snapshot = captureStartupRecoverySnapshot(manager, videoId, uiState, screenState)
        }

        val unresolvedStartup = !snapshot.hasDuration && !snapshot.hasStarted
        val stillStuck = snapshot.belongsToVideo &&
            recoveredVideoId != videoId &&
            (
                (!snapshot.isActivelyBuffering &&
                    (!manager.isPreparedForPlayback(videoId) || unresolvedStartup)) ||
                    (snapshot.isActivelyBuffering && unresolvedStartup)
            )

        if (stillStuck) {
            recoveredVideoId = videoId
            Log.w(
                TAG,
                "Startup recovery: reloading stuck playback for $videoId " +
                    "(state=${snapshot.playbackState}, pos=${snapshot.position}, " +
                    "dur=${snapshot.duration}, buff=${snapshot.bufferedPosition}, " +
                    "activeBuffering=${snapshot.isActivelyBuffering})"
            )
            viewModel.retryLoadVideo()
        }
    }
}

@Composable
fun VideoCleanupEffect(
    videoId: String,
    video: Video,
    currentPosition: Long,
    duration: Long,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel
) {
    var lastKnownPosition by remember(videoId) { mutableLongStateOf(currentPosition) }
    var lastKnownDuration by remember(videoId) { mutableLongStateOf(duration) }
    var lastKnownTitle by remember(videoId) { mutableStateOf(video.title) }
    var lastKnownThumbnail by remember(videoId) {
        mutableStateOf(
            video.thumbnailUrl.takeIf { it.isNotEmpty() }
                ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        )
    }
    var lastKnownChannelName by remember(videoId) { mutableStateOf(video.channelName) }
    var lastKnownChannelId by remember(videoId) { mutableStateOf(video.channelId) }

    SideEffect {
        val streamInfo = uiState.streamInfo
        val belongsToVideo = streamInfo?.id == videoId || uiState.cachedVideo?.id == videoId
        if (belongsToVideo) {
            lastKnownPosition = currentPosition
            lastKnownDuration = duration
            lastKnownTitle = streamInfo?.name ?: video.title
            lastKnownThumbnail = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url
                ?: video.thumbnailUrl.takeIf { it.isNotEmpty() }
                ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
            lastKnownChannelName = streamInfo?.uploaderName ?: video.channelName
            lastKnownChannelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
        }
    }

    DisposableEffect(videoId) {
        onDispose {
            viewModel.savePlaybackPosition(
                videoId = videoId,
                position = lastKnownPosition,
                duration = lastKnownDuration,
                title = lastKnownTitle,
                thumbnailUrl = lastKnownThumbnail,
                channelName = lastKnownChannelName,
                channelId = lastKnownChannelId
            )

            viewModel.reportWatchProgress(video, lastKnownPosition, lastKnownDuration)
            Log.d(TAG, "Video cleanup disposed for $videoId")
        }
    }
}

@Composable
fun ShortVideoPromptEffect(
    videoDuration: Int,
    screenState: PlayerScreenState,
    isInQueue: Boolean,
    disableShortsPlayer: Boolean
) {
    LaunchedEffect(videoDuration, screenState.hasShownShortsPrompt, isInQueue, disableShortsPlayer) {
        if (disableShortsPlayer) {
            screenState.showShortsPrompt = false
            return@LaunchedEffect
        }

        if (!isInQueue && !screenState.hasShownShortsPrompt && videoDuration > 0 && videoDuration <= 80) {
            delay(1000)
            if (!disableShortsPlayer) {
                screenState.showShortsPrompt = true
                screenState.hasShownShortsPrompt = true
            }
        }
    }
}

@Composable
fun CommentsLoadEffect(
    videoId: String,
    viewModel: VideoPlayerViewModel
) {
    LaunchedEffect(videoId) {
        viewModel.loadComments(videoId)
    }
}

@Composable
fun SubscriptionAndLikeEffect(
    videoId: String,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel
) {
    LaunchedEffect(uiState.streamInfo) {
        uiState.streamInfo?.let { streamInfo ->
            val channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: ""
            if (channelId.isNotEmpty()) {
                viewModel.loadSubscriptionAndLikeState(channelId, videoId)
            }
        }
    }
}

@Composable
fun SponsorSkipEffect(context: Context) {
    LaunchedEffect(Unit) {
        EnhancedPlayerManager.getInstance().skipEvent.collect { segment ->
            Toast.makeText(context, "Skipped ${segment.category}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun OrientationListenerEffect(
    context: Context,
    isExpanded: Boolean,
    isFullscreen: Boolean,
    videoAspectRatio: Float = 16f / 9f,
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    var physicalOrientation by remember { mutableIntStateOf(-1) }

    val currentIsFullscreen by rememberUpdatedState(isFullscreen)
    val currentAspectRatio by rememberUpdatedState(videoAspectRatio)
    val currentEnter by rememberUpdatedState(onEnterFullscreen)
    val currentExit by rememberUpdatedState(onExitFullscreen)

    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                
                val autoRotateOn = try {
                    Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
                } catch (e: Exception) { true }
                
                if (!autoRotateOn) return

                val newOrientation = when {
                    orientation in 60..120 || orientation in 240..300 -> 1 
                    orientation in 0..30 || orientation in 330..359 || orientation in 150..210 -> 0 
                    else -> physicalOrientation 
                }

                if (newOrientation != physicalOrientation) {
                    physicalOrientation = newOrientation
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    LaunchedEffect(physicalOrientation, isExpanded) {
        delay(150)
        
        if (physicalOrientation == -1) return@LaunchedEffect

        val isVerticalVideo = currentAspectRatio < 1f

        if (physicalOrientation == 1 && isExpanded && !currentIsFullscreen && !isVerticalVideo) {
            currentEnter()
        } else if (physicalOrientation == 0 && currentIsFullscreen && !isVerticalVideo) {
            currentExit()
        }
    }
}
