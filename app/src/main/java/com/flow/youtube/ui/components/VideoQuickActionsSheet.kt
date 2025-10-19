package com.flow.youtube.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.flow.youtube.data.model.Video

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoQuickActionsBottomSheet(
    video: Video,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onWatchLater: () -> Unit = {},
    onShare: () -> Unit = {},
    onDownload: () -> Unit = {},
    onNotInterested: () -> Unit = {},
    onReport: () -> Unit = {}
) {
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
            val actions = listOf(
                QuickAction(Icons.Outlined.PlaylistAdd, "Add to playlist") { onAddToPlaylist() },
                QuickAction(Icons.Outlined.WatchLater, "Save to Watch Later") { onWatchLater() },
                QuickAction(Icons.Outlined.Share, "Share") { onShare() },
                QuickAction(Icons.Outlined.Download, "Download") { onDownload() },
                QuickAction(Icons.Outlined.ThumbDown, "Not interested") { onNotInterested() },
                QuickAction(Icons.Outlined.Flag, "Report") { onReport() }
            )
            
            actions.forEach { action ->
                QuickActionItem(
                    icon = action.icon,
                    text = action.text,
                    onClick = {
                        action.onClick()
                        onDismiss()
                    }
                )
            }
        }
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
