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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.ui.screens.music.MusicTrack

/**
 * Mini floating music player that appears at the bottom of all screens
 * when music is playing. Can be expanded to full player.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MiniMusicPlayer(
    modifier: Modifier = Modifier,
    onExpandClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    progress: Float
) {
    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically(
            initialOffsetY = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = slideOutVertically(
            targetOffsetY = { it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(72.dp)
                .clickable(onClick = onExpandClick),
            shadowElevation = 8.dp,
            tonalElevation = 3.dp,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
        ) {
            Box {
                // Progress bar at the top
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .align(Alignment.TopCenter),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Album art + track info
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Album art with pulsing animation
                        Surface(
                            modifier = Modifier.size(56.dp),
                            shape = RoundedCornerShape(8.dp),
                            shadowElevation = 4.dp
                        ) {
                            Box {
                                AsyncImage(
                                    model = currentTrack?.thumbnailUrl,
                                    contentDescription = "Album art",
                                    modifier = Modifier.fillMaxSize()
                                )
                                
                                // Playing animation overlay
                                if (isPlaying) {
                                    PulsingOverlay()
                                }
                            }
                        }
                        
                        // Track info
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = currentTrack?.title ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
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
                    
                    // Playback controls
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play/Pause button
                        IconButton(
                            onClick = onPlayPauseClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaying) 
                                    Icons.Filled.Pause 
                                else 
                                    Icons.Filled.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                modifier = Modifier.size(32.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        
                        // Next button
                        IconButton(
                            onClick = onNextClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = "Next",
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Pulsing overlay for playing state
 */
@Composable
private fun PulsingOverlay() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha))
    )
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
    onPlayPauseClick: () -> Unit
) {
    AnimatedVisibility(
        visible = currentTrack != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(56.dp)
                .clickable(onClick = onExpandClick),
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Small album art
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        AsyncImage(
                            model = currentTrack?.thumbnailUrl,
                            contentDescription = null
                        )
                    }
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentTrack?.title ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (isPlaying) 
                            Icons.Filled.Pause 
                        else 
                            Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play"
                    )
                }
            }
        }
    }
}

/**
 * Enhanced mini player with waveform visualization
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
    onPreviousClick: () -> Unit
) {
    AnimatedVisibility(
        visible = currentTrack != null,
        enter = slideInVertically { it },
        exit = slideOutVertically { it }
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .height(80.dp)
                .clickable(onClick = onExpandClick),
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Box {
                // Gradient background
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        )
                )
                
                Column(modifier = Modifier.fillMaxSize()) {
                    // Progress bar
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Track info with larger album art
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(60.dp),
                                shape = RoundedCornerShape(10.dp),
                                shadowElevation = 6.dp
                            ) {
                                AsyncImage(
                                    model = currentTrack?.thumbnailUrl,
                                    contentDescription = "Album art"
                                )
                            }
                            
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = currentTrack?.title ?: "",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
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
                        
                        // Full controls
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onPreviousClick) {
                                Icon(
                                    imageVector = Icons.Filled.SkipPrevious,
                                    contentDescription = "Previous",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            
                            FilledIconButton(
                                onClick = onPlayPauseClick,
                                modifier = Modifier.size(52.dp),
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
                                    modifier = Modifier.size(32.dp),
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                            
                            IconButton(onClick = onNextClick) {
                                Icon(
                                    imageVector = Icons.Filled.SkipNext,
                                    contentDescription = "Next",
                                    modifier = Modifier.size(28.dp)
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
 * Global mini player state manager
 */
@Composable
fun rememberMiniPlayerState(
    currentTrack: MusicTrack?,
    isPlaying: Boolean,
    progress: Float
): MiniPlayerState {
    return remember(currentTrack, isPlaying, progress) {
        MiniPlayerState(
            currentTrack = currentTrack,
            isPlaying = isPlaying,
            progress = progress
        )
    }
}

data class MiniPlayerState(
    val currentTrack: MusicTrack?,
    val isPlaying: Boolean,
    val progress: Float
)
