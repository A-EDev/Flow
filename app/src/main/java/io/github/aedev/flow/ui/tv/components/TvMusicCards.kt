package io.github.aedev.flow.ui.tv.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.tv.focus.rememberTvFocusState
import io.github.aedev.flow.ui.tv.focus.tvFocusScale
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import io.github.aedev.flow.utils.formatDuration

/** Square-art music card for TV shelves — flat, matching [TvVideoCard]. */
@Composable
fun TvMusicCard(
    track: MusicTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFlatSquareCard(
        title = track.title,
        subtitle = track.artist,
        thumbnailUrl = track.thumbnailUrl,
        onClick = onClick,
        modifier = modifier,
    )
}

/** Album / playlist card for TV music shelves — opens the collection page. */
@Composable
fun TvMusicCollectionCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvFlatSquareCard(
        title = title,
        subtitle = subtitle,
        thumbnailUrl = thumbnailUrl,
        onClick = onClick,
        modifier = modifier,
    )
}

@Composable
private fun TvFlatSquareCard(
    title: String,
    subtitle: String?,
    thumbnailUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalTvDimens.current
    val focusState = rememberTvFocusState()

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(dimens.musicCardWidth)
            .tvFocusScale(focusState),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = if (focusState.isFocused) {
                    BorderStroke(dimens.focusBorderWidth, MaterialTheme.colorScheme.onSurface)
                } else {
                    null
                },
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Circular-avatar artist card for TV music shelves. */
@Composable
fun TvArtistCard(
    name: String,
    thumbnailUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalTvDimens.current
    val focusState = rememberTvFocusState()

    Surface(
        onClick = onClick,
        modifier = modifier
            .width(dimens.musicCardWidth)
            .tvFocusScale(focusState),
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = if (focusState.isFocused) {
                    BorderStroke(dimens.focusBorderWidth, MaterialTheme.colorScheme.onSurface)
                } else {
                    null
                },
            ) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Focusable track row for collection pages and other TV track lists. */
@Composable
fun TvMusicTrackRow(
    track: MusicTrack,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                AsyncImage(
                    model = track.listThumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleMedium,
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
            if (track.duration > 0) {
                Text(
                    text = formatDuration(track.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
