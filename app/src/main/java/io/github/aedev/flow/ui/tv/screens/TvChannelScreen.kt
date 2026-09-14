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
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.ui.screens.channel.ChannelViewModel
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvFilterChip
import io.github.aedev.flow.ui.tv.components.TvLoadingState
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvPlaylistCard
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.focus.tvInitialFocus
import io.github.aedev.flow.ui.tv.focus.tvRowFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

private const val CHANNEL_GRID_COLUMNS = 3

private enum class TvChannelTab(
    val kind: ChannelTabKind,
    val labelRes: Int,
) {
    VIDEOS(ChannelTabKind.Videos, R.string.tv_channel_videos),
    LIVE(ChannelTabKind.Live, R.string.tv_filter_live),
    PLAYLISTS(ChannelTabKind.Playlists, R.string.tv_filter_playlists),
    ABOUT(ChannelTabKind.Unknown, R.string.about),
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
        viewModel.loadChannel(channelUrl)
    }

    val selectedTab =
        TvChannelTab.entries.firstOrNull { it.kind == uiState.selectedTab }
            ?: TvChannelTab.VIDEOS

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(top = dimens.overscanVertical),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        val header = uiState.header
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.overscanHorizontal),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = header?.avatarUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = header?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                header?.subscriberCountText?.takeIf { it.isNotBlank() }?.let { subscribers ->
                    Text(
                        text = subscribers,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TvButton(
                text =
                    if (uiState.isSubscribed) {
                        stringResource(R.string.subscribed)
                    } else {
                        stringResource(R.string.subscribe)
                    },
                onClick = viewModel::toggleSubscription,
            )
        }

        LazyRow(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .tvRowFocus(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(horizontal = dimens.overscanHorizontal),
        ) {
            items(TvChannelTab.entries, key = { it.name }) { tab ->
                TvFilterChip(
                    label = stringResource(tab.labelRes),
                    selected = tab == selectedTab,
                    onClick = { viewModel.selectTab(tab.kind) },
                    modifier =
                        if (tab == TvChannelTab.VIDEOS) {
                            Modifier.tvInitialFocus()
                        } else {
                            Modifier
                        },
                )
            }
        }

        when {
            uiState.isLoading && header == null -> {
                TvLoadingState(Modifier.weight(1f))
            }

            uiState.error != null && header == null -> {
                TvMessageState(
                    title = stringResource(R.string.tv_error_loading),
                    message = uiState.error,
                    modifier = Modifier.weight(1f),
                )
            }

            selectedTab == TvChannelTab.ABOUT -> {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = dimens.overscanHorizontal),
                ) {
                    Text(
                        text = header?.description.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            else -> {
                val tabStates by viewModel.tabStates.collectAsStateWithLifecycle()
                val items = tabStates[selectedTab.kind]?.items?.collectAsLazyPagingItems()
                when {
                    items == null || items.loadState.refresh is LoadState.Loading -> {
                        TvLoadingState(Modifier.weight(1f))
                    }

                    items.itemCount == 0 -> {
                        TvMessageState(
                            title = stringResource(R.string.tv_library_empty),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(CHANNEL_GRID_COLUMNS),
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .tvRowFocus(),
                            contentPadding =
                                PaddingValues(
                                    start = dimens.overscanHorizontal,
                                    end = dimens.overscanHorizontal,
                                    bottom = dimens.overscanVertical,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                        ) {
                            items(count = items.itemCount) { index ->
                                when (val item = items[index]) {
                                    is FeedItem.VideoItem -> {
                                        TvVideoCard(
                                            video = item.video,
                                            onClick = { onVideoClick(item.video) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }

                                    is FeedItem.PlaylistItem -> {
                                        TvPlaylistCard(
                                            playlist = item.playlist,
                                            onClick = { onOpenPlaylist(item.playlist.id) },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }

                                    else -> {
                                        Unit
                                    }
                                }
                            }
                            if (items.loadState.append is LoadState.Loading) {
                                item(span = { GridItemSpan(maxLineSpan) }) { TvLoadingState() }
                            }
                        }
                    }
                }
            }
        }
    }
}
