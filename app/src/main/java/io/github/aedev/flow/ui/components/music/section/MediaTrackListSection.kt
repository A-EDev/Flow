package io.github.aedev.flow.ui.components.music.section

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.ui.components.music.common.musicLaneItemWidth
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction
import io.github.aedev.flow.ui.components.music.header.MusicSectionHeader
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.theme.Dimensions

private val MediaRowMaxWidth = 380.dp
private val MediaRowPeek = 48.dp
private val MediaThumbnailWidth = 96.dp
private const val MEDIA_ROWS = 4
private const val MEDIA_MAX_TRACKS = 16

@Composable
fun MediaTrackListSection(
    title: String,
    tracks: List<MusicTrack>,
    onPlayAll: () -> Unit,
    onTrackClick: (MusicTrack) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
    downloadedTrackIds: Set<String> = emptySet(),
) {
    val rowWidth = musicLaneItemWidth(maxWidth = MediaRowMaxWidth, peek = MediaRowPeek)

    Column(modifier = modifier.fillMaxWidth()) {
        MusicSectionHeader(title = title, action = MusicSectionAction.PlayAll(onPlayAll))
        LazyHorizontalGrid(
            rows = GridCells.Fixed(MEDIA_ROWS),
            contentPadding = PaddingValues(horizontal = Dimensions.ContentPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(Dimensions.ItemSpacing),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(336.dp)
                    .padding(bottom = Dimensions.ContentPaddingVertical),
        ) {
            items(tracks.take(MEDIA_MAX_TRACKS), key = { it.videoId }) { track ->
                MusicTrackItem(
                    track = track,
                    isDownloaded = downloadedTrackIds.contains(track.videoId),
                    onClick = { onTrackClick(track) },
                    onLongClick = { onTrackMenu(track) },
                    onMenuClick = { onTrackMenu(track) },
                    thumbnailWidth = MediaThumbnailWidth,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.width(rowWidth),
                )
            }
        }
    }
}
