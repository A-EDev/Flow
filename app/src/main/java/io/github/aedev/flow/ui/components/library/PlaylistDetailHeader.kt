package io.github.aedev.flow.ui.components.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SaveAs
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.FlowSortChip
import io.github.aedev.flow.ui.components.shared.connectedButtonShapes

private val HeaderPadding: Dp = 16.dp
private const val DESCRIPTION_COLLAPSED_LINES = 2
private const val ARTWORK_PLACEHOLDER_ALPHA = 0.5f
private val PlaceholderIconSize: Dp = 64.dp
private val DownloadProgressSize: Dp = 28.dp

/** What the playlist header shows. [downloadProgress] is null while no "Download all" is running. */
@Immutable
internal data class PlaylistHeaderState(
    val name: String,
    val metadata: String,
    val description: String,
    val thumbnailUrl: String,
    val isSaved: Boolean = false,
    val canSave: Boolean = false,
    val canAddAll: Boolean = false,
    val canEdit: Boolean = false,
    val canExport: Boolean = false,
    val downloadProgress: Float? = null,
)

/** What the header's buttons do; a missing capability in [PlaylistHeaderState] hides its button. */
internal class PlaylistHeaderActions(
    val onPlayAll: () -> Unit,
    val onShuffle: () -> Unit,
    val onDownloadAll: () -> Unit,
    val onSaveToggle: () -> Unit,
    val onAddAll: () -> Unit,
    val onShare: () -> Unit,
    val onExport: () -> Unit,
    val onEdit: () -> Unit,
    val onDelete: () -> Unit,
)

/** The header at the top of the list, on windows too narrow for a side pane. */
@Composable
internal fun PlaylistHeader(
    state: PlaylistHeaderState,
    actions: PlaylistHeaderActions,
    sortChip: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(HeaderPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlaylistArtwork(url = state.thumbnailUrl, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(4.dp))
        PlaylistTitleBlock(state)
        PlaylistPrimaryActions(actions)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlaylistSecondaryActions(state, actions)
            Spacer(Modifier.weight(1f))
            sortChip()
        }
    }
}

/** The same header as a side pane beside the list, as on the desktop app. */
@Composable
internal fun PlaylistHeaderPane(
    state: PlaylistHeaderState,
    actions: PlaylistHeaderActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PlaylistArtwork(url = state.thumbnailUrl, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.size(4.dp))
        PlaylistTitleBlock(state)
        Spacer(Modifier.weight(1f))
        PlaylistSecondaryActions(state, actions)
        PlaylistPrimaryActions(actions)
    }
}

@Composable
private fun PlaylistArtwork(
    url: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotEmpty()) {
            AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                contentDescription = null,
                modifier = Modifier.size(PlaceholderIconSize),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = ARTWORK_PLACEHOLDER_ALPHA),
            )
        }
    }
}

@Composable
private fun ColumnScope.PlaylistTitleBlock(state: PlaylistHeaderState) {
    Text(
        text = state.name,
        style = MaterialTheme.typography.headlineMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
    )
    if (state.metadata.isNotEmpty()) {
        Text(
            text = state.metadata,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (state.description.isNotEmpty()) {
        var expanded by rememberSaveable(state.description) { mutableStateOf(false) }
        Text(
            text = state.description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else DESCRIPTION_COLLAPSED_LINES,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clip(MaterialTheme.shapes.small).clickable { expanded = !expanded },
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistPrimaryActions(actions: PlaylistHeaderActions) {
    val height = ButtonDefaults.MediumContainerHeight
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        val leading = connectedButtonShapes(index = 0, count = 2)
        val trailing = connectedButtonShapes(index = 1, count = 2)
        Button(
            onClick = actions.onPlayAll,
            shapes = ButtonShapes(leading.shape, leading.pressedShape),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            modifier = Modifier.weight(1f).heightIn(min = height),
        ) {
            PrimaryActionLabel(Icons.Filled.PlayArrow, stringResource(R.string.play_all), height)
        }
        FilledTonalButton(
            onClick = actions.onShuffle,
            shapes = ButtonShapes(trailing.shape, trailing.pressedShape),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            modifier = Modifier.weight(1f).heightIn(min = height),
        ) {
            PrimaryActionLabel(Icons.Filled.Shuffle, stringResource(R.string.shuffle), height)
        }
    }
}

@Composable
private fun PrimaryActionLabel(
    icon: ImageVector,
    label: String,
    height: Dp,
) {
    Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)))
    Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
    Text(text = label, style = ButtonDefaults.textStyleFor(height), maxLines = 1)
}

@Composable
private fun PlaylistSecondaryActions(
    state: PlaylistHeaderState,
    actions: PlaylistHeaderActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val progress = state.downloadProgress
        IconButton(onClick = actions.onDownloadAll, enabled = progress == null) {
            if (progress != null) {
                CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(DownloadProgressSize))
            } else {
                Icon(Icons.Outlined.Download, contentDescription = stringResource(R.string.download_all))
            }
        }
        if (state.canSave) {
            IconToggleButton(checked = state.isSaved, onCheckedChange = { actions.onSaveToggle() }) {
                Icon(
                    imageVector = if (state.isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription =
                        stringResource(if (state.isSaved) R.string.ui_remove_from_library else R.string.ui_save_to_library),
                )
            }
        }
        if (state.canAddAll) {
            IconButton(onClick = actions.onAddAll) {
                Icon(Icons.AutoMirrored.Outlined.PlaylistAdd, contentDescription = stringResource(R.string.add_all_to_playlist))
            }
        }
        IconButton(onClick = actions.onShare) {
            Icon(Icons.Outlined.Share, contentDescription = stringResource(R.string.share))
        }
        if (state.canEdit || state.canExport) PlaylistOverflowMenu(state, actions)
    }
}

@Composable
private fun PlaylistOverflowMenu(
    state: PlaylistHeaderState,
    actions: PlaylistHeaderActions,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (state.canExport) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.export_playlist_action)) },
                    leadingIcon = { Icon(Icons.Outlined.SaveAs, contentDescription = null) },
                    onClick = {
                        open = false
                        actions.onExport()
                    },
                )
            }
            if (!state.canEdit) return@DropdownMenu
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_playlist_action)) },
                leadingIcon = { Icon(Icons.Outlined.Edit, contentDescription = null) },
                onClick = {
                    open = false
                    actions.onEdit()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete_playlist_action), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    open = false
                    actions.onDelete()
                },
            )
        }
    }
}

/** The sort order as a chip that opens a menu of the orders this playlist can offer. */
@Composable
internal fun PlaylistSortChip(
    options: List<PlaylistSortOrder>,
    selected: PlaylistSortOrder,
    onSelected: (PlaylistSortOrder) -> Unit,
    modifier: Modifier = Modifier,
    default: PlaylistSortOrder = PlaylistSortOrder.MANUAL,
) {
    FlowSortChip(
        options = options,
        selected = selected,
        default = default,
        label = { stringResource(it.labelRes) },
        onSelected = onSelected,
        modifier = modifier,
    )
}
