package com.flow.youtube.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.WatchLater
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.data.model.Video

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQuickActionsBottomSheet(
    video: Video,
    onDismiss: () -> Unit,
    onWatchLater: (() -> Unit)? = null,
    onShare: () -> Unit = {},
    onDownload: (() -> Unit)? = null,
    onNotInterested: () -> Unit = {},
    viewModel: QuickActionsViewModel = hiltViewModel()
) {
    val watchLaterIds by viewModel.watchLaterIds.collectAsState()
    val isInWatchLater = remember(watchLaterIds, video.id) { watchLaterIds.contains(video.id) }
    
    var showAddToPlaylistDialog by remember { mutableStateOf(false) }
    var showMediaInfo by remember { mutableStateOf(false) }

    // Dialogs
    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            video = video,
            onDismiss = { 
                showAddToPlaylistDialog = false
                onDismiss()
            }
        )
    }

    if (showMediaInfo) {
        MediaInfoDialog(
            video = video,
            onDismiss = { showMediaInfo = false }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Video info header
            item {
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
            }
            
            // Action Grid
            item {
                FlowActionGrid(
                    actions = listOf(
                        FlowAction(
                            icon = { Icon(Icons.Outlined.PlaylistAdd, null) },
                            text = "Save to playlist",
                            onClick = { showAddToPlaylistDialog = true }
                        ),
                        FlowAction(
                            icon = { 
                                Icon(
                                    if (isInWatchLater) Icons.Default.WatchLater else Icons.Outlined.WatchLater,
                                    null,
                                    tint = if (isInWatchLater) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            text = if (isInWatchLater) "Unsave Watch Later" else "Watch Later",
                            onClick = {
                                if (onWatchLater != null) {
                                    onWatchLater()
                                } else {
                                    viewModel.toggleWatchLater(video)
                                }
                                onDismiss()
                            }
                        ),
                        FlowAction(
                            icon = { Icon(Icons.Outlined.Share, null) },
                            text = "Share",
                            onClick = {
                                onShare()
                                onDismiss()
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // Other Actions Group
            item {
                FlowMenuGroup(
                    items = listOf(
                         FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.Download, null) },
                            title = { Text("Download") },
                            onClick = {
                                if (onDownload != null) {
                                    onDownload()
                                } else {
                                    viewModel.downloadVideo(video.id, video.title)
                                }
                                onDismiss()
                            }
                        ),
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.ThumbDown, null) },
                            title = { Text("Not interested") },
                            onClick = {
                                viewModel.markNotInterested(video)
                                onNotInterested()
                                onDismiss()
                            }
                        ),
                        FlowMenuItemData(
                            icon = { Icon(Icons.Outlined.Info, null) },
                            title = { Text("Details & Metadata") },
                            onClick = {
                                showMediaInfo = true
                            }
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
