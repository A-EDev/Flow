package io.github.aedev.flow.ui.components.musicplayer.full

import android.content.Intent
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle
import io.github.aedev.flow.data.localmedia.LocalMediaIds
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.SleepTimerManager
import io.github.aedev.flow.service.Media3MusicService
import io.github.aedev.flow.ui.components.layout.navigation.LocalMediaNavigator
import io.github.aedev.flow.ui.components.music.sheet.MusicQuickActionsSheet
import io.github.aedev.flow.ui.components.music.sheet.SaveSongSheet
import io.github.aedev.flow.ui.components.musicplayer.common.SkipDirection
import io.github.aedev.flow.ui.components.musicplayer.controls.PlayerPlaybackControls
import io.github.aedev.flow.ui.components.musicplayer.controls.PlayerProgressSlider
import io.github.aedev.flow.ui.components.musicplayer.lyrics.DEFAULT_LYRICS_TEXT_SIZE
import io.github.aedev.flow.ui.components.musicplayer.lyrics.LARGE_LYRICS_TEXT_SIZE
import io.github.aedev.flow.ui.components.musicplayer.lyrics.lyricsBackdrop
import io.github.aedev.flow.ui.components.musicplayer.queue.QueueActions
import io.github.aedev.flow.ui.components.musicplayer.queue.QueueList
import io.github.aedev.flow.ui.components.musicplayer.queue.QueuePullUpSheet
import io.github.aedev.flow.ui.components.musicplayer.queue.QueueSheet
import io.github.aedev.flow.ui.components.musicplayer.queue.queuePullUpGesture
import io.github.aedev.flow.ui.components.musicplayer.queue.rememberQueuePullUpState
import io.github.aedev.flow.ui.components.musicplayer.sheet.AudioSettingsSheet
import io.github.aedev.flow.ui.components.shared.MediaPalette
import io.github.aedev.flow.ui.components.shared.MediaSleepTimerSheet
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.sharedMusicPlayerViewModel
import io.github.aedev.flow.ui.utils.LocalWindowIsLandscape
import io.github.aedev.flow.ui.utils.LocalWindowSizeClass

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullMusicPlayerContent(
    track: MusicTrack,
    isPlayerSheetExpanded: Boolean,
    palette: MediaPalette,
    backgroundStyle: MusicPlayerBackgroundStyle,
    hideArtwork: Boolean,
    // The transport, seek bar, like and download and the action row draw with this scheme.
    controlScheme: ColorScheme = MaterialTheme.colorScheme,
    viewModel: MusicPlayerViewModel = sharedMusicPlayerViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val positionState = viewModel.currentPositionMs.collectAsState()
    val context = LocalContext.current
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme
    val navigator = LocalMediaNavigator.current

    val thumbnailUrl = uiState.currentTrack?.highResThumbnailUrl ?: track.highResThumbnailUrl
    var showMoreOptions by remember { mutableStateOf(false) }
    var showSaveSheet by remember { mutableStateOf(false) }
    var showAudioSettings by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var previewDirection by remember { mutableStateOf<SkipDirection?>(null) }
    val musicPlayer by EnhancedMusicPlayerManager.playerInstance.collectAsState()

    val previousTrack = uiState.queue.getOrNull(uiState.currentQueueIndex - 1)
    val nextTrack = uiState.queue.getOrNull(uiState.currentQueueIndex + 1)
    val previewTrack =
        when (previewDirection) {
            SkipDirection.NEXT -> nextTrack
            SkipDirection.PREVIOUS -> previousTrack
            null -> null
        }

    // Both the hold-press preview and the artwork drag carousel swap the background art too,
    // so the immersive/blur backdrops track whatever cover the user is currently peeking at.
    var artworkDragPreview by remember { mutableStateOf<SkipDirection?>(null) }
    LaunchedEffect(thumbnailUrl) { artworkDragPreview = null }
    val backgroundPreviewTrack =
        when (artworkDragPreview ?: previewDirection) {
            SkipDirection.NEXT -> nextTrack
            SkipDirection.PREVIOUS -> previousTrack
            null -> null
        }
    val backgroundThumbnailUrl = backgroundPreviewTrack?.highResThumbnailUrl ?: thumbnailUrl

    LaunchedEffect(musicPlayer) {
        SleepTimerManager.attachToPlayer(
            player = musicPlayer,
        ) {
            EnhancedMusicPlayerManager.player?.pause()
        }
    }

    LaunchedEffect(Unit) {
        SleepTimerManager.attachExitCallback {
            EnhancedMusicPlayerManager.stop()
            context.stopService(Intent(context, Media3MusicService::class.java))
            (context as? android.app.Activity)?.finishAndRemoveTask()
        }
    }

    var showLyricsSheet by remember { mutableStateOf(false) }

    val saveTrack = uiState.currentTrack
    if (showSaveSheet && saveTrack != null) {
        SaveSongSheet(
            track = saveTrack,
            onDismiss = { showSaveSheet = false },
        )
    }

    if (showMoreOptions && uiState.currentTrack != null) {
        MusicQuickActionsSheet(
            track = uiState.currentTrack!!,
            onDismiss = { showMoreOptions = false },
            onAudioEffectsClick = { showAudioSettings = true },
            onSleepTimerClick = { showSleepTimer = true },
        )
    }

    if (showAudioSettings) {
        AudioSettingsSheet(
            onDismiss = { showAudioSettings = false },
        )
    }

    LaunchedEffect(track.videoId) {
        viewModel.fetchRelatedContent(track.videoId)
        val managerTrack = EnhancedMusicPlayerManager.currentTrack.value
        val isManagerPlaying = EnhancedMusicPlayerManager.isPlaying()

        if (managerTrack?.videoId == track.videoId && (isManagerPlaying || managerTrack != null)) {
            viewModel.ensureLyricsLoaded(track)
        } else {
            viewModel.loadAndPlayTrack(track)
        }
    }

    if (isPlayerSheetExpanded) {
        DisposableEffect(Unit) {
            EnhancedMusicPlayerManager.acquirePreciseProgress()
            onDispose { EnhancedMusicPlayerManager.releasePreciseProgress() }
        }
    }

    val queueActions =
        remember(viewModel) {
            QueueActions(
                onTrackClick = { viewModel.playFromQueue(it) },
                onMoveTrack = { from, to -> viewModel.moveTrack(from, to) },
                onPlayNextFromQueue = { viewModel.playNextFromQueuePosition(it) },
                onSendQueueTrackToEnd = { viewModel.moveQueueTrackToEnd(it) },
                onRadioTrackClick = { viewModel.playRadioTrack(it) },
                onPlayNextRadio = { viewModel.playNextFromRadio(it) },
                onAddRadioToQueue = { viewModel.addRadioTrackToQueue(it) },
                onToggleEndlessRadio = { viewModel.setEndlessRadioEnabled(it) },
                onShuffleQueue = { viewModel.toggleShuffle() },
                onCycleRepeat = { viewModel.toggleRepeat() },
            )
        }

    val immersiveBackground = backgroundStyle == MusicPlayerBackgroundStyle.IMMERSIVE
    val displayTitle = previewTrack?.title ?: uiState.currentTrack?.title ?: track.title
    val displayArtist = previewTrack?.artist ?: uiState.currentTrack?.artist ?: track.artist

    val panes = rememberPlayerPanes()
    val layout =
        musicPlayerLayoutFor(LocalWindowSizeClass.current, LocalWindowIsLandscape.current).let {
            // The side pane needs two partitions; a window the scaffold keeps to one splits instead.
            if (it == MusicPlayerLayout.WIDE && !panes.showsSidePane) MusicPlayerLayout.SPLIT else it
        }
    val isWide = layout == MusicPlayerLayout.WIDE
    var sidePaneTab by rememberSaveable { mutableStateOf(PlayerSidePaneTab.UP_NEXT) }
    val selectSidePaneTab: (PlayerSidePaneTab) -> Unit = { tab ->
        if (tab == PlayerSidePaneTab.LYRICS) uiState.currentTrack?.let { viewModel.ensureLyricsLoaded(it) }
        sidePaneTab = tab
    }
    val openLyricsSheet = {
        uiState.currentTrack?.let { viewModel.ensureLyricsLoaded(it) }
        showLyricsSheet = true
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarPx = with(density) { navBarPadding.toPx() }

        val reservedHeight = statusBarPadding + 56.dp + 32.dp + 32.dp + 20.dp + 72.dp + 64.dp + navBarPadding
        val availableForArtwork = maxHeight - reservedHeight
        val columnWidth = if (layout == MusicPlayerLayout.PORTRAIT_LARGE) min(maxWidth, PortraitLargeMaxWidth) else maxWidth
        val artworkMaxWidth = columnWidth - (PlayerHorizontalPadding * 2)
        val artworkSize = min(availableForArtwork, artworkMaxWidth).coerceAtLeast(160.dp)

        val queueState =
            rememberQueuePullUpState(
                enabled = isPlayerSheetExpanded && !isWide,
                hiddenY = constraints.maxHeight.toFloat() + navBarPx,
            )
        LaunchedEffect(isPlayerSheetExpanded) {
            if (!isPlayerSheetExpanded) showLyricsSheet = false
        }

        val slots =
            NowPlayingSlots(
                topBar = { modifier ->
                    PlayerTopBar(
                        playingFrom = uiState.playingFrom,
                        modifier = modifier,
                        contentColor = colorScheme.onSurface,
                    )
                },
                artwork = { modifier ->
                    PlayerArtworkFrame(
                        immersive = immersiveBackground,
                        isPlaying = uiState.isPlaying,
                        modifier = modifier,
                    ) {
                        PlayerArtwork(
                            thumbnailUrl = thumbnailUrl,
                            previousThumbnailUrl = previousTrack?.highResThumbnailUrl,
                            nextThumbnailUrl = nextTrack?.highResThumbnailUrl,
                            previewDirection = previewDirection,
                            // Spinners in the warm, collapsed tree animate at alpha 0 otherwise.
                            isLoading = uiState.isLoading && isPlayerSheetExpanded,
                            hideArtwork = hideArtwork || immersiveBackground,
                            hiddenArtworkColor =
                                if (immersiveBackground) Color.Unspecified else colorScheme.surfaceContainerHigh,
                            onSkipPrevious = { viewModel.skipToPrevious() },
                            onSkipNext = { viewModel.skipToNext() },
                            modifier = Modifier.fillMaxSize(),
                            onDragPreviewChange = { artworkDragPreview = it },
                        )
                    }
                },
                header = { modifier ->
                    MaterialTheme(colorScheme = controlScheme) {
                        PlayerTrackHeader(
                            title = displayTitle,
                            artist = displayArtist,
                            onArtistClick = {
                                uiState.currentTrack
                                    ?.channelId
                                    ?.takeIf { it.isNotEmpty() }
                                    ?.let(navigator::openArtist)
                            },
                            animateTitle = isPlayerSheetExpanded,
                            showLibraryActions = !LocalMediaIds.isLocal(uiState.currentTrack?.videoId),
                            isLiked = uiState.isLiked,
                            isDownloaded = uiState.downloadedTrackIds.contains(uiState.currentTrack?.videoId),
                            onLikeClick = { viewModel.toggleLike() },
                            onDownloadClick = { viewModel.downloadTrack() },
                            onAddToPlaylist = { showSaveSheet = true },
                            modifier = modifier,
                        )
                    }
                },
                progress = { modifier ->
                    MaterialTheme(colorScheme = controlScheme) {
                        PlayerProgressSlider(
                            positionProvider = { positionState.value },
                            duration = uiState.duration,
                            onSeekTo = { viewModel.seekTo(it) },
                            // The tree stays composed while collapsed (warm for a jank-free expand), so the
                            // squiggly/wavy per-frame wave animations must stop when nobody can see them —
                            // they otherwise burn a frame budget for the whole background-listening session.
                            isPlaying = uiState.isPlaying && isPlayerSheetExpanded,
                            modifier = modifier,
                        )
                    }
                },
                controls = { modifier ->
                    MaterialTheme(colorScheme = controlScheme) {
                        PlayerPlaybackControls(
                            isPlaying = uiState.isPlaying,
                            isBuffering = uiState.isBuffering && isPlayerSheetExpanded,
                            onPreviousClick = { viewModel.skipToPrevious() },
                            onPlayPauseToggle = { viewModel.togglePlayPause() },
                            onNextClick = { viewModel.skipToNext() },
                            modifier = modifier,
                            onPreviewDirectionChange = { previewDirection = it },
                        )
                    }
                },
                actions = { modifier ->
                    MaterialTheme(colorScheme = controlScheme) {
                        // Wide windows show lyrics and the queue in the side pane, so the buttons pick its tab.
                        PlayerActionRow(
                            lyricsActive = if (isWide) sidePaneTab == PlayerSidePaneTab.LYRICS else showLyricsSheet,
                            shuffleEnabled = uiState.shuffleEnabled,
                            repeatMode = uiState.repeatMode,
                            onLyricsClick = { if (isWide) selectSidePaneTab(PlayerSidePaneTab.LYRICS) else openLyricsSheet() },
                            onShuffleClick = { viewModel.toggleShuffle() },
                            onRepeatClick = { viewModel.toggleRepeat() },
                            onQueueClick = { if (isWide) selectSidePaneTab(PlayerSidePaneTab.UP_NEXT) else queueState.open() },
                            onMoreClick = { showMoreOptions = true },
                            modifier = modifier,
                            queueActive = isWide && sidePaneTab == PlayerSidePaneTab.UP_NEXT,
                        )
                    }
                },
            )

        PlayerBackground(
            thumbnailUrl = backgroundThumbnailUrl,
            style = backgroundStyle,
            paletteBaseColor = palette.base,
            paletteAccentColor = palette.accent,
            artworkAtStart = layout == MusicPlayerLayout.WIDE || layout == MusicPlayerLayout.SPLIT,
        )

        val pullUpQueue = Modifier.queuePullUpGesture(queueState, enabled = isPlayerSheetExpanded && !isWide)
        when (layout) {
            MusicPlayerLayout.COMPACT -> {
                CompactPlayerLayout(slots, artworkSize, queueState::fraction, navBarPadding, pullUpQueue)
            }

            MusicPlayerLayout.PORTRAIT_LARGE -> {
                CompactPlayerLayout(
                    slots = slots,
                    artworkSize = artworkSize,
                    queueFraction = queueState::fraction,
                    bottomInset = navBarPadding,
                    modifier = Modifier.align(Alignment.TopCenter).widthIn(max = PortraitLargeMaxWidth).then(pullUpQueue),
                )
            }

            MusicPlayerLayout.SPLIT -> {
                SplitPlayerLayout(slots, queueState::fraction, pullUpQueue)
            }

            MusicPlayerLayout.WIDE -> {
                val paneLyricsBackdrop = remember(palette.base) { lyricsBackdrop(palette.base) }
                WidePlayerLayout(slots, panes, immersive = immersiveBackground) { modifier ->
                    PlayerSidePane(
                        tab = sidePaneTab,
                        onTabChange = selectSidePaneTab,
                        onOpenFullLyrics = openLyricsSheet,
                        lyricsBackdrop = paneLyricsBackdrop,
                        upNext = {
                            QueueList(
                                queue = uiState.queue,
                                radioTracks = uiState.autoplaySuggestions,
                                currentIndex = uiState.currentQueueIndex,
                                // The warm tree stays composed while collapsed; the row waveform must not animate there.
                                isPlaying = uiState.isPlaying && isPlayerSheetExpanded,
                                isRadioLoading = uiState.isRadioLoading,
                                endlessRadioEnabled = uiState.endlessRadioEnabled,
                                downloadedTrackIds = uiState.downloadedTrackIds,
                                actions = queueActions,
                                clearNavigationBar = false,
                            )
                        },
                        lyrics = {
                            NowPlayingLyricsPane(
                                uiState = uiState,
                                viewModel = viewModel,
                                accentColor = colorScheme.primary,
                                backdropColor = paneLyricsBackdrop,
                                positionState = positionState,
                                active = isPlayerSheetExpanded && !showLyricsSheet,
                            )
                        },
                        modifier = modifier,
                    )
                }
            }
        }

        val queueSheetModifier =
            if (layout == MusicPlayerLayout.PORTRAIT_LARGE) {
                Modifier.align(Alignment.TopCenter).widthIn(max = PortraitLargeQueueMaxWidth)
            } else {
                Modifier
            }
        QueuePullUpSheet(queueState, queueSheetModifier) { cornerRadius, dragHandleModifier ->
            QueueSheet(
                sheetCornerRadius = cornerRadius,
                queue = uiState.queue,
                radioTracks = uiState.autoplaySuggestions,
                currentIndex = uiState.currentQueueIndex,
                isPlaying = uiState.isPlaying,
                isRadioLoading = uiState.isRadioLoading,
                endlessRadioEnabled = uiState.endlessRadioEnabled,
                shuffleEnabled = uiState.shuffleEnabled,
                repeatMode = uiState.repeatMode,
                downloadedTrackIds = uiState.downloadedTrackIds,
                actions = queueActions,
                dragHandleModifier = dragHandleModifier,
            )
        }

        NowPlayingLyricsSheet(
            uiState = uiState,
            viewModel = viewModel,
            visible = showLyricsSheet,
            retainContent = isPlayerSheetExpanded,
            backdropBaseColor = palette.base,
            accentColor = colorScheme.primary,
            fallbackTitle = track.title,
            fallbackArtist = track.artist,
            artworkUrl = thumbnailUrl,
            positionState = positionState,
            baseTextSize =
                if (layout == MusicPlayerLayout.WIDE ||
                    layout == MusicPlayerLayout.PORTRAIT_LARGE
                ) {
                    LARGE_LYRICS_TEXT_SIZE
                } else {
                    DEFAULT_LYRICS_TEXT_SIZE
                },
            onDismiss = { showLyricsSheet = false },
        )

        // Hosted here rather than at app level so it picks up the palette-derived scheme.
        if (showSleepTimer) {
            MediaSleepTimerSheet(onDismiss = { showSleepTimer = false })
        }
    }
}

/** Upright tablets keep the phone column, centred at this width. */
private val PortraitLargeMaxWidth = 600.dp
private val PortraitLargeQueueMaxWidth = 640.dp
