package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockSegment
import kotlinx.coroutines.delay

@Composable
fun SeekAnimationOverlay(
    showSeekBack: Boolean,
    showSeekForward: Boolean,
    seekSeconds: Int = 10,
    modifier: Modifier = Modifier
) {
    // Force LTR so CenterStart/CenterEnd always map to physical left/right,
    // regardless of the device's system language direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = showSeekBack,
                enter = fadeIn(tween(150)),
                // Exit instantly when switching to forward (no overlap), otherwise fade normally.
                exit = fadeOut(tween(if (showSeekForward) 0 else 400)),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp)
            ) {
                SeekChevronLabel(forward = false, seconds = seekSeconds)
            }

            AnimatedVisibility(
                visible = showSeekForward,
                enter = fadeIn(tween(150)),
                // Exit instantly when switching to backward (no overlap), otherwise fade normally.
                exit = fadeOut(tween(if (showSeekBack) 0 else 400)),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp)
            ) {
                SeekChevronLabel(forward = true, seconds = seekSeconds)
            }
        }
    }
}

@Composable
private fun SeekChevronLabel(forward: Boolean, seconds: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "chevron")
    
    val progress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "chevronProgress"
    )

    val offsetProgress = LinearOutSlowInEasing.transform(progress)
    val chevronOffset = if (forward) 24f * offsetProgress else -24f * offsetProgress
    
    val chevronAlpha = when {
        progress < 0.2f -> progress * 5f
        progress > 0.5f -> (1f - progress) * 2f
        else -> 1f
    }.coerceIn(0f, 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!forward) {
            Text(
                text = "<",
                color = Color.White.copy(alpha = chevronAlpha),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = chevronOffset.dp)
            )
        }
        Text(
            text = if (forward) "+${seconds}s" else "-${seconds}s",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        if (forward) {
            Text(
                text = ">",
                color = Color.White.copy(alpha = chevronAlpha),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(x = chevronOffset.dp)
            )
        }
    }
}

@Composable
fun BrightnessOverlay(
    isVisible: Boolean,
    brightnessLevel: Float,
    modifier: Modifier = Modifier
) {
    val isAuto = brightnessLevel < 0f

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 2 },
        exit = fadeOut(tween(500)) + slideOutHorizontally(tween(500)) { it / 2 },
        modifier = modifier
    ) {
        val animatedBrightness by animateFloatAsState(
            targetValue = if (isAuto) 0f else brightnessLevel,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "brightness"
        )
        
        Surface(
            modifier = Modifier
                .width(46.dp)
                .height(220.dp),
            color = Color.Black.copy(alpha = 0.4f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Progress Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedBrightness)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.8f),
                                    Color.White.copy(alpha = 0.4f)
                                )
                            )
                        )
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    val iconVector = if (isAuto) {
                        Icons.Rounded.BrightnessMedium // Safe fallback, text says "Auto"
                    } else if (brightnessLevel > 0.7f) Icons.Rounded.BrightnessHigh 
                    else if (brightnessLevel > 0.3f) Icons.Rounded.BrightnessMedium
                    else Icons.Rounded.BrightnessLow

                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = if (animatedBrightness > 0.8f) Color.Black.copy(alpha = 0.7f) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    
                    Text(
                        text = if (isAuto) "Auto" else "${(brightnessLevel * 100).toInt()}",
                        color = if (animatedBrightness > 0.1f) Color.Black.copy(alpha = 0.7f) else Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun VolumeOverlay(
    isVisible: Boolean,
    volumeLevel: Float,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 2 },
        exit = fadeOut(tween(500)) + slideOutHorizontally(tween(500)) { -it / 2 },
        modifier = modifier
    ) {
        val animatedVolume by animateFloatAsState(
            targetValue = volumeLevel,
            animationSpec = spring(stiffness = Spring.StiffnessLow),
            label = "volume"
        )
        
        Surface(
            modifier = Modifier
                .width(46.dp)
                .height(220.dp),
            color = Color.Black.copy(alpha = 0.4f),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Progress Fill
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(animatedVolume / 2f) 
                        .background(
                            brush = Brush.verticalGradient(
                                colors = if (volumeLevel > 1f) {
                                    listOf(
                                        Color(0xFFFF5252),
                                        MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    )
                                }
                            )
                        )
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        imageVector = if (volumeLevel > 1.0f) Icons.Rounded.VolumeUp
                                     else if (volumeLevel > 0.6f) Icons.Rounded.VolumeUp 
                                     else if (volumeLevel > 0.1f) Icons.Rounded.VolumeDown
                                     else Icons.Rounded.VolumeMute,
                        contentDescription = null,
                        tint = if (animatedVolume > 1.6f) Color.Black.copy(alpha = 0.7f) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    
                    Text(
                        text = "${(volumeLevel * 100).toInt()}%",
                        color = if (animatedVolume > 0.2f) Color.Black.copy(alpha = 0.7f) else Color.White,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SpeedBoostOverlay(
    isVisible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        modifier = modifier
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
            modifier = Modifier.wrapContentSize()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "2x",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Overlay button that lets the user manually skip a SponsorBlock segment.
 */
@Composable
fun SponsorBlockSkipButton(
    sponsorSegments: List<SponsorBlockSegment>,
    currentPositionMs: Long,
    categoryActions: Map<String, SponsorBlockAction>,
    onSkipClick: (endPositionMs: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeSegment = remember(sponsorSegments, currentPositionMs) {
        val posSec = currentPositionMs / 1000f
        sponsorSegments.find { seg ->
            posSec >= seg.startTime && posSec < seg.endTime &&
                (categoryActions[seg.category] ?: SponsorBlockAction.SKIP) != SponsorBlockAction.SKIP
        }
    }

    var displaySegment by remember { mutableStateOf<SponsorBlockSegment?>(null) }
    var buttonVisible by remember { mutableStateOf(false) }

    LaunchedEffect(activeSegment?.uuid) {
        if (activeSegment != null) {
            displaySegment = activeSegment
            buttonVisible = true
            val remainingMs = ((activeSegment.endTime * 1000f).toLong() - currentPositionMs).coerceAtLeast(0L)
            val showMs = minOf(remainingMs, 4_000L)
            delay(showMs)
            buttonVisible = false
        } else {
            buttonVisible = false
        }
    }

    AnimatedVisibility(
        visible = buttonVisible,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(tween(200)),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(tween(200)),
        modifier = modifier
    ) {
        val seg = displaySegment ?: return@AnimatedVisibility
        Surface(
            onClick = {
                onSkipClick((seg.endTime * 1000L).toLong())
                buttonVisible = false
            },
            color = Color(0xFF00D400),
            shape = RoundedCornerShape(50),
            shadowElevation = 4.dp,
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.sb_manual_skip),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
