package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

object MediaThumbnailDefaults {
    val VideoWidth: Dp = 152.dp
    val ArtworkSize: Dp = 56.dp
    val VideoAspectRatio: Float = 16f / 9f
    val BadgePadding: Dp = 6.dp
    val ProgressHeight: Dp = 3.dp
    val PlaceholderSize: Dp = 24.dp
    const val PLACEHOLDER_ALPHA = 0.4f
}

@Composable
fun MediaThumbnail(
    videoId: String,
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    width: Dp = MediaThumbnailDefaults.VideoWidth,
    shape: Shape = MaterialTheme.shapes.small,
    durationSeconds: Int? = null,
    showWatchProgress: Boolean = false,
    placeholder: ImageVector? = null,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .width(width)
                .aspectRatio(MediaThumbnailDefaults.VideoAspectRatio)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (placeholder != null) {
            Icon(
                imageVector = placeholder,
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = MediaThumbnailDefaults.PLACEHOLDER_ALPHA),
                modifier = Modifier.size(MediaThumbnailDefaults.PlaceholderSize),
            )
        }

        VideoThumbnailImage(
            videoId = videoId,
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        if (durationSeconds != null && durationSeconds > 0) {
            DurationBadge(
                seconds = durationSeconds,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(MediaThumbnailDefaults.BadgePadding),
            )
        }

        if (showWatchProgress) {
            ThumbnailWatchProgress(
                videoId = videoId,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(MediaThumbnailDefaults.ProgressHeight),
            )
        }

        overlay()
    }
}

@Composable
fun ArtworkThumbnail(
    thumbnailUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = MediaThumbnailDefaults.ArtworkSize,
    shape: Shape = MaterialTheme.shapes.small,
    placeholder: ImageVector? = null,
) {
    Box(
        modifier =
            modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (placeholder != null) {
            Icon(
                imageVector = placeholder,
                contentDescription = null,
                tint =
                    MaterialTheme.colorScheme.onSurfaceVariant
                        .copy(alpha = MediaThumbnailDefaults.PLACEHOLDER_ALPHA),
                modifier = Modifier.size(MediaThumbnailDefaults.PlaceholderSize),
            )
        }

        if (thumbnailUrl != null) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}
