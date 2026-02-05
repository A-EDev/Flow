package com.flow.youtube.ui.screens.music.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.flow.youtube.ui.screens.music.MusicTrack
import com.flow.youtube.ui.screens.music.components.GenreCard

@Composable
fun GenresTab(
    genres: List<String>,
    genreTracks: Map<String, List<MusicTrack>>,
    onGenreClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(genres) { genre ->
            GenreCard(
                genre = genre,
                trackCount = genreTracks[genre]?.size ?: 0,
                onClick = { onGenreClick(genre) }
            )
        }
    }
}
