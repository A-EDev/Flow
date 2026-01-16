package com.flow.youtube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.MusicTrackRow
import com.flow.youtube.ui.screens.music.MusicPlayerViewModel
import com.flow.youtube.ui.screens.music.AddToPlaylistDialog
import com.flow.youtube.ui.screens.music.CreatePlaylistDialog
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicQuickActionsSheet(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onViewArtist: () -> Unit = {},
    onViewAlbum: () -> Unit = {},
    onShare: () -> Unit = {},
    onInfoClick: () -> Unit = {},
    viewModel: MusicPlayerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    
    // Dialogs
    if (uiState.showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { viewModel.showCreatePlaylistDialog(false) },
            onConfirm = { name, desc ->
                viewModel.createPlaylist(name, desc, track)
            }
        )
    }
    
    if (uiState.showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            playlists = uiState.playlists,
            onDismiss = { viewModel.showAddToPlaylistDialog(false) },
            onSelectPlaylist = { playlistId ->
                viewModel.addToPlaylist(playlistId, track)
            },
            onCreateNew = {
                viewModel.showAddToPlaylistDialog(false)
                viewModel.showCreatePlaylistDialog(true)
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            // Header with track info - using a simplified version or the existing row
            // We can't easily reuse MusicTrackRow if it has specific padding/clicks we don't want, 
            // but let's try to use it for consistency.
            MusicTrackRow(
                track = track,
                onClick = {}, // No action on click in header
                onMenuClick = {} // No menu in header
            )
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Actions
            MusicQuickActionItem(
                icon = Icons.Outlined.PlaylistAdd,
                text = "Add to playlist",
                onClick = {
                    viewModel.showAddToPlaylistDialog(true)
                }
            )
            
            MusicQuickActionItem(
                icon = Icons.Outlined.QueueMusic,
                text = "Play next",
                onClick = {
                    viewModel.playNext(track)
                    onDismiss()
                }
            )

            MusicQuickActionItem(
                icon = Icons.Outlined.PlaylistPlay,
                text = "Add to queue",
                onClick = {
                    viewModel.addToQueue(track)
                    onDismiss()
                }
            )

            val isDownloaded = uiState.downloadedTrackIds.contains(track.videoId)
            MusicQuickActionItem(
                icon = if (isDownloaded) Icons.Outlined.CheckCircle else Icons.Outlined.Download,
                text = if (isDownloaded) "Downloaded" else "Download",
                onClick = {
                    if (!isDownloaded) {
                        viewModel.downloadTrack(track)
                    }
                    onDismiss()
                }
            )
            
            if (track.channelId.isNotEmpty()) {
                MusicQuickActionItem(
                    icon = Icons.Outlined.Person,
                    text = "View Artist",
                    onClick = {
                        onViewArtist()
                        onDismiss()
                    }
                )
            }
            
            if (track.album.isNotEmpty()) {
                MusicQuickActionItem(
                    icon = Icons.Outlined.Album,
                    text = "View Album",
                    onClick = {
                        onViewAlbum()
                        onDismiss()
                    }
                )
            }
            
            MusicQuickActionItem(
                icon = Icons.Outlined.Info,
                text = "Info & Details",
                onClick = {
                    onInfoClick()
                    onDismiss()
                }
            )

            MusicQuickActionItem(
                icon = Icons.Outlined.Share,
                text = "Share",
                onClick = {
                    onShare()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun MusicQuickActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
