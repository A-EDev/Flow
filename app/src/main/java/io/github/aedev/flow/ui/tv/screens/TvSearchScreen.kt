package io.github.aedev.flow.ui.tv.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.paging.SearchResultItem
import io.github.aedev.flow.innertube.models.AlbumItem
import io.github.aedev.flow.innertube.models.ArtistItem
import io.github.aedev.flow.innertube.models.PlaylistItem
import io.github.aedev.flow.innertube.models.SongItem
import io.github.aedev.flow.innertube.models.YTItem
import io.github.aedev.flow.ui.screens.music.MusicSearchUiState
import io.github.aedev.flow.ui.screens.music.MusicSearchViewModel
import io.github.aedev.flow.ui.screens.music.MusicTrack
import io.github.aedev.flow.ui.screens.music.convertSongToMusicTrack
import io.github.aedev.flow.ui.screens.search.SearchViewModel
import io.github.aedev.flow.ui.tv.components.TvArtistCard
import io.github.aedev.flow.ui.tv.components.TvChannelCard
import io.github.aedev.flow.ui.tv.components.TvFilterChip
import io.github.aedev.flow.ui.tv.components.TvKeyboard
import io.github.aedev.flow.ui.tv.components.TvLoadingState
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvMusicCard
import io.github.aedev.flow.ui.tv.components.TvMusicCollectionCard
import io.github.aedev.flow.ui.tv.components.TvPlaylistCard
import io.github.aedev.flow.ui.tv.components.TvSectionHeader
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.focus.ProvideTvColumnPivot
import io.github.aedev.flow.ui.tv.focus.ProvideTvRowPivot
import io.github.aedev.flow.ui.tv.focus.tvRowFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import kotlinx.coroutines.delay

private val SEARCH_CONTENT_TYPES = listOf(
    ContentType.ALL to R.string.tv_filter_all,
    ContentType.VIDEOS to R.string.tv_filter_videos,
    ContentType.CHANNELS to R.string.tv_filter_channels,
    ContentType.PLAYLISTS to R.string.tv_filter_playlists,
    ContentType.LIVE to R.string.tv_filter_live,
)

/**
 * D-pad-first search: grid keyboard on the left, live results on the right.
 * A dedicated Music mode searches YouTube Music (songs, albums, artists,
 * playlists) via the shared [MusicSearchViewModel]; suggestion chips appear
 * while typing in both modes.
 */
@Composable
fun TvSearchScreen(
    viewModel: SearchViewModel,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
    onChannelClick: (String) -> Unit = {},
    onPlayPlaylist: (List<Video>, String) -> Unit = { _, _ -> },
    onPlayTrack: (MusicTrack, List<MusicTrack>, String) -> Unit = { _, _, _ -> },
    onOpenMusicCollection: (String) -> Unit = {},
    onOpenMusicArtist: (String) -> Unit = {},
    musicSearchViewModel: MusicSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val dimens = LocalTvDimens.current
    var query by rememberSaveable { mutableStateOf("") }
    var contentType by rememberSaveable { mutableStateOf(ContentType.ALL) }
    var musicMode by rememberSaveable { mutableStateOf(false) }
    val results = viewModel.searchResults.collectAsLazyPagingItems()
    val musicState by musicSearchViewModel.uiState.collectAsStateWithLifecycle()
    var videoSuggestions by remember { mutableStateOf(emptyList<String>()) }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.let { query = it }
        }
    }
    val voiceIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
    }
    val voiceAvailable = remember {
        voiceIntent.resolveActivity(context.packageManager) != null
    }

    // Live search with a short debounce as the user types on the grid keyboard.
    LaunchedEffect(query, contentType, musicMode) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            viewModel.clearSearch()
            musicSearchViewModel.clearSearch()
            videoSuggestions = emptyList()
            return@LaunchedEffect
        }
        delay(350)
        if (musicMode) {
            musicSearchViewModel.onQueryChange(trimmed)
            musicSearchViewModel.performSearch(trimmed)
        } else {
            videoSuggestions = runCatching { viewModel.getSearchSuggestions(trimmed) }
                .getOrDefault(emptyList())
            viewModel.search(trimmed, SearchFilter(contentType = contentType))
        }
    }

    val suggestions = if (musicMode) musicState.suggestions else videoSuggestions

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(
                start = dimens.overscanHorizontal,
                end = dimens.overscanHorizontal,
                top = dimens.overscanVertical,
            ),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(
            modifier = Modifier.width(340.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = query.ifBlank { stringResource(R.string.tv_search_prompt) },
                style = MaterialTheme.typography.headlineSmall,
                color = if (query.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TvKeyboard(
                onInput = { query += it },
                onDelete = { query = query.dropLast(1) },
                onClear = { query = "" },
                onVoice = if (voiceAvailable) {
                    { voiceLauncher.launch(voiceIntent) }
                } else {
                    null
                },
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .tvRowFocus(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(SEARCH_CONTENT_TYPES, key = { it.first.name }) { (type, labelRes) ->
                    TvFilterChip(
                        label = stringResource(labelRes),
                        selected = !musicMode && contentType == type,
                        onClick = {
                            musicMode = false
                            contentType = type
                        },
                    )
                }
                item(key = "music") {
                    TvFilterChip(
                        label = stringResource(R.string.nav_music),
                        selected = musicMode,
                        onClick = { musicMode = true },
                    )
                }
            }

            if (query.isNotBlank() && suggestions.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .tvRowFocus(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(suggestions.take(8), key = { it }) { suggestion ->
                        TvFilterChip(
                            label = suggestion,
                            selected = false,
                            onClick = { query = suggestion },
                        )
                    }
                }
            }

            if (musicMode) {
                TvMusicSearchResults(
                    query = query,
                    state = musicState,
                    onPlayTrack = onPlayTrack,
                    onOpenMusicCollection = onOpenMusicCollection,
                    onOpenMusicArtist = onOpenMusicArtist,
                    modifier = Modifier.weight(1f),
                )
            } else {
                when {
                    query.isBlank() -> TvMessageState(
                        title = stringResource(R.string.tv_search_empty),
                        modifier = Modifier.weight(1f),
                    )
                    results.loadState.refresh is LoadState.Loading && results.itemCount == 0 ->
                        TvLoadingState(modifier = Modifier.weight(1f))
                    results.loadState.refresh is LoadState.Error && results.itemCount == 0 ->
                        TvMessageState(
                            title = stringResource(R.string.tv_error_loading),
                            modifier = Modifier.weight(1f),
                        )
                    results.loadState.refresh is LoadState.NotLoading && results.itemCount == 0 ->
                        TvMessageState(
                            title = stringResource(R.string.tv_search_no_results),
                            modifier = Modifier.weight(1f),
                        )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = dimens.videoCardWidth),
                        modifier = Modifier
                            .weight(1f)
                            .tvRowFocus(),
                        contentPadding = PaddingValues(bottom = dimens.overscanVertical, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                    ) {
                        items(
                            count = results.itemCount,
                            key = results.itemKey { item ->
                                when (item) {
                                    is SearchResultItem.VideoResult -> "video:${item.video.id}"
                                    is SearchResultItem.ChannelResult -> "channel:${item.channel.id}"
                                    is SearchResultItem.PlaylistResult -> "playlist:${item.playlist.id}"
                                    is SearchResultItem.ShortsShelfResult ->
                                        "shorts:${item.shorts.firstOrNull()?.id.orEmpty()}"
                                }
                            },
                        ) { index ->
                            when (val item = results[index]) {
                                is SearchResultItem.VideoResult -> TvVideoCard(
                                    video = item.video,
                                    onClick = { onVideoClick(item.video) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                is SearchResultItem.ChannelResult -> TvChannelCard(
                                    channel = item.channel,
                                    onClick = { onChannelClick(item.channel.url.ifBlank { item.channel.id }) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                is SearchResultItem.PlaylistResult -> TvPlaylistCard(
                                    playlist = item.playlist,
                                    onClick = {
                                        if (item.playlist.videos.isNotEmpty()) {
                                            onPlayPlaylist(item.playlist.videos, item.playlist.name)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                is SearchResultItem.ShortsShelfResult -> item.shorts.firstOrNull()?.let { short ->
                                    TvVideoCard(
                                        video = short,
                                        onClick = { onVideoClick(short) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                                null -> Unit
                            }
                        }

                        if (results.loadState.append is LoadState.Loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                TvLoadingState()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvMusicSearchResults(
    query: String,
    state: MusicSearchUiState,
    onPlayTrack: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onOpenMusicCollection: (String) -> Unit,
    onOpenMusicArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val searchSource = stringResource(R.string.search_source_template, query)
    val summaries = state.searchSummary?.summaries.orEmpty().filter { it.items.isNotEmpty() }

    when {
        query.isBlank() -> TvMessageState(
            title = stringResource(R.string.tv_search_empty),
            modifier = modifier,
        )
        (state.isSearching || state.isLoading) && summaries.isEmpty() ->
            TvLoadingState(modifier = modifier)
        summaries.isEmpty() -> TvMessageState(
            title = stringResource(R.string.tv_search_no_results),
            modifier = modifier,
        )
        else -> ProvideTvColumnPivot {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = LocalTvDimens.current.overscanVertical,
                ),
            ) {
                itemsIndexed(
                    summaries,
                    key = { index, summary -> "music-section:$index:${summary.title}" },
                ) { _, summary ->
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        TvSectionHeader(title = summary.title)
                        val sectionSongs = remember(summary) {
                            summary.items.filterIsInstance<SongItem>().map(::convertSongToMusicTrack)
                        }
                        ProvideTvRowPivot {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvRowFocus(),
                                horizontalArrangement = Arrangement.spacedBy(LocalTvDimens.current.itemSpacing),
                                contentPadding = PaddingValues(vertical = 10.dp),
                            ) {
                                itemsIndexed(
                                    summary.items,
                                    key = { itemIndex, item -> "$itemIndex:${item.id}" },
                                ) { _, item ->
                                    TvMusicResultCard(
                                        item = item,
                                        sectionSongs = sectionSongs,
                                        searchSource = searchSource,
                                        onPlayTrack = onPlayTrack,
                                        onOpenMusicCollection = onOpenMusicCollection,
                                        onOpenMusicArtist = onOpenMusicArtist,
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

@Composable
private fun TvMusicResultCard(
    item: YTItem,
    sectionSongs: List<MusicTrack>,
    searchSource: String,
    onPlayTrack: (MusicTrack, List<MusicTrack>, String) -> Unit,
    onOpenMusicCollection: (String) -> Unit,
    onOpenMusicArtist: (String) -> Unit,
) {
    when (item) {
        is SongItem -> {
            val track = remember(item) { convertSongToMusicTrack(item) }
            TvMusicCard(
                track = track,
                onClick = {
                    onPlayTrack(track, sectionSongs.ifEmpty { listOf(track) }, searchSource)
                },
            )
        }
        is AlbumItem -> TvMusicCollectionCard(
            title = item.title,
            subtitle = item.artists?.joinToString { it.name },
            thumbnailUrl = item.thumbnail,
            onClick = { onOpenMusicCollection(item.playlistId) },
        )
        is PlaylistItem -> TvMusicCollectionCard(
            title = item.title,
            subtitle = item.author?.name,
            thumbnailUrl = item.thumbnail.orEmpty(),
            onClick = { onOpenMusicCollection(item.id) },
        )
        is ArtistItem -> TvArtistCard(
            name = item.title,
            thumbnailUrl = item.thumbnail.orEmpty(),
            onClick = { onOpenMusicArtist(item.id) },
        )
    }
}
