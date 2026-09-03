package io.github.aedev.flow.ui.components.music.sheet

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.*
import io.github.aedev.flow.ui.components.shared.rememberFlowSheetState

/**
 * Playlist Creation Dialog
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
) {
    var playlistName by remember { mutableStateOf("") }
    var playlistDescription by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(R.string.title_create_playlist),
                style = MaterialTheme.typography.headlineSmallEmphasized,
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text(stringResource(R.string.label_playlist_name)) },
                    placeholder = { Text(stringResource(R.string.placeholder_playlist_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = playlistDescription,
                    onValueChange = { playlistDescription = it },
                    label = { Text(stringResource(R.string.label_description_optional)) },
                    placeholder = { Text(stringResource(R.string.placeholder_playlist_desc)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (playlistName.isNotBlank()) {
                        onConfirm(playlistName, playlistDescription)
                        onDismiss()
                    }
                },
                enabled = playlistName.isNotBlank(),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.action_create))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

/**
 * Add to Playlist Dialog
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AddToPlaylistDialog(
    playlists: List<io.github.aedev.flow.data.music.Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onCreateNew: () -> Unit,
) {
    var addedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxHeight = configuration.screenHeightDp.dp * 0.65f

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Color.Transparent,
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
                stringResource(R.string.title_add_to_playlist),
                style = MaterialTheme.typography.titleLargeEmphasized,
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
                if (playlists.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.empty_playlists_dialog),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                        )
                    }
                } else {
                    items(playlists, key = { it.id }) { playlist ->
                        val isAdded = playlist.id in addedIds
                        MusicPlaylistSheetRow(
                            playlist = playlist,
                            isAdded = isAdded,
                            onClick = {
                                onSelectPlaylist(playlist.id)
                                addedIds = if (isAdded) addedIds - playlist.id else addedIds + playlist.id
                            },
                        )
                    }
                }

                item {
                    val buttonHeight = ButtonDefaults.MediumContainerHeight
                    FilledTonalButton(
                        onClick = onCreateNew,
                        shapes = ButtonDefaults.shapesFor(buttonHeight),
                        contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .heightIn(min = buttonHeight),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                        )
                        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                        Text(
                            text = stringResource(R.string.create_new_playlist),
                            style = ButtonDefaults.textStyleFor(buttonHeight),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MusicPlaylistSheetRow(
    playlist: io.github.aedev.flow.data.music.Playlist,
    isAdded: Boolean = false,
    onClick: () -> Unit,
) {
    val thumbnailUrl =
        remember(playlist.thumbnailUrl, playlist.tracks) {
            playlist.thumbnailUrl.ifBlank {
                playlist.tracks
                    .firstOrNull { it.thumbnailUrl.isNotBlank() }
                    ?.thumbnailUrl
                    .orEmpty()
            }
        }

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
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl.isNotBlank()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.tracks_count_template, playlist.trackCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Icon(
            imageVector = if (isAdded) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = null,
            tint = if (isAdded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(28.dp),
        )
    }
}
