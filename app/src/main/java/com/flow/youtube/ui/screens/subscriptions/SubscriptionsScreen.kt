package com.flow.youtube.ui.screens.subscriptions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
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
    onChannelClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    
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
        Text(
            text = "Subscriptions",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (subscribedChannels.isEmpty()) {
            // Empty State
            EmptySubscriptionsState(modifier = Modifier.fillMaxSize())
        } else {
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
                        }
                    )
                }
            }

            // Videos Feed
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp)
            ) {
                items(videos) { video ->
                    VideoCardHorizontal(
                        video = video,
                        onClick = { onVideoClick(video) }
                    )
                }
            }
        }
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

