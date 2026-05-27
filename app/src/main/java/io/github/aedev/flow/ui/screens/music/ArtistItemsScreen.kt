package io.github.aedev.flow.ui.screens.music

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.MoreVert
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
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SongItem
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.MusicCollectionActionItem
import io.github.aedev.flow.ui.components.MusicCollectionQuickActionsSheet
import io.github.aedev.flow.ui.components.MusicQuickActionsSheet
import io.github.aedev.flow.ui.screens.music.components.TrackListItem

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val context = LocalContext.current
    var selectedTrack by remember { mutableStateOf<MusicTrack?>(null) }
    var selectedCollection by remember { mutableStateOf<MusicCollectionActionItem?>(null) }

    selectedTrack?.let { track ->
        MusicQuickActionsSheet(
            track = track,
            onDismiss = { selectedTrack = null },
            onViewArtist = { artistId -> if (artistId.isNotEmpty()) onArtistClick(artistId) },
            onViewAlbum = { albumId -> if (albumId.isNotEmpty()) onAlbumClick(albumId) },
            onShare = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, track.title)
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.share_message_template, track.title, track.artist, track.videoId))
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_song)))
            }
        )
    }

    selectedCollection?.let { collection ->
        MusicCollectionQuickActionsSheet(
            item = collection,
            onDismiss = { selectedCollection = null },
            onOpen = {
                if (collection.isAlbum) onAlbumClick(collection.id) else onPlaylistClick(collection.id)
            }
        )
    }

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
                                val track = item.toMusicTrack()
                                TrackListItem(
                                    track = track,
                                    isDownloaded = uiState.downloadedTrackIds.contains(track.videoId),
                                    onClick = { onTrackClick(item) },
                                    onLongClick = { selectedTrack = track },
                                    onMenuClick = { selectedTrack = track }
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
                                onActionClick = when (item) {
                                    is AlbumItem, is PlaylistItem -> ({ selectedCollection = item.toCollectionActionItem() })
                                    else -> null
                                },
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

private fun SongItem.toMusicTrack(): MusicTrack {
    return MusicTrack(
        videoId = id,
        title = title,
        artist = artists.joinToString { it.name },
        thumbnailUrl = thumbnail,
        duration = duration ?: 0,
        sourceUrl = "https://www.youtube.com/watch?v=$id",
        album = album?.name ?: "",
        channelId = artists.firstOrNull()?.id ?: "",
        isExplicit = explicit,
        isVideoSong = isVideoSong
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArtistGridItem(
    item: io.github.aedev.flow.innertube.models.YTItem,
    onClick: () -> Unit,
    onActionClick: (() -> Unit)? = null
) {
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onActionClick
            )
    ) {
        Box {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .aspectRatio(1f)
                    .fillMaxWidth()
                    .clip(if (item is ArtistItem) androidx.compose.foundation.shape.CircleShape else RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            if (onActionClick != null) {
                IconButton(
                    onClick = onActionClick,
                    modifier = Modifier.align(Alignment.TopEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options),
                        tint = androidx.compose.ui.graphics.Color.White
                    )
                }
            }
        }
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

private fun io.github.aedev.flow.innertube.models.YTItem.toCollectionActionItem(): MusicCollectionActionItem? = when (this) {
    is AlbumItem -> MusicCollectionActionItem(
        id = id,
        title = title,
        subtitle = artists?.joinToString { it.name }.orEmpty(),
        thumbnailUrl = thumbnail,
        description = year?.toString().orEmpty(),
        isAlbum = true
    )
    is PlaylistItem -> MusicCollectionActionItem(
        id = id,
        title = title,
        subtitle = author?.name.orEmpty(),
        thumbnailUrl = thumbnail,
        description = author?.name.orEmpty(),
        isAlbum = false
    )
    else -> null
}
