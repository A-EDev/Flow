package com.flow.youtube.ui.screens.player.components

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SeekAnimationOverlay(
    showSeekBack: Boolean,
    showSeekForward: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = showSeekBack,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 60.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.FastRewind,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "10s",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        
        AnimatedVisibility(
            visible = showSeekForward,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 60.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.FastForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Text(
                    text = "10s",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
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
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(200)) { -it / 2 },
        exit = fadeOut(tween(500)) + slideOutHorizontally(tween(500)) { -it / 2 },
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
        enter = fadeIn(tween(200)) + slideInHorizontally(tween(200)) { it / 2 },
        exit = fadeOut(tween(500)) + slideOutHorizontally(tween(500)) { it / 2 },
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
                        .fillMaxHeight(animatedVolume)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
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
                    Icon(
                        imageVector = if (volumeLevel > 0.6f) Icons.Rounded.VolumeUp 
                                     else if (volumeLevel > 0.1f) Icons.Rounded.VolumeDown
                                     else Icons.Rounded.VolumeMute,
                        contentDescription = null,
                        tint = if (animatedVolume > 0.8f) Color.Black.copy(alpha = 0.7f) else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    
                    Text(
                        text = "${(volumeLevel * 100).toInt()}",
                        color = if (animatedVolume > 0.1f) Color.Black.copy(alpha = 0.7f) else Color.White,
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
