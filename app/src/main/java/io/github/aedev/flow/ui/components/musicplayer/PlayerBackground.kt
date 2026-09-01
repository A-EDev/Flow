package io.github.aedev.flow.ui.components.musicplayer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle

@Composable
fun PlayerBackground(
    thumbnailUrl: String?,
    style: MusicPlayerBackgroundStyle,
    paletteBaseColor: Color,
    paletteAccentColor: Color,
    modifier: Modifier = Modifier,
) {
    val baseColor = paletteBaseColor.copy(alpha = 0.78f)
    val accentColor = paletteAccentColor.copy(alpha = 0.72f)

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    if (style == MusicPlayerBackgroundStyle.DEFAULT) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Black
                    },
                ),
    ) {
        when (style) {
            MusicPlayerBackgroundStyle.DEFAULT -> {
                Unit
            }

            MusicPlayerBackgroundStyle.BLUR -> {
                BlurredArtworkLayer(thumbnailUrl = thumbnailUrl, alpha = 0.62f)
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.58f)),
                )
            }

            MusicPlayerBackgroundStyle.GRADIENT -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops =
                                        arrayOf(
                                            0.00f to accentColor,
                                            0.38f to baseColor,
                                            0.72f to Color.Black.copy(alpha = 0.88f),
                                            1.00f to Color.Black,
                                        ),
                                ),
                            ),
                )
            }

            MusicPlayerBackgroundStyle.IMMERSIVE -> {
                // Issue #954: full-bleed artwork holds the top of the screen, then hands off to
                // its own blurred continuation and a scrim so the controls stay readable.
                BlurredArtworkLayer(thumbnailUrl = thumbnailUrl, alpha = 0.85f, crossfadeMillis = 450)
                ImmersiveArtworkLayer(thumbnailUrl = thumbnailUrl)
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops =
                                        arrayOf(
                                            0.00f to Color.Transparent,
                                            0.52f to Color.Black.copy(alpha = 0.28f),
                                            1.00f to Color.Black.copy(alpha = 0.86f),
                                        ),
                                ),
                            ),
                )
            }

            MusicPlayerBackgroundStyle.BLUR_GRADIENT -> {
                BlurredArtworkLayer(thumbnailUrl = thumbnailUrl, alpha = 0.55f)
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops =
                                        arrayOf(
                                            0.00f to Color.Black.copy(alpha = 0.40f),
                                            0.30f to accentColor.copy(alpha = 0.22f),
                                            0.55f to baseColor.copy(alpha = 0.34f),
                                            0.80f to Color.Black.copy(alpha = 0.80f),
                                            1.00f to Color.Black.copy(alpha = 0.95f),
                                        ),
                                ),
                            ),
                )
            }
        }
    }
}

@Composable
private fun ImmersiveArtworkLayer(thumbnailUrl: String?) {
    AnimatedContent(
        targetState = thumbnailUrl,
        transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
        label = "playerImmersiveArt",
    ) { targetUrl ->
        Box(modifier = Modifier.fillMaxSize()) {
            // A full-width square window with minimal crop (no full-screen zoom, so covers with
            // their own borders don't blow up into a blank band); the bottom quarter of the ART
            // dissolves into the blurred continuation below it.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        0.72f to Color.Black,
                                        1.00f to Color.Transparent,
                                    ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
            ) {
                AsyncImage(
                    model = targetUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun BlurredArtworkLayer(
    thumbnailUrl: String?,
    alpha: Float,
    crossfadeMillis: Int = 800,
) {
    AnimatedContent(
        targetState = thumbnailUrl,
        transitionSpec = { fadeIn(tween(crossfadeMillis)) togetherWith fadeOut(tween(crossfadeMillis)) },
        label = "playerBackgroundArt",
    ) { targetUrl ->
        AsyncImage(
            model = targetUrl,
            contentDescription = null,
            modifier =
                Modifier
                    .fillMaxSize()
                    .blur(150.dp),
            alpha = alpha,
            contentScale = ContentScale.Crop,
        )
    }
}
