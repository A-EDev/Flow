package io.github.aedev.flow.ui.screens.music.collection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.ui.components.shared.CollectionEditDialog
import io.github.aedev.flow.ui.components.shared.CollectionTarget
import io.github.aedev.flow.ui.components.shared.DeleteCollectionDialog
import io.github.aedev.flow.ui.components.shared.MergeIntoCollectionSheet

/** The sheet or dialog a music page has open. */
internal sealed interface CollectionSheet {
    data object AddSongs : CollectionSheet

    data object Edit : CollectionSheet

    data object Delete : CollectionSheet

    /** [songs] null means every song on the page. */
    data class AddTo(
        val songs: List<MusicTrack>?,
    ) : CollectionSheet
}

@Composable
internal fun CollectionSheets(
    sheet: CollectionSheet?,
    details: PlaylistDetails,
    viewModel: MusicCollectionViewModel,
    onPreview: (MusicTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    when (sheet) {
        CollectionSheet.AddSongs -> {
            val search by viewModel.songSearch.state.collectAsStateWithLifecycle()
            MusicAddSongsSheet(
                search = search,
                inPlaylist = remember(details.tracks) { details.tracks.mapTo(HashSet()) { it.videoId } },
                onQueryChange = viewModel.songSearch::search,
                onAdd = viewModel::addTrack,
                onPreview = onPreview,
                onDismiss = {
                    onDismiss()
                    viewModel.songSearch.clear()
                },
            )
        }

        is CollectionSheet.AddTo -> {
            val targets by viewModel.mergeTargets.collectAsStateWithLifecycle()
            MergeIntoCollectionSheet(
                targets =
                    remember(targets) {
                        targets.map {
                            CollectionTarget(
                                id = it.id,
                                name = it.name,
                                thumbnailUrl = it.thumbnailUrl,
                                itemCount = it.videoCount,
                            )
                        }
                    },
                placeholder = Icons.Rounded.MusicNote,
                itemCountLabel = { pluralStringResource(R.plurals.songs_count_template, it, it) },
                onSelect = { target -> targets.firstOrNull { it.id == target.id }?.let { viewModel.addTo(it, sheet.songs) } },
                onDismiss = onDismiss,
            )
        }

        CollectionSheet.Edit -> {
            CollectionEditDialog(
                title = stringResource(R.string.edit_playlist_action),
                confirmLabel = stringResource(R.string.action_save),
                initialName = details.title,
                initialDescription = details.description.orEmpty(),
                icon = Icons.Rounded.Edit,
                onDismiss = onDismiss,
                onConfirm = { name, description ->
                    viewModel.updateDetails(name, description)
                    onDismiss()
                },
            )
        }

        CollectionSheet.Delete -> {
            DeleteCollectionDialog(
                collectionName = details.title,
                onDismiss = onDismiss,
                onConfirm = {
                    onDismiss()
                    viewModel.delete()
                },
            )
        }

        null -> {
            Unit
        }
    }
}
