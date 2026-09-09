package io.github.aedev.flow.ui.screens.player.stage

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.media3.common.util.UnstableApi
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.PictureInPictureHelper
import io.github.aedev.flow.player.dlna.DlnaCastManager
import io.github.aedev.flow.ui.components.videoplayer.controls.PlayerControlsOverlay
import io.github.aedev.flow.ui.components.videoplayer.controls.resolvePlayerQualityLabel
import io.github.aedev.flow.ui.components.videoplayer.overlay.AutoplayCountdownOverlay
import io.github.aedev.flow.ui.components.videoplayer.placedWhen
import io.github.aedev.flow.ui.components.videoplayer.settings.PlayerSettingsPage
import io.github.aedev.flow.ui.screens.player.state.PlayerSheet
import io.github.aedev.flow.ui.screens.player.state.SubtitleSelection

/** The transport controls the expanded player mounts once its surfaces are at rest. */
@UnstableApi
@Composable
internal fun VideoStageControls(
    session: VideoPlayerStageSession,
    expandedSurfacesPlaced: () -> Boolean,
    videoAspectRatio: Float,
    canGoPrevious: Boolean,
    isCommentsAvailable: Boolean,
    canUseFullscreenSidePanel: Boolean,
    showRemainingTime: Boolean,
    onToggleRemainingTime: () -> Unit,
    rememberSubtitleLanguage: (String) -> Unit,
    onSbSubmitClick: () -> Unit,
    onCastClick: () -> Unit,
) {
    val video = session.video
    val context = session.context
    val activity = session.activity
    val screenState = session.screenState
    val playerState = session.playerState
    val playerUiState = session.uiState
    val playerViewModel = session.viewModel
    val playerSheetState = session.sheetState
    val prefs = session.prefs
    val pipPreferences = session.pipPreferences

    val controlsShown = screenState.showControls || screenState.isTouchLocked
    // Buffered position advances on every position poll; quantised to 1% so
    // this scope recomposes on visible steps only.
    val bufferedFraction by remember(screenState) {
        derivedStateOf {
            val duration = screenState.duration
            val quantised =
                if (duration > 0) {
                    (screenState.bufferedPosition * 100L / duration).toInt() / 100f
                } else {
                    0f
                }
            quantised.coerceIn(0f, 1f)
        }
    }
    PlayerControlsOverlay(
        modifier = Modifier.placedWhen(expandedSurfacesPlaced),
        isVisible = controlsShown,
        isPlaying = playerState.playWhenReady,
        hasEnded = playerState.hasEnded,
        isBuffering = playerState.isBuffering,
        currentPosition = { screenState.currentPosition },
        duration = screenState.duration,
        qualityLabel =
            resolvePlayerQualityLabel(
                currentQuality = playerState.currentQuality,
                effectiveQuality = playerState.effectiveQuality,
                autoLabel = context.getString(R.string.quality_auto),
                autoWithHeightLabel =
                    context.getString(
                        R.string.quality_auto_template,
                        playerState.effectiveQuality,
                    ),
            ),
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
        onScrubbingChange = { scrubbing ->
            screenState.isScrubbing = scrubbing
            screenState.onInteraction()
        },
        onBack = { playerSheetState.collapse() },
        onSettingsClick = { screenState.open(PlayerSheet.Settings()) },
        onQualityClick = { screenState.open(PlayerSheet.Settings(PlayerSettingsPage.Quality)) },
        onSpeedClick = { screenState.open(PlayerSheet.Settings(PlayerSettingsPage.Speed)) },
        onFullscreenClick = { screenState.toggleFullscreen() },
        isFullscreen = screenState.isFullscreen,
        isPipSupported =
            android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
                io.github.aedev.flow.player.PictureInPictureHelper
                    .isPlayerPopupSupported(context) &&
                pipPreferences.manualPipButtonEnabled,
        onPipClick = {
            PictureInPictureHelper.requestPlayerPipMode(
                activity = activity,
                aspectRatio = videoAspectRatio,
                isPlaying = playerState.isPlaying,
            )
        },
        chapters = playerUiState.chapters,
        onChapterClick = { screenState.open(PlayerSheet.Chapters) },
        onSubtitleClick = {
            if (screenState.subtitlesEnabled) {
                SubtitleSelection.disable(screenState)
            } else {
                val enabled =
                    SubtitleSelection.enable(
                        screenState = screenState,
                        subtitles = playerState.availableSubtitles,
                        languageTag = prefs.preferredSubtitleLanguage,
                        rememberLanguage = rememberSubtitleLanguage,
                    )
                if (!enabled) screenState.open(PlayerSheet.Settings(PlayerSettingsPage.Subtitles))
            }
        },
        onSubtitleLongClick = { screenState.open(PlayerSheet.Settings(PlayerSettingsPage.Subtitles)) },
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
        bufferedPercentage = bufferedFraction,
        windowInsets = WindowInsets(0, 0, 0, 0),
        sbSubmitEnabled = prefs.sbSubmitEnabled,
        onSbSubmitClick = onSbSubmitClick,
        onCastClick = onCastClick,
        isCasting = DlnaCastManager.isCasting,
        isLive = !playerUiState.hlsUrl.isNullOrEmpty(),
        onLiveClick = {
            EnhancedPlayerManager.getInstance().seekToLiveEdge(resetSpeed = true)
        },
        isLiveChatAvailable = playerUiState.isLiveChatAvailable,
        onLiveChatClick = {
            val fullscreenLiveChat = PlayerSheet.LiveChat(fullscreen = true)
            if (screenState.activeSheet == fullscreenLiveChat) {
                screenState.closeSheet()
            } else {
                screenState.open(fullscreenLiveChat)
            }
        },
        isCommentsAvailable = isCommentsAvailable,
        isCommentsPanelOpen = screenState.activeSheet is PlayerSheet.Comments,
        onCommentsClick = {
            val comments = PlayerSheet.Comments(fullscreen = canUseFullscreenSidePanel)
            if (canUseFullscreenSidePanel && screenState.activeSheet == comments) {
                screenState.closeSheet()
            } else {
                screenState.open(comments)
            }
        },
        onSleepTimerClick = { screenState.open(PlayerSheet.SleepTimer) },
        isSleepTimerActive = io.github.aedev.flow.player.SleepTimerManager.isActive,
        showRemainingTime = showRemainingTime,
        onToggleRemainingTime = onToggleRemainingTime,
        isTouchLocked = screenState.isTouchLocked,
        lockModeEnabled = prefs.lockModeEnabled,
        lockOverlayRevealSignal = screenState.lockOverlayRevealSignal,
        isPortraitFullscreen = screenState.isFullscreenPortrait,
        onTouchLockToggle = {
            if (prefs.lockModeEnabled || screenState.isTouchLocked) {
                screenState.isTouchLocked = !screenState.isTouchLocked
                screenState.showControls = true
                if (screenState.isTouchLocked) {
                    screenState.revealLockOverlay()
                }
                screenState.onInteraction()
            }
        },
    )

    AutoplayCountdownOverlay()
}
