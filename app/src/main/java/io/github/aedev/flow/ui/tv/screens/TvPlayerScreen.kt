package io.github.aedev.flow.ui.tv.screens

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.GlobalPlayerState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.components.VideoPlayerSurface
import io.github.aedev.flow.ui.screens.player.effects.KeepScreenOnEffect
import io.github.aedev.flow.ui.screens.player.effects.WatchProgressSaveEffect
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.input.TvPlayerAction
import io.github.aedev.flow.ui.tv.input.TvPlayerKeyMapper
import io.github.aedev.flow.ui.tv.player.TvAutoplayCountdownCard
import io.github.aedev.flow.ui.tv.player.TvPlayerOverlay
import io.github.aedev.flow.ui.tv.player.TvSeekBar
import io.github.aedev.flow.ui.tv.player.TvSponsorSkipButton
import io.github.aedev.flow.ui.tv.player.TvTransportRow
import io.github.aedev.flow.ui.tv.player.TvUpNextRail
import io.github.aedev.flow.ui.tv.player.panels.TvPlayerPanelsHost
import io.github.aedev.flow.ui.tv.player.state.TvOverlayMode
import io.github.aedev.flow.ui.tv.player.state.TvPlayerOverlayController
import io.github.aedev.flow.ui.tv.player.state.TvScrubController
import io.github.aedev.flow.ui.tv.player.state.TvSeekBarMarks
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import kotlinx.coroutines.delay

private enum class TvPlayerFocusTarget { PLAY_PAUSE, SEEK_BAR, UP_NEXT }

/**
 * Full-screen TV player: video surface + overlay state machine.
 * Key events are routed through [TvPlayerKeyMapper]'s pure tables; scrubbing is
 * preview-then-commit via [TvScrubController]; auto-hide is one restartable delay.
 */
@Composable
fun TvPlayerScreen(
    video: Video,
    viewModel: VideoPlayerViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val manager = remember { EnhancedPlayerManager.getInstance() }
    val playerState by manager.playerState.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalContext.current as? android.app.Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    val overlayController = remember { TvPlayerOverlayController { SystemClock.uptimeMillis() } }
    val scrubController = remember { TvScrubController() }
    val overlayState by overlayController.state.collectAsStateWithLifecycle()
    var scrubUiState by remember { mutableStateOf(TvScrubController.ScrubState()) }

    val rootFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val upNextFocusRequester = remember { FocusRequester() }
    var seekBarFocused by remember { mutableStateOf(false) }
    var pendingFocusTarget by remember { mutableStateOf(TvPlayerFocusTarget.PLAY_PAUSE) }

    val autoplayCountdown by manager.autoplayCountdown.collectAsStateWithLifecycle()
    val sponsorSegments by manager.sponsorSegments.collectAsStateWithLifecycle()
    val queueVideos by manager.queueVideos.collectAsStateWithLifecycle()
    val currentQueueIndex by manager.currentQueueIndexState.collectAsStateWithLifecycle()

    fun playFromPlayer(next: Video) {
        GlobalPlayerState.setCurrentVideo(next)
        viewModel.playVideo(next)
        overlayController.hide()
    }

    KeepScreenOnEffect(
        isPlaying = playerState.isPlaying || playerState.isBuffering,
        activity = activity,
        lifecycleOwner = lifecycleOwner,
    )

    // Same history/progress saver the mobile overlay mounts — without it, TV
    // sessions never reach ViewHistory and Continue Watching stays empty.
    WatchProgressSaveEffect(
        videoId = video.id,
        video = video,
        isPlaying = playerState.isPlaying,
        currentPosition = { manager.getCurrentPosition().coerceAtLeast(0L) },
        duration = { manager.getDuration().coerceAtLeast(0L) },
        uiState = uiState,
        viewModel = viewModel,
    )

    fun commitScrub() {
        scrubController.commit()?.let(manager::seekTo)
        scrubUiState = scrubController.current
        overlayController.onUserInteraction()
    }

    fun stepScrub(direction: Int, repeatCount: Int) {
        scrubUiState = scrubController.beginOrStep(
            direction = direction,
            repeatCount = repeatCount,
            currentPositionMs = manager.getCurrentPosition().coerceAtLeast(0L),
            durationMs = manager.getDuration().coerceAtLeast(0L),
        )
        if (overlayController.state.value.mode == TvOverlayMode.HIDDEN) {
            pendingFocusTarget = TvPlayerFocusTarget.SEEK_BAR
            overlayController.showTransport()
        } else {
            overlayController.onUserInteraction()
        }
    }

    fun perform(action: TvPlayerAction, repeatCount: Int = 0) {
        when (action) {
            TvPlayerAction.TOGGLE_PLAYBACK ->
                if (manager.playerState.value.isPlaying) manager.pause() else manager.play()
            TvPlayerAction.PLAY -> manager.play()
            TvPlayerAction.PAUSE -> manager.pause()
            TvPlayerAction.SEEK_BACK ->
                manager.seekTo((manager.getCurrentPosition() - MEDIA_KEY_SEEK_MS).coerceAtLeast(0L))
            TvPlayerAction.SEEK_FORWARD ->
                manager.seekTo(manager.getCurrentPosition() + MEDIA_KEY_SEEK_MS)
            TvPlayerAction.NEXT -> viewModel.playNext()
            TvPlayerAction.PREVIOUS -> viewModel.playPrevious()
            TvPlayerAction.TOGGLE_CAPTIONS ->
                viewModel.toggleSubtitles(!viewModel.uiState.value.subtitlesEnabled)
            TvPlayerAction.SHOW_TRANSPORT -> {
                pendingFocusTarget = TvPlayerFocusTarget.PLAY_PAUSE
                overlayController.showTransport()
            }
            TvPlayerAction.SHOW_UP_NEXT -> {
                pendingFocusTarget = TvPlayerFocusTarget.UP_NEXT
                overlayController.showTransport()
            }
            TvPlayerAction.SCRUB_BACK -> stepScrub(direction = -1, repeatCount = repeatCount)
            TvPlayerAction.SCRUB_FORWARD -> stepScrub(direction = 1, repeatCount = repeatCount)
            TvPlayerAction.COMMIT_SCRUB -> commitScrub()
        }
        overlayController.onUserInteraction()
    }

    fun handleKeyDown(keyCode: Int, repeatCount: Int): Boolean {
        TvPlayerKeyMapper.map(keyCode)?.let {
            perform(it, repeatCount)
            return true
        }
        return when {
            overlayController.state.value.mode == TvOverlayMode.HIDDEN ->
                TvPlayerKeyMapper.mapDpadWhenControlsHidden(keyCode)?.let {
                    perform(it, repeatCount)
                    true
                } ?: false
            seekBarFocused ->
                TvPlayerKeyMapper.mapDpadWhenSeekBarFocused(keyCode)?.let {
                    perform(it, repeatCount)
                    true
                } ?: run {
                    overlayController.onUserInteraction()
                    false
                }
            else -> {
                overlayController.onUserInteraction()
                false
            }
        }
    }

    // Single restartable delay — replaces the old 1s polling loop.
    LaunchedEffect(
        overlayState.mode,
        overlayState.lastInteractionAtMs,
        playerState.isPlaying,
        scrubUiState.isScrubbing,
    ) {
        val deadline = overlayController.autoHideDeadline(
            isPlaying = playerState.isPlaying,
            isScrubbing = scrubUiState.isScrubbing,
        ) ?: return@LaunchedEffect
        val waitMs = deadline - SystemClock.uptimeMillis()
        if (waitMs > 0) delay(waitMs)
        overlayController.hide()
    }

    // Route focus when the overlay mode changes.
    LaunchedEffect(overlayState.mode) {
        when (overlayState.mode) {
            TvOverlayMode.HIDDEN -> runCatching { rootFocusRequester.requestFocus() }
            TvOverlayMode.TRANSPORT -> runCatching {
                when (pendingFocusTarget) {
                    TvPlayerFocusTarget.SEEK_BAR -> seekBarFocusRequester.requestFocus()
                    TvPlayerFocusTarget.PLAY_PAUSE -> playPauseFocusRequester.requestFocus()
                    TvPlayerFocusTarget.UP_NEXT -> upNextFocusRequester.requestFocus()
                }
            }
            TvOverlayMode.PANEL -> Unit
        }
    }

    LaunchedEffect(video.id) {
        runCatching { rootFocusRequester.requestFocus() }
    }

    BackHandler {
        if (scrubController.current.isScrubbing) {
            scrubController.cancel()
            scrubUiState = scrubController.current
        } else if (!overlayController.onBack()) {
            onClose()
        }
    }

    val title = uiState.streamInfo?.name?.takeIf { it.isNotBlank() } ?: video.title
    val channelName = uiState.streamInfo?.uploaderName?.takeIf { it.isNotBlank() } ?: video.channelName

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                val keyCode = event.nativeKeyEvent.keyCode
                when (event.type) {
                    KeyEventType.KeyUp ->
                        if (scrubController.current.isScrubbing &&
                            (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                        ) {
                            commitScrub()
                            true
                        } else {
                            false
                        }
                    KeyEventType.KeyDown -> handleKeyDown(keyCode, event.nativeKeyEvent.repeatCount)
                    else -> false
                }
            },
    ) {
        VideoPlayerSurface(
            video = video,
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
            modifier = Modifier.fillMaxSize(),
        )

        val seekBarMarks = remember(uiState.chapters, sponsorSegments, playerState.isPrepared, playerState.currentVideoId) {
            TvSeekBarMarks.from(
                chapterStartSeconds = uiState.chapters.map { it.startTimeSeconds },
                sponsorSegments = sponsorSegments,
                durationMs = manager.getDuration(),
            ).takeIf { !it.isEmpty() }
        }

        TvPlayerOverlay(
            visible = overlayState.mode != TvOverlayMode.HIDDEN,
            title = title,
            channelName = channelName,
            isLive = playerState.isLive,
        ) {
            if (sponsorSegments.isNotEmpty()) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TvSponsorSkipButton(
                        segments = sponsorSegments,
                        positionProvider = { manager.getCurrentPosition().coerceAtLeast(0L) },
                        onSkipTo = manager::seekTo,
                    )
                }
            }
            TvSeekBar(
                positionProvider = { manager.getCurrentPosition().coerceAtLeast(0L) },
                durationProvider = { manager.getDuration().coerceAtLeast(0L) },
                bufferedFractionProvider = { manager.playerState.value.bufferedPercentage / 100f },
                scrubTargetMs = scrubUiState.takeIf { it.isScrubbing }?.targetMs,
                active = overlayState.mode != TvOverlayMode.HIDDEN,
                focusRequester = seekBarFocusRequester,
                onFocusChanged = { seekBarFocused = it },
                marks = seekBarMarks,
            )
            TvTransportRow(
                isPlaying = playerState.isPlaying,
                hasPrevious = playerState.hasPrevious,
                hasNext = playerState.hasNext,
                subtitlesAvailable = uiState.subtitles.isNotEmpty(),
                subtitlesEnabled = uiState.subtitlesEnabled,
                autoplayEnabled = uiState.autoplayEnabled,
                isLooping = playerState.isLooping,
                onPrevious = { perform(TvPlayerAction.PREVIOUS) },
                onTogglePlayback = { perform(TvPlayerAction.TOGGLE_PLAYBACK) },
                onNext = { perform(TvPlayerAction.NEXT) },
                onToggleCaptions = { perform(TvPlayerAction.TOGGLE_CAPTIONS) },
                onToggleAutoplay = { viewModel.toggleAutoplay(!uiState.autoplayEnabled) },
                onToggleLoop = { viewModel.toggleLoop(!playerState.isLooping) },
                onClose = onClose,
                onOpenPanel = { panel ->
                    overlayController.openPanel(panel)
                },
                isLive = playerState.isLive,
                playPauseFocusRequester = playPauseFocusRequester,
            )
            TvUpNextRail(
                queue = queueVideos,
                currentQueueIndex = currentQueueIndex,
                relatedVideos = uiState.relatedVideos,
                onVideoClick = ::playFromPlayer,
                firstItemFocusRequester = upNextFocusRequester,
            )
        }

        TvPlayerPanelsHost(
            activePanel = overlayState.activePanel.takeIf { overlayState.mode == TvOverlayMode.PANEL },
            video = video,
            viewModel = viewModel,
            manager = manager,
            onOpenPanel = overlayController::openPanel,
            onClosePanel = overlayController::closePanel,
            onPlayVideo = ::playFromPlayer,
            onSeekTo = manager::seekTo,
        )

        TvAutoplayCountdownCard(
            countdown = autoplayCountdown,
            onPlayNow = manager::skipAutoplayCountdown,
            onCancel = manager::cancelAutoplayCountdown,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(LocalTvDimens.current.overscanHorizontal),
        )

        if (uiState.isLoading || playerState.isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        uiState.error?.let { error ->
            Surface(
                modifier = Modifier
                    .align(Alignment.Center)
                    .widthIn(max = 560.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                Column(
                    modifier = Modifier.padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    uiState.errorHint?.let { hint ->
                        Text(
                            text = hint,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TvButton(
                        text = stringResource(R.string.retry),
                        onClick = viewModel::retryLoadVideo,
                    )
                }
            }
        }
    }
}

private const val MEDIA_KEY_SEEK_MS = 10_000L
