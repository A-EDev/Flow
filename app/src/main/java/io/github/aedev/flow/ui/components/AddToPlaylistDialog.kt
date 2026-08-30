package io.github.aedev.flow.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.PlaylistPlay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.playlists.PlaylistInfo
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistDialog(
    video: Video,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { PlaylistRepository(context) }

    var playlists by remember { mutableStateOf<List<PlaylistInfo>>(emptyList()) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var watchLaterVideos by remember { mutableStateOf<List<Video>>(emptyList()) }
    var playlistsLoaded by remember { mutableStateOf(false) }
    var watchLaterLoaded by remember { mutableStateOf(false) }
    var selectedPlaylistIds by remember(video.id) { mutableStateOf<Set<String>>(emptySet()) }
    var playlistSelectionInitialized by remember(video.id) { mutableStateOf(false) }
    var selectedWatchLater by remember(video.id) { mutableStateOf(false) }
    var watchLaterSelectionInitialized by remember(video.id) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch {
            repo.getAllPlaylistsFlow().collect {
                playlists =
                    it.filter { playlist ->
                        playlist.id != PlaylistRepository.WATCH_LATER_ID
                    }
                playlistsLoaded = true
            }
        }
        launch {
            repo.getWatchLaterVideosFlow().collect {
                watchLaterVideos = it
                watchLaterLoaded = true
            }
        }
    }

    LaunchedEffect(playlistsLoaded, playlists, video.id) {
        if (playlistsLoaded && !playlistSelectionInitialized) {
            val existingPlaylistIds =
                playlists
                    .filter { playlist ->
                        repo.getPlaylistVideosFlow(playlist.id).first().any { it.id == video.id }
                    }.map { it.id }
                    .toSet()

            selectedPlaylistIds = existingPlaylistIds
            playlistSelectionInitialized = true
        }
    }

    LaunchedEffect(watchLaterLoaded, watchLaterVideos, video.id) {
        if (watchLaterLoaded && !watchLaterSelectionInitialized) {
            selectedWatchLater = watchLaterVideos.any { it.id == video.id }
            watchLaterSelectionInitialized = true
        }
    }

    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.65f
    val watchLaterThumbnail =
        watchLaterVideos
            .firstOrNull()
            ?.thumbnailUrl
            .orEmpty()
            .ifBlank { video.thumbnailUrl }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight)
                    .navigationBarsPadding()
                    .padding(bottom = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.save_to),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )

            HorizontalDivider()

            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                item(key = "watch_later") {
                    PlaylistSheetRow(
                        thumbnail = watchLaterThumbnail,
                        name = stringResource(R.string.watch_later),
                        privacy = stringResource(R.string.playlist_private),
                        isSaved = selectedWatchLater,
                        onClick = {
                            if (watchLaterSelectionInitialized) {
                                val shouldSave = !selectedWatchLater
                                selectedWatchLater = shouldSave
                                scope.launch {
                                    runCatching {
                                        if (shouldSave) {
                                            repo.addToWatchLater(video)
                                        } else {
                                            repo.removeFromWatchLater(video.id)
                                        }
                                    }.onFailure {
                                        selectedWatchLater = !shouldSave
                                    }
                                }
                            }
                        },
                    )
                }

                item(key = "watch_later_divider") {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                    )
                }

                items(
                    items = playlists,
                    key = { it.id },
                ) { playlist ->
                    PlaylistSheetRow(
                        thumbnail = playlist.thumbnailUrl,
                        name = playlist.name,
                        privacy =
                            if (playlist.isPrivate) {
                                stringResource(R.string.playlist_private)
                            } else {
                                stringResource(R.string.playlist_public)
                            },
                        isSaved = playlist.id in selectedPlaylistIds,
                        onClick = {
                            if (playlistSelectionInitialized) {
                                val isCurrentlySaved = playlist.id in selectedPlaylistIds
                                selectedPlaylistIds =
                                    if (isCurrentlySaved) {
                                        selectedPlaylistIds - playlist.id
                                    } else {
                                        selectedPlaylistIds + playlist.id
                                    }

                                scope.launch {
                                    runCatching {
                                        if (isCurrentlySaved) {
                                            repo.removeVideoFromPlaylist(playlist.id, video.id)
                                        } else {
                                            repo.addVideoToPlaylist(playlist.id, video)
                                        }
                                    }.onFailure {
                                        selectedPlaylistIds =
                                            if (isCurrentlySaved) {
                                                selectedPlaylistIds + playlist.id
                                            } else {
                                                selectedPlaylistIds - playlist.id
                                            }
                                    }
                                }
                            }
                        },
                    )
                }

                item(key = "create_playlist") {
                    Surface(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        onClick = { showCreateDialog = true },
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Add,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.create_new_playlist),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateNewPlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name, description ->
                scope.launch {
                    val playlistId = System.currentTimeMillis().toString()
                    repo.createPlaylist(playlistId, name, description, true)
                    repo.addVideoToPlaylist(playlistId, video)
                    selectedPlaylistIds = selectedPlaylistIds + playlistId
                    showCreateDialog = false
                }
            },
        )
    }
}

@Composable
private fun PlaylistSheetRow(
    thumbnail: String,
    name: String,
    privacy: String,
    isSaved: Boolean,
    onClick: () -> Unit,
) {
    val reduceMotion = rememberFlowReduceMotion()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 88.dp, height = 50.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            if (thumbnail.isNotEmpty()) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.PlaylistPlay,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(28.dp)
                            .align(Alignment.Center),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = privacy,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AnimatedContent(
            targetState = isSaved,
            transitionSpec = {
                fadeIn(
                    tween(
                        durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.EnterEasing,
                    ),
                ).togetherWith(
                    fadeOut(
                        tween(
                            durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                            easing = FlowMotion.ExitEasing,
                        ),
                    ),
                )
            },
            label = "playlistSavedState",
        ) { saved ->
            Icon(
                imageVector = if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = null,
                tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun CreateNewPlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(Icons.Outlined.PlaylistAdd, null)
        },
        title = {
            Text(stringResource(R.string.create_new_playlist))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.playlist_description_optional)) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim(), description.trim())
                    }
                },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(R.string.create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
