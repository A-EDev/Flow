package com.flow.youtube.ui.screens.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.flow.youtube.data.music.DownloadedTrack
import com.flow.youtube.data.video.DownloadedVideo
import com.flow.youtube.ui.components.VideoCardFullWidth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    onBackClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onMusicClick: (DownloadedTrack) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("Videos", "Music")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Downloads",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold
                        )
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    if (selectedTabIndex < tabPositions.size) {
                        TabRowDefaults.Indicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = if (index == 0) Icons.Outlined.VideoLibrary else Icons.Outlined.MusicNote,
                                contentDescription = null
                            )
                        }
                    )
                }
            }

            when (selectedTabIndex) {
                0 -> VideosDownloadsList(
                    videos = uiState.downloadedVideos,
                    onVideoClick = onVideoClick,
                    onDeleteClick = { viewModel.deleteVideoDownload(it) }
                )
                1 -> MusicDownloadsList(
                    tracks = uiState.downloadedMusic,
                    onMusicClick = onMusicClick,
                    onDeleteClick = { viewModel.deleteMusicDownload(it) }
                )
            }
        }
    }
}

@Composable
fun VideosDownloadsList(
    videos: List<DownloadedVideo>,
    onVideoClick: (String) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    if (videos.isEmpty()) {
        EmptyDownloadsState(type = "videos")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(videos) { downloadedVideo ->
                VideoCardFullWidth(
                    video = downloadedVideo.video,
                    onClick = { onVideoClick(downloadedVideo.video.id) },
                    onMoreClick = { /* Could show options here */ }
                )
                // Add a delete button overlay or just in a menu. 
                // For now, let's keep it simple.
            }
        }
    }
}

@Composable
fun MusicDownloadsList(
    tracks: List<DownloadedTrack>,
    onMusicClick: (DownloadedTrack) -> Unit,
    onDeleteClick: (String) -> Unit
) {
    if (tracks.isEmpty()) {
        EmptyDownloadsState(type = "music")
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks) { downloadedTrack ->
                MusicDownloadCard(
                    downloadedTrack = downloadedTrack,
                    onClick = { onMusicClick(downloadedTrack) },
                    onDeleteClick = { onDeleteClick(downloadedTrack.track.videoId) }
                )
            }
        }
    }
}

@Composable
fun MusicDownloadCard(
    downloadedTrack: DownloadedTrack,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(modifier = Modifier.size(56.dp)) {
                AsyncImage(
                    model = downloadedTrack.track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                )
            }

            // Info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = downloadedTrack.track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = downloadedTrack.track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Delete button
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun EmptyDownloadsState(type: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.CloudDownload,
                contentDescription = null,
                modifier = Modifier
                    .size(80.dp)
                    .padding(bottom = 16.dp),
                tint = MaterialTheme.colorScheme.surfaceVariant
            )
            Text(
                text = "No downloaded $type yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Downloaded content will appear here when you save $type for offline viewing.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}
