package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.VideoThumbnailImage
import io.github.aedev.flow.ui.tv.focus.rememberTvFocusState
import io.github.aedev.flow.ui.tv.focus.tvFocusScale
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import io.github.aedev.flow.utils.formatDuration
import io.github.aedev.flow.utils.formatTimeAgo
import io.github.aedev.flow.utils.formatViewCount

/**
 * Flat ten-foot video card, YouTube-TV style: no container surface — a rounded
 * 16:9 thumbnail carrying the focus ring, with title and metadata sitting
 * directly on the screen background beneath it.
 */
@Composable
fun TvVideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    watchProgress: Float? = null,
) {
    val dimens = LocalTvDimens.current
    val focusState = rememberTvFocusState()
    val focused = focusState.isFocused
    val viewsTemplate = if (video.viewCount > 0) {
        stringResource(R.string.views_template, formatViewCount(video.viewCount))
    } else {
        null
    }
    val metadata = remember(video.id, video.channelName, viewsTemplate, video.uploadDate) {
        listOfNotNull(
            video.channelName.takeIf { it.isNotBlank() },
            viewsTemplate,
            formatTimeAgo(video.uploadDate).takeIf { it.isNotBlank() },
        ).joinToString(separator = " • ")
    }

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(dimens.videoCardWidth)
            .tvFocusScale(focusState),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                // Focus indicator, not decoration: onSurface gives the
                // high-contrast neutral ring a thumbnail needs at 10 ft.
                border = if (focused) {
                    BorderStroke(dimens.focusBorderWidth, MaterialTheme.colorScheme.onSurface)
                } else {
                    null
                },
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    VideoThumbnailImage(
                        videoId = video.id,
                        model = video.thumbnailUrl,
                        contentDescription = video.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    when {
                        video.isLive -> Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ) {
                            Text(
                                text = stringResource(R.string.status_live),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        video.duration > 0 -> Surface(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp),
                            shape = MaterialTheme.shapes.extraSmall,
                            color = Color.Black.copy(alpha = 0.7f),
                            contentColor = Color.White,
                        ) {
                            Text(
                                text = formatDuration(video.duration),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    watchProgress?.let { progress ->
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(4.dp),
                            trackColor = Color.Black.copy(alpha = 0.4f),
                            drawStopIndicator = {},
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = metadata,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
