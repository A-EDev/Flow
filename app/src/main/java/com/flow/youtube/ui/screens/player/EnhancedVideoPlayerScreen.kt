package com.flow.youtube.ui.screens.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.movableContentOf
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.GlobalPlayerState
import com.flow.youtube.player.PictureInPictureHelper
import com.flow.youtube.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartDisplay
import com.flow.youtube.ui.components.*
import com.flow.youtube.ui.screens.player.components.*
import com.flow.youtube.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch


@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedVideoPlayerScreen(
    video: Video,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlayAsShort: (String) -> Unit = {},
    onPlayAsMusic: (String) -> Unit = {},
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

    // Show error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMsg ->
            snackbarHostState.showSnackbar(
                message = errorMsg,
                withDismissAction = true
            )
        }
    }

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

    val comments by viewModel.commentsState.collectAsStateWithLifecycle()
    val isLoadingComments by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val canGoPrevious by viewModel.canGoPrevious.collectAsStateWithLifecycle()

    // PiP Preferences
    val autoPipEnabled by remember(context) { 
        com.flow.youtube.data.local.PlayerPreferences(context).autoPipEnabled 
    }.collectAsState(initial = false)
    val manualPipButtonEnabled by remember(context) { 
        com.flow.youtube.data.local.PlayerPreferences(context).manualPipButtonEnabled 
    }.collectAsState(initial = true)

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
    var showChaptersSheet by remember { mutableStateOf(false) }
    
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
    
    // Subtitle states
    var subtitlesEnabled by remember { mutableStateOf(false) }
    var currentSubtitles by remember { mutableStateOf<List<SubtitleCue>>(emptyList()) }
    var selectedSubtitleUrl by remember { mutableStateOf<String?>(null) }
    var subtitleStyle by remember { mutableStateOf(SubtitleStyle()) }
    
    // Video resize mode
    var resizeMode by remember { mutableIntStateOf(0) } // 0=Fit, 1=Fill, 2=Zoom
    
    // Speed control states
    var isSpeedBoostActive by remember { mutableStateOf(false) }
    var normalSpeed by remember { mutableStateOf(1.0f) }
    
    // System managers    // Initializations
    LaunchedEffect(video.id) {
        viewModel.loadComments(video.id)
    }

    // Shorts/Music Prompt
    var showShortsPrompt by remember { mutableStateOf(false) }
    var hasShownShortsPrompt by remember { mutableStateOf(false) }

    LaunchedEffect(completeVideo.duration, hasShownShortsPrompt) {
        if (!hasShownShortsPrompt && completeVideo.duration > 0 && completeVideo.duration <= 120) {
            delay(1000) // Delay slightly
            showShortsPrompt = true
            hasShownShortsPrompt = true
        }
    }

    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }
    
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
        val observer = LifecycleEventObserver { _, _ ->
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
    LaunchedEffect(playerState.isPlaying, autoPipEnabled) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            PictureInPictureHelper.updatePipParams(
                activity = activity,
                aspectRatioWidth = 16,
                aspectRatioHeight = 9,
                isPlaying = playerState.isPlaying,
                autoEnterEnabled = autoPipEnabled
            )
        }
    }

    // Reset orientation when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            if (activity?.isInPictureInPictureMode == false) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val act = activity
                WindowCompat.setDecorFitsSystemWindows(act.window, true)
                val insetsController = WindowCompat.getInsetsController(act.window, act.window.decorView)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
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
                subtitles = subtitles,
                durationSeconds = streamInfo?.duration ?: 0L,
                dashManifestUrl = streamInfo?.dashMpdUrl,
                localFilePath = uiState.localFilePath
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
            uiState.savedPosition?.take(1)?.collect { position ->
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
            // Removed clearSurface() to prevent race condition where new surface is cleared
            Log.d("EnhancedVideoPlayerScreen", "Video ID changed, cleared player state (player kept alive)")
        }
    }
    
    // Note: We no longer release the player when leaving the screen to maintain
    // player state across navigation. The player lifecycle is now managed globally.
    // If you need to release on back navigation, handle it in the BackHandler instead.
    
    val portraitHeight = if (isFullscreen || isInPipMode) {
        configuration.screenHeightDp.dp
    } else {
        (configuration.screenWidthDp.dp * 9f / 16f).coerceAtLeast(220.dp)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isInPipMode) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideLayout = maxWidth > 600.dp && maxHeight < maxWidth && !isFullscreen && !isInPipMode
            val widePlayerHeight = (maxWidth * 0.65f * 9f / 16f)

            val playerContent = remember {
                movableContentOf { currentHeight: Dp ->
            // ============ VIDEO PLAYER SECTION ============
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(currentHeight)
                    .background(Color.Black)
                    .videoPlayerControls(
                        isSpeedBoostActive = isSpeedBoostActive,
                        onSpeedBoostChange = { isSpeedBoostActive = it },
                        showControls = showControls,
                        onShowControlsChange = { showControls = it },
                        onShowSeekBackChange = { showSeekBackAnimation = it },
                        onShowSeekForwardChange = { showSeekForwardAnimation = it },
                        currentPosition = currentPosition,
                        duration = duration,
                        normalSpeed = normalSpeed,
                        scope = scope,
                        isFullscreen = isFullscreen,
                        onBrightnessChange = { brightnessLevel = it },
                        onShowBrightnessChange = { showBrightnessOverlay = it },
                        onVolumeChange = { volumeLevel = it },
                        onShowVolumeChange = { showVolumeOverlay = it },
                        onBack = onBack,
                        brightnessLevel = brightnessLevel,
                        volumeLevel = volumeLevel,
                        maxVolume = maxVolume,
                        audioManager = audioManager,
                        activity = activity
                    )
            ) {
                // Video Surface
                VideoPlayerSurface(
                    video = video,
                    resizeMode = resizeMode,
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
                SeekAnimationOverlay(
                    showSeekBack = showSeekBackAnimation,
                    showSeekForward = showSeekForwardAnimation,
                    modifier = Modifier.align(Alignment.Center)
                )
                
                // Brightness overlay
                BrightnessOverlay(
                    isVisible = showBrightnessOverlay,
                    brightnessLevel = brightnessLevel,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 32.dp)
                )
                
                // Volume overlay
                VolumeOverlay(
                    isVisible = showVolumeOverlay,
                    volumeLevel = volumeLevel,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 32.dp)
                )
                
                // Speed boost overlay
                SpeedBoostOverlay(
                    isVisible = isSpeedBoostActive,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 0.dp)
                )
                
                // ============ CUSTOM CONTROLS OVERLAY ============
                PremiumControlsOverlay(
                    isVisible = showControls && !isInPipMode,
                    isPlaying = playerState.isPlaying,
                    isBuffering = playerState.isBuffering,
                    currentPosition = currentPosition,
                    duration = duration,
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
                    isPipSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
                                     PictureInPictureHelper.isPipSupported(context) &&
                                     manualPipButtonEnabled,
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
                    onChapterClick = { showChaptersSheet = true },
                    onSubtitleClick = { subtitlesEnabled = !subtitlesEnabled },
                    isSubtitlesEnabled = subtitlesEnabled,
                    autoplayEnabled = uiState.autoplayEnabled,
                    onAutoplayToggle = { viewModel.toggleAutoplay(it) },
                    onPrevious = {
                        viewModel.getPreviousVideoId()?.let { prevId ->
                            onVideoClick(Video(id = prevId, title = "", channelName = "", channelId = "", thumbnailUrl = "", duration = 0, viewCount = 0, uploadDate = ""))
                        }
                    },
                    onNext = {
                        uiState.relatedVideos.firstOrNull()?.let { nextVideo ->
                            onVideoClick(nextVideo)
                        }
                    },
                    hasPrevious = canGoPrevious,
                    hasNext = uiState.relatedVideos.isNotEmpty(),
                    bufferedPercentage = playerState.bufferedPercentage
                )
            }
        }
    }

        val videoInfoContent: @Composable () -> Unit = {
                        VideoInfoSection(
                            video = video,
                            title = uiState.streamInfo?.name ?: video.title,
                            viewCount = uiState.streamInfo?.viewCount ?: video.viewCount,
                            uploadDate = uiState.streamInfo?.uploadDate?.let { 
                                try { 
                                    val date = java.util.Date.from(it.offsetDateTime().toInstant())
                                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                    sdf.format(date)
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

                        // Comments Preview
                        CommentsPreview(
                            commentCount = uiState.commentCountText,
                            latestComment = comments.firstOrNull()?.text,
                            authorAvatar = comments.firstOrNull()?.authorThumbnail,
                            onClick = { showCommentsSheet = true }
                        )
                    }

        val relatedContent: androidx.compose.foundation.lazy.LazyListScope.() -> Unit = {
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

        if (isWideLayout) {
             Row(Modifier.fillMaxSize()) {
                  Column(
                      Modifier
                          .weight(0.65f)
                          .fillMaxHeight()
                          .verticalScroll(rememberScrollState())
                  ) {
                       playerContent(widePlayerHeight)
                       videoInfoContent()
                  }
                  
                  LazyColumn(Modifier.weight(0.35f), contentPadding = PaddingValues(bottom = 80.dp)) {
                       relatedContent()
                  }
             }
        } else {
             Column(Modifier.fillMaxSize()) {
                 playerContent(portraitHeight)
                 if (!isFullscreen && !isInPipMode) {
                     LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 80.dp)) {
                         item { videoInfoContent() }
                         relatedContent()
                     }
                 }
             }
        }
    // Quick actions sheet
    if (showQuickActions) {
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
        DownloadQualityDialog(
            uiState = uiState,
            video = completeVideo,
            onDismiss = { showDownloadDialog = false }
        )
    }

    // Quality selector
    if (showQualitySelector) {
        QualitySelectorDialog(
            availableQualities = playerState.availableQualities,
            currentQuality = playerState.currentQuality,
            onDismiss = { showQualitySelector = false },
            onQualitySelected = { height ->
                EnhancedPlayerManager.getInstance().switchQuality(height)
            }
        )
    }
    
    // Audio track selector
    if (showAudioTrackSelector) {
        AudioTrackSelectorDialog(
            availableAudioTracks = playerState.availableAudioTracks,
            currentAudioTrack = playerState.currentAudioTrack,
            onDismiss = { showAudioTrackSelector = false },
            onTrackSelected = { index ->
                EnhancedPlayerManager.getInstance().switchAudioTrack(index)
            }
        )
    }
    
    // Subtitle selector
    if (showSubtitleSelector) {
        SubtitleSelectorDialog(
            availableSubtitles = playerState.availableSubtitles,
            selectedSubtitleUrl = selectedSubtitleUrl,
            subtitlesEnabled = subtitlesEnabled,
            onDismiss = { showSubtitleSelector = false },
            onSubtitleSelected = { index, url ->
                selectedSubtitleUrl = url
                EnhancedPlayerManager.getInstance().selectSubtitle(index)
                subtitlesEnabled = true
            },
            onDisableSubtitles = {
                subtitlesEnabled = false
                selectedSubtitleUrl = null
                currentSubtitles = emptyList()
            }
        )
    }
    
    // Settings menu
    if (showSettingsMenu) {
        SettingsMenuDialog(
            playerState = playerState,
            autoplayEnabled = uiState.autoplayEnabled,
            subtitlesEnabled = subtitlesEnabled,
            onDismiss = { showSettingsMenu = false },
            onShowQuality = { showQualitySelector = true },
            onShowAudio = { showAudioTrackSelector = true },
            onShowSpeed = { showPlaybackSpeedSelector = true },
            onShowSubtitles = { showSubtitleSelector = true },
            onAutoplayToggle = { viewModel.toggleAutoplay(it) },
            onSkipSilenceToggle = { viewModel.toggleSkipSilence(it) },
            onShowSubtitleStyle = { showSubtitleStyleCustomizer = true }
        )
    }

    // Playback speed selector
    if (showPlaybackSpeedSelector) {
        PlaybackSpeedSelectorDialog(
            currentSpeed = playerState.playbackSpeed,
            onDismiss = { showPlaybackSpeedSelector = false },
            onSpeedSelected = { speed ->
                EnhancedPlayerManager.getInstance().setPlaybackSpeed(speed)
            }
        )
    }

    // Subtitle Style Customizer
    if (showSubtitleStyleCustomizer) {
        SubtitleStyleCustomizerDialog(
            subtitleStyle = subtitleStyle,
            onStyleChange = { subtitleStyle = it },
            onDismiss = { showSubtitleStyleCustomizer = false }
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
                            val date = java.util.Date.from(offsetDateTime().toInstant())
                            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            sdf.format(date)
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

    // Chapters Bottom Sheet
    if (showChaptersSheet) {
        FlowChaptersBottomSheet(
            chapters = uiState.chapters,
            currentPosition = currentPosition,
            onChapterClick = { newPosition ->
                EnhancedPlayerManager.getInstance().seekTo(newPosition)
            },
            onDismiss = { showChaptersSheet = false }
        )
    }

    if (showShortsPrompt) {
        ShortsSuggestionDialog(
            isMusic = completeVideo.isMusic || completeVideo.title.contains("Official Audio", true) || completeVideo.title.contains("Lyrics", true),
            onPlayAsShort = {
                showShortsPrompt = false
                onPlayAsShort(completeVideo.id)
            },
            onPlayAsMusic = {
                showShortsPrompt = false
                onPlayAsMusic(completeVideo.id)
            },
            onDismiss = { showShortsPrompt = false }
        )
    }
        }
    }
}

@Composable
fun ShortsSuggestionDialog(
    isMusic: Boolean,
    onPlayAsShort: () -> Unit,
    onPlayAsMusic: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SmartDisplay, null) },
        title = {
            Text(text = "Play Mode Suggestion", style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Text("This video is less than 2 minutes long. Would you like to switch to a specialized player?")
        },
        confirmButton = {
            TextButton(onClick = onPlayAsShort) {
                Text("Shorts Player")
            }
        },
        dismissButton = {
            Row {
                if (isMusic) {
                    TextButton(onClick = onPlayAsMusic) {
                        Text("Music Player")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}
