package io.github.aedev.flow.ui.screens.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.screens.music.components.TrackListItem

@Composable
fun MusicTrackRow(
    index: Int? = null,
    track: MusicTrack,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
    onMenuClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    isPlaying: Boolean = false,
    isDownloaded: Boolean = false
) {
    val backgroundColor = if (isPlaying) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent

    TrackListItem(
        track = track,
        modifier = Modifier.background(backgroundColor),
        isPlaying = isPlaying,
        isDownloaded = isDownloaded,
        showMenu = trailingContent == null,
        leadingContent = if (index != null) {
            {
                Text(
                    text = index.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal
                    ),
                    color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(32.dp),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            null
        },
        trailingContent = trailingContent?.let { content -> { content() } },
        onClick = onClick,
        onLongClick = onLongClick,
        onMenuClick = onMenuClick
    )
}

fun formatDuration(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(minutes, secs)
}

fun formatViews(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
