package io.github.aedev.flow.ui.screens.player.content

import android.app.Activity
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.DeArrowResult
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.DeArrowRepository
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.PictureInPictureHelper
import io.github.aedev.flow.player.state.EnhancedPlayerState
import io.github.aedev.flow.ui.components.Media3SubtitleOverlay
import io.github.aedev.flow.ui.screens.player.PremiumControlsOverlay
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.components.*
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.CoroutineScope
import kotlin.math.max

@UnstableApi
@Composable
fun PlayerContent(
    video: Video,
    height: Dp,
    screenState: PlayerScreenState,
    playerState: EnhancedPlayerState,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    scope: CoroutineScope,
    activity: Activity?,
    audioManager: AudioManager,
    maxVolume: Int,
    canGoPrevious: Boolean,
    isPipSupported: Boolean,
    onBack: () -> Unit,
    onVideoClick: (Video) -> Unit
) {
    val context = LocalContext.current
    val playerPrefs = remember { PlayerPreferences(context) }
    val deArrowEnabled by playerPrefs.deArrowEnabled.collectAsState(initial = false)
    val lockModeEnabled by playerPrefs.overlayLockModeEnabled.collectAsState(initial = false)
    val doubleTapSeekSeconds by playerPrefs.doubleTapSeekSeconds.collectAsState(initial = 10)
    val allowVolumeBoost by playerPrefs.allowVolumeBoost.collectAsState(initial = false)
    val deArrowResult by produceState<DeArrowResult?>(
        initialValue = null,
        key1 = video.id,
        key2 = deArrowEnabled
    ) {
        value = if (deArrowEnabled) DeArrowRepository.getDeArrowResult(video.id) else null
    }
    val resolvedVideoTitle = deArrowResult?.title ?: uiState.streamInfo?.name ?: video.title

    LaunchedEffect(lockModeEnabled) {
        if (!lockModeEnabled && screenState.isTouchLocked) {
            screenState.isTouchLocked = false
        }
    }

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val leftSafeInset = with(density) {
        max(
            WindowInsets.displayCutout.getLeft(this, layoutDirection),
            WindowInsets.systemBars.getLeft(this, layoutDirection)
        ).toDp()
    }
    val rightSafeInset = with(density) {
        max(
            WindowInsets.displayCutout.getRight(this, layoutDirection),
            WindowInsets.systemBars.getRight(this, layoutDirection)
        ).toDp()
    }
    val leftGestureOverlayPadding = maxOf(leftSafeInset + 36.dp, 72.dp)
    val rightGestureOverlayPadding = maxOf(rightSafeInset + 36.dp, 72.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.Black)
            .then(
                if (screenState.isTouchLocked) {
                    Modifier
                } else {
                    Modifier.videoPlayerControls(
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
                        onVolumeChange = { screenState.volumeLevel = it },
                        onShowVolumeChange = { screenState.showVolumeOverlay = it },
                        onBack = onBack,
                        brightnessLevel = screenState.brightnessLevel,
                        volumeLevel = screenState.volumeLevel,
                        maxVolume = maxVolume,
                        audioManager = audioManager,
                        activity = activity,
                        allowVolumeBoost = allowVolumeBoost,
                        doubleTapSeekMs = doubleTapSeekSeconds * 1000L,
                        onExitFullscreen = { screenState.isFullscreen = false }
                    )
                }
            )
    ) {
        // Video Surface
        VideoPlayerSurface(
            video = video,
            resizeMode = screenState.resizeMode,
            modifier = Modifier.fillMaxSize()
        )

        Media3SubtitleOverlay(
            enabled = screenState.subtitlesEnabled,
            isAutoGenerated = playerState.availableSubtitles
                .firstOrNull { it.url == screenState.selectedSubtitleUrl }
                ?.isAutoGenerated == true,
            style = screenState.subtitleStyle,
            modifier = Modifier.fillMaxSize()
        )
        
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
                .align(Alignment.CenterStart)
                .padding(start = leftGestureOverlayPadding)
        )
        
        // Volume overlay
        VolumeOverlay(
            isVisible = screenState.showVolumeOverlay,
            volumeLevel = screenState.volumeLevel,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = rightGestureOverlayPadding)
        )
        
        // Speed boost overlay
        SpeedBoostOverlay(
            isVisible = screenState.isSpeedBoostActive,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 0.dp)
        )

        // ── Error overlay — icon + title only; details/actions live in the body panel ──
        if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.80f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .widthIn(max = 400.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = "Playback error",
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = uiState.error,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        // Custom Controls Overlay
        var showRemainingTime by rememberSaveable { mutableStateOf(false) }
        PremiumControlsOverlay(
            isVisible = (screenState.showControls || screenState.isTouchLocked) && !screenState.isInPipMode,
            isPlaying = playerState.isPlaying,
            hasEnded = playerState.hasEnded,
            isBuffering = playerState.isBuffering,
            currentPosition = screenState.currentPosition,
            duration = screenState.duration,
            qualityLabel = if (playerState.currentQuality == 0) 
                stringResource(R.string.quality_auto_template, playerState.effectiveQuality) 
            else 
                playerState.currentQuality.toString(),
            videoTitle = resolvedVideoTitle,
            playbackSpeed = playerState.playbackSpeed,
            resizeMode = screenState.resizeMode,
            onResizeClick = { screenState.cycleResizeMode() },
            onPlayPause = {
                if (playerState.hasEnded) {
                    EnhancedPlayerManager.getInstance().replay()
                } else if (playerState.isPlaying) {
                    EnhancedPlayerManager.getInstance().pause()
                } else {
                    EnhancedPlayerManager.getInstance().play()
                }
            },
            onSeek = { newPosition ->
                val manager = EnhancedPlayerManager.getInstance()
                if (playerState.isLive) {
                    manager.seekToLiveTimeline(newPosition)
                } else {
                    manager.seekTo(newPosition)
                }
            },
            onBack = {
                if (screenState.isFullscreen) {
                    screenState.isFullscreen = false
                } else {
                    onBack()
                }
            },
            onSettingsClick = { screenState.showSettingsMenu = true },
            onQualityClick = { screenState.showQualitySelector = true },
            onSpeedClick = { screenState.showPlaybackSpeedSelector = true },
            onFullscreenClick = { screenState.toggleFullscreen() },
            isFullscreen = screenState.isFullscreen,
            isPipSupported = isPipSupported,
            onPipClick = {
                activity?.let { act ->
                    PictureInPictureHelper.requestPlayerPipMode(
                        activity = act,
                        isPlaying = playerState.isPlaying
                    )
                }
            },
            seekbarPreviewHelper = screenState.seekbarPreviewHelper,
            chapters = uiState.chapters,
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
            autoplayEnabled = uiState.autoplayEnabled,
            isLooping = playerState.isLooping,
            onAutoplayToggle = { viewModel.toggleAutoplay(it) },
            onPrevious = {
                viewModel.getPreviousVideoId()?.let { prevId ->
                    onVideoClick(Video(
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
            },
            onNext = {
                uiState.relatedVideos.firstOrNull()?.let { nextVideo ->
                    onVideoClick(nextVideo)
                }
            },
            hasPrevious = canGoPrevious,
            hasNext = uiState.relatedVideos.isNotEmpty(),
            bufferedPercentage = playerState.bufferedPercentage,
            windowInsets = WindowInsets(0, 0, 0, 0),
            onCastClick = {
                io.github.aedev.flow.player.CastHelper.showCastPicker(context)
            },
            isCasting = io.github.aedev.flow.player.CastHelper.isCasting(context),
            isLive = !uiState.hlsUrl.isNullOrEmpty(),
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
                }
            }
        )
    }
}
