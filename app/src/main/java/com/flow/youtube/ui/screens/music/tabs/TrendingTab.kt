package com.flow.youtube.ui.screens.music.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import androidx.compose.ui.unit.dp
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.components.TrendingTrackCard

@Composable
fun TrendingTab(
    songs: List<MusicTrack>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onArtistClick: (String) -> Unit,
    onMenuClick: (MusicTrack) -> Unit
) {
    if (songs.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.no_tracks_available),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp), // Space for mini player
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(songs.size) { index ->
            TrendingTrackCard(
                track = songs[index],
                rank = index + 1,
                onClick = { onSongClick(songs[index], songs, "trending") },
                onArtistClick = onArtistClick,
                onMenuClick = { onMenuClick(songs[index]) }
            )
        }
    }
}
