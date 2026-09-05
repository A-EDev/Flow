package io.github.aedev.flow.ui.screens.music

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.music.item.MusicCardOverflowButton
import io.github.aedev.flow.ui.components.music.item.MusicCollectionCard
import io.github.aedev.flow.ui.components.music.item.MusicTrackItem
import io.github.aedev.flow.ui.components.music.sheet.MusicCollectionActionItem
import io.github.aedev.flow.ui.components.music.sheet.MusicCollectionQuickActionsSheet
import io.github.aedev.flow.ui.components.music.sheet.MusicQuickActionsSheet
import io.github.aedev.flow.ui.components.music.sheet.toCollectionActionItem
import io.github.aedev.flow.ui.components.shared.FlowFeedProgress
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator
import io.github.aedev.flow.ui.components.shared.flowArtistShape

private val GridCellMinWidth = 150.dp
private const val LOAD_MORE_THRESHOLD = 5

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
    viewModel: MusicViewModel = sharedMusicViewModel(),
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
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, track.title)
                        putExtra(
                            Intent.EXTRA_TEXT,
                            context.getString(R.string.share_message_template, track.title, track.artist, track.videoId),
                        )
                    }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_song)))
            },
        )
    }

    selectedCollection?.let { collection ->
        MusicCollectionQuickActionsSheet(
            item = collection,
            onDismiss = { selectedCollection = null },
            onOpen = {
                if (collection.isAlbum) onAlbumClick(collection.id) else onPlaylistClick(collection.id)
            },
        )
    }

    LaunchedEffect(browseId, params) {
        viewModel.loadArtistItems(browseId, params)
    }

    val lazyListState = rememberLazyListState()
    val lazyGridState = rememberLazyGridState()

    fun loadMoreIfNearEnd(lastVisibleIndex: Int?) {
        if (lastVisibleIndex != null && artistItemsPage != null && artistItemsPage.continuation != null && !isMoreLoading &&
            lastVisibleIndex >= artistItemsPage.items.size - LOAD_MORE_THRESHOLD
        ) {
            viewModel.loadMoreArtistItems()
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { index -> loadMoreIfNearEnd(index) }
    }

    LaunchedEffect(lazyGridState) {
        snapshotFlow {
            lazyGridState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { index -> loadMoreIfNearEnd(index) }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            FlowTopBar(
                title = artistItemsPage?.title ?: "",
                onBack = onBackClick,
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
        ) {
            if (isLoading) {
                FlowLoadingIndicator()
            } else if (artistItemsPage != null) {
                if (artistItemsPage.items.firstOrNull() is SongItem) {
                    LazyColumn(
                        state = lazyListState,
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(artistItemsPage.items, key = { it.id }) { item ->
                            if (item is SongItem) {
                                val track = convertSongToMusicTrack(item)
                                MusicTrackItem(
                                    track = track,
                                    isDownloaded = uiState.downloadedTrackIds.contains(track.videoId),
                                    onClick = { onTrackClick(item) },
                                    onLongClick = { selectedTrack = track },
                                    onMenuClick = { selectedTrack = track },
                                )
                            }
                        }
                        if (isMoreLoading) {
                            item(key = "more_loading") { FlowFeedProgress() }
                        }
                    }
                } else {
                    val artistShape = flowArtistShape()
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(GridCellMinWidth),
                        state = lazyGridState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(artistItemsPage.items, key = { it.id }) { item ->
                            val onAction: (() -> Unit)? =
                                when (item) {
                                    is AlbumItem, is PlaylistItem -> ({ selectedCollection = item.toCollectionActionItem() })
                                    else -> null
                                }
                            MusicCollectionCard(
                                title = item.title,
                                subtitle = item.cardSubtitle(),
                                thumbnailUrl = item.thumbnail,
                                fillMaxWidth = true,
                                shape = if (item is ArtistItem) artistShape else MaterialTheme.shapes.large,
                                horizontalAlignment = if (item is ArtistItem) Alignment.CenterHorizontally else Alignment.Start,
                                onLongClick = onAction,
                                trailingContent = onAction?.let { action -> { MusicCardOverflowButton(onClick = action) } },
                                onClick = {
                                    when (item) {
                                        is AlbumItem -> onAlbumClick(item.id)
                                        is ArtistItem -> onArtistClick(item.id)
                                        is PlaylistItem -> onPlaylistClick(item.id)
                                        is SongItem -> onTrackClick(item)
                                    }
                                },
                            )
                        }
                        if (isMoreLoading) {
                            item(key = "more_loading") { FlowFeedProgress() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YTItem.cardSubtitle(): String =
    when (this) {
        is AlbumItem -> {
            val artistOrAlbum = artists?.firstOrNull()?.name ?: stringResource(R.string.album_label)
            stringResource(R.string.year_artist_template, year ?: "", artistOrAlbum)
        }

        is ArtistItem -> {
            stringResource(R.string.subtitle_artist)
        }

        is PlaylistItem -> {
            stringResource(R.string.subtitle_playlist_template, author?.name ?: "")
        }

        is SongItem -> {
            artists.joinToString { it.name }
        }
    }
