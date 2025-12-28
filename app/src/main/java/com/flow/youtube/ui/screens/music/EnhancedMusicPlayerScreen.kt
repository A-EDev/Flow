package com.flow.youtube.ui.screens.music

import android.media.AudioManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.player.RepeatMode
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedMusicPlayerScreen(
    track: MusicTrack,
    onBackClick: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    viewModel: MusicPlayerViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var showQueue by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    
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
    
    // Gesture states
    var dragOffset by remember { mutableStateOf(0f) }
    var showSkipAnimation by remember { mutableStateOf<SkipDirection?>(null) }
    
    LaunchedEffect(track.videoId) {
        viewModel.initialize(context)
        
        // Check if track is already loaded and playing
        val currentTrack = viewModel.uiState.value.currentTrack
        val isPlaying = viewModel.uiState.value.isPlaying
        
        if (currentTrack?.videoId == track.videoId && isPlaying) {
            // Track is already playing, don't reload
        } else {
            viewModel.loadAndPlayTrack(track)
        }
    }
    
    // Progress update loop
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
        
        // Gradient overlay
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
            TopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "NOW PLAYING",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = "Close",
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { /* Share */ }) {
                        Icon(
                            Icons.Outlined.Share, 
                            "Share",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { /* More options */ }) {
                        Icon(
                            Icons.Outlined.MoreVert, 
                            "More",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly
            ) {
                // Album art with swipe gestures
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 16.dp)
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (kotlin.math.abs(dragOffset) > 100) {
                                        if (dragOffset > 0) {
                                            showSkipAnimation = SkipDirection.PREVIOUS
                                            viewModel.skipToPrevious()
                                        } else {
                                            showSkipAnimation = SkipDirection.NEXT
                                            viewModel.skipToNext()
                                        }
                                    }
                                    dragOffset = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    dragOffset += dragAmount
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // Skip animations
                    AnimatedSkipIndicators(showSkipAnimation) {
                        showSkipAnimation = null
                    }
                    
                    // Album art card
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                        shape = RoundedCornerShape(24.dp),
                        shadowElevation = 24.dp,
                        tonalElevation = 4.dp
                    ) {
                        AsyncImage(
                            model = uiState.currentTrack?.thumbnailUrl ?: track.thumbnailUrl,
                            contentDescription = "Album art",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Track info with love button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = uiState.currentTrack?.title ?: track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.currentTrack?.artist ?: track.artist,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                val channelId = uiState.currentTrack?.channelId ?: track.channelId
                                if (channelId.isNotEmpty()) {
                                    onArtistClick(channelId)
                                }
                            }
                        )
                    }
                    
                    IconButton(
                        onClick = { viewModel.toggleLike() }
                    ) {
                        Icon(
                            imageVector = if (uiState.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "Like",
                            tint = if (uiState.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Progress bar and time
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = uiState.currentPosition.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.fillMaxWidth(),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatTime(uiState.currentPosition),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = formatTime(uiState.duration),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Playback controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Shuffle
                    IconButton(
                        onClick = { viewModel.toggleShuffle() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (uiState.shuffleEnabled) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    
                    // Previous
                    IconButton(
                        onClick = { viewModel.skipToPrevious() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Play/Pause - Large button
                    FilledIconButton(
                        onClick = { viewModel.togglePlayPause() },
                        modifier = Modifier.size(72.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (uiState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (uiState.isPlaying) 
                                    Icons.Filled.Pause 
                                else 
                                    Icons.Filled.PlayArrow,
                                contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    // Next
                    IconButton(
                        onClick = { viewModel.skipToNext() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    
                    // Repeat
                    IconButton(
                        onClick = { viewModel.toggleRepeat() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = when (uiState.repeatMode) {
                                RepeatMode.ONE -> Icons.Outlined.RepeatOne
                                else -> Icons.Outlined.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (uiState.repeatMode != RepeatMode.OFF) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Bottom actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Download button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = { viewModel.downloadTrack() }) {
                            Icon(
                                imageVector = Icons.Outlined.DownloadForOffline,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Download",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Add to Playlist button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = { viewModel.showAddToPlaylistDialog(true) }) {
                            Icon(
                                imageVector = Icons.Outlined.PlaylistAdd,
                                contentDescription = "Add to Playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Playlist",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Queue button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(onClick = { showQueue = !showQueue }) {
                            Icon(
                                imageVector = Icons.Outlined.QueueMusic,
                                contentDescription = "Queue",
                                tint = if (showQueue) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "Queue",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (showQueue) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        // Queue bottom sheet
        if (showQueue) {
            EnhancedQueueBottomSheet(
                queue = uiState.queue,
                currentIndex = uiState.currentQueueIndex,
                onDismiss = { showQueue = false },
                onTrackClick = { index -> viewModel.playFromQueue(index) },
                onRemoveTrack = { index -> viewModel.removeFromQueue(index) }
            )
        }
        
        // Lyrics bottom sheet
        if (showLyrics) {
            LyricsBottomSheet(
                trackTitle = uiState.currentTrack?.title ?: "",
                onDismiss = { showLyrics = false }
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
                        text = "Loading track...",
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
                        Text("Close")
                    }
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ) {
                Text(uiState.error ?: "An error occurred")
            }
        }
    }
}

@Composable
private fun AnimatedSkipIndicators(
    direction: SkipDirection?,
    onAnimationComplete: () -> Unit
) {
    direction?.let {
        LaunchedEffect(direction) {
            delay(500)
            onAnimationComplete()
        }
        
        val slideIn = remember { Animatable(if (direction == SkipDirection.NEXT) 1f else -1f) }
        
        LaunchedEffect(direction) {
            slideIn.animateTo(
                targetValue = 0f,
                animationSpec = tween(300, easing = FastOutSlowInEasing)
            )
        }
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = if (direction == SkipDirection.NEXT) 
                Alignment.CenterEnd 
            else 
                Alignment.CenterStart
        ) {
            Surface(
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                shadowElevation = 8.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (direction == SkipDirection.NEXT)
                            Icons.Filled.SkipNext
                        else
                            Icons.Filled.SkipPrevious,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedQueueBottomSheet(
    queue: List<MusicTrack>,
    currentIndex: Int,
    onDismiss: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onRemoveTrack: (Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Queue",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${queue.size} tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                items(queue.size) { index ->
                    QueueTrackItem(
                        track = queue[index],
                        isCurrentlyPlaying = index == currentIndex,
                        onClick = { onTrackClick(index) },
                        onRemove = { onRemoveTrack(index) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsBottomSheet(
    trackTitle: String,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .heightIn(min = 300.dp, max = 500.dp)
        ) {
            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = trackTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lyrics,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Lyrics not available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Lyrics not available for this track",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueTrackItem(
    track: MusicTrack,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (isCurrentlyPlaying)
            MaterialTheme.colorScheme.primaryContainer
        else
            MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null
                )
            }
            
            // Track info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isCurrentlyPlaying)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCurrentlyPlaying)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            // Playing indicator or remove button
            if (isCurrentlyPlaying) {
                Icon(
                    imageVector = Icons.Filled.Equalizer,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Remove",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = (milliseconds / 1000).toInt()
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%d:%02d".format(minutes, remainingSeconds)
}

private enum class SkipDirection {
    NEXT, PREVIOUS
}
