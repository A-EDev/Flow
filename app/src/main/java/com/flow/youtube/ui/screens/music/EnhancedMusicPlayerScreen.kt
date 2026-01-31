package com.flow.youtube.ui.screens.music

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.flow.youtube.player.EnhancedMusicPlayerManager
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

import com.flow.youtube.R
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
    
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showRelated by remember { mutableStateOf(false) }
    var isVideoMode by remember { mutableStateOf(false) }
    var showMoreOptions by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var skipDirection by remember { mutableStateOf<SkipDirection?>(null) }
    
    LaunchedEffect(isVideoMode) {
        viewModel.switchMode(isVideoMode)
    }
    
    // Dialogs
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
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top bar
            PlayerTopBar(
                isVideoMode = isVideoMode,
                onModeChange = { isVideoMode = it },
                onBackClick = onBackClick,
                onMoreOptionsClick = { showMoreOptions = true }
            )
            
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                val isWideLayout = maxWidth > 600.dp || (maxWidth > maxHeight && maxWidth > 400.dp)
                
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
                
                if (isWideLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(0.45f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                             artworkContent(
                                Modifier
                                    .aspectRatio(1f)
                                    .fillMaxHeight()
                             )
                        }
                        
                        Column(
                            modifier = Modifier
                                .weight(0.55f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(vertical = 8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
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
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontWeight = FontWeight.Bold
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

                            Spacer(modifier = Modifier.height(20.dp))

                            // Seekbar
                            PlayerProgressSlider(
                                currentPosition = uiState.currentPosition,
                                duration = uiState.duration,
                                onSeekTo = { viewModel.seekTo(it) }
                            )
                        
                            Spacer(modifier = Modifier.height(12.dp))

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
                        
                            // Bottom Tabs
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                TextButton(onClick = { showQueue = true }) {
                                    Text(stringResource(R.string.up_next), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { showLyrics = true }) {
                                    Text(stringResource(R.string.lyrics), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { showRelated = true }) {
                                    Text(stringResource(R.string.related), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else {
                    // Portrait Mode
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        artworkContent(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
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
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    fontWeight = FontWeight.Bold
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

                        Spacer(modifier = Modifier.height(20.dp))

                        // Seekbar
                        PlayerProgressSlider(
                            currentPosition = uiState.currentPosition,
                            duration = uiState.duration,
                            onSeekTo = { viewModel.seekTo(it) }
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))

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
                        
                        Spacer(modifier = Modifier.weight(1f))

                        // Bottom Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { showQueue = true }) {
                                Text(stringResource(R.string.up_next), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { showLyrics = true }) {
                                Text(stringResource(R.string.lyrics), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { showRelated = true }) {
                                Text(stringResource(R.string.related), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }
        
        // Queue bottom sheet
        if (showQueue) {
            UpNextBottomSheet(
                queue = uiState.queue,
                currentIndex = uiState.currentQueueIndex,
                playingFrom = uiState.playingFrom,
                autoplayEnabled = uiState.autoplayEnabled,
                selectedFilter = uiState.selectedFilter,
                onDismiss = { showQueue = false },
                onTrackClick = { index ->
                    viewModel.playFromQueue(index)
                    showQueue = false
                },
                onToggleAutoplay = {
                    viewModel.toggleAutoplay()
                },
                onFilterSelect = { filter ->
                    viewModel.setFilter(filter)
                },
                onMoveTrack = { from, to ->
                    viewModel.moveTrack(from, to)
                }
            )
        }
        
        // Lyrics bottom sheet
        if (showLyrics) {
            LyricsBottomSheet(
                trackTitle = uiState.currentTrack?.title ?: track.title,
                trackArtist = uiState.currentTrack?.artist ?: track.artist,
                thumbnailUrl = uiState.currentTrack?.thumbnailUrl ?: track.thumbnailUrl,
                lyrics = uiState.lyrics,
                syncedLyrics = uiState.syncedLyrics,
                currentPosition = uiState.currentPosition,
                isLoading = uiState.isLyricsLoading,
                onDismiss = { showLyrics = false },
                onSeekTo = { viewModel.seekTo(it) }
            )
        }

        // Related bottom sheet
        if (showRelated) {
            RelatedBottomSheet(
                relatedTracks = uiState.relatedContent,
                isLoading = uiState.isRelatedLoading,
                onDismiss = { showRelated = false },
                onTrackClick = { track ->
                    viewModel.loadAndPlayTrack(track)
                    showRelated = false
                }
            )
        }
        
        // Loading overlay
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Text(
                        text = stringResource(R.string.loading_track),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        // Error snackbar
        if (uiState.error != null) {
            Snackbar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                action = {
                    TextButton(onClick = onBackClick) {
                        Text(stringResource(R.string.close))
                    }
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(uiState.error ?: stringResource(R.string.error_occurred))
            }
        }
        
        AnimatedSkipIndicators(
            direction = skipDirection,
            onAnimationComplete = { skipDirection = null }
        )
    }
}
