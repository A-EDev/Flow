package com.flow.youtube.ui.screens.player

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
import com.flow.youtube.ui.screens.player.components.SeekbarWithPreview
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.seekbarpreview.SeekbarPreviewThumbnailHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import com.flow.youtube.player.CastHelper
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
                .background(Color.Black.copy(alpha = 0.6f))
        ) {
            // Top Bar
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
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(40.dp)
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
                            color = Color.White.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.clickable { onSettingsClick() }
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
                    if (isPipSupported) {
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

                    // CC Icon
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

                    // Autoplay Toggle Icon
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
                    IconButton(
                        onClick = onPrevious,
                        enabled = hasPrevious,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SkipPrevious,
                            contentDescription = stringResource(R.string.previous_video),
                            tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    // Play/Pause
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(color = Color.White)
                            ) { onPlayPause() }
                    ) {
                        if (isBuffering) {
                            SleekLoadingAnimation(modifier = Modifier.size(48.dp))
                        } else {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    // Next Video
                    IconButton(
                        onClick = onNext,
                        enabled = hasNext,
                        modifier = Modifier.size(48.dp)
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

            // Bottom Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                // Time and Chapter (Unified Pill Shape)
                Surface(
                    color = Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .wrapContentWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Current Position
                        Text(
                            text = formatTime(currentPosition),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        
                        // Separator
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
                        
                        // Chapter Display (If available)
                        if (currentChapter != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(start = 12.dp)
                                    .clickable { onChapterClick() }
                            ) {
                                // Tiny dot separator
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.4f))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                
                                Icon(
                                    imageVector = Icons.Rounded.Bookmark,
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
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
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Seekbar and Screen Size Controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.weight(1f)) {
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

                    // Fullscreen Button
                    IconButton(
                        onClick = onFullscreenClick,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                            contentDescription = stringResource(R.string.fullscreen),
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
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
