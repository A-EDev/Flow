package io.github.aedev.flow.ui.screens.library

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.localmedia.LocalMediaItem
import io.github.aedev.flow.ui.components.library.LibraryVideoCard
import io.github.aedev.flow.ui.components.shared.ArtworkThumbnail
import io.github.aedev.flow.ui.components.shared.MediaRow
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.ui.components.shared.MediaThumbnailDefaults
import io.github.aedev.flow.utils.formatDurationMillis
import io.github.aedev.flow.utils.formatYouTubeRelativeTime

private const val UHD_SHORT_SIDE = 2160

/** "1080p · 3 days ago" for a video, "Album · 3:45" for a song. */
@Composable
internal fun localSupportingLine(item: LocalMediaItem): String {
    val separator = stringResource(R.string.metadata_separator)
    val parts =
        if (item.isVideo) {
            listOfNotNull(resolutionLabel(item), item.dateAddedMs.takeIf { it > 0L }?.let { formatYouTubeRelativeTime(it) })
        } else {
            listOfNotNull(item.album.takeIf(String::isNotBlank), item.durationMs.takeIf { it > 0L }?.let(::formatDurationMillis))
        }
    return parts.joinToString(" $separator ")
}

@Composable
private fun resolutionLabel(item: LocalMediaItem): String? {
    val shortSide = minOf(item.width, item.height).takeIf { it > 0 } ?: return null
    return if (shortSide >= UHD_SHORT_SIDE) stringResource(R.string.local_filter_quality_4k) else "${shortSide}p"
}

/** A file as a list row: a video with its frame, progress and New mark, or a song with its album art. */
@Composable
internal fun LocalMediaRow(
    item: LocalMediaItem,
    isNew: Boolean,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MediaRow(
        title = item.title,
        modifier = modifier,
        subtitle = if (item.isVideo) item.folderName else item.artist.ifBlank { item.folderName },
        supporting = localSupportingLine(item),
        supportingColor = MaterialTheme.colorScheme.onSurfaceVariant,
        titleMaxLines = if (item.isVideo) 2 else 1,
        selected = selected,
        onClick = onClick,
        onLongClick = if (selectionMode) null else onLongClick,
        leading = if (selectionMode) ({ Checkbox(checked = selected, onCheckedChange = { onClick() }) }) else null,
    ) {
        if (item.isVideo) {
            LocalVideoThumbnail(item, isNew, Modifier, MediaThumbnailDefaults.VideoWidth)
        } else {
            ArtworkThumbnail(thumbnailUrl = item.artworkUri, placeholder = Icons.Outlined.MusicNote)
        }
    }
}

/** A video file as a grid card on wide windows. */
@Composable
internal fun LocalMediaCard(
    item: LocalMediaItem,
    isNew: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    LibraryVideoCard(
        title = item.title,
        subtitle = item.folderName,
        supporting = localSupportingLine(item),
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        modifier = modifier,
    ) {
        LocalVideoThumbnail(item, isNew, Modifier.fillMaxWidth(), Dp.Unspecified, MaterialTheme.shapes.large)
    }
}

@Composable
internal fun LocalVideoThumbnail(
    item: LocalMediaItem,
    isNew: Boolean,
    modifier: Modifier,
    width: Dp,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small,
) {
    MediaThumbnail(
        videoId = item.mediaId,
        thumbnailUrl = item.contentUri,
        durationSeconds = (item.durationMs / 1_000L).toInt(),
        showWatchProgress = true,
        placeholder = Icons.Outlined.VideoLibrary,
        modifier = modifier,
        width = width,
        shape = shape,
    ) {
        if (isNew) NewBadge()
    }
}

@Composable
private fun BoxScope.NewBadge() {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
    ) {
        Text(
            text = stringResource(R.string.local_new_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
        )
    }
}
