package com.flow.youtube.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.flow.youtube.data.model.Channel
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.components.*
import com.flow.youtube.ui.theme.extendedColors

@Composable
fun SubscriptionsScreen(
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit = {},
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Initialize view model
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    val subscribedChannels = uiState.subscribedChannels
    val videos = uiState.recentVideos

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Subscriptions",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row {
                IconButton(onClick = { viewModel.toggleViewMode() }) {
                    Icon(
                        imageVector = if (uiState.isFullWidthView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle View Mode"
                    )
                }
                IconButton(onClick = { viewModel.refreshFeed() }) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (subscribedChannels.isEmpty()) {
            // Empty State
            EmptySubscriptionsState(modifier = Modifier.fillMaxSize())
        } else {
            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            // Channel Carousel
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                items(subscribedChannels) { channel ->
                    ChannelChip(
                        channel = channel,
                        onClick = {
                            viewModel.selectChannel(
                                if (uiState.selectedChannelId == channel.id) null else channel.id
                            )
                            // Navigate to channel screen
                            val channelUrl = "https://youtube.com/channel/${channel.id}"
                            onChannelClick(channelUrl)
                        },
                        onLongClick = {
                            // Unsubscribe with Undo snackbar
                            scope.launch {
                                // capture subscription snapshot to allow undo
                                val sub = viewModel.getSubscriptionOnce(channel.id)
                                viewModel.unsubscribe(channel.id)
                                val result = snackbarHostState.showSnackbar("Unsubscribed from ${channel.name}", actionLabel = "Undo")
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    sub?.let { viewModel.subscribeChannel(it) }
                                }
                            }
                        }
                    )
                }
            }

            // Videos Feed
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                if (uiState.shorts.isNotEmpty()) {
                    item {
                        ShortsShelf(
                            shorts = uiState.shorts,
                            onShortClick = { short -> onShortClick(short.id) }
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                items(videos) { video ->
                    if (uiState.isFullWidthView) {
                        VideoCardFullWidth(
                            video = video,
                            onClick = { onVideoClick(video) }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    } else {
                        VideoCardHorizontal(
                            video = video,
                            onClick = { onVideoClick(video) }
                        )
                    }
                }
            }
        }
    }

    // Snackbar host
    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.material3.SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun EmptySubscriptionsState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📺",
            style = MaterialTheme.typography.displayLarge,
            fontSize = MaterialTheme.typography.displayLarge.fontSize * 2
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No Subscriptions Yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Subscribe to channels to see their\nlatest videos here",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

