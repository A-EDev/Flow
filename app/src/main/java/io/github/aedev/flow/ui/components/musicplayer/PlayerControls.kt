package io.github.aedev.flow.ui.components.musicplayer

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
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
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.SliderStyle
import io.github.aedev.flow.player.RepeatMode
import io.github.aedev.flow.ui.components.pressScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class PlaybackButtonType { PREVIOUS, PLAY_PAUSE, NEXT }

@Composable
fun PlayerPlaybackControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onPreviousClick: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    onPreviewDirectionChange: (SkipDirection?) -> Unit = {},
) {
    var isPressed by remember { mutableStateOf(false) }
    var isPreviousPressed by remember { mutableStateOf(false) }
    var isNextPressed by remember { mutableStateOf(false) }
    var lastClicked by remember { mutableStateOf<PlaybackButtonType?>(null) }
    var clickTrigger by remember { mutableIntStateOf(0) }
    val latestIsPlaying by rememberUpdatedState(isPlaying)
    val isPlayPauseLocked =
        lastClicked == PlaybackButtonType.NEXT || lastClicked == PlaybackButtonType.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(lastClicked, clickTrigger) {
        if (lastClicked != null) {
            val releaseDelay = if (lastClicked == PlaybackButtonType.PLAY_PAUSE) 220L else 600L
            delay(releaseDelay)
            lastClicked = null
        }
    }

    // Latch the icon while a skip is in flight so play/pause does not flicker on track changes.
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }
        if (lastClicked != PlaybackButtonType.PLAY_PAUSE) {
            delay(220L)
        }
        if (!latestIsPlaying) {
            pendingPlayPauseState = false
        }
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    val elasticSpec = spring<Float>(dampingRatio = 0.62f, stiffness = 720f)

    val playPauseWeight by animateFloatAsState(
        targetValue =
            if (isPressed) {
                1.9f
            } else if (isPreviousPressed || isNextPressed) {
                1.1f
            } else {
                1.3f
            },
        animationSpec = elasticSpec,
        label = "playPauseWeight",
    )
    val previousWeight by animateFloatAsState(
        targetValue =
            if (isPreviousPressed) {
                0.65f
            } else if (isPressed) {
                0.35f
            } else {
                0.45f
            },
        animationSpec = elasticSpec,
        label = "previousWeight",
    )
    val nextWeight by animateFloatAsState(
        targetValue =
            if (isNextPressed) {
                0.65f
            } else if (isPressed) {
                0.35f
            } else {
                0.45f
            },
        animationSpec = elasticSpec,
        label = "nextWeight",
    )
    val playPauseCorner by animateDpAsState(
        targetValue = if (playPauseVisualState) 18.dp else 34.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "playPauseCorner",
    )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(68.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ElasticControlButton(
            weight = previousWeight,
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = stringResource(R.string.previous),
            onClick = {
                lastClicked = PlaybackButtonType.PREVIOUS
                clickTrigger++
                scope.launch {
                    delay(180L)
                    onPreviousClick()
                }
            },
            onPressedChange = { isPreviousPressed = it },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconSize = 30.dp,
            onLongPressStart = { onPreviewDirectionChange(SkipDirection.PREVIOUS) },
            onLongPressEnd = { onPreviewDirectionChange(null) },
        )

        ElasticControlButton(
            weight = playPauseWeight,
            icon = if (playPauseVisualState) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription =
                if (playPauseVisualState) stringResource(R.string.pause) else stringResource(R.string.play),
            onClick = {
                lastClicked = PlaybackButtonType.PLAY_PAUSE
                clickTrigger++
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onPlayPauseToggle()
            },
            onPressedChange = { isPressed = it },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            iconSize = 36.dp,
            cornerRadius = playPauseCorner,
            isBuffering = isBuffering,
        )

        ElasticControlButton(
            weight = nextWeight,
            icon = Icons.Rounded.SkipNext,
            contentDescription = stringResource(R.string.next),
            onClick = {
                lastClicked = PlaybackButtonType.NEXT
                clickTrigger++
                scope.launch {
                    delay(180L)
                    onNextClick()
                }
            },
            onPressedChange = { isNextPressed = it },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            iconSize = 30.dp,
            onLongPressStart = { onPreviewDirectionChange(SkipDirection.NEXT) },
            onLongPressEnd = { onPreviewDirectionChange(null) },
        )
    }
}

private val SegmentFullRadius = 21.dp
private val SegmentEdgeRadius = 8.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerSecondaryActions(
    lyricsActive: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onLyricsClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(42.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayerToggleButton(
            checked = lyricsActive,
            checkedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            icon = Icons.Outlined.Lyrics,
            contentDescription = stringResource(R.string.lyrics),
            uncheckedShape =
                RoundedCornerShape(
                    topStart = SegmentFullRadius,
                    bottomStart = SegmentFullRadius,
                    topEnd = SegmentEdgeRadius,
                    bottomEnd = SegmentEdgeRadius,
                ),
            onClick = onLyricsClick,
        )
        PlayerToggleButton(
            checked = shuffleEnabled,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Rounded.Shuffle,
            contentDescription = stringResource(R.string.shuffle),
            uncheckedShape = RoundedCornerShape(SegmentEdgeRadius),
            onClick = onShuffleClick,
        )
        PlayerToggleButton(
            checked = repeatMode != RepeatMode.OFF,
            checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            icon =
                when (repeatMode) {
                    RepeatMode.ONE -> Icons.Rounded.RepeatOne
                    else -> Icons.Rounded.Repeat
                },
            contentDescription = stringResource(R.string.repeat),
            uncheckedShape = RoundedCornerShape(SegmentEdgeRadius),
            onClick = onRepeatClick,
        )
        PlayerToggleButton(
            checked = false,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            icon = Icons.Outlined.QueueMusic,
            contentDescription = stringResource(R.string.playlist_queue),
            uncheckedShape =
                RoundedCornerShape(
                    topStart = SegmentEdgeRadius,
                    bottomStart = SegmentEdgeRadius,
                    topEnd = SegmentFullRadius,
                    bottomEnd = SegmentFullRadius,
                ),
            onClick = onQueueClick,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RowScope.PlayerToggleButton(
    checked: Boolean,
    checkedContainerColor: Color,
    checkedContentColor: Color,
    icon: ImageVector,
    contentDescription: String?,
    uncheckedShape: Shape,
    onClick: () -> Unit,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        modifier =
            Modifier
                .weight(1f)
                .fillMaxHeight(),
        colors =
            ToggleButtonDefaults.toggleButtonColors(
                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = checkedContainerColor,
                checkedContentColor = checkedContentColor,
            ),
        shapes =
            ToggleButtonShapes(
                shape = uncheckedShape,
                pressedShape = RoundedCornerShape(12.dp),
                checkedShape = RoundedCornerShape(SegmentFullRadius),
            ),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RowScope.ElasticControlButton(
    weight: Float,
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    onPressedChange: (Boolean) -> Unit,
    containerColor: Color,
    contentColor: Color,
    iconSize: Dp,
    cornerRadius: Dp = 34.dp,
    isBuffering: Boolean = false,
    onLongPressStart: (() -> Unit)? = null,
    onLongPressEnd: (() -> Unit)? = null,
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPressedChange by rememberUpdatedState(onPressedChange)
    val currentLongPressStart by rememberUpdatedState(onLongPressStart)
    val currentLongPressEnd by rememberUpdatedState(onLongPressEnd)
    Box(
        modifier =
            Modifier
                .weight(weight)
                .fillMaxHeight()
                .clip(RoundedCornerShape(cornerRadius))
                .background(containerColor)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        currentOnPressedChange(true)
                        var longPressed = false
                        try {
                            var cancelled = false
                            val upBeforeTimeout =
                                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                    val up = waitForUpOrCancellation()
                                    if (up == null) cancelled = true
                                    up
                                }
                            when {
                                cancelled -> {
                                    Unit
                                }

                                upBeforeTimeout != null -> {
                                    currentOnClick()
                                }

                                currentLongPressStart != null -> {
                                    longPressed = true
                                    currentLongPressStart?.invoke()
                                    waitForUpOrCancellation()
                                }

                                else -> {
                                    if (waitForUpOrCancellation() != null) {
                                        currentOnClick()
                                    }
                                }
                            }
                        } finally {
                            currentOnPressedChange(false)
                            if (longPressed) currentLongPressEnd?.invoke()
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = contentColor,
                strokeWidth = 3.dp,
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = contentColor,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerProgressSlider(
    positionProvider: () -> Long,
    duration: Long,
    onSeekTo: (Long) -> Unit,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val currentPosition = positionProvider()
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    var isSeeking by remember { mutableStateOf(false) }
    var seekPreviewPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }
    val seekPreviewScope = rememberCoroutineScope()
    var clearSeekPreviewJob by remember { mutableStateOf<Job?>(null) }
    val sliderEnd = duration.toFloat().coerceAtPositive(1f)
    val isInteracting = isDragged || isPressed || isSeeking
    val displayedPosition =
        if (isInteracting) {
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
        label = "trackHeight",
    )

    val thumbAlpha by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        label = "thumbAlpha",
    )

    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { PlayerPreferences(context) }
    val sliderStyle by preferences.sliderStyle.collectAsState(initial = SliderStyle.METROLIST_SLIM)
    val squigglyEnabled by preferences.squigglySliderEnabled.collectAsState(initial = false)

    Column(
        modifier = modifier.fillMaxWidth(),
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
                clearSeekPreviewJob =
                    seekPreviewScope.launch {
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
                        colors =
                            SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp),
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
                        colors =
                            SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                    )
                }

                SliderStyle.SQUIGGLY -> {
                    SquigglySlider(
                        value = displayedPosition,
                        onValueChange = { handleSeekPreview(it) },
                        onValueChangeFinished = { commitSeekPreview() },
                        valueRange = 0f..sliderEnd,
                        colors =
                            SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                thumbColor = MaterialTheme.colorScheme.primary,
                            ),
                        isPlaying = isPlaying,
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
                                colors =
                                    SliderDefaults.colors(
                                        activeTrackColor = MaterialTheme.colorScheme.primary,
                                        inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                    ),
                                trackHeight = 4.dp,
                            )
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                    )
                }

                SliderStyle.DEFAULT -> {
                    Slider(
                        value = displayedPosition,
                        onValueChange = { handleSeekPreview(it) },
                        onValueChangeFinished = { commitSeekPreview() },
                        valueRange = 0f..sliderEnd,
                        interactionSource = interactionSource,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = Color.Transparent,
                                activeTrackColor = Color.Transparent,
                                inactiveTrackColor = Color.Transparent,
                            ),
                        thumb = {
                            Box(
                                modifier =
                                    Modifier
                                        .size(24.dp)
                                        .graphicsLayer { alpha = thumbAlpha }
                                        .shadow(8.dp, CircleShape)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                            )
                        },
                        track = {
                            val fraction = if (duration > 0) displayedPosition / sliderEnd else 0f

                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(animatedTrackHeight)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            ) {
                                // Active Track
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth(fraction)
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                                        MaterialTheme.colorScheme.primary,
                                                    ),
                                                ),
                                            ),
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(24.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                formatTime(displayedPositionMs),
                style = MaterialTheme.typography.labelSmall,
                color = if (isInteracting) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontWeight = if (isInteracting) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                formatTime(duration),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun Float.coerceAtPositive(minimumValue: Float): Float = if (this < minimumValue) minimumValue else this

@Composable
fun PlayerMainActionButtons(
    isLiked: Boolean,
    isDownloaded: Boolean,
    onLikeClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SplitCapsuleButton(
            active = isDownloaded,
            shape =
                RoundedCornerShape(
                    topStart = 50.dp,
                    topEnd = 6.dp,
                    bottomStart = 50.dp,
                    bottomEnd = 6.dp,
                ),
            icon = if (isDownloaded) Icons.Rounded.OfflinePin else Icons.Outlined.Download,
            contentDescription = stringResource(R.string.download),
            onClick = onDownloadClick,
        )
        SplitCapsuleButton(
            active = isLiked,
            shape =
                RoundedCornerShape(
                    topStart = 6.dp,
                    topEnd = 50.dp,
                    bottomStart = 6.dp,
                    bottomEnd = 50.dp,
                ),
            icon = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            contentDescription = stringResource(R.string.like),
            onClick = onLikeClick,
            onLongClick = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                onAddToPlaylist()
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SplitCapsuleButton(
    active: Boolean,
    shape: RoundedCornerShape,
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val containerColor by animateColorAsState(
        targetValue =
            if (active) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
            },
        animationSpec = tween(durationMillis = 250),
        label = "capsuleContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (active) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                readableAccentOn(
                    container = MaterialTheme.colorScheme.onPrimary,
                    accent = MaterialTheme.colorScheme.primary,
                )
            },
        animationSpec = tween(durationMillis = 250),
        label = "capsuleContent",
    )
    Box(
        modifier =
            Modifier
                .size(width = 50.dp, height = 42.dp)
                .clip(shape)
                .background(containerColor)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
            modifier = Modifier.size(22.dp),
        )
    }
}
