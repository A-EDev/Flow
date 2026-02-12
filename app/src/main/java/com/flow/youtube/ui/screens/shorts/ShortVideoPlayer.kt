package com.flow.youtube.ui.screens.shorts

import android.util.Log
import android.view.SurfaceHolder
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.flow.youtube.R
import com.flow.youtube.data.model.Video
import com.flow.youtube.data.model.toShortVideo
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.player.shorts.ShortsPlayerPool
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ShortVideoPage(
    video: Video,
    isActive: Boolean,
    pageIndex: Int,
    viewModel: ShortsViewModel,
    onBack: () -> Unit,
    onChannelClick: () -> Unit,
    onCommentsClick: () -> Unit,
    onDescriptionClick: () -> Unit,
    onShareClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val playerPool = remember { ShortsPlayerPool.getInstance() }

    // Dynamic colors
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    // ── State from ViewModel (single source of truth) ──
    val isLikedState = remember(video.id) { viewModel.isVideoLikedState(video.id) }
    val isLiked by isLikedState.collectAsState()

    val isSubscribedState = remember(video.channelId) { viewModel.isChannelSubscribedState(video.channelId) }
    val isSubscribed by isSubscribedState.collectAsState()

    val isSavedState = remember(video.id) { viewModel.isShortSavedState(video.id) }
    val isSaved by isSavedState.collectAsState()

    // ── Local UI-only state ──
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var isBuffering by remember { mutableStateOf(false) }
    var showPauseIndicator by remember { mutableStateOf(false) }
    var showLikeAnimation by remember { mutableStateOf(false) }
    var isFastForwarding by remember { mutableStateOf(false) }
    var hasStartedPlaying by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableStateOf(0f) }

    // ── PlayerView instance ──
    val playerView = remember(video.id) {
        PlayerView(context).apply {
            useController = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            keepScreenOn = true
        }
    }

    // ── Initialize player pool and handle playback when visibility changes ──
    LaunchedEffect(isActive, video.id) {
        if (isActive) {
            playerPool.initialize(context)
            EnhancedMusicPlayerManager.pause()

            val player = playerPool.getPlayerForIndex(pageIndex)
            playerView.player = player

            if (player != null && player.isPlaying) {
                hasStartedPlaying = true
            }
        } else {
            playerView.player = null
        }
    }

    // ── Efficient progress tracker: 250ms interval, only while active ──
    LaunchedEffect(isActive) {
        if (isActive) {
            while (true) {
                val p = playerPool.getPlayerForIndex(pageIndex)
                if (p != null) {
                    currentPosition = p.currentPosition
                    duration = p.duration.coerceAtLeast(0)
                    isBuffering = p.playbackState == androidx.media3.common.Player.STATE_BUFFERING
                    
                    val playerIsPlaying = p.isPlaying
                    if (isPlaying != playerIsPlaying) {
                        isPlaying = playerIsPlaying
                    }
                    
                    if (playerIsPlaying && !hasStartedPlaying) {
                        hasStartedPlaying = true
                    }
                }
                delay(250)
            }
        }
    }

    // ── Pause indicator auto-hide ──
    LaunchedEffect(showPauseIndicator) {
        if (showPauseIndicator) {
            delay(600)
            showPauseIndicator = false
        }
    }

    // ── Main Layout ──
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        playerPool.togglePlayPause()
                        showPauseIndicator = true
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    onDoubleTap = {
                        if (!isLiked) {
                            scope.launch { viewModel.toggleLike(video.toShortVideo()) }
                            showLikeAnimation = true
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    },
                    onPress = {
                        try {
                            awaitRelease()
                        } finally {
                            if (isFastForwarding) {
                                isFastForwarding = false
                                playerPool.resetPlaybackSpeed()
                            }
                        }
                    },
                    onLongPress = {
                        isFastForwarding = true
                        playerPool.setPlaybackSpeed(2.0f)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
    ) {
        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )

        // ── Thumbnail placeholder until video starts ──
        AnimatedVisibility(
            visible = !hasStartedPlaying && !isBuffering,
            enter = fadeIn(),
            exit = fadeOut(animationSpec = tween(300))
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        // ── 2x Speed Indicator ──
        AnimatedVisibility(
            visible = isFastForwarding,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
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
                        text = stringResource(R.string.speed_2x),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // ── Buffering Indicator ──
        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
                color = primaryColor,
                strokeWidth = 3.dp
            )
        }

        // ── Center Pause/Play Indicator (appears briefly on tap) ──
        AnimatedVisibility(
            visible = showPauseIndicator && !isBuffering,
            enter = scaleIn(initialScale = 0.6f, animationSpec = tween(150)) + fadeIn(animationSpec = tween(100)),
            exit = scaleOut(targetScale = 1.2f, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPlaying) stringResource(R.string.cd_play) else stringResource(R.string.cd_pause),
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // ── Like Animation (double-tap heart) ──
        AnimatedVisibility(
            visible = showLikeAnimation,
            enter = scaleIn(
                initialScale = 0.3f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            ) + fadeIn(),
            exit = scaleOut(targetScale = 1.4f, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = stringResource(R.string.cd_liked),
                tint = Color.Red,
                modifier = Modifier.size(120.dp)
            )
            LaunchedEffect(Unit) {
                delay(800)
                showLikeAnimation = false
            }
        }

        // ── Gradient Overlays ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(350.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomStart)
                .padding(bottom = 16.dp, start = 16.dp, end = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onChannelClick)
                ) {
                    AsyncImage(
                        model = video.channelThumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Gray),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (!isSubscribed) {
                        Button(
                            onClick = {
                                scope.launch {
                                    viewModel.toggleSubscription(
                                        video.channelId,
                                        video.channelName,
                                        video.channelThumbnailUrl
                                    )
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = onPrimaryColor
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(
                                stringResource(R.string.action_subscribe),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = onDescriptionClick)
                )

                if (video.viewCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${formatViewCount(video.viewCount)} views",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
                    val rotation by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 360f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(3000, easing = LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "disc_rotation"
                    )

                    Icon(
                        Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.shorts_original_sound, video.channelName),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ShortsActionButton(
                    icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    text = video.toShortVideo().likeCountText.takeIf { it.isNotBlank() } ?: stringResource(R.string.action_like),
                    tint = if (isLiked) Color.Red else Color.White,
                    onClick = {
                        scope.launch { viewModel.toggleLike(video.toShortVideo()) }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )

                ShortsActionButton(
                    icon = Icons.Default.ThumbDown,
                    text = stringResource(R.string.action_dislike),
                    onClick = {}
                )

                ShortsActionButton(
                    icon = Icons.Default.Comment,
                    text = stringResource(R.string.action_comments),
                    onClick = onCommentsClick
                )

                ShortsActionButton(
                    icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    text = stringResource(R.string.action_save),
                    tint = if (isSaved) primaryColor else Color.White,
                    onClick = {
                        viewModel.toggleSaveShort(video.toShortVideo())
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )

                ShortsActionButton(
                    icon = Icons.Default.Share,
                    text = stringResource(R.string.action_share),
                    onClick = onShareClick
                )

                val infiniteTransition = rememberInfiniteTransition(label = "album_spin")
                val albumRotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(4000, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "album_rotation"
                )

                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.DarkGray, CircleShape)
                        .padding(4.dp)
                ) {
                    AsyncImage(
                        model = video.channelThumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .then(
                                if (isPlaying) Modifier.graphicsLayer { rotationZ = albumRotation }
                                else Modifier
                            ),
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // ── Scrubbable Progress Bar ──
        if (duration > 0) {
            val progress = if (isDragging) dragProgress else (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
            
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(20.dp) 
                    .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { /* Consumes clicks to prevent pausing */ }
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    dragProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    isDragging = false
                                    playerPool.seekTo((dragProgress * duration).toLong())
                                },
                                onDragCancel = { isDragging = false },
                                onHorizontalDrag = { change, _ ->
                                    dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val newProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                                playerPool.seekTo((newProgress * duration).toLong())
                            }
                        }
                ) {
                    val barHeight = 2.dp.toPx()
                    val activeHeight = if (isDragging) 4.dp.toPx() else 2.dp.toPx()
                    val y = size.height - barHeight
                    
                    drawRect(
                        color = Color.White.copy(alpha = 0.3f),
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - barHeight),
                        size = androidx.compose.ui.geometry.Size(size.width, barHeight)
                    )
                    
                    drawRect(
                        color = primaryColor,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - activeHeight),
                        size = androidx.compose.ui.geometry.Size(size.width * progress, activeHeight)
                    )
                    
                    if (isDragging) {
                        drawCircle(
                            color = primaryColor,
                            radius = 6.dp.toPx(),
                            center = androidx.compose.ui.geometry.Offset(size.width * progress, size.height - activeHeight / 2)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ShortVideoPlayer(
    video: Video,
    isVisible: Boolean,
    pageIndex: Int = 0,
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
    ShortVideoPage(
        video = video,
        isActive = isVisible,
        pageIndex = pageIndex,
        viewModel = viewModel,
        onBack = onBack,
        onChannelClick = { onChannelClick(video.channelId) },
        onCommentsClick = onCommentsClick,
        onDescriptionClick = onDescriptionClick,
        onShareClick = onShareClick,
        modifier = modifier
    )
}

// Reusable Components
@Composable
fun ShortsActionButton(
    icon: ImageVector,
    text: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null, 
            onClick = onClick
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black,
                    blurRadius = 4f
                )
            ),
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
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
    ShortsActionButton(icon = icon, text = text, tint = tint, onClick = onClick, modifier = modifier)
}

fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
