package io.github.aedev.flow.ui.components.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
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
import io.github.aedev.flow.ui.components.shared.FlowEmptyState

/**
 * Every channel tab's list, whatever it holds.
 *
 * One renderer for all of them because every tab parses to the same [ChannelItem]; a tab the app has
 * no special layout for still shows its contents rather than nothing.
 */
@Composable
internal fun ChannelTabItems(
    pagingItems: LazyPagingItems<ChannelItem>?,
    kind: ChannelTabKind,
    isGridView: Boolean,
    listState: LazyListState,
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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "top_gap") { Spacer(Modifier.height(8.dp)) }
        if (kind == ChannelTabKind.Shorts) {
            shortsRows(pagingItems, onShortClick)
        } else {
            channelRows(pagingItems, isGridView, onVideoClick, onShortClick, onPlaylistClick, onChannelClick)
        }
        if (pagingItems.loadState.append is LoadState.Loading) {
            item(key = "append_spinner") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun LazyListScope.channelRows(
    pagingItems: LazyPagingItems<ChannelItem>,
    isGridView: Boolean,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onChannelClick: (String) -> Unit,
) {
    items(count = pagingItems.itemCount, key = { index -> pagingItems.peek(index)?.itemKey() ?: index }) { index ->
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
                CompactVideoCard(
                    video = item.video,
                    showChannelName = false,
                    onClick = { onShortClick(item.video.id) },
                )
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
}

private fun LazyListScope.shortsRows(
    pagingItems: LazyPagingItems<ChannelItem>,
    onShortClick: (String) -> Unit,
) {
    val rowCount = (pagingItems.itemCount + 1) / 2
    items(count = rowCount, key = { rowIndex -> "shorts_row_$rowIndex" }) { rowIndex ->
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            for (column in 0 until 2) {
                val index = rowIndex * 2 + column
                Box(modifier = Modifier.weight(1f)) {
                    if (index < pagingItems.itemCount) {
                        (pagingItems[index] as? ChannelItem.ShortItem)?.let { short ->
                            ChannelShortCard(video = short.video, onClick = { onShortClick(short.video.id) })
                        }
                    }
                }
            }
        }
    }
}

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
