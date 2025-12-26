package com.flow.youtube.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.components.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideoClick: (Video) -> Unit,
    onSearchClick: () -> Unit,
    onNotificationClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    
    // Initialize ViewModel with context
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    // Infinite scroll detection (when hasMorePages is true, meaning trending fallback)
    val isScrolledToEnd by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && 
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    
    // Trigger load more when scrolled near the end
    LaunchedEffect(isScrolledToEnd) {
        if (isScrolledToEnd && !uiState.isLoadingMore && uiState.hasMorePages) {
            viewModel.loadMoreVideos()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBarWithActions(
            title = "Flow",
            onSearchClick = onSearchClick,
            onNotificationClick = onNotificationClick,
            onSettingsClick = onSettingsClick
        )

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
                // Error state
                ErrorState(
                    message = uiState.error ?: "An error occurred",
                    onRetry = { viewModel.retry() }
                )
            }
            
            else -> {
                // Content state - LazyColumn with manual refresh via indicator
                Box(modifier = Modifier.fillMaxSize()) {
                    // Refresh indicator at top when refreshing
                    if (uiState.isRefreshing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(
                            items = uiState.videos,
                            key = { video -> video.id }
                        ) { video ->
                            VideoCardFullWidth(
                                video = video,
                                onClick = { onVideoClick(video) }
                            )
                            
                            // Insert Shorts shelf after the first video (only if shorts are available)
                            if (uiState.videos.indexOf(video) == 0 && uiState.shorts.isNotEmpty()) {
                                ShortsShelf(
                                    shorts = uiState.shorts,
                                    onShortClick = { onVideoClick(it) }
                                )
                            }
                        }
                        
                        // Loading more indicator (only for trending)
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        
                        // Flow feed footer with refresh
                        if (uiState.videos.isNotEmpty()) {
                            item {
                                FlowFeedFooter(
                                    videoCount = uiState.videos.size,
                                    onRefresh = { viewModel.refreshFeed(context) }
                                )
                            }
                        }
                    }
                }
            }
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

