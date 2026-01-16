package com.flow.youtube.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flow.youtube.ui.theme.extendedColors
import androidx.compose.ui.res.vectorResource
import com.flow.youtube.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToMusicPlaylists: () -> Unit,
    onNavigateToLikedVideos: () -> Unit,
    onNavigateToWatchLater: () -> Unit,
    onNavigateToSavedShorts: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onManageData: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
    // Initialize view model
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = "Library",
                        style = MaterialTheme.typography.headlineMedium
                    ) 
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Navigation Items
            item {
                LibrarySectionHeader("Your Content")
            }

            item {
                LibraryCard(
                    icon = Icons.Outlined.History,
                    title = "History",
                    subtitle = "${uiState.watchHistoryCount} videos",
                    onClick = onNavigateToHistory
                )
            }

            item {
                LibraryCard(
                    icon = Icons.Outlined.PlaylistPlay,
                    title = "Playlists",
                    subtitle = "${uiState.playlistsCount} playlists",
                    onClick = onNavigateToPlaylists
                )
            }

            item {
                LibraryCard(
                    icon = Icons.Outlined.QueueMusic,
                    title = "Music Playlists",
                    subtitle = "Your music collections",
                    onClick = onNavigateToMusicPlaylists
                )
            }

            item {
                LibraryCard(
                    icon = Icons.Outlined.ThumbUp,
                    title = "Liked Videos",
                    subtitle = "${uiState.likedVideosCount} videos",
                    onClick = onNavigateToLikedVideos
                )
            }

            item {
                LibraryCard(
                    icon = ImageVector.vectorResource(id = R.drawable.ic_shorts),
                    title = "Saved Shorts",
                    subtitle = "${uiState.savedShortsCount} shorts",
                    onClick = onNavigateToSavedShorts
                )
            }

            item {
                LibraryCard(
                    icon = Icons.Outlined.WatchLater,
                    title = "Watch Later",
                    subtitle = "${uiState.watchLaterCount} videos",
                    onClick = onNavigateToWatchLater
                )
            }

            item {
                LibraryCard(
                    icon = Icons.Outlined.Download,
                    title = "Downloads",
                    subtitle = "0 videos", // Placeholder
                    onClick = onNavigateToDownloads
                )
            }
            
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
            }
            
            item {
                LibrarySectionHeader("Settings & Data")
            }
            
            item {
                LibraryCard(
                    icon = Icons.Outlined.Storage,
                    title = "Manage Data",
                    subtitle = "Clear cache and history",
                    onClick = onManageData
                )
            }
        }
    }
}

@Composable
private fun LibrarySectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Icon Container
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}
