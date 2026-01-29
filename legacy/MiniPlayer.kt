package com.flow.youtube.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.ui.theme.extendedColors
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun EnhancedMiniPlayer(
    video: Video,
    onExpandClick: () -> Unit,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    
    // Screen dimensions
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Mini player dimensions
    val playerWidth = 200.dp
    val playerHeight = 112.dp // 16:9 aspect ratio approx
    val playerWidthPx = with(density) { playerWidth.toPx() }
    val playerHeightPx = with(density) { playerHeight.toPx() }
    
    // Initial position (Bottom Right, with some padding)
    // We use a persistent offset state
    var offsetX by remember { mutableFloatStateOf(screenWidth - playerWidthPx - 32f) }
    var offsetY by remember { mutableFloatStateOf(screenHeight - playerHeightPx - 200f) } // Above bottom nav
    
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    // Update position continuously
    LaunchedEffect(playerState.isPlaying) {
        while (true) {
            currentPosition = EnhancedPlayerManager.getInstance().getCurrentPosition()
            duration = EnhancedPlayerManager.getInstance().getDuration()
            delay(100)
        }
    }
    
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    val progressPercent = (progress * 100).toInt()

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(playerWidth)
            .height(playerHeight)
            .zIndex(1000f)
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(12.dp)
            )
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidth - playerWidthPx)
                    offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeight - playerHeightPx)
                }
            }
            .clickable(onClick = onExpandClick)
    ) {
        // Video Surface
        if (EnhancedPlayerManager.getInstance().getPlayer() != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = EnhancedPlayerManager.getInstance().getPlayer()
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                        controllerShowTimeoutMs = 0
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop
            )
        }
        
        // Overlay Gradient
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.3f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.6f)
                        )
                    )
                )
        )

        // Controls Overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // Close Button (Top Right)
            IconButton(
                onClick = {
                    EnhancedPlayerManager.getInstance().stop()
                    EnhancedPlayerManager.getInstance().release()
                    onCloseClick()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            // Play/Pause (Center)
            IconButton(
                onClick = {
                    if (playerState.isPlaying) {
                        EnhancedPlayerManager.getInstance().pause()
                    } else {
                        EnhancedPlayerManager.getInstance().play()
                    }
                },
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape)
            ) {
                if (playerState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Progress Percentage (Bottom Right)
            Text(
                text = "$progressPercent%",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
            )
            
            // Progress Bar (Bottom Edge)
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .align(Alignment.BottomCenter),
                color = Color.Red,
                trackColor = Color.White.copy(alpha = 0.3f)
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
        String.format("%d:%02d", minutes, seconds)
    }
}
