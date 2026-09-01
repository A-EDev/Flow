package io.github.aedev.flow.ui.components.musicplayer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.PlayingWaveform
import io.github.aedev.flow.ui.screens.music.MusicTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
internal fun MiniPlayerContent(
    track: MusicTrack,
    modifier: Modifier = Modifier,
    // False while the expanded player covers the (alpha-0) mini bar: the waveform, marquee
    // and smooth progress ring stop forcing frames nobody can see. Values still update.
    animationsEnabled: Boolean = true,
) {
    val playerState by EnhancedMusicPlayerManager.playerState.collectAsState()
    val scope = rememberCoroutineScope()

    val animatedProgress by animateFloatAsState(
        targetValue =
            if (playerState.duration > 0) {
                (playerState.position.toFloat() / playerState.duration.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            },
        animationSpec = if (animationsEnabled) tween(900, easing = LinearEasing) else snap(),
        label = "miniProgress",
    )

    var playPauseScale by remember { mutableFloatStateOf(1f) }
    val animatedScale by animateFloatAsState(
        targetValue = playPauseScale,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = 400f),
        label = "miniPlayPauseScale",
    )

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val ringTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
                val ringFillColor = MaterialTheme.colorScheme.primary
                Box(
                    modifier =
                        Modifier
                            .size(52.dp)
                            .drawBehind {
                                val stroke = 2.5.dp.toPx()
                                val inset = stroke / 2f
                                val arcSize = Size(size.width - stroke, size.height - stroke)
                                drawArc(
                                    color = ringTrackColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = Offset(inset, inset),
                                    size = arcSize,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                                )
                                if (animatedProgress > 0f) {
                                    drawArc(
                                        color = ringFillColor,
                                        startAngle = -90f,
                                        sweepAngle = 360f * animatedProgress,
                                        useCenter = false,
                                        topLeft = Offset(inset, inset),
                                        size = arcSize,
                                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                                    )
                                }
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(43.dp)
                                .clip(CircleShape),
                    ) {
                        AsyncImage(
                            model = track.listThumbnailUrl,
                            contentDescription = stringResource(R.string.album_art),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )

                        if (playerState.isPlaying && animationsEnabled) {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                PlayingWaveform(
                                    color = Color.White.copy(alpha = 0.9f),
                                    barCount = 3,
                                    barWidth = 2.5.dp,
                                    barSpacing = 1.5.dp,
                                    staggerMillis = 120,
                                )
                            }
                        }
                    }
                }

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
                        color = MaterialTheme.colorScheme.onSurface,
                        overflow = TextOverflow.Ellipsis,
                        modifier =
                            if (animationsEnabled) {
                                Modifier.basicMarquee(
                                    iterations = Int.MAX_VALUE,
                                    repeatDelayMillis = 2500,
                                )
                            } else {
                                Modifier
                            },
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                                    stringResource(R.string.pause)
                                } else {
                                    stringResource(R.string.play)
                                },
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }

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
