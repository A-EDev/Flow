package io.github.aedev.flow.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.Dimensions

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = Dimensions.ListThumbnailSize,
    shape: Shape = RoundedCornerShape(Dimensions.ThumbnailCornerRadius),
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSelected: Boolean = false,
    albumIndex: Int? = null,
    thumbnailRatio: Float = 1f,
) {
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

        if (isActive || isPlaying) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                if (isPlaying) {
                    PlayingWaveAnimation()
                }
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = stringResource(R.string.ui_selected),
                    tint = Color.White,
                    modifier = Modifier.size(size / 2),
                )
            }
        }

        if (albumIndex != null && !isActive && !isPlaying && !isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
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

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(Dimensions.ThumbnailCornerRadius),
) {
    Surface(
        shape = shape,
        modifier = modifier.size(size),
    ) {
        when {
            thumbnails.isEmpty() -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }

            thumbnails.size == 1 -> {
                AsyncImage(
                    model = thumbnails.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    val gridThumbs = thumbnails.take(4)
                    val halfSize = size / 2

                    gridThumbs.forEachIndexed { index, url ->
                        val xOffset = if (index % 2 == 0) 0.dp else halfSize
                        val yOffset = if (index < 2) 0.dp else halfSize

                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(halfSize),
                        )
                    }
                }
            }
        }
    }
}
