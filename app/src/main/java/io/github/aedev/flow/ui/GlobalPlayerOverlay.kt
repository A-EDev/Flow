package io.github.aedev.flow.ui

import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.res.stringResource
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.PlayerHardwareController
import io.github.aedev.flow.ui.components.DraggablePlayerLayout
import io.github.aedev.flow.ui.components.PlayerDraggableState
import io.github.aedev.flow.ui.components.rememberPlayerDraggableState
import io.github.aedev.flow.ui.components.PlayerSheetValue
import io.github.aedev.flow.ui.screens.player.EnhancedVideoPlayerScreen
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.components.VideoPlayerSurface
import io.github.aedev.flow.ui.components.FlowChaptersBottomSheet
import io.github.aedev.flow.ui.components.CommentSortFilterChips
import io.github.aedev.flow.ui.components.FlowCommentsList
import io.github.aedev.flow.ui.components.commentTimestampToMs
import io.github.aedev.flow.ui.components.sortCommentsByFilter
import io.github.aedev.flow.ui.components.Media3SubtitleOverlay
import io.github.aedev.flow.ui.components.SleepTimerSheet
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
import io.github.aedev.flow.ui.screens.player.components.PlayerGestureOverlays
import io.github.aedev.flow.ui.screens.player.components.SponsorBlockSkipButton
import io.github.aedev.flow.ui.screens.player.components.SettingsMenuDialog
import io.github.aedev.flow.ui.screens.player.components.PlayerSettingsPage
import io.github.aedev.flow.ui.screens.player.components.LockModeTouchShield
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.player.PictureInPictureHelper
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.draw.clip
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
    val rememberBrightnessEnabled by playerPreferences.rememberBrightnessEnabled.collectAsState(initial = false)
    val rememberedBrightnessLevel by playerPreferences.rememberedBrightnessLevel.collectAsState(initial = -1f)
    val volumeSwipeGesturesEnabled by playerPreferences.volumeSwipeGesturesEnabled.collectAsState(initial = true)
    val allowVolumeBoost by playerPreferences.allowVolumeBoost.collectAsState(initial = false)
    val sbSubmitEnabled by playerPreferences.sbSubmitEnabled.collectAsState(initial = false)
    val doubleTapSeekSeconds by playerPreferences.doubleTapSeekSeconds.collectAsState(initial = 10)
    val longPressPlaybackSpeed by playerPreferences.longPressPlaybackSpeed.collectAsState(initial = 2.0f)
    val disableShortsPlayer by playerPreferences.disableShortsPlayer.collectAsState(initial = false)
    val showShortsPlayerPrompt by playerPreferences.showShortsPlayerPrompt.collectAsState(initial = true)
    val savedSubtitleStyle by playerPreferences.subtitleStyle.collectAsState(initial = SubtitleStyle())
    val rememberPlaybackSpeed by playerPreferences.rememberPlaybackSpeed.collectAsState(initial = false)
    val ambientModeEnabled by playerPreferences.videoAmbientModeEnabled.collectAsState(initial = false)
    val adaptivePlayerSizeEnabled by playerPreferences.adaptivePlayerSizeEnabled.collectAsState(initial = true)
    val groupedQualitySelectorEnabled by playerPreferences.groupedQualitySelectorEnabled.collectAsState(initial = false)
    val lockModeEnabled by playerPreferences.overlayLockModeEnabled.collectAsState(initial = false)
    val commentsEnabled by playerPreferences.commentsEnabled.collectAsState(initial = true)
    val isCommentsAvailable = commentsEnabled && playerUiState.hlsUrl.isNullOrEmpty()
    val sortedComments = remember(comments, screenState.commentSortFilter) {
        sortCommentsByFilter(comments, screenState.commentSortFilter)
    }

    LaunchedEffect(allowVolumeBoost) {
        if (!allowVolumeBoost && screenState.volumeLevel > 1f) {
            screenState.volumeLevel = 1f
            EnhancedPlayerManager.getInstance().setVolumeBoost(1f)
        }
    }

    val syncVolumeFromSystem: () -> Unit = {
        val max = audioSystemInfo.maxVolume
        if (max > 0) {
            val systemVolume = audioSystemInfo.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            screenState.volumeLevel = (systemVolume.toFloat() / max).coerceIn(0f, 1f)
            EnhancedPlayerManager.getInstance().setVolumeBoost(1f)
        }
    }

    LaunchedEffect(video.id) {
        syncVolumeFromSystem()
    }

    LaunchedEffect(screenState.isFullscreen, volumeSwipeGesturesEnabled) {
        PlayerHardwareController.setFullscreenVideoActive(screenState.isFullscreen)
        PlayerHardwareController.setInAppVolumeOverlayEnabled(volumeSwipeGesturesEnabled)
    }
    DisposableEffect(Unit) {
        onDispose {
            PlayerHardwareController.setFullscreenVideoActive(false)
            PlayerHardwareController.setInAppVolumeOverlayEnabled(true)
        }
    }

    val volumeKeySignal by PlayerHardwareController.volumeKeySignal.collectAsState()
    LaunchedEffect(volumeKeySignal) {
        if (volumeKeySignal > 0L) {
            syncVolumeFromSystem()
            screenState.showVolumeOverlay = true
        }
    }

    var videoAspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var mediaSheetProgress by remember { mutableFloatStateOf(0f) }
    var lockedMediaSheetCollapsedHeight by remember { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }
    val progressDrivenMediaSheetVisible =
        screenState.showCommentsSheet ||
        screenState.showDescriptionSheet ||
        screenState.showChaptersSheet ||
        screenState.showPlaylistQueueSheet ||
        screenState.showLiveChatSheet ||
        screenState.showSleepTimerSheet ||
        screenState.showSettingsMenu ||
        screenState.showQualitySelector ||
        screenState.showAudioTrackSelector ||
        screenState.showPlaybackSpeedSelector ||
        screenState.showSubtitleSelector
    val progressDrivenMediaSheetResize =
        adaptivePlayerSizeEnabled &&
            !screenState.isFullscreen &&
            progressDrivenMediaSheetVisible &&
            videoAspectRatio < 1f
    LaunchedEffect(progressDrivenMediaSheetVisible) {
        if (!progressDrivenMediaSheetVisible) {
            if (mediaSheetProgress != 0f) {
                mediaSheetProgress = 0f
            }
            lockedMediaSheetCollapsedHeight = null
        }
    }
    val updateMediaSheetProgress: (Float) -> Unit = { progress ->
        val nextProgress = progress.coerceIn(0f, 1f)
        if (kotlin.math.abs(mediaSheetProgress - nextProgress) > 0.001f) {
            mediaSheetProgress = nextProgress
        }
    }
    val sheetPlayerHeightFractionOverride = if (progressDrivenMediaSheetResize) {
        1f - mediaSheetProgress.coerceIn(0f, 1f)
    } else {
        null
    }
    val effectiveVideoAspectRatio = when {
        adaptivePlayerSizeEnabled || screenState.isFullscreen -> videoAspectRatio
        else -> 16f / 9f
    }
    var expandedPlayerBottom by remember { mutableStateOf(0.dp) }
    var pipForcedFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(video.id) {
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

    LaunchedEffect(rememberBrightnessEnabled, rememberedBrightnessLevel) {
        if (rememberBrightnessEnabled) {
            screenState.brightnessLevel = if (rememberedBrightnessLevel < 0f) {
                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            } else {
                rememberedBrightnessLevel.coerceIn(0f, 1f)
            }
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
    
    val localIsInPipMode by GlobalPlayerState.isInPipMode.collectAsState()
    var keepMiniOnQueueAutoAdvance by remember { mutableStateOf(false) }
    
    val progressProvider = remember {
        {
            if (screenState.duration > 0) {
                (screenState.currentPosition.toFloat() / screenState.duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
        }
    }
    
    // Sync fullscreen state with player sheet state
    LaunchedEffect(playerSheetState.currentValue) {
        if (playerSheetState.currentValue == PlayerSheetValue.Collapsed) {
            screenState.isFullscreen = false
            screenState.isFullscreenPortrait = false
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
    val windowInsetDensity = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val sponsorSkipEndPadding = with(windowInsetDensity) {
        maxOf(
            WindowInsets.displayCutout.getRight(this, layoutDirection),
            WindowInsets.systemBars.getRight(this, layoutDirection)
        ).toDp() + 16.dp
    }
    val sponsorSkipBottomInset = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val updateBrightnessLevel: (Float) -> Unit = { brightnessLevel ->
        screenState.brightnessLevel = brightnessLevel
        if (rememberBrightnessEnabled) {
            scope.launch {
                playerPreferences.setRememberedBrightnessLevel(brightnessLevel)
            }
        }
    }

    LaunchedEffect(isLandscape, isTablet, localIsInPipMode) {
        if (isLandscape && !isTablet && !localIsInPipMode && playerSheetState.currentValue == PlayerSheetValue.Expanded) {
            // Automatically enter fullscreen on phones when rotated to landscape
            screenState.isFullscreen = true
        }
    }

    // Handle Back press in Fullscreen
    BackHandler(enabled = screenState.isFullscreen) {
        screenState.isFullscreen = false
        screenState.isFullscreenPortrait = false
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

    BackHandler(
        enabled = playerSheetState.currentValue == PlayerSheetValue.Expanded &&
            !localIsInPipMode &&
            !screenState.isFullscreen
    ) {
        playerSheetState.collapse()
    }

    BackHandler(enabled = screenState.isTouchLocked && !localIsInPipMode) {
        screenState.revealLockOverlay()
        screenState.onInteraction()
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
            GlobalPlayerState.setPipMode(inPipMode)
        }
    )

    FullscreenEffect(
        isFullscreen = screenState.isFullscreen,
        activity = activity,
        videoAspectRatio = effectiveVideoAspectRatio,
        lifecycleOwner = lifecycleOwner,
        fullscreenBrightnessLevel = if (rememberBrightnessEnabled) screenState.brightnessLevel else null,
        suppressFullscreenRequest = pipForcedFullscreen,
        isPortrait = screenState.isFullscreenPortrait
    )
    
    OrientationResetEffect(activity)
    
    WatchProgressSaveEffect(
        videoId = video.id,
        video = video,
        isPlaying = playerState.playWhenReady,
        currentPosition = { screenState.currentPosition },
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

        LaunchedEffect(
            video.id,
            playerUiState.isLoading,
            playerUiState.error,
            playerUiState.streamInfo,
            playerUiState.audioStream,
            playerUiState.localFilePath
        ) {
            playerViewModel.ensurePlaybackPrepared(video.id)
        }

        PlaybackStartupRecoveryEffect(
            videoId = video.id,
            uiState = playerUiState,
            screenState = screenState,
            viewModel = playerViewModel
        )
    }
    
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
        disableShortsPlayer = disableShortsPlayer,
        showShortsPlayerPrompt = showShortsPlayerPrompt
    )

    SponsorSkipEffect(context)
    
    OrientationListenerEffect(
        context = context,
        isExpanded = playerSheetState.currentValue == PlayerSheetValue.Expanded,
        isFullscreen = screenState.isFullscreen,
        videoAspectRatio = effectiveVideoAspectRatio,
        isPortraitFullscreen = screenState.isFullscreenPortrait,
        onEnterFullscreen = { screenState.isFullscreen = true },
        onExitFullscreen = {
            screenState.isFullscreen = false
            screenState.isFullscreenPortrait = false
        }
    )
    
    KeepScreenOnEffect(
        isPlaying = playerState.playWhenReady && !playerState.hasEnded,
        activity = activity,
        lifecycleOwner = lifecycleOwner
    )

    LaunchedEffect(video.id, playerUiState.isUpcoming, playerUiState.upcomingReleaseTimeMs) {
        val releaseMs = playerUiState.upcomingReleaseTimeMs
        if (!playerUiState.isUpcoming || releaseMs == null) return@LaunchedEffect
        val waitMs = (releaseMs - System.currentTimeMillis()).coerceAtLeast(0L)
        delay(waitMs + 3_000L)
        var attempts = 0
        while (playerViewModel.uiState.value.isUpcoming && attempts < 20) {
            playerViewModel.loadVideoInfo(video.id, forceRefresh = true)
            attempts++
            delay(30_000L)
        }
    }

    LaunchedEffect(playerState.hasEnded, playerSheetState.currentValue, localIsInPipMode) {
        if (
            playerState.hasEnded &&
            playerSheetState.currentValue == PlayerSheetValue.Expanded &&
            !localIsInPipMode
        ) {
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
                    channelId = channelId,
                    isShort = video.isShort
                )
            }
        }
    }
    
    // ===== UI =====
    val isMinimized by remember(playerSheetState) {
        derivedStateOf { playerSheetState.fraction > 0.5f }
    }
    val density = LocalDensity.current
    val floatingSponsorSkipBottomPadding = if (screenState.isFullscreen) {
        maxOf(sponsorSkipBottomInset + 128.dp, 136.dp)
    } else {
        56.dp
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val fullScreenHeight = constraints.maxHeight.toFloat()
        val rawMediaSheetCollapsedHeight = with(density) {
            val availablePx = fullScreenHeight - expandedPlayerBottom.toPx()
            if (expandedPlayerBottom > 0.dp && availablePx > 0f) {
                availablePx.toDp()
            } else {
                0.dp
            }
        }
        val defaultMediaSheetExpandedHeight = with(density) {
            val availablePx = fullScreenHeight - expandedPlayerBottom.toPx()
            if (expandedPlayerBottom > 0.dp && availablePx > 0f) {
                availablePx.toDp()
            } else {
                config.screenHeightDp.dp * 0.75f
            }
        }
        val sixteenNineMediaSheetExpandedHeight = with(density) {
            val statusBarHeightPx = WindowInsets.statusBars.getTop(this).toFloat()
            val sixteenNinePlayerBottomPx = statusBarHeightPx + constraints.maxWidth.toFloat() * 9f / 16f
            (fullScreenHeight - sixteenNinePlayerBottomPx).coerceAtLeast(0f).toDp()
        }
        LaunchedEffect(progressDrivenMediaSheetResize, rawMediaSheetCollapsedHeight, maxWidth, maxHeight) {
            if (
                progressDrivenMediaSheetResize &&
                lockedMediaSheetCollapsedHeight == null &&
                expandedPlayerBottom > 0.dp
            ) {
                lockedMediaSheetCollapsedHeight = rawMediaSheetCollapsedHeight
            } else if (!progressDrivenMediaSheetResize) {
                lockedMediaSheetCollapsedHeight = null
            }
        }
        val mediaSheetCollapsedHeight = if (progressDrivenMediaSheetResize) {
            lockedMediaSheetCollapsedHeight ?: rawMediaSheetCollapsedHeight
        } else {
            0.dp
        }
        val mediaSheetExpandedHeight = if (progressDrivenMediaSheetResize) {
            maxOf(mediaSheetCollapsedHeight, sixteenNineMediaSheetExpandedHeight)
        } else {
            defaultMediaSheetExpandedHeight
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
        val showLiveChatSidePanel = screenState.showLiveChatFullscreen && playerUiState.isLiveChatAvailable
        val showCommentsSidePanel = screenState.showCommentsFullscreen && commentsEnabled
        val showSleepTimerSidePanel = screenState.showSleepTimerSheet
        val fullscreenSidePanelVisible = canUseFullscreenSidePanel &&
            (showSettingsSurface || screenState.showChaptersSheet || showLiveChatSidePanel || showCommentsSidePanel || showSleepTimerSidePanel)
        val fullscreenDrawerWidth = minOf(maxWidth * 0.42f, 420.dp)
        val fullscreenDrawerWidthPx = with(density) { fullscreenDrawerWidth.toPx() }
        val fullscreenDrawerOffsetPx = remember { Animatable(0f) }

        LaunchedEffect(fullscreenSidePanelVisible, fullscreenDrawerWidthPx) {
            fullscreenDrawerOffsetPx.updateBounds(
                lowerBound = 0f,
                upperBound = fullscreenDrawerWidthPx
            )
            if (fullscreenSidePanelVisible) {
                if (fullscreenDrawerOffsetPx.value == 0f) {
                    fullscreenDrawerOffsetPx.snapTo(fullscreenDrawerWidthPx)
                }
                fullscreenDrawerOffsetPx.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 260)
                )
            }
        }

        fun closeFullscreenSidePanel() {
            scope.launch {
                fullscreenDrawerOffsetPx.animateTo(
                    targetValue = fullscreenDrawerWidthPx,
                    animationSpec = tween(durationMillis = 220)
                )
                screenState.dismissMediaSheets()
                screenState.showSleepTimerSheet = false
            }
        }
        val fullscreenSidePanelDragModifier = Modifier.pointerInput(fullscreenDrawerWidthPx, fullscreenSidePanelVisible) {
            if (!fullscreenSidePanelVisible || fullscreenDrawerWidthPx <= 0f) return@pointerInput
            val velocityTracker = VelocityTracker()
            detectHorizontalDragGestures(
                onHorizontalDrag = { change, dragAmount ->
                    velocityTracker.addPointerInputChange(change)
                    change.consume()
                    scope.launch {
                        fullscreenDrawerOffsetPx.snapTo(
                            (fullscreenDrawerOffsetPx.value + dragAmount)
                                .coerceIn(0f, fullscreenDrawerWidthPx)
                        )
                    }
                },
                onDragCancel = {
                    velocityTracker.resetTracking()
                    scope.launch {
                        fullscreenDrawerOffsetPx.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 220)
                        )
                    }
                },
                onDragEnd = {
                    val velocityX = velocityTracker.calculateVelocity().x
                    velocityTracker.resetTracking()
                    val shouldDismiss = velocityX > 900f ||
                        fullscreenDrawerOffsetPx.value > fullscreenDrawerWidthPx * 0.38f
                    if (shouldDismiss) {
                        closeFullscreenSidePanel()
                    } else {
                        scope.launch {
                            fullscreenDrawerOffsetPx.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 220)
                            )
                        }
                    }
                }
            )
        }
        val fullscreenDrawerOffset = with(density) { fullscreenDrawerOffsetPx.value.toDp() }
        val fullscreenReservedWidth = if (fullscreenSidePanelVisible) {
            (fullscreenDrawerWidth - fullscreenDrawerOffset).coerceIn(0.dp, fullscreenDrawerWidth)
        } else {
            0.dp
        }
        val fullscreenSidePanelHeight = maxHeight
        val fullscreenPlayerWidth = if (fullscreenSidePanelVisible) {
            (maxWidth - fullscreenReservedWidth).coerceAtLeast(maxWidth * 0.58f)
        } else {
            maxWidth
        }

        DraggablePlayerLayout(
                state = playerSheetState,
                progress = progressProvider,
                isFullscreen = screenState.isFullscreen,
                thumbnailUrl = video.thumbnailUrl.takeIf { it.isNotEmpty() }
                    ?: "https://i.ytimg.com/vi/${video.id}/hq720.jpg",
                videoAspectRatio = effectiveVideoAspectRatio,
                expandedPlayerHeightFractionOverride = sheetPlayerHeightFractionOverride,
                bottomPadding = bottomPadding,
                miniPlayerScale = miniPlayerScale,
                tapToExpand = true,
                onDismiss = onClose,
                onCollapseGesture = {
                    screenState.isFullscreen = false
                    screenState.isFullscreenPortrait = false
                    screenState.dismissMediaSheets()
                },
                onFullscreenGesture = {
                    screenState.dismissMediaSheets()
                    screenState.isFullscreenPortrait = false
                    screenState.isFullscreen = true
                },
                onEnterPortraitFullscreen = {
                    screenState.dismissMediaSheets()
                    screenState.isFullscreenPortrait = true
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
                            currentPosition = { screenState.currentPosition },
                            duration = screenState.duration,
                            normalSpeed = screenState.normalSpeed,
                            scope = scope,
                            isFullscreen = screenState.isFullscreen,
                            onBrightnessChange = updateBrightnessLevel,
                            onShowBrightnessChange = { screenState.showBrightnessOverlay = it },
                            onVolumeChange = { level ->
                                screenState.volumeLevel = level
                                EnhancedPlayerManager.getInstance()
                                    .setVolumeBoost(if (level > 1f) level else 1f)
                            },
                            onShowVolumeChange = { screenState.showVolumeOverlay = it },
                            onBack = {
                                screenState.isFullscreen = false
                                screenState.isFullscreenPortrait = false
                                playerSheetState.collapse()
                            },
                            brightnessLevel = screenState.brightnessLevel,
                            volumeLevel = screenState.volumeLevel,
                            maxVolume = audioSystemInfo.maxVolume,
                            audioManager = audioSystemInfo.audioManager,
                            activity = activity,
                            brightnessSwipeGesturesEnabled = brightnessSwipeGesturesEnabled,
                            volumeSwipeGesturesEnabled = volumeSwipeGesturesEnabled,
                            allowVolumeBoost = allowVolumeBoost,
                            doubleTapSeekMs = doubleTapSeekSeconds * 1000L,
                            longPressPlaybackSpeed = longPressPlaybackSpeed,
                            onExitFullscreen = {
                                screenState.isFullscreen = false
                                screenState.isFullscreenPortrait = false
                            },
                            isSeekForwardActive = screenState.showSeekForwardAnimation,
                            isSeekBackActive = screenState.showSeekBackAnimation
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
                            onVideoAspectRatioChanged = { videoAspectRatio = it },
                            cornerRadiusDp = if (isMinimized && !localIsInPipMode) {
                                12f / playerSheetState.miniVisualScale
                            } else 0f,
                            ambientMode = ambientModeEnabled && !isMinimized && !localIsInPipMode
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
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(
                                        RoundedCornerShape(
                                            if (isMinimized && !localIsInPipMode) {
                                                (12f / playerSheetState.miniVisualScale).dp
                                            } else 0.dp
                                        )
                                    ),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        }
                        } // end zoomable layer
                        
                        // Non-zoomable UI overlays (always at full-screen position)
                        if (!isMinimized && !localIsInPipMode) {
                            PlayerGestureOverlays(
                                screenState = screenState,
                                allowVolumeBoost = allowVolumeBoost,
                                speedBoostSpeed = longPressPlaybackSpeed
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
                        if (!playerUiState.isUpcoming && !isMinimized && !localIsInPipMode) {
                            val controlsShown = screenState.showControls || screenState.isTouchLocked
                            val frozenPosition = remember { longArrayOf(0L) }
                            val frozenBuffered = remember { floatArrayOf(0f) }
                            if (controlsShown) {
                                frozenPosition[0] = screenState.currentPosition
                                frozenBuffered[0] = (if (screenState.duration > 0) {
                                    screenState.bufferedPosition.toFloat() / screenState.duration.toFloat()
                                } else 0f).coerceIn(0f, 1f)
                            }
                            PremiumControlsOverlay(
                                isVisible = controlsShown,
                                isPlaying = playerState.playWhenReady,
                                hasEnded = playerState.hasEnded,
                                isBuffering = playerState.isBuffering,
                                currentPosition = frozenPosition[0],
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
                                    io.github.aedev.flow.player.PictureInPictureHelper.isPlayerPopupSupported(context) &&
                                    pipPreferences.manualPipButtonEnabled,
                                onPipClick = {
                                    PictureInPictureHelper.requestPlayerPipMode(
                                        activity = activity,
                                        isPlaying = playerState.isPlaying
                                    )
                                },
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
                                bufferedPercentage = frozenBuffered[0],
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
                                isLiveChatAvailable = playerUiState.isLiveChatAvailable,
                                onLiveChatClick = {
                                    if (screenState.showLiveChatFullscreen) {
                                        screenState.showLiveChatFullscreen = false
                                    } else {
                                        screenState.dismissMediaSheets()
                                        screenState.showLiveChatFullscreen = true
                                    }
                                },
                                isCommentsAvailable = isCommentsAvailable,
                                isCommentsPanelOpen = screenState.showCommentsFullscreen,
                                onCommentsClick = {
                                    if (screenState.showCommentsFullscreen) {
                                        screenState.showCommentsFullscreen = false
                                    } else {
                                        screenState.dismissMediaSheets()
                                        screenState.showCommentsFullscreen = true
                                    }
                                },
                                onSleepTimerClick = { screenState.showSleepTimerSheet = true },
                                isSleepTimerActive = io.github.aedev.flow.player.SleepTimerManager.isActive,
                                showRemainingTime = showRemainingTime,
                                onToggleRemainingTime = { showRemainingTime = !showRemainingTime },
                                isTouchLocked = screenState.isTouchLocked,
                                lockModeEnabled = lockModeEnabled,
                                lockOverlayRevealSignal = screenState.lockOverlayRevealSignal,
                                isPortraitFullscreen = screenState.isFullscreenPortrait,
                                onTouchLockToggle = {
                                    if (lockModeEnabled || screenState.isTouchLocked) {
                                        screenState.isTouchLocked = !screenState.isTouchLocked
                                        screenState.showControls = true
                                        if (screenState.isTouchLocked) {
                                            screenState.revealLockOverlay()
                                        }
                                        screenState.onInteraction()
                                    }
                                }
                            )
                        }
                    }
                },
            bodyContent = { alpha, videoHeight ->
                Box(Modifier.fillMaxSize()) {
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

                    if (screenState.isTouchLocked && !screenState.isFullscreen && !localIsInPipMode && !isLandscape) {
                        LockModeTouchShield(
                            onRevealUnlock = {
                                screenState.revealLockOverlay()
                                screenState.onInteraction()
                            },
                            onUnlock = {
                                screenState.isTouchLocked = false
                                screenState.showControls = true
                                screenState.onInteraction()
                            },
                            modifier = Modifier
                                .matchParentSize()
                                .zIndex(2f)
                        )
                    }
                }
            },
            miniControls = { _ ->
                Box(modifier = Modifier.fillMaxSize()) {
                    val currentSizeScale = playerSheetState.miniSizeScale.targetValue
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

        if (!playerUiState.isUpcoming && !isMinimized && !localIsInPipMode && sponsorSegments.isNotEmpty()) {
            val sponsorButtonPositionMs by remember {
                derivedStateOf { (screenState.currentPosition / 1_000L) * 1_000L }
            }
            val sponsorLayerModifier = if (screenState.isFullscreen || expandedPlayerBottom <= 0.dp) {
                Modifier.fillMaxHeight()
            } else {
                Modifier.height(expandedPlayerBottom)
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .width(fullscreenPlayerWidth)
                    .then(sponsorLayerModifier)
                    .zIndex(3f)
            ) {
                SponsorBlockSkipButton(
                    sponsorSegments = sponsorSegments,
                    currentPositionMs = sponsorButtonPositionMs,
                    categoryActions = EnhancedPlayerManager.getInstance().sbCategoryActions,
                    controlsVisible = screenState.showControls,
                    onSkipClick = { endPositionMs ->
                        EnhancedPlayerManager.getInstance().skipToSegmentEnd(endPositionMs)
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(
                            end = sponsorSkipEndPadding,
                            bottom = floatingSponsorSkipBottomPadding
                        )
                )
            }
        }

        BackHandler(enabled = fullscreenSidePanelVisible, onBack = ::closeFullscreenSidePanel)

        if (fullscreenSidePanelVisible) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = fullscreenDrawerOffset)
                    .width(fullscreenDrawerWidth)
                    .fillMaxHeight()
                    .then(fullscreenSidePanelDragModifier)
                    .background(MaterialTheme.colorScheme.surface)
                    .zIndex(8f)
            ) {
                if (showSettingsSurface) {
                    SettingsMenuDialog(
                        playerState = playerState,
                        autoplayEnabled = playerUiState.autoplayEnabled,
                        subtitlesEnabled = screenState.subtitlesEnabled,
                        initialPage = settingsInitialPage,
                        onDismiss = { closeFullscreenSidePanel() },
                        onQualitySelected = { option ->
                            EnhancedPlayerManager.getInstance().switchQuality(option)
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
                        ambientModeEnabled = ambientModeEnabled,
                        onAmbientModeToggle = { scope.launch { playerPreferences.setVideoAmbientModeEnabled(it) } },
                        onCastClick = {
                            DlnaCastManager.startDiscovery(context)
                            screenState.showDlnaDialog = true
                        },
                        onPipClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                                PictureInPictureHelper.isPlayerPopupSupported(context)
                            ) {
                                PictureInPictureHelper.requestPlayerPipMode(
                                    activity = activity,
                                    isPlaying = playerState.isPlaying
                                )
                            }
                        },
                        onSleepTimerClick = {
                            screenState.showSettingsMenu = false
                            screenState.showQualitySelector = false
                            screenState.showAudioTrackSelector = false
                            screenState.showPlaybackSpeedSelector = false
                            screenState.showSubtitleSelector = false
                            screenState.showSleepTimerSheet = true
                        },
                        expandedHeight = fullscreenSidePanelHeight,
                        enableVerticalDismiss = false,
                        useGroupedQualitySelector = groupedQualitySelectorEnabled,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (screenState.showChaptersSheet) {
                    val chaptersPositionMs by remember {
                        derivedStateOf { (screenState.currentPosition / 1_000L) * 1_000L }
                    }
                    FlowChaptersBottomSheet(
                        chapters = playerUiState.chapters,
                        currentPosition = chaptersPositionMs,
                        durationMs = screenState.duration,
                        onChapterClick = { newPosition ->
                            EnhancedPlayerManager.getInstance().seekTo(newPosition)
                        },
                        thumbnailUrl = video.thumbnailUrl,
                        expandedHeight = fullscreenSidePanelHeight,
                        enableVerticalDismiss = false,
                        modifier = Modifier.fillMaxSize(),
                        onDismiss = { closeFullscreenSidePanel() }
                    )
                } else if (showSleepTimerSidePanel) {
                    SleepTimerSheet(
                        onDismiss = { closeFullscreenSidePanel() },
                        expandedHeight = fullscreenSidePanelHeight,
                        enableVerticalDismiss = false,
                        asBottomSheet = false,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (showLiveChatSidePanel) {
                    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
                        androidx.compose.foundation.layout.Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.live_chat),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { closeFullscreenSidePanel() }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.close)
                                )
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        io.github.aedev.flow.ui.components.LiveChatList(
                            messages = playerUiState.liveChatMessages,
                            isLoading = playerUiState.isLiveChatLoading,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }
                } else if (showCommentsSidePanel) {
                    Column(Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.comments),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { closeFullscreenSidePanel() }) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.close)
                                )
                            }
                        }
                        CommentSortFilterChips(
                            selectedFilter = screenState.commentSortFilter,
                            onFilterChanged = { screenState.commentSortFilter = it },
                            modifier = Modifier.padding(start = 16.dp, bottom = 6.dp)
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                        val commentsListState = rememberLazyListState()
                        LaunchedEffect(screenState.commentSortFilter) {
                            commentsListState.scrollToItem(0)
                        }
                        FlowCommentsList(
                            comments = sortedComments,
                            isLoading = isLoadingComments,
                            listState = commentsListState,
                            selectedFilter = screenState.commentSortFilter,
                            onTimestampClick = { EnhancedPlayerManager.getInstance().seekTo(commentTimestampToMs(it)) },
                            onLoadReplies = { playerViewModel.loadCommentReplies(it) },
                            onLoadMoreReplies = { playerViewModel.loadMoreCommentReplies(it) },
                            onAuthorClick = { handle ->
                                closeFullscreenSidePanel()
                                onNavigateToChannel("@$handle")
                            },
                            onAvatarClick = {},
                            isLoadingMore = isLoadingMoreComments,
                            onLoadMore = { playerViewModel.loadMoreComments(video.id) },
                            hasMore = hasMoreComments,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }
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
            renderSettingsMenu = !canUseFullscreenSidePanel,
            mediaSheetExpandedHeight = mediaSheetExpandedHeight,
            mediaSheetCollapsedHeight = mediaSheetCollapsedHeight,
            onMediaSheetProgressChange = updateMediaSheetProgress
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
                    val currentPlayerUrl = EnhancedPlayerManager.getInstance().getPlayer()
                        ?.currentMediaItem?.localConfiguration?.uri?.toString()
                    DlnaCastManager.castStreamInfo(
                        device = device,
                        title = video.title,
                        streamInfo = playerUiState.streamInfo,
                        currentPlayerUrl = currentPlayerUrl
                    )
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
            showShortsPlayerPrompt = showShortsPlayerPrompt,
            comments = comments,
            commentsEnabled = commentsEnabled,
            isLoadingComments = isLoadingComments,
            isLoadingMoreComments = isLoadingMoreComments,
            hasMoreComments = hasMoreComments,
            onLoadMoreComments = { videoId -> playerViewModel.loadMoreComments(videoId) },
            mediaSheetExpandedHeight = mediaSheetExpandedHeight,
            mediaSheetCollapsedHeight = mediaSheetCollapsedHeight,
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
            renderChaptersSheet = !canUseFullscreenSidePanel,
            renderSleepTimerSheet = !canUseFullscreenSidePanel,
            onMediaSheetProgressChange = updateMediaSheetProgress
        )
    }
}
