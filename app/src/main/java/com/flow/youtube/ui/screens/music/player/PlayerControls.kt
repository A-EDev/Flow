package com.flow.youtube.ui.screens.music.player

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flow.youtube.R
import com.flow.youtube.player.RepeatMode

@Composable
fun PlayerPlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onShuffleToggle: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onNextClick: () -> Unit,
    onRepeatToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Shuffle
        IconButton(onClick = onShuffleToggle) {
            Icon(
                Icons.Outlined.Shuffle,
                contentDescription = stringResource(R.string.shuffle),
                tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
            )
        }
        
        // Previous
        IconButton(
            onClick = onPreviousClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = stringResource(R.string.previous),
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Play/Pause
        Surface(
            shape = CircleShape,
            color = Color.White,
            modifier = Modifier.size(72.dp),
            onClick = onPlayPauseToggle
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.Black,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                        tint = Color.Black,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Next
        IconButton(
            onClick = onNextClick,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = stringResource(R.string.next),
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Repeat
        IconButton(onClick = onRepeatToggle) {
            Icon(
                when (repeatMode) {
                    RepeatMode.ONE -> Icons.Outlined.RepeatOne
                    else -> Icons.Outlined.Repeat
                },
                contentDescription = stringResource(R.string.repeat),
                tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun PlayerProgressSlider(
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Slider(
            value = currentPosition.toFloat(),
            onValueChange = { onSeekTo(it.toLong()) },
            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun PlayerActionButtons(
    isDownloaded: Boolean,
    onShare: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        PillButton(
            icon = Icons.Outlined.Share,
            text = stringResource(R.string.share),
            onClick = onShare
        )
        
        PillButton(
            icon = if (isDownloaded) Icons.Filled.CheckCircle else Icons.Outlined.DownloadForOffline,
            text = if (isDownloaded) stringResource(R.string.downloaded) else stringResource(R.string.download),
            onClick = onDownload
        )
        PillButton(
            icon = Icons.Outlined.PlaylistAdd,
            text = stringResource(R.string.save),
            onClick = onAddToPlaylist
        )
    }
}
