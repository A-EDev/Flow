package com.flow.youtube.ui.components

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.GlobalPlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Persistent Video Mini Player that appears above the bottom navigation bar.
 * Similar to YouTube's mini player - shows video thumbnail, title, and controls.
 * Can be dragged to dismiss or tapped to expand to full player.
 */
@OptIn(UnstableApi::class)
@Composable
fun PersistentVideoMiniPlayer(
    video: Video,
    onExpandClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    // Animation for dismiss
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }
    val dismissThreshold = with(density) { 100.dp.toPx() }
    
    // Position tracking
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    // Update position continuously
    LaunchedEffect(playerState.isPlaying) {
        while (true) {
            currentPosition = EnhancedPlayerManager.getInstance().getCurrentPosition()
            duration = EnhancedPlayerManager.getInstance().getDuration()
            delay(200)
        }
    }
    
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    // Animate offset
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDismissing) dismissThreshold * 2 else offsetY,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        finishedListener = {
            if (isDismissing) {
                onDismiss()
            }
        },
        label = "dismissAnimation"
    )
    
    // Dismiss when dragged down enough
    LaunchedEffect(offsetY) {
        if (offsetY > dismissThreshold && !isDismissing) {
            isDismissing = true
            EnhancedPlayerManager.getInstance().stop()
            GlobalPlayerState.hideMiniPlayer()
        }
    }

    AnimatedVisibility(
        visible = !isDismissing,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .offset { IntOffset(0, animatedOffset.roundToInt()) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            if (offsetY < dismissThreshold) {
                                // Snap back
                                offsetY = 0f
                            }
                        },
                        onDragCancel = {
                            offsetY = 0f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            // Only allow dragging down
                            if (dragAmount > 0 || offsetY > 0) {
                                offsetY = (offsetY + dragAmount).coerceAtLeast(0f)
                            }
                        }
                    )
                }
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(onClick = onExpandClick)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Video thumbnail with live playback
                    Box(
                        modifier = Modifier
                            .size(width = 100.dp, height = 56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                    ) {
                        // Try to show actual video surface
                        val player = EnhancedPlayerManager.getInstance().getPlayer()
                        if (player != null && playerState.currentVideoId == video.id) {
                            AndroidView(
                                factory = { ctx ->
                                    PlayerView(ctx).apply {
                                        this.player = player
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
                                contentScale = ContentScale.Crop
                            )
                        }
                        
                        // Progress overlay at bottom
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
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    // Video info
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Play/Pause button
                    IconButton(
                        onClick = {
                            if (playerState.isPlaying) {
                                EnhancedPlayerManager.getInstance().pause()
                            } else {
                                EnhancedPlayerManager.getInstance().play()
                            }
                        }
                    ) {
                        if (playerState.isBuffering) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    
                    // Close button
                    IconButton(
                        onClick = {
                            isDismissing = true
                            EnhancedPlayerManager.getInstance().stop()
                            GlobalPlayerState.hideMiniPlayer()
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Floating draggable mini player that can be positioned anywhere on screen.
 * This is the alternate "corner" style mini player like YouTube PiP within app.
 */
@OptIn(UnstableApi::class)
@Composable
fun FloatingVideoMiniPlayer(
    video: Video,
    onExpandClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    
    // Screen dimensions
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    
    // Mini player dimensions
    val playerWidth = 180.dp
    val playerHeight = 101.dp // 16:9 aspect ratio
    val playerWidthPx = with(density) { playerWidth.toPx() }
    val playerHeightPx = with(density) { playerHeight.toPx() }
    
    // Position state (bottom right corner by default)
    var offsetX by remember { mutableFloatStateOf(screenWidth - playerWidthPx - 16f) }
    var offsetY by remember { mutableFloatStateOf(screenHeight - playerHeightPx - 180f) } // Above bottom nav
    
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    
    // Update position continuously
    LaunchedEffect(playerState.isPlaying) {
        while (true) {
            currentPosition = EnhancedPlayerManager.getInstance().getCurrentPosition()
            duration = EnhancedPlayerManager.getInstance().getDuration()
            delay(200)
        }
    }
    
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    Box(
        modifier = modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .width(playerWidth)
            .height(playerHeight)
            .zIndex(Float.MAX_VALUE)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    offsetX = (offsetX + dragAmount.x).coerceIn(0f, screenWidth - playerWidthPx)
                    offsetY = (offsetY + dragAmount.y).coerceIn(0f, screenHeight - playerHeightPx - 80f)
                }
            }
            .clickable(onClick = onExpandClick)
    ) {
        // Video surface
        val player = EnhancedPlayerManager.getInstance().getPlayer()
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        useController = false
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        
        // Controls overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.4f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.5f)
                        )
                    )
                )
                .padding(6.dp)
        ) {
            // Close button (top right)
            IconButton(
                onClick = {
                    EnhancedPlayerManager.getInstance().stop()
                    GlobalPlayerState.hideMiniPlayer()
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
            
            // Play/pause (center)
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
                    .size(36.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                if (playerState.isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            
            // Progress bar (bottom)
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
