package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistPlay
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.PlaylistInfo
import io.github.aedev.flow.ui.components.library.PlaylistActionsMenu
import io.github.aedev.flow.ui.components.shared.ArtworkThumbnail
import io.github.aedev.flow.ui.components.shared.MediaRow
import io.github.aedev.flow.ui.components.shared.MediaRowAction
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.ui.components.shared.MediaThumbnailDefaults

private val CompactVideoWidth = MediaThumbnailDefaults.ArtworkSize * MediaThumbnailDefaults.VideoAspectRatio

/** One playlist as a dense row: the compact layout and the reorder list both use it. */
@Composable
internal fun PlaylistCompactRow(
    playlist: PlaylistInfo,
    isMusic: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val thumbnailUrl = playlist.thumbnailUrl.ifBlank { null }
    MediaRow(
        title = playlist.name,
        subtitle =
            if (isMusic) {
                stringResource(R.string.tracks_count_template, playlist.videoCount)
            } else {
                pluralStringResource(R.plurals.videos_count_template, playlist.videoCount, playlist.videoCount)
            },
        titleMaxLines = 1,
        onClick = onClick,
        modifier = modifier,
        trailing = trailing,
        thumbnail = {
            if (isMusic) {
                ArtworkThumbnail(thumbnailUrl = thumbnailUrl, placeholder = Icons.Rounded.MusicNote)
            } else {
                MediaThumbnail(
                    videoId = playlist.id,
                    thumbnailUrl = thumbnailUrl,
                    width = CompactVideoWidth,
                    placeholder = Icons.AutoMirrored.Outlined.PlaylistPlay,
                )
            }
        },
    )
}

@Composable
internal fun PlaylistRowMenuButton(
    onDownload: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        MediaRowAction(
            icon = Icons.Rounded.MoreVert,
            contentDescription = stringResource(R.string.more_options),
            onClick = { expanded = true },
        )
        PlaylistActionsMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            onDownload = onDownload,
            onRename = onRename,
            onDelete = onDelete,
        )
    }
}
