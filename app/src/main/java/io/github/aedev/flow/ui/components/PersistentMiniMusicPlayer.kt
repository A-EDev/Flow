package io.github.aedev.flow.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.theme.Dimensions
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import kotlin.math.abs
import kotlin.math.roundToInt

private enum class MiniPlaybackControlState {
    BUFFERING,
    PLAYING,
    PAUSED,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersistentMiniMusicPlayer(
    onExpandClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsStateWithLifecycle()
    val playerState by EnhancedMusicPlayerManager.playerState.collectAsStateWithLifecycle()
    val reduceMotion = rememberFlowReduceMotion()
    val density = LocalDensity.current
    val fadeStartPx = with(density) { 80.dp.toPx() }
    val fadeRangePx = with(density) { 200.dp.toPx() }
    val dismissThresholdPx = with(density) { 140.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    LaunchedEffect(currentTrack?.videoId) {
        if (currentTrack != null) {
            isDismissing = false
            offsetX = 0f
        }
    }

    val animatedProgress =
        animateFloatAsState(
            targetValue =
                if (playerState.duration > 0) {
                    (playerState.position.toFloat() / playerState.duration.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                },
            animationSpec =
                tween(
                    durationMillis = FlowMotion.durationFor(900, reduceMotion),
                    easing = LinearEasing,
                ),
            label = "miniPlayerProgress",
        )

    AnimatedVisibility(
        visible = currentTrack != null && !isDismissing,
        enter =
            slideInVertically(
                initialOffsetY = { it },
                animationSpec =
                    tween(
                        FlowMotion.durationFor(FlowMotion.ENTER_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.EnterEasing,
                    ),
            ) +
                fadeIn(
                    animationSpec =
                        tween(
                            FlowMotion.durationFor(FlowMotion.ENTER_DURATION_MILLIS, reduceMotion),
                            easing = FlowMotion.EnterEasing,
                        ),
                ),
        exit =
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec =
                    tween(
                        FlowMotion.durationFor(FlowMotion.EXIT_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.ExitEasing,
                    ),
            ) +
                fadeOut(
                    animationSpec =
                        tween(
                            FlowMotion.durationFor(FlowMotion.EXIT_DURATION_MILLIS, reduceMotion),
                            easing = FlowMotion.ExitEasing,
                        ),
                ),
        modifier = modifier,
        label = "miniPlayerVisibility",
    ) {
        currentTrack?.let { track ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .graphicsLayer {
                            val dragDistance = abs(offsetX)
                            alpha =
                                if (dragDistance > fadeStartPx) {
                                    (1f - (dragDistance - fadeStartPx) / fadeRangePx).coerceIn(0f, 1f)
                                } else {
                                    1f
                                }
                        }.pointerInput(dismissThresholdPx) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (abs(offsetX) > dismissThresholdPx) {
                                        isDismissing = true
                                        onDismiss()
                                    }
                                    offsetX = 0f
                                },
                                onHorizontalDrag = { _, dragAmount ->
                                    offsetX += dragAmount
                                },
                            )
                        },
            ) {
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(Dimensions.MiniPlayerHeight)
                            .clickable(onClick = onExpandClick),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val progressTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        val progressFillColor = MaterialTheme.colorScheme.primary
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .align(Alignment.BottomCenter)
                                    .drawBehind {
                                        drawRect(color = progressTrackColor)
                                        drawRect(
                                            color = progressFillColor,
                                            size = Size(size.width * animatedProgress.value, size.height),
                                        )
                                    },
                        )

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(48.dp)
                                            .clip(MaterialTheme.shapes.medium),
                                ) {
                                    AsyncImage(
                                        model = track.listThumbnailUrl,
                                        contentDescription = stringResource(R.string.album_art),
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )

                                    AnimatedVisibility(
                                        visible = playerState.isPlaying,
                                        modifier = Modifier.fillMaxSize(),
                                        enter =
                                            fadeIn(
                                                tween(
                                                    FlowMotion.durationFor(
                                                        FlowMotion.FEEDBACK_DURATION_MILLIS,
                                                        reduceMotion,
                                                    ),
                                                ),
                                            ),
                                        exit =
                                            fadeOut(
                                                tween(
                                                    FlowMotion.durationFor(
                                                        FlowMotion.FEEDBACK_DURATION_MILLIS,
                                                        reduceMotion,
                                                    ),
                                                ),
                                            ),
                                        label = "miniArtworkPlaying",
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            MiniWaveform()
                                        }
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = track.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = track.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val playPauseInteractionSource = remember { MutableInteractionSource() }
                                FilledIconButton(
                                    onClick = { EnhancedMusicPlayerManager.togglePlayPause() },
                                    modifier =
                                        Modifier
                                            .size(48.dp)
                                            .pressScale(playPauseInteractionSource),
                                    colors =
                                        IconButtonDefaults.filledIconButtonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    interactionSource = playPauseInteractionSource,
                                ) {
                                    val controlState =
                                        when {
                                            playerState.isBuffering -> MiniPlaybackControlState.BUFFERING
                                            playerState.isPlaying -> MiniPlaybackControlState.PLAYING
                                            else -> MiniPlaybackControlState.PAUSED
                                        }
                                    AnimatedContent(
                                        targetState = controlState,
                                        transitionSpec = {
                                            fadeIn(
                                                tween(
                                                    FlowMotion.durationFor(
                                                        FlowMotion.FEEDBACK_DURATION_MILLIS,
                                                        reduceMotion,
                                                    ),
                                                ),
                                            ).togetherWith(
                                                fadeOut(
                                                    tween(
                                                        FlowMotion.durationFor(
                                                            FlowMotion.FEEDBACK_DURATION_MILLIS,
                                                            reduceMotion,
                                                        ),
                                                    ),
                                                ),
                                            )
                                        },
                                        contentAlignment = Alignment.Center,
                                        label = "miniPlaybackControl",
                                    ) { state ->
                                        when (state) {
                                            MiniPlaybackControlState.BUFFERING -> {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(20.dp),
                                                    strokeWidth = 2.dp,
                                                    color = MaterialTheme.colorScheme.onPrimary,
                                                )
                                            }

                                            MiniPlaybackControlState.PLAYING -> {
                                                Icon(
                                                    imageVector = Icons.Filled.Pause,
                                                    contentDescription = stringResource(R.string.pause),
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }

                                            MiniPlaybackControlState.PAUSED -> {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = stringResource(R.string.play),
                                                    modifier = Modifier.size(24.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = { EnhancedMusicPlayerManager.playNext() },
                                    modifier = Modifier.size(48.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = stringResource(R.string.next),
                                        modifier = Modifier.size(24.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

@Composable
private fun MiniWaveform() {
    PlayingWaveform(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        barCount = 3,
        barWidth = 2.5.dp,
        barSpacing = 1.5.dp,
        staggerMillis = 120,
    )
}
