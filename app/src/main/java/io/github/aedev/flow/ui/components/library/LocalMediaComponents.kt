package io.github.aedev.flow.ui.components.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.shared.ArtworkThumbnail
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.MediaKind
import io.github.aedev.flow.ui.components.shared.MediaRow
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.ui.screens.library.LocalMediaItem
import io.github.aedev.flow.utils.formatDurationMillis
import java.util.Locale

private val ListContentPadding = PaddingValues(vertical = 8.dp)
private val ListItemSpacing = 2.dp
private val ScanIndicatorSize = 36.dp
private val ScanIndicatorStroke = 3.dp
private val EmptyActionWidthFraction = 0.6f
private val EmptyActionHeight = 48.dp
private const val BYTES_PER_UNIT = 1024.0

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalMediaList(
    items: List<LocalMediaItem>,
    kind: MediaKind,
    isScanning: Boolean,
    hasScanned: Boolean,
    onRefresh: () -> Unit,
    onItemClick: (List<LocalMediaItem>, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isScanning,
        onRefresh = onRefresh,
        state = pullState,
        modifier = modifier.fillMaxSize(),
    ) {
        if (items.isEmpty()) {
            LocalMediaEmptyState(kind = kind, isScanning = isScanning && !hasScanned)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = ListContentPadding,
                verticalArrangement = Arrangement.spacedBy(ListItemSpacing),
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> kind.name },
                ) { index, item ->
                    LocalMediaRow(
                        item = item,
                        kind = kind,
                        onClick = { onItemClick(items, index) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun LocalMediaRow(
    item: LocalMediaItem,
    kind: MediaKind,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val trailingLabel =
        if (kind == MediaKind.Videos) {
            fileSizeLabel(item.sizeBytes)
        } else {
            if (item.durationMs > 0) formatDurationMillis(item.durationMs) else ""
        }

    MediaRow(
        title = item.title,
        modifier = modifier,
        subtitle = mediaSubtitle(item.subtitle, trailingLabel),
        titleMaxLines = if (kind == MediaKind.Videos) 2 else 1,
        onClick = onClick,
    ) {
        if (kind == MediaKind.Videos) {
            MediaThumbnail(
                videoId = item.contentUri,
                thumbnailUrl = item.contentUri,
                durationSeconds = (item.durationMs / 1000L).toInt(),
                placeholder = Icons.Outlined.VideoLibrary,
            )
        } else {
            ArtworkThumbnail(
                thumbnailUrl = item.artworkUri,
                placeholder = Icons.Outlined.MusicNote,
            )
        }
    }
}

@Composable
internal fun LocalMediaEmptyState(
    kind: MediaKind,
    isScanning: Boolean,
) {
    val label = stringResource(kind.labelRes)
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
    ) {
        if (isScanning) {
            FlowEmptyState(
                title = stringResource(R.string.local_media_scanning),
                action = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(ScanIndicatorSize),
                        strokeWidth = ScanIndicatorStroke,
                    )
                },
            )
        } else {
            FlowEmptyState(
                title = stringResource(R.string.local_media_empty_title, label),
                subtitle = stringResource(R.string.local_media_empty_body),
                icon = kind.icon,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LocalMediaPermissionState(onGrant: () -> Unit) {
    FlowEmptyState(
        title = stringResource(R.string.local_media_permission_title),
        subtitle = stringResource(R.string.local_media_permission_body),
        icon = Icons.Outlined.VideoLibrary,
        action = {
            FilledTonalButton(
                onClick = onGrant,
                shapes = ButtonDefaults.shapes(),
                modifier =
                    Modifier
                        .fillMaxWidth(EmptyActionWidthFraction)
                        .height(EmptyActionHeight),
            ) {
                Text(stringResource(R.string.local_media_grant))
            }
        },
    )
}

@Composable
internal fun mediaSubtitle(
    primary: String,
    secondary: String,
): String =
    when {
        primary.isNotBlank() && secondary.isNotBlank() -> {
            stringResource(R.string.video_metadata_short_template, primary, secondary)
        }

        primary.isNotBlank() -> {
            primary
        }

        else -> {
            secondary
        }
    }

@Composable
internal fun fileSizeLabel(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kilobytes = bytes / BYTES_PER_UNIT
    if (kilobytes < BYTES_PER_UNIT) {
        return stringResource(R.string.file_size_kb, String.format(Locale.getDefault(), "%.0f", kilobytes))
    }
    val megabytes = kilobytes / BYTES_PER_UNIT
    if (megabytes < BYTES_PER_UNIT) {
        return stringResource(R.string.file_size_mb, String.format(Locale.getDefault(), "%.1f", megabytes))
    }
    return stringResource(
        R.string.file_size_gb,
        String.format(Locale.getDefault(), "%.2f", megabytes / BYTES_PER_UNIT),
    )
}
