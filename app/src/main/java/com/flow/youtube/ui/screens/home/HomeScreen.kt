package com.flow.youtube.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.components.*
import com.flow.youtube.ui.screens.notifications.NotificationViewModel
import androidx.compose.ui.res.stringResource
import com.flow.youtube.R

// Add this import for snapshotFlow
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.ui.unit.Dp

private data class HomeLayoutConfig(
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp,
    val shortsShelfAfterIndex: Int,
    val shimmerColumns: Int
)

@Composable
private fun rememberHomeLayoutConfig(maxWidth: Dp): HomeLayoutConfig {
    return remember(maxWidth) {
        when {
            maxWidth < 480.dp -> HomeLayoutConfig(
                columns = 1,
                contentPadding = 0.dp,
                cardSpacing = 12.dp,
                shortsShelfAfterIndex = 1,
                shimmerColumns = 1
            )
            maxWidth < 700.dp -> HomeLayoutConfig(
                columns = 1,
                contentPadding = 12.dp,
                cardSpacing = 14.dp,
                shortsShelfAfterIndex = 2,
                shimmerColumns = 1
            )
            maxWidth < 900.dp -> HomeLayoutConfig(
                columns = 2,
                contentPadding = 16.dp,
                cardSpacing = 12.dp,
                shortsShelfAfterIndex = 2,
                shimmerColumns = 2
            )
            maxWidth < 1200.dp -> HomeLayoutConfig(
                columns = 3,
                contentPadding = 20.dp,
                cardSpacing = 14.dp,
                shortsShelfAfterIndex = 3,
                shimmerColumns = 3
            )
            else -> HomeLayoutConfig(
                columns = 4,
                contentPadding = 24.dp,
                cardSpacing = 16.dp,
                shortsShelfAfterIndex = 4,
                shimmerColumns = 4
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onShortClick: (Video) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
    notificationViewModel: NotificationViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val unreadNotifications by notificationViewModel.unreadCount.collectAsState()
    val gridState = rememberLazyGridState()
    
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    // --- FIXED INFINITE SCROLL LOGIC ---
    // We use snapshotFlow to monitor the last visible item index.
    LaunchedEffect(gridState) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            
            // Return true if we are near the bottom (threshold: 5 items)
            totalItems > 0 && lastVisibleItemIndex >= (totalItems - 5)
        }
        .distinctUntilChanged() // Only emit when the boolean changes (False -> True)
        .filter { it } // Only proceed if True (we reached bottom)
        .collect {
            // Trigger load more if not already loading and pages exist
            if (!uiState.isLoadingMore && uiState.hasMorePages) {
                viewModel.loadMoreVideos()
            }
        }
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh = { viewModel.refreshFeed() }
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.app_name_uppercase),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp
                        )
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onSearchClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(
                            onClick = onNotificationClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Icon(
                                    imageVector = Icons.Outlined.Notifications,
                                    contentDescription = stringResource(R.string.notifications),
                                    modifier = Modifier.size(24.dp)
                                )
                                if (unreadNotifications > 0) {
                                    Box(
                                        modifier = Modifier
                                            .offset(x = 4.dp, y = (-2).dp)
                                            .background(MaterialTheme.colorScheme.primary, shape = CircleShape)
                                            .size(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (unreadNotifications > 9) stringResource(R.string.notification_badge_9_plus) else unreadNotifications.toString(),
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                lineHeight = 9.sp
                                            )
                                        )
                                    }
                                }
                            }
                        }
                        IconButton(
                            onClick = onSettingsClick,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                Icons.Outlined.Settings,
                                contentDescription = stringResource(R.string.settings),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val layoutConfig = rememberHomeLayoutConfig(maxWidth)
            val gridCells = GridCells.Fixed(layoutConfig.columns)

            when {
                uiState.isLoading && uiState.videos.isEmpty() -> {
                    // Initial loading state — matches grid layout
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(layoutConfig.shimmerColumns),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = layoutConfig.contentPadding,
                            end = layoutConfig.contentPadding,
                            top = 8.dp,
                            bottom = 80.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
                        verticalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
                        userScrollEnabled = false
                    ) {
                        items(12) {
                            if (layoutConfig.shimmerColumns == 1) {
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
                        onRetry = { viewModel.retry() }
                    )
                }
                
                else -> {
                    LazyVerticalGrid(
                        columns = gridCells,
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = layoutConfig.contentPadding,
                            end = layoutConfig.contentPadding,
                            top = 4.dp, 
                            bottom = 80.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
                        verticalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing)
                    ) {
                        val videos = uiState.videos
                        if (videos.isNotEmpty()) {
                            val insertShortsAfter = layoutConfig.shortsShelfAfterIndex.coerceAtMost(videos.size)
                            
                            // ── Videos before shelves ──
                            val videosBeforeShorts = videos.take(insertShortsAfter)
                            items(
                                items = videosBeforeShorts,
                                key = { it.id }
                            ) { video ->
                                VideoCardFullWidth(
                                    video = video,
                                    onClick = { onVideoClick(video) },
                                    onChannelClick = { channelId -> onChannelClick(channelId) },
                                    useInternalPadding = false
                                )
                            }
                            
                            // ── Continue Watching Shelf (between first videos and shorts) ──
                            if (uiState.continueWatchingVideos.isNotEmpty()) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) },
                                    key = "continue_watching_shelf"
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
                                                        uploadDate = ""
                                                    )
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                            
                            // ── Shorts Shelf ──
                            if (uiState.shorts.isNotEmpty()) {
                                item(
                                    span = { GridItemSpan(maxLineSpan) }, 
                                    key = "shorts_shelf"
                                ) {
                                    ShortsShelf(
                                        shorts = uiState.shorts,
                                        onShortClick = { onShortClick(it) }
                                    )
                                }
                            }
                            
                            // ── Remaining Videos ──
                            val videosAfterShorts = videos.drop(insertShortsAfter)
                            items(
                                items = videosAfterShorts,
                                key = { it.id }
                            ) { video ->
                                VideoCardFullWidth(
                                    video = video,
                                    onClick = { onVideoClick(video) },
                                    onChannelClick = { channelId -> onChannelClick(channelId) },
                                    useInternalPadding = false
                                )
                            }
                        }
                        
                        if (uiState.isLoadingMore) {
                            item(
                                key = "loading_indicator",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(32.dp),
                                        strokeWidth = 3.dp
                                    )
                                }
                            }
                        }
                        
                        // End of feed indicator
                        if (!uiState.hasMorePages && uiState.videos.size > 100 && !uiState.isLoadingMore) {
                            item(
                                key = "feed_footer",
                                span = { GridItemSpan(maxLineSpan) }
                            ) {
                                FlowFeedFooter(
                                    videoCount = uiState.videos.size,
                                    onRefresh = { viewModel.refreshFeed() }
                                )
                            }
                        }
                    }
                }
            }
            
            PullRefreshIndicator(
                refreshing = uiState.isRefreshing,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                backgroundColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun FlowFeedFooter(
    videoCount: Int,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(32.dp)
        )
        Text(
            text = stringResource(R.string.personalized_feed),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = stringResource(R.string.videos_curated_template, videoCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedButton(
            onClick = onRefresh,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(androidx.compose.ui.res.stringResource(R.string.home_refresh_feed))
        }
    }
}

@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = onRetry) {
                Text(androidx.compose.ui.res.stringResource(R.string.retry))
            }
        }
    }
}