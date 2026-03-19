package io.github.aedev.flow.ui.screens.player

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.ui.screens.player.components.SeekbarWithPreview
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.player.CastHelper
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.components.pressScale
import org.schabi.newpipe.extractor.stream.StreamSegment

@Composable
fun PremiumControlsOverlay(
    isVisible: Boolean,
    isPlaying: Boolean,
    isBuffering: Boolean,
    currentPosition: Long,
    duration: Long,
    qualityLabel: String?,
    resizeMode: Int,
    onResizeClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onBack: () -> Unit,
    onSettingsClick: () -> Unit,
    onQualityClick: () -> Unit = {},
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
    onSleepTimerClick: () -> Unit = {},
    isSleepTimerActive: Boolean = false,
    showRemainingTime: Boolean = false,
    onToggleRemainingTime: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val resizeModes = listOf(
        stringResource(R.string.resize_fit),
        stringResource(R.string.resize_fill),
        stringResource(R.string.resize_zoom)
    )
    
    // Find current chapter
    val currentChapter = remember(currentPosition, chapters) {
        val positionSeconds = currentPosition / 1000
        chapters.lastOrNull { it.startTimeSeconds <= positionSeconds }
    }
    
    val sponsorSegments by EnhancedPlayerManager.getInstance().sponsorSegments.collectAsState()

    val context = LocalContext.current
    val playerPreferences = remember { PlayerPreferences(context) }
    val overlayCastEnabled by playerPreferences.overlayCastEnabled.collectAsState(initial = true)
    val overlayCcEnabled by playerPreferences.overlayCcEnabled.collectAsState(initial = false)
    val overlayPipEnabled by playerPreferences.overlayPipEnabled.collectAsState(initial = false)
    val overlayAutoplayEnabled by playerPreferences.overlayAutoplayEnabled.collectAsState(initial = false)
    val overlaySleepTimerEnabled by playerPreferences.overlaySleepTimerEnabled.collectAsState(initial = true)

    val isInitialLoading = isBuffering && duration <= 0L && currentPosition <= 0L

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(animationSpec = tween(300)),
        exit = fadeOut(animationSpec = tween(300)),
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(windowInsets)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (isInitialLoading) Color.Black else Color.Black.copy(alpha = 0.6f))
        ) {
            // Top Bar
            if (!isInitialLoading) {
                Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopStart)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
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
                    
                    // Quality Label Pill
                    if (qualityLabel != null) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .clickable { onQualityClick() }
                        ) {
                            Text(
                                text = if (qualityLabel.all { it.isDigit() }) "${qualityLabel}p" else qualityLabel,
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
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
                            onClick = { onAutoplayToggle(!autoplayEnabled) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SlowMotionVideo,
                                contentDescription = stringResource(R.string.autoplay),
                                tint = if (autoplayEnabled) primaryColor else Color.White.copy(alpha = 0.7f),
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
                                imageVector = Icons.Outlined.Bedtime,
                                contentDescription = stringResource(R.string.sleep_timer),
                                tint = if (isSleepTimerActive) primaryColor else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    // Settings Icon
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
                        if (isBuffering || isInitialLoading) {
                            SleekLoadingAnimation(modifier = Modifier.size(48.dp))
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
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
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                        .padding(horizontal = 14.dp, vertical = 0.dp)
                ) {
                // Duration and Chapter pills row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 0.dp),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Time Pill
                    Surface(
                        color = Color.Black.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onToggleRemainingTime() }
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isLive) {
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
                                text = if (showRemainingTime) "-${formatTime((duration - currentPosition).coerceAtLeast(0))}" else formatTime(currentPosition),
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
                            color = Color.Black.copy(alpha = 0.3f),
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
                                    modifier = Modifier.widthIn(max = 160.dp)
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
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.3f))
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

                if (isLive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.Red)
                    )
                } else {
                    SeekbarWithPreview(
                        value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
                        onValueChange = { progress ->
                            val newPosition = (progress * duration).toLong()
                            onSeek(newPosition)
                        },
                        seekbarPreviewHelper = seekbarPreviewHelper,
                        chapters = chapters,
                        sponsorSegments = sponsorSegments,
                        duration = duration,
                        bufferedValue = bufferedPercentage,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
        val size = size.minDimension - strokeWidth
        
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
