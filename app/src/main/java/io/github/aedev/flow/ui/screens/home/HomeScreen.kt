package io.github.aedev.flow.ui.screens.home

import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.DeepFlowManager
import io.github.aedev.flow.ui.TabScrollEventBus
import io.github.aedev.flow.ui.components.*
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.theme.Dimensions
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private data class HomeLayoutConfig(
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp,
    val shortsShelfAfterIndex: Int,
    val shimmerColumns: Int,
)

internal fun featuredFeedShortsInsertionIndex(
    shortsShelfAfterIndex: Int,
    videoCount: Int,
): Int =
    (shortsShelfAfterIndex.coerceIn(0, videoCount) - 1)
        .coerceIn(0, (videoCount - 1).coerceAtLeast(0))

@Composable
private fun rememberHomeLayoutConfig(maxWidth: Dp): HomeLayoutConfig {
    val base = rememberFeedGridLayout(maxWidth)
    return remember(base, maxWidth) {
        val shortsShelfAfterIndex =
            when {
                maxWidth < 480.dp -> 1
                maxWidth < 700.dp -> 2
                maxWidth < 900.dp -> 2
                maxWidth < 1200.dp -> 3
                else -> 4
            }
        HomeLayoutConfig(
            columns = base.columns,
            contentPadding = base.contentPadding,
            cardSpacing = base.cardSpacing,
            shortsShelfAfterIndex = shortsShelfAfterIndex,
            shimmerColumns = base.columns,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, FlowPreview::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onShortClick: (io.github.aedev.flow.data.shorts.queue.ShortsQueueSource) -> Unit,
    onSearchClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    onNavigateToHistory: () -> Unit = {},
    onOpenShortsFeed: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences =
        remember {
            io.github.aedev.flow.data.local
                .PlayerPreferences(context)
        }
    val homeViewMode by preferences.homeViewMode.collectAsStateWithLifecycle(
        initialValue = io.github.aedev.flow.data.local.HomeViewMode.GRID,
    )
    val homeFeedEnabled by preferences.homeFeedEnabled.collectAsStateWithLifecycle(initialValue = true)
    val refreshHomeOnReselect by preferences.refreshHomeOnReselect.collectAsStateWithLifecycle(initialValue = true)
    val showAppLogoIcon by preferences.showAppLogoIcon.collectAsStateWithLifecycle(initialValue = true)
    val deepFlowActive by preferences.deepFlowActive.collectAsStateWithLifecycle(initialValue = false)

    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

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
    LaunchedEffect(gridState, videoIndexById) {
        snapshotFlow {
            var lastVisibleVideoIndex = -1
            gridState.layoutInfo.visibleItemsInfo.forEach { item ->
                val index = videoIndexById[item.key as? String] ?: return@forEach
                if (index > lastVisibleVideoIndex) lastVisibleVideoIndex = index
            }
            lastVisibleVideoIndex
        }.distinctUntilChanged()
            .collect(viewModel::onHomeViewportChanged)
    }

    // Viewport impressions: only items dwelt in view are recorded as "shown".
    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .debounce(500)
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
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.3.sp,
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
                val isListView = homeViewMode == io.github.aedev.flow.data.local.HomeViewMode.LIST
                val layoutConfig = rememberHomeLayoutConfig(maxWidth)
                val gridCells = if (isListView) GridCells.Fixed(1) else GridCells.Fixed(layoutConfig.columns)

                when {
                    !homeFeedEnabled -> {
                        FeedDisabledState(modifier = Modifier.fillMaxSize())
                    }

                    uiState.isLoading && uiState.videos.isEmpty() -> {
                        // Initial loading state — matches grid layout
                        LazyVerticalGrid(
                            columns = if (isListView) GridCells.Fixed(1) else GridCells.Fixed(layoutConfig.shimmerColumns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                PaddingValues(
                                    start = if (isListView) 0.dp else layoutConfig.contentPadding,
                                    end = if (isListView) 0.dp else layoutConfig.contentPadding,
                                    top = 8.dp,
                                    bottom = 0.dp,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
                            verticalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
                            userScrollEnabled = false,
                        ) {
                            items(12) {
                                if (isListView) {
                                    ShimmerVideoCardHorizontal()
                                } else if (layoutConfig.shimmerColumns == 1) {
                                    ShimmerVideoCardFullWidth()
                                } else {
                                    ShimmerGridVideoCard()
                                }
                            }
                        }
                    }

                    uiState.error != null && uiState.videos.isEmpty() -> {
                        ErrorState(
                            message = uiState.error ?: stringResource(R.string.error_occurred),
                            onRetry = { viewModel.retry() },
                        )
                    }

                    else -> {
                        ReportDrawnWhen {
                            uiState.videos.isNotEmpty() || uiState.shorts.isNotEmpty() || uiState.continueWatchingVideos.isNotEmpty()
                        }

                        LazyVerticalGrid(
                            columns = gridCells,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .testTag("home_feed"),
                            state = gridState,
                            contentPadding =
                                PaddingValues(
                                    start = if (isListView) 0.dp else layoutConfig.contentPadding,
                                    end = if (isListView) 0.dp else layoutConfig.contentPadding,
                                    top = 4.dp,
                                    bottom = 0.dp,
                                ),
                            horizontalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
                            verticalArrangement = Arrangement.spacedBy(if (isListView) 0.dp else layoutConfig.cardSpacing),
                        ) {
                            val videos = uiState.videos
                            if (videos.isNotEmpty()) {
                                val featuredVideo = videos.first()
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = featuredVideo.id,
                                ) {
                                    LaunchedEffect(
                                        featuredVideo.id,
                                        featuredVideo.channelId,
                                        featuredVideo.channelThumbnailUrl,
                                    ) {
                                        viewModel.enrichChannelMetadataIfMissing(featuredVideo.id)
                                    }
                                    FlowFeaturedVideoCard(
                                        video = featuredVideo,
                                        onClick = { onVideoClick(featuredVideo) },
                                        onChannelClick = { channelId -> onChannelClick(channelId) },
                                        modifier =
                                            Modifier.padding(
                                                horizontal = if (isListView) Dimensions.ContentPaddingHorizontal else 0.dp,
                                            ),
                                    )
                                }

                                val feedVideos = videos.drop(1)
                                val insertShortsAfter =
                                    featuredFeedShortsInsertionIndex(
                                        shortsShelfAfterIndex = layoutConfig.shortsShelfAfterIndex,
                                        videoCount = videos.size,
                                    )

                                // ── Videos before shelves ──
                                val videosBeforeShorts = feedVideos.take(insertShortsAfter)
                                items(
                                    items = videosBeforeShorts,
                                    key = { it.id },
                                ) { video ->
                                    LaunchedEffect(
                                        video.id,
                                        video.channelId,
                                        video.channelThumbnailUrl,
                                    ) {
                                        viewModel.enrichChannelMetadataIfMissing(video.id)
                                    }
                                    if (isListView) {
                                        VideoCardHorizontal(
                                            video = video,
                                            onClick = { onVideoClick(video) },
                                            onChannelClick = { channelId -> onChannelClick(channelId) },
                                            modifier = Modifier.testTag("home_video_card"),
                                        )
                                    } else {
                                        VideoCardFullWidth(
                                            video = video,
                                            onClick = { onVideoClick(video) },
                                            onChannelClick = { channelId -> onChannelClick(channelId) },
                                            useInternalPadding = false,
                                            modifier = Modifier.testTag("home_video_card"),
                                        )
                                    }
                                }

                                // ── Continue Watching Shelf (between first videos and shorts) ──
                                if (uiState.continueWatchingVideos.isNotEmpty()) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "continue_watching_shelf",
                                    ) {
                                        ContinueWatchingShelf(
                                            entries = uiState.continueWatchingVideos,
                                            onVideoClick = { videoId ->
                                                val entry = uiState.continueWatchingVideos.find { it.videoId == videoId }
                                                if (entry != null) {
                                                    onVideoClick(
                                                        Video(
                                                            id = entry.videoId,
                                                            title = entry.title,
                                                            channelName = entry.channelName,
                                                            channelId = entry.channelId,
                                                            thumbnailUrl = entry.thumbnailUrl,
                                                            duration = (entry.duration / 1000).toInt(),
                                                            viewCount = 0L,
                                                            uploadDate = "",
                                                        ),
                                                    )
                                                }
                                            },
                                            onRemove = { videoId ->
                                                viewModel.removeContinueWatchingEntry(videoId)
                                            },
                                            onSeeAllClick = onNavigateToHistory,
                                            modifier = Modifier.testTag("home_continue_watching_shelf"),
                                        )
                                    }
                                }

                                // ── Shorts Shelf ──
                                if (uiState.shorts.isNotEmpty()) {
                                    item(
                                        span = { GridItemSpan(maxLineSpan) },
                                        key = "shorts_shelf",
                                    ) {
                                        ShortsShelf(
                                            shorts = uiState.shorts,
                                            onShortClick = { shelf, tapped ->
                                                onShortClick(viewModel.shortsShelfSource(shelf, tapped))
                                            },
                                            onSeeAllClick = onOpenShortsFeed,
                                            modifier = Modifier.testTag("home_shorts_shelf"),
                                        )
                                    }
                                }

                                // ── Remaining Videos ──
                                val videosAfterShorts = feedVideos.drop(insertShortsAfter)
                                items(
                                    items = videosAfterShorts,
                                    key = { it.id },
                                ) { video ->
                                    LaunchedEffect(
                                        video.id,
                                        video.channelId,
                                        video.channelThumbnailUrl,
                                    ) {
                                        viewModel.enrichChannelMetadataIfMissing(video.id)
                                    }
                                    if (isListView) {
                                        VideoCardHorizontal(
                                            video = video,
                                            onClick = { onVideoClick(video) },
                                            onChannelClick = { channelId -> onChannelClick(channelId) },
                                        )
                                    } else {
                                        VideoCardFullWidth(
                                            video = video,
                                            onClick = { onVideoClick(video) },
                                            onChannelClick = { channelId -> onChannelClick(channelId) },
                                            useInternalPadding = false,
                                        )
                                    }
                                }
                            }

                            if (uiState.isLoadingMore) {
                                item(
                                    key = "loading_indicator",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .padding(24.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(32.dp),
                                            strokeWidth = 3.dp,
                                        )
                                    }
                                }
                            }

                            // End of feed indicator
                            if (!uiState.hasMorePages && uiState.videos.size > 100 && !uiState.isLoadingMore) {
                                item(
                                    key = "feed_footer",
                                    span = { GridItemSpan(maxLineSpan) },
                                ) {
                                    FlowFeedFooter(
                                        videoCount = uiState.videos.size,
                                        onRefresh = { viewModel.refreshFeed() },
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = modifier,
            content = content,
        )
    }
}

@Composable
private fun FeedDisabledState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.SmartDisplay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp),
            )
            Text(
                text = stringResource(R.string.content_settings_home_feed_disabled_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.content_settings_home_feed_disabled_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}

@Composable
private fun FlowFeedFooter(
    videoCount: Int,
    onRefresh: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = stringResource(R.string.personalized_feed),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = pluralStringResource(R.plurals.videos_curated_template, videoCount, videoCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                androidx.compose.ui.res
                    .stringResource(R.string.home_refresh_feed),
            )
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRetry) {
                Text(
                    androidx.compose.ui.res
                        .stringResource(R.string.retry),
                )
            }
        }
    }
}

// SVG path data — a single unbreakable token, so the line-length rule cannot be satisfied by wrapping.
@Suppress("ktlint:standard:max-line-length")
private const val FLOW_LOGO_BG_PATH = "M21.58 7.16C21.33 6.22 20.59 5.48 19.65 5.23C17.96 4.77 12 4.77 12 4.77C12 4.77 6.04 4.77 4.35 5.23C3.41 5.48 2.67 6.22 2.42 7.16C1.96 8.85 1.96 12.38 1.96 12.38C1.96 12.38 1.96 15.91 2.42 17.6C2.67 18.54 3.41 19.28 4.35 19.53C6.04 19.99 12 19.99 12 19.99C12 19.99 17.96 19.99 19.65 19.53C20.59 19.28 21.33 18.54 21.58 17.6C22.04 15.91 22.04 12.38 22.04 12.38C22.04 12.38 22.04 8.85 21.58 7.16Z"
private const val FLOW_LOGO_GLYPH_PATH = "M10 7L18 7L17.2 9.5H12.8L12.2 11.5H16L15.2 14H11.5L10.5 17H7.5L10 7Z"

@Suppress("ktlint:standard:max-line-length")
private const val FLOW_INCOGNITO_GLYPH_PATH = "M17.06 13C15.2 13 13.64 14.33 13.24 16.1C12.29 15.69 11.42 15.8 10.76 16.09C10.35 14.31 8.79 13 6.94 13C4.77 13 3 14.79 3 17C3 19.21 4.77 21 6.94 21C9 21 10.68 19.38 10.84 17.32C11.18 17.08 12.07 16.63 13.16 17.34C13.34 19.39 15 21 17.06 21C19.23 21 21 19.21 21 17C21 14.79 19.23 13 17.06 13M6.94 19.86C5.38 19.86 4.13 18.58 4.13 17S5.39 14.14 6.94 14.14C8.5 14.14 9.75 15.42 9.75 17S8.5 19.86 6.94 19.86M17.06 19.86C15.5 19.86 14.25 18.58 14.25 17S15.5 14.14 17.06 14.14C18.62 14.14 19.88 15.42 19.88 17S18.61 19.86 17.06 19.86M22 10.5H2V12H22V10.5M15.53 2.63C15.31 2.14 14.75 1.88 14.22 2.05L12 2.79L9.77 2.05L9.72 2.04C9.19 1.89 8.63 2.17 8.43 2.68L6 9H18L15.56 2.68L15.53 2.63Z"

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FlowHeaderLogoIcon(
    isDeepFlowActive: Boolean,
    onToggleDeepFlow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val haptic = LocalHapticFeedback.current

    val bgPath =
        remember {
            PathParser().parsePathString(FLOW_LOGO_BG_PATH).toPath()
        }
    val glyphPath =
        remember {
            PathParser().parsePathString(FLOW_LOGO_GLYPH_PATH).toPath()
        }
    val incognitoPath =
        remember {
            PathParser().parsePathString(FLOW_INCOGNITO_GLYPH_PATH).toPath()
        }

    val glyphAlpha by animateFloatAsState(
        targetValue = if (isDeepFlowActive) 0f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "deepFlowGlyphAlpha",
    )
    val incognitoAlpha by animateFloatAsState(
        targetValue = if (isDeepFlowActive) 1f else 0f,
        animationSpec = tween(durationMillis = 250),
        label = "deepFlowIncognitoAlpha",
    )

    Canvas(
        modifier =
            modifier.combinedClickable(
                onClick = {},
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggleDeepFlow()
                },
            ),
    ) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        drawContext.canvas.save()
        drawContext.canvas.scale(sx, sy)
        drawPath(path = bgPath, color = primaryColor)

        if (glyphAlpha > 0f) {
            drawPath(path = glyphPath, color = onPrimaryColor.copy(alpha = glyphAlpha))
        }
        if (incognitoAlpha > 0f) {
            val incognitoScale = 0.65f
            val incognitoOffset = 12f * (1f - incognitoScale)
            drawContext.canvas.save()
            drawContext.canvas.translate(incognitoOffset, incognitoOffset)
            drawContext.canvas.scale(incognitoScale, incognitoScale)
            drawPath(path = incognitoPath, color = onPrimaryColor.copy(alpha = incognitoAlpha))
            drawContext.canvas.restore()
        }

        drawContext.canvas.restore()
    }
}
