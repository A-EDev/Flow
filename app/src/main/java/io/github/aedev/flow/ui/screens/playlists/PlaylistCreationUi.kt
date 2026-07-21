package io.github.aedev.flow.ui.screens.playlists

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R

internal enum class PlaylistCreationTarget {
    Video,
    Music
}

@Composable
internal fun PlaylistCreationFabMenu(
    onCreateVideo: () -> Unit,
    onCreateMusic: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val iconRotation by animateFloatAsState(
        targetValue = if (expanded) 45f else 0f,
        label = "playlist-fab-icon"
    )
    BackHandler(enabled = expanded) { expanded = false }

    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.video)) },
                    icon = { Icon(Icons.Default.VideoLibrary, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onCreateVideo()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
                ExtendedFloatingActionButton(
                    text = { Text(stringResource(R.string.tab_music)) },
                    icon = { Icon(Icons.Default.MusicNote, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onCreateMusic()
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        FloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(
                    if (expanded) {
                        R.string.playlist_creation_menu_close
                    } else {
                        R.string.playlist_creation_menu_open
                    }
                ),
                modifier = Modifier.rotate(iconRotation)
            )
        }
    }
}

@Composable
internal fun PlaylistCreationDialogHost(
    target: PlaylistCreationTarget?,
    onDismiss: () -> Unit,
    onCreateVideo: (name: String, description: String, isPrivate: Boolean) -> Unit,
    onCreateMusic: (name: String, description: String) -> Unit
) {
    target ?: return
    key(target) {
        PlaylistCreationDialog(
            target = target,
            onDismiss = onDismiss,
            onConfirm = { name, description, isPrivate ->
                when (target) {
                    PlaylistCreationTarget.Video -> onCreateVideo(name, description, isPrivate)
                    PlaylistCreationTarget.Music -> onCreateMusic(name, description)
                }
                onDismiss()
            }
        )
    }
}

@Composable
private fun PlaylistCreationDialog(
    target: PlaylistCreationTarget,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String, isPrivate: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(true) }
    val isVideo = target == PlaylistCreationTarget.Video

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = if (isVideo) Icons.Default.VideoLibrary else Icons.Default.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                stringResource(
                    if (isVideo) R.string.create_new_playlist else R.string.new_playlist_button
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.playlist_name)) },
                    placeholder = { Text(stringResource(R.string.my_playlist_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.playlist_description_optional)) },
                    placeholder = { Text(stringResource(R.string.add_description_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                if (isVideo) {
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
                                text = stringResource(
                                    if (isPrivate) R.string.playlist_private else R.string.playlist_public
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Switch(
                            checked = isPrivate,
                            onCheckedChange = { isPrivate = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, description, isPrivate) },
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
