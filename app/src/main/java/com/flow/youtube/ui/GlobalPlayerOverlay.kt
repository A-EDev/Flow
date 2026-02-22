package com.flow.youtube.ui

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.media3.common.util.UnstableApi
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.GlobalPlayerState
import com.flow.youtube.ui.components.DraggablePlayerLayout
import com.flow.youtube.ui.components.PlayerDraggableState
import com.flow.youtube.ui.components.rememberPlayerDraggableState
import com.flow.youtube.ui.components.PlayerSheetValue
import com.flow.youtube.ui.screens.player.EnhancedVideoPlayerScreen
import com.flow.youtube.ui.screens.player.VideoPlayerViewModel
import com.flow.youtube.ui.screens.player.VideoPlayerUiState
import com.flow.youtube.ui.screens.player.components.VideoPlayerSurface
import com.flow.youtube.ui.screens.player.content.PlayerContent
import com.flow.youtube.ui.screens.player.content.rememberCompleteVideo
import com.flow.youtube.ui.screens.player.dialogs.PlayerDialogsContainer
import com.flow.youtube.ui.screens.player.dialogs.PlayerBottomSheetsContainer
import com.flow.youtube.ui.screens.player.state.rememberPlayerScreenState
import com.flow.youtube.ui.screens.player.state.rememberAudioSystemInfo
import com.flow.youtube.ui.screens.player.effects.*
import com.flow.youtube.ui.components.SubtitleOverlay
import com.flow.youtube.ui.screens.player.PremiumControlsOverlay
import com.flow.youtube.ui.screens.player.components.videoPlayerControls
import com.flow.youtube.ui.screens.player.components.SeekAnimationOverlay
import com.flow.youtube.ui.screens.player.components.BrightnessOverlay
import com.flow.youtube.ui.screens.player.components.VolumeOverlay
import com.flow.youtube.ui.screens.player.components.SpeedBoostOverlay
import com.flow.youtube.player.PictureInPictureHelper
import com.flow.youtube.R
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.player.dlna.DlnaCastManager
import com.flow.youtube.player.dlna.DlnaDevice
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
    onClose: () -> Unit,
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
    
    val screenState = rememberPlayerScreenState()
    val audioSystemInfo = rememberAudioSystemInfo(context)
    val pipPreferences = rememberPipPreferences(context)
    val completeVideo = rememberCompleteVideo(video, playerUiState)
    val canGoPrevious by playerViewModel.canGoPrevious.collectAsStateWithLifecycle()
    val comments by playerViewModel.commentsState.collectAsStateWithLifecycle()
    val isLoadingComments by playerViewModel.isLoadingComments.collectAsStateWithLifecycle()

    val playerPreferences = remember { PlayerPreferences(context) }
    val swipeGesturesEnabled by playerPreferences.swipeGesturesEnabled.collectAsState(initial = true)
    val sbSubmitEnabled by playerPreferences.sbSubmitEnabled.collectAsState(initial = false)
    val doubleTapSeekSeconds by playerPreferences.doubleTapSeekSeconds.collectAsState(initial = 10)

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
        }
    }

    // Handle Back press in Fullscreen
    BackHandler(enabled = screenState.isFullscreen) {
        screenState.isFullscreen = false
    }
    
    // ===== EFFECTS =====
    LaunchedEffect(playerUiState.isLoading) {
        if (playerUiState.isLoading) {
            playerSheetState.expand()
        }
    }
    
    BackHandler(enabled = playerSheetState.fraction < 0.5f && !localIsInPipMode) {
        playerSheetState.collapse()
    }
    
    PositionTrackingEffect(
        isPlaying = playerState.playWhenReady,
        screenState = screenState
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
        activity = activity
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
    
    VideoLoadEffect(
        videoId = video.id,
        context = context,
        screenState = screenState,
        viewModel = playerViewModel
    )
    
    // Player initialization
    PlayerInitEffect(
        videoId = video.id,
        uiState = playerUiState,
        context = context,
        screenState = screenState
    )
    
    // Seekbar preview
    SeekbarPreviewEffectWithState(
        context = context,
        uiState = playerUiState,
        screenState = screenState
    )
    
    SubtitleLoadEffectWithState(screenState)
    
    LaunchedEffect(video.id) {
        playerViewModel.loadComments(video.id)
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
        isExpanded = playerSheetState.fraction > 0.9f,
        isFullscreen = screenState.isFullscreen,
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
            val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url ?: video.thumbnailUrl
            
            if (screenState.currentPosition > 0 && screenState.duration > 0) {
                playerViewModel.savePlaybackPosition(
                    videoId = video.id,
                    position = screenState.currentPosition,
                    duration = screenState.duration,
                    title = streamInfo?.name ?: video.title,
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
                videoContent = { modifier ->
                    // ALWAYS use the same video surface
                    val gestureModifier = if (!isMinimized) {
                        modifier.videoPlayerControls(
                            isSpeedBoostActive = screenState.isSpeedBoostActive,
                            onSpeedBoostChange = { screenState.isSpeedBoostActive = it },
                            showControls = screenState.showControls,
                            onShowControlsChange = { screenState.showControls = it },
                            onShowSeekBackChange = { screenState.showSeekBackAnimation = it },
                            onShowSeekForwardChange = { screenState.showSeekForwardAnimation = it },
                            currentPosition = screenState.currentPosition,
                            duration = screenState.duration,
                            normalSpeed = screenState.normalSpeed,
                            scope = scope,
                            isFullscreen = screenState.isFullscreen,
                            onBrightnessChange = { screenState.brightnessLevel = it },
                            onShowBrightnessChange = { screenState.showBrightnessOverlay = it },
                            onVolumeChange = { screenState.volumeLevel = it },
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
                            swipeGesturesEnabled = swipeGesturesEnabled,
                            doubleTapSeekMs = doubleTapSeekSeconds * 1000L
                        )
                    } else {
                        modifier
                    }
                    
                    Box(modifier = gestureModifier) {
                        VideoPlayerSurface(
                            video = video,
                            resizeMode = screenState.resizeMode,
                            modifier = Modifier.fillMaxSize()
                        )
                        
                        // Show subtitles only when expanded
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
                            
                            // Seek animations
                            SeekAnimationOverlay(
                                showSeekBack = screenState.showSeekBackAnimation,
                                showSeekForward = screenState.showSeekForwardAnimation,
                                seekSeconds = doubleTapSeekSeconds,
                                modifier = Modifier.align(Alignment.Center)
                            )
                            
                            // Brightness overlay
                            BrightnessOverlay(
                                isVisible = screenState.showBrightnessOverlay,
                                brightnessLevel = screenState.brightnessLevel,
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .padding(start = 32.dp)
                            )
                            
                            // Volume overlay
                            VolumeOverlay(
                                isVisible = screenState.showVolumeOverlay,
                                volumeLevel = screenState.volumeLevel,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .padding(end = 32.dp)
                            )
                            
                               // 2x Speed overlay  
                            SpeedBoostOverlay(
                                isVisible = screenState.isSpeedBoostActive,
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .padding(top = 0.dp)
                            )
                        }
                        
                        // Controls overlay - fully expanded only
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
                                onFullscreenClick = { screenState.toggleFullscreen() },
                                isFullscreen = screenState.isFullscreen,
                                isPipSupported = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && 
                                    com.flow.youtube.player.PictureInPictureHelper.isPipSupported(context) &&
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
                                    if (playerState.hasPrevious) {
                                        // Queue is active — use queue-based previous
                                        playerViewModel.playPrevious()
                                    } else {
                                        // No queue — fall back to navigation history
                                        playerViewModel.getPreviousVideoId()?.let { prevId ->
                                            GlobalPlayerState.setCurrentVideo(Video(
                                                id = prevId, 
                                                title = "", 
                                                channelName = "", 
                                                channelId = "", 
                                                thumbnailUrl = "", 
                                                duration = 0, 
                                                viewCount = 0, 
                                                uploadDate = ""
                                            ))
                                        }
                                    }
                                },
                                onNext = {
                                    if (playerState.hasNext) {
                                        // Queue is active — advance to next queue item
                                        playerViewModel.playNext()
                                    } else {
                                        // No queue — fall back to first related video
                                        playerUiState.relatedVideos.firstOrNull()?.let { nextVideo ->
                                            playerViewModel.playVideo(nextVideo)
                                            GlobalPlayerState.setCurrentVideo(nextVideo)
                                        }
                                    }
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
                                isCasting = DlnaCastManager.isCasting
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
                        if (clickedVideo.duration <= 120) {
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
                MiniPlayerControls(
                    playerState = playerState,
                    onClose = onClose
                )
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
            com.flow.youtube.ui.screens.player.dialogs.SbSubmitSegmentDialog(
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
                    val streamUrl = EnhancedPlayerManager.getInstance().getPlayer()
                        ?.currentMediaItem?.localConfiguration?.uri?.toString() ?: ""
                    if (streamUrl.isNotEmpty()) {
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
 * Mini Player Controls 
 */
@Composable
private fun MiniPlayerControls(
    playerState: com.flow.youtube.player.state.EnhancedPlayerState,
    onClose: () -> Unit
) {
    // Overlay Center
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Play/Pause - Top Left
        IconButton(
            onClick = { 
                if (playerState.playWhenReady) {
                    EnhancedPlayerManager.getInstance().pause()
                } else {
                    EnhancedPlayerManager.getInstance().play()
                }
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(56.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            if (playerState.isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = if (playerState.playWhenReady) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState.playWhenReady) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        // Close - Top Right
        IconButton(
            onClick = {
                EnhancedPlayerManager.getInstance().stop()
                GlobalPlayerState.hideMiniPlayer()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .size(56.dp) 
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
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
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
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
