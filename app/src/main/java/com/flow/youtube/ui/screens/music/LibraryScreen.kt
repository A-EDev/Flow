package com.flow.youtube.ui.screens.music

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

/**
 * Library Screen - Shows Playlists, Favorites, and Downloads
 */
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBackClick: () -> Unit,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Playlists", "Favorites", "History", "Downloads")
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Library",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                }
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
            
            // Content
            AnimatedContent(
                targetState = selectedTab,
                label = "library_content"
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> PlaylistsTab(
                        playlists = uiState.playlists,
                        onPlaylistClick = { /* TODO */ },
                        onCreatePlaylist = { viewModel.showCreatePlaylistDialog(true) }
                    )
                    1 -> FavoritesTab(
                        favorites = uiState.favorites,
                        onTrackClick = onTrackClick,
                        onRemoveFavorite = { viewModel.removeFromFavorites(it.videoId) }
                    )
                    2 -> HistoryTab(
                        history = uiState.history,
                        onTrackClick = onTrackClick,
                        onClearHistory = { viewModel.clearHistory() }
                    )
                    3 -> DownloadsTab(
                        downloads = uiState.downloads,
                        onTrackClick = onTrackClick,
                        onDeleteDownload = { viewModel.deleteDownload(it.track.videoId) },
                        downloadProgress = uiState.downloadProgress,
                        downloadStatus = uiState.downloadStatus
                    )
                }
            }
        }
    }
    
    // Create Playlist Dialog
    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.showCreatePlaylistDialog(false) },
            onConfirm = { name, desc ->
                viewModel.createPlaylist(name, desc)
            }
        )
    }
}

@Composable
private fun PlaylistsTab(
    playlists: List<com.flow.youtube.data.music.Playlist>,
    onPlaylistClick: (com.flow.youtube.data.music.Playlist) -> Unit,
    onCreatePlaylist: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Create New Playlist Card
        item {
            Surface(
                onClick = onCreatePlaylist,
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    
                    Column {
                        Text(
                            "Create New Playlist",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Organize your music",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
        
        if (playlists.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Outlined.QueueMusic,
                    title = "No Playlists Yet",
                    subtitle = "Create your first playlist to organize your favorite tracks"
                )
            }
        } else {
            items(playlists) { playlist ->
                PlaylistCard(
                    playlist = playlist,
                    onClick = { onPlaylistClick(playlist) }
                )
            }
        }
    }
}

@Composable
private fun FavoritesTab(
    favorites: List<MusicTrack>,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onRemoveFavorite: (MusicTrack) -> Unit
) {
    if (favorites.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.FavoriteBorder,
            title = "No Favorites Yet",
            subtitle = "Tap the heart icon on any track to add it to your favorites"
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${favorites.size} favorite tracks",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            
            items(favorites) { track ->
                CompactSleekCard(
                    track = track,
                    onClick = { onTrackClick(track, favorites) },
                    onMoreClick = { onRemoveFavorite(track) }
                )
            }
        }
    }
}

@Composable
private fun HistoryTab(
    history: List<MusicTrack>,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onClearHistory: () -> Unit
) {
    if (history.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.History,
            title = "No History Yet",
            subtitle = "Start listening to music to see your history here"
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Recently Played",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    
                    TextButton(onClick = onClearHistory) {
                        Text("Clear All")
                    }
                }
            }
            
            items(history) { track ->
                CompactSleekCard(
                    track = track,
                    onClick = { onTrackClick(track, history) },
                    onMoreClick = { /* TODO: Show options */ }
                )
            }
        }
    }
}

@Composable
private fun DownloadsTab(
    downloads: List<com.flow.youtube.data.music.DownloadedTrack>,
    onTrackClick: (MusicTrack, List<MusicTrack>) -> Unit,
    onDeleteDownload: (com.flow.youtube.data.music.DownloadedTrack) -> Unit,
    downloadProgress: Map<String, Int>,
    downloadStatus: Map<String, com.flow.youtube.data.music.DownloadStatus>
) {
    if (downloads.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Download,
            title = "No Downloads Yet",
            subtitle = "Download tracks for offline playback"
        )
    } else {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                DownloadStatsCard(downloads)
            }
            
            items(downloads) { download ->
                DownloadedTrackCard(
                    download = download,
                    onClick = { onTrackClick(download.track, downloads.map { it.track }) },
                    onDelete = { onDeleteDownload(download) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: com.flow.youtube.data.music.Playlist,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Playlist Cover
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (playlist.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(Modifier.height(4.dp))
                
                Text(
                    "${playlist.trackCount} tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                if (playlist.description.isNotBlank()) {
                    Text(
                        playlist.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
            
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
private fun DownloadedTrackCard(
    download: com.flow.youtube.data.music.DownloadedTrack,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
            ) {
                AsyncImage(
                    model = download.track.thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
                
                // Offline badge
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(12.dp)
                    )
                }
            }
            
            Spacer(Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    download.track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    download.track.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1
                )
                Text(
                    formatFileSize(download.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun DownloadStatsCard(downloads: List<com.flow.youtube.data.music.DownloadedTrack>) {
    val totalSize = downloads.sumOf { it.fileSize }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${downloads.size}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    formatFileSize(totalSize),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    "Total Size",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
    }
}
