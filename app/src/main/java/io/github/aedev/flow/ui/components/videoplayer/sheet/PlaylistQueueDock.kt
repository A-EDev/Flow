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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R

private val DockHeight = 76.dp
private val DockHorizontalPadding = 12.dp
private val OverlineTracking = 0.8.sp

/**
 * The bar that sits under the player while a queue is playing, and opens it.
 *
 * One opaque container on the Material 3 surface roles rather than a translucent panel with an
 * accent-tinted tile: the dock floats over video, and a see-through surface with a coloured outline
 * was reading as part of the frame behind it. What is coming next is the line that matters, so it
 * takes the title style and the icon that only repeated the label is gone.
 */
@Composable
fun PlaylistQueueDock(
    nextVideoTitle: String?,
    playlistName: String,
    currentIndex: Int,
    queueSize: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val open = {
        haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
        onClick()
    }
    val position = (currentIndex + 1).coerceIn(1, queueSize.coerceAtLeast(1))

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
                .height(DockHeight)
                .clickable(onClick = open),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.next_up).uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = OverlineTracking,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                    if (queueSize > 0) {
                        Text(
                            text = stringResource(R.string.queue_position_template, position, queueSize),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }

                Text(
                    text = nextVideoTitle ?: stringResource(R.string.playlist_queue),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = playlistName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            FilledTonalIconButton(
                onClick = open,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.playlist_queue),
                )
            }
        }
    }
}
