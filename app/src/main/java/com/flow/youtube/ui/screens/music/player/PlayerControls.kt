package com.flow.youtube.ui.screens.music.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.flow.youtube.R
import com.flow.youtube.player.RepeatMode
import com.flow.youtube.data.local.PlayerPreferences
import com.flow.youtube.data.local.SliderStyle
import com.flow.youtube.ui.screens.music.player.components.PlayerSliderTrack
import com.flow.youtube.ui.screens.music.player.components.SquigglySlider


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
        ControlButton(
            icon =  Icons.Rounded.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            onClick = onShuffleToggle,
            isActive = shuffleEnabled,
            size = 24.dp,
            activeColor = Color.White,
            inactiveColor = Color.White.copy(alpha = 0.4f)
        )
        
        // Previous
        ControlButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.previous),
            onClick = onPreviousClick,
            size = 36.dp
        )

        // Play/Pause
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        val scale by animateFloatAsState(
            targetValue = if (isPressed) 0.85f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 400f),
            label = "scale"
        )
        
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFE0E0E0)
                        )
                    )
                )
                .clickable(
                    interactionSource = interactionSource,
                    indication = null
                ) { onPlayPauseToggle() }
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = Color.Black,
                    strokeWidth = 3.dp
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                    tint = Color.Black,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // Next
        ControlButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.next),
            onClick = onNextClick,
            size = 36.dp
        )

        // Repeat
        ControlButton(
            icon = when (repeatMode) {
                RepeatMode.ONE -> Icons.Rounded.RepeatOne
                RepeatMode.ALL -> Icons.Rounded.Repeat
                else -> Icons.Rounded.Repeat
            },
            contentDescription = stringResource(R.string.repeat),
            onClick = onRepeatToggle,
            isActive = repeatMode != RepeatMode.OFF,
            size = 24.dp,
            activeColor = Color.White,
            inactiveColor = Color.White.copy(alpha = 0.4f)
        )
    }
}

@Composable
fun ControlButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    isActive: Boolean = false,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.75f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 400f),
        label = "scale"
    )
    
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        interactionSource = interactionSource,
        colors = IconButtonDefaults.iconButtonColors(contentColor = if (isActive) activeColor else inactiveColor)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(size)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProgressSlider(
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val animatedTrackHeight by animateDpAsState(
        targetValue = if (isInteracting) 16.dp else 12.dp,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "trackHeight"
    )
    
    val thumbAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        label = "thumbAlpha"
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { PlayerPreferences(context) }
    val sliderStyle by preferences.sliderStyle.collectAsState(initial = SliderStyle.DEFAULT)
    val squigglyEnabled by preferences.squigglySliderEnabled.collectAsState(initial = false)

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Box(contentAlignment = Alignment.Center) {
            val haptic = LocalHapticFeedback.current
            
            when (sliderStyle) {
                SliderStyle.METROLIST -> {
                    // Metrolist Thick Style 
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { 
                            if (isInteracting) {
                                 haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onSeekTo(it.toLong()) 
                        },
                        valueRange = 0f..duration.toFloat().coerceAtPositive(1f),
                        interactionSource = interactionSource,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp) 
                    )
                }
                SliderStyle.METROLIST_SLIM -> {
                    // Metrolist Slim Style
                     Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { 
                            if (isInteracting) {
                                 haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onSeekTo(it.toLong()) 
                        },
                        valueRange = 0f..duration.toFloat().coerceAtPositive(1f),
                        interactionSource = interactionSource,
                         colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )
                }
                SliderStyle.SQUIGGLY -> {
                     SquigglySlider(
                        value = currentPosition.toFloat(),
                        onValueChange = { onSeekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat().coerceAtPositive(1f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f),
                            thumbColor = Color.White
                        ),
                        isPlaying = isPlaying 
                    )
                }
                SliderStyle.SLIM -> {
                     Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { 
                             if (isInteracting) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                             onSeekTo(it.toLong()) 
                        },
                        valueRange = 0f..duration.toFloat().coerceAtPositive(1f),
                        interactionSource = interactionSource,
                        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                        track = { sliderState ->
                            PlayerSliderTrack(
                                sliderState = sliderState,
                                colors = SliderDefaults.colors(
                                    activeTrackColor = Color.White,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                trackHeight = 4.dp
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                    )
                }
                SliderStyle.DEFAULT -> {
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = { 
                            if (isInteracting) {
                                 haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                            onSeekTo(it.toLong()) 
                        },
                        valueRange = 0f..duration.toFloat().coerceAtPositive(1f),
                        interactionSource = interactionSource,
                        colors = SliderDefaults.colors(
                            thumbColor = Color.Transparent,
                            activeTrackColor = Color.Transparent,
                            inactiveTrackColor = Color.Transparent
                        ),
                        thumb = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .graphicsLayer { alpha = thumbAlpha }
                                    .shadow(8.dp, CircleShape)
                                    .background(Color.White, CircleShape)
                            )
                        },
                        track = {
                            val fraction = if (duration > 0) currentPosition.toFloat() / duration.toFloat().coerceAtLeast(1f) else 0f
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(animatedTrackHeight)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.15f))
                            ) {
                                // Active Track
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(fraction)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color.White.copy(alpha = 0.8f),
                                                    Color.White
                                                )
                                            )
                                        )
                                )
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(currentPosition),
                style = MaterialTheme.typography.labelSmall,
                color = if (isInteracting) Color.White else Color.White.copy(alpha = 0.5f),
                fontWeight = if (isInteracting) FontWeight.Bold else FontWeight.Medium
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun Float.coerceAtPositive(minimumValue: Float): Float {
    return if (this < minimumValue) minimumValue else this
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerMainActionButtons(
    isLiked: Boolean,
    isDownloaded: Boolean,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(32.dp)
            )
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Download Button
        IconButton(
            onClick = onDownloadClick,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                imageVector = if (isDownloaded) Icons.Rounded.OfflinePin else Icons.Outlined.Download,
                contentDescription = stringResource(R.string.download),
                tint = if (isDownloaded) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(2.dp))
        
        // Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(Color.White.copy(alpha = 0.2f))
        )
        
        Spacer(modifier = Modifier.width(2.dp))
        
        // Like Button
        val likeInteractionSource = remember { MutableInteractionSource() }
        val isLikePressed by likeInteractionSource.collectIsPressedAsState()
        val likeScale by animateFloatAsState(
            targetValue = if (isLikePressed) 0.8f else if (isLiked) 1.2f else 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
            label = "likeScale"
        )
        // Reset the bounce effect
        val finalLikeScale = if (likeScale > 1f) 1f else likeScale

        Box(
            modifier = Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = likeScale
                    scaleY = likeScale
                }
                .clip(CircleShape)
                .combinedClickable(
                    interactionSource = likeInteractionSource,
                    indication = LocalIndication.current,
                    onClick = onLikeClick,
                    onLongClick = onAddToPlaylist
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                contentDescription = stringResource(R.string.like),
                tint = if (isLiked) MaterialTheme.colorScheme.primary else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun MinimalActionButton(
    icon: ImageVector,
    text: String,
    onClick: () -> Unit,
    isActive: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = if (isActive) Color.Green else Color.White.copy(alpha = 0.8f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) Color.Green else Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}