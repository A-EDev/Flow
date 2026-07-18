package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R

@Composable
internal fun PlaylistManagementDialogHost(
    videoToDelete: PlaylistInfo?,
    musicToRename: PlaylistInfo?,
    musicToDelete: PlaylistInfo?,
    onDismissVideoDelete: () -> Unit,
    onConfirmVideoDelete: (PlaylistInfo) -> Unit,
    onDismissMusicRename: () -> Unit,
    onConfirmMusicRename: (PlaylistInfo, String) -> Unit,
    onDismissMusicDelete: () -> Unit,
    onConfirmMusicDelete: (PlaylistInfo) -> Unit
) {
    videoToDelete?.let { playlist ->
        DeletePlaylistDialog(
            playlist = playlist,
            onDismiss = onDismissVideoDelete,
            onConfirm = { onConfirmVideoDelete(playlist) }
        )
    }
    musicToRename?.let { playlist ->
        RenamePlaylistDialog(
            playlist = playlist,
            onDismiss = onDismissMusicRename,
            onConfirm = { onConfirmMusicRename(playlist, it) }
        )
    }
    musicToDelete?.let { playlist ->
        DeletePlaylistDialog(
            playlist = playlist,
            onDismiss = onDismissMusicDelete,
            onConfirm = { onConfirmMusicDelete(playlist) }
        )
    }
}

@Composable
private fun RenamePlaylistDialog(
    playlist: PlaylistInfo,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember(playlist.id) { mutableStateOf(playlist.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_playlist_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.playlist_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) {
                Text(stringResource(R.string.action_rename))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun DeletePlaylistDialog(
    playlist: PlaylistInfo,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_playlist_dialog_title)) },
        text = { Text(stringResource(R.string.delete_playlist_dialog_text, playlist.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
