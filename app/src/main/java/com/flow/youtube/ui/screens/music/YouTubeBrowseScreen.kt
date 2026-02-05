package com.flow.youtube.ui.screens.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.innertube.models.AlbumItem
import com.flow.youtube.innertube.models.ArtistItem
import com.flow.youtube.innertube.models.PlaylistItem
import com.flow.youtube.innertube.models.SongItem
import com.flow.youtube.innertube.models.YTItem
import com.flow.youtube.player.EnhancedMusicPlayerManager
import com.flow.youtube.ui.components.*
import com.flow.youtube.ui.theme.Dimensions

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun YouTubeBrowseScreen(
    onBackClick: () -> Unit,
    onSongClick: (SongItem) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    viewModel: YouTubeBrowseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentTrack by EnhancedMusicPlayerManager.currentTrack.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = uiState.title ?: "Browse",
                        fontWeight = FontWeight.Bold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                uiState.isLoading -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        item {
                            ShimmerHost {
                                repeat(3) {
                                    ShimmerSectionTitle(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(5) {
                                            ShimmerGridItem()
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(16.dp))
                                }
                            }
                        }
                    }
                }
                
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = uiState.error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.retry() }) {
                            Text("Retry")
                        }
                    }
                }
                
                uiState.sections.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No content available",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        uiState.sections.forEach { section ->
                            if (section.items.isNotEmpty()) {
                                section.title?.let { title ->
                                    item(key = "title_${title.hashCode()}") {
                                        SectionTitle(
                                            title = title,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                                
                                if (section.items.all { it is SongItem }) {
                                    item(key = "songs_${section.title?.hashCode() ?: section.hashCode()}") {
                                        val gridState = rememberLazyGridState()
                                        LazyHorizontalGrid(
                                            rows = GridCells.Fixed(4),
                                            state = gridState,
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                            modifier = Modifier
                                                .height(Dimensions.ListItemHeight * 4 + 12.dp)
                                                .fillMaxWidth()
                                        ) {
                                            items(section.items) { item ->
                                                val song = item as SongItem
                                                ListItem(
                                                    title = song.title,
                                                    subtitle = song.artists.joinToString(", ") { it.name },
                                                    thumbnailUrl = song.thumbnail,
                                                    isPlaying = currentTrack?.videoId == song.id,
                                                    onClick = { onSongClick(song) },
                                                    modifier = Modifier.width(300.dp)
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    item(key = "items_${section.title?.hashCode() ?: section.hashCode()}") {
                                        LazyRow(
                                            contentPadding = PaddingValues(horizontal = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            items(section.items) { item ->
                                                when (item) {
                                                    is SongItem -> {
                                                        GridItem(
                                                            title = item.title,
                                                            subtitle = item.artists.joinToString(", ") { it.name },
                                                            thumbnailUrl = item.thumbnail,
                                                            thumbnailHeight = currentGridThumbnailHeight(),
                                                            onClick = { onSongClick(item) }
                                                        )
                                                    }
                                                    is AlbumItem -> {
                                                        GridItem(
                                                            title = item.title,
                                                            subtitle = item.artists?.joinToString(", ") { it.name } ?: "",
                                                            thumbnailUrl = item.thumbnail,
                                                            thumbnailHeight = currentGridThumbnailHeight(),
                                                            onClick = { onAlbumClick(item.id) }
                                                        )
                                                    }
                                                    is ArtistItem -> {
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            modifier = Modifier
                                                                .width(100.dp)
                                                                .clickable { onArtistClick(item.id) }
                                                        ) {
                                                            ArtistThumbnail(
                                                                thumbnailUrl = item.thumbnail,
                                                                size = 100.dp
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Text(
                                                                text = item.title,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                maxLines = 1
                                                            )
                                                        }
                                                    }
                                                    is PlaylistItem -> {
                                                        GridItem(
                                                            title = item.title,
                                                            subtitle = item.author?.name ?: "",
                                                            thumbnailUrl = item.thumbnail,
                                                            thumbnailHeight = currentGridThumbnailHeight(),
                                                            onClick = { onPlaylistClick(item.id) }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
