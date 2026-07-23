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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.paging.SearchResultItem
import io.github.aedev.flow.ui.screens.search.SearchViewModel
import io.github.aedev.flow.ui.tv.components.TvChannelCard
import io.github.aedev.flow.ui.tv.components.TvFilterChip
import io.github.aedev.flow.ui.tv.components.TvKeyboard
import io.github.aedev.flow.ui.tv.components.TvLoadingState
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvPlaylistCard
import io.github.aedev.flow.ui.tv.components.TvVideoCard
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
 * D-pad-first search: grid keyboard on the left, live results on the right,
 * with content-type filter chips covering every result kind.
 */
@Composable
fun TvSearchScreen(
    viewModel: SearchViewModel,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
    onChannelClick: (String) -> Unit = {},
    onPlayPlaylist: (List<Video>, String) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val dimens = LocalTvDimens.current
    var query by rememberSaveable { mutableStateOf("") }
    var contentType by rememberSaveable { mutableStateOf(ContentType.ALL) }
    val results = viewModel.searchResults.collectAsLazyPagingItems()

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
    LaunchedEffect(query, contentType) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) {
            viewModel.clearSearch()
            return@LaunchedEffect
        }
        delay(350)
        viewModel.search(trimmed, SearchFilter(contentType = contentType))
    }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        selected = contentType == type,
                        onClick = { contentType = type },
                    )
                }
            }

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
