package com.flow.youtube.ui.screens.music.tabs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.components.FeaturedTrackCard
import com.flow.youtube.ui.screens.music.components.GenreSection

@Composable
fun DiscoverTab(
    trendingSongs: List<MusicTrack>,
    genreTracks: Map<String, List<MusicTrack>>,
    onSongClick: (MusicTrack, List<MusicTrack>, String?) -> Unit,
    onGenreClick: (String) -> Unit,
    onArtistClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 80.dp), // Space for mini player
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Hero section - Featured/Top picks
        item {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Top Picks for You",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp)
                ) {
                    items(trendingSongs.take(5)) { track ->
                        FeaturedTrackCard(
                            track = track,
                            onClick = { onSongClick(track, trendingSongs, "top_picks") },
                            onArtistClick = onArtistClick
                        )
                    }
                }
            }
        }

        // Genre sections
        genreTracks.forEach { (genre, tracks) ->
            if (tracks.isNotEmpty()) {
                item {
                    GenreSection(
                        genre = genre,
                        tracks = tracks,
                        onSongClick = onSongClick,
                        onSeeAllClick = { onGenreClick(genre) }
                    )
                }
            }
        }
    }
}
