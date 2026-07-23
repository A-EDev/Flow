package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.screens.music.MusicTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Floating mini player for the TV shell, styled after the mobile mini player:
 * rounded card with artwork + track info (focusable body expands the full
 * player), transport buttons, a dismiss action, and a thin 1 Hz progress line.
 */
@Composable
fun TvNowPlayingStrip(
    track: MusicTrack,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playerState by EnhancedMusicPlayerManager.playerState.collectAsStateWithLifecycle()
    var progressFraction by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(playerState.isPlaying, track.videoId) {
        while (isActive) {
            val duration = EnhancedMusicPlayerManager.getDuration()
            progressFraction = if (duration > 0) {
                (EnhancedMusicPlayerManager.getCurrentPosition().toFloat() / duration).coerceIn(0f, 1f)
            } else {
                0f
            }
            if (!playerState.isPlaying) break
            delay(1_000)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                var bodyFocused by remember { mutableStateOf(false) }
                Surface(
                    onClick = onExpand,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { bodyFocused = it.isFocused },
                    shape = MaterialTheme.shapes.large,
                    color = if (bodyFocused) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    contentColor = if (bodyFocused) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AsyncImage(
                            model = track.listThumbnailUrl,
                            contentDescription = stringResource(R.string.tv_music_expand),
                            modifier = Modifier
                                .size(52.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentScale = ContentScale.Crop,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = track.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                TvIconButton(
                    icon = Icons.Outlined.SkipPrevious,
                    contentDescription = stringResource(R.string.previous),
                    onClick = EnhancedMusicPlayerManager::playPrevious,
                )
                TvIconButton(
                    icon = if (playerState.isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    contentDescription = if (playerState.isPlaying) {
                        stringResource(R.string.pause)
                    } else {
                        stringResource(R.string.play)
                    },
                    onClick = EnhancedMusicPlayerManager::togglePlayPause,
                    active = playerState.isPlaying,
                )
                TvIconButton(
                    icon = Icons.Outlined.SkipNext,
                    contentDescription = stringResource(R.string.next),
                    onClick = EnhancedMusicPlayerManager::playNext,
                )
                TvIconButton(
                    icon = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.dismiss),
                    onClick = onDismiss,
                )
            }
            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                drawStopIndicator = {},
            )
        }
    }
}
