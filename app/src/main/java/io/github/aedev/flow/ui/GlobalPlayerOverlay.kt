package io.github.aedev.flow.ui

import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import io.github.aedev.flow.ui.components.FlowChaptersBottomSheet
import io.github.aedev.flow.ui.components.Media3SubtitleOverlay
import io.github.aedev.flow.ui.components.SubtitleStyle
import io.github.aedev.flow.ui.screens.player.content.PlayerContent
import io.github.aedev.flow.ui.screens.player.content.rememberCompleteVideo
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerDialogsContainer
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerBottomSheetsContainer
import io.github.aedev.flow.ui.screens.player.state.rememberPlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.rememberAudioSystemInfo
import io.github.aedev.flow.ui.screens.player.effects.*
import io.github.aedev.flow.ui.screens.player.PremiumControlsOverlay
import io.github.aedev.flow.ui.screens.player.components.videoPlayerControls
import io.github.aedev.flow.ui.screens.player.components.SeekAnimationOverlay
import io.github.aedev.flow.ui.screens.player.components.BrightnessOverlay
import io.github.aedev.flow.ui.screens.player.components.VolumeOverlay
import io.github.aedev.flow.ui.screens.player.components.SpeedBoostOverlay
import io.github.aedev.flow.ui.screens.player.components.SponsorBlockSkipButton
import io.github.aedev.flow.ui.screens.player.components.SettingsMenuDialog
import io.github.aedev.flow.ui.screens.player.components.PlayerSettingsPage
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.player.PictureInPictureHelper
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.graphics.graphicsLayer
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.dlna.DlnaCastManager
import io.github.aedev.flow.player.dlna.DlnaDevice
import java.util.Locale
import kotlinx.coroutines.delay
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
    val brightnessSwipeGesturesEnabled by playerPreferences.brightnessSwipeGesturesEnabled.collectAsState(initial = true)
    val volumeSwipeGesturesEnabled by playerPreferences.volumeSwipeGesturesEnabled.collectAsState(initial = true)
    val sbSubmitEnabled by playerPreferences.sbSubmitEnabled.collectAsState(initial = false)
    val doubleTapSeekSeconds by playerPreferences.doubleTapSeekSeconds.collectAsState(initial = 10)
    val disableShortsPlayer by playerPreferences.disableShortsPlayer.collectAsState(initial = false)
    val savedSubtitleStyle by playerPreferences.subtitleStyle.collectAsState(initial = SubtitleStyle())
    val rememberPlaybackSpeed by playerPreferences.rememberPlaybackSpeed.collectAsState(initial = false)
    val adaptivePlayerSizeEnabled by playerPreferences.adaptivePlayerSizeEnabled.collectAsState(initial = true)
    val lockModeEnabled by playerPreferences.overlayLockModeEnabled.collectAsState(initial = false)
    val commentsEnabled by playerPreferences.commentsEnabled.collectAsState(initial = true)

    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    val effectiveVideoAspectRatio = if (adaptivePlayerSizeEnabled || screenState.isFullscreen) {
        videoAspectRatio
    } else {
        16f / 9f
    }
    var expandedPlayerBottom by remember { mutableStateOf(0.dp) }
    var pipForcedFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(video.id) {
        videoAspectRatio = 16f / 9f
        screenState.zoomScale = 1f
        screenState.zoomOffsetX = 0f
        screenState.zoomOffsetY = 0f
        screenState.showZoomIndicator = false
        screenState.zoomIndicatorSequence = 0
    }

    LaunchedEffect(savedSubtitleStyle) {
        if (screenState.subtitleStyle != savedSubtitleStyle) {
            screenState.subtitleStyle = savedSubtitleStyle
        }
    }

    LaunchedEffect(lockModeEnabled) {
        if (!lockModeEnabled && screenState.isTouchLocked) {
            screenState.isTouchLocked = false
        }
    }

    var showSbSubmitDialog by remember { mutableStateOf(false) }
    var showDlnaDialog by remember { mutableStateOf(false) }
    val dlnaDevices by DlnaCastManager.devices.collectAsState()
    val isDlnaDiscovering by DlnaCastManager.isDiscovering.collectAsState()
    
    var localIsInPipMode by remember { mutableStateOf(false) }
    var keepMiniOnQueueAutoAdvance by remember { mutableStateOf(false) }
    
    val progress = if (screenState.duration > 0) {
        (screenState.currentPosition.toFloat() / screenState.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f
    
    // Sync fullscreen state with player sheet state
    LaunchedEffect(playerSheetState.currentValue) {
        if (playerSheetState.currentValue == PlayerSheetValue.Collapsed) {
            screenState.isFullscreen = false
            screenState.dismissMediaSheets()
            screenState.zoomScale = 1f
            screenState.zoomOffsetX = 0f
            screenState.zoomOffsetY = 0f
            screenState.showZoomIndicator = false
        }
    }

    LaunchedEffect(screenState.isFullscreen) {
        screenState.dismissMediaSheets()
    }

    LaunchedEffect(screenState.zoomIndicatorSequence) {
        if (screenState.showZoomIndicator) {
            delay(if (screenState.zoomScale > 1.02f) 900 else 600)
            screenState.showZoomIndicator = false
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

    LaunchedEffect(Unit) {
        EnhancedPlayerManager.getInstance().queueAutoAdvanceEvent.collect {
            keepMiniOnQueueAutoAdvance = playerSheetState.currentValue == PlayerSheetValue.Collapsed
        }
    }
    
    LaunchedEffect(playerUiState.isLoading) {
        val isQueueAutoAdvanceInMiniPlayer =
            keepMiniOnQueueAutoAdvance &&
            playerState.queueTitle != null &&
            playerSheetState.currentValue == PlayerSheetValue.Collapsed

        if (
            playerUiState.isLoading &&
            !playerUiState.isRestoredSession &&
            !playerUiState.resumedInMiniPlayer &&
            !isQueueAutoAdvanceInMiniPlayer
        ) {
            playerSheetState.expand()
        }
        if (!playerUiState.isLoading) {
            if (playerUiState.resumedInMiniPlayer) {
                playerViewModel.clearResumedInMiniPlayer()
            }
            keepMiniOnQueueAutoAdvance = false
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
        hasEnded = playerState.hasEnded,
        lastInteractionTimestamp = screenState.lastInteractionTimestamp,
        isTouchLocked = screenState.isTouchLocked,
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
        videoAspectRatio = effectiveVideoAspectRatio,
        lifecycleOwner = lifecycleOwner,
        suppressFullscreenRequest = pipForcedFullscreen
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

        PlaybackStartupRecoveryEffect(
            videoId = video.id,
            uiState = playerUiState,
            screenState = screenState,
            viewModel = playerViewModel
        )
    }
    
    // Seekbar preview
    SeekbarPreviewEffectWithState(
        context = context,
        uiState = playerUiState,
        screenState = screenState
    )
    
    val globalCurrentVideo by GlobalPlayerState.currentVideo.collectAsState()
    LaunchedEffect(globalCurrentVideo?.id) {
        val current = globalCurrentVideo
        if (current != null && !playerUiState.isRestoredSession) {
            if (current.id != playerUiState.cachedVideo?.id || playerUiState.streamInfo?.id != current.id) {
                playerViewModel.syncWithCurrentPlayerVideo(current)
            }
            if (commentsEnabled) {
                playerViewModel.loadComments(current.id)
            }
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
        isInQueue = playerState.queueSize > 1,
        disableShortsPlayer = disableShortsPlayer
    )

    SponsorSkipEffect(context)
    
    OrientationListenerEffect(
        context = context,
        isExpanded = playerSheetState.fraction < 0.1f,
        isFullscreen = screenState.isFullscreen,
        videoAspectRatio = effectiveVideoAspectRatio,
        onEnterFullscreen = { screenState.isFullscreen = true },
        onExitFullscreen = { screenState.isFullscreen = false }
    )
    
    KeepScreenOnEffect(
        isPlaying = playerState.playWhenReady && !playerState.hasEnded,
        activity = activity,
        lifecycleOwner = lifecycleOwner
    )

    LaunchedEffect(playerState.hasEnded, playerSheetState.fraction, localIsInPipMode) {
        if (playerState.hasEnded && playerSheetState.fraction <= 0.5f && !localIsInPipMode) {
            screenState.showControls = true
        }
    }
    
    LaunchedEffect(localIsInPipMode, isLandscape) {
        if (localIsInPipMode) {
            playerSheetState.expand()
            if (!screenState.isFullscreen) {
                pipForcedFullscreen = true
                screenState.isFullscreen = true
            }
            screenState.showControls = false
        } else if (pipForcedFullscreen && !isLandscape) {
            pipForcedFullscreen = false
            screenState.isFullscreen = false
        }
    }

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
    val isMinimized = playerSheetState.fraction > 0.5f
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullScreenHeight = constraints.maxHeight.toFloat()
        val mediaSheetExpandedHeight = with(density) {
            val availablePx = fullScreenHeight - expandedPlayerBottom.toPx()
            if (expandedPlayerBottom > 0.dp && availablePx > 0f) {
                availablePx.toDp()
            } else {
                config.screenHeightDp.dp * 0.75f
            }
        }
        val canUseFullscreenSidePanel = screenState.isFullscreen && maxWidth > maxHeight
        val settingsInitialPage = when {
            screenState.showQualitySelector -> PlayerSettingsPage.Quality
            screenState.showAudioTrackSelector -> PlayerSettingsPage.Audio
            screenState.showPlaybackSpeedSelector -> PlayerSettingsPage.Speed
            screenState.showSubtitleSelector -> PlayerSettingsPage.Subtitles
            else -> PlayerSettingsPage.Main
        }
        val showSettingsSurface = screenState.showSettingsMenu ||
            screenState.showQualitySelector ||
            screenState.showAudioTrackSelector ||
            screenState.showPlaybackSpeedSelector ||
            screenState.showSubtitleSelector
        val fullscreenSidePanelVisible = canUseFullscreenSidePanel &&
            (showSettingsSurface || screenState.showChaptersSheet)
        val fullscreenSidePanelTargetWidth = maxWidth * 0.36f
        val fullscreenSidePanelTargetWidthPx = with(density) { fullscreenSidePanelTargetWidth.toPx() }
        val fullscreenSidePanelWidthPx = remember { Animatable(0f) }
        LaunchedEffect(fullscreenSidePanelVisible, fullscreenSidePanelTargetWidthPx) {
            fullscreenSidePanelWidthPx.updateBounds(
                lowerBound = 0f,
                upperBound = fullscreenSidePanelTargetWidthPx
            )
            fullscreenSidePanelWidthPx.animateTo(
                targetValue = if (fullscreenSidePanelVisible) fullscreenSidePanelTargetWidthPx else 0f,
                animationSpec = tween(durationMillis = 260)
            )
        }
        fun closeFullscreenSidePanel() {
            scope.launch {
                fullscreenSidePanelWidthPx.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 220)
                )
                screenState.dismissMediaSheets()
            }
        }
        val fullscreenSidePanelDragModifier = Modifier.pointerInput(fullscreenSidePanelTargetWidthPx, fullscreenSidePanelVisible) {
            if (!fullscreenSidePanelVisible || fullscreenSidePanelTargetWidthPx <= 0f) return@pointerInput
            val velocityTracker = VelocityTracker()
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    velocityTracker.addPointerInputChange(change)
                    change.consume()
                    scope.launch {
                        fullscreenSidePanelWidthPx.snapTo(
                            (fullscreenSidePanelWidthPx.value - dragAmount)
                                .coerceIn(0f, fullscreenSidePanelTargetWidthPx)
                        )
                    }
                },
                onDragCancel = {
                    velocityTracker.resetTracking()
                    scope.launch {
                        fullscreenSidePanelWidthPx.animateTo(
                            targetValue = fullscreenSidePanelTargetWidthPx,
                            animationSpec = tween(durationMillis = 220)
                        )
                    }
                },
                onDragEnd = {
                    val velocityX = velocityTracker.calculateVelocity().x
                    velocityTracker.resetTracking()
                    val shouldDismiss = velocityX > 900f ||
                        fullscreenSidePanelWidthPx.value < fullscreenSidePanelTargetWidthPx * 0.62f
                    if (shouldDismiss) {
                        closeFullscreenSidePanel()
                    } else {
                        scope.launch {
                            fullscreenSidePanelWidthPx.animateTo(
                                targetValue = fullscreenSidePanelTargetWidthPx,
                                animationSpec = tween(durationMillis = 220)
                            )
                        }
                    }
                }
            )
        }
        val fullscreenSidePanelWidth = with(density) { fullscreenSidePanelWidthPx.value.toDp() }
        val fullscreenSidePanelHeight = maxHeight
        val fullscreenPlayerWidth = (maxWidth - fullscreenSidePanelWidth).coerceAtLeast(maxWidth * 0.6f)

        DraggablePlayerLayout(
                state = playerSheetState,
                progress = progress,
                isFullscreen = screenState.isFullscreen,
                thumbnailUrl = video.thumbnailUrl.takeIf { it.isNotEmpty() }
                    ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg",
                videoAspectRatio = effectiveVideoAspectRatio,
                bottomPadding = bottomPadding,
                miniPlayerScale = miniPlayerScale,
                tapToExpand = true,
                onDismiss = onClose,
                onCollapseGesture = {
                    screenState.isFullscreen = false
                    screenState.dismissMediaSheets()
                },
                onFullscreenGesture = {
                    screenState.dismissMediaSheets()
                    screenState.isFullscreen = true
                },
                onExpandedPlayerBottomChanged = { bottom ->
                    expandedPlayerBottom = bottom
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(fullscreenPlayerWidth)
                    .fillMaxHeight(),
                videoContent = { modifier ->
                    // ALWAYS use the same video surface
                    val gestureModifier = if (!isMinimized && !localIsInPipMode && !screenState.isTouchLocked) {
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
                            brightnessSwipeGesturesEnabled = brightnessSwipeGesturesEnabled,
                            volumeSwipeGesturesEnabled = volumeSwipeGesturesEnabled,
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
                                    screenState.showZoomIndicator = true
                                    screenState.zoomIndicatorSequence += 1
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
                        VideoPlayerSurface(
                            video = video,
                            resizeMode = screenState.resizeMode,
                            modifier = Modifier.fillMaxSize(),
                            onVideoAspectRatioChanged = { videoAspectRatio = it }
                        )
                        if (!isMinimized && !localIsInPipMode) {
                            Media3SubtitleOverlay(
                                enabled = screenState.subtitlesEnabled,
                                isAutoGenerated = playerState.availableSubtitles
                                    .firstOrNull { it.url == screenState.selectedSubtitleUrl }
                                    ?.isAutoGenerated == true,
                                style = screenState.subtitleStyle,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (playerUiState.isRestoredSession) {
                            val thumbUrl = video.thumbnailUrl.takeIf { it.isNotEmpty() }
                                ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg"
                            coil.compose.AsyncImage(
                                model = thumbUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        } // end zoomable layer
                        
                        // Non-zoomable UI overlays (always at full-screen position)
                        if (!isMinimized && !localIsInPipMode) {
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
                                    .padding(end = 44.dp)
                            )
                            
                            // Volume overlay
                            VolumeOverlay(
                                isVisible = screenState.showVolumeOverlay,
                                volumeLevel = screenState.volumeLevel,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 44.dp)
                            )
                            
                               // 2x Speed overlay  
                            SpeedBoostOverlay(
                                isVisible = screenState.isSpeedBoostActive,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 0.dp)
                            )

                            AnimatedVisibility(
                                visible = screenState.showZoomIndicator,
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = if (screenState.isFullscreen) 28.dp else 16.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                    shape = RoundedCornerShape(999.dp),
                                    tonalElevation = 3.dp,
                                    shadowElevation = 2.dp
                                ) {
                                    Text(
                                        text = String.format(Locale.US, "%.1fx", screenState.zoomScale),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

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

                            if (playerUiState.isUpcoming) {
                                UpcomingVideoOverlay(
                                    title = video.title,
                                    releaseTimeMs = playerUiState.upcomingReleaseTimeMs,
                                    isReminderSet = playerUiState.isUpcomingReminderSet,
                                    onToggleReminder = playerViewModel::toggleUpcomingReminder,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }

                            // ── Error overlay — icon + title only; details/actions in body panel ──
                            val errorMsg  = playerUiState.error
                            if (errorMsg != null && !playerUiState.isUpcoming) {
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
                        if (!playerUiState.isUpcoming && !isMinimized && !localIsInPipMode && (screenState.showControls || screenState.isTouchLocked || !screenState.isFullscreen)) {
                            PremiumControlsOverlay(
                                isVisible = screenState.showControls || screenState.isTouchLocked,
                                isPlaying = playerState.playWhenReady,
                                hasEnded = playerState.hasEnded,
                                isBuffering = playerState.isBuffering,
                                currentPosition = screenState.currentPosition,
                                duration = screenState.duration,
                                qualityLabel = if (playerState.currentQuality == 0) 
                                    context.getString(R.string.quality_auto_template, playerState.effectiveQuality) 
                                else 
                                    playerState.currentQuality.toString(),
                                videoTitle = playerUiState.streamInfo?.name ?: video.title,
                                playbackSpeed = playerState.playbackSpeed,
                                resizeMode = screenState.resizeMode,
                                onResizeClick = { 
                                    screenState.onInteraction()
                                    screenState.cycleResizeMode() 
                                },
                                onPlayPause = {
                                    screenState.onInteraction()
                                    if (playerState.hasEnded) {
                                        EnhancedPlayerManager.getInstance().replay()
                                        playerViewModel.ensureNotificationServiceRunning()
                                    } else if (playerState.playWhenReady) {
                                        EnhancedPlayerManager.getInstance().pause()
                                    } else {
                                        EnhancedPlayerManager.getInstance().play()
                                        playerViewModel.ensureNotificationServiceRunning()
                                    }
                                },
                                onSeek = { newPosition ->
                                    screenState.onInteraction()
                                    val manager = EnhancedPlayerManager.getInstance()
                                    if (playerState.isLive) {
                                        manager.seekToLiveTimeline(newPosition)
                                    } else {
                                        manager.seekTo(newPosition)
                                    }
                                },
                                onBack = { playerSheetState.collapse() },
                                onSettingsClick = { screenState.showSettingsMenu = true },
                                onQualityClick = { screenState.showQualitySelector = true },
                                onSpeedClick = { screenState.showPlaybackSpeedSelector = true },
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
                                        EnhancedPlayerManager.getInstance().selectSubtitle(null)
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
                                            val index = playerState.availableSubtitles.indexOfFirst { it.url == screenState.selectedSubtitleUrl }
                                            if (index >= 0) {
                                                EnhancedPlayerManager.getInstance().selectSubtitle(index)
                                                screenState.subtitlesEnabled = true
                                            } else {
                                                screenState.showSubtitleSelector = true
                                            }
                                        }
                                    }
                                },
                                isSubtitlesEnabled = screenState.subtitlesEnabled,
                                autoplayEnabled = playerUiState.autoplayEnabled,
                                isLooping = playerState.isLooping,
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
                                onLiveClick = {
                                    EnhancedPlayerManager.getInstance().seekToLiveEdge(resetSpeed = true)
                                },
                                onSleepTimerClick = { screenState.showSleepTimerSheet = true },
                                isSleepTimerActive = io.github.aedev.flow.player.SleepTimerManager.isActive,
                                showRemainingTime = showRemainingTime,
                                onToggleRemainingTime = { showRemainingTime = !showRemainingTime },
                                isTouchLocked = screenState.isTouchLocked,
                                lockModeEnabled = lockModeEnabled,
                                onTouchLockToggle = {
                                    if (lockModeEnabled || screenState.isTouchLocked) {
                                        screenState.isTouchLocked = !screenState.isTouchLocked
                                        screenState.showControls = true
                                        screenState.onInteraction()
                                    }
                                }
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
                            } else if (playerState.hasEnded) {
                                EnhancedPlayerManager.getInstance().replay()
                                playerViewModel.ensureNotificationServiceRunning()
                            } else if (playerState.playWhenReady) {
                                EnhancedPlayerManager.getInstance().pause()
                            } else {
                                EnhancedPlayerManager.getInstance().play()
                                playerViewModel.ensureNotificationServiceRunning()
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

        if (fullscreenSidePanelWidth > 1.dp) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(fullscreenSidePanelWidth)
                    .fillMaxHeight()
                    .then(fullscreenSidePanelDragModifier)
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (showSettingsSurface) {
                    SettingsMenuDialog(
                        playerState = playerState,
                        autoplayEnabled = playerUiState.autoplayEnabled,
                        subtitlesEnabled = screenState.subtitlesEnabled,
                        initialPage = settingsInitialPage,
                        onDismiss = {
                            screenState.showSettingsMenu = false
                            screenState.showQualitySelector = false
                            screenState.showAudioTrackSelector = false
                            screenState.showPlaybackSpeedSelector = false
                            screenState.showSubtitleSelector = false
                        },
                        onQualitySelected = { height ->
                            EnhancedPlayerManager.getInstance().switchQuality(height)
                        },
                        onAudioTrackSelected = { index ->
                            EnhancedPlayerManager.getInstance().switchAudioTrack(index)
                        },
                        onSpeedSelected = { speed ->
                            EnhancedPlayerManager.getInstance().setPlaybackSpeed(speed)
                            screenState.normalSpeed = speed
                            if (rememberPlaybackSpeed) {
                                scope.launch { playerPreferences.setPlaybackSpeed(speed) }
                            }
                        },
                        selectedSubtitleUrl = screenState.selectedSubtitleUrl,
                        onSubtitleSelected = { index, url ->
                            screenState.selectedSubtitleUrl = url
                            EnhancedPlayerManager.getInstance().selectSubtitle(index)
                            screenState.subtitlesEnabled = true
                        },
                        onDisableSubtitles = {
                            EnhancedPlayerManager.getInstance().selectSubtitle(null)
                            screenState.disableSubtitles()
                        },
                        onAutoplayToggle = { playerViewModel.toggleAutoplay(it) },
                        onSkipSilenceToggle = { playerViewModel.toggleSkipSilence(it) },
                        onStableVolumeToggle = { playerViewModel.toggleStableVolume(it) },
                        onShowSubtitleStyle = {
                            screenState.showSettingsMenu = false
                            screenState.showSubtitleStyleCustomizer = true
                        },
                        onLoopToggle = { playerViewModel.toggleLoop(it) },
                        onCastClick = {
                            DlnaCastManager.startDiscovery(context)
                            screenState.showDlnaDialog = true
                        },
                        onPipClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                PictureInPictureHelper.isPipSupported(context)
                            ) {
                                PictureInPictureHelper.enterPipMode(
                                    activity = activity,
                                    isPlaying = playerState.isPlaying
                                )
                            }
                        },
                        onSleepTimerClick = {
                            screenState.showSleepTimerSheet = true
                        },
                        expandedHeight = fullscreenSidePanelHeight,
                        enableVerticalDismiss = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (screenState.showChaptersSheet) {
                    FlowChaptersBottomSheet(
                        chapters = playerUiState.chapters,
                        currentPosition = screenState.currentPosition,
                        durationMs = screenState.duration,
                        onChapterClick = { newPosition ->
                            EnhancedPlayerManager.getInstance().seekTo(newPosition)
                        },
                        thumbnailUrl = video.thumbnailUrl,
                        expandedHeight = fullscreenSidePanelHeight,
                        enableVerticalDismiss = false,
                        modifier = Modifier.fillMaxSize(),
                        onDismiss = { screenState.showChaptersSheet = false }
                    )
                }
            }
        }
        
        // Dialogs
        PlayerDialogsContainer(
            screenState = screenState,
            playerState = playerState,
            uiState = playerUiState,
            video = completeVideo,
            viewModel = playerViewModel,
            renderSettingsMenu = !canUseFullscreenSidePanel
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
                    val streamInfo = playerUiState.streamInfo

                    if (streamInfo != null) {
                        val duration = streamInfo.duration

                        val videoVariants = (streamInfo.videoOnlyStreams ?: emptyList())
                            .filter { it.height > 0 }
                            .filter {
                                val mime = it.format?.mimeType ?: ""
                                mime.contains("mp4") || mime.contains("avc")
                            }
                            .sortedByDescending { it.height }
                            .map { stream ->
                                io.github.aedev.flow.player.dlna.CastStreamVariant(
                                    url = stream.content ?: stream.url ?: "",
                                    width = stream.width.takeIf { it > 0 } ?: (stream.height * 16 / 9),
                                    height = stream.height,
                                    bitrate = stream.bitrate.takeIf { it > 0 } ?: 2_500_000,
                                    mime = "video/mp4",
                                    codec = stream.codec?.takeIf { it.isNotBlank() } ?: "avc1.64001F"
                                )
                            }
                            .filter { it.url.isNotEmpty() }

                        val bestAudio = streamInfo.audioStreams
                            ?.filter {
                                val mime = it.format?.mimeType ?: ""
                                mime.contains("mp4") || mime.contains("m4a") || mime.contains("aac")
                            }
                            ?.maxByOrNull { it.bitrate }

                        val audioUrl = bestAudio?.let { it.content ?: it.url }
                        val audioBitrate = bestAudio?.bitrate?.takeIf { it > 0 } ?: 128_000
                        val audioCodec = bestAudio?.codec?.takeIf { it?.isNotBlank() == true } ?: "mp4a.40.2"
                        val audioMime = bestAudio?.format?.mimeType?.let {
                            if (it.contains("mp4") || it.contains("m4a")) "audio/mp4" else it
                        } ?: "audio/mp4"

                        if (videoVariants.isNotEmpty() && audioUrl != null) {
                            android.util.Log.d("DlnaCast", "HLS cast: ${videoVariants.size} variants, " +
                                "audio=${audioBitrate/1000}kbps")

                            DlnaCastManager.castTo(
                                device = device,
                                title = video.title,
                                videoVariants = videoVariants,
                                audioUrl = audioUrl,
                                audioMime = audioMime,
                                audioBitrate = audioBitrate,
                                audioCodec = audioCodec,
                                durationSeconds = duration
                            )
                        } else {
                            val bestMuxed = streamInfo.videoStreams
                                ?.filter { it.height > 0 }
                                ?.maxByOrNull { it.height }
                            val muxedUrl = bestMuxed?.let { it.content ?: it.url }
                                ?: EnhancedPlayerManager.getInstance().getPlayer()
                                    ?.currentMediaItem?.localConfiguration?.uri?.toString()

                            if (muxedUrl != null && muxedUrl.isNotEmpty() && !muxedUrl.startsWith("local://")) {
                                android.util.Log.d("DlnaCast", "Fallback to pre-muxed: ${bestMuxed?.height}p")
                                DlnaCastManager.castTo(
                                    device = device,
                                    title = video.title,
                                    fallbackVideoUrl = muxedUrl
                                )
                            }
                        }
                    } else {
                        val playerUrl = EnhancedPlayerManager.getInstance().getPlayer()
                            ?.currentMediaItem?.localConfiguration?.uri?.toString()
                        if (playerUrl != null && playerUrl.isNotEmpty() && !playerUrl.startsWith("local://")) {
                            DlnaCastManager.castTo(
                                device = device,
                                title = video.title,
                                fallbackVideoUrl = playerUrl
                            )
                        }
                    }
                    showDlnaDialog = false
                },
                onStopCasting = {
                    DlnaCastManager.disconnect()
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
            disableShortsPlayer = disableShortsPlayer,
            comments = comments,
            commentsEnabled = commentsEnabled,
            isLoadingComments = isLoadingComments,
            isLoadingMoreComments = isLoadingMoreComments,
            hasMoreComments = hasMoreComments,
            onLoadMoreComments = { videoId -> playerViewModel.loadMoreComments(videoId) },
            mediaSheetExpandedHeight = mediaSheetExpandedHeight,
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
            onLoadMoreReplies = { comment ->
                playerViewModel.loadMoreCommentReplies(comment)
            },
            onNavigateToChannel = { channelId ->
                onNavigateToChannel(channelId)
            },
            renderChaptersSheet = !canUseFullscreenSidePanel
        )
    }
}

@Composable
private fun UpcomingVideoOverlay(
    title: String,
    releaseTimeMs: Long?,
    isReminderSet: Boolean,
    onToggleReminder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var nowMs by remember(releaseTimeMs) { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(releaseTimeMs) {
        if (releaseTimeMs == null) return@LaunchedEffect
        while (true) {
            nowMs = System.currentTimeMillis()
            delay(1000)
        }
    }

    Surface(
        modifier = modifier
            .padding(horizontal = 24.dp)
            .widthIn(max = 420.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.78f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Text(
                text = stringResource(R.string.upcoming_video_overlay_title),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
            Text(
                text = releaseTimeMs?.let { formatCountdown(it - nowMs) }
                    ?: stringResource(R.string.premiere_soon),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            if (releaseTimeMs != null) {
                FilledTonalButton(onClick = onToggleReminder) {
                    Icon(
                        imageVector = if (isReminderSet) Icons.Rounded.NotificationsActive else Icons.Rounded.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            if (isReminderSet) R.string.upcoming_video_reminder_enabled
                            else R.string.upcoming_video_reminder_action
                        )
                    )
                }
            }
        }
    }
}

private fun formatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0L) return "00:00"
    val totalSeconds = remainingMs / 1000L
    val days = totalSeconds / 86_400L
    val hours = (totalSeconds % 86_400L) / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        days > 0L -> String.format(Locale.US, "%dd %02dh %02dm", days, hours, minutes)
        hours > 0L -> String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        else -> String.format(Locale.US, "%02d:%02d", minutes, seconds)
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

    val baseTouchSize = if (isTablet) 44.dp else 36.dp
    val baseBgSize  = if (isTablet) 34.dp else 24.dp
    val baseIconSize = if (isTablet) 30.dp else 24.dp
    val finalTouchSize = baseTouchSize * scaleMult
    val finalBgSize   = baseBgSize   * scaleMult
    val finalIconSize = baseIconSize * scaleMult
    val topTouchSize = if (isTablet) 50.dp else 42.dp
    val topBgSize = if (isTablet) 42.dp else 34.dp

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        IconButton(
            onClick = onPlayPause,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .size(topTouchSize)
        ) {
            MiniPlayerButtonBackground(
                backgroundSize = topBgSize,
                backgroundAlpha = 0.28f
            ) {
                if (playerState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(if (isTablet) 30.dp else 24.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        imageVector = when {
                            playerState.hasEnded -> Icons.Rounded.Replay
                            playerState.playWhenReady -> Icons.Rounded.Pause
                            else -> Icons.Rounded.PlayArrow
                        },
                        contentDescription = when {
                            playerState.hasEnded -> "Replay"
                            playerState.playWhenReady -> "Pause"
                            else -> "Play"
                        },
                        tint = Color.White,
                        modifier = Modifier.size(if (isTablet) 42.dp else 34.dp)
                    )
                }
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
                .size(topTouchSize)
        ) {
            MiniPlayerButtonBackground(
                backgroundSize = topBgSize,
                backgroundAlpha = 0.28f
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(if (isTablet) 34.dp else 30.dp)
                )
            }
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
                    MiniPlayerIconButton(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        touchSize = finalTouchSize,
                        backgroundSize = finalBgSize,
                        iconSize = finalIconSize,
                        onClick = onPrevious
                    )
                }

                if (showSkipControls) {
                    MiniPlayerIconButton(
                        imageVector = Icons.Rounded.Replay10,
                        contentDescription = "Skip Back 10s",
                        touchSize = finalTouchSize,
                        backgroundSize = finalBgSize,
                        iconSize = finalIconSize,
                        onClick = onSkipBack
                    )
                }

                if (showSkipControls) {
                    MiniPlayerIconButton(
                        imageVector = Icons.Rounded.Forward10,
                        contentDescription = "Skip Forward 10s",
                        touchSize = finalTouchSize,
                        backgroundSize = finalBgSize,
                        iconSize = finalIconSize,
                        onClick = onSkipForward
                    )
                }

                if (showNextPrevControls) {
                    MiniPlayerIconButton(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        touchSize = finalTouchSize,
                        backgroundSize = finalBgSize,
                        iconSize = finalIconSize,
                        onClick = onNext
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerIconButton(
    imageVector: ImageVector,
    contentDescription: String,
    touchSize: androidx.compose.ui.unit.Dp,
    backgroundSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(touchSize)
    ) {
        MiniPlayerButtonBackground(backgroundSize = backgroundSize) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
private fun MiniPlayerButtonBackground(
    backgroundSize: androidx.compose.ui.unit.Dp,
    backgroundAlpha: Float = 0.36f,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .size(backgroundSize)
            .background(Color.Black.copy(alpha = backgroundAlpha), CircleShape),
        contentAlignment = Alignment.Center,
        content = content
    )
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
