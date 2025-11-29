package com.flow.youtube.ui.screens.shorts

import android.util.Log
import android.view.SurfaceHolder
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedPlayerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@Composable
fun ShortVideoPlayer(
    video: Video,
    isVisible: Boolean,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    onLikeClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    viewModel: ShortsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerManager = remember { EnhancedPlayerManager.getInstance() }
    
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(false) }
    var showLikeAnimation by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    
    // Load like and subscribe states
    LaunchedEffect(video.id) {
        isLiked = viewModel.isVideoLiked(video.id)
        isSubscribed = viewModel.isChannelSubscribed(video.channelId)
    }
    
    // Initialize player when visible
    LaunchedEffect(isVisible) {
        if (isVisible) {
            playerManager.initialize(context)
            // Note: Actual video loading will be handled by the EnhancedVideoPlayerScreen integration
            // For now, we'll use the player manager's existing functionality
            delay(300) // Small delay for surface attachment
        } else {
            // Pause when not visible
            playerManager.pause()
        }
    }
    
    // Observe player state
    val playerState by playerManager.playerState.collectAsState()
    
    LaunchedEffect(playerState) {
        isPlaying = playerState.isPlaying
        isBuffering = playerState.isBuffering
        // Handle position and duration from player state
    }
    
    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(3000)
            showControls = false
        }
    }
    
    // Handle like animation
    LaunchedEffect(showLikeAnimation) {
        if (showLikeAnimation) {
            delay(800)
            showLikeAnimation = false
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = {
                        // Double tap to like
                        if (!isLiked) {
                            onLikeClick()
                            isLiked = true
                            showLikeAnimation = true
                        }
                    }
                )
            }
    ) {
        // Video player
        val playerView = remember(video.id) {
            PlayerView(context).apply {
                useController = false
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            }
        }
        
        DisposableEffect(playerView) {
            val surfaceView = playerView.videoSurfaceView as? android.view.SurfaceView
            val callback = surfaceView?.let {
                object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        Log.d("ShortVideoPlayer", "Surface created for ${video.id}")
                        playerManager.attachVideoSurface(holder)
                    }
                    
                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                    
                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        Log.d("ShortVideoPlayer", "Surface destroyed for ${video.id}")
                    }
                }
            }
            
            callback?.let { surfaceView.holder.addCallback(it) }
            playerView.player = playerManager.getPlayer()
            
            onDispose {
                callback?.let { surfaceView?.holder?.removeCallback(it) }
            }
        }
        
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )
        
        // Buffering indicator
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
        
        // Double-tap like animation
        androidx.compose.animation.AnimatedVisibility(
            visible = showLikeAnimation,
            enter = scaleIn(
                initialScale = 0.5f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Liked",
                tint = Color.Red,
                modifier = Modifier.size(120.dp)
            )
        }
        
        // Top gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.6f),
                            Color.Transparent
                        )
                    )
                )
        )
        
        // Bottom gradient overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.8f)
                        )
                    )
                )
        )
        
        // Top controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = "Shorts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                
                IconButton(
                    onClick = { /* Search */ },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White
                    )
                }
            }
        }
        
        // Bottom info and controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                // Video info
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 16.dp)
                ) {
                    // Channel info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onChannelClick(video.channelId) }
                            .padding(vertical = 8.dp)
                    ) {
                        // Channel avatar
                        AsyncImage(
                            model = video.channelThumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.Gray),
                            contentScale = ContentScale.Crop
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Subscribe button
                        if (!isSubscribed) {
                            Button(
                                onClick = {
                                    onSubscribeClick()
                                    isSubscribed = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Red
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = "Subscribe",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    
                    // Video title
                    Text(
                        text = video.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        maxLines = if (showDescription) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .clickable { showDescription = !showDescription }
                            .padding(bottom = 8.dp)
                    )
                    
                    // View count
                    Text(
                        text = "${formatViewCount(video.viewCount)} views",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                // Action buttons
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // Like button
                    ActionButton(
                        icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        text = "Like",
                        tint = if (isLiked) Color.Red else Color.White,
                        onClick = {
                            onLikeClick()
                            isLiked = !isLiked
                        }
                    )
                    
                    // Dislike button
                    ActionButton(
                        icon = Icons.Default.ThumbDown,
                        text = "Dislike",
                        onClick = { /* Handle dislike */ }
                    )
                    
                    // Comment button
                    ActionButton(
                        icon = Icons.Default.Comment,
                        text = "Comment",
                        onClick = { /* Handle comment */ }
                    )
                    
                    // Share button
                    ActionButton(
                        icon = Icons.Default.Share,
                        text = "Share",
                        onClick = { /* Handle share */ }
                    )
                    
                    // More button
                    ActionButton(
                        icon = Icons.Default.MoreVert,
                        text = "More",
                        onClick = { /* Handle more */ }
                    )
                }
            }
            
            // Progress indicator
            if (duration > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                
                val progress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                
                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
        
        // Center play/pause button (visible when controls shown and video is paused)
        AnimatedVisibility(
            visible = showControls && !isPlaying && !isBuffering,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconButton(
                onClick = {
                    if (isPlaying) {
                        playerManager.pause()
                    } else {
                        playerManager.play()
                    }
                },
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
            }
        }
    }
}

@Composable
fun ActionButton(
    icon: ImageVector,
    text: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessHigh
        )
    )
    
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .scale(scale)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                isPressed = true
                onClick()
                // Note: Animation will complete naturally via animateFloatAsState
                isPressed = false
            }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = tint,
                modifier = Modifier.size(28.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
