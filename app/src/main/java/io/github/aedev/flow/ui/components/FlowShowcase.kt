package io.github.aedev.flow.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion
import io.github.aedev.flow.utils.formatDuration

@Composable
fun FlowFeaturedVideoCard(
    video: Video,
    onClick: () -> Unit,
    onChannelClick: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val channelInteractionSource = remember { MutableInteractionSource() }
    val watchProgress = rememberWatchProgress(video.id)
    val cardPreferences = LocalVideoCardPreferences.current
    val deArrowResult = rememberDeArrowResult(video.id, cardPreferences.deArrowEnabled)
    val displayTitle = deArrowResult?.title ?: video.title
    val displayThumbnailUrl = deArrowResult?.thumbnailUrl ?: video.thumbnailUrl
    val reduceMotion = rememberFlowReduceMotion()
    val cardShape = MaterialTheme.shapes.extraLarge

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pressScale(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                ).testTag("flow_featured_video"),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.38f),
        ) {
            VideoThumbnailImage(
                videoId = video.id,
                model = displayThumbnailUrl,
                contentDescription = displayTitle,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            Surface(
                modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.label_featured),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }

            if (video.isLive || video.duration > 0) {
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(14.dp),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Text(
                        text = if (video.isLive) stringResource(R.string.status_live) else formatDuration(video.duration),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    )
                }
            }

            Surface(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = video.channelName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .clickable(
                                        enabled = onChannelClick != null && video.channelId.isNotBlank(),
                                        interactionSource = channelInteractionSource,
                                        indication = ripple(),
                                        role = Role.Button,
                                        onClick = { onChannelClick?.invoke(video.channelId) },
                                    ).padding(vertical = 8.dp),
                        )
                    }

                    FilledIconButton(
                        onClick = onClick,
                        modifier = Modifier.size(56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = watchProgress != null,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter =
                    fadeIn(
                        animationSpec =
                            tween(
                                durationMillis =
                                    FlowMotion.durationFor(
                                        FlowMotion.FEEDBACK_DURATION_MILLIS,
                                        reduceMotion,
                                    ),
                                easing = FlowMotion.EnterEasing,
                            ),
                    ),
                exit =
                    fadeOut(
                        animationSpec =
                            tween(
                                durationMillis =
                                    FlowMotion.durationFor(
                                        FlowMotion.FEEDBACK_DURATION_MILLIS,
                                        reduceMotion,
                                    ),
                                easing = FlowMotion.ExitEasing,
                            ),
                    ),
                label = "featuredWatchProgress",
            ) {
                LinearProgressIndicator(
                    progress = { watchProgress?.coerceIn(0f, 1f) ?: 0f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
            }
        }
    }
}

@Composable
fun FlowMusicSpotlightCard(
    track: MusicTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val cardShape = MaterialTheme.shapes.extraLarge

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .pressScale(interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onClick = onClick,
                ).testTag("flow_music_spotlight"),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.7f),
        ) {
            AsyncImage(
                model = track.highResThumbnailUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            Surface(
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .fillMaxWidth(0.64f)
                        .padding(12.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.music_player),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = track.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track.artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    FilledIconButton(
                        onClick = onClick,
                        modifier = Modifier.size(56.dp),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = stringResource(R.string.play),
                        )
                    }
                }
            }
        }
    }
}
