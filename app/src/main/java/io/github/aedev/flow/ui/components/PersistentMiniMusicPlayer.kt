package io.github.aedev.flow.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.theme.Dimensions
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersistentMiniMusicPlayer(
    onExpandClick: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    val playerState by EnhancedMusicPlayerManager.playerState.collectAsState()
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberFlowReduceMotion()

    var offsetX by remember { mutableFloatStateOf(0f) }
    var isDismissing by remember { mutableStateOf(false) }

    val animatedProgress by animateFloatAsState(
        targetValue =
            if (playerState.duration > 0) {
                (playerState.position.toFloat() / playerState.duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            },
        animationSpec = tween(900, easing = LinearEasing),
        label = "progress",
    )

    var playPauseScale by remember { mutableFloatStateOf(1f) }
    val animatedScale by animateFloatAsState(
        targetValue = playPauseScale,
        animationSpec =
            tween(
                FlowMotion.durationFor(FlowMotion.ExitDurationMillis, reduceMotion),
                easing = FlowMotion.ExitEasing,
            ),
        label = "scale",
    )

    // Dismiss offset animation
    val dismissAlpha by animateFloatAsState(
        targetValue =
            if (kotlin.math.abs(offsetX) > 80) {
                1f - (kotlin.math.abs(offsetX) - 80f) / 200f
            } else {
                1f
            },
        animationSpec = tween(50),
        label = "dismiss_alpha",
    )

    AnimatedVisibility(
        visible = currentTrack != null && !isDismissing,
        enter =
            slideInVertically(
                initialOffsetY = { it },
                animationSpec =
                    tween(
                        FlowMotion.durationFor(FlowMotion.EnterDurationMillis, reduceMotion),
                        easing = FlowMotion.EnterEasing,
                    ),
            ) + fadeIn(
                animationSpec =
                    tween(
                        FlowMotion.durationFor(FlowMotion.EnterDurationMillis, reduceMotion),
                        easing = FlowMotion.EnterEasing,
                    ),
            ),
        exit =
            slideOutVertically(
                targetOffsetY = { it },
                animationSpec =
                    tween(
                        FlowMotion.durationFor(FlowMotion.ExitDurationMillis, reduceMotion),
                        easing = FlowMotion.ExitEasing,
                    ),
            ) + fadeOut(
                animationSpec =
                    tween(
                        FlowMotion.durationFor(FlowMotion.ExitDurationMillis, reduceMotion),
                        easing = FlowMotion.ExitEasing,
                    ),
            ),
        modifier = modifier,
    ) {
        currentTrack?.let { track ->
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                        .offset { IntOffset(offsetX.roundToInt(), 0) }
                        .graphicsLayer { alpha = dismissAlpha.coerceIn(0f, 1f) }
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    if (kotlin.math.abs(offsetX) > 140) {
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
                // Container
                Surface(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(Dimensions.MiniPlayerHeight)
                            .shadow(
                                16.dp,
                                RoundedCornerShape(Dimensions.CardCornerRadius),
                                ambientColor = Color.Black.copy(alpha = 0.5f),
                                spotColor = Color.Black.copy(alpha = 0.5f),
                            ).clickable(onClick = onExpandClick),
                    shape = RoundedCornerShape(Dimensions.CardCornerRadius),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = track.listThumbnailUrl,
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .blur(40.dp),
                            contentScale = ContentScale.Crop,
                            alpha = 0.25f,
                        )

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                        )

                        // Progress
                        val progressTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
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
                                            size = Size(size.width * animatedProgress, size.height),
                                        )
                                    },
                        )

                        // Content row
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 8.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            // Album art + info
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Album art
                                Box(
                                    modifier =
                                        Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(Dimensions.ThumbnailCornerRadius)),
                                ) {
                                    AsyncImage(
                                        model = track.listThumbnailUrl,
                                        contentDescription = stringResource(R.string.album_art),
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .clip(RoundedCornerShape(Dimensions.ThumbnailCornerRadius)),
                                        contentScale = ContentScale.Crop,
                                    )

                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxSize()
                                                .border(
                                                    1.dp,
                                                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                                    RoundedCornerShape(Dimensions.ThumbnailCornerRadius),
                                                ),
                                    )

                                    if (playerState.isPlaying) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(48.dp)
                                                    .background(Color.Black.copy(alpha = 0.35f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            MiniWaveform()
                                        }
                                    }
                                }

                                // Track info
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.Center,
                                ) {
                                    Text(
                                        text = track.title,
                                        style =
                                            MaterialTheme.typography.titleMedium.copy(
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                letterSpacing = (-0.2).sp,
                                            ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = track.artist,
                                        style =
                                            MaterialTheme.typography.bodyMedium.copy(
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }

                            // Controls
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Play/Pause
                                Box(
                                    modifier =
                                        Modifier
                                            .size(44.dp)
                                            .scale(animatedScale)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary)
                                            .clickable {
                                                playPauseScale = 0.85f
                                                EnhancedMusicPlayerManager.togglePlayPause()
                                                scope.launch {
                                                    delay(100)
                                                    playPauseScale = 1f
                                                }
                                            },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (playerState.isBuffering) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    } else {
                                        Icon(
                                            imageVector =
                                                if (playerState.isPlaying) {
                                                    Icons.Filled.Pause
                                                } else {
                                                    Icons.Filled.PlayArrow
                                                },
                                            contentDescription =
                                                if (playerState.isPlaying) {
                                                    stringResource(
                                                        R.string.pause,
                                                    )
                                                } else {
                                                    stringResource(R.string.play)
                                                },
                                            modifier = Modifier.size(24.dp),
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }

                                // Next
                                IconButton(
                                    onClick = { EnhancedMusicPlayerManager.playNext() },
                                    modifier = Modifier.size(38.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.SkipNext,
                                        contentDescription = stringResource(R.string.next),
                                        modifier = Modifier.size(22.dp),
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

/** Narrower, three-bar variant of [PlayingWaveform] that fits over the 48 dp album art. */
@Composable
private fun MiniWaveform() {
    PlayingWaveform(
        color = Color.White.copy(alpha = 0.9f),
        barCount = 3,
        barWidth = 2.5.dp,
        barSpacing = 1.5.dp,
        staggerMillis = 120,
    )
}
