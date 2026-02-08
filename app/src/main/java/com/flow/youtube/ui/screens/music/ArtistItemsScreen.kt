package com.flow.youtube.ui.screens.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.flow.youtube.innertube.models.AlbumItem
import com.flow.youtube.innertube.models.ArtistItem
import com.flow.youtube.innertube.models.PlaylistItem
import com.flow.youtube.innertube.models.SongItem
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R
import com.flow.youtube.ui.components.YouTubeListItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistItemsScreen(
    browseId: String,
    params: String?,
    onBackClick: () -> Unit,
    onTrackClick: (SongItem) -> Unit,
    onAlbumClick: (String) -> Unit,
    onArtistClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    viewModel: MusicViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val artistItemsPage = uiState.artistItemsPage
    val isLoading = uiState.isArtistItemsLoading
    val isMoreLoading = uiState.isMoreLoading

    LaunchedEffect(browseId, params) {
        viewModel.loadArtistItems(browseId, params)
    }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && artistItemsPage != null && artistItemsPage.continuation != null && !isMoreLoading && index >= artistItemsPage.items.size - 5) {
                    viewModel.loadMoreArtistItems()
                }
            }
    }
    
    LaunchedEffect(lazyGridState) {
        snapshotFlow { lazyGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { index ->
                if (index != null && artistItemsPage != null && artistItemsPage.continuation != null && !isMoreLoading && index >= artistItemsPage.items.size - 5) {
                    viewModel.loadMoreArtistItems()
                }
            }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = artistItemsPage?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (artistItemsPage != null) {
                if (artistItemsPage.items.firstOrNull() is SongItem) {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(artistItemsPage.items) { item ->
                            if (item is SongItem) {
                                YouTubeListItem(
                                    item = item,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onTrackClick(item) }
                                )
                            }
                        }
                        if (isMoreLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        state = lazyGridState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(artistItemsPage.items) { item ->
                            ArtistGridItem(
                                item = item,
                                onClick = {
                                    when (item) {
                                        is AlbumItem -> onAlbumClick(item.id)
                                        is ArtistItem -> onArtistClick(item.id)
                                        is PlaylistItem -> onPlaylistClick(item.id)
                                        is SongItem -> {
                                            onTrackClick(item)
                                        }
                                    }
                                }
                            )
                        }
                        if (isMoreLoading) {
                            item {
                                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArtistGridItem(item: com.flow.youtube.innertube.models.YTItem, onClick: () -> Unit) {
    var title = ""
    var subtitle = ""
    var thumbnailUrl: String? = null

    when (item) {
        is AlbumItem -> {
            title = item.title
            val artistOrAlbum = item.artists?.firstOrNull()?.name ?: stringResource(R.string.album_label)
            subtitle = stringResource(R.string.year_artist_template, item.year ?: "", artistOrAlbum)
            thumbnailUrl = item.thumbnail
        }
        is ArtistItem -> {
            title = item.title
            subtitle = stringResource(R.string.subtitle_artist)
            thumbnailUrl = item.thumbnail
        }
        is PlaylistItem -> {
            title = item.title
            subtitle = stringResource(R.string.subtitle_playlist_template, item.author?.name ?: "")
            thumbnailUrl = item.thumbnail
        }
        is SongItem -> {
            title = item.title
            subtitle = item.artists.joinToString { it.name }
            thumbnailUrl = item.thumbnail
        }
    }

    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxWidth()
                .clip(if (item is ArtistItem) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
