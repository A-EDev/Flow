package io.github.aedev.flow.ui.screens.player

import android.os.SystemClock
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.ui.screens.player.components.SeekbarWithPreview
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.zIndex
import io.github.aedev.flow.R
import io.github.aedev.flow.player.CastHelper
import io.github.aedev.flow.data.local.DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.quality.QualityManager
import io.github.aedev.flow.ui.components.pressScale
import org.schabi.newpipe.extractor.stream.StreamSegment
import kotlin.math.abs

private const val LIVE_SCRUB_SEEK_INTERVAL_MS = 80L
private const val LIVE_SCRUB_IMMEDIATE_DELTA_MS = 750L

@Composable
fun PremiumControlsOverlay(
    isVisible: Boolean,
    isPlaying: Boolean,
    hasEnded: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    qualityLabel: String?,
    videoTitle: String?,
    playbackSpeed: Float = 1.0f,
    resizeMode: Int,
    onResizeClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onQualityClick: () -> Unit = {},
    onSpeedClick: () -> Unit = {},
    onFullscreenClick: () -> Unit,
    isFullscreen: Boolean,
    isPipSupported: Boolean = false,
    onPipClick: () -> Unit = {},
    seekbarPreviewHelper: SeekbarPreviewThumbnailHelper?,
    chapters: List<StreamSegment> = emptyList(),
    onChapterClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    isSubtitlesEnabled: Boolean = false,
    autoplayEnabled: Boolean = true,
    isLooping: Boolean = false,
    onAutoplayToggle: (Boolean) -> Unit = {},
    onPrevious: () -> Unit = {},
    onNext: () -> Unit = {},
    hasPrevious: Boolean = false,
    hasNext: Boolean = false,
    bufferedPercentage: Float = 0f,
    windowInsets: WindowInsets = WindowInsets.systemBars,
    sbSubmitEnabled: Boolean = false,
    onSbSubmitClick: () -> Unit = {},
    // Cast / Chromecast support
    onCastClick: () -> Unit = {},
    isCasting: Boolean = false,
    isLive: Boolean = false,
    onLiveClick: () -> Unit = {},
    onSleepTimerClick: () -> Unit = {},
    isSleepTimerActive: Boolean = false,
    showRemainingTime: Boolean = false,
    onToggleRemainingTime: () -> Unit = {},
    isTouchLocked: Boolean = false,
    lockModeEnabled: Boolean = false,
    onTouchLockToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val resizeModes = listOf(
        stringResource(R.string.resize_fit),
        stringResource(R.string.resize_fill),
        stringResource(R.string.resize_zoom)
    )
    val scrubScope = rememberCoroutineScope()
    
    var scrubPosition by remember { mutableStateOf<Long?>(null) }
    var isScrubbing by remember { mutableStateOf(false) }
    var lastScrubSeekAt by remember { mutableLongStateOf(0L) }
    var lastScrubSeekPosition by remember { mutableLongStateOf(Long.MIN_VALUE) }
    var pendingScrubSeekJob by remember { mutableStateOf<Job?>(null) }
    val displayedPosition = scrubPosition ?: currentPosition

    DisposableEffect(Unit) {
        onDispose {
            pendingScrubSeekJob?.cancel()
            EnhancedPlayerManager.getInstance().setScrubbingModeEnabled(false)
        }
    }

    LaunchedEffect(currentPosition, scrubPosition, isScrubbing) {
        if (isScrubbing) {
            return@LaunchedEffect
        }
        val targetPosition = scrubPosition ?: return@LaunchedEffect
        if (abs(currentPosition - targetPosition) <= 1_000L) {
            scrubPosition = null
        }
    }

    // Find current chapter
    val currentChapter = remember(displayedPosition, chapters) {
        val positionSeconds = displayedPosition / 1000
        chapters.lastOrNull { it.startTimeSeconds <= positionSeconds }
    }
    
    val sponsorSegments by EnhancedPlayerManager.getInstance().sponsorSegments.collectAsState()

    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val overlayCastEnabled by playerPreferences.overlayCastEnabled.collectAsState(initial = false)
    val overlayCcEnabled by playerPreferences.overlayCcEnabled.collectAsState(initial = false)
    val overlayPipEnabled by playerPreferences.overlayPipEnabled.collectAsState(initial = false)
    val overlayAutoplayEnabled by playerPreferences.overlayAutoplayEnabled.collectAsState(initial = false)
    val overlaySleepTimerEnabled by playerPreferences.overlaySleepTimerEnabled.collectAsState(initial = false)
    val overlaySpeedIndicatorEnabled by playerPreferences.overlaySpeedIndicatorEnabled.collectAsState(initial = false)
    val showFullscreenTitle by playerPreferences.showFullscreenTitle.collectAsState(initial = false)
    val fullscreenSeekbarHorizontalPaddingDp by playerPreferences.fullscreenSeekbarHorizontalPaddingDp.collectAsState(
        initial = DEFAULT_FULLSCREEN_SEEKBAR_PADDING_DP
    )
    val fullscreenSeekbarBottomPadding = if (isFullscreen) 30.dp else 0.dp
    val topControlHorizontalPadding = if (isFullscreen) 56.dp else 20.dp
    val topControlVerticalPadding = if (isFullscreen) 8.dp else 0.dp
    val bottomControlHorizontalPadding = if (isFullscreen) 56.dp else 10.dp
    val bottomControlsSeekbarOverlap = 0.dp
    val seekbarHorizontalPadding = if (isFullscreen) fullscreenSeekbarHorizontalPaddingDp.dp else 0.dp
    val chapterMaxWidth = if (isFullscreen) 240.dp else 96.dp
    val compactQualityLabel = remember(qualityLabel) { qualityLabel?.toCompactQualityLabel() }
    val speedIndicatorLabel = remember(playbackSpeed) { playbackSpeed.toSpeedIndicatorLabel() }


    val isInitialLoading = isBuffering && duration <= 0L && currentPosition <= 0L

    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets)
    ) {
        AnimatedVisibility(
            visible = isVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(300)),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        when {
                            isTouchLocked -> Color.Transparent
                            isInitialLoading -> Color.Black
                            else -> Color.Black.copy(alpha = 0.24f)
                        }
                    )
            ) {
            if (isTouchLocked) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                )

                Surface(
                    color = Color.Black.copy(alpha = 0.42f),
                    shape = CircleShape,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(color = Color.White),
                            onClick = onTouchLockToggle
                        )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.LockOpen,
                            contentDescription = stringResource(R.string.player_unlock_controls),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                // Top Bar
            if (!isInitialLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopStart)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent)
                            )
                        )
                ) {
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = topControlHorizontalPadding, vertical = topControlVerticalPadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Down Arrow (Minimize/Back)
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(),
                                onClick = { onBack() }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.btn_minimize),
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    if (isFullscreen && showFullscreenTitle && !videoTitle.isNullOrBlank()) {
                        Text(
                            text = videoTitle,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        )
                    }

                    // PiP Button
                    if (isPipSupported && overlayPipEnabled) {
                        IconButton(
                            onClick = onPipClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PictureInPicture,
                                contentDescription = stringResource(R.string.pip_mode),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // SponsorBlock Submit Button
                    if (sbSubmitEnabled) {
                        IconButton(
                            onClick = onSbSubmitClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_upload_segment),
                                contentDescription = stringResource(R.string.sb_submit_dialog_title),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                // Right Actions: Cast, CC, Autoplay, Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (overlaySpeedIndicatorEnabled) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .height(32.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onSpeedClick() }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = speedIndicatorLabel,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    // Resize Button (Only in Fullscreen)
                    if (isFullscreen) {
                        IconButton(
                            onClick = onResizeClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = when (resizeMode) {
                                    0 -> Icons.Rounded.AspectRatio 
                                    1 -> Icons.Rounded.Fullscreen 
                                    else -> Icons.Rounded.ZoomIn 
                                },
                                contentDescription = stringResource(R.string.resize_to, resizeModes[resizeMode]),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Cast button
                    if (overlayCastEnabled) {
                        IconButton(
                            onClick = onCastClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isCasting) Icons.Rounded.Cast else Icons.Outlined.Cast,
                                contentDescription = stringResource(R.string.cast_to_tv),
                                tint = if (isCasting) primaryColor else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // CC Icon
                    if (overlayCcEnabled) {
                        IconButton(
                            onClick = onSubtitleClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = if (isSubtitlesEnabled) Icons.Rounded.ClosedCaption else Icons.Outlined.ClosedCaption,
                                contentDescription = stringResource(R.string.captions),
                                tint = if (isSubtitlesEnabled) primaryColor else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Autoplay Toggle Icon
                    if (overlayAutoplayEnabled) {
                        IconButton(
                            onClick = { if (!isLooping) onAutoplayToggle(!autoplayEnabled) },
                            enabled = !isLooping,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SlowMotionVideo,
                                contentDescription = stringResource(R.string.autoplay),
                                tint = when {
                                    isLooping -> Color.White.copy(alpha = 0.35f)
                                    autoplayEnabled -> primaryColor
                                    else -> Color.White.copy(alpha = 0.7f)
                                },
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Sleep Timer
                    if (overlaySleepTimerEnabled) {
                        IconButton(
                            onClick = onSleepTimerClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Bedtime,
                                contentDescription = stringResource(R.string.sleep_timer),
                                tint = if (isSleepTimerActive) primaryColor else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (lockModeEnabled) {
                        IconButton(
                            onClick = onTouchLockToggle,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = stringResource(R.string.player_lock_controls),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.settings),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
                }
        }

        // Center Controls
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp)
                ) {
                    // Previous Video
                    if (!isInitialLoading) {
                        val prevInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = onPrevious,
                            enabled = hasPrevious,
                            modifier = Modifier.size(48.dp).pressScale(prevInteractionSource, pressedScale = 0.82f),
                            interactionSource = prevInteractionSource
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipPrevious,
                                contentDescription = stringResource(R.string.previous_video),
                                tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    // Play/Pause
                    val playPauseInteractionSource = remember { MutableInteractionSource() }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(62.dp)
                            .pressScale(playPauseInteractionSource, pressedScale = 0.88f)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable(
                                interactionSource = playPauseInteractionSource,
                                indication = ripple(color = Color.White)
                            ) { onPlayPause() }
                    ) {
                        if ((isBuffering || isInitialLoading) && !isScrubbing) {
                            SleekLoadingAnimation(modifier = Modifier.size(48.dp))
                        } else {
                            Icon(
                                imageVector = when {
                                    hasEnded -> Icons.Rounded.Replay
                                    isPlaying -> Icons.Rounded.Pause
                                    else -> Icons.Rounded.PlayArrow
                                },
                                contentDescription = when {
                                    hasEnded -> "Replay"
                                    isPlaying -> stringResource(R.string.pause)
                                    else -> stringResource(R.string.play)
                                },
                                tint = Color.White,
                                modifier = Modifier.size(54.dp)
                            )
                        }
                    }

                    // Next Video
                    if (!isInitialLoading) {
                        val nextInteractionSource = remember { MutableInteractionSource() }
                        IconButton(
                            onClick = onNext,
                            enabled = hasNext,
                            modifier = Modifier.size(48.dp).pressScale(nextInteractionSource, pressedScale = 0.82f),
                            interactionSource = nextInteractionSource
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SkipNext,
                                contentDescription = stringResource(R.string.next_video),
                                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }
            }

            // Bottom Bar
            if (!isInitialLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.44f))
                            )
                        )
                        .padding(start = 0.dp, end = 0.dp, top = 0.dp, bottom = fullscreenSeekbarBottomPadding)
                ) {
                // Duration and Chapter pills row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(1f)
                        .offset(y = bottomControlsSeekbarOverlap)
                        .padding(
                            start = bottomControlHorizontalPadding,
                            end = bottomControlHorizontalPadding,
                            top = if (isFullscreen) 4.dp else 0.dp,
                            bottom = 0.dp
                        ),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time Pill
                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { if (isLive) onLiveClick() else onToggleRemainingTime() }
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLive) {
                                Text(
                                    text = formatTime(displayedPosition),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = " / ",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = 2.dp)
                                )
                                val dotAlpha by rememberInfiniteTransition(label = "liveDot").animateFloat(
                                    initialValue = 1f,
                                    targetValue = 0.2f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(800, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "dotAlpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red.copy(alpha = dotAlpha))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.player_live_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.Red,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 1.sp
                                )
                            } else {
                            Text(
                                text = if (showRemainingTime) "-${formatTime((duration - displayedPosition).coerceAtLeast(0))}" else formatTime(displayedPosition),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Text(
                                text = " / ",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.padding(horizontal = 2.dp)
                            )
                            
                            // Total Duration
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                            }
                        }
                    }

                    // Chapter Display Pill
                    if (currentChapter != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        Surface(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onChapterClick() }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = currentChapter.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = chapterMaxWidth)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Rounded.ChevronRight,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    if (compactQualityLabel != null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.4f),
                            shape = CircleShape,
                            modifier = Modifier
                                .height(32.dp)
                                .widthIn(min = 32.dp)
                                .clip(CircleShape)
                                .clickable { onQualityClick() }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            ) {
                                Text(
                                    text = compactQualityLabel,
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable(onClick = onFullscreenClick),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                            contentDescription = stringResource(R.string.fullscreen),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                if (isLive && duration <= 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Red)
                    )
                } else {
                    val seekDuration = if (isLive) duration.coerceAtLeast(displayedPosition) else duration
                    SeekbarWithPreview(
                        value = if (seekDuration > 0) {
                            (displayedPosition.toFloat() / seekDuration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        onValueChange = { progress ->
                            val newPosition = (progress * seekDuration).toLong()
                            val playerManager = EnhancedPlayerManager.getInstance()

                            scrubPosition = newPosition

                            if (!isScrubbing) {
                                isScrubbing = true
                                playerManager.setScrubbingModeEnabled(true)
                            }

                            if (isLive) {
                                return@SeekbarWithPreview
                            }

                            pendingScrubSeekJob?.cancel()

                            val now = SystemClock.elapsedRealtime()
                            val remainingDelay = (LIVE_SCRUB_SEEK_INTERVAL_MS - (now - lastScrubSeekAt)).coerceAtLeast(0L)
                            val movedFarEnough = lastScrubSeekPosition == Long.MIN_VALUE ||
                                abs(newPosition - lastScrubSeekPosition) >= LIVE_SCRUB_IMMEDIATE_DELTA_MS

                            if (remainingDelay == 0L || movedFarEnough) {
                                onSeek(newPosition)
                                lastScrubSeekAt = now
                                lastScrubSeekPosition = newPosition
                            } else {
                                pendingScrubSeekJob = scrubScope.launch {
                                    delay(remainingDelay)
                                    val targetPosition = scrubPosition ?: return@launch
                                    onSeek(targetPosition)
                                    lastScrubSeekAt = SystemClock.elapsedRealtime()
                                    lastScrubSeekPosition = targetPosition
                                }
                            }
                        },
                        onValueChangeFinished = {
                            pendingScrubSeekJob?.cancel()
                            pendingScrubSeekJob = null
                            scrubPosition?.let { targetPosition ->
                                onSeek(targetPosition)
                                lastScrubSeekPosition = targetPosition
                            }
                            lastScrubSeekAt = 0L
                            lastScrubSeekPosition = Long.MIN_VALUE
                            isScrubbing = false
                            EnhancedPlayerManager.getInstance().setScrubbingModeEnabled(false)
                        },
                        seekbarPreviewHelper = seekbarPreviewHelper,
                        chapters = chapters,
                        sponsorSegments = sponsorSegments,
                        duration = seekDuration,
                        bufferedValue = bufferedPercentage,
                        edgeAligned = !isFullscreen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .zIndex(2f)
                            .padding(horizontal = seekbarHorizontalPadding)
                    )
                }
            }
        }
            }
        }
        }

        if (!isVisible && !isFullscreen && !isInitialLoading && !isTouchLocked) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
            ) {
                if (isLive && duration <= 0L) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Red)
                    )
                } else {
                    val seekDuration = if (isLive) duration.coerceAtLeast(displayedPosition) else duration
                    SeekbarWithPreview(
                        value = if (seekDuration > 0) {
                            (displayedPosition.toFloat() / seekDuration.toFloat()).coerceIn(0f, 1f)
                        } else {
                            0f
                        },
                        onValueChange = { progress ->
                            val newPosition = (progress * seekDuration).toLong()
                            val playerManager = EnhancedPlayerManager.getInstance()

                            scrubPosition = newPosition

                            if (!isScrubbing) {
                                isScrubbing = true
                                playerManager.setScrubbingModeEnabled(true)
                            }

                            if (isLive) {
                                return@SeekbarWithPreview
                            }

                            pendingScrubSeekJob?.cancel()

                            val now = SystemClock.elapsedRealtime()
                            val remainingDelay = (LIVE_SCRUB_SEEK_INTERVAL_MS - (now - lastScrubSeekAt)).coerceAtLeast(0L)
                            val movedFarEnough = lastScrubSeekPosition == Long.MIN_VALUE ||
                                abs(newPosition - lastScrubSeekPosition) >= LIVE_SCRUB_IMMEDIATE_DELTA_MS

                            if (remainingDelay == 0L || movedFarEnough) {
                                onSeek(newPosition)
                                lastScrubSeekAt = now
                                lastScrubSeekPosition = newPosition
                            } else {
                                pendingScrubSeekJob = scrubScope.launch {
                                    delay(remainingDelay)
                                    val targetPosition = scrubPosition ?: return@launch
                                    onSeek(targetPosition)
                                    lastScrubSeekAt = SystemClock.elapsedRealtime()
                                    lastScrubSeekPosition = targetPosition
                                }
                            }
                        },
                        onValueChangeFinished = {
                            pendingScrubSeekJob?.cancel()
                            pendingScrubSeekJob = null
                            scrubPosition?.let { targetPosition ->
                                onSeek(targetPosition)
                                lastScrubSeekPosition = targetPosition
                            }
                            lastScrubSeekAt = 0L
                            lastScrubSeekPosition = Long.MIN_VALUE
                            isScrubbing = false
                            EnhancedPlayerManager.getInstance().setScrubbingModeEnabled(false)
                        },
                        seekbarPreviewHelper = seekbarPreviewHelper,
                        chapters = chapters,
                        sponsorSegments = sponsorSegments,
                        duration = seekDuration,
                        bufferedValue = bufferedPercentage,
                        edgeAligned = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun SleekLoadingAnimation(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        val strokeWidth = 4.dp.toPx()
        
        // Draw background track
        drawArc(
            color = Color.White.copy(alpha = 0.2f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        
        // Draw animated arc
        rotate(rotation) {
            drawArc(
                brush = Brush.sweepGradient(
                    0.0f to Color.Transparent,
                    0.5f to primaryColor,
                    1.0f to primaryColor
                ),
                startAngle = 0f,
                sweepAngle = 280f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

private fun String.toCompactQualityLabel(): String {
    val height = Regex("""\d+""").find(this)?.value?.toIntOrNull()
        ?.let(QualityManager::normalizeQualityHeight)
    return when (height) {
        2160 -> "4K"
        1440 -> "QHD"
        1080 -> "FHD"
        720 -> "HD"
        480 -> "SD"
        360 -> "360p"
        240 -> "240p"
        144 -> "144p"
        null -> this
        else -> "${height}p"
    }
}

private fun Float.toSpeedIndicatorLabel(): String {
    val speed = coerceIn(0.1f, 10.0f)
    return if (kotlin.math.abs(speed - speed.toInt()) < 0.01f) {
        "${speed.toInt()}x"
    } else {
        val rounded = kotlin.math.round(speed * 100f) / 100f
        "${rounded.toString().trimEnd('0').trimEnd('.')}x"
    }
}

private fun formatTime(timeMs: Long): String {
    val totalSeconds = timeMs / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
