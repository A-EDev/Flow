package io.github.aedev.flow.ui.components.videoplayer.sheet

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.shared.MediaThumbnail

object PlaylistQueueDockDefaults {
    val Height: Dp = 76.dp
}

private val DockHorizontalPadding = 12.dp
private val NextThumbnailWidth = 72.dp

/**
 * The bar that floats under the player while a queue is playing, and opens it.
 *
 * One opaque container on the Material 3 surface roles rather than a translucent panel: the dock
 * floats over video, and a see-through surface was reading as part of the frame behind it.
 */
@Composable
fun PlaylistQueueDock(
    nextVideo: Video?,
    playlistName: String,
    currentIndex: Int,
    queueSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = rememberQueueOpenAction(onClick)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 3.dp,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = DockHorizontalPadding)
                .safeDrawingPadding()
                .height(PlaylistQueueDockDefaults.Height)
                .clickable(onClick = open),
    ) {
        QueueNextUpRow(
            nextVideo = nextVideo,
            playlistName = playlistName,
            currentIndex = currentIndex,
            queueSize = queueSize,
            actionIcon = Icons.Rounded.KeyboardArrowUp,
            onAction = open,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** What plays next, at the top of the wide layout's side pane; opens the queue in the pane. */
@Composable
fun PlaylistQueuePaneCard(
    nextVideo: Video?,
    playlistName: String,
    currentIndex: Int,
    queueSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = rememberQueueOpenAction(onClick)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.large,
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = open),
    ) {
        QueueNextUpRow(
            nextVideo = nextVideo,
            playlistName = playlistName,
            currentIndex = currentIndex,
            queueSize = queueSize,
            actionIcon = Icons.AutoMirrored.Rounded.QueueMusic,
            onAction = open,
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

@Composable
private fun rememberQueueOpenAction(onClick: () -> Unit): () -> Unit {
    val haptics = LocalHapticFeedback.current
    return {
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        onClick()
    }
}

@Composable
private fun QueueNextUpRow(
    nextVideo: Video?,
    playlistName: String,
    currentIndex: Int,
    queueSize: Int,
    actionIcon: ImageVector,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val position = (currentIndex + 1).coerceIn(1, queueSize.coerceAtLeast(1))
    Row(
        modifier = modifier.padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (nextVideo != null) {
            MediaThumbnail(
                videoId = nextVideo.id,
                thumbnailUrl = nextVideo.thumbnailUrl,
                width = NextThumbnailWidth,
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text =
                    if (queueSize > 0) {
                        "${stringResource(R.string.next_up)} ${stringResource(R.string.metadata_separator)} " +
                            stringResource(R.string.queue_position_template, position, queueSize)
                    } else {
                        stringResource(R.string.next_up)
                    },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
            )
            Text(
                text = nextVideo?.title ?: stringResource(R.string.playlist_queue),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (playlistName.isNotBlank()) {
                Text(
                    text = playlistName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        FilledTonalIconButton(
            onClick = onAction,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                imageVector = actionIcon,
                contentDescription = stringResource(R.string.playlist_queue),
            )
        }
    }
}
