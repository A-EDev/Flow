package io.github.aedev.flow.ui.components.categories

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.renderer.FeedItem
import io.github.aedev.flow.ui.components.FeedGridLayout
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.PlaylistCardLayout
import io.github.aedev.flow.ui.components.shared.FeedPagingFooter
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.MediaVideoCard
import io.github.aedev.flow.ui.components.shared.rememberFeedGridPlan

/** A destination shelf's "see all", paged. */
@Composable
internal fun CategoryPagedGrid(
    pagingItems: LazyPagingItems<FeedItem>,
    gridState: LazyGridState,
    feedLayout: FeedGridLayout,
    isListView: Boolean,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The pager owns the first page, so its own refresh is the only thing that knows how this tab
    // is doing — the screen's flag was cleared as soon as the pager was handed its key, and the
    // footer speaks for the append. Without this the tab is a blank screen while it loads, and
    // stays one if the first page fails or comes back empty.
    val refresh = pagingItems.loadState.refresh
    if (pagingItems.itemCount == 0) {
        when {
            refresh is LoadState.Loading -> {
                CategoryShimmer(feedLayout = feedLayout, isListView = isListView, modifier = modifier)
            }

            refresh is LoadState.Error -> {
                FlowErrorState(
                    error = refresh.error.localizedMessage ?: stringResource(R.string.error_failed_to_load_videos),
                    onRetry = pagingItems::retry,
                    modifier = modifier,
                )
            }

            refresh.endOfPaginationReached -> {
                FlowEmptyState(
                    title = stringResource(R.string.error_no_videos_for_category),
                    modifier = modifier,
                )
            }
        }
        return
    }

    val plan =
        rememberFeedGridPlan(
            layout = feedLayout,
            listMode = isListView,
            itemCount = pagingItems.itemCount,
            spansOwnRow = { false },
            includeLastRun = pagingItems.loadState.append.endOfPaginationReached,
            itemsKey = pagingItems.itemSnapshotList,
        )

    LazyVerticalGrid(
        columns = plan.cells,
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = plan.contentPadding,
        horizontalArrangement = Arrangement.spacedBy(plan.gutter),
        verticalArrangement = Arrangement.spacedBy(plan.gutter),
    ) {
        items(
            count = pagingItems.itemCount,
            key = { index -> pagingItems.peek(index)?.gridKey() ?: "placeholder:$index" },
            contentType = { index -> pagingItems.peek(index)?.gridContentType() ?: "placeholder" },
            span = { index -> plan.span(index, spansOwnRow = false, maxLineSpan = maxLineSpan) },
        ) { index ->
            when (val item = pagingItems[index]) {
                is FeedItem.VideoItem, is FeedItem.ShortItem -> {
                    val video = item.gridVideo()
                    MediaVideoCard(
                        video = video,
                        asThumbnailRow = plan.isListCard(index),
                        onClick = { onVideoClick(video) },
                        onChannelClick = onChannelClick,
                        thumbnailWidth = plan.listThumbnailWidth,
                    )
                }

                is FeedItem.PlaylistItem -> {
                    PlaylistCard(
                        playlist = item.playlist,
                        onClick = { onPlaylistClick(item.playlist.id) },
                        layout = if (plan.isListCard(index)) PlaylistCardLayout.LIST else PlaylistCardLayout.SHELF,
                    )
                }

                else -> {
                    Unit
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            FeedPagingFooter(
                appendState = pagingItems.loadState.append,
                itemCount = pagingItems.itemCount,
                onRetry = pagingItems::retry,
            )
        }
    }
}

/** A chart: 30 ranked entries and no next page, in the same grid the paged surfaces use. */
@Composable
internal fun CategoryChartGrid(
    entries: List<Video>,
    gridState: LazyGridState,
    feedLayout: FeedGridLayout,
    isListView: Boolean,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val plan =
        rememberFeedGridPlan(
            layout = feedLayout,
            listMode = isListView,
            itemCount = entries.size,
            spansOwnRow = { false },
            includeLastRun = true,
            itemsKey = entries,
        )

    LazyVerticalGrid(
        columns = plan.cells,
        state = gridState,
        modifier = modifier.fillMaxSize(),
        contentPadding = plan.contentPadding,
        horizontalArrangement = Arrangement.spacedBy(plan.gutter),
        verticalArrangement = Arrangement.spacedBy(plan.gutter),
    ) {
        itemsIndexed(
            items = entries,
            key = { _, video -> video.id },
            contentType = { _, _ -> "video" },
            span = { index, _ -> plan.span(index, spansOwnRow = false, maxLineSpan = maxLineSpan) },
        ) { index, video ->
            MediaVideoCard(
                video = video,
                asThumbnailRow = plan.isListCard(index),
                onClick = { onVideoClick(video) },
                onChannelClick = onChannelClick,
                thumbnailWidth = plan.listThumbnailWidth,
            )
        }
    }
}

private fun FeedItem?.gridVideo(): Video =
    when (this) {
        is FeedItem.VideoItem -> video
        is FeedItem.ShortItem -> video
        else -> error("not a video item")
    }

private fun FeedItem.gridKey(): String =
    when (this) {
        is FeedItem.VideoItem -> "v:${video.id}"
        is FeedItem.ShortItem -> "s:${video.id}"
        is FeedItem.PlaylistItem -> "p:${playlist.id}"
        is FeedItem.RelatedChannelItem -> "c:${channel.id}"
        is FeedItem.PostItem -> "b:${post.id}"
    }

private fun FeedItem.gridContentType(): String =
    when (this) {
        is FeedItem.VideoItem, is FeedItem.ShortItem -> "video"
        is FeedItem.PlaylistItem -> "playlist"
        is FeedItem.RelatedChannelItem -> "channel"
        is FeedItem.PostItem -> "post"
    }
