package io.github.aedev.flow.ui.screens.shorts

import android.util.Log
import android.view.SurfaceHolder
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ShortsPlayerUiMode
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.toShortVideo
import io.github.aedev.flow.data.shorts.ShortVideoQuality
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.shorts.ShortsPlayerPool
import io.github.aedev.flow.player.stream.StreamProcessor
import io.github.aedev.flow.player.stream.VideoCodecUtils
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.ui.components.PlaybackSpeedSlider
import io.github.aedev.flow.ui.components.playbackSpeedOptions
import io.github.aedev.flow.ui.components.playbackSpeedSliderPresets
import io.github.aedev.flow.ui.components.rememberFlowSheetState
import io.github.aedev.flow.ui.components.rememberDateDisplaySettings
import io.github.aedev.flow.ui.screens.player.components.PlayerQualitySelectorContent
import io.github.aedev.flow.ui.screens.player.components.PlayerQualitySelectorOption
import io.github.aedev.flow.ui.screens.player.components.SeekbarWithPreview
import io.github.aedev.flow.ui.screens.player.components.VideoAmbientBackground
import io.github.aedev.flow.ui.screens.player.components.rememberAmbientFrame
import io.github.aedev.flow.utils.DateContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val shortsOverlayTextShadow = Shadow(
    color = Color.Black,
    blurRadius = 4f
)

@Composable
internal fun ShortVideoPage(
    video: Video,
    isActive: Boolean,
    pageIndex: Int,
    viewModel: ShortsViewModel,
    bottomNavOverlayPadding: androidx.compose.ui.unit.Dp = 0.dp,
    actions: ShortVideoPageActions,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerPreferences = remember { io.github.aedev.flow.data.local.PlayerPreferences(context) }
    val settings = rememberShortVideoPlayerSettings(playerPreferences)
    val isSimpleShortsUi = settings.uiMode == ShortsPlayerUiMode.SIMPLE
    val isImpressiveShortsUi = settings.uiMode == ShortsPlayerUiMode.IMPRESSIVE
    val pageState = remember(video.id) { ShortVideoPageState() }
    val sessionState = remember(video.id, isActive) { ShortVideoSessionState() }
    val autoAdvanceState = remember(
        video.id,
        isActive,
        settings.playbackMode,
        settings.autoScrollSeconds
    ) { ShortVideoAutoAdvanceState() }
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
    val controlsVisible = !isImpressiveShortsUi || sessionState.showImpressiveControls
    val seekBarTouchHeight = 28.dp
    val seekBarBottomPadding = bottomNavOverlayPadding.coerceAtLeast(0.dp)
    val controlsBottomPadding = seekBarBottomPadding + 34.dp
    val seekBarInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(isActive, settings.playbackSpeed) {
        if (isActive) playerPool.setBasePlaybackSpeed(settings.playbackSpeed)
    }

    // ── PlayerView instance ──
    val playerView = remember {
        PlayerView(context).apply {
            useController = false
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            keepScreenOn = true
        }
    }
    val ambientActive = isActive && settings.ambientModeEnabled
    val ambientFrame = rememberAmbientFrame(playerView, ambientActive) {
        playerPool.getPlayerForIndex(pageIndex)?.isPlaying == true
    }

    // Register a MediaSessionCompat so earphone / Bluetooth media buttons (play-pause)
    // work while a short is active. Re-created every time isActive changes; released on dispose.
    DisposableEffect(isActive) {
        val session = MediaSessionCompat(context, "ShortsPlayer").also { s ->
            s.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setActions(
                        PlaybackStateCompat.ACTION_PLAY or
                        PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE
                    )
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 1f)
                    .build()
            )
            s.setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay()  { playerPool.play() }
                override fun onPause() { playerPool.pause() }
            })
            s.isActive = isActive
        }
        onDispose {
            session.isActive = false
            session.release()
        }
    }

    // ── Initialize player pool and handle playback when visibility changes ──
    LaunchedEffect(isActive, video.id) {
        if (isActive) {
            pageState.hasStartedPlaying = false
            playerPool.initialize(context)
            EnhancedMusicPlayerManager.pause()

            val player = playerPool.getPlayerForIndex(pageIndex)
            playerView.player = player

            if (player != null && player.isPlaying) {
                pageState.hasStartedPlaying = true
            }
        } else {
            playerView.player = null
        }
    }

    // ── Add listener to detect when video ends (for auto-play-next) ──
    fun requestAutoAdvance() {
        if (!autoAdvanceState.hasAutoAdvanced) {
            autoAdvanceState.hasAutoAdvanced = true
            actions.onVideoEnded()
        }
    }

    fun recordShortWatched(
        positionMs: Long = pageState.currentPosition,
        durationMs: Long = pageState.duration
    ) {
        if (!sessionState.hasRecordedWatched) {
            sessionState.hasRecordedWatched = true
            viewModel.recordShortWatched(video.toShortVideo(), positionMs, durationMs)
        }
    }

    fun recordShortProgress(
        positionMs: Long = pageState.currentPosition,
        durationMs: Long = pageState.duration
    ) {
        if (!sessionState.hasRecordedWatched) {
            sessionState.hasTouchedHistory = true
            sessionState.lastProgressSavedAt = positionMs
            viewModel.recordShortProgress(video.toShortVideo(), positionMs, durationMs)
        }
    }

    val latestPosition by rememberUpdatedState(pageState.currentPosition)
    val latestDuration by rememberUpdatedState(pageState.duration)
    val latestHasStartedPlaying by rememberUpdatedState(pageState.hasStartedPlaying)
    val latestHasRecordedWatched by rememberUpdatedState(sessionState.hasRecordedWatched)

    DisposableEffect(video.id, isActive) {
        onDispose {
            if (
                isActive &&
                !latestHasRecordedWatched &&
                (latestHasStartedPlaying || latestPosition >= 1_000L)
            ) {
                viewModel.recordShortProgress(video.toShortVideo(), latestPosition, latestDuration)
            }
        }
    }

    DisposableEffect(isActive, pageIndex, settings.playbackMode, settings.autoScrollSeconds) {
        val player = playerPool.getPlayerForIndex(pageIndex)
        val eventListener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_ENDED) {
                    val endedDuration = player?.duration?.coerceAtLeast(0L) ?: pageState.duration
                    recordShortWatched(
                        positionMs = endedDuration.takeIf { it > 0L } ?: pageState.currentPosition,
                        durationMs = endedDuration
                    )
                    if (settings.playbackMode == "auto_next" || settings.playbackMode == "auto_interval") {
                        requestAutoAdvance()
                    }
                }
            }
        }
        
        if (isActive && player != null) {
            player.addListener(eventListener)
        }

        onDispose {
            player?.removeListener(eventListener)
        }
    }

    // ── Efficient progress tracker: throttles Compose writes while active ──
    LaunchedEffect(isActive, pageIndex, settings.playbackMode, settings.autoScrollSeconds) {
        if (isActive) {
            while (true) {
                val p = playerPool.getPlayerForIndex(pageIndex)
                if (p != null) {
                    val position = p.currentPosition.coerceAtLeast(0L)
                    val safeDuration = p.duration.coerceAtLeast(0L)
                    val newBuffering = p.playbackState == androidx.media3.common.Player.STATE_BUFFERING

                    if (safeDuration != pageState.duration) {
                        pageState.duration = safeDuration
                    }
                    if (
                        pageState.currentPosition == 0L ||
                        position < pageState.currentPosition ||
                        kotlin.math.abs(position - pageState.currentPosition) >= 1_000L
                    ) {
                        pageState.currentPosition = position
                    }
                    if (pageState.isBuffering != newBuffering) {
                        pageState.isBuffering = newBuffering
                    }

                    val playerIsPlaying = p.isPlaying
                    if (pageState.isPlaying != playerIsPlaying) {
                        pageState.isPlaying = playerIsPlaying
                    }

                    if (playerIsPlaying && !pageState.hasStartedPlaying) {
                        pageState.hasStartedPlaying = true
                    }

                    if (!pageState.isDragging && !newBuffering && playerIsPlaying) {
                        if (!sessionState.hasTouchedHistory && position >= 1_500L) {
                            recordShortProgress(position, safeDuration)
                        } else if (
                            sessionState.hasTouchedHistory &&
                            position - sessionState.lastProgressSavedAt >= 5_000L
                        ) {
                            recordShortProgress(position, safeDuration)
                        }

                        if (
                            !sessionState.hasRecordedWatched &&
                            safeDuration > 0L &&
                            position >= (safeDuration * 0.9f).toLong()
                        ) {
                            recordShortWatched(position, safeDuration)
                        }

                        if (settings.playbackMode == "auto_interval" && !autoAdvanceState.hasAutoAdvanced) {
                            val intervalMs = settings.autoScrollSeconds.coerceIn(5, 20) * 1000L
                            val shouldWaitForEnd = safeDuration in 1..intervalMs
                            if (!shouldWaitForEnd && position >= intervalMs) {
                                recordShortWatched(
                                    positionMs = position,
                                    durationMs = safeDuration.takeIf { it > 0L } ?: intervalMs
                                )
                                requestAutoAdvance()
                            }
                        }
                    }
                }
                delay(500)
            }
        }
    }

    // ── Pause indicator auto-hide ──
    LaunchedEffect(pageState.showPauseIndicator) {
        if (pageState.showPauseIndicator) {
            delay(600)
            pageState.showPauseIndicator = false
        }
    }

    LaunchedEffect(isActive, settings.uiMode, video.id) {
        if (!isActive || !isImpressiveShortsUi) {
            sessionState.showImpressiveControls = false
        }
    }

    LaunchedEffect(sessionState.showImpressiveControls, isImpressiveShortsUi) {
        if (isImpressiveShortsUi && sessionState.showImpressiveControls) {
            delay(2000)
            sessionState.showImpressiveControls = false
        }
    }

    fun togglePlaybackWithFeedback() {
        playerPool.togglePlayPause()
        val player = playerPool.getPlayerForIndex(pageIndex)
        if (player != null) pageState.isPlaying = player.isPlaying
        pageState.showPauseIndicator = true
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // ── Main Layout ──
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (ambientActive) {
            VideoAmbientBackground(
                frame = ambientFrame.frame,
                baseColor = ambientFrame.base,
                accentColor = ambientFrame.accent,
                modifier = Modifier.fillMaxSize()
            )
        }

        AndroidView(
            factory = { playerView },
            modifier = Modifier.fillMaxSize()
        )

        // ── Thumbnail placeholder until video starts ──
        AnimatedVisibility(
            visible = !pageState.hasStartedPlaying && !pageState.isBuffering,
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(settings.uiMode, isLiked, sessionState.showImpressiveControls) {
                    detectTapGestures(
                        onTap = { offset ->
                            val isCenterTap = offset.x in (size.width * 0.25f)..(size.width * 0.75f) &&
                                offset.y in (size.height * 0.25f)..(size.height * 0.75f)
                            if (
                                isImpressiveShortsUi &&
                                isCenterTap &&
                                !sessionState.showImpressiveControls
                            ) {
                                sessionState.showImpressiveControls = true
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            } else {
                                togglePlaybackWithFeedback()
                            }
                        },
                        onDoubleTap = {
                            if (!isLiked) {
                                scope.launch { viewModel.toggleLike(video.toShortVideo()) }
                                pageState.showLikeAnimation = true
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        },
                        onPress = {
                            try {
                                awaitRelease()
                            } finally {
                                if (pageState.isFastForwarding) {
                                    pageState.isFastForwarding = false
                                    playerPool.resetPlaybackSpeed()
                                }
                            }
                        },
                        onLongPress = { offset ->
                            val isCenterTap = offset.x in (size.width * 0.25f)..(size.width * 0.75f) &&
                                offset.y in (size.height * 0.25f)..(size.height * 0.75f)
                            if (isImpressiveShortsUi && isCenterTap) {
                                actions.onCommentsClick()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            } else {
                                pageState.isFastForwarding = true
                                playerPool.setPlaybackSpeed(2.0f)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    )
                }
        )

        AnimatedVisibility(
            visible = pageState.isFastForwarding,
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
        AnimatedVisibility(
            visible = controlsVisible && isActive && settings.playbackMode == "auto_interval",
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 56.dp, end = 16.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.55f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            R.string.shorts_auto_scroll_active_template,
                            settings.autoScrollSeconds
                        ),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        if (pageState.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(44.dp),
                color = primaryColor,
                strokeWidth = 3.dp
            )
        }

        AnimatedVisibility(
            visible = pageState.showPauseIndicator && !pageState.isBuffering,
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
                    imageVector = if (pageState.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (pageState.isPlaying) {
                        stringResource(R.string.cd_play)
                    } else {
                        stringResource(R.string.cd_pause)
                    },
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        // ── Like Animation (double-tap heart) ──
        AnimatedVisibility(
            visible = pageState.showLikeAnimation,
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
                pageState.showLikeAnimation = false
            }
        }

        if (controlsVisible) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(bottom = controlsBottomPadding, start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = actions.onChannelClick)
                ) {
                    ChannelAvatarImage(
                        url = video.channelThumbnailUrl,
                        contentDescription = video.channelName,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Gray)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = video.channelName,
                        style = MaterialTheme.typography.titleMedium.copy(
                            shadow = shortsOverlayTextShadow
                        ),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    if (isSimpleShortsUi) {
                        val subscriptionDescription = if (isSubscribed) {
                            stringResource(R.string.unsubscribe)
                        } else {
                            stringResource(R.string.action_subscribe)
                        }
                        Surface(
                            onClick = {
                                scope.launch {
                                    viewModel.toggleSubscription(
                                        video.channelId,
                                        video.channelName,
                                        video.channelThumbnailUrl
                                    )
                                }
                                val toastText = if (isSubscribed) {
                                    context.getString(R.string.unsubscribed_from, video.channelName)
                                } else {
                                    context.getString(R.string.subscribed_to, video.channelName)
                                }
                                Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            shape = RoundedCornerShape(10.dp),
                            color = Color.Transparent,
                            contentColor = if (isSubscribed) Color.White else onPrimaryColor,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSubscribed) Color.Transparent else primaryColor,
                                    contentColor = if (isSubscribed) Color.White else onPrimaryColor,
                                    border = if (isSubscribed) {
                                        androidx.compose.foundation.BorderStroke(
                                            1.dp,
                                            Color.White
                                        )
                                    } else {
                                        null
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        ShortsOverlayIcon(
                                            imageVector = if (isSubscribed) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = subscriptionDescription,
                                            modifier = Modifier.size(22.dp),
                                            tint = if (isSubscribed) {
                                                Color.White
                                            } else {
                                                onPrimaryColor
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!isSubscribed) {
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
                    } else {
                        OutlinedButton(
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
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(
                                stringResource(R.string.subscribed),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    shadow = shortsOverlayTextShadow
                                ),
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = video.title,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        shadow = shortsOverlayTextShadow
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable(onClick = actions.onDescriptionClick)
                )

                if (video.uploadDate.isNotBlank() || video.viewCount > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (video.viewCount > 0) {
                            Text(
                                text = stringResource(
                                    R.string.views_template,
                                    formatViewCount(video.viewCount)
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    shadow = shortsOverlayTextShadow
                                ),
                                color = Color.White
                            )
                        }
                        if (video.viewCount > 0 && video.uploadDate.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.video_metadata_short_template, "", ""),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    shadow = shortsOverlayTextShadow
                                ),
                                color = Color.White
                            )
                        }
                        if (video.uploadDate.isNotBlank()) {
                            Text(
                                text = rememberDateDisplaySettings().format(
                                    video.uploadDate,
                                    DateContext.WATCH,
                                    video.timestamp
                                ),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    shadow = shortsOverlayTextShadow
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp) 
            ) {
                ShortsActionButton(
                    icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    text = if (isSimpleShortsUi) {
                        video.toShortVideo().likeCountText.takeIf { it.isNotBlank() }.orEmpty()
                    } else {
                        video.toShortVideo().likeCountText.takeIf { it.isNotBlank() } ?: stringResource(R.string.action_like)
                    },
                    contentDescription = stringResource(R.string.action_like),
                    tint = if (isLiked) Color.Red else Color.White,
                    onClick = {
                        scope.launch { viewModel.toggleLike(video.toShortVideo()) }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )

                ShortsActionButton(
                    icon = Icons.Default.Comment,
                    text = if (isSimpleShortsUi) {
                        video.toShortVideo().commentCountText.takeIf { it.isNotBlank() }.orEmpty()
                    } else {
                        video.toShortVideo().commentCountText.takeIf { it.isNotBlank() } ?: stringResource(R.string.action_comments)
                    },
                    contentDescription = stringResource(R.string.action_comments),
                    onClick = actions.onCommentsClick
                )

                ShortsActionButton(
                    icon = if (isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    text = if (isSimpleShortsUi) "" else stringResource(R.string.action_save),
                    contentDescription = stringResource(R.string.action_save),
                    tint = if (isSaved) primaryColor else Color.White,
                    onClick = {
                        viewModel.toggleSaveShort(video.toShortVideo())
                        if (isSimpleShortsUi) {
                            Toast.makeText(
                                context,
                                context.getString(if (isSaved) R.string.shorts_unsaved else R.string.shorts_saved),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                )

                ShortsActionButton(
                    icon = Icons.Default.Share,
                    text = if (isSimpleShortsUi) "" else stringResource(R.string.action_share),
                    contentDescription = stringResource(R.string.action_share),
                    onClick = actions.onShareClick
                )

                ShortsActionButton(
                    icon = Icons.Default.MoreVert,
                    text = if (isSimpleShortsUi) "" else stringResource(R.string.cd_more_options),
                    contentDescription = stringResource(R.string.cd_more_options),
                    onClick = { pageState.showShortsOptionsSheet = true }
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
                        .size(36.dp) 
                        .background(Color.DarkGray, CircleShape)
                        .padding(3.dp)
                ) {
                    ChannelAvatarImage(
                        url = video.channelThumbnailUrl,
                        contentDescription = video.channelName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .then(
                                if (isActive && pageState.isPlaying) {
                                    Modifier.graphicsLayer { rotationZ = albumRotation }
                                }
                                else Modifier
                            )
                    )
                }
            }
        }

        // ── Scrubbable Progress Bar ──
        }
        if (pageState.duration > 0) {
            val progress = if (pageState.isDragging) {
                pageState.dragProgress
            } else {
                (pageState.currentPosition.toFloat() / pageState.duration.toFloat()).coerceIn(0f, 1f)
            }

            SeekbarWithPreview(
                value = progress,
                onValueChange = { newProgress ->
                    pageState.isDragging = true
                    pageState.dragProgress = newProgress.coerceIn(0f, 1f)
                },
                onValueChangeFinished = {
                    playerPool.seekTo(
                        (pageState.dragProgress.coerceIn(0f, 1f) * pageState.duration).toLong()
                    )
                    pageState.isDragging = false
                },
                interactionSource = seekBarInteractionSource,
                duration = pageState.duration,
                edgeAligned = true,
                enabled = isActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = seekBarBottomPadding)
                    .height(seekBarTouchHeight)
                    .zIndex(1f)
            )
        }
    }

    if (pageState.showShortsOptionsSheet) {
        ShortsOptionsSheet(
            isLoadingStreams = pageState.isLoadingStreams,
            onWantMore = {
                pageState.showShortsOptionsSheet = false
                actions.onWantMore()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onNotInterested = {
                pageState.showShortsOptionsSheet = false
                actions.onNotInterested()
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            ambientModeEnabled = settings.ambientModeEnabled,
            onAmbientModeToggle = { enabled ->
                scope.launch { playerPreferences.setVideoAmbientModeEnabled(enabled) }
            },
            onDownloadClick = {
                pageState.showShortsOptionsSheet = false
                if (!pageState.isLoadingStreams) {
                    pageState.isLoadingStreams = true
                    scope.launch {
                        val streamInfo = viewModel.getVideoStreamInfo(video.id)
                        pageState.currentStreamInfo = streamInfo
                        val (itVideo, itAudio) = viewModel.getInnerTubeDownloadFormats(video.id)
                        pageState.currentInnerTubeVideoFormats = itVideo
                        pageState.currentInnerTubeAudioFormats = itAudio
                        if (streamInfo != null || itVideo.isNotEmpty()) {
                            pageState.currentStreamSizes = viewModel.fetchStreamSizes(video.id)
                            pageState.showDownloadDialog = true
                        }
                        pageState.isLoadingStreams = false
                    }
                }
            },
            onAudioTrackClick = {
                pageState.showShortsOptionsSheet = false
                if (!pageState.isLoadingStreams) {
                    pageState.isLoadingStreams = true
                    scope.launch {
                        val streamInfo = viewModel.getVideoStreamInfo(video.id)
                        pageState.availableAudioStreams = streamInfo?.audioStreams
                            ?.sortedByDescending { it.averageBitrate }
                            ?.groupBy { stream ->
                                val trackIdLang = stream.audioTrackId
                                    ?.substringAfterLast(".")
                                    ?.takeIf { it.isNotBlank() && it != stream.audioTrackId }
                                val localeLang = stream.audioLocale?.language?.takeIf { it.isNotBlank() }
                                val trackName = stream.audioTrackName?.takeIf { it.isNotBlank() }
                                trackIdLang ?: localeLang ?: trackName ?: "default"
                            }
                            ?.map { (_, group) -> group.first() }
                            ?: emptyList()
                        pageState.isLoadingStreams = false
                        if (pageState.availableAudioStreams.isNotEmpty()) {
                            pageState.showAudioTrackSheet = true
                        }
                    }
                }
            },
            onQualityClick = {
                pageState.showShortsOptionsSheet = false
                if (!pageState.isLoadingStreams) {
                    pageState.isLoadingStreams = true
                    scope.launch {
                        pageState.availableQualities = viewModel.getAvailableQualities(video.id)
                        val activeFormat = playerPool.getPlayerForIndex(pageIndex)?.videoFormat
                        val activeCodecKey = activeFormat?.let { format ->
                            VideoCodecUtils.codecKeyFromMimeType(
                                buildString {
                                    append(format.sampleMimeType.orEmpty())
                                    format.codecs?.takeIf { it.isNotBlank() }?.let { codecs ->
                                        append("; codecs=\"")
                                        append(codecs)
                                        append('"')
                                    }
                                }
                            )
                        }
                        val activeQuality = findActiveShortQuality(
                            qualities = pageState.availableQualities,
                            currentVideoUrl = playerPool.getVideoUrlForIndex(pageIndex),
                            activeVideoWidth = activeFormat?.width ?: 0,
                            activeVideoHeight = activeFormat?.height ?: 0,
                            activeCodecKey = activeCodecKey
                        )
                        pageState.selectedQualityHeight = activeQuality?.heightClass ?: -1
                        pageState.selectedQualityUrl = activeQuality?.videoUrl
                        pageState.isLoadingStreams = false
                        if (pageState.availableQualities.isNotEmpty()) {
                            pageState.showQualitySheet = true
                        }
                    }
                }
            },
            currentSpeed = settings.playbackSpeed,
            onSpeedClick = {
                pageState.showShortsOptionsSheet = false
                pageState.showSpeedSheet = true
            },
            onDismiss = { pageState.showShortsOptionsSheet = false }
        )
    }

    if (pageState.showSpeedSheet) {
        ShortsSpeedSheet(
            currentSpeed = settings.playbackSpeed,
            speedSliderEnabled = settings.speedSliderEnabled,
            customSpeedsEnabled = settings.customSpeedsEnabled,
            customSpeedPresetsRaw = settings.customSpeedPresetsRaw,
            onSpeedSelected = { speed ->
                playerPool.setBasePlaybackSpeed(speed)
            },
            onSpeedSelectionFinished = { speed ->
                scope.launch { playerPreferences.setShortsPlaybackSpeed(speed) }
            },
            onDismiss = { pageState.showSpeedSheet = false }
        )
    }

    // ── Audio Track Selection Sheet ──
    if (pageState.showAudioTrackSheet && pageState.availableAudioStreams.isNotEmpty()) {
        ShortsAudioTrackSheet(
            audioStreams = pageState.availableAudioStreams,
            selectedIndex = pageState.selectedAudioIndex,
            onTrackSelected = { index ->
                val stream = pageState.availableAudioStreams[index]
                val audioUrl = stream.content ?: stream.url
                playerPool.reloadWithAudioUrl(pageIndex, video.id, audioUrl)
                pageState.selectedAudioIndex = index
                pageState.showAudioTrackSheet = false
            },
            onDismiss = { pageState.showAudioTrackSheet = false }
        )
    }

    // ── Quality Selection Sheet ──
    if (pageState.showQualitySheet && pageState.availableQualities.isNotEmpty()) {
        ShortsQualitySheet(
            qualities = pageState.availableQualities,
            selectedHeight = pageState.selectedQualityHeight.takeIf { it >= 0 },
            selectedVideoUrl = pageState.selectedQualityUrl,
            onQualitySelected = { quality ->
                playerPool.reloadWithVideoUrl(pageIndex, video.id, quality.videoUrl)
                pageState.selectedQualityHeight = quality.heightClass
                pageState.selectedQualityUrl = quality.videoUrl
                pageState.showQualitySheet = false
            },
            groupedByResolution = settings.groupedQualitySelectorEnabled,
            onDismiss = { pageState.showQualitySheet = false }
        )
    }

    // ── Download Dialog ──
    if (
        pageState.showDownloadDialog &&
        (pageState.currentStreamInfo != null || pageState.currentInnerTubeVideoFormats.isNotEmpty())
    ) {
        if (settings.downloadDialogStyle == io.github.aedev.flow.data.local.DownloadDialogStyle.COMPACT) {
            io.github.aedev.flow.ui.screens.player.components.DownloadQualityDialogCompact(
                streamInfo = pageState.currentStreamInfo,
                streamSizes = pageState.currentStreamSizes,
                innerTubeVideoFormats = pageState.currentInnerTubeVideoFormats,
                innerTubeAudioFormats = pageState.currentInnerTubeAudioFormats,
                video = video,
                onDismiss = { pageState.showDownloadDialog = false }
            )
        } else {
            io.github.aedev.flow.ui.screens.player.components.DownloadQualityDialog(
                streamInfo = pageState.currentStreamInfo,
                streamSizes = pageState.currentStreamSizes,
                innerTubeVideoFormats = pageState.currentInnerTubeVideoFormats,
                innerTubeAudioFormats = pageState.currentInnerTubeAudioFormats,
                video = video,
                onDismiss = { pageState.showDownloadDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortsOptionsSheet(
    isLoadingStreams: Boolean,
    onWantMore: () -> Unit,
    onNotInterested: () -> Unit,
    onDislikeClick: () -> Unit = {},
    ambientModeEnabled: Boolean,
    onAmbientModeToggle: (Boolean) -> Unit,
    onDownloadClick: () -> Unit,
    onAudioTrackClick: () -> Unit,
    onQualityClick: () -> Unit,
    currentSpeed: Float = 1f,
    onSpeedClick: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFlowSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.cd_more_options),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            HorizontalDivider()
            Surface(
                onClick = { onAmbientModeToggle(!ambientModeEnabled) },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_ambient_mode),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.player_settings_ambient_mode),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = ambientModeEnabled,
                        onCheckedChange = null
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Surface(
                onClick = onWantMore,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ThumbUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.action_want_more),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                onClick = onNotInterested,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.NotInterested,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.action_not_interested),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                onClick = {
                    onDismiss()
                    onDislikeClick()
                },
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ThumbDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.action_dislike),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // ── Download ──
            Surface(
                onClick = onDownloadClick,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoadingStreams
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        tint = if (isLoadingStreams) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                               else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.download_video),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isLoadingStreams) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                else MaterialTheme.colorScheme.onSurface
                    )
                    if (isLoadingStreams) {
                        Spacer(Modifier.weight(1f))
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            Surface(
                onClick = onAudioTrackClick,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoadingStreams
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AudioFile,
                        contentDescription = null,
                        tint = if (isLoadingStreams) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                               else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.shorts_audio_track),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isLoadingStreams) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                else MaterialTheme.colorScheme.onSurface
                    )
                    if (isLoadingStreams) {
                        Spacer(Modifier.weight(1f))
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            Surface(
                onClick = onQualityClick,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoadingStreams
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HighQuality,
                        contentDescription = null,
                        tint = if (isLoadingStreams) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                               else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.shorts_quality),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isLoadingStreams) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                else MaterialTheme.colorScheme.onSurface
                    )
                    if (isLoadingStreams) {
                        Spacer(Modifier.weight(1f))
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp))
            Surface(
                onClick = onSpeedClick,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Speed,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.shorts_playback_speed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = if (currentSpeed == 1f) {
                            stringResource(R.string.normal)
                        } else {
                            stringResource(
                                R.string.playback_speed_multiplier,
                                currentSpeed.toString()
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortsSpeedSheet(
    currentSpeed: Float,
    speedSliderEnabled: Boolean,
    customSpeedsEnabled: Boolean,
    customSpeedPresetsRaw: String,
    onSpeedSelected: (Float) -> Unit,
    onSpeedSelectionFinished: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val speeds = remember(customSpeedsEnabled, customSpeedPresetsRaw) {
        playbackSpeedOptions(customSpeedsEnabled, customSpeedPresetsRaw)
    }
    val sliderPresets = remember(customSpeedsEnabled, customSpeedPresetsRaw) {
        playbackSpeedSliderPresets(customSpeedsEnabled, customSpeedPresetsRaw)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFlowSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.shorts_playback_speed),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close)
                    )
                }
            }
            HorizontalDivider()
            if (speedSliderEnabled) {
                PlaybackSpeedSlider(
                    currentSpeed = currentSpeed,
                    quickPresets = sliderPresets,
                    onSpeedSelected = onSpeedSelected,
                    onSpeedSelectionFinished = onSpeedSelectionFinished
                )
            } else {
                LazyColumn {
                    items(speeds, key = { it }) { speed ->
                        val isSelected = speed == currentSpeed
                        Surface(
                            onClick = {
                                onSpeedSelected(speed)
                                onSpeedSelectionFinished(speed)
                                onDismiss()
                            },
                            color = Color.Transparent,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (speed == 1.0f) {
                                        stringResource(R.string.normal)
                                    } else {
                                        stringResource(
                                            R.string.playback_speed_multiplier,
                                            speed.toString()
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortsAudioTrackSheet(
    audioStreams: List<org.schabi.newpipe.extractor.stream.AudioStream>,
    selectedIndex: Int,
    onTrackSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFlowSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.shorts_audio_track),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            HorizontalDivider()
            LazyColumn {
                items(audioStreams.size) { index ->
                    val stream = audioStreams[index]
                    val displayName = StreamProcessor.audioTrackDisplayName(stream)
                        ?: stringResource(
                            R.string.audio_track_number_template,
                            stringResource(R.string.audio_track),
                            index + 1
                        )
                    val bitrateLabel = if (stream.averageBitrate >= 1000) "${stream.averageBitrate / 1000} kbps" else ""
                    val isSelected = index == selectedIndex
                    val selectedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    Surface(
                        onClick = { onTrackSelected(index) },
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (isSelected) selectedContentColor else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (isSelected) selectedContentColor
                                            else MaterialTheme.colorScheme.onSurface
                                )
                                if (bitrateLabel.isNotEmpty()) {
                                    Text(
                                        text = bitrateLabel,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isSelected) selectedContentColor.copy(alpha = 0.72f)
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = selectedContentColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShortsQualitySheet(
    qualities: List<ShortVideoQuality>,
    selectedHeight: Int?,
    selectedVideoUrl: String?,
    onQualitySelected: (ShortVideoQuality) -> Unit,
    groupedByResolution: Boolean,
    onDismiss: () -> Unit
) {
    val configuration = LocalConfiguration.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberFlowSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = configuration.screenHeightDp.dp * 0.75f)
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = stringResource(R.string.shorts_quality),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
            HorizontalDivider()
            val selectorOptions = qualities.map { quality ->
                val isSelected = selectedVideoUrl?.let { it == quality.videoUrl }
                    ?: (quality.heightClass == selectedHeight)
                PlayerQualitySelectorOption(
                    item = quality,
                    height = quality.heightClass,
                    label = quality.label,
                    selected = isSelected,
                    supportingText = quality.codecLabel.takeIf { it.isNotBlank() },
                    codecKey = quality.codecKey,
                    codecLabel = quality.codecLabel,
                    streamKey = quality.videoUrl
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                PlayerQualitySelectorContent(
                    options = selectorOptions,
                    groupedByResolution = groupedByResolution,
                    onOptionSelected = onQualitySelected
                )
            }
        }
    }
}

@Composable
private fun ShortsOverlayIcon(
    imageVector: ImageVector,
    contentDescription: String?,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier
                .matchParentSize()
                .scale(1.08f)
        )
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.matchParentSize()
        )
    }
}

@Composable
fun ShortsActionButton(
    icon: ImageVector,
    text: String,
    contentDescription: String = text,
    tint: Color = Color.White,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 4.dp)
    ) {
        ShortsOverlayIcon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(26.dp)
        )
        if (text.isNotBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall.copy(
                    shadow = shortsOverlayTextShadow
                ),
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
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
    ShortsActionButton(
        icon = icon,
        text = text,
        tint = tint,
        onClick = onClick,
        modifier = modifier
    )
}

fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format("%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
