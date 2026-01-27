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

// Add this import for snapshotFlow
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onShortClick: (Video) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
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
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "FLOW",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 2.sp
                        )
                    ) 
                },
                actions = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search")
                    }
                    IconButton(onClick = onNotificationClick) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                modifier = Modifier.size(26.dp)
                            )
                            if (unreadNotifications > 0) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 2.dp, y = (-2).dp)
                                        .background(Color.Red, shape = CircleShape)
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                        .heightIn(min = 16.dp)
                                        .widthIn(min = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (unreadNotifications > 9) "9+" else unreadNotifications.toString(),
                                        color = Color.White,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 10.sp, 
                                            fontWeight = FontWeight.Bold,
                                            lineHeight = 10.sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val isPortrait = maxWidth < 600.dp
            val gridCells = if (isPortrait) GridCells.Fixed(1) else GridCells.Adaptive(300.dp)

            when {
                uiState.isLoading && uiState.videos.isEmpty() -> {
                    // Initial loading state
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(10) {
                            ShimmerVideoCardFullWidth()
                        }
                    }
                }
                
                uiState.error != null && uiState.videos.isEmpty() -> {
                    ErrorState(
                        message = uiState.error ?: "An error occurred",
                        onRetry = { viewModel.retry() }
                    )
                }
                
                else -> {
                    LazyVerticalGrid(
                        columns = gridCells,
                        modifier = Modifier.fillMaxSize(),
                        state = gridState,
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val videos = uiState.videos
                        if (videos.isNotEmpty()) {
                            // First video
                            item(
                                key = videos[0].id,
                                span = { GridItemSpan(1) }
                            ) {
                                VideoCardFullWidth(
                                    video = videos[0],
                                    onClick = { onVideoClick(videos[0]) }
                                )
                            }
                            
                            // Shorts Shelf
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
                            
                            // Remaining videos
                            items(
                                items = videos.drop(1),
                                key = { video -> video.id }
                            ) { video ->
                                VideoCardFullWidth(
                                    video = video,
                                    onClick = { onVideoClick(video) }
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
            text = "Your personalized feed",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$videoCount videos curated just for you",
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
            Text("Refresh Feed")
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
                Text("Retry")
            }
        }
    }
}