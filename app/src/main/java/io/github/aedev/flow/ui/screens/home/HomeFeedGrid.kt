package io.github.aedev.flow.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.home.ContinueWatchingShelf
import io.github.aedev.flow.ui.components.layout.flowBottomContentPadding
import io.github.aedev.flow.ui.components.shared.FlowFeedProgress
import io.github.aedev.flow.ui.components.shared.MediaShortsShelf
import io.github.aedev.flow.ui.components.shared.card.MediaVideoCard
import io.github.aedev.flow.ui.components.shared.card.VideoCardLayout

private const val FEED_FOOTER_MIN_VIDEOS = 100

@Composable
internal fun HomeFeedGrid(
    uiState: HomeUiState,
    layoutConfig: HomeLayoutConfig,
    isListView: Boolean,
    gridState: LazyGridState,
    onVideoClick: (Video) -> Unit,
    onEnrichChannelMetadata: (Video) -> Unit,
    onContinueWatchingClick: (VideoHistoryEntry) -> Unit,
    onContinueWatchingRemove: (String) -> Unit,
    onShortClick: (List<Video>, Video) -> Unit,
    onSeeAllHistory: () -> Unit,
    onOpenShortsFeed: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = if (isListView) GridCells.Fixed(1) else layoutConfig.cells,
        modifier =
            modifier
                .fillMaxSize()
                .testTag("home_feed"),
        state = gridState,
        contentPadding =
            PaddingValues(
                start = if (isListView) 0.dp else layoutConfig.contentPadding,
                end = if (isListView) 0.dp else layoutConfig.contentPadding,
                top = 4.dp,
                bottom = flowBottomContentPadding(),
            ),
        horizontalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
    ) {
        val videos = uiState.videos
        if (videos.isNotEmpty()) {
            val insertShortsAfter = layoutConfig.shortsShelfAfterIndex.coerceAtMost(videos.size)

            feedVideos(
                videos = videos.take(insertShortsAfter),
                isListView = isListView,
                onVideoClick = onVideoClick,
                onEnrichChannelMetadata = onEnrichChannelMetadata,
            )

            if (uiState.continueWatchingVideos.isNotEmpty()) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "continue_watching_shelf",
                ) {
                    ContinueWatchingShelf(
                        entries = uiState.continueWatchingVideos,
                        onVideoClick = { videoId ->
                            uiState.continueWatchingVideos
                                .find { it.videoId == videoId }
                                ?.let(onContinueWatchingClick)
                        },
                        onRemove = onContinueWatchingRemove,
                        onSeeAllClick = onSeeAllHistory,
                        modifier = Modifier.testTag("home_continue_watching_shelf"),
                    )
                }
            }

            if (uiState.shorts.isNotEmpty()) {
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "shorts_shelf",
                ) {
                    MediaShortsShelf(
                        shorts = uiState.shorts,
                        onShortClick = onShortClick,
                        onSeeAllClick = onOpenShortsFeed,
                        modifier = Modifier.testTag("home_shorts_shelf"),
                    )
                }
            }

            feedVideos(
                videos = videos.drop(insertShortsAfter),
                isListView = isListView,
                onVideoClick = onVideoClick,
                onEnrichChannelMetadata = onEnrichChannelMetadata,
            )
        }

        if (uiState.isLoadingMore) {
            item(
                key = "loading_indicator",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                FlowFeedProgress()
            }
        }

        if (!uiState.hasMorePages && videos.size > FEED_FOOTER_MIN_VIDEOS && !uiState.isLoadingMore) {
            item(
                key = "feed_footer",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                FlowFeedFooter(
                    videoCount = videos.size,
                    onRefresh = onRefresh,
                )
            }
        }
    }
}

private fun LazyGridScope.feedVideos(
    videos: List<Video>,
    isListView: Boolean,
    onVideoClick: (Video) -> Unit,
    onEnrichChannelMetadata: (Video) -> Unit,
) {
    items(
        items = videos,
        key = { it.id },
    ) { video ->
        HomeFeedVideoItem(
            video = video,
            isListView = isListView,
            onVideoClick = onVideoClick,
            onEnrichChannelMetadata = onEnrichChannelMetadata,
        )
    }
}

@Composable
private fun LazyGridItemScope.HomeFeedVideoItem(
    video: Video,
    isListView: Boolean,
    onVideoClick: (Video) -> Unit,
    onEnrichChannelMetadata: (Video) -> Unit,
) {
    LaunchedEffect(video.id, video.channelId, video.channelThumbnailUrl) {
        onEnrichChannelMetadata(video)
    }
    if (isListView) {
        MediaVideoCard(
            video = video,
            layout = VideoCardLayout.Row,
            onClick = { onVideoClick(video) },
            modifier = Modifier.testTag("home_video_card"),
        )
    } else {
        MediaVideoCard(
            video = video,
            onClick = { onVideoClick(video) },
            useInternalPadding = false,
            modifier = Modifier.testTag("home_video_card"),
        )
    }
}
