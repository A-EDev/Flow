package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.distinctUntilChanged
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.home.HomeViewModel
import io.github.aedev.flow.ui.tv.components.TvButton
import io.github.aedev.flow.ui.tv.components.TvMediaRow
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvScreenScaffold
import io.github.aedev.flow.ui.tv.components.TvSectionHeader
import io.github.aedev.flow.ui.tv.components.TvShimmerRow
import io.github.aedev.flow.ui.tv.components.TvVideoCard
import io.github.aedev.flow.ui.tv.focus.ProvideTvColumnPivot
import io.github.aedev.flow.ui.tv.theme.LocalTvDimens
import io.github.aedev.flow.ui.tv.toTvVideo
import io.github.aedev.flow.ui.tv.tvWatchProgress

private const val HOME_GRID_COLUMNS = 3

@Composable
fun TvHomeScreen(
    viewModel: HomeViewModel,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dimens = LocalTvDimens.current
    val listState = rememberLazyListState()
    val hasContinueWatching = state.continueWatchingVideos.isNotEmpty()

    LaunchedEffect(viewModel) {
        viewModel.initialize(context.applicationContext)
    }

    // Same viewport-driven prefetch mobile uses: report the last visible video
    // index so HomeViewModel keeps extending the Flow feed while scrolling.
    LaunchedEffect(listState, hasContinueWatching) {
        val fixedItemsBefore = (if (hasContinueWatching) 1 else 0) + 1
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1 }
            .distinctUntilChanged()
            .collect { lastItem ->
                val rowIndex = lastItem - fixedItemsBefore
                if (rowIndex >= 0) {
                    viewModel.onHomeViewportChanged((rowIndex + 1) * HOME_GRID_COLUMNS - 1)
                }
            }
    }
    DisposableEffect(viewModel) {
        viewModel.onHomeVisible()
        onDispose { viewModel.onHomeHidden() }
    }

    TvScreenScaffold(
        title = stringResource(R.string.tv_home_title),
        modifier = modifier,
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
                // One shared card width so shelf and grid cards line up exactly.
                val cardWidth = (maxWidth - dimens.overscanHorizontal * 2 -
                    dimens.itemSpacing * (HOME_GRID_COLUMNS - 1)) / HOME_GRID_COLUMNS
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
                    contentPadding = PaddingValues(bottom = dimens.overscanVertical),
                ) {
                    if (state.continueWatchingVideos.isNotEmpty()) {
                        item(key = "continue-watching") {
                            TvMediaRow(
                                items = state.continueWatchingVideos,
                                key = { it.videoId },
                                title = stringResource(R.string.tv_continue_watching),
                            ) { entry ->
                                val video = remember(entry) { entry.toTvVideo() }
                                TvVideoCard(
                                    video = video,
                                    onClick = { onVideoClick(video) },
                                    modifier = Modifier.width(cardWidth),
                                    watchProgress = entry.tvWatchProgress(),
                                )
                            }
                        }
                    }

                    when {
                        state.isLoading && state.videos.isEmpty() -> item(key = "home-loading") {
                            TvShimmerRow()
                        }
                        state.error != null && state.videos.isEmpty() -> item(key = "home-error") {
                            Box(Modifier.fillMaxWidth().padding(horizontal = dimens.overscanHorizontal)) {
                                TvMessageState(
                                    title = stringResource(R.string.tv_error_loading),
                                    message = state.error,
                                )
                            }
                        }
                        state.videos.isEmpty() -> item(key = "home-empty") {
                            Box(Modifier.fillMaxWidth().padding(horizontal = dimens.overscanHorizontal)) {
                                TvMessageState(title = stringResource(R.string.tv_no_recommendations))
                            }
                        }
                        else -> {
                            item(key = "recommended-header") {
                                TvSectionHeader(
                                    title = stringResource(R.string.tv_home_recommended),
                                    modifier = Modifier.padding(horizontal = dimens.overscanHorizontal),
                                )
                            }
                            items(
                                items = state.videos.chunked(HOME_GRID_COLUMNS),
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
                            if (state.isLoadingMore) {
                                item(key = "home-loading-more") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.size(32.dp))
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
