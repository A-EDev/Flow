package com.flow.youtube.ui.screens.player

import android.app.Activity
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.PictureInPictureHelper

// Modular components
import com.flow.youtube.ui.screens.player.content.PlayerContent
import com.flow.youtube.ui.screens.player.content.VideoInfoContent
import com.flow.youtube.ui.screens.player.content.relatedVideosContent
import com.flow.youtube.ui.screens.player.content.rememberCompleteVideo
import com.flow.youtube.ui.screens.player.dialogs.PlayerBottomSheetsContainer
import com.flow.youtube.ui.screens.player.dialogs.PlayerDialogsContainer
import com.flow.youtube.ui.screens.player.effects.*
import com.flow.youtube.ui.screens.player.state.PlayerScreenState
import com.flow.youtube.ui.screens.player.state.rememberAudioSystemInfo
import com.flow.youtube.ui.screens.player.state.rememberPlayerScreenState

@UnstableApi
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
    // ===== CONTEXT & SYSTEM =====
    val context = LocalContext.current
    val activity = context as? Activity
    val configuration = LocalConfiguration.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    
    // ===== VIEW MODELS =====
    val musicVm: com.flow.youtube.ui.screens.music.MusicPlayerViewModel = hiltViewModel()
    
    // ===== CONSOLIDATED STATE =====
    val screenState = rememberPlayerScreenState()
    val snackbarHostState = remember { SnackbarHostState() }
    val audioSystemInfo = rememberAudioSystemInfo(context)
    
    // ===== COLLECTED STATE =====
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()
    val comments by viewModel.commentsState.collectAsStateWithLifecycle()
    val isLoadingComments by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val canGoPrevious by viewModel.canGoPrevious.collectAsStateWithLifecycle()
    
    // ===== PIP PREFERENCES =====
    val pipPreferences = rememberPipPreferences(context)
    
    // ===== DERIVED STATE =====
    val completeVideo = rememberCompleteVideo(video, uiState)
    val isPipSupported = remember(pipPreferences.manualPipButtonEnabled) {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && 
        PictureInPictureHelper.isPipSupported(context) &&
        pipPreferences.manualPipButtonEnabled
    }
    
    // ===== LAYOUT CALCULATIONS =====
    val portraitHeight = if (screenState.isFullscreen || screenState.isInPipMode) {
        configuration.screenHeightDp.dp
    } else {
        (configuration.screenWidthDp.dp * 9f / 16f).coerceAtLeast(220.dp)
    }

    // ===== EFFECTS =====
    
    // Error snackbar
    LaunchedEffect(uiState.error) {
        uiState.error?.let { errorMsg ->
            snackbarHostState.showSnackbar(message = errorMsg, withDismissAction = true)
        }
    }
    
    // PiP effects
    SetupPipEffects(
        context = context,
        activity = activity,
        lifecycleOwner = lifecycleOwner,
        isPlaying = playerState.isPlaying,
        pipPreferences = pipPreferences,
        onPipModeChanged = { screenState.isInPipMode = it }
    )
    
    // Orientation reset on dispose
    OrientationResetEffect(activity)
    
    // Position tracking
    PositionTrackingEffect(
        isPlaying = playerState.isPlaying,
        screenState = screenState
    )
    
    // Watch progress saving
    WatchProgressSaveEffect(
        videoId = video.id,
        video = video,
        isPlaying = playerState.isPlaying,
        currentPosition = screenState.currentPosition,
        duration = screenState.duration,
        uiState = uiState,
        viewModel = viewModel
    )
    
    // Auto-hide controls
    AutoHideControlsEffect(
        showControls = screenState.showControls,
        isPlaying = playerState.isPlaying,
        onHideControls = { screenState.showControls = false }
    )
    
    // Auto-play next video
    AutoPlayNextEffect(
        hasEnded = playerState.hasEnded,
        autoplayEnabled = uiState.autoplayEnabled,
        relatedVideos = uiState.relatedVideos,
        onVideoClick = onVideoClick
    )
    
    // Gesture overlay auto-hide
    GestureOverlayAutoHideEffect(screenState)
    
    // Subtitle loading
    SubtitleLoadEffectWithState(screenState)
    
    // Fullscreen handling
    FullscreenEffect(
        isFullscreen = screenState.isFullscreen,
        activity = activity
    )
    
    // Video loading
    VideoLoadEffect(
        videoId = video.id,
        context = context,
        screenState = screenState,
        viewModel = viewModel
    )
    
    // Player initialization
    PlayerInitEffect(
        videoId = video.id,
        uiState = uiState,
        context = context,
        screenState = screenState
    )
    
    // Seekbar preview
    SeekbarPreviewEffectWithState(
        context = context,
        uiState = uiState,
        screenState = screenState
    )
    
    // Comments loading
    CommentsLoadEffect(
        videoId = video.id,
        viewModel = viewModel
    )
    
    // Subscription and like state
    SubscriptionAndLikeEffect(
        videoId = video.id,
        uiState = uiState,
        viewModel = viewModel
    )
    
    // Short video prompt
    ShortVideoPromptEffect(
        videoDuration = completeVideo.duration,
        screenState = screenState
    )
    
    // Video cleanup on dispose
    VideoCleanupEffect(
        videoId = video.id,
        video = video,
        currentPosition = screenState.currentPosition,
        duration = screenState.duration,
        uiState = uiState,
        viewModel = viewModel
    )
    
    // ===== BACK HANDLER =====
    BackHandler(enabled = !screenState.isFullscreen) {
        EnhancedPlayerManager.getInstance().pause()
        onBack()
    }
    
    // ===== UI LAYOUT =====
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (screenState.isInPipMode) Color.Black else MaterialTheme.colorScheme.background)
    ) {
        // Snackbar host
        SnackbarHost(
            hostState = snackbarHostState, 
            modifier = Modifier.align(Alignment.BottomCenter)
        )
        
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val isWideLayout = maxWidth > 600.dp && maxHeight < maxWidth && 
                              !screenState.isFullscreen && !screenState.isInPipMode
            val widePlayerHeight = (maxWidth * 0.65f * 9f / 16f)

            if (isWideLayout) {
                // Wide/Tablet layout
                Row(Modifier.fillMaxSize()) {
                    Column(
                        Modifier
                            .weight(0.65f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        PlayerContent(
                            video = video,
                            height = widePlayerHeight,
                            screenState = screenState,
                            playerState = playerState,
                            uiState = uiState,
                            viewModel = viewModel,
                            scope = scope,
                            activity = activity,
                            audioManager = audioSystemInfo.audioManager,
                            maxVolume = audioSystemInfo.maxVolume,
                            canGoPrevious = canGoPrevious,
                            isPipSupported = isPipSupported,
                            onBack = onBack,
                            onVideoClick = onVideoClick
                        )
                        
                        VideoInfoContent(
                            video = video,
                            uiState = uiState,
                            viewModel = viewModel,
                            screenState = screenState,
                            comments = comments,
                            context = context,
                            scope = scope,
                            snackbarHostState = snackbarHostState,
                            onChannelClick = onChannelClick
                        )
                    }
                    
                    LazyColumn(
                        Modifier.weight(0.35f), 
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        relatedVideosContent(
                            relatedVideos = uiState.relatedVideos,
                            onVideoClick = onVideoClick
                        )
                    }
                }
            } else {
                // Portrait/Phone layout
                Column(Modifier.fillMaxSize()) {
                    PlayerContent(
                        video = video,
                        height = portraitHeight,
                        screenState = screenState,
                        playerState = playerState,
                        uiState = uiState,
                        viewModel = viewModel,
                        scope = scope,
                        activity = activity,
                        audioManager = audioSystemInfo.audioManager,
                        maxVolume = audioSystemInfo.maxVolume,
                        canGoPrevious = canGoPrevious,
                        isPipSupported = isPipSupported,
                        onBack = onBack,
                        onVideoClick = onVideoClick
                    )
                    
                    if (!screenState.isFullscreen && !screenState.isInPipMode) {
                        LazyColumn(
                            Modifier.weight(1f), 
                            contentPadding = PaddingValues(bottom = 80.dp)
                        ) {
                            item {
                                VideoInfoContent(
                                    video = video,
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    screenState = screenState,
                                    comments = comments,
                                    context = context,
                                    scope = scope,
                                    snackbarHostState = snackbarHostState,
                                    onChannelClick = onChannelClick
                                )
                            }
                            relatedVideosContent(
                                relatedVideos = uiState.relatedVideos,
                                onVideoClick = onVideoClick
                            )
                        }
                    }
                }
            }
            
            // ===== DIALOGS =====
            PlayerDialogsContainer(
                screenState = screenState,
                playerState = playerState,
                uiState = uiState,
                video = completeVideo,
                viewModel = viewModel
            )
            
            // ===== BOTTOM SHEETS =====
            PlayerBottomSheetsContainer(
                screenState = screenState,
                uiState = uiState,
                video = video,
                completeVideo = completeVideo,
                comments = comments,
                isLoadingComments = isLoadingComments,
                context = context,
                musicVm = musicVm,
                onPlayAsShort = onPlayAsShort,
                onPlayAsMusic = onPlayAsMusic
            )
        }
    }
}
