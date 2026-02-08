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
import androidx.compose.material.icons.rounded.*
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
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.flow.youtube.data.model.Video
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.RepeatMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ShortVideoPlayer(
    video: Video,
    isVisible: Boolean,
    onBack: () -> Unit,
    onChannelClick: (String) -> Unit,
    onLikeClick: () -> Unit,
    onSubscribeClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onShareClick: () -> Unit,
    onDescriptionClick: () -> Unit,
    viewModel: ShortsViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerManager = remember { EnhancedPlayerManager.getInstance() }
    
    var isPlaying by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }
    var isLiked by remember { mutableStateOf(false) }
    var isSubscribed by remember { mutableStateOf(false) }
    var isSaved by remember { mutableStateOf(false) }
    var showLikeAnimation by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    var showDescription by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }
    
    // Dynamic Colors from Theme
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    
    // Load like, subscribe and saved states
    LaunchedEffect(video.id) {
        isLiked = viewModel.isVideoLiked(video.id)
        isSubscribed = viewModel.isChannelSubscribed(video.channelId)
        isSaved = viewModel.isShortSaved(video.id)
    }
    
    // Video player instance
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

    // Initialize player and handle playback when visibility changes
    LaunchedEffect(isVisible) {
        if (isVisible) {
            playerManager.initialize(context)
            EnhancedMusicPlayerManager.pause()
            
            playerView.player = playerManager.getPlayer()
            (playerView.videoSurfaceView as? android.view.SurfaceView)?.holder?.let { holder ->
                if (holder.surface?.isValid == true) {
                    playerManager.attachVideoSurface(holder)
                }
            }
            
            // Fetch streams for the short
            try {
                val streamInfo = viewModel.getVideoStreamInfo(video.id)
                if (streamInfo != null && isVisible) {
                    val videoStream = streamInfo.videoStreams?.firstOrNull { it.height >= 720 } 
                                     ?: streamInfo.videoStreams?.firstOrNull()
                                     ?: streamInfo.videoOnlyStreams?.firstOrNull()
                    
                    val audioStream = streamInfo.audioStreams?.maxByOrNull { it.averageBitrate }
                    
                    if (audioStream != null) {
                        playerManager.setStreams(
                            videoId = video.id,
                            videoStream = videoStream,
                            audioStream = audioStream,
                            videoStreams = (streamInfo.videoStreams ?: emptyList()) + (streamInfo.videoOnlyStreams ?: emptyList()),
                            audioStreams = streamInfo.audioStreams ?: emptyList(),
                            subtitles = streamInfo.subtitles ?: emptyList(),
                            durationSeconds = streamInfo.duration
                        )
                        if (isVisible) {
                            playerManager.play()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("ShortVideoPlayer", "Error loading short streams", e)
            }
            
            // Progress tracker
            while(true) {
                currentPosition = playerManager.getCurrentPosition()
                duration = playerManager.getDuration()
                isBuffering = playerManager.getPlayer()?.playbackState == androidx.media3.common.Player.STATE_BUFFERING
                
                // Sync isPlaying state with actual player state
                if (isVisible) {
                    val playerIsPlaying = playerManager.isPlaying()
                    if (isPlaying != playerIsPlaying) {
                        isPlaying = playerIsPlaying
                    }
                }
                
                // Ensure looping is enabled
                if (playerManager.getPlayer()?.repeatMode != androidx.media3.common.Player.REPEAT_MODE_ONE) {
                    playerManager.getPlayer()?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                }
                
                delay(100)
            }
        } else {
            if (playerManager.playerState.value.currentVideoId == video.id) {
                playerManager.pause()
            }
            playerView.player = null
        }
    }
    
    // Auto-hide controls after showing them
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(2500) // Show controls for 2.5 seconds
            showControls = false
        }
    }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        // Toggle play/pause on tap
                        if (isPlaying) {
                            playerManager.pause()
                        } else {
                            playerManager.play()
                        }
                        // Show controls briefly to indicate the action
                        showControls = true
                    },
                    onDoubleTap = {
                        if (!isLiked) {
                            onLikeClick()
                            isLiked = true
                            showLikeAnimation = true
                        }
                    },
                    onPress = {
                        try {
                            awaitRelease()
                        } finally {
                            if (isFastForwarding) {
                                isFastForwarding = false
                                playerManager.setPlaybackSpeed(1.0f)
                            }
                        }
                    },
                    onLongPress = {
                        isFastForwarding = true
                        playerManager.setPlaybackSpeed(2.0f)
                    }
                )
            }
    ) {
        DisposableEffect(playerView, isVisible) {
            val surfaceView = playerView.videoSurfaceView as? android.view.SurfaceView
            val callback = object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    if (isVisible) {
                        playerManager.attachVideoSurface(holder)
                    }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {}
            }
            surfaceView?.holder?.addCallback(callback)
            onDispose {
                surfaceView?.holder?.removeCallback(callback)
                if (isVisible) playerView.player = null
            }
        }
        
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )

        // 2x Speed Indicator
        if (isFastForwarding) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.speed_2x),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = primaryColor
            )
        }
        
        // Like Animation
        AnimatedVisibility(
            visible = showLikeAnimation,
            enter = scaleIn(initialScale = 0.5f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.cd_liked),
                tint = Color.Red,
                modifier = Modifier.size(120.dp)
            )
            LaunchedEffect(Unit) {
                delay(800)
                showLikeAnimation = false
            }
        }
        
        // Overlays
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .align(Alignment.TopCenter)
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
        )
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
        )
        
        // Top Controls
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .statusBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.btn_back), tint = Color.White)
                }
                IconButton(onClick = { /* Search */ }) {
                    Icon(Icons.Default.Search, androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.action_search), tint = Color.White)
                }
            }
        }
        
        // Bottom Info & Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(bottom = 16.dp, start = 16.dp, end = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Left Side: Info
            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                // Channel Info
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onChannelClick(video.channelId) }) {
                    AsyncImage(
                        model = video.channelThumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.Gray),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    if (!isSubscribed) {
                        Button(
                            onClick = { onSubscribeClick(); isSubscribed = true },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = onPrimaryColor),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.action_subscribe), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Description
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = if (showDescription) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable { showDescription = !showDescription }
                )
                
                if (showDescription) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "${formatViewCount(video.viewCount)} views • ${video.uploadDate}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Music/Sound Info
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.MusicNote, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.shorts_original_sound, video.channelName),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1
                    )
                }
            }
            
            // Right Side: Actions
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                ActionButton(
                    icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.action_like),
                    tint = if (isLiked) Color.Red else Color.White,
                    onClick = { onLikeClick(); isLiked = !isLiked }
                )
                ActionButton(icon = Icons.Default.ThumbDown, text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.action_dislike), onClick = {})
                ActionButton(icon = Icons.Default.Comment, text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.action_comments), onClick = onCommentsClick)
                
                // Save Button
                ActionButton(
                    icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.action_save),
                    tint = if (isSaved) primaryColor else Color.White,
                    onClick = { 
                        isSaved = !isSaved
                        viewModel.toggleSaveShort(video)
                    }
                )
                
                ActionButton(icon = Icons.Default.Share, text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.action_share), onClick = onShareClick)
                ActionButton(icon = Icons.Default.Description, text = androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.action_description), onClick = onDescriptionClick)
                
                // Album Art / Sound Spinner
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.DarkGray, CircleShape)
                        .padding(6.dp)
                ) {
                    AsyncImage(
                        model = video.channelThumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
            }
        }
        
        // Progress Bar
        if (duration > 0) {
            LinearProgressIndicator(
                progress = (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).height(2.dp),
                color = primaryColor,
                trackColor = Color.White.copy(alpha = 0.3f),
            )
        }
        
        // Center play/pause button
        AnimatedVisibility(
            visible = showControls && !isBuffering,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IconButton(
                onClick = {
                    // Keep controls visible when button is tapped
                    showControls = true
                },
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.cd_pause) else androidx.compose.ui.res.stringResource(com.flow.youtube.R.string.cd_play),
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
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier.clickable(onClick = onClick)) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = tint,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                shadow = androidx.compose.ui.graphics.Shadow(color = Color.Black, blurRadius = 4f)
            ),
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
