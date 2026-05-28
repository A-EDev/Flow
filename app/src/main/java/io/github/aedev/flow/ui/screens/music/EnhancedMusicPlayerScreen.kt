package io.github.aedev.flow.ui.screens.music


import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.min
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.animation.animateColorAsState
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap

import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.SleepTimerManager
import io.github.aedev.flow.ui.components.SleepTimerSheet
import io.github.aedev.flow.ui.screens.music.player.*
import io.github.aedev.flow.ui.components.MusicQuickActionsSheet
import androidx.compose.foundation.clickable

private val PlayerHorizontalPadding = 28.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMusicPlayerScreen(
    track: MusicTrack,
    onBackClick: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    isPlayerSheetExpanded: Boolean = true,
    viewModel: MusicPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val backgroundStyle by playerPreferences.musicPlayerBackgroundStyle.collectAsState(
        initial = MusicPlayerBackgroundStyle.BLUR_GRADIENT
    )
    
    val isVideoMode = false 
    var sheetColor by remember { mutableStateOf<Color?>(null) }
    var sheetAccentColor by remember { mutableStateOf<Color?>(null) }
    
    val thumbnailUrl = uiState.currentTrack?.highResThumbnailUrl ?: track.highResThumbnailUrl
    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl.isNotEmpty()) {
            val request = ImageRequest.Builder(context)
                .data(thumbnailUrl)
                .allowHardware(false)
                .size(128)
                .build()
            val result = context.imageLoader.execute(request)
            if (result is SuccessResult) {
                val bitmap = result.drawable.toBitmap()
                val palette = Palette.from(bitmap).generate()
                val bgSwatch = palette.darkMutedSwatch ?: palette.darkVibrantSwatch ?: palette.dominantSwatch
                val accentSwatch = palette.vibrantSwatch ?: palette.lightVibrantSwatch ?: palette.lightMutedSwatch
                sheetColor = bgSwatch?.let { Color(it.rgb) }
                sheetAccentColor = accentSwatch?.let { Color(it.rgb) }
            } else {
                sheetColor = null
                sheetAccentColor = null
            }
        }
    }
    
    val defaultSheetColor = MaterialTheme.colorScheme.surface
    val animatedSheetColor by animateColorAsState(
        targetValue = sheetColor ?: defaultSheetColor,
        animationSpec = tween(1000),
        label = "sheetColor"
    )
    val animatedAccentColor by animateColorAsState(
        targetValue = sheetAccentColor ?: MaterialTheme.colorScheme.primary,
        animationSpec = tween(1000),
        label = "accentColor"
    )
    val adaptiveOnSheetColor = remember(animatedSheetColor) {
        if (animatedSheetColor.luminance() < 0.45f) Color.White else Color(0xFF161616)
    }
    var showMoreOptions by remember { mutableStateOf(false) }
    var showAudioSettings by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var skipDirection by remember { mutableStateOf<SkipDirection?>(null) }

    // ── Sleep Timer ──────────────────────────────────────────────────────
    LaunchedEffect(Unit) {
        SleepTimerManager.attachToPlayer(
            player = EnhancedMusicPlayerManager.player
        ) {
            EnhancedMusicPlayerManager.player?.pause()
        }
        SleepTimerManager.attachExitCallback {
            EnhancedMusicPlayerManager.stop()
            context.stopService(
                android.content.Intent(context, io.github.aedev.flow.service.Media3MusicService::class.java)
            )
            (context as? android.app.Activity)?.finishAndRemoveTask()
        }
    }
    
    // ── Unified Sheet State ──────────────────────────────────────────────
    var showQueueSheet by remember { mutableStateOf(false) }
    var showInlineLyrics by remember { mutableStateOf(false) }
    
    // ── Dialogs & Sheets ─────────────────────────────────────────────────
    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.showCreatePlaylistDialog(false) },
            onConfirm = { name, desc ->
                viewModel.createPlaylist(name, desc, uiState.currentTrack)
            }
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
            }
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
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, uiState.currentTrack!!.title)
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_message_template, uiState.currentTrack!!.title, uiState.currentTrack!!.artist, uiState.currentTrack!!.videoId))
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_song)))
            },
            onInfoClick = { showInfoDialog = true },
            onAudioEffectsClick = { showAudioSettings = true },
            showPlaylistDialogs = false
        )
    }

    if (showAudioSettings) {
        AudioSettingsSheet(
            onDismiss = { showAudioSettings = false }
        )
    }

    if (showSleepTimer) {
        SleepTimerSheet(
            onDismiss = { showSleepTimer = false }
        )
    }

    if (showInfoDialog && uiState.currentTrack != null) {
        TrackInfoDialog(
            track = uiState.currentTrack!!,
            onDismiss = { showInfoDialog = false }
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
    
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.updateProgress()
            delay(100)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
        val queueExpandedY = with(density) { (statusBarPadding + 72.dp).toPx() }
        val safeHiddenY = queueHiddenY.coerceAtLeast(queueExpandedY)

        val queueOffsetY = remember { Animatable(safeHiddenY) }
        LaunchedEffect(isPlayerSheetExpanded, safeHiddenY) {
            if (!isPlayerSheetExpanded) {
                showQueueSheet = false
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
        val clampedQueueOffset = if (!queueSheetActive) {
            safeHiddenY
        } else {
            queueOffsetY.value.coerceIn(queueExpandedY, safeHiddenY)
        }

        val queueFraction = if (safeHiddenY != queueExpandedY) {
            (1f - ((clampedQueueOffset - queueExpandedY) / (safeHiddenY - queueExpandedY))).coerceIn(0f, 1f)
        } else 0f

        val mainAlpha = (1f - (queueFraction / 0.4f)).coerceIn(0f, 1f)
        val artworkScale = 1f - (queueFraction * 0.10f)
        
        val miniHeaderAlpha = ((queueFraction - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val miniHeaderTranslation = with(density) { 10.dp.toPx() * (1f - miniHeaderAlpha) }

        // ── Sheet animation helper ──────────────────────────────────────────
        suspend fun animateQueueSheetTo(target: Float, initialVelocity: Float = 0f) {
            if (target < safeHiddenY && isPlayerSheetExpanded) showQueueSheet = true
            queueOffsetY.stop()
            queueOffsetY.animateTo(
                targetValue = target.coerceIn(queueExpandedY, safeHiddenY),
                initialVelocity = initialVelocity,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
            if (target >= safeHiddenY) {
                queueOffsetY.snapTo(safeHiddenY)
                showQueueSheet = false
            }
        }

        suspend fun settleQueueSheet(velocity: Float) {
            val distance = safeHiddenY - queueExpandedY
            val progress = if (distance > 0f) {
                ((queueOffsetY.value - queueExpandedY) / distance).coerceIn(0f, 1f)
            } else {
                1f
            }
            val target = when {
                velocity < -900f -> queueExpandedY
                velocity > 900f -> safeHiddenY
                progress < 0.42f -> queueExpandedY
                else -> safeHiddenY
            }
            animateQueueSheetTo(target, velocity)
        }

        fun animateQueueSheet(target: Float) {
            scope.launch { animateQueueSheetTo(target) }
        }

        // ── Intercept system back when sheet is expanded ────────────────────
        BackHandler(enabled = queueSheetActive && queueFraction > 0.05f) {
            animateQueueSheet(safeHiddenY)
        }

        PlayerBackground(
            thumbnailUrl = uiState.currentTrack?.highResThumbnailUrl ?: track.highResThumbnailUrl,
            style = backgroundStyle,
            paletteBaseColor = animatedSheetColor,
            paletteAccentColor = animatedAccentColor
        )

        // ══════════════════════════════════════════════════════════
        //  MAIN PLAYER CONTENT
        // ══════════════════════════════════════════════════════════
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = mainAlpha }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                AnimatedContent(
                    targetState = showInlineLyrics,
                    transitionSpec = {
                        (fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.98f)) togetherWith
                            (fadeOut(tween(260)) + scaleOut(tween(260), targetScale = 1.02f))
                    },
                    label = "artworkInlineLyrics"
                ) { lyricsVisible ->
                    if (lyricsVisible) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = artworkScale
                                    scaleY = artworkScale
                                }
                        ) {
                            InlineLyricsPanel(
                                lyrics = uiState.lyrics,
                                syncedLyrics = uiState.syncedLyrics,
                                currentPosition = uiState.currentPosition,
                                isLoading = uiState.isLyricsLoading,
                                accentColor = animatedAccentColor,
                                onSeekTo = { viewModel.seekTo(it) },
                                providerName = uiState.lyricsProviderName,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(onBackClick) {
                                    var downwardDrag = 0f
                                    detectVerticalDragGestures(
                                        onDragStart = { downwardDrag = 0f },
                                        onVerticalDrag = { change, dragAmount ->
                                            if (dragAmount > 0f) {
                                                downwardDrag += dragAmount
                                                change.consume()
                                            }
                                        },
                                        onDragEnd = {
                                            if (downwardDrag > 72.dp.toPx()) {
                                                onBackClick()
                                            }
                                            downwardDrag = 0f
                                        },
                                        onDragCancel = { downwardDrag = 0f }
                                    )
                                }
                        ) {
                            PlayerTopBar(
                                playingFrom = uiState.playingFrom,
                                onBackClick = onBackClick,
                                onSleepTimerClick = { showSleepTimer = true },
                                onMoreOptionsClick = { showMoreOptions = true },
                                modifier = Modifier.statusBarsPadding(),
                                activeColor = animatedAccentColor,
                                showSleepTimerAction = false
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        Box(
                            modifier = Modifier
                                .padding(horizontal = PlayerHorizontalPadding)
                                .size(artworkSize)
                                .graphicsLayer {
                                    scaleX = artworkScale
                                    scaleY = artworkScale
                                }
                                .shadow(
                                    elevation = if (uiState.isPlaying) 32.dp else 12.dp,
                                    shape = RoundedCornerShape(8.dp),
                                    ambientColor = Color.Black.copy(alpha = 0.5f),
                                    spotColor = Color.Black.copy(alpha = 0.6f)
                                )
                                .clip(RoundedCornerShape(8.dp))
                        ) {
                            PlayerArtwork(
                                thumbnailUrl = (uiState.currentTrack?.highResThumbnailUrl ?: track.highResThumbnailUrl),
                                isVideoMode = isVideoMode,
                                isLoading = uiState.isLoading,
                                player = EnhancedMusicPlayerManager.player,
                                onSkipPrevious = {
                                    viewModel.skipToPrevious()
                                    skipDirection = SkipDirection.PREVIOUS
                                },
                                onSkipNext = {
                                    viewModel.skipToNext()
                                    skipDirection = SkipDirection.NEXT
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(if (showInlineLyrics) 8.dp else 16.dp))

            // ── Title & Artist + Action Buttons ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    AnimatedContent(
                        targetState = uiState.currentTrack?.title ?: track.title,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "title"
                    ) { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(
                                iterations = 1,
                                initialDelayMillis = 3000,
                                velocity = 30.dp
                            )
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = uiState.currentTrack?.artist ?: track.artist,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f),
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable {
                            uiState.currentTrack?.channelId?.takeIf { it.isNotEmpty() }?.let { onArtistClick(it) }
                        }
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                AnimatedContent(
                    targetState = showInlineLyrics,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.9f)) togetherWith
                            (fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.9f))
                    },
                    label = "lyricsActionsSwap"
                ) { lyricsActive ->
                    if (lyricsActive) {
                        PlayerLyricsRefreshButton(
                            isLoading = uiState.isLyricsLoading,
                            accentColor = animatedAccentColor,
                            onRefresh = { viewModel.refreshLyrics() }
                        )
                    } else {
                        PlayerMainActionButtons(
                            isLiked = uiState.isLiked,
                            isDownloaded = uiState.downloadedTrackIds.contains(uiState.currentTrack?.videoId),
                            onLikeClick = { viewModel.toggleLike() },
                            onDownloadClick = { viewModel.downloadTrack() },
                            onAddToPlaylist = { viewModel.showAddToPlaylistDialog(true) },
                            accentColor = animatedAccentColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ── Progress Slider ──
            PlayerProgressSlider(
                currentPosition = uiState.currentPosition,
                duration = uiState.duration,
                onSeekTo = { viewModel.seekTo(it) },
                isPlaying = uiState.isPlaying,
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // ── Playback Controls ──
            PlayerPlaybackControls(
                isPlaying = uiState.isPlaying,
                isBuffering = uiState.isBuffering,
                onPreviousClick = { viewModel.skipToPrevious() },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onNextClick = { viewModel.skipToNext() },
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
            )

            Spacer(modifier = Modifier.height(22.dp))

            PlayerSecondaryActions(
                lyricsActive = showInlineLyrics,
                shuffleEnabled = uiState.shuffleEnabled,
                repeatMode = uiState.repeatMode,
                sleepTimerActive = SleepTimerManager.isActive,
                accentColor = animatedAccentColor,
                onLyricsClick = {
                    uiState.currentTrack?.let { viewModel.ensureLyricsLoaded(it) }
                    showInlineLyrics = !showInlineLyrics
                },
                onShuffleClick = { viewModel.toggleShuffle() },
                onRepeatClick = { viewModel.toggleRepeat() },
                onQueueClick = {
                    if (isPlayerSheetExpanded) {
                        showQueueSheet = true
                        animateQueueSheet(queueExpandedY)
                    }
                },
                onSleepTimerClick = { showSleepTimer = true },
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
            )

            Spacer(modifier = Modifier.height(navBarPadding + 20.dp))
        }

        if (queueFraction > 0.3f) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(64.dp)
                    .padding(horizontal = 20.dp)
                    .graphicsLayer {
                        alpha = miniHeaderAlpha
                        translationY = miniHeaderTranslation
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(42.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                    ) {
                        AsyncImage(
                            model = uiState.currentTrack?.highResThumbnailUrl ?: track.highResThumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.currentTrack?.title ?: "",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = uiState.currentTrack?.artist ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FilledIconButton(
                    onClick = { viewModel.togglePlayPause() },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black
                    ),
                    shape = CircleShape,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        val queueCornerRadius = 28.dp * (1f - queueFraction)

        val queueDraggableState = rememberDraggableState { delta ->
            scope.launch {
                queueOffsetY.snapTo((queueOffsetY.value + delta).coerceIn(queueExpandedY, safeHiddenY))
            }
        }

        val queueDragHandleModifier = Modifier.draggable(
            orientation = Orientation.Vertical,
            state = queueDraggableState,
            onDragStarted = {
                scope.launch { queueOffsetY.stop() }
            },
            onDragStopped = { velocity ->
                settleQueueSheet(velocity)
            }
        )

        // ── NestedScrollConnection: isolates sheet events from MusicPlayerBottomSheet ──
        val sheetNestedScrollConnection = remember(queueExpandedY, safeHiddenY) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
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
                    source: NestedScrollSource
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

                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
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
                modifier = Modifier
                    .offset { IntOffset(0, clampedQueueOffset.roundToInt()) }
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .shadow(
                        elevation = (18.dp * queueFraction),
                        shape = RoundedCornerShape(topStart = queueCornerRadius, topEnd = queueCornerRadius),
                        clip = false
                    )
                    .nestedScroll(sheetNestedScrollConnection)
            ) {
                QueueSheet(
                    sheetBackgroundColor = animatedSheetColor,
                    accentColor = animatedAccentColor,
                    onSheetColor = adaptiveOnSheetColor,
                    sheetCornerRadius = queueCornerRadius,
                    queue = uiState.queue,
                    automixTracks = uiState.autoplaySuggestions,
                    currentIndex = uiState.currentQueueIndex,
                    downloadedTrackIds = uiState.downloadedTrackIds,
                    playingFrom = uiState.playingFrom,
                    selectedFilter = uiState.selectedFilter,
                    isAutomixLoading = uiState.isRelatedLoading,
                    onTrackClick = { viewModel.playFromQueue(it) },
                    onMoveTrack = { from, to -> viewModel.moveTrack(from, to) },
                    onFilterSelect = { viewModel.setFilter(it) },
                    onAutomixTrackClick = { viewModel.loadAndPlayTrack(it) },
                    onPlayNextAutomix = { viewModel.playNext(it) },
                    onAddToQueueAutomix = { viewModel.addToQueue(it) },
                    dragHandleModifier = queueDragHandleModifier
                )
            }
        }

        AnimatedSkipIndicators(
            direction = skipDirection,
            onAnimationComplete = { skipDirection = null }
        )
    }
}
