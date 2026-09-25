package io.github.aedev.flow.ui.screens.library

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowNavRow
import io.github.aedev.flow.ui.components.shared.FlowSegmentedGap
import io.github.aedev.flow.ui.components.shared.flowRowGroupShape

/** A folder on the settings row kit: its name, how many files and how much space, selected when open. */
@Composable
internal fun LocalFolderRow(
    folder: LocalFolder,
    index: Int,
    count: Int,
    isVideos: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val separator = stringResource(R.string.metadata_separator)
    val files =
        pluralStringResource(
            if (isVideos) R.plurals.local_videos_count else R.plurals.local_songs_count,
            folder.items.size,
            folder.items.size,
        )
    FlowNavRow(
        title = folder.name,
        supportingText = "$files $separator ${Formatter.formatShortFileSize(context, folder.sizeBytes)}",
        leadingIcon = Icons.Outlined.Folder,
        onClick = onClick,
        selected = selected,
        shape = flowRowGroupShape(index, count),
    )
}

/** The folders as the side pane beside the open folder's files on a wide window. */
@Composable
internal fun LocalFolderList(
    state: LocalMediaUiState,
    isVideos: Boolean,
    onOpenFolder: (String) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(FlowSegmentedGap),
    ) {
        itemsIndexed(state.folders, key = { _, folder -> folder.id }) { index, folder ->
            LocalFolderRow(
                folder,
                index,
                state.folders.size,
                isVideos,
                selected = folder.id == state.openFolder?.id,
            ) { onOpenFolder(folder.id) }
        }
    }
}
