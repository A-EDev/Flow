package io.github.aedev.flow.ui.components.musicplayer.mini

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.ui.components.PlayingWaveform
import io.github.aedev.flow.ui.theme.ArtworkScrimContent
import io.github.aedev.flow.ui.theme.ArtworkScrimNowPlaying

private val ArtworkRingSize = 52.dp
private val ArtworkSize = 43.dp
private val ProgressRingStroke = 2.5.dp
private val PlayButtonSize = 48.dp

/**
 * The collapsed player: cover in a progress ring, title and artist, then the transport. Wider
 * windows give the card room for Previous as well.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MiniPlayerContent(
    track: MusicTrack,
    modifier: Modifier = Modifier,
    // False while the expanded player covers the (alpha-0) mini bar: the waveform, marquee
    // and smooth progress ring stop forcing frames nobody can see. Values still update.
    animationsEnabled: Boolean = true,
    showPrevious: Boolean = false,
) {
    val playerState by EnhancedMusicPlayerManager.playerState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current

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

    Row(
        modifier =
            modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val ringTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
        val ringFillColor = MaterialTheme.colorScheme.primary
        Box(
            modifier =
                Modifier
                    .size(ArtworkRingSize)
                    .drawBehind {
                        val stroke = ProgressRingStroke.toPx()
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
                        .size(ArtworkSize)
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
                                .background(ArtworkScrimNowPlaying),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayingWaveform(
                            color = ArtworkScrimContent,
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
                style = MaterialTheme.typography.titleSmall,
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
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showPrevious) {
                IconButton(onClick = { EnhancedMusicPlayerManager.playPrevious() }) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.previous),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FilledIconButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    EnhancedMusicPlayerManager.togglePlayPause()
                },
                // Rounded square while playing, circle while paused, as the full player's button.
                shapes =
                    IconButtonDefaults.shapes(
                        shape = if (playerState.isPlaying) IconButtonDefaults.mediumSquareShape else IconButtonDefaults.mediumRoundShape,
                    ),
                modifier = Modifier.size(PlayButtonSize),
            ) {
                if (playerState.isBuffering && animationsEnabled) {
                    LoadingIndicator(
                        modifier = Modifier.size(28.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = if (playerState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(if (playerState.isPlaying) R.string.pause else R.string.play),
                    )
                }
            }
            IconButton(onClick = { EnhancedMusicPlayerManager.playNext() }) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = stringResource(R.string.next),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
