package io.github.aedev.flow.ui.screens.music


import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
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

import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.SleepTimerManager
import io.github.aedev.flow.ui.components.SleepTimerSheet
import io.github.aedev.flow.ui.screens.music.player.*
import io.github.aedev.flow.ui.components.MusicQuickActionsSheet
import androidx.compose.foundation.clickable

private val PlayerHorizontalPadding = 32.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMusicPlayerScreen(
    track: MusicTrack,
    onBackClick: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    onAlbumClick: (String) -> Unit = {},
    viewModel: MusicPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    val isVideoMode = false 
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
    var currentTab by remember { mutableStateOf(PlayerTab.UP_NEXT) }
    
    val queuePeekHeight = 64.dp
    
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
            onViewArtist = { 
                if (uiState.currentTrack!!.channelId.isNotEmpty()) {
                    onArtistClick(uiState.currentTrack!!.channelId)
                }
            },
            onViewAlbum = { 
                if (uiState.currentTrack!!.album.isNotEmpty()) {
                    onAlbumClick(uiState.currentTrack!!.album)
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
            onAudioEffectsClick = { showAudioSettings = true }
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

        val reservedHeight = statusBarPadding + 56.dp + 60.dp + 40.dp + 72.dp + 84.dp + queuePeekHeight + navBarPadding
        val availableForArtwork = screenHeight - reservedHeight
        val artworkMaxWidth = screenWidth - (PlayerHorizontalPadding * 2)
        val artworkSize = min(availableForArtwork, artworkMaxWidth).coerceAtLeast(120.dp)

        val maxHeightPx = constraints.maxHeight.toFloat()
        val queuePeekPx = with(density) { queuePeekHeight.toPx() }
        val queueCollapsedY = maxHeightPx - queuePeekPx - navBarPx
        val queueExpandedY = with(density) { (statusBarPadding + 72.dp).toPx() }
        val safeCollapsedY = queueCollapsedY.coerceAtLeast(queueExpandedY)

        var queueOffsetY by remember(safeCollapsedY) { mutableFloatStateOf(safeCollapsedY) }
        val clampedQueueOffset = queueOffsetY.coerceIn(queueExpandedY, safeCollapsedY)

        val queueFraction = if (safeCollapsedY != queueExpandedY) {
            (1f - ((clampedQueueOffset - queueExpandedY) / (safeCollapsedY - queueExpandedY))).coerceIn(0f, 1f)
        } else 0f

        val mainAlpha = (1f - (queueFraction / 0.4f)).coerceIn(0f, 1f)
        val artworkScale = 1f - (queueFraction * 0.10f)
        
        val miniHeaderAlpha = ((queueFraction - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val miniHeaderTranslation = with(density) { 10.dp.toPx() * (1f - miniHeaderAlpha) }

        AnimatedContent(
            targetState = uiState.currentTrack?.highResThumbnailUrl ?: track.highResThumbnailUrl,
            transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
            label = "bgArt"
        ) { thumbnailUrl ->
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(150.dp),
                alpha = 0.55f,
                contentScale = ContentScale.Crop
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.40f),
                            0.30f to Color.Black.copy(alpha = 0.20f),
                            0.55f to Color.Black.copy(alpha = 0.40f),
                            0.80f to Color.Black.copy(alpha = 0.80f),
                            1.00f to Color.Black.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        // ══════════════════════════════════════════════════════════
        //  MAIN PLAYER CONTENT
        // ══════════════════════════════════════════════════════════
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .graphicsLayer { alpha = mainAlpha }
        ) {
            // ── Top Bar ──
            PlayerTopBar(
                playingFrom = uiState.playingFrom,
                onBackClick = onBackClick,
                onSleepTimerClick = { showSleepTimer = true },
                onMoreOptionsClick = { showMoreOptions = true }
            )
            Spacer(modifier = Modifier.height(48.dp))

            // ── Artwork ──
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                Box(
                    modifier = Modifier
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
            }

            Spacer(modifier = Modifier.height(48.dp))

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

                PlayerMainActionButtons(
                    isLiked = uiState.isLiked,
                    isDownloaded = uiState.downloadedTrackIds.contains(uiState.currentTrack?.videoId),
                    onLikeClick = { viewModel.toggleLike() },
                    onDownloadClick = { viewModel.downloadTrack() },
                    onAddToPlaylist = { viewModel.showAddToPlaylistDialog(true) }
                )
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

            Spacer(modifier = Modifier.height(40.dp))

            // ── Playback Controls ──
            PlayerPlaybackControls(
                isPlaying = uiState.isPlaying,
                isBuffering = uiState.isBuffering,
                shuffleEnabled = uiState.shuffleEnabled,
                repeatMode = uiState.repeatMode,
                onShuffleToggle = { viewModel.toggleShuffle() },
                onPreviousClick = { viewModel.skipToPrevious() },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onNextClick = { viewModel.skipToNext() },
                onRepeatToggle = { viewModel.toggleRepeat() },
                modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
            )

            Spacer(modifier = Modifier.height(queuePeekHeight + navBarPadding + 20.dp))
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

        fun animateQueueSheet(target: Float) {
            scope.launch {
                animate(
                    initialValue = queueOffsetY,
                    targetValue = target,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { value, _ ->
                    queueOffsetY = value
                }
            }
        }

        val queueCornerRadius = 24.dp * (1f - queueFraction)

        val queueDraggableState = rememberDraggableState { delta ->
            queueOffsetY = (queueOffsetY + delta).coerceIn(queueExpandedY, safeCollapsedY)
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(0, clampedQueueOffset.roundToInt()) }
                .fillMaxWidth()
                .fillMaxHeight()
                .shadow(
                    elevation = (16.dp * queueFraction),
                    shape = RoundedCornerShape(topStart = queueCornerRadius, topEnd = queueCornerRadius),
                    clip = false
                )
                .draggable(
                    orientation = Orientation.Vertical,
                    state = queueDraggableState,
                    onDragStopped = { velocity ->
                        val midPoint = (safeCollapsedY + queueExpandedY) / 2
                        val target = when {
                            velocity < -800f -> queueExpandedY
                            velocity > 800f -> safeCollapsedY
                            clampedQueueOffset < midPoint -> queueExpandedY
                            else -> safeCollapsedY
                        }
                        animateQueueSheet(target)
                    }
                )
        ) {
            UnifiedPlayerSheet(
                currentTab = currentTab,
                onTabSelect = { currentTab = it },
                isExpanded = queueFraction > 0.5f,
                onExpand = { animateQueueSheet(queueExpandedY) },
                sheetCornerRadius = queueCornerRadius,
                queue = uiState.queue,
                currentIndex = uiState.currentQueueIndex,
                playingFrom = uiState.playingFrom,
                autoplayEnabled = uiState.autoplayEnabled,
                selectedFilter = uiState.selectedFilter,
                onTrackClick = { viewModel.playFromQueue(it) },
                onToggleAutoplay = { viewModel.toggleAutoplay() },
                onFilterSelect = { viewModel.setFilter(it) },
                onMoveTrack = { from, to -> viewModel.moveTrack(from, to) },
                lyrics = uiState.lyrics,
                syncedLyrics = uiState.syncedLyrics,
                currentPosition = uiState.currentPosition,
                isLyricsLoading = uiState.isLyricsLoading,
                onSeekTo = { viewModel.seekTo(it) },
                relatedTracks = uiState.relatedContent,
                isRelatedLoading = uiState.isRelatedLoading,
                onRelatedTrackClick = { viewModel.loadAndPlayTrack(it) }
            )
        }

        AnimatedSkipIndicators(
            direction = skipDirection,
            onAnimationComplete = { skipDirection = null }
        )
    }
}
