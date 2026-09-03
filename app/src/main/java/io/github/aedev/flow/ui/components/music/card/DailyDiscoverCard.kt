package io.github.aedev.flow.ui.components.music.card

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.github.aedev.flow.data.music.model.DailyDiscoverItem
import io.github.aedev.flow.ui.components.music.common.MusicDownloadedBadge
import io.github.aedev.flow.ui.components.music.common.MusicNowPlayingOverlay
import io.github.aedev.flow.ui.components.music.common.isTrackPlaying

val DailyDiscoverCaptionHeight = 72.dp

/**
 * One Daily Discover carousel item: artwork on top, caption on a solid container below it.
 *
 * [captionAlpha] is read in the draw phase only, so a carousel can fade the caption out as the
 * item shrinks into a preview without recomposing anything while it scrolls.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DailyDiscoverCard(
    item: DailyDiscoverItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDownloaded: Boolean = false,
    captionAlpha: () -> Float = { 1f },
) {
    val track = item.recommendation
    val isPlaying = isTrackPlaying(track.videoId)

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        ) {
            AsyncImage(
                model = track.highResThumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (isPlaying) {
                MusicNowPlayingOverlay()
            }
            if (isDownloaded) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                ) {
                    MusicDownloadedBadge(modifier = Modifier.padding(6.dp))
                }
            }
        }
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(DailyDiscoverCaptionHeight)
                    .graphicsLayer { alpha = captionAlpha() }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
