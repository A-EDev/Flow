package io.github.aedev.flow.ui.screens.playlists

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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import coil.compose.AsyncImage
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.Video
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onBackClick: () -> Unit,
    onPlaylistClick: (PlaylistInfo) -> Unit,
    onNavigateToWatchLater: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToDelete by remember { mutableStateOf<PlaylistInfo?>(null) }
    
    Scaffold(
        topBar = {
            PlaylistLibraryTopBar(
                title = stringResource(R.string.library_playlists_label),
                onBackClick = onBackClick
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.new_playlist_button))
            }
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // User playlists section
                    if (uiState.playlists.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.your_playlists_header),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }
                        
                        items(uiState.playlists) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                onDeleteClick = { playlistToDelete = playlist }
                            )
                        }
                    } else {
                        item {
                            EmptyPlaylistsState(
                                onCreateClick = { showCreateDialog = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 32.dp)
                            )
                        }
                    }

                    if (uiState.savedPlaylists.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.saved_playlists_header),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                            )
                        }

                        items(uiState.savedPlaylists) { playlist ->
                            PlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist) },
                                onDeleteClick = { playlistToDelete = playlist }
                            )
                        }
                    }

                    // Bottom padding for FAB
                    item {
                        Spacer(Modifier.height(80.dp))
                    }
                }
            }
        }
    }
    
    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { name, description ->
                viewModel.createPlaylist(name, description)
                showCreateDialog = false
            }
        )
    }

    if (playlistToDelete != null) {
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text(stringResource(R.string.delete_playlist_dialog_title)) },
            text = { Text(stringResource(R.string.delete_playlist_dialog_text, playlistToDelete!!.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deletePlaylist(playlistToDelete!!.id)
                        playlistToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun PlaylistLibraryTopBar(
    title: String,
    onBackClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.Default.ArrowBack, stringResource(R.string.btn_back))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: PlaylistInfo,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(142.dp)
                .aspectRatio(16f / 9f)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .offset(x = (-3).dp, y = (-3).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f))
            ) {
                if (playlist.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(10.dp),
                        contentScale = ContentScale.Crop,
                        alpha = 0.7f
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.18f))
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (playlist.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = playlist.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier
                            .size(30.dp)
                            .align(Alignment.Center),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.74f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 7.dp, vertical = 5.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = playlist.videoCount.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = buildString {
                    append(if (playlist.isPrivate) stringResource(R.string.playlist_private) else stringResource(R.string.playlist_public))
                    append(" • Playlist")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (playlist.description.isNotBlank()) {
                Text(
                    text = playlist.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Open") },
                    onClick = {
                        showMenu = false
                        onClick()
                    },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.action_delete)) },
                    onClick = {
                        showMenu = false
                        onDeleteClick()
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Delete,
                            null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyPlaylistsState(
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Outlined.PlaylistAdd,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.no_playlists_found),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.create_playlist_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onCreateClick) {
            Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.create_new_playlist))
        }
    }
}

@Composable
private fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Default.PlaylistAdd, null, tint = MaterialTheme.colorScheme.primary)
        },
        title = {
            Text(stringResource(R.string.create_new_playlist), style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    placeholder = { Text(stringResource(R.string.my_playlist_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.playlist_description_optional)) },
                    placeholder = { Text(stringResource(R.string.add_description_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp)
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
                            imageVector = if (isPrivate) Icons.Default.Lock else Icons.Default.Public,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = if (isPrivate) stringResource(R.string.playlist_private) else stringResource(R.string.playlist_public),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
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
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onConfirm(name, description)
                    }
                },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

// ViewModel

@HiltViewModel
class PlaylistsViewModel @Inject constructor(
    private val repo: PlaylistRepository
) : ViewModel() {
    
    private val _uiState = MutableStateFlow(PlaylistsUiState())
    val uiState: StateFlow<PlaylistsUiState> = _uiState.asStateFlow()
    
    init {
        loadPlaylists()
    }
    
    // fun initialize(context: android.content.Context) { ... } // Removed

    private fun loadPlaylists() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            
            // Load watch later count
            launch {
                repo.getWatchLaterVideosFlow().collect { videos ->
                    _uiState.update { it.copy(watchLaterCount = videos.size) }
                }
            }
            
            // Load user playlists
            launch {
                repo.getUserCreatedVideoPlaylistsFlow().collect { playlists ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            playlists = playlists
                        )
                    }
                }
            }

            launch {
                repo.getSavedVideoPlaylistsFlow().collect { saved ->
                    _uiState.update { it.copy(savedPlaylists = saved) }
                }
            }
        }
    }
    
    fun createPlaylist(name: String, description: String) {
        viewModelScope.launch {
            val playlistId = System.currentTimeMillis().toString()
            repo.createPlaylist(playlistId, name, description, isPrivate = true)
        }
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            repo.deletePlaylist(playlistId)
        }
    }
}

data class PlaylistsUiState(
    val isLoading: Boolean = false,
    val watchLaterCount: Int = 0,
    val playlists: List<PlaylistInfo> = emptyList(),
    val savedPlaylists: List<PlaylistInfo> = emptyList()
)

data class PlaylistInfo(
    val id: String,
    val name: String,
    val description: String,
    val videoCount: Int,
    val thumbnailUrl: String,
    val isPrivate: Boolean,
    val createdAt: Long
)
