package io.github.aedev.flow.ui.components.musicplayer

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.SleepTimerManager
import io.github.aedev.flow.service.Media3MusicService
import io.github.aedev.flow.ui.components.MusicQuickActionsSheet
import io.github.aedev.flow.ui.components.SleepTimerSheet
import io.github.aedev.flow.ui.screens.music.AddToPlaylistDialog
import io.github.aedev.flow.ui.screens.music.CreatePlaylistDialog
import io.github.aedev.flow.ui.screens.music.MusicPlayerViewModel
import io.github.aedev.flow.ui.screens.music.MusicTrack
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val PlayerHorizontalPadding = 28.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FullMusicPlayerContent(
    track: MusicTrack,
    isPlayerSheetExpanded: Boolean,
    palette: MusicPaletteColors,
    backgroundStyle: MusicPlayerBackgroundStyle,
    hideArtwork: Boolean,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val positionState = viewModel.currentPositionMs.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val colorScheme = MaterialTheme.colorScheme

    val thumbnailUrl = uiState.currentTrack?.highResThumbnailUrl ?: track.highResThumbnailUrl
    var showMoreOptions by remember { mutableStateOf(false) }
    var showAudioSettings by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
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

    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }

    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.showCreatePlaylistDialog(false) },
            onConfirm = { name, desc ->
                viewModel.createPlaylist(name, desc, uiState.currentTrack)
            },
        )
    }

    if (uiState.showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = uiState.playlists,
            onDismiss = { viewModel.showAddToPlaylistDialog(false) },
            onSelectPlaylist = { playlistId ->
                viewModel.addToPlaylist(playlistId)
            },
            onCreateNew = {
                viewModel.showAddToPlaylistDialog(false)
                viewModel.showCreatePlaylistDialog(true)
            },
        )
    }

    if (showMoreOptions && uiState.currentTrack != null) {
        MusicQuickActionsSheet(
            track = uiState.currentTrack!!,
            onDismiss = { showMoreOptions = false },
            onViewArtist = { channelId ->
                if (channelId.isNotEmpty()) {
                    onArtistClick(channelId)
                }
            },
            onViewAlbum = { albumId ->
                if (albumId.isNotEmpty()) {
                    onAlbumClick(albumId)
                }
            },
            onShare = {
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, uiState.currentTrack!!.title)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            context.getString(
                                R.string.share_message_template,
                                uiState.currentTrack!!.title,
                                uiState.currentTrack!!.artist,
                                uiState.currentTrack!!.videoId,
                            ),
                        )
                    }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_song)))
            },
            onInfoClick = { showInfoDialog = true },
            onAudioEffectsClick = { showAudioSettings = true },
            onSleepTimerClick = { showSleepTimer = true },
            showPlaylistDialogs = false,
        )
    }

    if (showAudioSettings) {
        AudioSettingsSheet(
            onDismiss = { showAudioSettings = false },
        )
    }

    if (showInfoDialog && uiState.currentTrack != null) {
        TrackInfoDialog(
            track = uiState.currentTrack!!,
            onDismiss = { showInfoDialog = false },
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

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val navBarPx = with(density) { navBarPadding.toPx() }

        val reservedHeight = statusBarPadding + 56.dp + 32.dp + 32.dp + 20.dp + 72.dp + 64.dp + navBarPadding
        val availableForArtwork = screenHeight - reservedHeight
        val artworkMaxWidth = screenWidth - (PlayerHorizontalPadding * 2)
        val artworkSize = min(availableForArtwork, artworkMaxWidth).coerceAtLeast(160.dp)

        val maxHeightPx = constraints.maxHeight.toFloat()
        val queueHiddenY = maxHeightPx + navBarPx
        val queueExpandedY = 0f
        val safeHiddenY = queueHiddenY.coerceAtLeast(queueExpandedY)

        val queueOffsetY = remember { Animatable(safeHiddenY) }
        LaunchedEffect(isPlayerSheetExpanded, safeHiddenY) {
            if (!isPlayerSheetExpanded) {
                showQueueSheet = false
                showLyricsSheet = false
                queueOffsetY.snapTo(safeHiddenY)
            }
        }
        LaunchedEffect(queueExpandedY, safeHiddenY) {
            queueOffsetY.updateBounds(lowerBound = queueExpandedY, upperBound = safeHiddenY)
            if (!showQueueSheet) {
                queueOffsetY.snapTo(safeHiddenY)
            } else {
                queueOffsetY.snapTo(queueOffsetY.value.coerceIn(queueExpandedY, safeHiddenY))
            }
        }
        val queueSheetActive = isPlayerSheetExpanded && showQueueSheet
        val clampedQueueOffset =
            if (!queueSheetActive) {
                safeHiddenY
            } else {
                queueOffsetY.value.coerceIn(queueExpandedY, safeHiddenY)
            }

        val queueFraction =
            if (safeHiddenY != queueExpandedY) {
                (1f - ((clampedQueueOffset - queueExpandedY) / (safeHiddenY - queueExpandedY))).coerceIn(0f, 1f)
            } else {
                0f
            }

        val mainAlpha = (1f - (queueFraction / 0.4f)).coerceIn(0f, 1f)
        val artworkScale = 1f - (queueFraction * 0.10f)

        suspend fun animateQueueSheetTo(
            target: Float,
            initialVelocity: Float = 0f,
        ) {
            if (target < safeHiddenY && isPlayerSheetExpanded) showQueueSheet = true
            queueOffsetY.stop()
            queueOffsetY.animateTo(
                targetValue = target.coerceIn(queueExpandedY, safeHiddenY),
                initialVelocity = initialVelocity,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
            if (target >= safeHiddenY) {
                queueOffsetY.snapTo(safeHiddenY)
                showQueueSheet = false
            }
        }

        suspend fun settleQueueSheet(
            velocity: Float,
            totalDrag: Float = 0f,
        ) {
            val distance = safeHiddenY - queueExpandedY
            val progress =
                if (distance > 0f) {
                    ((queueOffsetY.value - queueExpandedY) / distance).coerceIn(0f, 1f)
                } else {
                    1f
                }
            val dragThresholdPx =
                (distance * 0.05f).coerceIn(
                    with(density) { 14.dp.toPx() },
                    with(density) { 56.dp.toPx() },
                )
            val target =
                when {
                    velocity < -520f -> queueExpandedY
                    velocity > 450f -> safeHiddenY
                    totalDrag < -dragThresholdPx -> queueExpandedY
                    totalDrag > dragThresholdPx -> safeHiddenY
                    progress < 0.5f -> queueExpandedY
                    else -> safeHiddenY
                }
            animateQueueSheetTo(target, velocity)
        }

        fun animateQueueSheet(target: Float) {
            scope.launch { animateQueueSheetTo(target) }
        }

        BackHandler(enabled = queueSheetActive && queueFraction > 0.05f) {
            animateQueueSheet(safeHiddenY)
        }

        PlayerBackground(
            thumbnailUrl = backgroundThumbnailUrl,
            style = backgroundStyle,
            paletteBaseColor = palette.base,
            paletteAccentColor = palette.accent,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = mainAlpha }
                    .pointerInput(isPlayerSheetExpanded, queueExpandedY, safeHiddenY) {
                        if (!isPlayerSheetExpanded) return@pointerInput
                        // Claims only clearly upward drags (queue pull-up); anything else stays
                        // unconsumed so the sheet's collapse drag underneath keeps working.
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val velocityTracker = VelocityTracker()
                            var totalDy = 0f
                            var claimed = false
                            while (true) {
                                val event = awaitPointerEvent()
                                val change =
                                    event.changes.firstOrNull { it.id == down.id }
                                        ?: event.changes.firstOrNull { it.pressed }
                                        ?: break
                                if (!change.pressed) {
                                    if (claimed) {
                                        val velocity = velocityTracker.calculateVelocity().y
                                        val committedDrag = totalDy
                                        scope.launch { settleQueueSheet(velocity, committedDrag) }
                                    }
                                    break
                                }
                                val dy = change.positionChange().y
                                totalDy += dy
                                if (!claimed) {
                                    if (change.isConsumed) {
                                        break
                                    }
                                    if (totalDy <= -viewConfiguration.touchSlop) {
                                        claimed = true
                                        showQueueSheet = true
                                        velocityTracker.resetTracking()
                                    } else if (totalDy >= viewConfiguration.touchSlop) {
                                        break
                                    } else {
                                        continue
                                    }
                                }
                                change.consume()
                                velocityTracker.addPointerInputChange(change)
                                scope.launch {
                                    queueOffsetY.snapTo(
                                        (queueOffsetY.value + dy).coerceIn(queueExpandedY, safeHiddenY),
                                    )
                                }
                            }
                        }
                    },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                PlayerTopBar(
                    playingFrom = uiState.playingFrom,
                    modifier = Modifier.statusBarsPadding(),
                    contentColor = colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                // Under the immersive background the full-bleed art IS the artwork, so the card
                // vanishes (no placeholder, no shadow) while keeping its swipe-to-skip gestures.
                val immersiveBackground = backgroundStyle == MusicPlayerBackgroundStyle.IMMERSIVE
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = PlayerHorizontalPadding)
                            .size(artworkSize)
                            .graphicsLayer {
                                scaleX = artworkScale
                                scaleY = artworkScale
                            }.then(
                                if (immersiveBackground) {
                                    Modifier
                                } else {
                                    Modifier.shadow(
                                        elevation = if (uiState.isPlaying) 24.dp else 8.dp,
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                },
                            ).clip(RoundedCornerShape(8.dp)),
                ) {
                    PlayerArtwork(
                        thumbnailUrl = thumbnailUrl,
                        previousThumbnailUrl = previousTrack?.highResThumbnailUrl,
                        nextThumbnailUrl = nextTrack?.highResThumbnailUrl,
                        previewDirection = previewDirection,
                        isVideoMode = false,
                        isLoading = uiState.isLoading,
                        hideArtwork = hideArtwork || immersiveBackground,
                        hiddenArtworkColor =
                            if (immersiveBackground) Color.Unspecified else colorScheme.surfaceContainerHigh,
                        player = EnhancedMusicPlayerManager.player,
                        onSkipPrevious = { viewModel.skipToPrevious() },
                        onSkipNext = { viewModel.skipToNext() },
                        modifier = Modifier.fillMaxSize(),
                        onDragPreviewChange = { artworkDragPreview = it },
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    AnimatedContent(
                        targetState = previewTrack?.title ?: uiState.currentTrack?.title ?: track.title,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "title",
                    ) { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier.basicMarquee(
                                    iterations = 1,
                                    initialDelayMillis = 3000,
                                    velocity = 30.dp,
                                ),
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    AnimatedContent(
                        targetState = previewTrack?.artist ?: uiState.currentTrack?.artist ?: track.artist,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "artist",
                    ) { artist ->
                        Text(
                            text = artist,
                            style = MaterialTheme.typography.bodyLarge,
                            color = colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier.clickable {
                                    uiState.currentTrack
                                        ?.channelId
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.let { onArtistClick(it) }
                                },
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                PlayerMainActionButtons(
                    isLiked = uiState.isLiked,
                    isDownloaded = uiState.downloadedTrackIds.contains(uiState.currentTrack?.videoId),
                    onLikeClick = { viewModel.toggleLike() },
                    onDownloadClick = { viewModel.downloadTrack() },
                    onAddToPlaylist = { viewModel.showAddToPlaylistDialog(true) },
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            PlayerProgressSlider(
                positionProvider = { positionState.value },
                duration = uiState.duration,
                onSeekTo = { viewModel.seekTo(it) },
                // The tree stays composed while collapsed (warm for a jank-free expand), so the
                // squiggly/wavy per-frame wave animations must stop when nobody can see them —
                // they otherwise burn a frame budget for the whole background-listening session.
                isPlaying = uiState.isPlaying && isPlayerSheetExpanded,
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
            )

            Spacer(modifier = Modifier.height(26.dp))

            PlayerPlaybackControls(
                isPlaying = uiState.isPlaying,
                isBuffering = uiState.isBuffering,
                onPreviousClick = { viewModel.skipToPrevious() },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onNextClick = { viewModel.skipToNext() },
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding),
                onPreviewDirectionChange = { previewDirection = it },
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerHorizontalPadding),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlayerSecondaryActions(
                    lyricsActive = showLyricsSheet,
                    shuffleEnabled = uiState.shuffleEnabled,
                    repeatMode = uiState.repeatMode,
                    onLyricsClick = {
                        uiState.currentTrack?.let { viewModel.ensureLyricsLoaded(it) }
                        showLyricsSheet = true
                    },
                    onShuffleClick = { viewModel.toggleShuffle() },
                    onRepeatClick = { viewModel.toggleRepeat() },
                    onQueueClick = {
                        if (isPlayerSheetExpanded) {
                            showQueueSheet = true
                            animateQueueSheet(queueExpandedY)
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(
                    onClick = { showMoreOptions = true },
                    modifier = Modifier.size(42.dp),
                    shape = CircleShape,
                    colors =
                        IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = colorScheme.secondaryContainer,
                            contentColor = colorScheme.onSecondaryContainer,
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(navBarPadding + 16.dp))
        }

        val queueCornerRadius = 28.dp * (1f - queueFraction)

        var handleDragActive by remember { mutableStateOf(false) }
        var handleDragTotal by remember { mutableFloatStateOf(0f) }
        val queueDraggableState =
            rememberDraggableState { delta ->
                handleDragTotal += delta
                scope.launch {
                    // A delta queued behind the release must not cancel the settle animation.
                    if (handleDragActive) {
                        queueOffsetY.snapTo((queueOffsetY.value + delta).coerceIn(queueExpandedY, safeHiddenY))
                    }
                }
            }

        val queueDragHandleModifier =
            Modifier.draggable(
                orientation = Orientation.Vertical,
                state = queueDraggableState,
                onDragStarted = {
                    handleDragActive = true
                    handleDragTotal = 0f
                    scope.launch { queueOffsetY.stop() }
                },
                onDragStopped = { velocity ->
                    handleDragActive = false
                    settleQueueSheet(velocity, handleDragTotal)
                },
            )

        val sheetNestedScrollConnection =
            remember(queueExpandedY, safeHiddenY) {
                object : NestedScrollConnection {
                    override fun onPreScroll(
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source == NestedScrollSource.UserInput && available.y < 0f && queueOffsetY.value > queueExpandedY) {
                            val toMove = maxOf(available.y, queueExpandedY - queueOffsetY.value)
                            scope.launch {
                                queueOffsetY.snapTo((queueOffsetY.value + toMove).coerceIn(queueExpandedY, safeHiddenY))
                            }
                            return Offset(0f, toMove)
                        }
                        return Offset.Zero
                    }

                    override fun onPostScroll(
                        consumed: Offset,
                        available: Offset,
                        source: NestedScrollSource,
                    ): Offset {
                        if (source == NestedScrollSource.UserInput && available.y > 0f && queueOffsetY.value < safeHiddenY) {
                            val toMove = minOf(available.y, safeHiddenY - queueOffsetY.value)
                            scope.launch {
                                queueOffsetY.snapTo((queueOffsetY.value + toMove).coerceIn(queueExpandedY, safeHiddenY))
                            }
                            return Offset(0f, toMove)
                        }
                        return Offset.Zero
                    }

                    override suspend fun onPreFling(available: Velocity): Velocity {
                        if (queueOffsetY.value > queueExpandedY && queueOffsetY.value < safeHiddenY) {
                            settleQueueSheet(available.y)
                            return available
                        }
                        return Velocity.Zero
                    }

                    override suspend fun onPostFling(
                        consumed: Velocity,
                        available: Velocity,
                    ): Velocity {
                        if (queueOffsetY.value > queueExpandedY && queueOffsetY.value < safeHiddenY) {
                            settleQueueSheet(available.y)
                            return available
                        }
                        return Velocity.Zero
                    }
                }
            }

        if (queueSheetActive || clampedQueueOffset < safeHiddenY - 1f) {
            Box(
                modifier =
                    Modifier
                        .offset { IntOffset(0, clampedQueueOffset.roundToInt()) }
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .shadow(
                            elevation = (18.dp * queueFraction),
                            shape = RoundedCornerShape(topStart = queueCornerRadius, topEnd = queueCornerRadius),
                            clip = false,
                        ).nestedScroll(sheetNestedScrollConnection),
            ) {
                QueueSheet(
                    sheetCornerRadius = queueCornerRadius,
                    queue = uiState.queue,
                    radioTracks = uiState.autoplaySuggestions,
                    currentIndex = uiState.currentQueueIndex,
                    isRadioLoading = uiState.isRelatedLoading,
                    endlessRadioEnabled = uiState.endlessRadioEnabled,
                    shuffleEnabled = uiState.shuffleEnabled,
                    repeatMode = uiState.repeatMode,
                    downloadedTrackIds = uiState.downloadedTrackIds,
                    onTrackClick = { viewModel.playFromQueue(it) },
                    onMoveTrack = { from, to -> viewModel.moveTrack(from, to) },
                    onPlayNextFromQueue = { viewModel.playNextFromQueuePosition(it) },
                    onSendQueueTrackToEnd = { viewModel.moveQueueTrackToEnd(it) },
                    onRadioTrackClick = { viewModel.loadAndPlayTrack(it) },
                    onPlayNextRadio = { viewModel.playNextFromRadio(it) },
                    onAddRadioToQueue = { viewModel.addRadioTrackToQueue(it) },
                    onToggleEndlessRadio = { viewModel.setEndlessRadioEnabled(it) },
                    onShuffleQueue = { viewModel.toggleShuffle() },
                    onCycleRepeat = { viewModel.toggleRepeat() },
                    dragHandleModifier = queueDragHandleModifier,
                )
            }
        }

        MusicLyricsSheet(
            visible = showLyricsSheet,
            retainContent = isPlayerSheetExpanded,
            backdropBaseColor = palette.base,
            accentColor = colorScheme.primary,
            trackTitle = uiState.currentTrack?.title ?: track.title,
            trackArtist = uiState.currentTrack?.artist ?: track.artist,
            artworkUrl = thumbnailUrl,
            isPlaying = uiState.isPlaying,
            isBuffering = uiState.isBuffering,
            lyrics = uiState.lyrics,
            syncedLyrics = uiState.syncedLyrics,
            // Raw position — the panel's own sync loops apply syncOffsetMs; baking the
            // offset in here double-counted (and the loops ignored it anyway, #offset fix).
            positionProvider = { positionState.value },
            isLoading = uiState.isLyricsLoading,
            providerName = uiState.lyricsProviderName,
            alignPref = uiState.lyricsTextAlign,
            syncOffsetMs = uiState.lyricsSyncOffsetMs,
            candidates = uiState.lyricsCandidates,
            isBrowsing = uiState.isBrowsingLyrics,
            onSeekTo = { viewModel.seekTo((it - uiState.lyricsSyncOffsetMs).coerceAtLeast(0L)) },
            onRefresh = { viewModel.refreshLyrics() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onAlignChange = { viewModel.setLyricsTextAlign(it) },
            onAdjustOffset = { viewModel.adjustLyricsSyncOffset(it) },
            onResetOffset = { viewModel.resetLyricsSyncOffset() },
            onBrowseSources = { viewModel.browseLyricsCandidates() },
            onCancelBrowse = { viewModel.cancelLyricsBrowse() },
            onSelectCandidate = { viewModel.applyLyricsCandidate(it) },
            onApplyEditedLyrics = { viewModel.applyEditedLyrics(it) },
            onDismiss = { showLyricsSheet = false },
        )

        // Hosted here rather than at app level so it picks up the palette-derived scheme.
        if (showSleepTimer) {
            SleepTimerSheet(onDismiss = { showSleepTimer = false })
        }
    }
}
