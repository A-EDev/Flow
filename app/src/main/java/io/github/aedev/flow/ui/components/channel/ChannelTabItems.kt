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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.ui.components.CompactVideoCard
import io.github.aedev.flow.ui.components.FEED_MAX_AUTO_COLUMNS
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.components.feedCardsFormGrid
import io.github.aedev.flow.ui.components.rememberFeedGridLayout
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowFeedProgress
import io.github.aedev.flow.ui.components.shared.FlowLoadingIndicator

/**
 * Every channel tab's list, whatever it holds.
 *
 * Column counts come from [rememberFeedGridLayout], the decision Home, Subscriptions, Categories and
 * Search already share, so a tablet lays a channel out like the rest of the app rather than stretching
 * two cards across the window. Playlists, shows and podcasts stay single-column rows at every width:
 * a thumbnail-left row shrunk into a grid cell is two words and a stamp.
 */
@Composable
internal fun ChannelTabItems(
    pagingItems: LazyPagingItems<FeedItem>?,
    kind: ChannelTabKind,
    isGridView: Boolean,
    columnPreference: HomeFeedColumns,
    hasFilterBar: Boolean,
    listState: LazyGridState,
    contentPadding: PaddingValues,
    topInset: Dp,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    if (pagingItems == null || pagingItems.loadState.refresh is LoadState.Loading) {
        FlowLoadingIndicator(modifier = Modifier.padding(top = topInset))
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
        val feedLayout = rememberFeedGridLayout(maxWidth, columnPreference, FEED_MAX_AUTO_COLUMNS)
        // Shorts are portrait, so many more fit per row than a 16:9 card ever would.
        val isShorts = kind == ChannelTabKind.Shorts
        val loneItem = feedLayout.columns > 1 && !feedCardsFormGrid(feedLayout.columns, pagingItems.itemCount)
        val cells =
            when {
                isShorts -> GridCells.Adaptive(ShortCellMinWidth)
                loneItem -> GridCells.Fixed(1)
                else -> feedLayout.cells
            }
        val rowGutter = if (isShorts) ShortCellSpacing else feedLayout.cardSpacing
        // Cards carry their own 12 dp inset, so no column gutter still leaves 24 dp between thumbnails
        // and keeps them flush with the chips above.
        val columnGutter = if (isShorts) ShortCellSpacing else 0.dp
        val gridCards = isGridView && !loneItem
        // Even a zero-height item collects the row gutter, so a wide window with chips above adds none.
        val topGap = if (feedLayout.isCompact || !hasFilterBar) 8.dp else null

        LazyVerticalGrid(
            columns = cells,
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(columnGutter),
            verticalArrangement = Arrangement.spacedBy(rowGutter),
        ) {
            if (topGap != null) {
                fullSpanItem(key = "top_gap") { Spacer(Modifier.height(topGap)) }
            }
            items(
                count = pagingItems.itemCount,
                key = { index -> pagingItems.peek(index)?.itemKey() ?: index },
                span = { index ->
                    if (pagingItems.peek(index).spansRow()) GridItemSpan(maxLineSpan) else GridItemSpan(1)
                },
            ) { index ->
                when (val item = pagingItems[index]) {
                    is FeedItem.VideoItem -> {
                        if (gridCards) {
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

                    is FeedItem.ShortItem -> {
                        ChannelShortCard(video = item.video, onClick = { onShortClick(item.video.id) })
                    }

                    is FeedItem.PlaylistItem -> {
                        PlaylistCard(playlist = item.playlist, onClick = { onPlaylistClick(item.playlist.id) })
                    }

                    is FeedItem.RelatedChannelItem -> {
                        ChannelRow(channel = item.channel, onClick = { onChannelClick(item.channel.id) })
                    }

                    is FeedItem.PostItem, null -> {
                        Unit
                    }
                }
            }
            if (pagingItems.loadState.append is LoadState.Loading) {
                fullSpanItem(key = "append_spinner") { FlowFeedProgress() }
            }
            fullSpanItem(key = "bottom_gap") { Spacer(Modifier.height(16.dp)) }
        }
    }
}

private fun LazyGridScope.fullSpanItem(
    key: String,
    content: @Composable () -> Unit,
) = item(key = key, span = { GridItemSpan(maxLineSpan) }) { content() }

private fun FeedItem?.spansRow(): Boolean =
    when (this) {
        is FeedItem.PlaylistItem, is FeedItem.RelatedChannelItem, is FeedItem.PostItem -> true
        is FeedItem.VideoItem, is FeedItem.ShortItem, null -> false
    }

private fun FeedItem.itemKey(): String =
    when (this) {
        is FeedItem.VideoItem -> "v_${video.id}"
        is FeedItem.ShortItem -> "s_${video.id}"
        is FeedItem.PlaylistItem -> "p_${playlist.id}"
        is FeedItem.RelatedChannelItem -> "c_${channel.id}"
        is FeedItem.PostItem -> "b_${post.id}"
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
