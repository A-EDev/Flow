package io.github.aedev.flow.ui.components

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.aedev.flow.R

internal const val CHANNEL_BANNER_ASPECT_RATIO = 1060f / 175f

@Composable
internal fun ChannelBanner(
    imageUrl: String?,
    modifier: Modifier = Modifier
) {
    val bannerModifier = modifier
        .padding(start = 16.dp, end = 16.dp, top = 12.dp)
        .fillMaxWidth()
        .aspectRatio(CHANNEL_BANNER_ASPECT_RATIO)
        .clip(MaterialTheme.shapes.medium)
        .background(MaterialTheme.colorScheme.surfaceVariant)

    if (imageUrl.isNullOrBlank()) {
        androidx.compose.foundation.layout.Box(modifier = bannerModifier)
        return
    }

    AsyncImage(
        model = imageUrl,
        contentDescription = stringResource(R.string.channel_banner),
        modifier = bannerModifier,
        contentScale = ContentScale.Fit,
        onError = { result ->
            Log.e(
                "ChannelBanner",
                "Banner load failed for $imageUrl: ${result.result.throwable.message}"
            )
        }
    )
}
