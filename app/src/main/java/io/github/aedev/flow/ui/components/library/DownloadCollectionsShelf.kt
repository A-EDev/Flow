package io.github.aedev.flow.ui.components.library

import android.text.format.Formatter
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.dao.DownloadCollectionSummary
import io.github.aedev.flow.ui.components.shared.FlowAlertDialog

/**
 * The playlists and albums downloaded as one item, above the loose downloads of the same tab. A
 * tap opens the collection's page, a long press offers to remove it.
 */
@Composable
internal fun DownloadCollectionsShelf(
    collections: List<DownloadCollectionSummary>,
    onOpen: (DownloadCollectionSummary) -> Unit,
    onRemove: (DownloadCollectionSummary) -> Unit,
    horizontalInset: Dp,
    modifier: Modifier = Modifier,
) {
    if (collections.isEmpty()) return
    val context = LocalContext.current
    LibraryShelf(
        title = stringResource(R.string.downloads_collections_title),
        icon = Icons.Outlined.FolderOpen,
        onTitleClick = null,
        modifier = modifier,
        horizontalInset = horizontalInset,
    ) { cardWidth ->
        items(collections, key = { it.collection.id }, contentType = { "download_collection" }) { summary ->
            LibraryAlbumCard(
                title = summary.collection.title,
                subtitle =
                    if (summary.isComplete) {
                        Formatter.formatShortFileSize(context, summary.downloadedBytes)
                    } else {
                        stringResource(R.string.download_collection_progress, summary.downloadedCount, summary.wantedCount)
                    },
                thumbnailUrl = summary.collection.coverPath?.let { "file://$it" } ?: summary.collection.thumbnailUrl,
                onClick = { onOpen(summary) },
                isDownloaded = summary.isComplete,
                artworkSize = cardWidth * 9f / 16f,
                onLongClick = { onRemove(summary) },
            )
        }
    }
}

/** Removing a downloaded collection: its files go too, or only the grouping. */
@Composable
internal fun RemoveDownloadCollectionDialog(
    title: String,
    onDeleteFiles: () -> Unit,
    onKeepFiles: () -> Unit,
    onDismiss: () -> Unit,
) {
    FlowAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.download_collection_remove_title, title)) },
        text = { Text(stringResource(R.string.download_collection_remove_body)) },
        confirmButton = { TextButton(onClick = onDeleteFiles) { Text(stringResource(R.string.download_collection_delete_files)) } },
        dismissButton = { TextButton(onClick = onKeepFiles) { Text(stringResource(R.string.download_collection_keep_files)) } },
    )
}
