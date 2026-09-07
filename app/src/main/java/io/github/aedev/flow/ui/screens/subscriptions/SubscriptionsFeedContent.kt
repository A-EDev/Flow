package io.github.aedev.flow.ui.screens.subscriptions

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.ui.components.ShortsShelf
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.components.VideoCardHorizontal
import io.github.aedev.flow.ui.components.rememberFeedGridLayout
import io.github.aedev.flow.ui.components.shared.FlowFilterChip
import io.github.aedev.flow.ui.components.shared.FlowPullToRefreshBox

private val GroupRowHorizontalPadding = 12.dp
private val GroupRowVerticalPadding = 8.dp
private val GroupChipSpacing = 8.dp
private val GroupEditButtonSize = 32.dp
private val GroupEditGlyphSize = 18.dp
private val HeaderTopPadding = 8.dp
private val HeaderBottomPadding = 12.dp
private val ShelfSpacing = 8.dp
private val ErrorCardPadding = 4.dp
private val FeedBottomSpacer = 80.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubscriptionsFeedContent(
    state: SubscriptionsUiState,
    videos: List<Video>,
    topChannels: List<Channel>,
    gridState: LazyGridState,
    onRefresh: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onShortClick: (ShortsQueueSource) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onVideoChannelClick: (String) -> Unit,
    onViewAllClick: () -> Unit,
    onGroupSelected: (String?) -> Unit,
    onManageGroups: () -> Unit,
    onRetryFailedChannels: () -> Unit,
    onDismissFailedChannels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            pullRefreshState.animateToHidden()
        }
    }

    FlowPullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = modifier.fillMaxSize(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val feedLayout = rememberFeedGridLayout(maxWidth)
            val gridSpacing = if (state.isFullWidthView) feedLayout.cardSpacing else 0.dp
            LazyVerticalGrid(
                columns = if (state.isFullWidthView) GridCells.Fixed(feedLayout.columns) else GridCells.Fixed(1),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                    PaddingValues(
                        start = if (state.isFullWidthView) feedLayout.contentPadding else 0.dp,
                        end = if (state.isFullWidthView) feedLayout.contentPadding else 0.dp,
                        top = 4.dp,
                        bottom = FeedBottomSpacer,
                    ),
                verticalArrangement = Arrangement.spacedBy(gridSpacing),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        SubscriptionsQuickAccessRow(
                            channels = topChannels,
                            onChannelClick = onChannelClick,
                            onViewAllClick = onViewAllClick,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = HeaderTopPadding, bottom = HeaderBottomPadding),
                        )

                        HorizontalDivider()

                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                                    .padding(
                                        horizontal = GroupRowHorizontalPadding,
                                        vertical = GroupRowVerticalPadding,
                                    ),
                            horizontalArrangement = Arrangement.spacedBy(GroupChipSpacing),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            FlowFilterChip(
                                label = stringResource(R.string.group_all),
                                selected = state.selectedGroupName == null,
                                onClick = { onGroupSelected(null) },
                            )
                            state.groups.forEach { group ->
                                FlowFilterChip(
                                    label = group.name,
                                    selected = state.selectedGroupName == group.name,
                                    onClick = { onGroupSelected(group.name) },
                                )
                            }
                            IconButton(
                                onClick = onManageGroups,
                                modifier = Modifier.size(GroupEditButtonSize),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = stringResource(R.string.manage_groups),
                                    modifier = Modifier.size(GroupEditGlyphSize),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        SubscriptionFeedErrorCard(
                            failedChannelNames = state.failedChannelNames,
                            failedChannelIds = state.failedChannelIds,
                            failedChannelReasons = state.failedChannelReasons,
                            onRetry = onRetryFailedChannels,
                            onDismiss = onDismissFailedChannels,
                            modifier =
                                Modifier.padding(
                                    horizontal = GroupRowHorizontalPadding,
                                    vertical = ErrorCardPadding,
                                ),
                        )

                        SubscriptionsRefreshStatus(
                            processedChannels = state.refreshProcessedChannels,
                            totalChannels = state.refreshTotalChannels,
                            lastRefreshText = state.lastRefreshText,
                            lastRefreshVideoCount = state.lastRefreshVideoCount,
                            showLastRefreshVideoCount = state.showLastRefreshVideoCount,
                        )
                    }
                }

                if (state.isShortsShelfEnabled && state.shorts.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            ShortsShelf(
                                shorts = state.shorts,
                                // The shelf shows one reel per channel; the queue behind it is
                                // every subscription reel in date order (#823).
                                onShortClick = { _, tapped ->
                                    onShortClick(ShortsQueueSource.Subscriptions(tapped.id))
                                },
                            )
                            Spacer(modifier = Modifier.height(ShelfSpacing))
                            HorizontalDivider()
                        }
                    }
                }

                items(videos, key = { it.id }) { video ->
                    if (state.isFullWidthView) {
                        VideoCardFullWidth(
                            video = video,
                            onClick = { onVideoClick(video) },
                            onChannelClick = onVideoChannelClick,
                            useInternalPadding = false,
                        )
                    } else {
                        VideoCardHorizontal(
                            video = video,
                            onClick = { onVideoClick(video) },
                            onChannelClick = onVideoChannelClick,
                        )
                    }
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(modifier = Modifier.height(FeedBottomSpacer))
                }
            }
        }
    }
}
