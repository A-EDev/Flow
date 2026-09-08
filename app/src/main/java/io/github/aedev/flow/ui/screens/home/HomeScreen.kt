package io.github.aedev.flow.ui.screens.home

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.data.local.HomeViewMode
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.player.DeepFlowManager
import io.github.aedev.flow.ui.TabScrollEventBus
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.FlowPullToRefreshBox
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val IMPRESSION_DEBOUNCE_MS = 500L
private const val MILLIS_PER_SECOND = 1000L

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onShortClick: (ShortsQueueSource) -> Unit,
    onSearchClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onOpenShortsFeed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = remember(context) { PlayerPreferences(context) }
    val homeViewMode by preferences.homeViewMode.collectAsStateWithLifecycle(initialValue = HomeViewMode.GRID)
    val homeFeedColumns by preferences.homeFeedColumns.collectAsStateWithLifecycle(initialValue = HomeFeedColumns.AUTO)
    val homeFeedEnabled by preferences.homeFeedEnabled.collectAsStateWithLifecycle(initialValue = true)
    val refreshHomeOnReselect by preferences.refreshHomeOnReselect.collectAsStateWithLifecycle(initialValue = true)
    val showAppLogoIcon by preferences.showAppLogoIcon.collectAsStateWithLifecycle(initialValue = true)
    val deepFlowActive by preferences.deepFlowActive.collectAsStateWithLifecycle(initialValue = false)

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    LifecycleStartEffect(viewModel, homeFeedEnabled) {
        if (homeFeedEnabled) {
            viewModel.onHomeVisible()
        } else {
            viewModel.onHomeHidden()
        }
        onStopOrDispose { viewModel.onHomeHidden() }
    }

    val videoIndexById =
        remember(uiState.videos) {
            buildMap(uiState.videos.size) {
                uiState.videos.forEachIndexed { index, video -> put(video.id, index) }
            }
        }
    // Pairing the index with the scroll state matters: a user parked on the last item keeps the
    // same index, so an index-only flow can never re-arm a prefetch that came back empty. Each
    // further scroll attempt toggles isScrollInProgress and gives the feed another chance.
    LaunchedEffect(gridState, videoIndexById) {
        snapshotFlow {
            var lastVisibleVideoIndex = -1
            gridState.layoutInfo.visibleItemsInfo.forEach { item ->
                val index = videoIndexById[item.key as? String] ?: return@forEach
                if (index > lastVisibleVideoIndex) lastVisibleVideoIndex = index
            }
            lastVisibleVideoIndex to gridState.isScrollInProgress
        }.distinctUntilChanged()
            .collect { (lastVisibleVideoIndex, _) ->
                viewModel.onHomeViewportChanged(lastVisibleVideoIndex)
            }
    }

    // Viewport impressions: only items dwelt in view are recorded as "shown".
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .debounce(IMPRESSION_DEBOUNCE_MS)
            .collect { viewModel.recordImpressions(it) }
    }

    LaunchedEffect(refreshHomeOnReselect) {
        TabScrollEventBus.scrollToTopEvents
            .filter { it == "home" }
            .collectLatest {
                gridState.animateScrollToItem(0)
                if (refreshHomeOnReselect) {
                    viewModel.refreshFeed()
                }
            }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = {
                    Text(
                        stringResource(R.string.app_name_uppercase),
                        style =
                            MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp,
                            ),
                    )
                },
                leading =
                    if (showAppLogoIcon) {
                        {
                            FlowHeaderLogoIcon(
                                isDeepFlowActive = deepFlowActive,
                                onToggleDeepFlow = {
                                    coroutineScope.launch {
                                        DeepFlowManager.toggle(context)
                                    }
                                },
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    } else {
                        null
                    },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                },
            )
        },
    ) { padding ->
        ResettableHomePullToRefreshBox(
            resetKey = uiState.isLoading && uiState.videos.isEmpty(),
            isRefreshing = uiState.isRefreshing,
            onRefresh = { viewModel.refreshFeed() },
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isListView = homeViewMode == HomeViewMode.LIST
                val layoutConfig = rememberHomeLayoutConfig(maxWidth, homeFeedColumns)

                when {
                    !homeFeedEnabled -> {
                        HomeFeedDisabledState(modifier = Modifier.fillMaxSize())
                    }

                    uiState.isLoading && uiState.videos.isEmpty() -> {
                        HomeFeedShimmer(layoutConfig = layoutConfig, isListView = isListView)
                    }

                    uiState.error != null && uiState.videos.isEmpty() -> {
                        FlowErrorState(
                            error = uiState.error ?: stringResource(R.string.error_occurred),
                            onRetry = { viewModel.retry() },
                        )
                    }

                    else -> {
                        ReportDrawnWhen {
                            uiState.videos.isNotEmpty() ||
                                uiState.shorts.isNotEmpty() ||
                                uiState.continueWatchingVideos.isNotEmpty()
                        }

                        HomeFeedGrid(
                            uiState = uiState,
                            layoutConfig = layoutConfig,
                            isListView = isListView,
                            gridState = gridState,
                            onVideoClick = onVideoClick,
                            onChannelClick = onChannelClick,
                            onEnrichChannelMetadata = viewModel::enrichChannelMetadataIfMissing,
                            onContinueWatchingClick = { entry -> onVideoClick(entry.toResumeVideo()) },
                            onContinueWatchingRemove = viewModel::removeContinueWatchingEntry,
                            onShortClick = { shelf, tapped ->
                                onShortClick(viewModel.shortsShelfSource(shelf, tapped))
                            },
                            onSeeAllHistory = onNavigateToHistory,
                            onOpenShortsFeed = onOpenShortsFeed,
                            onRefresh = { viewModel.refreshFeed() },
                        )
                    }
                }
            }
        }
    }
}

// Deliberately not data/model's VideoHistoryEntry.toVideo(): that one carries isShort, which would
// reroute a resumed short to the Shorts player instead of the video player.
private fun VideoHistoryEntry.toResumeVideo(): Video =
    Video(
        id = videoId,
        title = title,
        channelName = channelName,
        channelId = channelId,
        thumbnailUrl = thumbnailUrl,
        duration = (duration / MILLIS_PER_SECOND).toInt(),
        viewCount = 0L,
        uploadDate = "",
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResettableHomePullToRefreshBox(
    resetKey: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    key(resetKey) {
        FlowPullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            content = content,
        )
    }
}
