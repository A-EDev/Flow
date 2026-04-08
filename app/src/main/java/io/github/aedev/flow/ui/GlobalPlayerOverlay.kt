package io.github.aedev.flow.ui

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ContentCopy
import android.widget.Toast
import io.github.aedev.flow.player.error.PlayerDiagnostics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.components.DraggablePlayerLayout
import io.github.aedev.flow.ui.components.PlayerDraggableState
import io.github.aedev.flow.ui.components.rememberPlayerDraggableState
import io.github.aedev.flow.ui.components.PlayerSheetValue
import io.github.aedev.flow.ui.screens.player.EnhancedVideoPlayerScreen
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.components.VideoPlayerSurface
import io.github.aedev.flow.ui.screens.player.content.PlayerContent
import io.github.aedev.flow.ui.screens.player.content.rememberCompleteVideo
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerDialogsContainer
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerBottomSheetsContainer
import io.github.aedev.flow.ui.screens.player.state.rememberPlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.rememberAudioSystemInfo
import io.github.aedev.flow.ui.screens.player.effects.*
import io.github.aedev.flow.ui.components.SubtitleOverlay
import io.github.aedev.flow.ui.screens.player.PremiumControlsOverlay
import io.github.aedev.flow.ui.screens.player.components.videoPlayerControls
import io.github.aedev.flow.ui.screens.player.components.SeekAnimationOverlay
import io.github.aedev.flow.ui.screens.player.components.BrightnessOverlay
import io.github.aedev.flow.ui.screens.player.components.VolumeOverlay
import io.github.aedev.flow.ui.screens.player.components.SpeedBoostOverlay
import io.github.aedev.flow.ui.screens.player.components.SponsorBlockSkipButton
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.player.PictureInPictureHelper
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.graphics.graphicsLayer
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.dlna.DlnaCastManager
import io.github.aedev.flow.player.dlna.DlnaDevice
import kotlinx.coroutines.launch

/**
 * GlobalPlayerOverlay - The main video player overlay that sits above everything.
 * 
 * This composable handles:
 * - Draggable player layout (expanded/collapsed states)
 * - All player effects (position tracking, controls, PiP, etc.)
 * - Dialogs and bottom sheets
 * - PiP mode rendering
 * 
 * @param video The current video to play (null if no video)
 * @param isVisible Whether the player overlay should be visible
 * @param playerSheetState State of the draggable player (expanded/collapsed)
 * @param onClose Called when the player is closed
 * @param onNavigateToChannel Called when navigating to a channel
 * @param onNavigateToShorts Called when navigating to shorts
 */
@UnstableApi
@Composable
fun GlobalPlayerOverlay(
    video: Video?,
    isVisible: Boolean,
    playerSheetState: PlayerDraggableState,
    bottomPadding: androidx.compose.ui.unit.Dp = 0.dp,
    miniPlayerScale: Float = 0.45f,
    miniPlayerShowSkipControls: Boolean = false,
    miniPlayerShowNextPrevControls: Boolean = false,
    onClose: () -> Unit,
    onMinimize: () -> Unit,
    onNavigateToChannel: (String) -> Unit,
    onNavigateToShorts: (String) -> Unit
) {
    if (video == null || !isVisible) return
    
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    
    val playerViewModel: VideoPlayerViewModel = hiltViewModel(activity)
    val playerUiState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()
    val sponsorSegments by EnhancedPlayerManager.getInstance().sponsorSegments.collectAsState()

    val screenState = rememberPlayerScreenState()
    val audioSystemInfo = rememberAudioSystemInfo(context)
    val pipPreferences = rememberPipPreferences(context)
    val completeVideo = rememberCompleteVideo(video, playerUiState)
    val canGoPrevious by playerViewModel.canGoPrevious.collectAsStateWithLifecycle()
    val comments by playerViewModel.commentsState.collectAsStateWithLifecycle()
    val isLoadingComments by playerViewModel.isLoadingComments.collectAsStateWithLifecycle()
    val hasMoreComments by playerViewModel.hasMoreComments.collectAsStateWithLifecycle()
    val isLoadingMoreComments by playerViewModel.isLoadingMoreComments.collectAsStateWithLifecycle()

    val playerPreferences = remember { PlayerPreferences(context) }
    val swipeGesturesEnabled by playerPreferences.swipeGesturesEnabled.collectAsState(initial = true)
    val sbSubmitEnabled by playerPreferences.sbSubmitEnabled.collectAsState(initial = false)
    val doubleTapSeekSeconds by playerPreferences.doubleTapSeekSeconds.collectAsState(initial = 10)

    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }

    LaunchedEffect(video.id) {
        videoAspectRatio = 16f / 9f
        screenState.zoomScale = 1f
        screenState.zoomOffsetX = 0f
        screenState.zoomOffsetY = 0f
    }

    var showSbSubmitDialog by remember { mutableStateOf(false) }
    var showDlnaDialog by remember { mutableStateOf(false) }
    val dlnaDevices by DlnaCastManager.devices.collectAsState()
    val isDlnaDiscovering by DlnaCastManager.isDiscovering.collectAsState()
    
    var localIsInPipMode by remember { mutableStateOf(false) }
    
    val progress = if (screenState.duration > 0) {
        (screenState.currentPosition.toFloat() / screenState.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    
    // Sync fullscreen state with player sheet state
    LaunchedEffect(playerSheetState.currentValue) {
        if (playerSheetState.currentValue == PlayerSheetValue.Collapsed) {
            screenState.isFullscreen = false
            screenState.zoomScale = 1f
            screenState.zoomOffsetX = 0f
            screenState.zoomOffsetY = 0f
        }
    }

    val config = LocalConfiguration.current
    val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isTablet = config.smallestScreenWidthDp >= 600

    LaunchedEffect(isLandscape, isTablet, localIsInPipMode) {
        if (isLandscape && !isTablet && !localIsInPipMode && playerSheetState.currentValue == PlayerSheetValue.Expanded) {
            // Automatically enter fullscreen on phones when rotated to landscape
            screenState.isFullscreen = true
        }
    }

    // Handle Back press in Fullscreen
    BackHandler(enabled = screenState.isFullscreen) {
        screenState.isFullscreen = false
    }
    
    // ===== EFFECTS =====
    LaunchedEffect(playerUiState.shouldDismissPlayer) {
        if (playerUiState.shouldDismissPlayer) {
            onMinimize()
            playerViewModel.resetDismissState()
        }
    }
    
    LaunchedEffect(playerUiState.isLoading) {
        if (playerUiState.isLoading && !playerUiState.isRestoredSession && !playerUiState.resumedInMiniPlayer) {
            playerSheetState.expand()
        }
        if (!playerUiState.isLoading && playerUiState.resumedInMiniPlayer) {
            playerViewModel.clearResumedInMiniPlayer()
        }
    }

    LaunchedEffect(playerSheetState.currentValue) {
        if (playerSheetState.currentValue == PlayerSheetValue.Expanded && playerUiState.isRestoredSession) {
            playerViewModel.resumeRestoredSession()
        }
    }

    BackHandler(enabled = playerSheetState.fraction < 0.5f && !localIsInPipMode) {
        playerSheetState.collapse()
    }
    
    PositionTrackingEffect(
        isPlaying = playerState.playWhenReady,
        screenState = screenState
    )

    PlaybackRefocusEffect(
        screenState = screenState,
        lifecycleOwner = lifecycleOwner
    )
    
    AutoHideControlsEffect(
        showControls = screenState.showControls,
        isPlaying = playerState.playWhenReady,
        lastInteractionTimestamp = screenState.lastInteractionTimestamp,
        onHideControls = { screenState.showControls = false }
    )
    
    GestureOverlayAutoHideEffect(screenState)
    
    SetupPipEffects(
        context = context,
        activity = activity,
        lifecycleOwner = lifecycleOwner,
        isPlaying = playerState.playWhenReady,
        pipPreferences = pipPreferences,
        onPipModeChanged = { inPipMode -> 
            localIsInPipMode = inPipMode
            screenState.isInPipMode = inPipMode
        }
    )

    FullscreenEffect(
        isFullscreen = screenState.isFullscreen,
        activity = activity,
        videoAspectRatio = videoAspectRatio,
        lifecycleOwner = lifecycleOwner
    )
    
    OrientationResetEffect(activity)
    
    WatchProgressSaveEffect(
        videoId = video.id,
        video = video,
        isPlaying = playerState.playWhenReady,
        currentPosition = screenState.currentPosition,
        duration = screenState.duration,
        uiState = playerUiState,
        viewModel = playerViewModel
    )
    
    AutoPlayNextEffect(
        hasEnded = playerState.hasEnded,
        autoplayEnabled = playerUiState.autoplayEnabled,
        hasNextInQueue = playerState.hasNext,
        relatedVideos = playerUiState.relatedVideos,
        onVideoClick = { nextVideo ->
            playerViewModel.playVideo(nextVideo)
            GlobalPlayerState.setCurrentVideo(nextVideo)
        }
    )
    
    if (!playerUiState.isRestoredSession) {
        VideoLoadEffect(
            videoId = video.id,
            context = context,
            screenState = screenState,
            viewModel = playerViewModel
        )

        PlayerInitEffect(
            videoId = video.id,
            uiState = playerUiState,
            context = context,
            screenState = screenState
        )
    }
    
    // Seekbar preview
    SeekbarPreviewEffectWithState(
        context = context,
        uiState = playerUiState,
        screenState = screenState
    )
    
    SubtitleLoadEffectWithState(screenState)
    
    LaunchedEffect(video.id, playerUiState.isRestoredSession) {
        if (!playerUiState.isRestoredSession) {
            playerViewModel.loadComments(video.id)
        }
    }
    
    SubscriptionAndLikeEffect(
        videoId = video.id,
        uiState = playerUiState,
        viewModel = playerViewModel
    )
    
    // Short video prompt
    ShortVideoPromptEffect(
        videoDuration = completeVideo.duration,
        screenState = screenState,
        isInQueue = playerState.queueSize > 1
    )

    SponsorSkipEffect(context)
    
    OrientationListenerEffect(
        context = context,
        isExpanded = playerSheetState.fraction < 0.1f,
        isFullscreen = screenState.isFullscreen,
        videoAspectRatio = videoAspectRatio,
        onEnterFullscreen = { screenState.isFullscreen = true },
        onExitFullscreen = { screenState.isFullscreen = false }
    )
    
    KeepScreenOnEffect(
        isPlaying = playerState.playWhenReady && !playerState.hasEnded,
        activity = activity
    )
    
    // Video cleanup on dispose
    DisposableEffect(video.id) {
        onDispose {
            val streamInfo = playerUiState.streamInfo
            val channelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
            val channelName = streamInfo?.uploaderName ?: video.channelName
            val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url
                ?: video.thumbnailUrl.takeIf { it.isNotEmpty() }
                ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg"
            
            val title = streamInfo?.name ?: video.title
            if (title.isNotEmpty() && screenState.duration > 0) {
                playerViewModel.savePlaybackPosition(
                    videoId = video.id,
                    position = screenState.currentPosition,
                    duration = screenState.duration,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    channelName = channelName,
                    channelId = channelId
                )
            }
        }
    }
    
    // ===== UI =====
    // ===== UI =====
    val isMinimized = playerSheetState.fraction > 0.5f

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullScreenHeight = constraints.maxHeight.toFloat()

        // PiP Mode: Show only the video surface fullscreen
        if (localIsInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                VideoPlayerSurface(
                    video = video,
                    resizeMode = screenState.resizeMode,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            DraggablePlayerLayout(
                state = playerSheetState,
                progress = progress,
                isFullscreen = screenState.isFullscreen,
                thumbnailUrl = video.thumbnailUrl.takeIf { it.isNotEmpty() }
                    ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg",
                videoAspectRatio = videoAspectRatio,
                bottomPadding = bottomPadding,
                miniPlayerScale = miniPlayerScale,
                tapToExpand = !playerUiState.isRestoredSession,
                onDismiss = onClose,
                onCollapseGesture = {
                    screenState.isFullscreen = false
                },
                onFullscreenGesture = {
                    screenState.isFullscreen = true
                },
                videoContent = { modifier ->
                    // ALWAYS use the same video surface
                    // Disable brightness/volume swipes when zoomed so pan gesture works cleanly
                    val effectiveSwipeGesturesEnabled = swipeGesturesEnabled && screenState.zoomScale <= 1.02f
                    val gestureModifier = if (!isMinimized) {
                        modifier.videoPlayerControls(
                            isSpeedBoostActive = screenState.isSpeedBoostActive,
                            onSpeedBoostChange = { screenState.isSpeedBoostActive = it },
                            showControls = screenState.showControls,
                            onShowControlsChange = { screenState.showControls = it },
                            onShowSeekBackChange = { screenState.showSeekBackAnimation = it },
                            onShowSeekForwardChange = { screenState.showSeekForwardAnimation = it },
                            onSeekAccumulate = { screenState.seekAccumulation = kotlin.math.abs(it) },
                            currentPosition = screenState.currentPosition,
                            duration = screenState.duration,
                            normalSpeed = screenState.normalSpeed,
                            scope = scope,
                            isFullscreen = screenState.isFullscreen,
                            onBrightnessChange = { screenState.brightnessLevel = it },
                            onShowBrightnessChange = { screenState.showBrightnessOverlay = it },
                            onVolumeChange = { 
                                screenState.volumeLevel = it 
                                EnhancedPlayerManager.getInstance().setVolumeBoost(it)
                            },
                            onShowVolumeChange = { screenState.showVolumeOverlay = it },
                            onBack = { 
                                screenState.isFullscreen = false
                                playerSheetState.collapse() 
                            },
                            brightnessLevel = screenState.brightnessLevel,
                            volumeLevel = screenState.volumeLevel,
                            maxVolume = audioSystemInfo.maxVolume,
                            audioManager = audioSystemInfo.audioManager,
                            activity = activity,
                            swipeGesturesEnabled = effectiveSwipeGesturesEnabled,
                            doubleTapSeekMs = doubleTapSeekSeconds * 1000L,
                            onExitFullscreen = { screenState.isFullscreen = false }
                        )
                        // Two-finger pinch-to-zoom gesture. Only activates for 2+ pointers,
                        // so single-finger gestures (brightness/volume swipe, tap) are unaffected.
                        .pointerInput("pinchZoom") {
                            awaitEachGesture {
                                val firstDown = awaitFirstDown(requireUnconsumed = false)
                                var secondPtr: PointerInputChange? = null
                                while (secondPtr == null) {
                                    val event = awaitPointerEvent()
                                    secondPtr = event.changes.firstOrNull {
                                        it.id != firstDown.id && it.pressed && !it.previousPressed
                                    }
                                    val p1 = event.changes.firstOrNull { it.id == firstDown.id }
                                    if (p1 == null || !p1.pressed) return@awaitEachGesture
                                }
                                val p2 = secondPtr!!
                                p2.consume()
                                val dx0 = firstDown.position.x - p2.position.x
                                val dy0 = firstDown.position.y - p2.position.y
                                var prevDist = kotlin.math.sqrt(dx0 * dx0 + dy0 * dy0).coerceAtLeast(1f)
                                var prevCentroidX = (firstDown.position.x + p2.position.x) / 2f
                                var prevCentroidY = (firstDown.position.y + p2.position.y) / 2f
                                val p1Id = firstDown.id
                                val p2Id = p2.id
                                do {
                                    val event = awaitPointerEvent()
                                    val tp1 = event.changes.firstOrNull { it.id == p1Id }
                                    val tp2 = event.changes.firstOrNull { it.id == p2Id }
                                    if (tp1 == null || tp2 == null || !tp1.pressed || !tp2.pressed) break
                                    tp1.consume()
                                    tp2.consume()
                                    val dx = tp1.position.x - tp2.position.x
                                    val dy = tp1.position.y - tp2.position.y
                                    val dist = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
                                    val centroidX = (tp1.position.x + tp2.position.x) / 2f
                                    val centroidY = (tp1.position.y + tp2.position.y) / 2f
                                    val panX = centroidX - prevCentroidX
                                    val panY = centroidY - prevCentroidY
                                    val factor = dist / prevDist
                                    val newScale = (screenState.zoomScale * factor).coerceIn(1f, 6f)
                                    if (newScale <= 1.02f) {
                                        screenState.zoomScale = 1f
                                        screenState.zoomOffsetX = 0f
                                        screenState.zoomOffsetY = 0f
                                    } else {
                                        screenState.zoomScale = newScale
                                        val maxPanX = (newScale - 1f) * size.width / 2f
                                        val maxPanY = (newScale - 1f) * size.height / 2f
                                        screenState.zoomOffsetX = (screenState.zoomOffsetX + panX).coerceIn(-maxPanX, maxPanX)
                                        screenState.zoomOffsetY = (screenState.zoomOffsetY + panY).coerceIn(-maxPanY, maxPanY)
                                    }
                                    prevDist = dist
                                    prevCentroidX = centroidX
                                    prevCentroidY = centroidY
                                } while (true)
                            }
                        }
                    } else {
                        modifier
                    }
                    
                    Box(modifier = gestureModifier) {
                        // Zoomable layer: video + subtitles scale together with the pinch transform
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    if (!isMinimized) {
                                        scaleX = screenState.zoomScale
                                        scaleY = screenState.zoomScale
                                        translationX = screenState.zoomOffsetX
                                        translationY = screenState.zoomOffsetY
                                    }
                                }
                        ) {
                        if (playerUiState.isRestoredSession) {
                            val thumbUrl = video.thumbnailUrl.takeIf { it.isNotEmpty() }
                                ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg"
                            coil.compose.AsyncImage(
                                model = thumbUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            VideoPlayerSurface(
                                video = video,
                                resizeMode = screenState.resizeMode,
                                modifier = Modifier.fillMaxSize(),
                                onVideoAspectRatioChanged = { videoAspectRatio = it }
                            )
                        }
                        
                        // Subtitles scale with the video when zoomed in
                        if (!isMinimized) {
                            SubtitleOverlay(
                                currentPosition = screenState.currentPosition,
                                subtitles = screenState.currentSubtitles,
                                enabled = screenState.subtitlesEnabled,
                                style = screenState.subtitleStyle,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                            )
                        }
                        } // end zoomable layer
                        
                        // Non-zoomable UI overlays (always at full-screen position)
                        if (!isMinimized) {
                            // Seek animations
                            SeekAnimationOverlay(
                                showSeekBack = screenState.showSeekBackAnimation,
                                showSeekForward = screenState.showSeekForwardAnimation,
                                seekSeconds = screenState.seekAccumulation,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            
                            // Brightness overlay
                            BrightnessOverlay(
                                isVisible = screenState.showBrightnessOverlay,
                                brightnessLevel = screenState.brightnessLevel,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 32.dp)
                            )
                            
                            // Volume overlay
                            VolumeOverlay(
                                isVisible = screenState.showVolumeOverlay,
                                volumeLevel = screenState.volumeLevel,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 32.dp)
                            )
                            
                               // 2x Speed overlay  
                            SpeedBoostOverlay(
                                isVisible = screenState.isSpeedBoostActive,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 0.dp)
                            )

                            // SponsorBlock manual skip button — shown for MUTE / NOTIFY / IGNORE segments
                            SponsorBlockSkipButton(
                                sponsorSegments = sponsorSegments,
                                currentPositionMs = screenState.currentPosition,
                                categoryActions = EnhancedPlayerManager.getInstance().sbCategoryActions,
                                onSkipClick = { endPositionMs ->
                                    EnhancedPlayerManager.getInstance().seekTo(endPositionMs)
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 16.dp, bottom = 80.dp)
                            )

                            // ── Error overlay — icon + title only; details/actions in body panel ──
                            val errorMsg  = playerUiState.error
                            if (errorMsg != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.82f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier
                                            .padding(horizontal = 32.dp)
                                            .widthIn(max = 380.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.ErrorOutline,
                                            contentDescription = "Playback error",
                                            tint = Color(0xFFFF6B6B),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Text(
                                            text = errorMsg,
                                            color = Color.White,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }
                        
                        // Controls overlay - fully expanded only
                        var showRemainingTime by rememberSaveable { mutableStateOf(false) }
                        if (!isMinimized && screenState.showControls) {
                            PremiumControlsOverlay(
                                isVisible = true,
                                isPlaying = playerState.playWhenReady,
                                isBuffering = playerState.isBuffering,
                                currentPosition = screenState.currentPosition,
                                duration = screenState.duration,
                                qualityLabel = if (playerState.currentQuality == 0) 
                                    context.getString(R.string.quality_auto_template, playerState.effectiveQuality) 
                                else 
                                    playerState.currentQuality.toString(),
                                resizeMode = screenState.resizeMode,
                                onResizeClick = { 
                                    screenState.onInteraction()
                                    screenState.cycleResizeMode() 
                                },
                                onPlayPause = {
                                    screenState.onInteraction()
                                    if (playerState.playWhenReady) {
                                        EnhancedPlayerManager.getInstance().pause()
                                    } else {
                                        EnhancedPlayerManager.getInstance().play()
                                    }
                                },
                                onSeek = { newPosition ->
                                    screenState.onInteraction()
                                    EnhancedPlayerManager.getInstance().seekTo(newPosition)
                                },
                                onBack = { playerSheetState.collapse() },
                                onSettingsClick = { screenState.showSettingsMenu = true },
                                onQualityClick = { screenState.showQualitySelector = true },
                                onFullscreenClick = { screenState.toggleFullscreen() },
                                isFullscreen = screenState.isFullscreen,
                                isPipSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && 
                                    io.github.aedev.flow.player.PictureInPictureHelper.isPipSupported(context) &&
                                    pipPreferences.manualPipButtonEnabled,
                                onPipClick = {
                                    PictureInPictureHelper.enterPipMode(
                                        activity = activity,
                                        isPlaying = playerState.isPlaying
                                    )
                                },
                                seekbarPreviewHelper = screenState.seekbarPreviewHelper,
                                chapters = playerUiState.chapters,
                                onChapterClick = { screenState.showChaptersSheet = true },
                                onSubtitleClick = {
                                    if (screenState.subtitlesEnabled) {
                                        screenState.disableSubtitles()
                                    } else {
                                        if (screenState.selectedSubtitleUrl == null && playerState.availableSubtitles.isNotEmpty()) {
                                            val targetSub = playerState.availableSubtitles.firstOrNull { !it.isAutoGenerated }
                                                ?: playerState.availableSubtitles.first()
                                            val index = playerState.availableSubtitles.indexOf(targetSub)

                                            screenState.selectedSubtitleUrl = targetSub.url
                                            EnhancedPlayerManager.getInstance().selectSubtitle(index)
                                            screenState.subtitlesEnabled = true
                                        } else if (screenState.selectedSubtitleUrl == null) {
                                            screenState.showSubtitleSelector = true
                                        } else {
                                            screenState.subtitlesEnabled = true
                                        }
                                    }
                                },
                                isSubtitlesEnabled = screenState.subtitlesEnabled,
                                autoplayEnabled = playerUiState.autoplayEnabled,
                                onAutoplayToggle = { playerViewModel.toggleAutoplay(it) },
                                onPrevious = {
                                    playerViewModel.playPrevious()
                                },
                                onNext = {
                                    playerViewModel.playNext()
                                },
                                hasPrevious = playerState.hasPrevious || canGoPrevious,
                                hasNext = playerState.hasNext || playerUiState.relatedVideos.isNotEmpty(),
                                bufferedPercentage = (if (screenState.duration > 0) screenState.bufferedPosition.toFloat() / screenState.duration.toFloat() else 0f).coerceIn(0f, 1f),
                                windowInsets = WindowInsets(0, 0, 0, 0),
                                sbSubmitEnabled = sbSubmitEnabled,
                                onSbSubmitClick = {
                                    screenState.showControls = false
                                    showSbSubmitDialog = true
                                },
                                onCastClick = {
                                    DlnaCastManager.startDiscovery(context)
                                    showDlnaDialog = true
                                },
                                isCasting = DlnaCastManager.isCasting,
                                isLive = !playerUiState.hlsUrl.isNullOrEmpty(),
                                onSleepTimerClick = { screenState.showSleepTimerSheet = true },
                                isSleepTimerActive = io.github.aedev.flow.player.SleepTimerManager.isActive,
                                showRemainingTime = showRemainingTime,
                                onToggleRemainingTime = { showRemainingTime = !showRemainingTime }
                            )
                        }
                    }
                },
            bodyContent = { alpha, videoHeight ->
                EnhancedVideoPlayerScreen(
                    viewModel = playerViewModel,
                    video = video,
                    alpha = alpha,
                    videoPlayerHeight = videoHeight,
                    screenState = screenState,
                    onVideoClick = { clickedVideo ->
                        if (clickedVideo.isShort) {
                            onClose()
                            EnhancedPlayerManager.getInstance().stop()
                            onNavigateToShorts(clickedVideo.id)
                        } else {
                            playerViewModel.playVideo(clickedVideo)
                            GlobalPlayerState.setCurrentVideo(clickedVideo)
                        }
                    },
                    onChannelClick = { channelId ->
                        onNavigateToChannel(channelId)
                    }
                )
            },
            miniControls = { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                    val currentSizeScale by remember { derivedStateOf { playerSheetState.miniSizeScale.value } }
                    MiniPlayerControls(
                        playerState = playerState,
                        showSkipControls = miniPlayerShowSkipControls,
                        showNextPrevControls = miniPlayerShowNextPrevControls,
                        sizeScale = currentSizeScale,
                        onPlayPause = {
                            if (playerUiState.isRestoredSession) {
                                playerViewModel.resumeRestoredSession(stayMini = true)
                            } else if (playerState.playWhenReady) {
                                EnhancedPlayerManager.getInstance().pause()
                            } else {
                                EnhancedPlayerManager.getInstance().play()
                            }
                        },
                        onSkipForward = {
                            EnhancedPlayerManager.getInstance().seekTo(screenState.currentPosition + 10000)
                        },
                        onSkipBack = {
                            EnhancedPlayerManager.getInstance().seekTo(screenState.currentPosition - 10000)
                        },
                        onNext = {
                            playerViewModel.playNext()
                        },
                        onPrevious = {
                            playerViewModel.playPrevious()
                        },
                        onClose = onClose
                    )
                    if (playerUiState.isRestoredSession) {
                        Text(
                            text = stringResource(R.string.player_mini_player_continue_watching_label),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 4.dp)
                                .background(
                                    Color(0xBB000000),
                                    RoundedCornerShape(3.dp)
                                )
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        )
        
        // Dialogs
        PlayerDialogsContainer(
            screenState = screenState,
            playerState = playerState,
            uiState = playerUiState,
            video = completeVideo,
            viewModel = playerViewModel
        )

        // SB Submit dialog
        if (showSbSubmitDialog) {
            val initialPosition = remember { screenState.currentPosition }
            io.github.aedev.flow.ui.screens.player.dialogs.SbSubmitSegmentDialog(
                videoId = video.id,
                currentPositionMs = initialPosition,
                onDismiss = { showSbSubmitDialog = false }
            )
        }
        
        // DLNA device picker dialog
        if (showDlnaDialog) {
            DlnaDevicePickerDialog(
                devices = dlnaDevices,
                isDiscovering = isDlnaDiscovering,
                isCasting = DlnaCastManager.isCasting,
                videoTitle = video.title,
                onDeviceSelected = { device ->
                    var streamUrl = ""
                    val streamInfo = playerUiState.streamInfo
                    if (streamInfo != null) {
                        val castStream = streamInfo.videoStreams
                            ?.filter { it.height <= 1080 }
                            ?.maxByOrNull { it.height }
                        if (castStream != null) {
                            streamUrl = castStream.content ?: castStream.url ?: ""
                        }
                    }
                    if (streamUrl.isEmpty()) {
                        streamUrl = EnhancedPlayerManager.getInstance().getPlayer()
                            ?.currentMediaItem?.localConfiguration?.uri?.toString() ?: ""
                    }
                    
                    if (streamUrl.isNotEmpty() && !streamUrl.startsWith("local://")) {
                        DlnaCastManager.castTo(device, streamUrl, video.title)
                    }
                    showDlnaDialog = false
                },
                onStopCasting = {
                    DlnaCastManager.stop()
                    showDlnaDialog = false
                },
                onDismiss = {
                    DlnaCastManager.stopDiscovery()
                    showDlnaDialog = false
                }
            )
        }
        
        // Bottom Sheets
        PlayerBottomSheetsContainer(
            screenState = screenState,
            uiState = playerUiState,
            video = video,
            completeVideo = completeVideo,
            comments = comments,
            isLoadingComments = isLoadingComments,
            isLoadingMoreComments = isLoadingMoreComments,
            hasMoreComments = hasMoreComments,
            onLoadMoreComments = { videoId -> playerViewModel.loadMoreComments(videoId) },
            context = context,
            onPlayAsShort = { videoId ->
                onClose()
                onNavigateToShorts(videoId)
            },
            onPlayAsMusic = { _ ->
                // Handle play as music - still placeholder for now
            },
            onLoadReplies = { comment ->
                playerViewModel.loadCommentReplies(comment)
            },
            onNavigateToChannel = { channelId ->
                onNavigateToChannel(channelId)
            }
        )
    }
  }
}

/**
 * Mini Player Controls - Dynamically arranges Play/Pause, Rewind/FastForward, and Next/Previous.
 */
@Composable
private fun MiniPlayerControls(
    playerState: io.github.aedev.flow.player.state.EnhancedPlayerState,
    showSkipControls: Boolean,
    showNextPrevControls: Boolean,
    sizeScale: Float = 1f,
    onPlayPause: () -> Unit,
    onSkipForward: () -> Unit,
    onSkipBack: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp > 600

    val scaleMult = sizeScale.coerceIn(1f, 1.6f)

    val baseBgSize  = if (isTablet) 36.dp else 26.dp
    val baseIconSize = if (isTablet) 28.dp else 22.dp
    val finalBgSize   = baseBgSize   * scaleMult
    val finalIconSize = baseIconSize * scaleMult

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(if (isTablet) 48.dp else 40.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            if (playerState.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(if (isTablet) 32.dp else 24.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = if (playerState.playWhenReady) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playerState.playWhenReady) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(if (isTablet) 40.dp else 32.dp)
                )
            }
        }

        IconButton(
            onClick = {
                EnhancedPlayerManager.getInstance().stop()
                GlobalPlayerState.hideMiniPlayer()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(if (isTablet) 48.dp else 40.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(if (isTablet) 32.dp else 28.dp)
            )
        }

        if (showSkipControls || showNextPrevControls) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showNextPrevControls) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier
                            .size(finalBgSize)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(finalIconSize)
                        )
                    }
                }

                if (showSkipControls) {
                    IconButton(
                        onClick = onSkipBack,
                        modifier = Modifier
                            .size(finalBgSize)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Replay10,
                            contentDescription = "Skip Back 10s",
                            tint = Color.White,
                            modifier = Modifier.size(finalIconSize)
                        )
                    }
                }

                if (showSkipControls) {
                    IconButton(
                        onClick = onSkipForward,
                        modifier = Modifier
                            .size(finalBgSize)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Forward10,
                            contentDescription = "Skip Forward 10s",
                            tint = Color.White,
                            modifier = Modifier.size(finalIconSize)
                        )
                    }
                }

                if (showNextPrevControls) {
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier
                            .size(finalBgSize)
                            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(finalIconSize)
                        )
                    }
                }
            }
        }
    }
}

/** DLNA / UPnP device-picker dialog shown when the cast button is pressed. */
@Composable
private fun DlnaDevicePickerDialog(
    devices: List<DlnaDevice>,
    isDiscovering: Boolean,
    isCasting: Boolean,
    videoTitle: String,
    onDeviceSelected: (DlnaDevice) -> Unit,
    onStopCasting: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = if (isCasting) "Casting to TV" else "Cast to Device")
                if (isDiscovering) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                }
            }
        },
        text = {
            Column {
                if (!isCasting && devices.isEmpty() && !isDiscovering) {
                    Text(
                        text = "No DLNA/UPnP renderers found on this network.\n\n" +
                            "Make sure your TV or media player (VLC, Kodi, etc.) is on the " +
                            "same Wi-Fi network and has media renderer mode enabled.",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (!isCasting && devices.isEmpty()) {
                    Text(
                        text = "Searching for DLNA devices…",
                        style = MaterialTheme.typography.bodySmall
                    )
                } else if (isCasting) {
                    Text(
                        text = "Now casting: $videoTitle",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyColumn {
                        items(devices) { device ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onDeviceSelected(device) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = device.friendlyName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isCasting) {
                TextButton(onClick = onStopCasting) { Text("Stop Casting") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
