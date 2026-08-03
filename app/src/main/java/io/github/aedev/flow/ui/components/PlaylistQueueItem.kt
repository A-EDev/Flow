package io.github.aedev.flow.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video

@Composable
internal fun PlaylistQueueItem(
    video: Video,
    isPlaying: Boolean,
    reorderModifier: Modifier,
    dragHandleModifier: Modifier,
    onClick: () -> Unit,
    onRemove: (() -> Unit)?,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    var showRemoveDialog by remember { mutableStateOf(false) }
    val containerColor =
        if (isPlaying) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (isPlaying) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Row(
        modifier =
            Modifier
                .then(reorderModifier)
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(containerColor)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .width(120.dp)
                    .aspectRatio(16f / 9f)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (isPlaying) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = stringResource(R.string.now_playing),
                        tint = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            } else if (video.duration > 0) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.inverseSurface,
                                shape = MaterialTheme.shapes.extraSmall,
                            ).padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = formatQueueDuration(video.duration),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = contentColor,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (isPlaying) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (onRemove != null) {
            IconButton(onClick = { showRemoveDialog = true }) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.remove_from_queue),
                )
            }
        }

        QueueReorderHandle(
            dragHandleModifier = dragHandleModifier,
            onMoveUp = onMoveUp,
            onMoveDown = onMoveDown,
        )
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = {
                Text(text = stringResource(R.string.remove_from_queue))
            },
            text = {
                Text(text = stringResource(R.string.remove_from_queue_confirmation))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveDialog = false
                        onRemove?.invoke()
                    },
                ) {
                    Text(text = stringResource(R.string.remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text(text = stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun QueueReorderHandle(
    dragHandleModifier: Modifier,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
) {
    val moveUpLabel = stringResource(R.string.move_up)
    val moveDownLabel = stringResource(R.string.move_down)
    val reorderLabel = stringResource(R.string.reorder_queue_item)
    val accessibilityActions =
        buildList {
            onMoveUp?.let { action ->
                add(
                    CustomAccessibilityAction(moveUpLabel) {
                        action()
                        true
                    },
                )
            }
            onMoveDown?.let { action ->
                add(
                    CustomAccessibilityAction(moveDownLabel) {
                        action()
                        true
                    },
                )
            }
        }

    Box(
        modifier =
            dragHandleModifier
                .size(48.dp)
                .semantics {
                    contentDescription = reorderLabel
                    customActions = accessibilityActions
                },
        contentAlignment = Alignment.Center,
    ) {
        ReorderHandle()
    }
}

private fun formatQueueDuration(durationSeconds: Int): String =
    if (durationSeconds >= 3_600) {
        "%d:%02d:%02d".format(
            durationSeconds / 3_600,
            (durationSeconds % 3_600) / 60,
            durationSeconds % 60,
        )
    } else {
        "%d:%02d".format(durationSeconds / 60, durationSeconds % 60)
    }
