package com.flow.youtube.ui.screens.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.*
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.flow.youtube.data.local.VideoQuality
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.GlobalPlayerState
import com.flow.youtube.player.PictureInPictureHelper
import com.flow.youtube.ui.components.VideoCardFullWidth
import com.flow.youtube.ui.theme.extendedColors
import kotlinx.coroutines.delay
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    video: Video,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: VideoPlayerViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    
    var showQualitySelector by remember { mutableStateOf(false) }
    var showSubtitleSelector by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var bufferedPercentage by remember { mutableStateOf(0) }
    var isBuffering by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isInPipMode by remember { mutableStateOf(false) }
    
    // Brightness state for gesture overlay
    var currentBrightness by remember { mutableFloatStateOf(0.5f) }
    
    // PiP support - update params when playback state changes
    LaunchedEffect(isPlaying) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
            PictureInPictureHelper.updatePipParams(
                activity = activity,
                aspectRatioWidth = 16,
                aspectRatioHeight = 9,
                isPlaying = isPlaying,
                autoEnterEnabled = true
            )
        }
    }
    
    // Handle PiP mode changes via lifecycle
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
    
    // Initialize view history
    LaunchedEffect(Unit) {
        viewModel.initializeViewHistory(context)
    }
    
    // Use GlobalPlayerState's ExoPlayer instance
    val exoPlayer = remember {
        GlobalPlayerState.exoPlayer ?: ExoPlayer.Builder(context)
            .setLoadControl(
                // Optimize for fast startup
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        2000,   // Min buffer (2s for fast start)
                        10000,  // Max buffer (10s)
                        1500,   // Buffer for playback
                        2000    // Buffer for playback after rebuffer
                    )
                    .build()
            )
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                
                // Add player listener
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        isBuffering = playbackState == Player.STATE_BUFFERING
                        
                        // Adaptive quality scaling
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                // Scale down quality if buffering in adaptive mode
                                if (uiState.isAdaptiveMode) {
                                    viewModel.scaleDownQuality()
                                }
                            }
                            Player.STATE_READY -> {
                                // Scale up quality if buffer is healthy
                                if (uiState.isAdaptiveMode && bufferedPercentage > 80) {
                                    viewModel.scaleUpQuality()
                                }
                            }
                        }
                    }
                    
                    override fun onIsPlayingChanged(playing: Boolean) {
                        isPlaying = playing
                    }
                })
            }
    }
    
    // Create data source factory
    val dataSourceFactory = remember {
        DefaultDataSource.Factory(context)
    }
    
    // Load video info on first composition
    LaunchedEffect(video.id) {
        viewModel.loadVideoInfo(video.id, VideoQuality.AUTO)
        // Load subscription and like state
        viewModel.loadSubscriptionAndLikeState(video.channelId, video.id)
    }
    
    // Load saved playback position
    LaunchedEffect(uiState.savedPosition) {
        uiState.savedPosition?.collect { savedPos ->
            if (savedPos > 0 && exoPlayer.currentPosition < 1000) {
                exoPlayer.seekTo(savedPos)
            }
        }
    }
    
    // Update player when streams change
    LaunchedEffect(uiState.videoStream, uiState.audioStream) {
        val videoStream = uiState.videoStream
        val audioStream = uiState.audioStream
        
        if (videoStream != null && audioStream != null) {
            try {
                val videoUrl = videoStream.url ?: return@LaunchedEffect
                val videoMediaItem = MediaItem.fromUri(videoUrl)
                val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(videoMediaItem)
                
                val audioUrl = audioStream.url ?: return@LaunchedEffect
                val audioMediaItem = MediaItem.fromUri(audioUrl)
                val audioSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(audioMediaItem)
                
                val mergedSource = MergingMediaSource(videoSource, audioSource)
                
                val finalSource = if (uiState.subtitlesEnabled && uiState.selectedSubtitle != null) {
                    val subtitle = uiState.selectedSubtitle!!
                    val subtitleUrl = subtitle.url
                    val subtitleUri = android.net.Uri.parse(subtitleUrl)
                    val subtitleMediaItem = MediaItem.SubtitleConfiguration.Builder(subtitleUri)
                        .setMimeType(MimeTypes.TEXT_VTT)
                        .setLanguage(subtitle.languageCode)
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                    
                    val subtitleSource = SingleSampleMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(subtitleMediaItem, C.TIME_UNSET)
                    
                    MergingMediaSource(mergedSource, subtitleSource)
                } else {
                    mergedSource
                }
                
                val wasPlaying = exoPlayer.isPlaying
                val currentPos = exoPlayer.currentPosition
                
                exoPlayer.setMediaSource(finalSource)
                exoPlayer.prepare()
                
                if (currentPos > 0) {
                    exoPlayer.seekTo(currentPos)
                }
                
                exoPlayer.playWhenReady = wasPlaying
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (videoStream != null) {
            val videoUrl = videoStream.url ?: return@LaunchedEffect
            val videoMediaItem = MediaItem.fromUri(videoUrl)
            val videoSource = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(videoMediaItem)
            
            exoPlayer.setMediaSource(videoSource)
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
        }
    }
    
    // Update playback progress
    LaunchedEffect(Unit) {
        while (true) {
            if (exoPlayer.isPlaying || exoPlayer.playbackState == Player.STATE_READY) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                bufferedPercentage = exoPlayer.bufferedPercentage
                
                // Save playback position every 5 seconds
                if (currentPosition % 5000 < 100) {
                    val channelId = uiState.streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
                    val channelName = uiState.streamInfo?.uploaderName ?: video.channelName
                    
                    viewModel.savePlaybackPosition(
                        videoId = video.id,
                        position = currentPosition,
                        duration = duration,
                        title = uiState.streamInfo?.name ?: video.title,
                        thumbnailUrl = video.thumbnailUrl,
                        channelName = channelName,
                        channelId = channelId
                    )
                }
            }
            delay(100)
        }
    }
    
    // Auto-hide controls
    LaunchedEffect(showControls) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }
    
    // Handle fullscreen
    LaunchedEffect(isFullscreen) {
        activity?.let { act ->
            if (isFullscreen) {
                // Enter fullscreen mode
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                
                // Keep screen on during playback
                act.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                
                // Hide system UI with immersive mode
                val windowInsetsController = WindowCompat.getInsetsController(act.window, view)
                windowInsetsController.apply {
                    hide(WindowInsetsCompat.Type.systemBars())
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                }
            } else {
                // Exit fullscreen mode
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                
                // Remove keep screen on flag
                act.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                
                // Show system UI
                val windowInsetsController = WindowCompat.getInsetsController(act.window, view)
                windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        viewModel.setFullscreen(isFullscreen)
    }
    
    // Handle back button - exit fullscreen first, then show mini player
    BackHandler(enabled = isFullscreen) {
        isFullscreen = false
    }
    
    // Handle back button - minimize to mini player when not fullscreen
    BackHandler(enabled = !isFullscreen) {
        // Set current video in global state
        GlobalPlayerState.setCurrentVideo(video)
        
        // Update playback info
        GlobalPlayerState.updatePlaybackInfo(currentPosition, duration)
        
        // Show mini player
        GlobalPlayerState.showMiniPlayer()
        
        // Navigate back
        onBack()
    }
    
    // Set current video when screen loads
    LaunchedEffect(video.id) {
        GlobalPlayerState.setCurrentVideo(video)
        GlobalPlayerState.hideMiniPlayer()
    }
    
    // Clean up player on dispose
    DisposableEffect(Unit) {
        onDispose {
            val channelId = uiState.streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
            val channelName = uiState.streamInfo?.uploaderName ?: video.channelName
            
            viewModel.savePlaybackPosition(
                videoId = video.id,
                position = currentPosition,
                duration = duration,
                title = uiState.streamInfo?.name ?: video.title,
                thumbnailUrl = video.thumbnailUrl,
                channelName = channelName,
                channelId = channelId
            )
            // Don't release the player - it's managed by GlobalPlayerState
            // exoPlayer.release()
            
            // Reset orientation and flags
            activity?.let { act ->
                act.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                act.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }
    
    val playerHeight = if (isFullscreen) {
        configuration.screenHeightDp.dp
    } else {
        (configuration.screenWidthDp.dp * 9f / 16f).coerceAtLeast(250.dp)
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Video Player Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(playerHeight)
                    .background(Color.Black)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                } else if (uiState.error != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "Failed to load video",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White
                        )
                        TextButton(
                            onClick = { viewModel.loadVideoInfo(video.id) },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)
                        ) {
                            Text("Retry")
                        }
                    }
                } else if (uiState.videoStream != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                layoutParams = FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                useController = false
                                controllerShowTimeoutMs = 0
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                    
                    // Enhanced Gesture Overlay (only when not in PiP)
                    if (!isInPipMode) {
                        // Simple tap to toggle controls and double-tap seek
                        var lastTapTime by remember { mutableStateOf(0L) }
                        var lastTapX by remember { mutableFloatStateOf(0f) }
                        val screenWidth = LocalConfiguration.current.screenWidthDp.dp
                        
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = { offset ->
                                            val now = System.currentTimeMillis()
                                            if (now - lastTapTime < 300 && abs(offset.x - lastTapX) < 100) {
                                                // Double tap - seek forward or backward
                                                val seekAmount = if (offset.x < size.width / 2) -10000L else 10000L
                                                val newPosition = (exoPlayer.currentPosition + seekAmount).coerceIn(0L, duration)
                                                exoPlayer.seekTo(newPosition)
                                            } else {
                                                // Single tap - toggle controls
                                                showControls = !showControls
                                            }
                                            lastTapTime = now
                                            lastTapX = offset.x
                                        }
                                    )
                                }
                        )
                    }
                }
                
                // Buffering indicator
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = Color.White
                    )
                }
                
                // Custom Controls Overlay (hide in PiP mode)
                if (!isInPipMode) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showControls,
                        enter = fadeIn(animationSpec = tween(200)),
                        exit = fadeOut(animationSpec = tween(200)),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                        ) {
                            // Top controls
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopStart)
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = onBack,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = Color.White
                                    )
                                }
                            
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Quality button
                                if (uiState.availableQualities.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = { showQualitySelector = true },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.5f),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = uiState.selectedQuality.label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                
                                // Subtitle button
                                if (uiState.subtitles.isNotEmpty()) {
                                    FilledTonalButton(
                                        onClick = { showSubtitleSelector = true },
                                        colors = ButtonDefaults.filledTonalButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.5f),
                                            contentColor = Color.White
                                        ),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = if (uiState.subtitlesEnabled) "CC" else "CC Off",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                
                                // Fullscreen button
                                IconButton(
                                    onClick = { isFullscreen = !isFullscreen },
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.5f))
                                ) {
                                    Icon(
                                        imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                        contentDescription = if (isFullscreen) "Exit Fullscreen" else "Fullscreen",
                                        tint = Color.White
                                    )
                                }
                                
                                // PiP button (Android O+)
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
                                    PictureInPictureHelper.isPipSupported(context)) {
                                    IconButton(
                                        onClick = {
                                            activity?.let { act ->
                                                PictureInPictureHelper.enterPipMode(
                                                    activity = act,
                                                    aspectRatioWidth = 16,
                                                    aspectRatioHeight = 9,
                                                    isPlaying = isPlaying
                                                )
                                            }
                                        },
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.PictureInPictureAlt,
                                            contentDescription = "Picture in Picture",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }
                        }
                        
                        // Center play/pause button
                        IconButton(
                            onClick = {
                                if (isPlaying) {
                                    exoPlayer.pause()
                                } else {
                                    exoPlayer.play()
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(48.dp)
                            )
                        }
                        
                        // Bottom controls
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                        ) {
                            // Progress bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = formatTime(currentPosition),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                                
                                Slider(
                                    value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                                    onValueChange = { progress ->
                                        val newPosition = (progress * duration).toLong()
                                        exoPlayer.seekTo(newPosition)
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                    )
                                )
                                
                                Text(
                                    text = formatTime(duration),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
            
            // Video details and related videos (only if not fullscreen)
            if (!isFullscreen) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    // Video Information
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = uiState.streamInfo?.name ?: video.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    uiState.streamInfo?.let { streamInfo ->
                                        Text(
                                            text = "${streamInfo.viewCount} views",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.extendedColors.textSecondary
                                        )
                                    }
                                }
                                
                                // Action buttons
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            when (uiState.likeState) {
                                                "LIKED" -> viewModel.removeLikeState(video.id)
                                                else -> viewModel.likeVideo(
                                                    video.id,
                                                    uiState.streamInfo?.name ?: video.title,
                                                    video.thumbnailUrl,
                                                    uiState.streamInfo?.uploaderName ?: video.channelName
                                                )
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (uiState.likeState == "LIKED") Icons.Filled.ThumbUp else Icons.Filled.ThumbUp,
                                            contentDescription = "Like",
                                            tint = if (uiState.likeState == "LIKED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            when (uiState.likeState) {
                                                "DISLIKED" -> viewModel.removeLikeState(video.id)
                                                else -> viewModel.dislikeVideo(video.id)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ThumbDown,
                                            contentDescription = "Dislike",
                                            tint = if (uiState.likeState == "DISLIKED") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    IconButton(onClick = { /* Share */ }) {
                                        Icon(
                                            imageVector = Icons.Filled.Share,
                                            contentDescription = "Share"
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Channel info
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onTap = {
                                                    val channelUrl = uiState.streamInfo?.uploaderUrl
                                                        ?: "https://youtube.com/channel/${video.channelId}"
                                                    onChannelClick(channelUrl)
                                                }
                                            )
                                        }
                                ) {
                                    AsyncImage(
                                        model = video.channelThumbnailUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape)
                                    )
                                    
                                    Column {
                                        Text(
                                            text = uiState.streamInfo?.uploaderName ?: video.channelName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                                
                                Button(
                                    onClick = {
                                        val channelId = uiState.streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
                                        val channelName = uiState.streamInfo?.uploaderName ?: video.channelName
                                        val channelThumbnail = video.channelThumbnailUrl ?: ""
                                        viewModel.toggleSubscription(channelId, channelName, channelThumbnail)
                                    },
                                    colors = if (uiState.isSubscribed) {
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                                        )
                                    } else {
                                        ButtonDefaults.buttonColors()
                                    }
                                ) {
                                    Text(if (uiState.isSubscribed) "Subscribed" else "Subscribe")
                                }
                            }
                            
                            // Description
                            uiState.streamInfo?.description?.content?.let { description ->
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    
                    // Related Videos
                    if (uiState.relatedVideos.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Related Videos",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        
                        items(
                            items = uiState.relatedVideos,
                            key = { it.id }
                        ) { relatedVideo ->
                            VideoCardFullWidth(
                                video = relatedVideo,
                                onClick = { onVideoClick(relatedVideo) }
                            )
                        }
                    }
                }
            }
        }
        
        // Quality Selector Bottom Sheet
        if (showQualitySelector) {
            ModalBottomSheet(
                onDismissRequest = { showQualitySelector = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Video Quality",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    uiState.availableQualities.forEach { quality ->
                        val isSelected = quality == uiState.selectedQuality
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.switchQuality(quality)
                                    showQualitySelector = false
                                }
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = quality.label,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                
                                if (quality != VideoQuality.AUTO) {
                                    Text(
                                        text = "${quality.height}p",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.extendedColors.textSecondary
                                    )
                                } else {
                                    Text(
                                        text = "Adapts to network speed",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.extendedColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Subtitle Selector Bottom Sheet
        if (showSubtitleSelector) {
            ModalBottomSheet(
                onDismissRequest = { showSubtitleSelector = false }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = "Subtitles/CC",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                    
                    // Off option
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !uiState.subtitlesEnabled,
                            onClick = {
                                viewModel.toggleSubtitles(false)
                                showSubtitleSelector = false
                            }
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = "Off",
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (!uiState.subtitlesEnabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                    
                    // Subtitle language options
                    uiState.subtitles.forEach { subtitle ->
                        val isSelected = uiState.subtitlesEnabled && 
                                       uiState.selectedSubtitle?.languageCode == subtitle.languageCode
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.selectSubtitleTrack(subtitle)
                                    viewModel.toggleSubtitles(true)
                                    showSubtitleSelector = false
                                }
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = subtitle.language,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                
                                if (subtitle.isAutoGenerated) {
                                    Text(
                                        text = "Auto-generated",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.extendedColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

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
