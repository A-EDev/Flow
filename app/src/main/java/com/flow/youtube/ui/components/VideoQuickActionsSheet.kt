package com.flow.youtube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.flow.youtube.data.local.PlaylistRepository
import com.flow.youtube.data.model.Video
import kotlinx.coroutines.launch

import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQuickActionsBottomSheet(
    video: Video,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onWatchLater: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onDownload: (() -> Unit)? = null,
    onNotInterested: () -> Unit = {},
    onReport: () -> Unit = {},
    viewModel: QuickActionsViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PlaylistRepository(context) }
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Video info header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Divider(modifier = Modifier.padding(vertical = 8.dp))
            
            // Action items
            QuickActionItem(
                icon = Icons.Outlined.PlaylistAdd,
                text = "Save to playlist",
                onClick = {
                    showAddToPlaylistDialog = true
                }
            )
            
            QuickActionItem(
                icon = Icons.Outlined.WatchLater,
                text = "Save to Watch later",
                onClick = {
                    if (onWatchLater != null) {
                        onWatchLater()
                    } else {
                        scope.launch {
                            repo.addToWatchLater(video)
                        }
                    }
                    onDismiss()
                }
            )
            
            QuickActionItem(
                icon = Icons.Outlined.Share,
                text = "Share",
                onClick = {
                    onShare()
                    onDismiss()
                }
            )
            
            QuickActionItem(
                icon = Icons.Outlined.Download,
                text = "Download",
                onClick = {
                    if (onDownload != null) {
                        onDownload()
                    } else {
                        viewModel.downloadVideo(video.id, video.title)
                    }
                    onDismiss()
                }
            )
            
            QuickActionItem(
                icon = Icons.Outlined.ThumbDown,
                text = "Not interested",
                onClick = {
                    onNotInterested()
                    onDismiss()
                }
            )
            
            QuickActionItem(
                icon = Icons.Outlined.Flag,
                text = "Report",
                onClick = {
                    onReport()
                    onDismiss()
                }
            )
        }
    }
    
    // Add to Playlist Dialog
    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            video = video,
            onDismiss = { 
                showAddToPlaylistDialog = false
                onDismiss()
            }
        )
    }
}

@Composable
private fun QuickActionItem(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private data class QuickAction(
    val icon: ImageVector,
    val text: String,
    val onClick: () -> Unit
)
