package com.flow.youtube.ui.screens.music

import android.content.Intent
import android.media.AudioManager
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.flow.youtube.player.EnhancedMusicPlayerManager
import android.view.ViewGroup
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.player.RepeatMode
import coil.compose.AsyncImage
import com.flow.youtube.R
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay

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
        com.flow.youtube.ui.components.MusicQuickActionsSheet(
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
        
        // Check global manager state to avoid overwriting an existing queue
        val managerTrack = EnhancedMusicPlayerManager.currentTrack.value
        val isManagerPlaying = EnhancedMusicPlayerManager.isPlaying()
        
        if (managerTrack?.videoId == track.videoId && (isManagerPlaying || managerTrack != null)) {
            // Track is already loaded in the global manager, don't reload as it might reset the queue
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
            CenterAlignedTopAppBar(
                title = {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (!isVideoMode) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                onClick = { isVideoMode = false },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Text(
                                        context.getString(R.string.song),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isVideoMode) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                                onClick = { isVideoMode = true },
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Text(
                                        context.getString(R.string.video),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.close),
                            modifier = Modifier.size(32.dp),
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMoreOptions = true }) {
                        Icon(
                            Icons.Outlined.MoreVert, 
                            stringResource(R.string.more_options),
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
            
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                val isWideLayout = maxWidth > 600.dp || (maxWidth > maxHeight && maxWidth > 400.dp)
                
                val artworkContent: @Composable (Modifier) -> Unit = { modifier ->
                    // Artwork / Video Area with Gestures
                    Box(
                        modifier = modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                            .pointerInput(Unit) {
                                var totalDrag = 0f
                                detectHorizontalDragGestures(
                                    onDragStart = { totalDrag = 0f },
                                    onDragEnd = {
                                        if (totalDrag > 100) {
                                            viewModel.skipToPrevious()
                                        } else if (totalDrag < -100) {
                                            viewModel.skipToNext()
                                        }
                                    },
                                    onHorizontalDrag = { change, dragAmount ->
                                        totalDrag += dragAmount
                                        change.consume()
                                    }
                                )
                            }
                    ) {
                        if (isVideoMode) {
                            AndroidView(
                                factory = { context ->
                                    PlayerView(context).apply {
                                        player = EnhancedMusicPlayerManager.player
                                        useController = false
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.MATCH_PARENT
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            AsyncImage(
                                model = (uiState.currentTrack?.thumbnailUrl ?: track.thumbnailUrl).replace("w120-h120", "w1000-h1000"),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                            
                            if (uiState.isLoading) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White)
                                }
                            }
                        }
                    }
                }
                
                if (isWideLayout) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 16.dp), // Add padding to avoid bottom overlap
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
                                    .aspectRatio(1f) // Square aspect ratio
                                    .fillMaxHeight() // Fill height available
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
                
                        Spacer(modifier = Modifier.height(16.dp))

                        // Pill Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            PillButton(
                                icon = Icons.Outlined.Share,
                                text = context.getString(R.string.share),
                                onClick = {
                                    val sendIntent: Intent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_message_template, uiState.currentTrack?.title, uiState.currentTrack?.artist, uiState.currentTrack?.videoId))
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, null)
                                    context.startActivity(shareIntent)
                                }
                            )
                            
                            val isDownloaded = uiState.downloadedTrackIds.contains(uiState.currentTrack?.videoId)
                            PillButton(
                                icon = if (isDownloaded) Icons.Filled.CheckCircle else Icons.Outlined.DownloadForOffline,
                                text = if (isDownloaded) context.getString(R.string.downloaded) else context.getString(
                                    R.string.download
                                ),
                                onClick = { 
                                    if (!isDownloaded) viewModel.downloadTrack() 
                                }
                            )
                            PillButton(
                                icon = Icons.Outlined.PlaylistAdd,
                                text = context.getString(R.string.save),
                                onClick = { viewModel.showAddToPlaylistDialog(true) }
                            )
                            
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Seekbar
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = uiState.currentPosition.toFloat(),
                                onValueChange = { viewModel.seekTo(it.toLong()) },
                                valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = Color.White,
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    formatTime(uiState.currentPosition),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    formatTime(uiState.duration),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))

                        // Playback Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { viewModel.toggleShuffle() }) {
                                Icon(
                                    Icons.Outlined.Shuffle,
                                    contentDescription = stringResource(R.string.shuffle),
                                    tint = if (uiState.shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                )
                            }
                            
                            IconButton(
                                onClick = { viewModel.skipToPrevious() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SkipPrevious,
                                    contentDescription = stringResource(R.string.previous),
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Surface(
                                shape = CircleShape,
                                color = Color.White,
                                modifier = Modifier.size(72.dp),
                                onClick = { viewModel.togglePlayPause() }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    if (uiState.isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            color = Color.Black,
                                            strokeWidth = 3.dp
                                        )
                                    } else {
                                        Icon(
                                            imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                            contentDescription = if (uiState.isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                                            tint = Color.Black,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                            }

                            IconButton(
                                onClick = { viewModel.skipToNext() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    Icons.Filled.SkipNext,
                                    contentDescription = stringResource(R.string.next),
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            IconButton(onClick = { viewModel.toggleRepeat() }) {
                                Icon(
                                    when (uiState.repeatMode) {
                                        RepeatMode.ONE -> Icons.Outlined.RepeatOne
                                        else -> Icons.Outlined.Repeat
                                    },
                                    contentDescription = stringResource(R.string.repeat),
                                    tint = if (uiState.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        // Spacer(modifier = Modifier.weight(1f)) // Removed weight from scrollable column

                        // Bottom Tabs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            TextButton(onClick = { showQueue = true }) {
                                Text(androidx.compose.ui.res.stringResource(R.string.up_next), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { showLyrics = true }) {
                                Text(androidx.compose.ui.res.stringResource(R.string.lyrics), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { showRelated = true }) {
                                Text(androidx.compose.ui.res.stringResource(R.string.related), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
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

                // Pill Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    PillButton(
                        icon = Icons.Outlined.Share,
                        text = stringResource(R.string.share),
                        onClick = {
                            val sendIntent: Intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_message_template, uiState.currentTrack?.title, uiState.currentTrack?.artist, uiState.currentTrack?.videoId))
                                type = "text/plain"
                            }
                            val shareIntent = Intent.createChooser(sendIntent, null)
                            context.startActivity(shareIntent)
                        }
                    )
                    
                    val isDownloaded = uiState.downloadedTrackIds.contains(uiState.currentTrack?.videoId)
                    PillButton(
                        icon = if (isDownloaded) Icons.Filled.CheckCircle else Icons.Outlined.DownloadForOffline,
                        text = if (isDownloaded) stringResource(R.string.downloaded) else stringResource(R.string.download),
                        onClick = { 
                            if (!isDownloaded) viewModel.downloadTrack() 
                        }
                    )
                    PillButton(
                        icon = Icons.Outlined.PlaylistAdd,
                        text = stringResource(R.string.save),
                        onClick = { viewModel.showAddToPlaylistDialog(true) }
                    )
                    
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Seekbar
                Column(modifier = Modifier.fillMaxWidth()) {
                    Slider(
                        value = uiState.currentPosition.toFloat(),
                        onValueChange = { viewModel.seekTo(it.toLong()) },
                        valueRange = 0f..uiState.duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatTime(uiState.currentPosition),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            formatTime(uiState.duration),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            Icons.Outlined.Shuffle,
                            contentDescription = "Shuffle",
                            tint = if (uiState.shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                        )
                    }
                    
                    IconButton(
                        onClick = { viewModel.skipToPrevious() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipPrevious,
                            contentDescription = "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(72.dp),
                        onClick = { viewModel.togglePlayPause() }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (uiState.isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = Color.Black,
                                    strokeWidth = 3.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (uiState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                                    tint = Color.Black,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { viewModel.skipToNext() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Filled.SkipNext,
                            contentDescription = "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    IconButton(onClick = { viewModel.toggleRepeat() }) {
                        Icon(
                            when (uiState.repeatMode) {
                                RepeatMode.ONE -> Icons.Outlined.RepeatOne
                                else -> Icons.Outlined.Repeat
                            },
                            contentDescription = "Repeat",
                            tint = if (uiState.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
                
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
private fun UpNextBottomSheet(
    queue: List<MusicTrack>,
    currentIndex: Int,
    playingFrom: String,
    autoplayEnabled: Boolean,
    selectedFilter: String,
    onDismiss: () -> Unit,
    onTrackClick: (Int) -> Unit,
    onToggleAutoplay: () -> Unit,
    onFilterSelect: (String) -> Unit,
    onMoveTrack: (Int, Int) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Text(
                text = stringResource(R.string.up_next),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Playing From Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.playing_from),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = playingFrom,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Button(
                    onClick = { /* Save to playlist */ },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlaylistAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.save), style = MaterialTheme.typography.labelLarge)
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            // Autoplay Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.autoplay),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Switch(
                    checked = autoplayEnabled,
                    onCheckedChange = { onToggleAutoplay() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Filter Chips
            val filters = listOf(
                stringResource(R.string.view_all_button_label),
                stringResource(R.string.filter_discover),
                stringResource(R.string.filter_popular),
                stringResource(R.string.filter_deep_cuts),
                stringResource(R.string.filter_workout)
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(filters) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilterSelect(filter) },
                        label = { Text(filter) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                            labelColor = MaterialTheme.colorScheme.onSurface,
                            selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                            selectedLabelColor = MaterialTheme.colorScheme.surface
                        ),
                        border = null,
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Queue List
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxHeight(0.8f)
            ) {
                itemsIndexed(queue) { index, track ->
                    UpNextTrackItem(
                        track = track,
                        isCurrentlyPlaying = index == currentIndex,
                        onClick = { onTrackClick(index) },
                        onMoveUp = { if (index > 0) onMoveTrack(index, index - 1) },
                        onMoveDown = { if (index < queue.size - 1) onMoveTrack(index, index + 1) }
                    )
                }
            }
        }
    }
}

@Composable
private fun UpNextTrackItem(
    track: MusicTrack,
    isCurrentlyPlaying: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        AsyncImage(
            model = track.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrentlyPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.duration}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Reorder Controls
        Column {
            IconButton(
                onClick = onMoveUp,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.move_up),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
            IconButton(
                onClick = onMoveDown,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(R.string.move_down),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsBottomSheet(
    trackTitle: String,
    trackArtist: String,
    thumbnailUrl: String?,
    lyrics: String?,
    syncedLyrics: List<LyricLine>,
    currentPosition: Long,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    
    // Find current line index
    val currentLineIndex = remember(currentPosition, syncedLyrics) {
        val index = syncedLyrics.indexOfLast { it.time <= currentPosition }
        if (index == -1) 0 else index
    }
    
    // Auto-scroll to current line
    LaunchedEffect(currentLineIndex) {
        if (syncedLyrics.isNotEmpty()) {
            listState.animateScrollToItem(currentLineIndex, scrollOffset = -400)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color.Black,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) },
        windowInsets = WindowInsets(0)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Blurred background
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(100.dp),
                alpha = 0.35f,
                contentScale = ContentScale.Crop
            )
            
            // Dark overlay gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.7f),
                                Color.Black
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
            ) {
                // Header info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = trackArtist,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                
                Box(modifier = Modifier.weight(1f)) {
                    if (isLoading) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                    } else if (syncedLyrics.isNotEmpty()) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(32.dp),
                            contentPadding = PaddingValues(top = 100.dp, bottom = 200.dp, start = 24.dp, end = 24.dp)
                        ) {
                            itemsIndexed(syncedLyrics) { index, line ->
                                val isCurrent = index == currentLineIndex
                                val alpha by animateFloatAsState(
                                    targetValue = if (isCurrent) 1f else 0.4f,
                                    animationSpec = tween(durationMillis = 600)
                                )
                                val scale by animateFloatAsState(
                                    targetValue = if (isCurrent) 1.08f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                )
                                
                                Text(
                                    text = line.content,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Bold,
                                        fontSize = 28.sp,
                                        lineHeight = 38.sp,
                                        letterSpacing = (-0.5).sp
                                    ),
                                    color = Color.White,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .graphicsLayer {
                                            this.alpha = alpha
                                            this.scaleX = scale
                                            this.scaleY = scale
                                        }
                                        .clickable { onSeekTo(line.time) },
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    } else if (lyrics != null) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(24.dp)
                        ) {
                            item {
                                Text(
                                    text = lyrics,
                                    style = MaterialTheme.typography.headlineSmall.copy(
                                        lineHeight = 36.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White.copy(alpha = 0.9f),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(stringResource(R.string.lyrics_not_available), color = Color.White.copy(alpha = 0.6f))
                        }
                    }
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
                    contentDescription = stringResource(R.string.playing),
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.remove),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun PillButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.1f),
        onClick = onClick,
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White
            )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RelatedBottomSheet(
    relatedTracks: List<MusicTrack>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onTrackClick: (MusicTrack) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.background,
        scrimColor = Color.Black.copy(alpha = 0.6f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .padding(horizontal = 20.dp)
        ) {
            Text(
                stringResource(R.string.related_content),
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (relatedTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_related_content), color = Color.White.copy(alpha = 0.6f))
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    items(relatedTracks) { track ->
                        RelatedTrackItem(
                            track = track,
                            onClick = { onTrackClick(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RelatedTrackItem(
    track: MusicTrack,
    onClick: () -> Unit
) {
    var showMoreOptions by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.size(56.dp)
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        IconButton(onClick = { showMoreOptions = true }) {
            Icon(
                Icons.Outlined.MoreVert,
                contentDescription = stringResource(R.string.more_options),
                tint = Color.White.copy(alpha = 0.6f)
            )
        }
    }
    
    if (showMoreOptions) {
        com.flow.youtube.ui.components.MusicQuickActionsSheet(
            track = track,
            onDismiss = { showMoreOptions = false }
        )
    }
}

@Composable
fun TrackInfoDialog(
    track: MusicTrack,
    onDismiss: () -> Unit
) {
    val player = EnhancedMusicPlayerManager.player
    val audioFormat = player?.audioFormat
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.track_details)) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                InfoRow(stringResource(R.string.title_label), track.title)
                InfoRow(stringResource(R.string.artist_label), track.artist)
                if (track.album.isNotEmpty()) {
                    InfoRow(stringResource(R.string.album_label), track.album)
                }
                InfoRow(stringResource(R.string.video_id_label), track.videoId)
                
                Divider()
                
                if (audioFormat != null) {
                    InfoRow(stringResource(R.string.codec_label), audioFormat.sampleMimeType ?: "Unknown")
                    InfoRow(stringResource(R.string.sample_rate_label), "${audioFormat.sampleRate} ${stringResource(R.string.hz)}")
                    if (audioFormat.bitrate > 0) {
                        InfoRow(stringResource(R.string.bitrate_label), "${audioFormat.bitrate / 1000} ${stringResource(R.string.kbps)}")
                    }
                    InfoRow(stringResource(R.string.channels_label), audioFormat.channelCount.toString())
                } else {
                    Text(
                        stringResource(R.string.audio_info_not_available),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
