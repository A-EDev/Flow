package io.github.aedev.flow.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.Dimensions
import io.github.aedev.flow.ui.theme.FlowMotion
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

private enum class ThumbnailOverlayState {
    NONE,
    ACTIVE,
    PLAYING,
    SELECTED,
    INDEX,
}

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = Dimensions.ListThumbnailSize,
    shape: Shape = MaterialTheme.shapes.medium,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    albumIndex: Int? = null,
    thumbnailRatio: Float = 1f,
) {
    val reduceMotion = rememberFlowReduceMotion()
    val overlayState =
        when {
            isSelected -> ThumbnailOverlayState.SELECTED
            isPlaying -> ThumbnailOverlayState.PLAYING
            isActive -> ThumbnailOverlayState.ACTIVE
            albumIndex != null -> ThumbnailOverlayState.INDEX
            else -> ThumbnailOverlayState.NONE
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .aspectRatio(thumbnailRatio)
                .clip(shape),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedContent(
            targetState = overlayState,
            transitionSpec = {
                fadeIn(
                    tween(
                        durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                        easing = FlowMotion.EnterEasing,
                    ),
                ).togetherWith(
                    fadeOut(
                        tween(
                            durationMillis = FlowMotion.durationFor(FlowMotion.FEEDBACK_DURATION_MILLIS, reduceMotion),
                            easing = FlowMotion.ExitEasing,
                        ),
                    ),
                )
            },
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
            label = "thumbnailOverlay",
        ) { state ->
            when (state) {
                ThumbnailOverlayState.NONE -> {
                    Unit
                }

                ThumbnailOverlayState.ACTIVE -> {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    )
                }

                ThumbnailOverlayState.PLAYING -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)),
                    ) {
                        PlayingWaveAnimation()
                    }
                }

                ThumbnailOverlayState.SELECTED -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primaryContainer),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.ui_selected),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(size / 2),
                        )
                    }
                }

                ThumbnailOverlayState.INDEX -> {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
                    ) {
                        Text(
                            text = albumIndex?.toString().orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayingWaveAnimation(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    PlayingWaveform(modifier = modifier, color = color)
}

@Composable
fun ArtistThumbnail(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = Dimensions.ListThumbnailSize,
) {
    ItemThumbnail(
        thumbnailUrl = thumbnailUrl,
        size = size,
        shape = CircleShape,
        modifier = modifier,
    )
}
