package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.channel.ChannelViewModel
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvFilterChip
import io.github.aedev.flow.ui.tv.components.TvLoadingState
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvPlaylistCard
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.focus.tvRowFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import io.github.aedev.flow.utils.formatSubscriberCount

private const val CHANNEL_GRID_COLUMNS = 3

private enum class TvChannelTab(val vmIndex: Int, val labelRes: Int) {
    VIDEOS(0, R.string.tv_channel_videos),
    LIVE(2, R.string.tv_filter_live),
    PLAYLISTS(3, R.string.tv_filter_playlists),
    ABOUT(5, R.string.about),
}

/** Channel detail page: header, subscribe, tab chips, and paged content grids. */
@Composable
fun TvChannelScreen(
    channelUrl: String,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
    onOpenPlaylist: (String) -> Unit = {},
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalTvDimens.current

    LaunchedEffect(channelUrl) {
        viewModel.initialize(context.applicationContext)
        viewModel.loadChannel(channelUrl)
    }

    val selectedTab = TvChannelTab.entries.firstOrNull { it.vmIndex == uiState.selectedTab }
        ?: TvChannelTab.VIDEOS

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = dimens.overscanVertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val info = uiState.channelInfo
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.overscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val avatarUrl = info?.avatars?.maxByOrNull { it.height }?.url
                ?: info?.avatars?.firstOrNull()?.url
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = info?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subscribers = info?.subscriberCount?.let(::formatSubscriberCount).orEmpty()
                if (subscribers.isNotBlank()) {
                    Text(
                        text = stringResource(R.string.subscribers_count_template, subscribers),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TvButton(
                text = if (uiState.isSubscribed) {
                    stringResource(R.string.subscribed)
                } else {
                    stringResource(R.string.subscribe)
                },
                onClick = viewModel::toggleSubscription,
            )
        }

        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .tvRowFocus(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = dimens.overscanHorizontal),
        ) {
            items(TvChannelTab.entries, key = { it.name }) { tab ->
                TvFilterChip(
                    label = stringResource(tab.labelRes),
                    selected = tab == selectedTab,
                    onClick = { viewModel.selectTab(tab.vmIndex) },
                )
            }
        }

        when {
            uiState.isLoading && info == null -> TvLoadingState(Modifier.weight(1f))
            uiState.error != null && info == null -> TvMessageState(
                title = stringResource(R.string.tv_error_loading),
                message = uiState.error,
                modifier = Modifier.weight(1f),
            )
            selectedTab == TvChannelTab.ABOUT -> Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimens.overscanHorizontal),
            ) {
                Text(
                    text = info?.description.orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            selectedTab == TvChannelTab.PLAYLISTS -> {
                val flow by viewModel.playlistsPagingFlow.collectAsStateWithLifecycle()
                val playlists = flow?.collectAsLazyPagingItems()
                if (playlists == null) {
                    TvLoadingState(Modifier.weight(1f))
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(CHANNEL_GRID_COLUMNS),
                        modifier = Modifier
                            .weight(1f)
                            .tvRowFocus(),
                        contentPadding = PaddingValues(
                            start = dimens.overscanHorizontal,
                            end = dimens.overscanHorizontal,
                            bottom = dimens.overscanVertical,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                    ) {
                        items(
                            count = playlists.itemCount,
                            key = playlists.itemKey { "playlist:${it.id}" },
                        ) { index ->
                            playlists[index]?.let { playlist ->
                                TvPlaylistCard(
                                    playlist = playlist,
                                    onClick = { onOpenPlaylist(playlist.id) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                        if (playlists.loadState.append is LoadState.Loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) { TvLoadingState() }
                        }
                    }
                }
            }
            else -> {
                // The videos/live tabs are backed by the eagerly loaded full
                // lists — the ViewModel never populates its paging flows for
                // them (mobile reads these same lists for filtering support).
                val videos by if (selectedTab == TvChannelTab.LIVE) {
                    viewModel.liveAll.collectAsStateWithLifecycle()
                } else {
                    viewModel.videosAll.collectAsStateWithLifecycle()
                }
                val isLoadingAll by viewModel.isLoadingAllVideos.collectAsStateWithLifecycle()
                when {
                    videos.isEmpty() && (isLoadingAll || uiState.isLoadingVideos || uiState.isLoading) ->
                        TvLoadingState(Modifier.weight(1f))
                    videos.isEmpty() -> TvMessageState(
                        title = stringResource(R.string.tv_library_empty),
                        modifier = Modifier.weight(1f),
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(CHANNEL_GRID_COLUMNS),
                        modifier = Modifier
                            .weight(1f)
                            .tvRowFocus(),
                        contentPadding = PaddingValues(
                            start = dimens.overscanHorizontal,
                            end = dimens.overscanHorizontal,
                            bottom = dimens.overscanVertical,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                        verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                    ) {
                        items(
                            count = videos.size,
                            key = { "video:$it:${videos[it].id}" },
                        ) { index ->
                            val video = videos[index]
                            TvVideoCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (isLoadingAll) {
                            item(span = { GridItemSpan(maxLineSpan) }) { TvLoadingState() }
                        }
                    }
                }
            }
        }
    }
}
