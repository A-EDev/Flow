package io.github.aedev.flow.ui.screens.player.effects

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import io.github.aedev.flow.data.local.PlayerPreferences
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
import androidx.media3.common.Player
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import io.github.aedev.flow.player.sponsorblock.SponsorBlockHandler

private const val TAG = "PlayerEffects"

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
                    screenState.currentPosition = player.currentPosition.coerceAtLeast(0L)
                    screenState.bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)
                    // Only write duration when ExoPlayer reports a valid value.
                    // player.duration returns C.TIME_UNSET (Long.MIN_VALUE) while buffering/recovering,
                    // coercing that to 0 would clear the known duration and break the seekbar.
                    val playerDuration = player.duration
                    if (playerDuration > 0L) {
                        screenState.duration = playerDuration
                    }
                }
            }
            delay(50)
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(500L)
            if (screenState.duration <= 0L) {
                EnhancedPlayerManager.getInstance().getPlayer()?.let { player ->
                    val playerDuration = player.duration
                    if (playerDuration > 0L) {
                        screenState.duration = playerDuration
                        screenState.currentPosition = player.currentPosition.coerceAtLeast(0L)
                    }
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
    lastInteractionTimestamp: Long,
    onHideControls: () -> Unit
) {
    LaunchedEffect(showControls, isPlaying, lastInteractionTimestamp) {
        if (showControls && isPlaying) {
            delay(3000)
            onHideControls()
        }
    }
}

@Composable
fun AutoPlayNextEffect(
    hasEnded: Boolean,
    autoplayEnabled: Boolean,
    hasNextInQueue: Boolean,
    relatedVideos: List<Video>,
    onVideoClick: (Video) -> Unit
) {
    LaunchedEffect(hasEnded, autoplayEnabled, hasNextInQueue) {
        if (hasEnded && autoplayEnabled && !hasNextInQueue) {
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
            screenState.seekAccumulation = 10
        }
    }

    LaunchedEffect(screenState.seekAccumulation, screenState.showSeekBackAnimation) {
        if (screenState.showSeekBackAnimation) {
            delay(800)
            screenState.showSeekBackAnimation = false
            screenState.seekAccumulation = 10
        }
    }
}

@Composable
fun FullscreenEffect(
    isFullscreen: Boolean,
    activity: Activity?,
    videoAspectRatio: Float = 16f / 9f,
    lifecycleOwner: LifecycleOwner
) {
    var resumeTrigger by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeTrigger++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isFullscreen, videoAspectRatio, resumeTrigger) {
        activity?.let { act ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && act.isInPictureInPictureMode) return@let
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
            } else {
                // Return to unspecified mode when exiting fullscreen
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                // We don't clear KEEP_SCREEN_ON here because the generic effect handles it based on playing state
                
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
    activity: Activity?
) {
    DisposableEffect(isPlaying) {
        if (isPlaying) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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

@Suppress("UNUSED_PARAMETER")
@Composable
fun PlayerInitEffect(
    videoId: String,
    uiState: VideoPlayerUiState,
    context: Context,
    screenState: PlayerScreenState
) {
    LaunchedEffect(uiState.videoStream, uiState.audioStream, uiState.localFilePath, videoId) {
        val videoStream = uiState.videoStream
        val audioStream = uiState.audioStream
        val localFilePath = uiState.localFilePath
        val hlsUrl = uiState.hlsUrl

        if (localFilePath != null && videoStream == null && audioStream == null && hlsUrl == null) {
            if (uiState.localFileVideoId != null && uiState.localFileVideoId != videoId) {
                Log.d(TAG, "Skipping stale localFilePath for $videoId (belongs to ${uiState.localFileVideoId})")
                return@LaunchedEffect
            }

            val currentPlayerState = EnhancedPlayerManager.getInstance().playerState.value
            if (currentPlayerState.currentVideoId == videoId && currentPlayerState.isPrepared) {
                Log.d(TAG, "Player already prepared for $videoId (offline), skipping")
                return@LaunchedEffect
            }

            Log.d(TAG, "Playing offline file for $videoId: $localFilePath")
            EnhancedPlayerManager.getInstance().initialize(context)
            EnhancedPlayerManager.getInstance().playLocalFile(videoId, localFilePath)
            applyRememberedSpeed(context, screenState)
            return@LaunchedEffect
        }

        if (videoStream != null && audioStream != null) {
            val currentPlayerState = EnhancedPlayerManager.getInstance().playerState.value
            if (currentPlayerState.currentVideoId == videoId && currentPlayerState.isPrepared
                && EnhancedPlayerManager.getInstance().isSurfaceReady) {
                Log.d(TAG, "Player already prepared for $videoId, skipping setStreams")
                return@LaunchedEffect
            }

            // Clear previous video if switching
            if (currentPlayerState.currentVideoId != null && currentPlayerState.currentVideoId != videoId) {
                Log.d(TAG, "Switching from ${currentPlayerState.currentVideoId} to $videoId")
                EnhancedPlayerManager.getInstance().clearCurrentVideo()
            }
            
            EnhancedPlayerManager.getInstance().initialize(context)
            
            // Get all available streams
            val streamInfo = uiState.streamInfo
            val videoStreams = streamInfo?.videoStreams?.plus(streamInfo.videoOnlyStreams ?: emptyList()) ?: emptyList()
            val audioStreams = streamInfo?.audioStreams ?: emptyList()
            val subtitles = streamInfo?.subtitles ?: emptyList()
            
            // Read saved position BEFORE setStreams (DB is correct here because touchHistoryEntry
            // preserves any existing progress instead of wiping it to 0)
            val savedPos = uiState.savedPosition?.first() ?: 0L

            EnhancedPlayerManager.getInstance().setStreams(
                videoId = videoId,
                videoStream = videoStream,
                audioStream = audioStream,
                videoStreams = videoStreams.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>(),
                audioStreams = audioStreams,
                subtitles = subtitles,
                durationSeconds = streamInfo?.duration ?: 0L,
                dashManifestUrl = streamInfo?.dashMpdUrl,
                localFilePath = uiState.localFilePath,
                hlsUrl = uiState.hlsUrl,
                startPosition = savedPos
            )
            
            EnhancedPlayerManager.getInstance().play()
            applyRememberedSpeed(context, screenState)
        } else if (uiState.isAdaptiveMode && audioStream != null && uiState.streamInfo != null) {
            val currentPlayerState = EnhancedPlayerManager.getInstance().playerState.value
            
            if (currentPlayerState.currentVideoId == videoId && currentPlayerState.isPrepared
                && EnhancedPlayerManager.getInstance().isSurfaceReady) {
                Log.d(TAG, "Player already prepared for $videoId (AUTO mode), skipping setStreams")
                return@LaunchedEffect
            }

            if (currentPlayerState.currentVideoId != null && currentPlayerState.currentVideoId != videoId) {
                Log.d(TAG, "Switching from ${currentPlayerState.currentVideoId} to $videoId (AUTO mode)")
                EnhancedPlayerManager.getInstance().clearCurrentVideo()
            }

            EnhancedPlayerManager.getInstance().initialize(context)

            val streamInfo = uiState.streamInfo
            val videoStreams = streamInfo.videoStreams.plus(streamInfo.videoOnlyStreams ?: emptyList())
            val audioStreams = streamInfo.audioStreams
            val subtitles = streamInfo.subtitles ?: emptyList()
            val savedPos = uiState.savedPosition?.first() ?: 0L

            EnhancedPlayerManager.getInstance().setStreams(
                videoId = videoId,
                videoStream = null, 
                audioStream = audioStream,
                videoStreams = videoStreams.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>(),
                audioStreams = audioStreams,
                subtitles = subtitles,
                durationSeconds = streamInfo.duration,
                dashManifestUrl = streamInfo.dashMpdUrl,
                localFilePath = uiState.localFilePath,
                hlsUrl = uiState.hlsUrl,
                startPosition = savedPos
            )

            EnhancedPlayerManager.getInstance().play()
            applyRememberedSpeed(context, screenState)
        }
    }
}

private suspend fun applyRememberedSpeed(context: Context, screenState: PlayerScreenState) {
    val prefs = PlayerPreferences(context)
    val remember = prefs.rememberPlaybackSpeed.first()
    if (remember) {
        val speed = prefs.playbackSpeed.first()
        if (speed != 1.0f) {
            EnhancedPlayerManager.getInstance().setPlaybackSpeed(speed)
            screenState.normalSpeed = speed
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
    val currentPos by rememberUpdatedState(currentPosition)
    val currentDur by rememberUpdatedState(duration)
    val currentUi by rememberUpdatedState(uiState)

    DisposableEffect(videoId) {
        onDispose {
            val streamInfo = currentUi.streamInfo
            val channelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
            val channelName = streamInfo?.uploaderName ?: video.channelName
            val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url
                ?: video.thumbnailUrl.takeIf { it.isNotEmpty() }
                ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"

            viewModel.savePlaybackPosition(
                videoId = videoId,
                position = currentPos,
                duration = currentDur,
                title = streamInfo?.name ?: video.title,
                thumbnailUrl = thumbnailUrl,
                channelName = channelName,
                channelId = channelId
            )

            EnhancedPlayerManager.getInstance().clearCurrentVideo()
            Log.d(TAG, "Video ID changed, cleared player state (player kept alive)")
        }
    }
}

@Composable
fun ShortVideoPromptEffect(
    videoDuration: Int,
    screenState: PlayerScreenState,
    isInQueue: Boolean
) {
    LaunchedEffect(videoDuration, screenState.hasShownShortsPrompt, isInQueue) {
        if (!isInQueue && !screenState.hasShownShortsPrompt && videoDuration > 0 && videoDuration <= 80) {
            delay(1000)
            screenState.showShortsPrompt = true
            screenState.hasShownShortsPrompt = true
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

        // We still need to keep this disposable effect to detect physical orientation changes when auto rotate is ON.
        // BUT we should also just rely on Configuration changes inside GlobalPlayerOverlay for a more robust fallback.
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
