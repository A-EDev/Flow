package com.flow.youtube.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flow.youtube.data.local.PlaylistRepository
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.screens.playlists.PlaylistInfo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    video: Video,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PlaylistRepository(context) }
    
    var playlists by remember { mutableStateOf<List<PlaylistInfo>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var watchLaterVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    
    // Load playlists and watch later
    LaunchedEffect(Unit) {
        launch {
            repo.getAllPlaylistsFlow().collect { playlists = it }
        }
        launch {
            repo.getWatchLaterVideosFlow().collect { watchLaterVideos = it }
        }
    }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Text(
                text = "Save to...",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            
            Divider()
            
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Watch Later
                item {
                    val isInWatchLater = watchLaterVideos.any { it.id == video.id }
                    PlaylistItem(
                        icon = Icons.Outlined.WatchLater,
                        name = "Watch later",
                        isChecked = isInWatchLater,
                        onClick = {
                            scope.launch {
                                if (isInWatchLater) {
                                    repo.removeFromWatchLater(video.id)
                                } else {
                                    repo.addToWatchLater(video)
                                }
                            }
                        }
                    )
                }
                
                // Divider
                item {
                    Divider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                
                // User Playlists
                items(
                    items = playlists,
                    key = { it.id }
                ) { playlist ->
                    var playlistVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
                    
                    LaunchedEffect(playlist.id) {
                        repo.getPlaylistVideosFlow(playlist.id).collect {
                            playlistVideos = it
                        }
                    }
                    
                    val isInPlaylist = playlistVideos.any { it.id == video.id }
                    
                    PlaylistItemWithThumbnail(
                        thumbnail = playlist.thumbnailUrl,
                        name = playlist.name,
                        privacy = if (playlist.isPrivate) "Private" else "Public",
                        isChecked = isInPlaylist,
                        onClick = {
                            scope.launch {
                                if (isInPlaylist) {
                                    repo.removeVideoFromPlaylist(playlist.id, video.id)
                                } else {
                                    repo.addVideoToPlaylist(playlist.id, video)
                                }
                            }
                        }
                    )
                }
                
                // Create New Playlist
                item {
                    Spacer(Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showCreateDialog = true }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Add,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Create new playlist",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
    
    // Create Playlist Dialog
    if (showCreateDialog) {
        CreateNewPlaylistDialog(
            initialVideo = video,
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description, isPrivate ->
                scope.launch {
                    val playlistId = System.currentTimeMillis().toString()
                    repo.createPlaylist(playlistId, name, description, isPrivate)
                    repo.addVideoToPlaylist(playlistId, video)
                    showCreateDialog = false
                    onDismiss()
                }
            }
        )
    }
}

@Composable
private fun PlaylistItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    name: String,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
        
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onClick() }
        )
    }
}

@Composable
private fun PlaylistItemWithThumbnail(
    thumbnail: String,
    name: String,
    privacy: String,
    isChecked: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (thumbnail.isNotEmpty()) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }
        
        // Info
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = privacy,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onClick() }
        )
    }
}

@Composable
private fun CreateNewPlaylistDialog(
    initialVideo: Video,
    onDismiss: () -> Unit,
    onCreate: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Outlined.PlaylistAdd, null)
        },
        title = {
            Text("Create new playlist")
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPrivate) Icons.Filled.Lock else Icons.Filled.Public,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isPrivate) "Private" else "Public",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Switch(
                        checked = isPrivate,
                        onCheckedChange = { isPrivate = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim(), description.trim(), isPrivate)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
