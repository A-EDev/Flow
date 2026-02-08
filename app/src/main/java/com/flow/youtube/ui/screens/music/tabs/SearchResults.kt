package com.flow.youtube.ui.screens.music.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import androidx.compose.ui.unit.dp
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.components.CompactTrackCard

@Composable
fun SearchResults(
    songs: List<MusicTrack>,
    isSearching: Boolean,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onArtistClick: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            isSearching -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            songs.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.no_results_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(songs.size) { index ->
                        CompactTrackCard(
                            track = songs[index],
                            onClick = { onSongClick(songs[index], songs, "search_results") },
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }
        }
    }
}
