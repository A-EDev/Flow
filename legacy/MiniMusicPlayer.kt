package com.flow.youtube.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.ui.screens.music.MusicTrack

/**
  mini floating music player with modern design and dismiss functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniMusicPlayer(
    modifier: Modifier = Modifier,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onDismiss: () -> Unit, // New dismiss callback
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    progress: Float
) {
    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(400)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(76.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shadowElevation = 16.dp,
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box {
                // Premium gradient background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
                                )
                            )
                        )
                )

                // Progress indicator with glow effect
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .align(Alignment.TopCenter),
                    color = Color.Transparent
                ) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Album art + track info
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Premium album art with enhanced styling
                        Surface(
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(12.dp),
                            shadowElevation = 8.dp,
                            tonalElevation = 2.dp
                        ) {
                            Box {
                                AsyncImage(
                                    model = currentTrack?.thumbnailUrl,
                                    contentDescription = "Album art",
                                    modifier = Modifier.fillMaxSize()
                                )

                                // Playing state indicator
                                if (isPlaying) {
                                    PlayingIndicator()
                                }
                            }
                        }

                        // Enhanced track info
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = currentTrack?.title ?: "",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = currentTrack?.artist ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Premium playback controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous button (compact)
                        IconButton(
                            onClick = { /* Previous track */ },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipPrevious,
                                contentDescription = "Previous",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Enhanced play/pause button
                        FilledIconButton(
                            onClick = onPlayPauseClick,
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(
                                imageVector = if (isPlaying)
                                    Icons.Filled.Pause
                                else
                                    Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(24.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }

                        // Next button
                        IconButton(
                            onClick = onNextClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Dismiss/Close button
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close player",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Modern playing state indicator with subtle glow effect
 */
@Composable
private fun PlayingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "playing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .scale(scale)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                        Color.Transparent
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Equalizer,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
        )
    }
}

/**
 * Compact mini player for use in constrained spaces
 */
@Composable
fun CompactMiniPlayer(
    modifier: Modifier = Modifier,
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onDismiss: () -> Unit // New dismiss callback
) {
    AnimatedVisibility(
        visible = currentTrack != null,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(),
        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 3.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Modern album art
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        shadowElevation = 4.dp
                    ) {
                        Box {
                            AsyncImage(
                                model = currentTrack?.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (isPlaying) {
                                PlayingIndicator()
                            }
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack?.title ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = currentTrack?.artist ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Play/Pause button
                    FilledIconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying)
                                Icons.Filled.Pause
                            else
                                Icons.Filled.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }

                    // Dismiss button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close player",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Enhanced mini player with waveform visualization and premium design
 */
@Composable
fun VisualizerMiniPlayer(
    modifier: Modifier = Modifier,
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    progress: Float,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onDismiss: () -> Unit // New dismiss callback
) {
    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(84.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            shadowElevation = 20.dp,
            tonalElevation = 6.dp,
            shape = RoundedCornerShape(24.dp)
        ) {
            Box {
                // Premium gradient background with blur effect
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        )
                )

                Column(modifier = Modifier.fillMaxSize()) {
                    // Enhanced progress bar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp),
                        color = Color.Transparent
                    ) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Premium track info with larger album art
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(60.dp),
                                shape = RoundedCornerShape(14.dp),
                                shadowElevation = 10.dp,
                                tonalElevation = 4.dp
                            ) {
                                Box {
                                    AsyncImage(
                                        model = currentTrack?.thumbnailUrl,
                                        contentDescription = "Album art",
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isPlaying) {
                                        PlayingIndicator()
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = currentTrack?.title ?: "",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = currentTrack?.artist ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Premium full controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onPreviousClick,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Previous",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            FilledIconButton(
                                onClick = onPlayPauseClick,
                                modifier = Modifier.size(56.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = if (isPlaying)
                                        Icons.Filled.Pause
                                    else
                                        Icons.Filled.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(28.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }

                            IconButton(
                                onClick = onNextClick,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(24.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Dismiss button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close player",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Enhanced mini player state manager with dismiss functionality
 */
@Composable
fun rememberMiniPlayerState(
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    progress: Float,
    onDismiss: () -> Unit
): MiniPlayerState {
    return remember(currentTrack, isPlaying, progress) {
        MiniPlayerState(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            progress = progress,
            onDismiss = onDismiss
        )
    }
}

data class MiniPlayerState(
    val currentTrack: MusicTrack?,
    val isPlaying: Boolean,
    val progress: Float,
    val onDismiss: () -> Unit = {}
)
