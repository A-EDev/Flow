package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.subscriptions.SubscriptionsViewModel
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvChannelCard
import io.github.aedev.flow.ui.tv.components.TvFilterChip
import io.github.aedev.flow.ui.tv.components.TvMediaRow
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.components.TvSectionHeader
import io.github.aedev.flow.ui.tv.components.TvShimmerRow
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.focus.ProvideTvColumnPivot
import io.github.aedev.flow.ui.tv.focus.tvRowFocus
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens

private const val SUBS_GRID_COLUMNS = 3

/**
 * Subscriptions feed, mirroring mobile: group chips, channel shelf (opens
 * channel pages), per-channel refresh progress, and the full latest-videos
 * feed as a grid rather than a single shelf.
 */
@Composable
fun TvSubscriptionsScreen(
    viewModel: SubscriptionsViewModel,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
    onChannelClick: (String) -> Unit = {},
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalTvDimens.current

    LaunchedEffect(viewModel) {
        viewModel.initialize(context.applicationContext)
    }

    fun channelRef(channel: Channel): String =
        channel.url.ifBlank { "https://www.youtube.com/channel/${channel.id}" }

    TvScreenScaffold(
        title = stringResource(R.string.tv_subscriptions_title),
        modifier = modifier,
        subtitle = state.lastRefreshText,
        action = {
            TvButton(
                text = stringResource(R.string.action_refresh),
                onClick = viewModel::refreshFeed,
                icon = Icons.Outlined.Refresh,
            )
        },
    ) {
        ProvideTvColumnPivot {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val cardWidth = (maxWidth - dimens.overscanHorizontal * 2 -
                    dimens.itemSpacing * (SUBS_GRID_COLUMNS - 1)) / SUBS_GRID_COLUMNS
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = dimens.overscanVertical),
                ) {
                    if (state.isLoading && state.refreshTotalChannels > 0) {
                        item(key = "refresh-progress") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = dimens.overscanHorizontal),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val progress = state.refreshProcessedChannels.toFloat() /
                                    state.refreshTotalChannels.toFloat().coerceAtLeast(1f)
                                LinearProgressIndicator(
                                    progress = { progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.subscriptions_refresh_progress_template,
                                        state.refreshProcessedChannels,
                                        state.refreshTotalChannels,
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    if (state.groups.isNotEmpty()) {
                        item(key = "groups") {
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .tvRowFocus(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                contentPadding = PaddingValues(horizontal = dimens.overscanHorizontal),
                            ) {
                                item(key = "all") {
                                    TvFilterChip(
                                        label = stringResource(R.string.tv_subscriptions_all),
                                        selected = state.selectedGroupName == null,
                                        onClick = { viewModel.selectGroup(null) },
                                    )
                                }
                                items(state.groups, key = { it.name }) { group ->
                                    TvFilterChip(
                                        label = group.name,
                                        selected = state.selectedGroupName == group.name,
                                        onClick = { viewModel.selectGroup(group.name) },
                                    )
                                }
                            }
                        }
                    }

                    if (state.subscribedChannels.isNotEmpty()) {
                        item(key = "channels") {
                            TvMediaRow(
                                items = state.subscribedChannels,
                                key = Channel::id,
                            ) { channel ->
                                TvChannelCard(
                                    channel = channel,
                                    onClick = { onChannelClick(channelRef(channel)) },
                                )
                            }
                        }
                    }

                    when {
                        state.isLoading && state.recentVideos.isEmpty() -> item(key = "loading") {
                            TvShimmerRow()
                        }
                        state.recentVideos.isEmpty() && state.subscribedChannels.isEmpty() -> item(key = "empty") {
                            TvMessageState(
                                title = stringResource(R.string.tv_no_subscriptions),
                                modifier = Modifier.padding(horizontal = dimens.overscanHorizontal),
                            )
                        }
                        state.recentVideos.isNotEmpty() -> {
                            item(key = "recent-header") {
                                TvSectionHeader(
                                    title = stringResource(R.string.tv_subscriptions_title),
                                    modifier = Modifier.padding(horizontal = dimens.overscanHorizontal),
                                )
                            }
                            items(
                                items = state.recentVideos.chunked(SUBS_GRID_COLUMNS),
                                key = { rowVideos -> rowVideos.first().id },
                            ) { rowVideos ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = dimens.overscanHorizontal),
                                    horizontalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                                ) {
                                    rowVideos.forEach { video ->
                                        TvVideoCard(
                                            video = video,
                                            onClick = { onVideoClick(video) },
                                            modifier = Modifier.width(cardWidth),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
