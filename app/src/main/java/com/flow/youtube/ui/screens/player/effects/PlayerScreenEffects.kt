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
import kotlinx.coroutines.flow.take

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
    onHideControls: () -> Unit
) {
    LaunchedEffect(showControls, isPlaying) {
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
    relatedVideos: List<Video>,
    onVideoClick: (Video) -> Unit
) {
    LaunchedEffect(hasEnded, autoplayEnabled) {
        if (hasEnded && autoplayEnabled) {
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
                act.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                
                WindowCompat.setDecorFitsSystemWindows(act.window, false)
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}

@Composable
fun OrientationResetEffect(activity: Activity?) {
    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isInPictureInPictureMode == false) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

        // Stop any existing playback and clear player before loading new video
        EnhancedPlayerManager.getInstance().pause()
        EnhancedPlayerManager.getInstance().clearCurrentVideo()

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
    LaunchedEffect(uiState.videoStream, uiState.audioStream, videoId) {
        val videoStream = uiState.videoStream
        val audioStream = uiState.audioStream
        
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
                localFilePath = uiState.localFilePath
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
    screenState: PlayerScreenState
) {
    LaunchedEffect(videoDuration, screenState.hasShownShortsPrompt) {
        if (!screenState.hasShownShortsPrompt && videoDuration > 0 && videoDuration <= 120) {
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
