package io.github.aedev.flow.ui.components.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.channel.ChannelItem
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.ui.components.CompactVideoCard
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.components.rememberFeedGridLayout
import io.github.aedev.flow.ui.components.shared.FlowEmptyState

/**
 * Every channel tab's list, whatever it holds.
 *
 * Column counts come from [rememberFeedGridLayout], the decision Home, Subscriptions, Categories and
 * Search already share, so a tablet lays a channel out like the rest of the app rather than stretching
 * two cards across the window.
 */
@Composable
internal fun ChannelTabItems(
    pagingItems: LazyPagingItems<ChannelItem>?,
    kind: ChannelTabKind,
    isGridView: Boolean,
    listState: LazyGridState,
    contentPadding: PaddingValues,
    topInset: Dp,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    if (pagingItems == null || pagingItems.loadState.refresh is LoadState.Loading) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = topInset),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    if (pagingItems.itemCount == 0) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = topInset),
            contentAlignment = Alignment.Center,
        ) { FlowEmptyState(title = stringResource(kind.emptyLabel())) }
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val feedLayout = rememberFeedGridLayout(maxWidth)
        // Shorts are portrait, so many more fit per row than a 16:9 card ever would.
        val isShorts = kind == ChannelTabKind.Shorts
        val cells = if (isShorts) GridCells.Adaptive(ShortCellMinWidth) else feedLayout.cells
        val gutter = if (isShorts) ShortCellSpacing else feedLayout.cardSpacing

        LazyVerticalGrid(
            columns = cells,
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(gutter),
            verticalArrangement = Arrangement.spacedBy(gutter),
        ) {
            fullSpanItem(key = "top_gap") { Spacer(Modifier.height(8.dp)) }
            items(
                count = pagingItems.itemCount,
                key = { index -> pagingItems.peek(index)?.itemKey() ?: index },
            ) { index ->
                when (val item = pagingItems[index]) {
                    is ChannelItem.VideoItem -> {
                        if (isGridView) {
                            VideoCardFullWidth(
                                video = item.video,
                                showChannelAvatar = false,
                                showChannelName = false,
                                onClick = { onVideoClick(item.video) },
                            )
                        } else {
                            CompactVideoCard(
                                video = item.video,
                                showChannelName = false,
                                onClick = { onVideoClick(item.video) },
                            )
                        }
                    }

                    is ChannelItem.ShortItem -> {
                        ChannelShortCard(video = item.video, onClick = { onShortClick(item.video.id) })
                    }

                    is ChannelItem.PlaylistItem -> {
                        PlaylistCard(playlist = item.playlist, onClick = { onPlaylistClick(item.playlist.id) })
                    }

                    is ChannelItem.RelatedChannelItem -> {
                        ChannelRow(channel = item.channel, onClick = { onChannelClick(item.channel.id) })
                    }

                    is ChannelItem.PostItem, null -> {
                        Unit
                    }
                }
            }
            if (pagingItems.loadState.append is LoadState.Loading) {
                fullSpanItem(key = "append_spinner") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }
            fullSpanItem(key = "bottom_gap") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun LazyGridScope.fullSpanItem(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun ChannelItem.itemKey(): String =
    when (this) {
        is ChannelItem.VideoItem -> "v_${video.id}"
        is ChannelItem.ShortItem -> "s_${video.id}"
        is ChannelItem.PlaylistItem -> "p_${playlist.id}"
        is ChannelItem.RelatedChannelItem -> "c_${channel.id}"
        is ChannelItem.PostItem -> "b_${post.id}"
    }

private fun ChannelTabKind.emptyLabel(): Int =
    when (this) {
        ChannelTabKind.Shorts -> R.string.error_no_shorts_found
        ChannelTabKind.Live -> R.string.error_no_live_videos_found
        ChannelTabKind.Playlists, ChannelTabKind.Podcasts -> R.string.error_no_playlists_found
        else -> R.string.error_no_videos_found
    }

private val ShortCellMinWidth = 160.dp
private val ShortCellSpacing = 2.dp
