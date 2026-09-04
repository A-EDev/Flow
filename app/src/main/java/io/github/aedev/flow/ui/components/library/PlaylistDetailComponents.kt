package io.github.aedev.flow.ui.components.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.VideoQuickActionsBottomSheet
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.MediaRow
import io.github.aedev.flow.ui.components.shared.MediaRowAction
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.ui.components.shared.ReorderHandle
import io.github.aedev.flow.ui.components.shared.rememberFlowSheetState
import io.github.aedev.flow.ui.components.shared.videoMetadataLine
import io.github.aedev.flow.utils.formatYouTubeRelativeTime

private val HeaderPadding: Dp = 16.dp
private val HeaderArtworkWidthFraction = 0.95f
private val ActionButtonSize: Dp = 48.dp
private val ActionIconSize: Dp = 24.dp
private val PositionColumnWidth: Dp = 20.dp
private val SortSheetRowPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp)
private val SortSheetBottomPadding: Dp = 24.dp
private const val ARTWORK_PLACEHOLDER_ALPHA = 0.5f

@Composable
internal fun PlaylistDetailTopBar(
    title: String,
    showTitle: Boolean,
    inSelectionMode: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    canSelect: Boolean,
    isUserCreatedPlaylist: Boolean,
    isWatchLater: Boolean,
    isSaved: Boolean,
    showOptionsMenu: Boolean,
    onNavigateBack: () -> Unit,
    onEnterSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMergeClick: () -> Unit,
    onSaveToggle: () -> Unit,
    onOptionsClick: () -> Unit,
    onOptionsDismiss: () -> Unit,
    onEditClick: () -> Unit,
    onDeletePlaylistClick: () -> Unit,
) {
    FlowTopBar(
        title =
            when {
                inSelectionMode -> pluralStringResource(R.plurals.selected_count_template, selectedCount, selectedCount)
                showTitle -> title
                else -> ""
            },
        onBack = if (inSelectionMode) onClearSelection else onNavigateBack,
        actions = {
            if (inSelectionMode) {
                IconButton(onClick = onSelectAll) {
                    Icon(
                        imageVector = if (allSelected) Icons.Outlined.CheckBox else Icons.Default.SelectAll,
                        contentDescription =
                            if (allSelected) {
                                stringResource(R.string.deselect_all)
                            } else {
                                stringResource(R.string.select_all)
                            },
                    )
                }
                IconButton(onClick = onDeleteSelected) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_selected),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                if (canSelect) {
                    IconButton(onClick = onEnterSelection) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = stringResource(R.string.select_videos),
                        )
                    }
                }
                if (!isUserCreatedPlaylist) {
                    IconButton(onClick = onMergeClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = stringResource(R.string.add_all_to_playlist),
                        )
                    }
                    if (!isWatchLater) {
                        IconButton(onClick = onSaveToggle) {
                            Icon(
                                imageVector = if (isSaved) Icons.Default.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription =
                                    if (isSaved) {
                                        stringResource(R.string.ui_remove_from_library)
                                    } else {
                                        stringResource(R.string.ui_save_to_library)
                                    },
                                tint = if (isSaved) MaterialTheme.colorScheme.primary else LocalContentColor.current,
                            )
                        }
                    }
                }
                if (isUserCreatedPlaylist && !isWatchLater) {
                    Box {
                        IconButton(onClick = onOptionsClick) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = onOptionsDismiss,
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.edit_playlist_action)) },
                                onClick = onEditClick,
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete_playlist_action)) },
                                onClick = onDeletePlaylistClick,
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            )
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun PlaylistHeader(
    name: String,
    description: String,
    videoCount: Int,
    thumbnailUrl: String,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onDownloadAll: () -> Unit,
    isDownloading: Boolean,
    downloadProgress: Float,
    currentDownloadingTitle: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(HeaderPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(HeaderArtworkWidthFraction)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            if (thumbnailUrl.isNotEmpty()) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.PlaylistPlay,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint =
                        MaterialTheme.colorScheme.onSurfaceVariant
                            .copy(alpha = ARTWORK_PLACEHOLDER_ALPHA),
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text =
                    pluralStringResource(
                        R.plurals.videos_count_template,
                        videoCount,
                        videoCount,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (description.isNotEmpty()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier =
                        Modifier
                            .height(ActionButtonSize)
                            .weight(1f),
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(ActionIconSize),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.play_all),
                        fontWeight = FontWeight.Bold,
                    )
                }

                Surface(
                    onClick = onShuffle,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(ActionButtonSize),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = stringResource(R.string.shuffle),
                            modifier = Modifier.size(ActionIconSize),
                        )
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(ActionButtonSize),
                ) {
                    Surface(
                        onClick = { if (!isDownloading) onDownloadAll() },
                        shape = CircleShape,
                        color =
                            if (isDownloading) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                        modifier = Modifier.size(ActionButtonSize),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector =
                                    if (isDownloading) {
                                        Icons.Default.Downloading
                                    } else {
                                        Icons.Default.ArrowDownward
                                    },
                                contentDescription =
                                    if (isDownloading) {
                                        stringResource(R.string.ui_downloading_playlist)
                                    } else {
                                        stringResource(R.string.download_all)
                                    },
                                tint =
                                    if (isDownloading) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        LocalContentColor.current
                                    },
                                modifier = Modifier.size(ActionIconSize),
                            )
                        }
                    }
                    if (isDownloading && downloadProgress > 0f) {
                        CircularProgressIndicator(
                            progress = { downloadProgress },
                            modifier = Modifier.size(ActionButtonSize),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isDownloading && currentDownloadingTitle != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = stringResource(R.string.playlist_downloading_template, currentDownloadingTitle.orEmpty()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
internal fun PlaylistSortButton(
    sortOrder: PlaylistSortOrder,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(sortOrder.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = stringResource(R.string.playlist_sort_options),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlaylistSortSheet(
    selected: PlaylistSortOrder,
    onSelected: (PlaylistSortOrder) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberFlowSheetState(),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = SortSheetBottomPadding),
        ) {
            PlaylistSortOrder.entries.forEach { option ->
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option) }
                            .padding(SortSheetRowPadding),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Icon(
                        imageVector = if (option == selected) Icons.Default.Check else Icons.Default.Sort,
                        contentDescription = null,
                        tint =
                            if (option == selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                    Text(
                        text = stringResource(option.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (option == selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlaylistVideoRow(
    video: Video,
    position: Int,
    modifier: Modifier = Modifier,
    isSelected: Boolean,
    inSelectionMode: Boolean,
    canModify: Boolean,
    reorderModifier: Modifier,
    dragHandleModifier: Modifier,
    showDragHandle: Boolean,
    showAddedDate: Boolean,
    isWatchLater: Boolean,
    onChannelClick: ((String) -> Unit)?,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    var showQuickActions by remember { mutableStateOf(false) }

    MediaRow(
        title = video.title,
        modifier = modifier.then(reorderModifier),
        subtitle = video.channelName,
        supporting = video.playlistMetadataLine(showAddedDate),
        supportingColor =
            if (video.viewCount < 0L && !showAddedDate) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        selected = isSelected,
        onClick = onClick,
        onLongClick =
            if (!inSelectionMode) {
                { showQuickActions = true }
            } else {
                null
            },
        leading = {
            when {
                inSelectionMode -> {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onClick() },
                    )
                }

                showDragHandle -> {
                    ReorderHandle(modifier = dragHandleModifier)
                }

                else -> {
                    Text(
                        text = position.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(PositionColumnWidth),
                    )
                }
            }
        },
        trailing = {
            if (!inSelectionMode) {
                MediaRowAction(
                    icon = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.more_options),
                    onClick = { showQuickActions = true },
                )
            }
        },
    ) {
        MediaThumbnail(
            videoId = video.id,
            thumbnailUrl = video.thumbnailUrl,
            durationSeconds = video.duration,
            showWatchProgress = true,
        )
    }

    if (showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = video,
            onChannelClick = onChannelClick,
            onRemoveFromCollection = if (canModify) onRemove else null,
            removeFromCollectionLabel =
                if (canModify) {
                    stringResource(
                        if (isWatchLater) {
                            R.string.remove_from_watch_later
                        } else {
                            R.string.remove_from_playlist_action
                        },
                    )
                } else {
                    null
                },
            onDismiss = { showQuickActions = false },
        )
    }
}

@Composable
internal fun Video.playlistMetadataLine(showAddedDate: Boolean): String {
    val addedAt = addedAtInPlaylist
    if (showAddedDate && addedAt != null) {
        return stringResource(R.string.playlist_video_added_template, formatYouTubeRelativeTime(addedAt))
    }
    return videoMetadataLine(video = this, isUpcoming = viewCount < 0L)
}
