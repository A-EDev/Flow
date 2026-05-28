package io.github.aedev.flow.ui.screens.music.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.OfflinePin
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.RepeatMode
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SliderStyle
import io.github.aedev.flow.ui.components.pressScale
import io.github.aedev.flow.ui.screens.music.player.components.PlayerSliderTrack
import io.github.aedev.flow.ui.screens.music.player.components.SquigglySlider
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@Composable
fun PlayerPlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPreviousClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val previousInteractionSource = remember { MutableInteractionSource() }
        val nextInteractionSource = remember { MutableInteractionSource() }
        val interactionSource = remember { MutableInteractionSource() }
        var isPressed by remember { mutableStateOf(false) }
        var isPreviousPressed by remember { mutableStateOf(false) }
        var isNextPressed by remember { mutableStateOf(false) }

        val elasticSpec = spring<Float>(dampingRatio = 0.62f, stiffness = 720f)

        val playPauseWeight by animateFloatAsState(
            targetValue = if (isPressed) {
                1.9f
            } else if (isPreviousPressed || isNextPressed) {
                1.1f
            } else {
                1.3f
            },
            animationSpec = elasticSpec,
            label = "playPauseWeight"
        )
        val previousWeight by animateFloatAsState(
            targetValue = if (isPreviousPressed) {
                0.65f
            } else if (isPressed) {
                0.35f
            } else {
                0.45f
            },
            animationSpec = elasticSpec,
            label = "previousWeight"
        )
        val nextWeight by animateFloatAsState(
            targetValue = if (isNextPressed) {
                0.65f
            } else if (isPressed) {
                0.35f
            } else {
                0.45f
            },
            animationSpec = elasticSpec,
            label = "nextWeight"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ElasticControlButton(
                weight = previousWeight,
                icon = Icons.Rounded.SkipPrevious,
                contentDescription = stringResource(R.string.previous),
                onClick = onPreviousClick,
                interactionSource = previousInteractionSource,
                onPressedChange = { isPreviousPressed = it },
                containerColor = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White,
                iconSize = 30.dp
            )

            ElasticControlButton(
                weight = playPauseWeight,
                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                onClick = onPlayPauseToggle,
                interactionSource = interactionSource,
                onPressedChange = { isPressed = it },
                containerColor = Color.White,
                contentColor = Color.Black,
                iconSize = 36.dp,
                isBuffering = isBuffering
            )

            ElasticControlButton(
                weight = nextWeight,
                icon = Icons.Rounded.SkipNext,
                contentDescription = stringResource(R.string.next),
                onClick = onNextClick,
                interactionSource = nextInteractionSource,
                onPressedChange = { isNextPressed = it },
                containerColor = Color.White.copy(alpha = 0.12f),
                contentColor = Color.White,
                iconSize = 30.dp
            )
        }
    }
}

@Composable
fun PlayerSecondaryActions(
    lyricsActive: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    sleepTimerActive: Boolean,
    accentColor: Color,
    onLyricsClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onQueueClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SecondaryActionButton(
            icon = Icons.Outlined.Lyrics,
            contentDescription = stringResource(R.string.lyrics),
            isActive = lyricsActive,
            activeColor = accentColor,
            onClick = onLyricsClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        SecondaryActionButton(
            icon = Icons.Rounded.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            isActive = shuffleEnabled,
            activeColor = accentColor,
            onClick = onShuffleClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        SecondaryActionButton(
            icon = when (repeatMode) {
                RepeatMode.ONE -> Icons.Rounded.RepeatOne
                RepeatMode.ALL -> Icons.Rounded.Repeat
                else -> Icons.Rounded.Repeat
            },
            contentDescription = stringResource(R.string.repeat),
            isActive = repeatMode != RepeatMode.OFF,
            activeColor = accentColor,
            onClick = onRepeatClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        SecondaryActionButton(
            icon = Icons.Outlined.QueueMusic,
            contentDescription = stringResource(R.string.playlist_queue),
            isActive = false,
            activeColor = accentColor,
            onClick = onQueueClick
        )
        Spacer(modifier = Modifier.width(8.dp))
        SecondaryActionButton(
            icon = Icons.Outlined.Bedtime,
            contentDescription = stringResource(R.string.sleep_timer),
            isActive = sleepTimerActive,
            activeColor = accentColor,
            onClick = onSleepTimerClick
        )
    }
}

@Composable
private fun SecondaryActionButton(
    icon: ImageVector,
    contentDescription: String?,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = 420f),
        label = "secondaryActionScale"
    )
    IconButton(
        onClick = onClick,
        interactionSource = interactionSource,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (isActive) activeColor.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
            contentColor = if (isActive) activeColor else Color.White.copy(alpha = 0.68f)
        ),
        modifier = Modifier
            .size(48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun RowScope.ElasticControlButton(
    weight: Float,
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    onPressedChange: (Boolean) -> Unit,
    containerColor: Color,
    contentColor: Color,
    iconSize: Dp,
    isBuffering: Boolean = false
) {
    Box(
        modifier = Modifier
            .weight(weight)
            .fillMaxHeight()
            .clip(RoundedCornerShape(50))
            .background(containerColor)
            .pointerInput(onPressedChange) {
                awaitEachGesture {
                    try {
                        awaitFirstDown(requireUnconsumed = false)
                        onPressedChange(true)
                        waitForUpOrCancellation()
                    } finally {
                        onPressedChange(false)
                    }
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = contentColor,
                strokeWidth = 3.dp
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize)
            )
        }
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
    var isSeeking by remember { mutableStateOf(false) }
    var seekPreviewPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }
    val seekPreviewScope = rememberCoroutineScope()
    var clearSeekPreviewJob by remember { mutableStateOf<Job?>(null) }
    val sliderEnd = duration.toFloat().coerceAtPositive(1f)
    val isInteracting = isDragged || isPressed || isSeeking
    val displayedPosition = if (isInteracting) {
        seekPreviewPosition.coerceIn(0f, sliderEnd)
    } else {
        currentPosition.toFloat().coerceIn(0f, sliderEnd)
    }
    val displayedPositionMs = displayedPosition.toLong()

    LaunchedEffect(currentPosition, sliderEnd, isInteracting) {
        if (!isInteracting) {
            seekPreviewPosition = currentPosition.toFloat().coerceIn(0f, sliderEnd)
        }
    }

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
            fun handleSeekPreview(value: Float) {
                clearSeekPreviewJob?.cancel()
                seekPreviewPosition = value.coerceIn(0f, sliderEnd)
                isSeeking = true
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }

            fun commitSeekPreview() {
                if (isSeeking) {
                    onSeekTo(seekPreviewPosition.toLong())
                }
                clearSeekPreviewJob?.cancel()
                clearSeekPreviewJob = seekPreviewScope.launch {
                    delay(200)
                    isSeeking = false
                }
            }
            
            when (sliderStyle) {
                SliderStyle.METROLIST -> {
                    // Metrolist Thick Style 
                    Slider(
                        value = displayedPosition,
                        onValueChange = { handleSeekPreview(it) },
                        onValueChangeFinished = { commitSeekPreview() },
                        valueRange = 0f..sliderEnd,
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
                        value = displayedPosition,
                        onValueChange = { handleSeekPreview(it) },
                        onValueChangeFinished = { commitSeekPreview() },
                        valueRange = 0f..sliderEnd,
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
                        value = displayedPosition,
                        onValueChange = { handleSeekPreview(it) },
                        onValueChangeFinished = { commitSeekPreview() },
                        valueRange = 0f..sliderEnd,
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
                        value = displayedPosition,
                        onValueChange = { handleSeekPreview(it) },
                        onValueChangeFinished = { commitSeekPreview() },
                        valueRange = 0f..sliderEnd,
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
                        value = displayedPosition,
                        onValueChange = { handleSeekPreview(it) },
                        onValueChangeFinished = { commitSeekPreview() },
                        valueRange = 0f..sliderEnd,
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
                            val fraction = if (duration > 0) displayedPosition / sliderEnd else 0f
                            
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
                formatTime(displayedPositionMs),
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
    accentColor: Color = MaterialTheme.colorScheme.primary,
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
        val downloadInteractionSource = remember { MutableInteractionSource() }
        IconButton(
            onClick = onDownloadClick,
            modifier = Modifier.size(40.dp).pressScale(downloadInteractionSource),
            interactionSource = downloadInteractionSource
        ) {
            Icon(
                imageVector = if (isDownloaded) Icons.Rounded.OfflinePin else Icons.Outlined.Download,
                contentDescription = stringResource(R.string.download),
                tint = if (isDownloaded) accentColor else Color.White,
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
                tint = if (isLiked) accentColor else Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PlayerLyricsRefreshButton(
    isLoading: Boolean,
    accentColor: Color,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isLoading) {
        if (isLoading) {
            while (true) {
                rotation.snapTo(0f)
                rotation.animateTo(
                    targetValue = 360f,
                    animationSpec = tween(durationMillis = 900, easing = LinearEasing)
                )
            }
        } else {
            rotation.snapTo(0f)
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

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
        IconButton(
            onClick = {
                if (!isLoading) {
                    coroutineScope.launch {
                        rotation.snapTo(0f)
                        rotation.animateTo(
                            targetValue = 360f,
                            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing)
                        )
                    }
                    onRefresh()
                }
            },
            modifier = Modifier
                .size(40.dp)
                .pressScale(interactionSource),
            interactionSource = interactionSource
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.refresh_lyrics),
                tint = if (isLoading) accentColor else Color.White,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation.value }
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