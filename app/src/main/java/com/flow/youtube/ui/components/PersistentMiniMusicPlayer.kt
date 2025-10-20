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
import androidx.compose.ui.draw.clip
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
 * Premium persistent mini music player that appears at the bottom of screens
 * Features modern design with gradient backgrounds, enhanced animations, and dismiss functionality
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersistentMiniMusicPlayer(
    onExpandClick: () -> Unit,
    onDismiss: () -> Unit, // New dismiss callback
    modifier: Modifier = Modifier
) {
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    val playerState by EnhancedMusicPlayerManager.playerState.collectAsState()

    // Track progress for smooth animation
    var currentPosition by remember { mutableStateOf(0L) }

    // Update progress every 100ms when playing
    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying) {
            while (playerState.isPlaying) {
                kotlinx.coroutines.delay(100)
                currentPosition = EnhancedMusicPlayerManager.getCurrentPosition()
            }
        }
    }

    // Animate visibility
    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(400, easing = FastOutSlowInEasing)
        ) + fadeIn(animationSpec = tween(400)),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(300)),
        modifier = modifier
    ) {
        currentTrack?.let { track ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(onClick = onExpandClick),
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
                    val progress = if (playerState.duration > 0) {
                        (currentPosition.toFloat() / playerState.duration.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    }

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
                                        model = track.thumbnailUrl,
                                        contentDescription = "Album art",
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Playing state indicator
                                    if (playerState.isPlaying) {
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
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = track.artist,
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
                                onClick = { EnhancedMusicPlayerManager.playPrevious() },
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
                                onClick = { EnhancedMusicPlayerManager.togglePlayPause() },
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (playerState.isBuffering) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (playerState.isPlaying)
                                            Icons.Filled.Pause
                                        else
                                            Icons.Filled.PlayArrow,
                                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }

                            // Next button
                            IconButton(
                                onClick = { EnhancedMusicPlayerManager.playNext() },
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
 * Premium compact mini player for use in constrained spaces
 */
@Composable
fun CompactMiniMusicPlayer(
    onExpandClick: () -> Unit,
    onDismiss: () -> Unit, // New dismiss callback
    modifier: Modifier = Modifier
) {
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    val playerState by EnhancedMusicPlayerManager.playerState.collectAsState()

    AnimatedVisibility(
        visible = currentTrack != null,
        enter = fadeIn(animationSpec = tween(300)) + expandVertically(),
        exit = fadeOut(animationSpec = tween(200)) + shrinkVertically(),
        modifier = modifier
    ) {
        currentTrack?.let { track ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(onClick = onExpandClick),
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
                                    model = track.thumbnailUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (playerState.isPlaying) {
                                    PlayingIndicator()
                                }
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track.artist,
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
                            onClick = { EnhancedMusicPlayerManager.togglePlayPause() },
                            modifier = Modifier.size(44.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            if (playerState.isBuffering) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Icon(
                                    imageVector = if (playerState.isPlaying)
                                        Icons.Filled.Pause
                                    else
                                        Icons.Filled.PlayArrow,
                                    contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
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
}
