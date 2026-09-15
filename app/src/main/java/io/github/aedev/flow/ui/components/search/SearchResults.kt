package io.github.aedev.flow.ui.components.search

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.LazyPagingItems
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Playlist
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.paging.SearchResultItem
import io.github.aedev.flow.data.paging.SearchShelfKind
import io.github.aedev.flow.ui.components.CompactVideoCardThumbnailWidth
import io.github.aedev.flow.ui.components.FeedGridLayout
import io.github.aedev.flow.ui.components.PlaylistCard
import io.github.aedev.flow.ui.components.PlaylistCardLayout
import io.github.aedev.flow.ui.components.ShortsCard
import io.github.aedev.flow.ui.components.partialRowIndices
import io.github.aedev.flow.ui.components.shared.dismissKeyboardOnPress

/** Every callback the result surfaces need, threaded through one object rather than nine parameters. */
data class SearchResultActions(
    val onVideoClick: (Video) -> Unit,
    val onShortsClick: (shelf: List<Video>, tapped: Video) -> Unit,
    val onChannelClick: (Channel) -> Unit,
    val onPlaylistClick: (Playlist) -> Unit,
    val dismissKeyboard: () -> Unit,
    val isSubscribed: (String) -> Boolean = { false },
    val onSubscribeToggle: (Channel) -> Unit = {},
)

@Composable
fun SearchResults(
    pagingItems: LazyPagingItems<SearchResultItem>,
    gridState: LazyGridState,
    feedLayout: FeedGridLayout,
    isGridMode: Boolean,
    actions: SearchResultActions,
    modifier: Modifier = Modifier,
) {
    // The toggle's stored flag is named for its icon: set means the thumbnail-left rows, which are a
    // single full-width column at every size.
    val listMode = isGridMode
    val columns = if (listMode) 1 else feedLayout.columns
    val cells = if (listMode) GridCells.Fixed(1) else feedLayout.cells

    val partialRows =
        remember(pagingItems.itemSnapshotList, columns, pagingItems.loadState.append.endOfPaginationReached) {
            partialRowIndices(
                spansOwnRow = (0 until pagingItems.itemCount).map { pagingItems.peek(it).spansRow() },
                columns = columns,
                includeLastRun = pagingItems.loadState.append.endOfPaginationReached,
            )
        }

    // A card takes the thumbnail-left shape whenever its row is its own: the toggle, a row the grid
    // could not fill, or a wide window the user pinned to one column.
    fun isListCard(index: Int) = listMode || index in partialRows || (columns == 1 && !feedLayout.isCompact)

    // Its thumbnail is one grid column wide, so it lines up with the cards it sits between rather
    // than reading as a different kind of row.
    val listThumbnailWidth =
        if (feedLayout.isCompact) CompactVideoCardThumbnailWidth else feedLayout.cardWidth

    val gutter = if (columns == 1) 0.dp else feedLayout.cardSpacing
    LazyVerticalGrid(
        columns = cells,
        state = gridState,
        modifier = modifier.fillMaxSize().dismissKeyboardOnPress(actions.dismissKeyboard),
        contentPadding =
            PaddingValues(
                start = feedLayout.contentPadding,
                end = feedLayout.contentPadding,
                top = TopPadding,
                bottom = BottomPadding,
            ),
        horizontalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(gutter),
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(gutter),
    ) {
        items(
            count = pagingItems.itemCount,
            key = { index -> pagingItems.peek(index).itemKey(index) },
            contentType = { index -> pagingItems.peek(index).contentType() },
            span = { index ->
                if (pagingItems.peek(index).spansRow() || index in partialRows) {
                    GridItemSpan(maxLineSpan)
                } else {
                    GridItemSpan(1)
                }
            },
        ) { index ->
            when (val item = pagingItems[index]) {
                is SearchResultItem.VideoResult -> {
                    SearchVideoCard(
                        video = item.video,
                        asThumbnailRow = isListCard(index),
                        onClick = { actions.onVideoClick(item.video) },
                        onChannelClick = { actions.onChannelClick(item.video.asChannel(it)) },
                        thumbnailWidth = listThumbnailWidth,
                    )
                }

                is SearchResultItem.ChannelResult -> {
                    SearchChannelHeroCard(
                        channel = item.channel,
                        isSubscribed = actions.isSubscribed(item.channel.id),
                        onSubscribeToggle = { actions.onSubscribeToggle(item.channel) },
                        onClick = { actions.onChannelClick(item.channel) },
                        latestTitle = item.latestTitle,
                        latestVideos = item.latestVideos,
                        onVideoClick = actions.onVideoClick,
                    )
                }

                is SearchResultItem.PlaylistResult -> {
                    PlaylistCard(
                        playlist = item.playlist,
                        onClick = { actions.onPlaylistClick(item.playlist) },
                        layout = if (isListCard(index)) PlaylistCardLayout.LIST else PlaylistCardLayout.SHELF,
                    )
                }

                is SearchResultItem.ShelfResult -> {
                    SearchShelf(
                        shelf = item,
                        asThumbnailRows = listMode || !feedLayout.isCompact,
                        thumbnailWidth = listThumbnailWidth,
                        onVideoClick = actions.onVideoClick,
                        onShortsClick = actions.onShortsClick,
                        onChannelClick = { actions.onChannelClick(Channel(it, "", "", 0)) },
                    )
                }

                null -> {
                    Unit
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchPagingFooter(
                appendState = pagingItems.loadState.append,
                itemCount = pagingItems.itemCount,
                onRetry = pagingItems::retry,
            )
        }
    }
}

/** The Shorts tab is a portrait grid of its own, never mixed with long-form cards. */
@Composable
fun SearchShortsGrid(
    pagingItems: LazyPagingItems<SearchResultItem>,
    gridState: LazyGridState,
    actions: SearchResultActions,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(ShortCellMinWidth),
        state = gridState,
        modifier = modifier.fillMaxSize().dismissKeyboardOnPress(actions.dismissKeyboard),
        contentPadding =
            PaddingValues(
                start = ShortGridPadding,
                end = ShortGridPadding,
                top = TopPadding,
                bottom = BottomPadding,
            ),
        horizontalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(ShortCellSpacing),
        verticalArrangement =
            androidx.compose.foundation.layout.Arrangement
                .spacedBy(ShortCellSpacing),
    ) {
        items(
            count = pagingItems.itemCount,
            key = { index -> pagingItems.peek(index).itemKey(index) },
            contentType = { index -> pagingItems.peek(index).contentType() },
        ) { index ->
            (pagingItems[index] as? SearchResultItem.VideoResult)?.let { result ->
                ShortsCard(
                    video = result.video,
                    onClick = { actions.onShortsClick(pagingItems.loadedShorts(), result.video) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            SearchPagingFooter(
                appendState = pagingItems.loadState.append,
                itemCount = pagingItems.itemCount,
                onRetry = pagingItems::retry,
            )
        }
    }
}

private fun LazyPagingItems<SearchResultItem>.loadedShorts(): List<Video> =
    (0 until itemCount).mapNotNull { (peek(it) as? SearchResultItem.VideoResult)?.video }

private fun Video.asChannel(channelId: String) =
    Channel(
        id = channelId,
        name = channelName,
        thumbnailUrl = channelThumbnailUrl,
        subscriberCount = 0,
        url = "https://www.youtube.com/channel/$channelId",
    )

/** The hero card and every strip own their row; only results share one. */
private fun SearchResultItem?.spansRow(): Boolean =
    when (this) {
        is SearchResultItem.ShelfResult, is SearchResultItem.ChannelResult -> true
        is SearchResultItem.VideoResult, is SearchResultItem.PlaylistResult, null -> false
    }

private fun SearchResultItem?.itemKey(index: Int): Any =
    when (this) {
        is SearchResultItem.VideoResult -> "video:${video.id}"
        is SearchResultItem.ChannelResult -> "channel:${channel.id}"
        is SearchResultItem.PlaylistResult -> "playlist:${playlist.id}"
        is SearchResultItem.ShelfResult -> "shelf:$id"
        null -> "placeholder:$index"
    }

/** Lets the grid reuse a composition when a slot is filled by another item of the same kind. */
private fun SearchResultItem?.contentType(): Any =
    when (this) {
        is SearchResultItem.VideoResult -> "video"
        is SearchResultItem.ChannelResult -> "channel"
        is SearchResultItem.PlaylistResult -> "playlist"
        is SearchResultItem.ShelfResult -> "shelf:${kind.name}"
        null -> "placeholder"
    }

private val TopPadding = 8.dp
private val BottomPadding = 90.dp
private val ShortCellMinWidth = 160.dp
private val ShortCellSpacing = 12.dp
private val ShortGridPadding = 12.dp
