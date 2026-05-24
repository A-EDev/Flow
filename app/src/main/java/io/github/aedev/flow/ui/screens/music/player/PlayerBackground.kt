package io.github.aedev.flow.ui.screens.music.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.aedev.flow.data.local.MusicPlayerBackgroundStyle

@Composable
fun PlayerBackground(
    thumbnailUrl: String?,
    style: MusicPlayerBackgroundStyle,
    paletteBaseColor: Color,
    paletteAccentColor: Color,
    modifier: Modifier = Modifier
) {
    val baseColor = paletteBaseColor.copy(alpha = 0.78f)
    val accentColor = paletteAccentColor.copy(alpha = 0.72f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when (style) {
            MusicPlayerBackgroundStyle.DEFAULT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Black,
                                    0.45f to baseColor.copy(alpha = 0.22f),
                                    1.00f to Color.Black
                                )
                            )
                        )
                )
            }

            MusicPlayerBackgroundStyle.BLUR -> {
                BlurredArtworkLayer(thumbnailUrl = thumbnailUrl, alpha = 0.62f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.58f))
                )
            }

            MusicPlayerBackgroundStyle.GRADIENT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to accentColor,
                                    0.38f to baseColor,
                                    0.72f to Color.Black.copy(alpha = 0.88f),
                                    1.00f to Color.Black
                                )
                            )
                        )
                )
            }

            MusicPlayerBackgroundStyle.BLUR_GRADIENT -> {
                BlurredArtworkLayer(thumbnailUrl = thumbnailUrl, alpha = 0.55f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.00f to Color.Black.copy(alpha = 0.40f),
                                    0.30f to accentColor.copy(alpha = 0.22f),
                                    0.55f to baseColor.copy(alpha = 0.34f),
                                    0.80f to Color.Black.copy(alpha = 0.80f),
                                    1.00f to Color.Black.copy(alpha = 0.95f)
                                )
                            )
                        )
                )
            }
        }
    }
}

@Composable
private fun BlurredArtworkLayer(
    thumbnailUrl: String?,
    alpha: Float
) {
    AnimatedContent(
        targetState = thumbnailUrl,
        transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
        label = "playerBackgroundArt"
    ) { targetUrl ->
        AsyncImage(
            model = targetUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(150.dp),
            alpha = alpha,
            contentScale = ContentScale.Crop
        )
    }
}
