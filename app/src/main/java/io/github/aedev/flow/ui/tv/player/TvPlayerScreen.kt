package io.github.aedev.flow.ui.tv.player

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import io.github.aedev.flow.data.local.PlayerOverlayPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.player.subtitle.SubtitleChoice
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.SaveVideoSheet
import io.github.aedev.flow.ui.components.videoplayer.VideoPlayerSurface
import io.github.aedev.flow.ui.components.videoplayer.controls.PlayerControlActions
import io.github.aedev.flow.ui.components.videoplayer.overlay.AutoplayCountdownOverlay
import io.github.aedev.flow.ui.components.videoplayer.overlay.SponsorBlockSkipButton
import io.github.aedev.flow.ui.components.videoplayer.subtitle.Media3SubtitleOverlay
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.effects.KeepScreenOnEffect
import io.github.aedev.flow.ui.screens.player.effects.PositionTrackingEffect
import io.github.aedev.flow.ui.screens.player.effects.WatchProgressSaveEffect
import io.github.aedev.flow.ui.screens.player.state.PlayerSheet
import io.github.aedev.flow.ui.screens.player.state.rememberPlayerCommentsUiState
import io.github.aedev.flow.ui.screens.player.state.rememberPlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.rememberVideoPlayerPreferences
import io.github.aedev.flow.ui.tv.input.TvPlayerAction
import io.github.aedev.flow.ui.tv.player.state.TvOverlayMode
import io.github.aedev.flow.ui.tv.player.state.TvPlayerOverlayController
import io.github.aedev.flow.ui.tv.player.state.TvScrubController
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine

private const val MEDIA_KEY_SEEK_MS = 10_000L

private enum class TvPlayerFocusTarget { PLAY_PAUSE, SEEK_BAR, UP_NEXT }

/**
 * Full-screen TV player: the phone player's surface, captions, seek bar, transport, skip button,
 * autoplay countdown and sheets, driven by the remote. Keys go through [TvPlayerInput]; scrubbing is
 * preview-then-commit through [TvScrubController]; the controls hide on one restartable delay.
 */
@Composable
fun TvPlayerScreen(
    video: Video,
    viewModel: VideoPlayerViewModel,
    onClose: () -> Unit,
    onOpenChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val manager = remember { EnhancedPlayerManager.getInstance() }
    val playerState by manager.playerState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val prefs = rememberVideoPlayerPreferences(context)
    val screenState = rememberPlayerScreenState()
    val commentsUiState = rememberPlayerCommentsUiState(viewModel)
    val overlayPreferences by prefs.preferences.overlayPreferences.collectAsState(initial = remember { PlayerOverlayPreferences() })
    val sponsorColors =
        remember(overlayPreferences.sponsorCategoryColors) {
            overlayPreferences.sponsorCategoryColors.mapValues { (_, argb) -> Color(argb) }
        }

    val overlayController = remember { TvPlayerOverlayController { SystemClock.uptimeMillis() } }
    val scrubController = remember { TvScrubController() }
    val overlayState by overlayController.state.collectAsStateWithLifecycle()
    var scrubUiState by remember { mutableStateOf(TvScrubController.ScrubState()) }
    var showSave by remember { mutableStateOf(false) }

    val rootFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val upNextFocusRequester = remember { FocusRequester() }
    val countdownFocusRequester = remember { FocusRequester() }
    var seekBarFocused by remember { mutableStateOf(false) }
    var upNextShown by remember { mutableStateOf(false) }
    var pendingFocusTarget by remember { mutableStateOf(TvPlayerFocusTarget.PLAY_PAUSE) }

    val autoplayCountdown by manager.autoplayCountdown.collectAsStateWithLifecycle()
    val sponsorSegments by manager.sponsorSegments.collectAsStateWithLifecycle()
    val queueVideos by manager.queueVideos.collectAsStateWithLifecycle()
    val currentQueueIndex by manager.currentQueueIndexState.collectAsStateWithLifecycle()

    PositionTrackingEffect(
        isPlaying = playerState.isPlaying,
        screenState = screenState,
        showsPreciseProgress = overlayState.mode == TvOverlayMode.TRANSPORT,
    )
    KeepScreenOnEffect(
        isPlaying = playerState.isPlaying || playerState.isBuffering,
        activity = context as? android.app.Activity,
        lifecycleOwner = lifecycleOwner,
    )
    // History and Continue Watching are written here, as the phone's player overlay does.
    WatchProgressSaveEffect(
        video = video,
        isPlaying = playerState.isPlaying,
        currentPosition = { manager.getCurrentPosition().coerceAtLeast(0L) },
        duration = { manager.getDuration().coerceAtLeast(0L) },
        uiState = uiState,
        viewModel = viewModel,
    )
    TvPlayerSheetEffects(video.id, screenState, viewModel, prefs.commentsEnabled)

    fun openSheet(sheet: PlayerSheet) {
        screenState.open(sheet)
        overlayController.openPanel()
    }

    fun closeSheet() {
        screenState.closeSheet()
        overlayController.closePanel()
    }

    fun playFromPlayer(next: Video) {
        GlobalPlayerState.setCurrentVideo(next)
        viewModel.playVideo(next, userOpened = false)
        overlayController.hide()
    }

    fun revealTransport(target: TvPlayerFocusTarget) {
        if (overlayController.state.value.mode == TvOverlayMode.HIDDEN) {
            pendingFocusTarget = target
            overlayController.showTransport()
        }
    }

    fun perform(
        action: TvPlayerAction,
        repeatCount: Int,
    ) {
        when (action) {
            TvPlayerAction.TOGGLE_PLAYBACK -> {
                if (manager.playerState.value.isPlaying) manager.pause() else manager.play()
                revealTransport(TvPlayerFocusTarget.PLAY_PAUSE)
            }

            TvPlayerAction.PLAY -> {
                manager.play()
                revealTransport(TvPlayerFocusTarget.PLAY_PAUSE)
            }

            TvPlayerAction.PAUSE -> {
                manager.pause()
                revealTransport(TvPlayerFocusTarget.PLAY_PAUSE)
            }

            TvPlayerAction.SEEK_BACK -> {
                manager.seekTo((manager.getCurrentPosition() - MEDIA_KEY_SEEK_MS).coerceAtLeast(0L))
            }

            TvPlayerAction.SEEK_FORWARD -> {
                manager.seekTo(manager.getCurrentPosition() + MEDIA_KEY_SEEK_MS)
            }

            TvPlayerAction.NEXT -> {
                viewModel.playNext()
            }

            TvPlayerAction.PREVIOUS -> {
                viewModel.playPrevious()
            }

            TvPlayerAction.TOGGLE_CAPTIONS -> {
                val state = manager.playerState.value
                if (state.selectedSubtitleUrl != null) {
                    manager.selectSubtitle(null)
                } else {
                    SubtitleChoice.preferred(state.availableSubtitles, languageTag = null)?.let { manager.selectSubtitle(it.url) }
                }
            }

            TvPlayerAction.SHOW_TRANSPORT -> {
                revealTransport(TvPlayerFocusTarget.PLAY_PAUSE)
            }

            TvPlayerAction.SHOW_UP_NEXT -> {
                upNextShown = true
                revealTransport(TvPlayerFocusTarget.UP_NEXT)
            }

            TvPlayerAction.SCRUB_BACK, TvPlayerAction.SCRUB_FORWARD -> {
                scrubUiState =
                    scrubController.beginOrStep(
                        direction = if (action == TvPlayerAction.SCRUB_BACK) -1 else 1,
                        repeatCount = repeatCount,
                        currentPositionMs = manager.getCurrentPosition().coerceAtLeast(0L),
                        durationMs = manager.getDuration().coerceAtLeast(0L),
                    )
                revealTransport(TvPlayerFocusTarget.SEEK_BAR)
            }

            TvPlayerAction.COMMIT_SCRUB -> {
                scrubController.commit()?.let(manager::seekTo)
                scrubUiState = scrubController.current
            }
        }
        overlayController.onUserInteraction()
    }

    val input =
        remember {
            TvPlayerInput(
                overlayMode = {
                    if (manager.autoplayCountdown.value.isActive) TvOverlayMode.PANEL else overlayController.state.value.mode
                },
                seekBarFocused = { seekBarFocused },
                isScrubbing = { scrubController.current.isScrubbing },
                perform = ::perform,
                onInteraction = overlayController::onUserInteraction,
                onDownFromControls = {
                    val opens = !upNextShown
                    upNextShown = true
                    opens
                },
            )
        }

    LaunchedEffect(overlayState.mode, overlayState.lastInteractionAtMs, playerState.isPlaying, scrubUiState.isScrubbing) {
        val deadline =
            overlayController.autoHideDeadline(
                isPlaying = playerState.isPlaying,
                isScrubbing = scrubUiState.isScrubbing,
            ) ?: return@LaunchedEffect
        val waitMs = deadline - SystemClock.uptimeMillis()
        if (waitMs > 0) delay(waitMs)
        overlayController.hide()
    }

    LaunchedEffect(overlayState.mode) {
        when (overlayState.mode) {
            TvOverlayMode.HIDDEN -> {
                upNextShown = false
                rootFocusRequester.requestFocus()
            }

            TvOverlayMode.TRANSPORT -> {
                when (pendingFocusTarget) {
                    TvPlayerFocusTarget.SEEK_BAR -> seekBarFocusRequester
                    TvPlayerFocusTarget.PLAY_PAUSE -> playPauseFocusRequester
                    TvPlayerFocusTarget.UP_NEXT -> upNextFocusRequester
                }.requestFocus()
                pendingFocusTarget = TvPlayerFocusTarget.PLAY_PAUSE
            }

            TvOverlayMode.PANEL -> {
                Unit
            }
        }
    }

    LaunchedEffect(video.id) {
        rootFocusRequester.requestFocus()
    }
    LaunchedEffect(upNextShown) {
        if (upNextShown && overlayController.state.value.mode == TvOverlayMode.TRANSPORT) upNextFocusRequester.requestFocus()
    }
    LaunchedEffect(autoplayCountdown.isActive) {
        if (autoplayCountdown.isActive) countdownFocusRequester.requestFocus()
    }

    BackHandler {
        when {
            scrubController.current.isScrubbing -> {
                scrubController.cancel()
                scrubUiState = scrubController.current
            }

            overlayController.state.value.mode == TvOverlayMode.PANEL -> {
                closeSheet()
            }

            !overlayController.onBack() -> {
                onClose()
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    input.onKey(event.type, event.nativeKeyEvent.keyCode, event.nativeKeyEvent.repeatCount)
                },
    ) {
        VideoPlayerSurface(
            video = video,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
            modifier = Modifier.fillMaxSize(),
            ambientMode = prefs.ambientModeEnabled,
        )
        Media3SubtitleOverlay(
            enabled = playerState.selectedSubtitleUrl != null,
            isAutoGenerated =
                playerState.availableSubtitles
                    .firstOrNull { it.url == playerState.selectedSubtitleUrl }
                    ?.isAutoGenerated == true,
            style = prefs.savedSubtitleStyle,
            modifier = Modifier.fillMaxSize(),
        )

        TvPlayerControls(
            visible = overlayState.mode == TvOverlayMode.TRANSPORT,
            video = video,
            isPlaying = playerState.isPlaying,
            hasEnded = playerState.hasEnded,
            isBuffering = playerState.isBuffering,
            isLive = playerState.isLive,
            hasPrevious = playerState.hasPrevious,
            hasNext = playerState.hasNext,
            subtitlesAvailable = playerState.availableSubtitles.isNotEmpty(),
            subtitlesOn = playerState.selectedSubtitleUrl != null,
            positionMs = { screenState.currentPosition },
            durationMs = { screenState.duration },
            scrubTargetMs = scrubUiState.takeIf { it.isScrubbing }?.targetMs,
            bufferedFraction = playerState.bufferedPercentage / 100f,
            chapters = uiState.chapters,
            sponsorSegments = sponsorSegments,
            sponsorColors = sponsorColors,
            playActions =
                PlayerControlActions(
                    onPlayPause = { perform(TvPlayerAction.TOGGLE_PLAYBACK, 0) },
                    onPrevious = { perform(TvPlayerAction.PREVIOUS, 0) },
                    onNext = { perform(TvPlayerAction.NEXT, 0) },
                ),
            utilityActions =
                TvPlayerUtilityActions(
                    onCaptions = { perform(TvPlayerAction.TOGGLE_CAPTIONS, 0) },
                    onSave = { showSave = true },
                    onComments = { openSheet(PlayerSheet.Comments(fullscreen = true)) },
                    onLiveChat = { openSheet(PlayerSheet.LiveChat(fullscreen = true)) },
                    onInfo = { openSheet(PlayerSheet.Description) },
                    onSettings = { openSheet(PlayerSheet.Settings()) },
                ),
            seekBarFocusRequester = seekBarFocusRequester,
            playPauseFocusRequester = playPauseFocusRequester,
            onSeekBarFocusChanged = { seekBarFocused = it },
            upNextShown = upNextShown,
            upNext = {
                TvUpNextRail(
                    queue = queueVideos,
                    currentQueueIndex = currentQueueIndex,
                    relatedVideos = uiState.relatedVideos,
                    onVideoClick = ::playFromPlayer,
                    firstItemFocusRequester = upNextFocusRequester,
                )
            },
        )

        SponsorBlockSkipButton(
            sponsorSegments = sponsorSegments,
            currentPositionMs = (screenState.currentPosition / 1_000L) * 1_000L,
            categoryActions = manager.sbCategoryActions,
            controlsVisible = overlayState.mode != TvOverlayMode.HIDDEN,
            playbackEnded = playerState.hasEnded,
            onSkipClick = manager::skipToSegmentEnd,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(LocalTvDimens.current.overscanHorizontal),
        )

        TvPlayerSheets(
            video = video,
            screenState = screenState,
            playerState = playerState,
            uiState = uiState,
            viewModel = viewModel,
            prefs = prefs,
            commentsUiState = commentsUiState,
            onNavigateToChannel = onOpenChannel,
            onClose = ::closeSheet,
        )

        Box(modifier = Modifier.fillMaxSize().focusRequester(countdownFocusRequester)) {
            AutoplayCountdownOverlay()
        }

        if (overlayState.mode == TvOverlayMode.HIDDEN && (uiState.isLoading || playerState.isBuffering)) {
            FlowLoadingIndicator(modifier = Modifier.align(Alignment.Center))
        }

        uiState.error?.let { error ->
            Surface(
                modifier = Modifier.align(Alignment.Center).widthIn(max = 560.dp),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                FlowErrorState(
                    error = uiState.errorHint?.let { hint -> "$error\n$hint" } ?: error,
                    onRetry = viewModel::retryLoadVideo,
                )
            }
        }
    }

    if (showSave) {
        SaveVideoSheet(video = video, onDismiss = { showSave = false })
    }
}

/**
 * Comments load once per video, the first time their panel opens; live chat polls only while its panel
 * shows and the player is resumed, as on the phone.
 */
@Composable
private fun TvPlayerSheetEffects(
    videoId: String,
    screenState: io.github.aedev.flow.ui.screens.player.state.PlayerScreenState,
    viewModel: VideoPlayerViewModel,
    commentsEnabled: Boolean,
) {
    var commentsLoadedFor by remember { mutableStateOf<String?>(null) }
    val commentsOpen = screenState.activeSheet is PlayerSheet.Comments
    LaunchedEffect(videoId, commentsOpen) {
        if (!commentsOpen || !commentsEnabled || commentsLoadedFor == videoId) return@LaunchedEffect
        commentsLoadedFor = videoId
        viewModel.loadComments(videoId)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        combine(lifecycle.currentStateFlow, snapshotFlow { screenState.activeSheet is PlayerSheet.LiveChat }) { state, shown ->
            shown && state.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        }.collect(viewModel::setLiveChatPanelVisible)
    }
}
