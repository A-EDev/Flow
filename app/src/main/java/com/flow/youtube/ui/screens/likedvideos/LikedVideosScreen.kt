package com.flow.youtube.ui.screens.likedvideos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.flow.youtube.data.local.LikedVideoInfo
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.components.AddToPlaylistDialog
import com.flow.youtube.ui.components.MusicQuickActionsSheet
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.MusicTrackRow
import com.flow.youtube.ui.theme.extendedColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LikedVideosScreen(
    onVideoClick: (MusicTrack) -> Unit,
    onBackClick: () -> Unit,
    onArtistClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    isMusic: Boolean = false,
    viewModel: LikedVideosViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    var showBottomSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    
    // Initialize
    LaunchedEffect(Unit) {
        viewModel.initialize(context, isMusic)
    }
    
    if (showBottomSheet && selectedTrack != null) {
        MusicQuickActionsSheet(
            track = selectedTrack!!,
            onDismiss = { showBottomSheet = false },
            onAddToPlaylist = { showAddToPlaylistDialog = true },
            onDownload = { /* TODO: Implement download */ },
            onViewArtist = { 
                if (selectedTrack!!.channelId.isNotEmpty()) {
                    onArtistClick(selectedTrack!!.channelId)
                }
            },
            onViewAlbum = { /* TODO: Implement view album */ },
            onShare = { /* TODO: Implement share */ }
        )
    }
    
    if (showAddToPlaylistDialog && selectedTrack != null) {
        AddToPlaylistDialog(
            video = Video(
                id = selectedTrack!!.videoId,
                title = selectedTrack!!.title,
                channelName = selectedTrack!!.artist,
                channelId = selectedTrack!!.channelId,
                thumbnailUrl = selectedTrack!!.thumbnailUrl,
                duration = selectedTrack!!.duration,
                viewCount = selectedTrack!!.views,
                uploadDate = "",
                isMusic = true
            ),
            onDismiss = { showAddToPlaylistDialog = false }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (isMusic) "Liked Music" else "Liked Videos",
                        style = MaterialTheme.typography.headlineMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                
                uiState.likedVideos.isEmpty() -> {
                    EmptyLikedVideosState(modifier = Modifier.fillMaxSize())
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(
                            items = uiState.likedVideos,
                            key = { it.videoId }
                        ) { video ->
                            if (isMusic) {
                                MusicTrackRow(
                                    track = MusicTrack(
                                        videoId = video.videoId,
                                        title = video.title,
                                        artist = video.channelName,
                                        thumbnailUrl = video.thumbnail,
                                        duration = 0,
                                        channelId = "" // LikedVideoInfo doesn't have channelId, might need to fetch or store it
                                    ),
                                    onClick = { 
                                        onVideoClick(
                                            MusicTrack(
                                                videoId = video.videoId,
                                                title = video.title,
                                                artist = video.channelName,
                                                thumbnailUrl = video.thumbnail,
                                                duration = 0,
                                                channelId = ""
                                            )
                                        ) 
                                    },
                                    onMenuClick = {
                                        selectedTrack = MusicTrack(
                                            videoId = video.videoId,
                                            title = video.title,
                                            artist = video.channelName,
                                            thumbnailUrl = video.thumbnail,
                                            duration = 0
                                        )
                                        showBottomSheet = true
                                    }
                                )
                            } else {
                                LikedVideoCard(
                                    video = video,
                                    onClick = { 
                                        onVideoClick(
                                            MusicTrack(
                                                videoId = video.videoId,
                                                title = video.title,
                                                artist = video.channelName,
                                                thumbnailUrl = video.thumbnail,
                                                duration = 0,
                                                channelId = ""
                                            )
                                        ) 
                                    },
                                    onUnlikeClick = { viewModel.removeLike(video.videoId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LikedVideoCard(
    video: LikedVideoInfo,
    onClick: () -> Unit,
    onUnlikeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            
            // Video info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Text(
                            text = formatTimestamp(video.likedAt),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                // Unlike button
                IconButton(
                    onClick = onUnlikeClick,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.End)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ThumbUp,
                        contentDescription = "Unlike",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyLikedVideosState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.ThumbUp,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Liked Videos",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Videos you like will appear here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val weeks = days / 7
    val months = days / 30
    
    return when {
        months > 0 -> "$months month${if (months > 1) "s" else ""} ago"
        weeks > 0 -> "$weeks week${if (weeks > 1) "s" else ""} ago"
        days > 0 -> "$days day${if (days > 1) "s" else ""} ago"
        hours > 0 -> "$hours hour${if (hours > 1) "s" else ""} ago"
        minutes > 0 -> "$minutes minute${if (minutes > 1) "s" else ""} ago"
        else -> "Just now"
    }
}
