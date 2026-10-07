package io.github.aedev.flow.ui.components.videoplayer

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.MiniBarSwipeAction
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.components.shared.MediaMiniBarBounds
import io.github.aedev.flow.ui.components.shared.MediaMiniBarDefaults
import io.github.aedev.flow.ui.components.shared.MediaMiniBarSwipeReveal
import io.github.aedev.flow.ui.components.shared.MediaPlayPauseButton
import io.github.aedev.flow.ui.components.shared.MediaThumbnail
import io.github.aedev.flow.ui.components.shared.MiniBarReveal
import io.github.aedev.flow.ui.components.shared.icon
import io.github.aedev.flow.ui.components.shared.isDestructive
import io.github.aedev.flow.ui.components.shared.labelRes
import io.github.aedev.flow.ui.components.shared.mediaMiniBarSwipe
import io.github.aedev.flow.ui.components.shared.rememberMediaMiniBarSwipeHandler
import io.github.aedev.flow.ui.screens.player.state.hasVisibleQueue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val ThumbnailWidth = 78.dp
private const val PROGRESS_INTERVAL_MS = 1_000L
private const val CLOSE_PULL_HEIGHTS = 0.5f
private const val CLOSE_FLING_VELOCITY = 800f
private const val RESTORE_PULL_PX = 24f
private const val RESTORE_FLING_VELOCITY = -800f
private const val UPWARD_PULL_FOLLOW = 0.25f

/**
 * The mini bar for a video playing in the background, in the music bar's slot and shape. A tap or
 * a swipe up brings the video back, a swipe down or sideways closes it.
 */
@Composable
internal fun VideoBackgroundBar(
    video: Video,
    bounds: MediaMiniBarBounds,
    containerWidthPx: Float,
    containerHeightPx: Float,
    restingBottomPx: () -> Float,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    onOpenQueue: () -> Unit,
    swipeLeftAction: MiniBarSwipeAction,
    swipeRightAction: MiniBarSwipeAction,
    swipeActions: VideoBarSwipeActions,
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val heightPx = with(density) { MediaMiniBarDefaults.Height.toPx() }
    val spacerPx = with(density) { MediaMiniBarDefaults.BottomSpacer.toPx() }
    val shape = RoundedCornerShape(MediaMiniBarDefaults.CornerRadius)
    val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()
    val hasQueue = hasVisibleQueue(playerState.queueTitle, playerState.queueSize)
    val motion = MaterialTheme.motionScheme

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, motion.defaultEffectsSpec()) }
    val pullY = remember { Animatable(0f) }
    val swipeX = remember { Animatable(0f) }
    val swipeHandler =
        rememberMediaMiniBarSwipeHandler(
            scope = scope,
            density = density,
            hapticFeedback = LocalHapticFeedback.current,
            offsetAnimatable = swipeX,
            screenWidthPx = containerWidthPx,
            onCommit = { towardsStart -> swipeActions.commit(if (towardsStart) swipeLeftAction else swipeRightAction) },
        )

    Box(
        modifier =
            Modifier
                .offset {
                    val restingY = containerHeightPx - restingBottomPx() - spacerPx - heightPx
                    IntOffset(bounds.start.roundToInt(), restingY.roundToInt())
                }.size(with(density) { bounds.width.toDp() }, MediaMiniBarDefaults.Height)
                .graphicsLayer {
                    translationY = pullY.value + (1f - appear.value) * heightPx
                    alpha = appear.value * (1f - (pullY.value / (heightPx * 2f)).coerceIn(0f, 1f))
                }.mediaMiniBarSwipe(enabled = true, handler = swipeHandler)
                .pointerInput(Unit) {
                    val tracker = VelocityTracker()
                    detectVerticalDragGestures(
                        onDragStart = { tracker.resetTracking() },
                        onVerticalDrag = { change, amount ->
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val next = pullY.value + if (pullY.value + amount < 0f) amount * UPWARD_PULL_FOLLOW else amount
                            scope.launch { pullY.snapTo(next) }
                        },
                        onDragEnd = {
                            val velocity = tracker.calculateVelocity().y
                            val pulled = pullY.value
                            scope.launch {
                                when {
                                    pulled > heightPx * CLOSE_PULL_HEIGHTS || velocity > CLOSE_FLING_VELOCITY -> {
                                        pullY.animateTo(heightPx * 2f, motion.fastSpatialSpec(), velocity.coerceAtLeast(0f))
                                        onClose()
                                    }

                                    pulled < -RESTORE_PULL_PX || velocity < RESTORE_FLING_VELOCITY -> {
                                        pullY.snapTo(0f)
                                        onRestore()
                                    }

                                    else -> {
                                        pullY.animateTo(0f, motion.defaultSpatialSpec(), velocity)
                                    }
                                }
                            }
                        },
                        onDragCancel = { scope.launch { pullY.animateTo(0f, motion.defaultSpatialSpec()) } },
                    )
                }.clickable(onClick = onRestore),
    ) {
        MediaMiniBarSwipeReveal(
            offset = { swipeX.value },
            towardsStart = videoBarReveal(swipeLeftAction, swipeActions),
            towardsEnd = videoBarReveal(swipeRightAction, swipeActions),
            shape = shape,
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { translationX = swipeX.value }
                    .shadow(6.dp, shape, clip = false)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
                    .clip(shape),
        ) {
            BackgroundBarContent(
                video = video,
                isPlaying = playerState.playWhenReady,
                isBuffering = playerState.isBuffering,
                hasQueue = hasQueue,
                onOpenQueue = onOpenQueue,
            )
        }
    }
}

@Composable
private fun BoxScope.BackgroundBarContent(
    video: Video,
    isPlaying: Boolean,
    isBuffering: Boolean,
    hasQueue: Boolean,
    onOpenQueue: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(start = 10.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MediaThumbnail(
            videoId = video.id,
            thumbnailUrl = video.thumbnailUrl,
            width = ThumbnailWidth,
            shape = MaterialTheme.shapes.medium,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        MediaPlayPauseButton(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            onClick = {
                val manager = EnhancedPlayerManager.getInstance()
                if (isPlaying) manager.pause() else manager.play()
            },
        )
        if (hasQueue) {
            IconButton(onClick = onOpenQueue) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                    contentDescription = stringResource(R.string.playlist_queue),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    BackgroundBarProgress(
        isPlaying = isPlaying,
        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp),
    )
}

/** What a swipe towards one side uncovers: the video it moves to, or the action. */
@Composable
private fun videoBarReveal(
    action: MiniBarSwipeAction,
    actions: VideoBarSwipeActions,
): MiniBarReveal {
    val peek = actions.peek(action)
    val available = actions.isAvailable(action)
    val label =
        when {
            peek != null -> peek.title
            !available && action == MiniBarSwipeAction.NEXT -> stringResource(R.string.mini_bar_nothing_next)
            !available && action == MiniBarSwipeAction.PREVIOUS -> stringResource(R.string.mini_bar_nothing_previous)
            else -> stringResource(action.labelRes)
        }
    return MiniBarReveal(
        icon = action.icon,
        label = label,
        destructive = action.isDestructive,
        enabled = available,
        peekImageUrl = peek?.thumbnailUrl,
    )
}

/**
 * Playback progress along the bottom edge. Nothing publishes the position while the player is
 * hidden, so it is read once a second, and only while the video plays and the app is in view.
 */
@Composable
private fun BackgroundBarProgress(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(isPlaying, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            do {
                val manager = EnhancedPlayerManager.getInstance()
                val duration = manager.getDuration()
                progress = if (duration > 0L) (manager.getCurrentPosition().toFloat() / duration).coerceIn(0f, 1f) else 0f
                if (isPlaying) delay(PROGRESS_INTERVAL_MS)
            } while (isPlaying)
        }
    }
    LinearProgressIndicator(
        progress = { progress },
        modifier = modifier.fillMaxWidth().height(2.dp),
        color = MaterialTheme.colorScheme.primary,
        trackColor = Color.Transparent,
        drawStopIndicator = {},
        gapSize = 0.dp,
    )
}
