package com.flow.youtube.ui.screens.player.content

import android.app.Activity
import android.media.AudioManager
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.flow.youtube.R
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.PictureInPictureHelper
import com.flow.youtube.player.state.EnhancedPlayerState
import com.flow.youtube.ui.components.SubtitleOverlay
import com.flow.youtube.ui.screens.player.PremiumControlsOverlay
import com.flow.youtube.ui.screens.player.VideoPlayerUiState
import com.flow.youtube.ui.screens.player.VideoPlayerViewModel
import com.flow.youtube.ui.screens.player.components.*
import com.flow.youtube.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.CoroutineScope

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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .background(Color.Black)
            .videoPlayerControls(
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
                onBack = onBack,
                brightnessLevel = screenState.brightnessLevel,
                volumeLevel = screenState.volumeLevel,
                maxVolume = maxVolume,
                audioManager = audioManager,
                activity = activity
            )
    ) {
        // Video Surface
        VideoPlayerSurface(
            video = video,
            resizeMode = screenState.resizeMode,
            modifier = Modifier.fillMaxSize()
        )
        
        // Subtitle Overlay
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
        
        // Speed boost overlay
        SpeedBoostOverlay(
            isVisible = screenState.isSpeedBoostActive,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 0.dp)
        )
        
        // Custom Controls Overlay
        PremiumControlsOverlay(
            isVisible = screenState.showControls && !screenState.isInPipMode,
            isPlaying = playerState.isPlaying,
            isBuffering = playerState.isBuffering,
            currentPosition = screenState.currentPosition,
            duration = screenState.duration,
            qualityLabel = if (playerState.currentQuality == 0) 
                stringResource(R.string.quality_auto_template, playerState.effectiveQuality) 
            else 
                playerState.currentQuality.toString(),
            resizeMode = screenState.resizeMode,
            onResizeClick = { screenState.cycleResizeMode() },
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
                if (screenState.isFullscreen) {
                    screenState.isFullscreen = false
                } else {
                    onBack()
                }
            },
            onSettingsClick = { screenState.showSettingsMenu = true },
            onFullscreenClick = { screenState.toggleFullscreen() },
            isFullscreen = screenState.isFullscreen,
            isPipSupported = isPipSupported,
            onPipClick = {
                activity?.let { act ->
                    PictureInPictureHelper.enterPipMode(
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
            autoplayEnabled = uiState.autoplayEnabled,
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
                com.flow.youtube.player.CastHelper.showCastPicker(context)
            },
            isCasting = com.flow.youtube.player.CastHelper.isCasting(context)
        )
    }
}
