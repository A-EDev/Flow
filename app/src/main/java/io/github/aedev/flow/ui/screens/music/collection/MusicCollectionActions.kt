package io.github.aedev.flow.ui.screens.music.collection

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddToQueue
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HeartBroken
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.QueuePlayNext
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.SaveAs
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowSelectionAction

/** The header's ⋮ menu: add songs, edit or delete your own playlist, or add all of these to one of yours. */
@Composable
internal fun collectionMenu(
    state: MusicCollectionUiState,
    onAddSongs: () -> Unit,
    onAddAll: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onSaveAsPlaylist: () -> Unit,
): List<CollectionMenuItem> =
    buildList {
        if (state.kind == MusicCollectionKind.DAILY_MIX) {
            add(CollectionMenuItem(stringResource(R.string.save_as_playlist), Icons.Rounded.LibraryAdd, onClick = onSaveAsPlaylist))
        }
        if (state.canExport) {
            add(
                CollectionMenuItem(stringResource(R.string.export_playlist_action), Icons.Rounded.SaveAs, onClick = onExport),
            )
        }
        if (state.isOwn) {
            add(CollectionMenuItem(stringResource(R.string.ui_add_songs), Icons.Rounded.Add, onClick = onAddSongs))
            add(CollectionMenuItem(stringResource(R.string.edit_playlist_action), Icons.Rounded.Edit, onClick = onEdit))
            add(
                CollectionMenuItem(
                    stringResource(R.string.delete_playlist_action),
                    Icons.Rounded.Delete,
                    destructive = true,
                    onClick = onDelete,
                ),
            )
        } else {
            val label = stringResource(R.string.add_all_to_playlist)
            add(CollectionMenuItem(label, Icons.AutoMirrored.Rounded.PlaylistAdd, onClick = onAddAll))
        }
    }

/** The collection's own order reads as what it is: album order, playlist order, or when you liked them. */
@Composable
internal fun sortLabel(
    order: MusicSortOrder,
    kind: MusicCollectionKind?,
): String =
    if (order != MusicSortOrder.COLLECTION) {
        stringResource(order.labelRes)
    } else {
        stringResource(
            when (kind) {
                MusicCollectionKind.ALBUM -> R.string.music_sort_album_order
                MusicCollectionKind.LIKED -> R.string.music_sort_recently_liked
                else -> R.string.music_sort_collection
            },
        )
    }

/** What the floating toolbar offers for the selected songs; taking them out only where that is possible. */
@Composable
internal fun selectionActions(
    state: MusicCollectionUiState,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddTo: () -> Unit,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
): List<FlowSelectionAction> =
    buildList {
        add(FlowSelectionAction(Icons.Rounded.QueuePlayNext, stringResource(R.string.play_next), onClick = onPlayNext))
        add(FlowSelectionAction(Icons.Rounded.AddToQueue, stringResource(R.string.add_to_queue), onClick = onAddToQueue))
        add(FlowSelectionAction(Icons.AutoMirrored.Rounded.PlaylistAdd, stringResource(R.string.add_to_playlist), onClick = onAddTo))
        add(FlowSelectionAction(Icons.Rounded.Download, stringResource(R.string.download), onClick = onDownload))
        when (state.kind) {
            MusicCollectionKind.OWN -> {
                add(
                    FlowSelectionAction(
                        Icons.Rounded.RemoveCircleOutline,
                        stringResource(R.string.remove),
                        onClick = onRemove,
                    ),
                )
            }

            MusicCollectionKind.LIKED -> {
                add(FlowSelectionAction(Icons.Rounded.HeartBroken, stringResource(R.string.unlike), onClick = onRemove))
            }

            else -> {
                Unit
            }
        }
    }
