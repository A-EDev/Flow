package io.github.aedev.flow.ui.screens.music.components

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
import io.github.aedev.flow.ui.components.music.header.MusicSectionAction
import io.github.aedev.flow.ui.components.music.header.MusicSectionHeader
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem

@Composable
fun MediaTrackListSection(
    title: String,
    tracks: List<MusicTrack>,
    downloadedTrackIds: Set<String> = emptySet(),
    onPlayAll: () -> Unit,
    onTrackClick: (MusicTrack) -> Unit,
    onTrackMenu: (MusicTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        MusicSectionHeader(title = title, action = MusicSectionAction.PlayAll(onPlayAll))
        LazyHorizontalGrid(
            rows = GridCells.Fixed(4),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(336.dp)
                    .padding(bottom = 12.dp),
        ) {
            items(tracks.take(16), key = { it.videoId }) { track ->
                MusicTrackItem(
                    track = track,
                    isDownloaded = downloadedTrackIds.contains(track.videoId),
                    onClick = { onTrackClick(track) },
                    onLongClick = { onTrackMenu(track) },
                    onMenuClick = { onTrackMenu(track) },
                    thumbnailWidth = 96.dp,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.width(360.dp),
                )
            }
        }
    }
}
