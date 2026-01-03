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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.window.Dialog
import android.text.TextUtils
import com.flow.youtube.ui.components.SubtitleCue
import com.flow.youtube.ui.components.SubtitleStyle
import com.flow.youtube.ui.components.SubtitleOverlay
import com.flow.youtube.ui.components.SubtitleCustomizer
import android.text.method.LinkMovementMethod
import org.schabi.newpipe.extractor.stream.StreamSegment
import org.schabi.newpipe.extractor.stream.VideoStream
import android.widget.TextView
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.toSize
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
import com.flow.youtube.player.GlobalPlayerState
import com.flow.youtube.player.PictureInPictureHelper
import com.flow.youtube.ui.components.VideoCardFullWidth
import com.flow.youtube.ui.components.fetchSubtitles
import com.flow.youtube.ui.theme.extendedColors
import com.flow.youtube.ui.screens.player.VideoPlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.toArgb
import com.flow.youtube.data.local.PlaylistRepository
import com.flow.youtube.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import com.flow.youtube.player.seekbarpreview.SeekbarPreviewThumbnailQuality

import com.flow.youtube.ui.components.VideoQuickActionsBottomSheet
import com.flow.youtube.ui.components.VideoInfoSection
import com.flow.youtube.ui.components.CommentsPreview
import com.flow.youtube.ui.components.FlowCommentsBottomSheet
import com.flow.youtube.ui.components.FlowDescriptionBottomSheet
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
    val comments by viewModel.commentsState.collectAsStateWithLifecycle()
    val isLoadingComments by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val canGoPrevious by viewModel.canGoPrevious.collectAsStateWithLifecycle()

    var showQuickActions by remember { mutableStateOf(false) }
    var showCommentsSheet by remember { mutableStateOf(false) }
    var isTopComments by remember { mutableStateOf(true) }
    
    val sortedComments = remember(comments, isTopComments) {
        if (isTopComments) {
            comments.sortedByDescending { it.likeCount }
        } else {
            // Newest first - in a real app we'd have timestamps to parse, 
            // but for now we'll just reverse or keep as is if API returns newest first
            comments
        }
    }
    var showDescriptionSheet by remember { mutableStateOf(false) }
    
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
    var showPlaybackSpeedSelector by remember { mutableStateOf(false) }
    var showSubtitleStyleCustomizer by remember { mutableStateOf(false) }
    
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
    var subtitleStyle by remember { mutableStateOf(SubtitleStyle()) }
    
    // Video resize mode
    var resizeMode by remember { mutableIntStateOf(0) } // 0=Fit, 1=Fill, 2=Zoom
    val resizeModes = listOf("Fit", "Fill", "Zoom")
    
    // Speed control states
    var isSpeedBoostActive by remember { mutableStateOf(false) }
    var normalSpeed by remember { mutableStateOf(1.0f) }
    
    // System managers    // Initializations
    LaunchedEffect(video.id) {
        viewModel.loadComments(video.id)
    }
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

    // Reset orientation when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isInPictureInPictureMode == false) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                activity?.let { act ->
                    WindowCompat.setDecorFitsSystemWindows(act.window, true)
                    val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
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
            delay(50)
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

    // Auto-play Next Video Logic
    LaunchedEffect(playerState.hasEnded, uiState.autoplayEnabled) {
        if (playerState.hasEnded && uiState.autoplayEnabled) {
            val nextVideo = uiState.relatedVideos.firstOrNull()
            if (nextVideo != null) {
                onVideoClick(nextVideo)
            }
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
                Log.d("EnhancedVideoPlayer", "Selected subtitle URL changed: $url")
                currentSubtitles = fetchSubtitles(url)
                subtitlesEnabled = currentSubtitles.isNotEmpty()
                Log.d("EnhancedVideoPlayer", "Subtitles loaded: ${currentSubtitles.size} cues, enabled: $subtitlesEnabled")
            } catch (e: Exception) {
                Log.e("EnhancedVideoPlayer", "Failed to load subtitles from URL", e)
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
            // Guard: If the player is already playing this video and has these streams, don't reset
            // This prevents quality reset to "Auto" and playback interruptions during PiP transitions
            val currentPlayerState = EnhancedPlayerManager.getInstance().playerState.value
            if (currentPlayerState.currentVideoId == video.id && currentPlayerState.isPrepared) {
                Log.d("EnhancedVideoPlayerScreen", "Player already prepared for ${video.id}, skipping setStreams")
                return@LaunchedEffect
            }

            // Clear previous video if this is a different video
            val currentVideoId = currentPlayerState.currentVideoId
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
    
    val playerHeight = if (isFullscreen || isInPipMode) {
        configuration.screenHeightDp.dp
    } else {
        (configuration.screenWidthDp.dp * 9f / 16f).coerceAtLeast(220.dp)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isInPipMode) Color.Black else MaterialTheme.colorScheme.background)
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
                    .pointerInput(isFullscreen) {
                        var totalDragY = 0f
                        val dragThreshold = 150f

                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                if (isFullscreen) {
                                    val screenWidth = size.width
                                    if (offset.x < screenWidth / 2) {
                                        showBrightnessOverlay = true
                                    } else {
                                        showVolumeOverlay = true
                                    }
                                } else {
                                    totalDragY = 0f
                                }
                            },
                            onDragEnd = {
                                if (isFullscreen) {
                                    scope.launch {
                                        delay(1000)
                                        showBrightnessOverlay = false
                                        showVolumeOverlay = false
                                    }
                                } else {
                                    if (totalDragY > dragThreshold) {
                                        GlobalPlayerState.showMiniPlayer()
                                        onBack()
                                    }
                                    totalDragY = 0f
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                
                                if (isFullscreen) {
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
                                } else {
                                    // Accumulate drag for minimize gesture
                                    totalDragY += dragAmount
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
                        // Set background to black to avoid white flash during transitions
                        setBackgroundColor(android.graphics.Color.BLACK)
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
                
                // Subtitle Overlay
                SubtitleOverlay(
                    currentPosition = currentPosition,
                    subtitles = currentSubtitles,
                    enabled = subtitlesEnabled,
                    style = subtitleStyle,
                    modifier = Modifier
                        .fillMaxWidth()
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
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 2 },
                    exit = fadeOut(tween(500)) + slideOutHorizontally(tween(500)) { -it / 2 },
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
                ) {
                    val animatedBrightness by animateFloatAsState(
                        targetValue = brightnessLevel,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "brightness"
                    )
                    
                    Surface(
                        modifier = Modifier
                            .width(46.dp)
                            .height(220.dp),
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Progress Fill
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(animatedBrightness)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                Color.White.copy(alpha = 0.8f),
                                                Color.White.copy(alpha = 0.4f)
                                            )
                                        )
                                    )
                            )
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    imageVector = if (brightnessLevel > 0.7f) Icons.Rounded.BrightnessHigh 
                                                 else if (brightnessLevel > 0.3f) Icons.Rounded.BrightnessMedium
                                                 else Icons.Rounded.BrightnessLow,
                                    contentDescription = null,
                                    tint = if (animatedBrightness > 0.8f) Color.Black.copy(alpha = 0.7f) else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                
                                Text(
                                    text = "${(brightnessLevel * 100).toInt()}",
                                    color = if (animatedBrightness > 0.1f) Color.Black.copy(alpha = 0.7f) else Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // Volume overlay
                androidx.compose.animation.AnimatedVisibility(
                    visible = showVolumeOverlay,
                    enter = fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 2 },
                    exit = fadeOut(tween(500)) + slideOutHorizontally(tween(500)) { it / 2 },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
                ) {
                    val animatedVolume by animateFloatAsState(
                        targetValue = volumeLevel,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "volume"
                    )
                    
                    Surface(
                        modifier = Modifier
                            .width(46.dp)
                            .height(220.dp),
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(24.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            // Progress Fill
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(animatedVolume)
                                    .background(
                                        brush = Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                            )
                                        )
                                    )
                            )
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(vertical = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Icon(
                                    imageVector = if (volumeLevel > 0.6f) Icons.Rounded.VolumeUp 
                                                 else if (volumeLevel > 0.1f) Icons.Rounded.VolumeDown
                                                 else Icons.Rounded.VolumeMute,
                                    contentDescription = null,
                                    tint = if (animatedVolume > 0.8f) Color.Black.copy(alpha = 0.7f) else Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                                
                                Text(
                                    text = "${(volumeLevel * 100).toInt()}",
                                    color = if (animatedVolume > 0.1f) Color.Black.copy(alpha = 0.7f) else Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // Speed boost overlay (2x speed indicator)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isSpeedBoostActive,
                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.FastForward,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "2x Speed",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
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
                    qualityLabel = if (playerState.currentQuality == 0) "Auto (${playerState.effectiveQuality}p)" else playerState.currentQuality.toString(),
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
                    isSubtitlesEnabled = subtitlesEnabled,
                    autoplayEnabled = uiState.autoplayEnabled,
                    onAutoplayToggle = { viewModel.toggleAutoplay(it) },
                    onPrevious = {
                        viewModel.getPreviousVideoId()?.let { prevId ->
                            // We need to find the video object for this ID
                            // For now, we'll just create a dummy video object with the ID
                            // The loadVideoInfo will handle the rest
                            onVideoClick(Video(id = prevId, title = "", channelName = "", channelId = "", thumbnailUrl = "", duration = 0, viewCount = 0, uploadDate = ""))
                        }
                    },
                    onNext = {
                        uiState.relatedVideos.firstOrNull()?.let { nextVideo ->
                            onVideoClick(nextVideo)
                        }
                    },
                    hasPrevious = canGoPrevious,
                    hasNext = uiState.relatedVideos.isNotEmpty()
                )
            }
            
            // ============ VIDEO DETAILS AND RELATED (Only when not fullscreen and not in PiP) ============
            if (!isFullscreen && !isInPipMode) {
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
                            },
                            onDescriptionClick = {
                                showDescriptionSheet = true
                            }
                        )
                    }

                    // Comments Preview
                    item {
                        CommentsPreview(
                            commentCount = uiState.commentCountText,
                            latestComment = comments.firstOrNull()?.text,
                            authorAvatar = comments.firstOrNull()?.authorThumbnail,
                            onClick = { showCommentsSheet = true }
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
        // Create a complete Video object from streamInfo if available
        val completeVideo = remember(uiState.streamInfo, video) {
            val streamInfo = uiState.streamInfo
            if (streamInfo != null) {
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
                video
            }
        }

        VideoQuickActionsBottomSheet(
            video = completeVideo,
            onDismiss = { showQuickActions = false },
            onAddToPlaylist = {
                // open AddToPlaylistDialog flow using MusicPlayerViewModel
                showQuickActions = false
                musicVm.showAddToPlaylistDialog(true)
            },
            onShare = {
                showQuickActions = false
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, completeVideo.title)
                    putExtra(Intent.EXTRA_TEXT, "Check out this video: ${completeVideo.title}\nhttps://youtube.com/watch?v=${completeVideo.id}")
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
                    putExtra(Intent.EXTRA_SUBJECT, "Report video: ${completeVideo.title}")
                    putExtra(Intent.EXTRA_TEXT, "Video ID: ${completeVideo.id}\nReason: ")
                }
                context.startActivity(Intent.createChooser(reportIntent, "Report video"))
            }
        )
    }
    
    // ============ DIALOGS ============
    
    // Download Quality Dialog
    if (showDownloadDialog) {
        val downloadVideoTitle = uiState.streamInfo?.name ?: video.title
        
        Dialog(onDismissRequest = { showDownloadDialog = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Download Video",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Select your preferred quality",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 400.dp)
                    ) {
                        items(uiState.availableQualities.filter { it != VideoQuality.AUTO }.sortedByDescending { it.height }) { quality ->
                            val stream = (uiState.streamInfo?.videoStreams?.filterIsInstance<VideoStream>() ?: emptyList()).find { it.height == quality.height }
                                ?: (uiState.streamInfo?.videoOnlyStreams?.filterIsInstance<VideoStream>() ?: emptyList()).find { it.height == quality.height }
                            
                            val sizeInBytes = uiState.streamSizes[quality.height]
                            val formatName = stream?.format?.name ?: "MP4"
                            
                            val sizeText = if (sizeInBytes != null && sizeInBytes > 0) {
                                val mb = sizeInBytes / (1024 * 1024.0)
                                "$formatName • ${String.format("%.1f MB", mb)}"
                            } else {
                                "$formatName • ${quality.label}"
                            }

                            Surface(
                                onClick = {
                                    showDownloadDialog = false
                                    val downloadUrl = stream?.url
                                    if (downloadUrl != null) {
                                        startDownload(context, downloadVideoTitle, downloadUrl, "mp4")
                                        Toast.makeText(context, "Downloading ${quality.label}...", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Quality ${quality.label} not available for download", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Download,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.width(16.dp))
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = quality.label,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = sizeText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    
                                    if (quality.height >= 1080) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "HD",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                style = MaterialTheme.typography.labelSmall,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    TextButton(
                        onClick = { showDownloadDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                            showPlaybackSpeedSelector = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("Playback Speed") },
                            supportingContent = { 
                                Text("${playerState.playbackSpeed}x") 
                            },
                            leadingContent = {
                                Icon(Icons.Filled.Speed, contentDescription = null)
                            }
                        )
                    }

                    // Auto-play Toggle
                    Surface(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("Auto-play Next Video") },
                            supportingContent = { 
                                Text(if (uiState.autoplayEnabled) "On" else "Off") 
                            },
                            leadingContent = {
                                Icon(Icons.Filled.SkipNext, contentDescription = null)
                            },
                            trailingContent = {
                                Switch(
                                    checked = uiState.autoplayEnabled,
                                    onCheckedChange = { viewModel.toggleAutoplay(it) }
                                )
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
                            },
                            trailingContent = {
                                if (subtitlesEnabled) {
                                    IconButton(onClick = { 
                                        showSettingsMenu = false
                                        showSubtitleStyleCustomizer = true 
                                    }) {
                                        Icon(Icons.Filled.Settings, contentDescription = "Subtitle Style")
                                    }
                                }
                            }
                        )
                    }

                    Surface(
                        onClick = {
                            viewModel.toggleSkipSilence(!playerState.isSkipSilenceEnabled)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ListItem(
                            headlineContent = { Text("Skip Silence") },
                            supportingContent = { 
                                Text(if (playerState.isSkipSilenceEnabled) "On" else "Off") 
                            },
                            leadingContent = {
                                Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                            },
                            trailingContent = {
                                Switch(
                                    checked = playerState.isSkipSilenceEnabled,
                                    onCheckedChange = { viewModel.toggleSkipSilence(it) }
                                )
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

    // Playback speed selector
    if (showPlaybackSpeedSelector) {
        val speeds = listOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)
        AlertDialog(
            onDismissRequest = { showPlaybackSpeedSelector = false },
            title = { Text("Playback Speed") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(speeds) { speed ->
                        Surface(
                            onClick = {
                                EnhancedPlayerManager.getInstance().setPlaybackSpeed(speed)
                                showPlaybackSpeedSelector = false
                            },
                            color = if (speed == playerState.playbackSpeed) 
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
                                    text = if (speed == 1.0f) "Normal" else "${speed}x",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                
                                if (speed == playerState.playbackSpeed) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaybackSpeedSelector = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Subtitle Style Customizer
    if (showSubtitleStyleCustomizer) {
        AlertDialog(
            onDismissRequest = { showSubtitleStyleCustomizer = false },
            text = {
                SubtitleCustomizer(
                    currentStyle = subtitleStyle,
                    onStyleChange = { subtitleStyle = it }
                )
            },
            confirmButton = {
                TextButton(onClick = { showSubtitleStyleCustomizer = false }) {
                    Text("Done")
                }
            }
        )
    }

    // Comments Bottom Sheet
    if (showCommentsSheet) {
        FlowCommentsBottomSheet(
            comments = sortedComments,
            commentCount = uiState.commentCountText,
            isLoading = isLoadingComments,
            isTopSelected = isTopComments,
            onFilterChanged = { isTop ->
                isTopComments = isTop
            },
            onDismiss = { showCommentsSheet = false }
        )
    }

    // Description Bottom Sheet
    if (showDescriptionSheet) {
        // Build a complete video object for the description sheet
        val currentVideo = remember(uiState.streamInfo, video) {
            val streamInfo = uiState.streamInfo
            if (streamInfo != null) {
                Video(
                    id = streamInfo.id ?: video.id,
                    title = streamInfo.name ?: video.title,
                    channelName = streamInfo.uploaderName ?: video.channelName,
                    channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId,
                    thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url ?: video.thumbnailUrl,
                    duration = streamInfo.duration.toInt(),
                    viewCount = streamInfo.viewCount,
                    likeCount = streamInfo.likeCount,
                    uploadDate = streamInfo.textualUploadDate ?: streamInfo.uploadDate?.run { 
                        // Fallback formatting if textual date is missing
                        try {
                            val cal = date()
                            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            sdf.format(cal.time)
                        } catch (e: Exception) {
                            video.uploadDate
                        }
                    } ?: video.uploadDate,
                    description = streamInfo.description?.content ?: video.description,
                    channelThumbnailUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl
                )
            } else {
                video
            }
        }

        FlowDescriptionBottomSheet(
            video = currentVideo,
            onDismiss = { showDescriptionSheet = false }
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
    seekbarPreviewHelper: SeekbarPreviewThumbnailHelper? = null,
    chapters: List<StreamSegment> = emptyList(),
    duration: Long = 0L
) {
    var showPreview by remember { mutableStateOf(false) }
    var previewPosition by remember { mutableFloatStateOf(0f) }
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val primaryColor = MaterialTheme.colorScheme.primary
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    var sliderWidth by remember { mutableFloatStateOf(0f) }
    
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged
    
    // Internal value to keep the thumb following the finger smoothly
    var internalValue by remember { mutableFloatStateOf(value) }
    
    // Sync internal value with external value when not interacting
    LaunchedEffect(value) {
        if (!isInteracting) {
            internalValue = value
        }
    }

    // Async thumbnail loading with debouncing and better responsiveness
    LaunchedEffect(internalValue, isInteracting) {
        if (isInteracting && seekbarPreviewHelper != null) {
            val duration = seekbarPreviewHelper.getPlayer().duration
            if (duration > 0) {
                // Round to nearest 2 seconds for better cache hits during scrub
                val positionMs = ((internalValue * duration) / 2000).toLong() * 2000
                
                withContext(Dispatchers.IO) {
                    try {
                        val bitmap = seekbarPreviewHelper.loadThumbnailForPosition(positionMs)
                        if (bitmap != null) {
                            previewBitmap = bitmap
                            showPreview = true
                        }
                    } catch (e: Exception) {
                        // Keep previous bitmap if error
                    }
                }
            }
        } else {
            // Delay hiding to make it feel smoother
            delay(300)
            if (!isInteracting) {
                showPreview = false
                previewBitmap = null
            }
        }
    }
    
    val trackHeight by animateDpAsState(
        targetValue = if (isInteracting) 10.dp else 4.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "trackHeight"
    )
    
    val thumbScale by animateFloatAsState(
        targetValue = if (isInteracting) 1.8f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .onGloballyPositioned { coordinates ->
                sliderWidth = coordinates.size.width.toFloat()
            },
        contentAlignment = Alignment.Center
    ) {
        // Custom Track with Segments
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
        ) {
            val height = size.height
            val width = size.width
            
            // Draw inactive track (background)
            drawRoundRect(
                color = Color.White.copy(alpha = 0.15f),
                size = androidx.compose.ui.geometry.Size(width, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2)
            )
            
            // Draw active track (progress)
            val activeWidth = width * internalValue
            drawRoundRect(
                color = primaryColor,
                size = androidx.compose.ui.geometry.Size(activeWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(height / 2)
            )
            
            // Draw Chapter Separators (Gaps)
            if (chapters.isNotEmpty() && duration > 0) {
                val gapWidth = 3.dp.toPx()
                
                chapters.forEach { chapter ->
                    if (chapter.startTimeSeconds > 0) {
                        val chapterStartMs = chapter.startTimeSeconds * 1000
                        val chapterProgress = chapterStartMs.toFloat() / duration.toFloat()
                        
                        if (chapterProgress in 0f..1f) {
                            val gapX = width * chapterProgress
                            
                            // Draw a clear line to simulate a gap
                            drawLine(
                                color = Color.Black.copy(alpha = 0.8f), 
                                start = androidx.compose.ui.geometry.Offset(gapX, 0f), 
                                end = androidx.compose.ui.geometry.Offset(gapX, height),
                                strokeWidth = gapWidth
                            )
                        }
                    }
                }
            }
        }

        // Preview thumbnail overlay - BIGGER and SLEEKER
        androidx.compose.animation.AnimatedVisibility(
            visible = showPreview && previewBitmap != null,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.9f, animationSpec = tween(200)) + slideInVertically(initialOffsetY = { 20 }),
            exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.9f, animationSpec = tween(200)) + slideOutVertically(targetOffsetY = { 20 }),
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = (previewPosition - 110).dp, y = (-150).dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    modifier = Modifier
                        .size(220.dp, 124.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.Black,
                    border = BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)),
                    shadowElevation = 12.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
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
                        
                        // Time overlay on preview
                        Surface(
                            color = Color.Black.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                        ) {
                            Text(
                                text = formatTime((internalValue * duration).toLong()),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                
                // Triangle pointer
                Box(
                    modifier = Modifier
                        .size(16.dp, 8.dp)
                        .background(Color.White, shape = androidx.compose.foundation.shape.GenericShape { size, _ ->
                            moveTo(0f, 0f)
                            lineTo(size.width, 0f)
                            lineTo(size.width / 2f, size.height)
                            close()
                        })
                )
            }
        }

        // The actual slider
        @OptIn(ExperimentalMaterial3Api::class)
        Slider(
            value = internalValue,
            onValueChange = { newValue ->
                internalValue = newValue
                onValueChange(newValue)

                // Update preview position
                if (seekbarPreviewHelper != null) {
                    previewPosition = with(density) { (newValue * sliderWidth).toDp().value }
                }
            },
            onValueChangeFinished = {
                onValueChangeFinished?.invoke()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            ),
            thumb = {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .scale(thumbScale)
                        .background(Color.White, CircleShape)
                        .border(3.dp, primaryColor, CircleShape)
                        .then(
                            if (isInteracting) {
                                Modifier.background(
                                    Brush.radialGradient(
                                        colors = listOf(primaryColor.copy(alpha = 0.4f), Color.Transparent),
                                        radius = 40f
                                    )
                                )
                            } else Modifier
                        )
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
