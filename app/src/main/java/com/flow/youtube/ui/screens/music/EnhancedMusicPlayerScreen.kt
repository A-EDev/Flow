package com.flow.youtube.ui.screens.music

import android.content.Intent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import com.flow.youtube.R
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.ui.screens.music.player.*
import com.flow.youtube.ui.components.MusicQuickActionsSheet

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
    
    var isVideoMode by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var skipDirection by remember { mutableStateOf<SkipDirection?>(null) }
    
    // Unified Sheet State
    var currentTab by remember { mutableStateOf(PlayerTab.UP_NEXT) }
    
    // Drag State
    val density = LocalDensity.current
    val peekHeight = with(density) { 60.dp.toPx() } 
    
    var dragOffsetY by remember { mutableStateOf(Float.MAX_VALUE) }
    var isInitialized by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVideoMode) {
        viewModel.switchMode(isVideoMode)
    }
    
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
            onInfoClick = { showInfoDialog = true }
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
            .background(MaterialTheme.colorScheme.background)
    ) {
        val navBarInsets = WindowInsets.navigationBars.asPaddingValues()
        val navBarHeightPx = with(density) { navBarInsets.calculateBottomPadding().toPx() }
        val maxHeight = constraints.maxHeight.toFloat()
        val collapsedY = maxHeight - peekHeight - navBarHeightPx
        val expandedY = with(density) { 100.dp.toPx() }
        
        if (!isInitialized) {
            dragOffsetY = collapsedY
            isInitialized = true
        }

        val draggableState = rememberDraggableState { delta ->
             val newOffset = (dragOffsetY + delta).coerceIn(expandedY, collapsedY)
             dragOffsetY = newOffset
        }
        
        val dragFraction = if (collapsedY != expandedY) {
            (1f - ((dragOffsetY - expandedY) / (collapsedY - expandedY))).coerceIn(0f, 1f)
        } else 0f
        
        // --- Animations ---
        // Alpha for Main Content
        val mainContentAlpha = (1f - (dragFraction / 0.6f)).coerceIn(0f, 1f)
        
        // Scale for Main Artwork
        val mainArtworkScale = 1f - (dragFraction * 0.2f)
        
        // Mini Player Header
        val miniPlayerProgress = ((dragFraction - 0.6f) / 0.4f).coerceIn(0f, 1f)
        val miniPlayerAlpha = miniPlayerProgress
        val miniPlayerTranslationY = with(density) { 20.dp.toPx() * (1f - miniPlayerProgress) }
        
        
        // Blurred background
        AsyncImage(
            model = uiState.currentTrack?.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(80.dp),
            alpha = 0.4f,
            contentScale = ContentScale.Crop
        )
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.85f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
             // Top bar
            Box(
                modifier = Modifier
                    .graphicsLayer { alpha = mainContentAlpha }
            ) {
                PlayerTopBar(
                    isVideoMode = isVideoMode,
                    onModeChange = { isVideoMode = it },
                    onBackClick = onBackClick,
                    onMoreOptionsClick = { showMoreOptions = true }
                )
            }
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
                    .graphicsLayer { alpha = mainContentAlpha } 
            ) {
                 val artworkContent: @Composable (Modifier) -> Unit = { modifier ->
                    PlayerArtwork(
                        thumbnailUrl = (uiState.currentTrack?.thumbnailUrl ?: track.thumbnailUrl),
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
                        modifier = modifier
                    )
                }

                val boxConstraints = this@BoxWithConstraints.constraints
                val boxMaxWidth = this@BoxWithConstraints.maxWidth
                val boxMaxHeight = this@BoxWithConstraints.maxHeight
                
                val contentModifier = if (boxMaxWidth > 600.dp) {
                     Modifier.width(600.dp)
                } else {
                     Modifier.fillMaxWidth()
                }
                
                // Artwork Sizing
                val maxArtworkHeightDp = boxMaxHeight * 0.45f 
                val artworkSize = if (boxMaxWidth > maxArtworkHeightDp) maxArtworkHeightDp else boxMaxWidth

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .then(contentModifier)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = with(density) { peekHeight.toDp() + 16.dp }),
                    verticalArrangement = Arrangement.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(8.dp)) 
                    
                    Box(
                        modifier = Modifier
                            .size(artworkSize)
                            .aspectRatio(1f)
                            .graphicsLayer {
                                this.scaleX = mainArtworkScale
                                this.scaleY = mainArtworkScale
                            }
                            .shadow(
                                elevation = if (uiState.isPlaying) 16.dp else 4.dp,
                                shape = MaterialTheme.shapes.medium
                            )
                    ) {
                        artworkContent(Modifier.fillMaxSize())
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Title and Artist Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = uiState.currentTrack?.title ?: track.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = uiState.currentTrack?.artist ?: track.artist,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        
                        IconButton(
                            onClick = { viewModel.toggleLike() },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (uiState.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = stringResource(R.string.like),
                                tint = if (uiState.isLiked) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
        
                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons
                    PlayerActionButtons(
                        isDownloaded = uiState.downloadedTrackIds.contains(uiState.currentTrack?.videoId),
                        onShare = {
                             val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_message_template, uiState.currentTrack?.title, uiState.currentTrack?.artist, uiState.currentTrack?.videoId))
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        },
                        onDownload = {
                            val isDownloaded = uiState.downloadedTrackIds.contains(uiState.currentTrack?.videoId)
                            if (!isDownloaded) viewModel.downloadTrack()
                        },
                        onAddToPlaylist = { viewModel.showAddToPlaylistDialog(true) }
                    )

                    Spacer(modifier = Modifier.height(16.dp)) 

                    // Seekbar
                    PlayerProgressSlider(
                        currentPosition = uiState.currentPosition,
                        duration = uiState.duration,
                        onSeekTo = { viewModel.seekTo(it) }
                    )
                
                    Spacer(modifier = Modifier.height(4.dp)) 

                    // Playback Controls
                    PlayerPlaybackControls(
                        isPlaying = uiState.isPlaying,
                        isBuffering = uiState.isBuffering,
                        shuffleEnabled = uiState.shuffleEnabled,
                        repeatMode = uiState.repeatMode,
                        onShuffleToggle = { viewModel.toggleShuffle() },
                        onPreviousClick = { viewModel.skipToPrevious() },
                        onPlayPauseToggle = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.skipToNext() },
                        onRepeatToggle = { viewModel.toggleRepeat() }
                    )
                     
                    Spacer(modifier = Modifier.height(16.dp)) 
                }
            }
        }
        
        // Mini Player Header 
        if (dragFraction > 0.4f) {
           Row(
               modifier = Modifier
                   .fillMaxWidth()
                   .height(80.dp) 
                   .padding(horizontal = 20.dp)
                   .graphicsLayer { 
                       alpha = miniPlayerAlpha 
                       translationY = miniPlayerTranslationY
                   },
               verticalAlignment = Alignment.CenterVertically,
               horizontalArrangement = Arrangement.SpaceBetween
           ) {
               Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                   // Mini Artwork
                   Card(
                       shape = MaterialTheme.shapes.small,
                       modifier = Modifier.size(48.dp) 
                   ) {
                        AsyncImage(
                            model = uiState.currentTrack?.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                   }
                   Spacer(modifier = Modifier.width(16.dp))
                   Column {
                       Text(
                           text = uiState.currentTrack?.title ?: "",
                           style = MaterialTheme.typography.titleMedium,
                           fontWeight = FontWeight.Bold,
                           color = Color.White,
                           maxLines = 1,
                           overflow = TextOverflow.Ellipsis
                       )
                       Text(
                           text = uiState.currentTrack?.artist ?: "",
                           style = MaterialTheme.typography.bodySmall,
                           color = Color.White.copy(alpha = 0.7f),
                           maxLines = 1,
                           overflow = TextOverflow.Ellipsis
                       )
                   }
               }
               
               IconButton(
                   onClick = { viewModel.togglePlayPause() },
                   colors = IconButtonDefaults.iconButtonColors(
                       containerColor = Color.White,
                       contentColor = Color.Black
                   ),
                   modifier = Modifier.size(40.dp)
               ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
               }
           }
        }
        
        // Unified Bottom Sheet
        val sheetOffset = dragOffsetY
        
        fun animateSheet(target: Float) {
            scope.launch {
                animate(
                    initialValue = dragOffsetY,
                    targetValue = target,
                    animationSpec = tween(
                        durationMillis = 400,
                        easing = FastOutSlowInEasing
                    )
                ) { value, _ ->
                    dragOffsetY = value
                }
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(0, sheetOffset.roundToInt()) }
                .fillMaxWidth()
                .height((maxHeight - expandedY).dp)
                .draggable(
                    orientation = Orientation.Vertical,
                    state = draggableState,
                    onDragStopped = { velocity ->
                        // Fling logic
                        val target = if (velocity < -1000f || (velocity < 0f && dragOffsetY < (collapsedY + expandedY) * 0.6)) {
                            expandedY
                        } else if (velocity > 1000f) {
                            collapsedY
                        } else {
                            if (dragOffsetY < (collapsedY + expandedY) / 2) expandedY else collapsedY
                        }
                        animateSheet(target)
                    }
                )
        ) {
            UnifiedPlayerSheet(
                currentTab = currentTab,
                onTabSelect = { currentTab = it },
                isExpanded = dragFraction > 0.5f,
                onExpand = { animateSheet(expandedY) },
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
