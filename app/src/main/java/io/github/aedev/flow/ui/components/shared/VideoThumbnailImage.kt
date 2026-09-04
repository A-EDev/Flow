package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import io.github.aedev.flow.utils.ThumbnailUrlResolver

@Composable
fun VideoThumbnailImage(
    videoId: String,
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val models =
        remember(videoId, model) {
            when {
                model is String || model == null -> {
                    ThumbnailUrlResolver.resolveVideoThumbnailCandidates(videoId, model as? String)
                }

                else -> {
                    listOf(model)
                }
            }
        }

    SafeAsyncImage(
        models = models,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@Composable
private fun SafeAsyncImage(
    models: List<Any>,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    var index by remember(models) { mutableStateOf(0) }
    val currentModel = models.getOrNull(index)

    when {
        currentModel is ImageVector -> {
            Image(
                imageVector = currentModel,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
            )
        }

        (currentModel is String && currentModel.isNotEmpty()) || currentModel is Int -> {
            AsyncImage(
                model = currentModel,
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = contentScale,
                onError = {
                    index = if (index < models.lastIndex) index + 1 else models.size
                },
            )
        }

        else -> {
            Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}
