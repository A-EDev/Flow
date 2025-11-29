package com.flow.youtube.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flow.youtube.ui.theme.extendedColors

@Composable
fun LibraryScreen(
    onNavigateToHistory: () -> Unit,
    onNavigateToPlaylists: () -> Unit,
    onNavigateToLikedVideos: () -> Unit,
    onNavigateToWatchLater: () -> Unit,
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Text(
            text = "Library",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // History
            item {
                LibraryItem(
                    icon = Icons.Outlined.History,
                    title = "History",
                    subtitle = if (uiState.watchHistoryCount > 0) {
                        "${uiState.watchHistoryCount} video${if (uiState.watchHistoryCount > 1) "s" else ""} watched"
                    } else {
                        "Watch your viewing history"
                    },
                    count = uiState.watchHistoryCount,
                    onClick = onNavigateToHistory
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Playlists
            item {
                LibraryItem(
                    icon = Icons.Outlined.PlaylistPlay,
                    title = "Playlists",
                    subtitle = if (uiState.playlistsCount > 0) {
                        "${uiState.playlistsCount} playlist${if (uiState.playlistsCount > 1) "s" else ""}"
                    } else {
                        "View and manage your playlists"
                    },
                    count = uiState.playlistsCount,
                    onClick = onNavigateToPlaylists
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Liked Videos
            item {
                LibraryItem(
                    icon = Icons.Outlined.ThumbUp,
                    title = "Liked Videos",
                    subtitle = if (uiState.likedVideosCount > 0) {
                        "${uiState.likedVideosCount} video${if (uiState.likedVideosCount > 1) "s" else ""} liked"
                    } else {
                        "Videos you've liked"
                    },
                    count = uiState.likedVideosCount,
                    onClick = onNavigateToLikedVideos
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Watch Later
            item {
                LibraryItem(
                    icon = Icons.Outlined.WatchLater,
                    title = "Watch Later",
                    subtitle = if (uiState.watchLaterCount > 0) {
                        "${uiState.watchLaterCount} video${if (uiState.watchLaterCount > 1) "s" else ""} saved"
                    } else {
                        "Save videos to watch later"
                    },
                    count = uiState.watchLaterCount,
                    onClick = onNavigateToWatchLater
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }

            // Downloads
            item {
                LibraryItem(
                    icon = Icons.Outlined.Download,
                    title = "Downloads",
                    subtitle = if (uiState.downloadsCount > 0) {
                        "${uiState.downloadsCount} video${if (uiState.downloadsCount > 1) "s" else ""} downloaded"
                    } else {
                        "Saved for offline viewing"
                    },
                    count = uiState.downloadsCount,
                    onClick = onNavigateToDownloads
                )
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }

            // Data Management Section
            item {
                Divider(
                    color = MaterialTheme.extendedColors.border,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }

            item {
                TextButton(
                    onClick = onManageData,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Manage Data",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    count: Int = 0,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (count > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        ) {
                            Text(
                                text = count.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary
                )
            }

            Icon(
                imageVector = Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.extendedColors.textSecondary
            )
        }
    }
}
