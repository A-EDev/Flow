package com.flow.youtube.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import com.flow.youtube.data.local.VideoQuality
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.border
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.content.ContextCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flow.youtube.ui.components.ShimmerVideoCardHorizontal
import com.flow.youtube.ui.components.shimmerEffect
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.PictureInPictureHelper
import com.flow.youtube.ui.components.SubtitleCue
import com.flow.youtube.ui.components.SubtitleOverlay
import com.flow.youtube.ui.components.VideoCardFullWidth
import com.flow.youtube.ui.components.fetchSubtitles
import com.flow.youtube.ui.theme.extendedColors
import com.flow.youtube.ui.screens.player.VideoPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.toArgb
import com.flow.youtube.data.local.PlaylistRepository
import com.flow.youtube.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import com.flow.youtube.player.seekbarpreview.SeekbarPreviewThumbnailQuality

import com.flow.youtube.ui.components.VideoQuickActionsBottomSheet
import com.flow.youtube.ui.components.VideoInfoSection
import androidx.core.text.HtmlCompat

import androidx.hilt.navigation.compose.hiltViewModel

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedVideoPlayerScreen(
    video: Video,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VideoPlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val musicVm: com.flow.youtube.ui.screens.music.MusicPlayerViewModel = hiltViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // State
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()
    var showQuickActions by remember { mutableStateOf(false) }
    
    var showControls by remember { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isInPipMode by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    // Dialog states
    var showQualitySelector by remember { mutableStateOf(false) }
    var showAudioTrackSelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var showSettingsMenu by remember { mutableStateOf(false) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    
    // Gesture states
    var brightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var volumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showBrightnessOverlay by remember { mutableStateOf(false) }
    var showVolumeOverlay by remember { mutableStateOf(false) }
    
    // Animation states
    var showSeekForwardAnimation by remember { mutableStateOf(false) }
    var showSeekBackAnimation by remember { mutableStateOf(false) }
    var playPauseScale by remember { mutableFloatStateOf(1f) }
    
    // Subtitle states
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var currentSubtitles by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var selectedSubtitleUrl by remember { mutableStateOf<String?>(null) }
    
    // Video resize mode
    var resizeMode by remember { mutableIntStateOf(0) } // 0=Fit, 1=Fill, 2=Zoom
    val resizeModes = listOf("Fit", "Fill", "Zoom")
    
    // Speed control states
    var isSpeedBoostActive by remember { mutableStateOf(false) }
    var normalSpeed by remember { mutableStateOf(1.0f) }
    
    // System managers
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    val windowManager = remember { context.getSystemService(Context.WINDOW_SERVICE) as WindowManager }
    
    // Seekbar preview helper
    var seekbarPreviewHelper by remember { mutableStateOf<SeekbarPreviewThumbnailHelper?>(null) }
    
    // Initialize seekbar preview helper when stream info is available
    LaunchedEffect(uiState.streamInfo) {
        uiState.streamInfo?.let { streamInfo ->
            try {
                val player = EnhancedPlayerManager.getInstance().getPlayer()
                if (player != null) {
                    seekbarPreviewHelper = SeekbarPreviewThumbnailHelper(
                        context = context,
                        player = player,
                        timeBar = object : androidx.media3.ui.TimeBar {
                            override fun addListener(listener: androidx.media3.ui.TimeBar.OnScrubListener) {}
                            override fun removeListener(listener: androidx.media3.ui.TimeBar.OnScrubListener) {}
                            override fun getPreferredUpdateDelay(): Long = 1000L
                            override fun setAdGroupTimesMs(adGroupTimesMs: LongArray?, playedAdGroups: BooleanArray?, adGroupCount: Int) {}
                            override fun setBufferedPosition(positionMs: Long) {}
                            override fun setDuration(durationMs: Long) {}
                            override fun setEnabled(enabled: Boolean) {}
                            override fun setKeyCountIncrement(increment: Int) {}
                            override fun setKeyTimeIncrement(increment: Long) {}
                            override fun setPosition(positionMs: Long) {}
                        }
                    ).apply {
                        setupSeekbarPreview(streamInfo)
                    }
                }
            } catch (e: Exception) {
                Log.w("EnhancedVideoPlayerScreen", "Failed to initialize seekbar preview helper", e)
            }
        }
    }
    
    // PiP mode support - detect PiP state changes
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                isInPipMode = activity.isInPictureInPictureMode
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    // PiP broadcast receiver for play/pause controls
    DisposableEffect(Unit) {
        val receiver = PictureInPictureHelper.createPipActionReceiver(
            onPlay = { EnhancedPlayerManager.getInstance().play() },
            onPause = { EnhancedPlayerManager.getInstance().pause() }
        )
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.registerReceiver(
                context,
                receiver,
                PictureInPictureHelper.getPipIntentFilter(),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            context.registerReceiver(receiver, PictureInPictureHelper.getPipIntentFilter())
        }
        
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                // Receiver may not be registered
            }
        }
    }
    
    // Update PiP params when playback state changes
    LaunchedEffect(playerState.isPlaying) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            PictureInPictureHelper.updatePipParams(
                activity = activity,
                aspectRatioWidth = 16,
                aspectRatioHeight = 9,
                isPlaying = playerState.isPlaying,
                autoEnterEnabled = true
            )
        }
    }
    
    // Snackbar host
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    // Track position
    LaunchedEffect(playerState.isPlaying) {
        while (playerState.isPlaying) {
            EnhancedPlayerManager.getInstance().getPlayer()?.let { player ->
                currentPosition = player.currentPosition
                duration = player.duration.coerceAtLeast(0)
            }
            delay(100)
        }
    }
    
    // Periodic watch progress saving (every 10 seconds while playing)
    LaunchedEffect(video.id, playerState.isPlaying) {
        while (playerState.isPlaying) {
            delay(10000) // Save every 10 seconds
            val streamInfo = uiState.streamInfo
            val channelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
            val channelName = streamInfo?.uploaderName ?: video.channelName
            val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url ?: video.thumbnailUrl
            
            if (currentPosition > 0 && duration > 0) {
                viewModel.savePlaybackPosition(
                    videoId = video.id,
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
    
    // Auto-hide controls
    LaunchedEffect(showControls, playerState.isPlaying) {
        if (showControls && playerState.isPlaying) {
            delay(3000)
            showControls = false
        }
    }
    
    // Hide overlays
    LaunchedEffect(showBrightnessOverlay) {
        if (showBrightnessOverlay) {
            delay(1000)
            showBrightnessOverlay = false
        }
    }
    
    LaunchedEffect(showVolumeOverlay) {
        if (showVolumeOverlay) {
            delay(1000)
            showVolumeOverlay = false
        }
    }
    
    // Hide seek animations
    LaunchedEffect(showSeekForwardAnimation) {
        if (showSeekForwardAnimation) {
            delay(500)
            showSeekForwardAnimation = false
        }
    }
    
    LaunchedEffect(showSeekBackAnimation) {
        if (showSeekBackAnimation) {
            delay(500)
            showSeekBackAnimation = false
        }
    }
    
    // Load subtitles when selected
    LaunchedEffect(selectedSubtitleUrl) {
        selectedSubtitleUrl?.let { url ->
            try {
                currentSubtitles = fetchSubtitles(url)
                subtitlesEnabled = currentSubtitles.isNotEmpty()
            } catch (e: Exception) {
                currentSubtitles = emptyList()
                subtitlesEnabled = false
            }
        }
    }
    
    // Fullscreen handling
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
    
    // Initialize ViewModel - Handled by Hilt
    // LaunchedEffect(Unit) {
    //    viewModel.initializeViewHistory(context)
    // }
    
    // Load video info from NewPipe extractor
    LaunchedEffect(video.id) {
        // Reset UI state for new video
        showControls = true
        currentPosition = 0L
        duration = 0L
        subtitlesEnabled = false
        currentSubtitles = emptyList()
        selectedSubtitleUrl = null
        seekbarPreviewHelper = null
        showBrightnessOverlay = false
        showVolumeOverlay = false
        showSeekBackAnimation = false
        showSeekForwardAnimation = false

        // Stop any existing playback and clear player before loading new video
        EnhancedPlayerManager.getInstance().pause()
        EnhancedPlayerManager.getInstance().clearCurrentVideo()

        // Detect if on Wifi for preferred quality
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ?: true
        
        viewModel.loadVideoInfo(video.id, isWifi)
    }
    
    // Initialize player when streams are available
    LaunchedEffect(uiState.videoStream, uiState.audioStream, video.id) {
        val videoStream = uiState.videoStream
        val audioStream = uiState.audioStream
        
        if (videoStream != null && audioStream != null) {
            // Clear previous video if this is a different video
            val currentVideoId = EnhancedPlayerManager.getInstance().playerState.value.currentVideoId
            if (currentVideoId != null && currentVideoId != video.id) {
                Log.d("EnhancedVideoPlayerScreen", "Switching from $currentVideoId to ${video.id}")
                EnhancedPlayerManager.getInstance().clearCurrentVideo()
                // No delay needed - setStreams will await surface readiness
            }
            
            EnhancedPlayerManager.getInstance().initialize(context)
            
            // Get all available streams
            val streamInfo = uiState.streamInfo
            val videoStreams = streamInfo?.videoStreams?.plus(streamInfo.videoOnlyStreams ?: emptyList()) ?: emptyList()
            val audioStreams = streamInfo?.audioStreams ?: emptyList()
            val subtitles = streamInfo?.subtitles ?: emptyList()
            
            EnhancedPlayerManager.getInstance().setStreams(
                videoId = video.id,
                videoStream = videoStream,
                audioStream = audioStream,
                videoStreams = videoStreams.filterIsInstance<org.schabi.newpipe.extractor.stream.VideoStream>(),
                audioStreams = audioStreams,
                subtitles = subtitles
            )
            
            // Initialize seekbar preview helper
            streamInfo?.let { info ->
                val player = EnhancedPlayerManager.getInstance().getPlayer()
                if (player != null) {
                    seekbarPreviewHelper = SeekbarPreviewThumbnailHelper(context, player, null).apply {
                        setupSeekbarPreview(info)
                    }
                }
            }
            
            // Resume from saved position
            uiState.savedPosition?.collect { position ->
                if (position > 0) {
                    EnhancedPlayerManager.getInstance().seekTo(position)
                }
            }
            
            EnhancedPlayerManager.getInstance().play()
        }
    }
    
    // Load subscription and like state
    LaunchedEffect(uiState.streamInfo) {
        uiState.streamInfo?.let { streamInfo ->
            val channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: ""
            if (channelId.isNotEmpty()) {
                viewModel.loadSubscriptionAndLikeState(channelId, video.id)
            }
        }
    }
    
    // Back handler
    BackHandler(enabled = !isFullscreen) {
        EnhancedPlayerManager.getInstance().pause()
        onBack()
    }
    
    // Cleanup when this video's composable leaves (e.g., switching to another video)
    DisposableEffect(video.id) {
        onDispose {
            // Save playback position for the video that's being disposed
            val streamInfo = uiState.streamInfo
            val channelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
            val channelName = streamInfo?.uploaderName ?: video.channelName
            val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url ?: video.thumbnailUrl

            viewModel.savePlaybackPosition(
                videoId = video.id,
                position = currentPosition,
                duration = duration,
                title = streamInfo?.name ?: video.title,
                thumbnailUrl = thumbnailUrl,
                channelName = channelName,
                channelId = channelId
            )

            // DON'T release player - just clear current video to allow switching
            // The player instance stays alive and keeps the surface binding intact
            EnhancedPlayerManager.getInstance().clearCurrentVideo()
            Log.d("EnhancedVideoPlayerScreen", "Video ID changed, cleared player state (player kept alive)")
        }
    }
    
    // Note: We no longer release the player when leaving the screen to maintain
    // player state across navigation. The player lifecycle is now managed globally.
    // If you need to release on back navigation, handle it in the BackHandler instead.
    
    val playerHeight = if (isFullscreen) {
        configuration.screenHeightDp.dp
    } else {
        (configuration.screenWidthDp.dp * 9f / 16f).coerceAtLeast(220.dp)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ============ VIDEO PLAYER SECTION ============
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(playerHeight)
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                            },
                            onDoubleTap = { offset ->
                                val screenWidth = size.width
                                val tapPosition = offset.x
                                
                                if (tapPosition < screenWidth * 0.4f) {
                                    // Seek backward 10s
                                    showSeekBackAnimation = true
                                    EnhancedPlayerManager.getInstance().seekTo(
                                        (currentPosition - 10000).coerceAtLeast(0)
                                    )
                                } else if (tapPosition > screenWidth * 0.6f) {
                                    // Seek forward 10s
                                    showSeekForwardAnimation = true
                                    EnhancedPlayerManager.getInstance().seekTo(
                                        (currentPosition + 10000).coerceAtMost(duration)
                                    )
                                } else {
                                    // Center tap - play/pause
                                    if (playerState.isPlaying) {
                                        EnhancedPlayerManager.getInstance().pause()
                                    } else {
                                        EnhancedPlayerManager.getInstance().play()
                                    }
                                }
                            },
                            onLongPress = { offset ->
                                val screenWidth = size.width
                                val tapPosition = offset.x
                                
                                // Long press on left or right side for 2x speed
                                if (tapPosition < screenWidth * 0.3f || tapPosition > screenWidth * 0.7f) {
                                    val player = EnhancedPlayerManager.getInstance().getPlayer()
                                    if (player != null && !isSpeedBoostActive) {
                                        normalSpeed = player.playbackParameters.speed
                                        player.setPlaybackSpeed(2.0f)
                                        isSpeedBoostActive = true
                                    }
                                }
                            },
                            onPress = { offset ->
                                val screenWidth = size.width
                                val tapPosition = offset.x
                                
                                // Detect when finger is released to restore speed
                                if (tapPosition < screenWidth * 0.3f || tapPosition > screenWidth * 0.7f) {
                                    tryAwaitRelease()
                                    if (isSpeedBoostActive) {
                                        val player = EnhancedPlayerManager.getInstance().getPlayer()
                                        player?.setPlaybackSpeed(normalSpeed)
                                        isSpeedBoostActive = false
                                    }
                                }
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                val screenWidth = size.width
                                if (offset.x < screenWidth / 2) {
                                    showBrightnessOverlay = true
                                } else {
                                    showVolumeOverlay = true
                                }
                            },
                            onDragEnd = {
                                scope.launch {
                                    delay(1000)
                                    showBrightnessOverlay = false
                                    showVolumeOverlay = false
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val screenHeight = size.height
                                val dragPosition = change.position.x
                                val screenWidth = size.width
                                
                                if (dragPosition < screenWidth / 2) {
                                    // Left side - brightness
                                    brightnessLevel = (brightnessLevel - dragAmount / screenHeight)
                                        .coerceIn(0f, 1f)
                                    
                                    // Apply system brightness
                                    try {
                                        activity?.window?.let { window ->
                                            val layoutParams = window.attributes
                                            layoutParams.screenBrightness = brightnessLevel
                                            window.attributes = layoutParams
                                        }
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                    
                                    showBrightnessOverlay = true
                                } else {
                                    // Right side - volume
                                    volumeLevel = (volumeLevel - dragAmount / screenHeight)
                                        .coerceIn(0f, 1f)
                                    
                                    // Apply system volume
                                    val newVolume = (volumeLevel * maxVolume).toInt()
                                    audioManager.setStreamVolume(
                                        AudioManager.STREAM_MUSIC,
                                        newVolume,
                                        0
                                    )
                                    
                                    showVolumeOverlay = true
                                }
                            }
                        )
                    }
            ) {
                // ExoPlayer view - create new instance per video to ensure fresh surface
                // This guarantees surfaceCreated() fires for every video switch
                val playerView = remember(video.id) {
                    Log.d("EnhancedVideoPlayer", "Creating new PlayerView for video ${video.id}")
                    PlayerView(context).apply {
                        useController = false
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                    }
                }
                
                // Setup surface callback immediately when playerView changes
                DisposableEffect(playerView) {
                    val surfaceView = playerView.videoSurfaceView as? android.view.SurfaceView
                    val callback = if (surfaceView != null) {
                        object : android.view.SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: android.view.SurfaceHolder) {
                                Log.d("EnhancedVideoPlayer", "Surface created for video ${video.id}")
                                EnhancedPlayerManager.getInstance().attachVideoSurface(holder)
                            }
                            
                            override fun surfaceChanged(
                                holder: android.view.SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                                // Surface resized but still valid
                            }
                            
                            override fun surfaceDestroyed(holder: android.view.SurfaceHolder) {
                                Log.d("EnhancedVideoPlayer", "Surface destroyed for video ${video.id}")
                                EnhancedPlayerManager.getInstance().detachVideoSurface()
                            }
                        }.also { surfaceView.holder.addCallback(it) }
                    } else null
                    
                    onDispose {
                        callback?.let { surfaceView?.holder?.removeCallback(it) }
                    }
                }
                
                AndroidView(
                    factory = { playerView },
                    update = { view ->
                        view.player = EnhancedPlayerManager.getInstance().getPlayer()
                        
                        // Apply resize mode
                        view.resizeMode = when (resizeMode) {
                            0 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                            1 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FILL
                            2 -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            else -> androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        }
                        
                        // CRITICAL: Ensure surface is attached whenever this view updates
                        // This handles the case where PlayerView is recreated after navigation
                        try {
                            val surfaceView = view.videoSurfaceView
                            if (surfaceView is android.view.SurfaceView) {
                                val holder = surfaceView.holder
                                val surface = holder.surface
                                if (surface != null && surface.isValid) {
                                    EnhancedPlayerManager.getInstance().attachVideoSurface(holder)
                                    Log.d("EnhancedVideoPlayer", "Surface reattached in update block")
                                }
                            }
                        } catch (e: Exception) {
                            Log.w("EnhancedVideoPlayer", "Failed to reattach surface in update", e)
                        }
                        
                        // Fallback: If surface is still not ready after update, mark it as ready
                        // This ensures media loads even if the surface callback doesn't fire properly
                        if (!EnhancedPlayerManager.getInstance().isSurfaceReady) {
                            try {
                                val surfaceView = view.videoSurfaceView
                                Log.d("EnhancedVideoPlayer", "Fallback check - videoSurfaceView: ${surfaceView?.javaClass?.simpleName}, width=${(surfaceView as? android.view.SurfaceView)?.width}, height=${(surfaceView as? android.view.SurfaceView)?.height}, isSurfaceReady: ${EnhancedPlayerManager.getInstance().isSurfaceReady}")
                                if (surfaceView is android.view.SurfaceView) {
                                    EnhancedPlayerManager.getInstance().attachVideoSurface(surfaceView.holder)
                                    val holderIsValid = runCatching { surfaceView.holder.surface?.isValid == true }.getOrDefault(false)
                                    if (holderIsValid) {
                                        Log.d("EnhancedVideoPlayer", "Surface exists, marking as ready (fallback)")
                                        EnhancedPlayerManager.getInstance().setSurfaceReady(true)
                                        EnhancedPlayerManager.getInstance().retryLoadMediaIfSurfaceReady()
                                    }
                                }
                            } catch (e: Exception) {
                                Log.d("EnhancedVideoPlayer", "Fallback surface ready check error: ${e.message}")
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Subtitle overlay
                SubtitleOverlay(
                    currentPosition = currentPosition,
                    subtitles = currentSubtitles,
                    enabled = subtitlesEnabled,
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.BottomCenter)
                )
                
                // Seek animations
                androidx.compose.animation.AnimatedVisibility(
                    visible = showSeekBackAnimation,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 60.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FastRewind,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "10s",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
                
                androidx.compose.animation.AnimatedVisibility(
                    visible = showSeekForwardAnimation,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 60.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FastForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "10s",
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    }
                }
                
                // Brightness overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showBrightnessOverlay,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .width(60.dp)
                            .height(200.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(30.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (brightnessLevel > 0.5f) Icons.Filled.BrightnessHigh 
                                             else if (brightnessLevel > 0.2f) Icons.Filled.BrightnessMedium
                                             else Icons.Filled.BrightnessLow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            LinearProgressIndicator(
                                progress = brightnessLevel,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(4.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "${(brightnessLevel * 100).toInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                
                // Volume overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showVolumeOverlay,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .width(60.dp)
                            .height(200.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        shape = RoundedCornerShape(30.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (volumeLevel > 0.5f) Icons.Filled.VolumeUp 
                                             else if (volumeLevel > 0f) Icons.Filled.VolumeDown
                                             else Icons.Filled.VolumeMute,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            LinearProgressIndicator(
                                progress = volumeLevel,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(4.dp),
                                color = Color.White,
                                trackColor = Color.White.copy(alpha = 0.3f)
                            )
                            Text(
                                text = "${(volumeLevel * 100).toInt()}%",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
                
                // Speed boost overlay (2x speed indicator)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSpeedBoostActive,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        color = Color(0xFFFF6B35).copy(alpha = 0.9f),
                        shape = RoundedCornerShape(40.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.FastForward,
                                    contentDescription = "2x Speed",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = "2x",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // ============ CUSTOM CONTROLS OVERLAY ============
                PremiumControlsOverlay(
                    isVisible = showControls && !isInPipMode,
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    currentPosition = currentPosition,
                    duration = duration,
                    title = uiState.streamInfo?.name ?: video.title,
                    qualityLabel = playerState.currentQuality.toString(),
                    resizeMode = resizeMode,
                    onResizeClick = {
                        resizeMode = (resizeMode + 1) % 3
                    },
                    onPlayPause = {
                        if (playerState.isPlaying) {
                            EnhancedPlayerManager.getInstance().pause()
                        } else {
                            EnhancedPlayerManager.getInstance().play()
                        }
                    },
                    onSeek = { newPosition ->
                        EnhancedPlayerManager.getInstance().seekTo(newPosition)
                    },
                    onBack = {
                        if (isFullscreen) {
                            isFullscreen = false
                        } else {
                            onBack()
                        }
                    },
                    onSettingsClick = { showSettingsMenu = true },
                    onFullscreenClick = { isFullscreen = !isFullscreen },
                    isFullscreen = isFullscreen,
                    isPipSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && PictureInPictureHelper.isPipSupported(context),
                    onPipClick = {
                        activity?.let { act ->
                            PictureInPictureHelper.enterPipMode(
                                activity = act,
                                isPlaying = playerState.isPlaying
                            )
                        }
                    },
                    seekbarPreviewHelper = seekbarPreviewHelper,
                    chapters = uiState.chapters,
                    onSubtitleClick = { subtitlesEnabled = !subtitlesEnabled },
                    isSubtitlesEnabled = subtitlesEnabled
                )
            }
            
            // ============ VIDEO DETAILS AND RELATED (Only when not fullscreen) ============
            if (!isFullscreen) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        VideoInfoSection(
                            video = video,
                            title = uiState.streamInfo?.name ?: video.title,
                            viewCount = uiState.streamInfo?.viewCount ?: video.viewCount,
                            uploadDate = uiState.streamInfo?.uploadDate?.let { 
                                try { 
                                    val cal = it.date()
                                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                    sdf.format(cal.time)
                                } catch(e: Exception) { null } 
                            } ?: video.uploadDate,
                            description = uiState.streamInfo?.description?.content ?: video.description,
                            channelName = uiState.streamInfo?.uploaderName ?: video.channelName,
                            channelAvatarUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl,
                            subscriberCount = uiState.channelSubscriberCount,
                            isSubscribed = uiState.isSubscribed,
                            likeState = uiState.likeState ?: "NONE",
                            onLikeClick = {
                                val streamInfo = uiState.streamInfo
                                val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url ?: video.thumbnailUrl
                                
                                when (uiState.likeState) {
                                    "LIKED" -> viewModel.removeLikeState(video.id)
                                    else -> viewModel.likeVideo(
                                        video.id,
                                        streamInfo?.name ?: video.title,
                                        thumbnailUrl,
                                        streamInfo?.uploaderName ?: video.channelName
                                    )
                                }
                            },
                            onDislikeClick = {
                                when (uiState.likeState) {
                                    "DISLIKED" -> viewModel.removeLikeState(video.id)
                                    else -> viewModel.dislikeVideo(video.id)
                                }
                            },
                            onSubscribeClick = {
                                uiState.streamInfo?.let { streamInfo ->
                                    val channelIdSafe = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId
                                    val channelNameSafe = streamInfo.uploaderName ?: video.channelName
                                    val channelThumbSafe = streamInfo.uploaderUrl ?: video.thumbnailUrl
                                    
                                    viewModel.toggleSubscription(channelIdSafe, channelNameSafe, channelThumbSafe)
                                    
                                    scope.launch {
                                        val message = if (uiState.isSubscribed) 
                                            "Unsubscribed from $channelNameSafe" 
                                        else 
                                            "Subscribed to $channelNameSafe"
                                            
                                        val result = snackbarHostState.showSnackbar(message, actionLabel = if (uiState.isSubscribed) "Undo" else null)
                                        
                                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed && uiState.isSubscribed) {
                                            // Undo unsubscribe
                                            viewModel.toggleSubscription(channelIdSafe, channelNameSafe, channelThumbSafe)
                                        }
                                    }
                                }
                            },
                            onChannelClick = {
                                uiState.streamInfo?.let { streamInfo ->
                                    val channelIdSafe = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId
                                    onChannelClick(channelIdSafe)
                                } ?: onChannelClick(video.channelId)
                            },
                            onSaveClick = { showQuickActions = true },
                            onShareClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, video.title)
                                    putExtra(Intent.EXTRA_TEXT, "Check out this video: ${video.title}\nhttps://youtube.com/watch?v=${video.id}")
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share video"))
                            },
                            onDownloadClick = {
                                showDownloadDialog = true
                            }
                        )
                    }
                    
                    // Related videos
                    item {
                        if (uiState.relatedVideos.isNotEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "Related Videos",
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                )
                            }
                        }
                    }
                    
                    items(
                        count = uiState.relatedVideos.size,
                        key = { index -> uiState.relatedVideos[index].id }
                    ) { index ->
                        val relatedVideo = uiState.relatedVideos[index]
                        VideoCardFullWidth(
                            video = relatedVideo,
                            onClick = { onVideoClick(relatedVideo) }
                        )
                    }
                }
            }
        }
    }
    // Quick actions sheet
    if (showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = video,
            onDismiss = { showQuickActions = false },
            onAddToPlaylist = {
                // open AddToPlaylistDialog flow using MusicPlayerViewModel
                showQuickActions = false
                musicVm.showAddToPlaylistDialog(true)
            },
            onWatchLater = {
                // Create a complete Video object from streamInfo
                showQuickActions = false
                scope.launch {
                    val streamInfo = uiState.streamInfo
                    
                    // Create a complete Video object with all metadata
                    val completeVideo = if (streamInfo != null) {
                        Video(
                            id = streamInfo.id ?: video.id,
                            title = streamInfo.name ?: video.title,
                            channelName = streamInfo.uploaderName ?: video.channelName,
                            channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId,
                            thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url ?: video.thumbnailUrl,
                            duration = streamInfo.duration.toInt(),
                            viewCount = streamInfo.viewCount,
                            uploadDate = streamInfo.uploadDate?.toString() ?: video.uploadDate,
                            description = streamInfo.description?.content ?: video.description,
                            channelThumbnailUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl
                        )
                    } else {
                        video // Fallback to original video if streamInfo not loaded yet
                    }
                    
                    viewModel.toggleWatchLater(completeVideo)
                    Toast.makeText(context, "Updated Watch Later", Toast.LENGTH_SHORT).show()
                }
            },
            onShare = {
                showQuickActions = false
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, video.title)
                    putExtra(Intent.EXTRA_TEXT, "Check out this video: ${video.title}\nhttps://youtube.com/watch?v=${video.id}")
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share video"))
            },
            onDownload = {
                showQuickActions = false
                showDownloadDialog = true
            },
            onNotInterested = {
                showQuickActions = false
                Toast.makeText(context, "Video marked as not interested", Toast.LENGTH_SHORT).show()
            },
            onReport = {
                showQuickActions = false
                val reportIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Report video: ${video.title}")
                    putExtra(Intent.EXTRA_TEXT, "Video ID: ${video.id}\nReason: ")
                }
                context.startActivity(Intent.createChooser(reportIntent, "Report video"))
            }
        )
    }
    
    // ============ DIALOGS ============
    
    // Download Quality Dialog
    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text("Download Video") },
            text = {
                Column {
                    Text(
                        text = "Select quality",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    LazyColumn {
                        items(uiState.availableQualities.filter { it != VideoQuality.AUTO }.sortedByDescending { it.height }) { quality ->
                            Surface(
                                onClick = {
                                    showDownloadDialog = false
                                    // Start download
                                    val stream = uiState.streamInfo?.videoStreams?.find { it.height == quality.height }
                                        ?: uiState.streamInfo?.videoStreams?.maxByOrNull { it.height }
                                    
                                    if (stream != null && stream.url != null) {
                                        startDownload(context, video.title, stream.url!!, "mp4")
                                        Toast.makeText(context, "Downloading ${quality.label}...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Stream not found", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Download,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text(
                                        text = quality.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Quality selector
    if (showQualitySelector) {
        AlertDialog(
            onDismissRequest = { showQualitySelector = false },
            title = { Text("Video Quality") },
            text = {
                LazyColumn {
                    items(playerState.availableQualities.sortedByDescending { it.height }) { quality ->
                        Surface(
                            onClick = {
                                EnhancedPlayerManager.getInstance().switchQuality(quality.height)
                                showQualitySelector = false
                            },
                            color = if (quality.height == playerState.currentQuality) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                
                                if (quality.height == playerState.currentQuality) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualitySelector = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Audio track selector
    if (showAudioTrackSelector) {
        AlertDialog(
            onDismissRequest = { showAudioTrackSelector = false },
            title = { Text("Audio Track") },
            text = {
                LazyColumn {
                    items(playerState.availableAudioTracks.size) { index ->
                        val track = playerState.availableAudioTracks[index]
                        Surface(
                            onClick = {
                                EnhancedPlayerManager.getInstance().switchAudioTrack(index)
                                showAudioTrackSelector = false
                            },
                            color = if (index == playerState.currentAudioTrack) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = track.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = track.language,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (index == playerState.currentAudioTrack) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAudioTrackSelector = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Subtitle selector
    if (showSubtitleSelector) {
        AlertDialog(
            onDismissRequest = { showSubtitleSelector = false },
            title = { Text("Subtitles") },
            text = {
                LazyColumn {
                    // Off option
                    item {
                        Surface(
                            onClick = {
                                subtitlesEnabled = false
                                selectedSubtitleUrl = null
                                currentSubtitles = emptyList()
                                showSubtitleSelector = false
                            },
                            color = if (!subtitlesEnabled) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Off",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                
                                if (!subtitlesEnabled) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                    
                    // Available subtitles
                    items(playerState.availableSubtitles.size) { index ->
                        val subtitle = playerState.availableSubtitles[index]
                        Surface(
                            onClick = {
                                // Select subtitle in manager and update local state
                                selectedSubtitleUrl = subtitle.url
                                EnhancedPlayerManager.getInstance().selectSubtitle(index)
                                subtitlesEnabled = true
                                showSubtitleSelector = false
                            },
                            color = if (subtitle.url == selectedSubtitleUrl) 
                                MaterialTheme.colorScheme.primaryContainer 
                            else 
                                Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = subtitle.label,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        text = subtitle.language,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (subtitle.url == selectedSubtitleUrl) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleSelector = false }) {
                    Text("Close")
                }
            }
        )
    }
    
    // Settings menu
    if (showSettingsMenu) {
        AlertDialog(
            onDismissRequest = { showSettingsMenu = false },
            title = { Text("Player Settings") },
            text = {
                Column {
                    Surface(
                        onClick = {
                            showSettingsMenu = false
                            showQualitySelector = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("Quality") },
                            supportingContent = { Text("${playerState.currentQuality}p") },
                            leadingContent = {
                                Icon(Icons.Filled.HighQuality, contentDescription = null)
                            }
                        )
                    }
                    
                    Surface(
                        onClick = {
                            showSettingsMenu = false
                            showAudioTrackSelector = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("Audio Track") },
                            supportingContent = { 
                                Text("Track ${playerState.currentAudioTrack + 1}") 
                            },
                            leadingContent = {
                                Icon(Icons.Filled.AudioFile, contentDescription = null)
                            }
                        )
                    }
                    
                    Surface(
                        onClick = {
                            showSettingsMenu = false
                            showSubtitleSelector = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("Subtitles") },
                            supportingContent = { 
                                Text(if (subtitlesEnabled) "On" else "Off") 
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Subtitles, contentDescription = null)
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsMenu = false }) {
                    Text("Close")
                }
            }
        )
    }
}

// Custom seekbar with preview thumbnails
@Composable
fun SeekbarWithPreview(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    seekbarPreviewHelper: SeekbarPreviewThumbnailHelper? = null
) {
    var showPreview by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableFloatStateOf(0f) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    Box(modifier = modifier) {
        // Preview thumbnail overlay
        if (showPreview && previewBitmap != null) {
            Box(
                modifier = Modifier
                    .offset(x = previewPosition.dp - 80.dp, y = (-120).dp)
                    .size(160.dp, 90.dp)
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
            ) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.ImageView(ctx).apply {
                            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                            setImageBitmap(previewBitmap)
                        }
                    },
                    update = { imageView ->
                        imageView.setImageBitmap(previewBitmap)
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // The actual slider
        @OptIn(ExperimentalMaterial3Api::class)
        Slider(
            value = value,
            onValueChange = { newValue ->
                onValueChange(newValue)

                // Update preview position and show thumbnail
                if (seekbarPreviewHelper != null) {
                    val sliderWidth = 300f // Approximate width, could be improved
                    previewPosition = newValue * sliderWidth

                    // Load thumbnail for this position
                    val duration = seekbarPreviewHelper.getPlayer().duration
                    if (duration > 0) {
                        val positionMs = (newValue * duration).toLong()

                        try {
                            val bitmap = seekbarPreviewHelper.loadThumbnailForPosition(positionMs)
                            previewBitmap = bitmap
                            showPreview = true
                        } catch (e: Exception) {
                            previewBitmap = null
                            showPreview = false
                        }
                    }
                }
            },
            onValueChangeFinished = {
                onValueChangeFinished?.invoke()
                showPreview = false
                previewBitmap = null
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            colors = colors,
            interactionSource = interactionSource,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        )
    }
}

// Helper function to format time
private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun startDownload(context: Context, title: String, url: String, extension: String) {
    try {
        val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
            .setTitle(title)
            .setDescription("Downloading video...")
            .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MOVIES, "$title.$extension")
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
        downloadManager.enqueue(request)
    } catch (e: Exception) {
        Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
