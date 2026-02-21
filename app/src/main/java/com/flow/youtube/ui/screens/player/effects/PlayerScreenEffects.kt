package com.flow.youtube.ui.screens.player.effects

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
import androidx.lifecycle.LifecycleOwner
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.state.EnhancedPlayerState
import com.flow.youtube.ui.screens.player.VideoPlayerUiState
import com.flow.youtube.ui.screens.player.VideoPlayerViewModel
import com.flow.youtube.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.delay
import android.view.OrientationEventListener
import android.widget.Toast
import android.provider.Settings
import androidx.media3.common.Player
import kotlinx.coroutines.flow.take
import com.flow.youtube.player.sponsorblock.SponsorBlockHandler

private const val TAG = "PlayerEffects"

@Composable
fun PositionTrackingEffect(
    isPlaying: Boolean,
    screenState: PlayerScreenState
) {
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            EnhancedPlayerManager.getInstance().getPlayer()?.let { player ->
                screenState.currentPosition = player.currentPosition
                screenState.bufferedPosition = player.bufferedPosition
                screenState.duration = player.duration.coerceAtLeast(0)
            }
            delay(50)
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
    LaunchedEffect(videoId, isPlaying) {
        while (isPlaying) {
            delay(10000) // Save every 10 seconds
            val streamInfo = uiState.streamInfo
            val channelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
            val channelName = streamInfo?.uploaderName ?: video.channelName
            val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url ?: video.thumbnailUrl
            
            if (currentPosition > 0 && duration > 0) {
                viewModel.savePlaybackPosition(
                    videoId = videoId,
                    position = currentPosition,
                    duration = duration,
                    title = streamInfo?.name ?: video.title,
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
    
    // Seek animations auto-hide
    LaunchedEffect(screenState.showSeekForwardAnimation) {
        if (screenState.showSeekForwardAnimation) {
            delay(500)
            screenState.showSeekForwardAnimation = false
        }
    }
    
    LaunchedEffect(screenState.showSeekBackAnimation) {
        if (screenState.showSeekBackAnimation) {
            delay(500)
            screenState.showSeekBackAnimation = false
        }
    }
}

@Composable
fun FullscreenEffect(
    isFullscreen: Boolean,
    activity: Activity?
) {
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            if (isFullscreen) {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                
                WindowCompat.setDecorFitsSystemWindows(act.window, false)
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                // Return to unspecified mode when exiting fullscreen
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                // We don't clear KEEP_SCREEN_ON here because the generic effect handles it based on playing state
                
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
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
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
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
        // Reset UI state for new video
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
            val currentPlayerState = EnhancedPlayerManager.getInstance().playerState.value
            if (currentPlayerState.currentVideoId == videoId && currentPlayerState.isPrepared) {
                Log.d(TAG, "Player already prepared for $videoId (offline), skipping")
                return@LaunchedEffect
            }

            Log.d(TAG, "Playing offline file for $videoId: $localFilePath")
            EnhancedPlayerManager.getInstance().initialize(context)
            EnhancedPlayerManager.getInstance().playLocalFile(videoId, localFilePath)
            return@LaunchedEffect
        }

        if (videoStream != null && audioStream != null) {
            val currentPlayerState = EnhancedPlayerManager.getInstance().playerState.value
            
            // Guard: Don't reset if already prepared for this video
            if (currentPlayerState.currentVideoId == videoId && currentPlayerState.isPrepared) {
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
                hlsUrl = uiState.hlsUrl
            )
            
            // Resume from saved position
            uiState.savedPosition?.take(1)?.collect { position ->
                if (position > 0) {
                    EnhancedPlayerManager.getInstance().seekTo(position)
                }
            }
            
            EnhancedPlayerManager.getInstance().play()
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
    DisposableEffect(videoId) {
        onDispose {
            val streamInfo = uiState.streamInfo
            val channelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
            val channelName = streamInfo?.uploaderName ?: video.channelName
            val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url ?: video.thumbnailUrl

            viewModel.savePlaybackPosition(
                videoId = videoId,
                position = currentPosition,
                duration = duration,
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
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    var targetOrientation by remember { mutableStateOf<Int?>(null) } // 0=Portrait, 1=Landscape

    DisposableEffect(context) {
        val listener = object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                // Check setting
                val autoRotateOn = try {
                    Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION) == 1
                } catch (e: Exception) { true }
                if (!autoRotateOn) return

                if ((orientation in 260..280) || (orientation in 80..100)) {
                    targetOrientation = 1
                } else if ((orientation in 350..360) || (orientation in 0..10) || (orientation in 170..190)) {
                    targetOrientation = 0
                }
            }
        }
        listener.enable()
        onDispose { listener.disable() }
    }

    LaunchedEffect(targetOrientation) {
        targetOrientation?.let { target ->
            // Use kotlinx.coroutines.delay
            kotlinx.coroutines.delay(500)
            if (target == 1 && isExpanded && !isFullscreen) {
                onEnterFullscreen()
            } else if (target == 0 && isFullscreen) {
                onExitFullscreen()
            }
        }
    }
}
