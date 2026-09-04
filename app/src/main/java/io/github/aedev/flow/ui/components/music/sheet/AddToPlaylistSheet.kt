package io.github.aedev.flow.ui.components.music.sheet

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.Playlist
import io.github.aedev.flow.ui.components.shared.CollectionEditDialog
import io.github.aedev.flow.ui.components.shared.CollectionSheetEntry
import io.github.aedev.flow.ui.components.shared.SaveToCollectionSheet

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit,
) {
    CollectionEditDialog(
        title = stringResource(R.string.title_create_playlist),
        confirmLabel = stringResource(R.string.action_create),
        icon = Icons.Default.PlaylistAdd,
        onDismiss = onDismiss,
        onConfirm = { name, description ->
            onConfirm(name, description)
            onDismiss()
        },
    )
}

@Composable
fun AddToPlaylistDialog(
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (String) -> Unit,
    onCreateNew: () -> Unit,
) {
    var addedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    val entries =
        playlists.map { playlist ->
            CollectionSheetEntry(
                id = playlist.id,
                name = playlist.name,
                supporting = stringResource(R.string.tracks_count_template, playlist.trackCount),
                thumbnailUrl =
                    playlist.thumbnailUrl.ifBlank {
                        playlist.tracks
                            .firstOrNull { it.thumbnailUrl.isNotBlank() }
                            ?.thumbnailUrl
                            .orEmpty()
                    },
                isSaved = playlist.id in addedIds,
            )
        }

    SaveToCollectionSheet(
        title = stringResource(R.string.title_add_to_playlist),
        entries = entries,
        placeholderIcon = Icons.Default.MusicNote,
        createLabel = stringResource(R.string.create_new_playlist),
        emptyLabel = stringResource(R.string.empty_playlists_dialog),
        onToggle = { entry ->
            onSelectPlaylist(entry.id)
            addedIds = if (entry.isSaved) addedIds - entry.id else addedIds + entry.id
        },
        onCreateNew = onCreateNew,
        onDismiss = onDismiss,
    )
}
