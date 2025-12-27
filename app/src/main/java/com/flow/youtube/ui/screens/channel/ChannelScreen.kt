package com.flow.youtube.ui.screens.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.theme.extendedColors
import com.flow.youtube.ui.components.VideoCardFullWidth
import androidx.paging.compose.itemKey

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelUrl: String,
    onVideoClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val videosPagingFlow by viewModel.videosPagingFlow.collectAsState()
    
    // Collect paging items when flow is available
    val videosLazyPagingItems = videosPagingFlow?.collectAsLazyPagingItems()
    
    // Initialize view model
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }
    
    // Load channel info
    LaunchedEffect(channelUrl) {
        viewModel.loadChannel(channelUrl)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = uiState.channelInfo?.name ?: "Channel",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                
                uiState.error != null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = uiState.error ?: "Failed to load channel",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        TextButton(onClick = { viewModel.loadChannel(channelUrl) }) {
                            Text("Retry")
                        }
                    }
                }
                
                uiState.channelInfo != null -> {
                    ChannelContent(
                        uiState = uiState,
                        videosLazyPagingItems = videosLazyPagingItems,
                        onVideoClick = onVideoClick,
                        onSubscribeClick = { viewModel.toggleSubscription() },
                        onTabSelected = { viewModel.selectTab(it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelContent(
    uiState: ChannelUiState,
    videosLazyPagingItems: LazyPagingItems<Video>?,
    onVideoClick: (String) -> Unit,
    onSubscribeClick: () -> Unit,
    onTabSelected: (Int) -> Unit
) {
    val channelInfo = uiState.channelInfo ?: return
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        // Banner
        item {
            val bannerUrl = try { channelInfo.banners?.firstOrNull()?.url } catch (e: Exception) { null }
            if (!bannerUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = "Banner",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // Header
        item {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val avatarUrl = try { channelInfo.avatars?.firstOrNull()?.url } catch (e: Exception) { null }
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar",
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = channelInfo.name,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatSubscriberCount(channelInfo.subscriberCount),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.textSecondary
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onSubscribeClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isSubscribed) 
                            MaterialTheme.colorScheme.surfaceVariant 
                        else 
                            MaterialTheme.colorScheme.primary,
                        contentColor = if (uiState.isSubscribed) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text(if (uiState.isSubscribed) "Subscribed" else "Subscribe")
                }
            }
        }
        
        // Tabs
        item {
            TabRow(selectedTabIndex = uiState.selectedTab) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { onTabSelected(0) },
                    text = { Text("Videos") }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { onTabSelected(1) },
                    text = { Text("Playlists") }
                )
                Tab(
                    selected = uiState.selectedTab == 2,
                    onClick = { onTabSelected(2) },
                    text = { Text("About") }
                )
            }
        }
        
        // Content
        if (uiState.selectedTab == 0 && videosLazyPagingItems != null) {
             items(
                count = videosLazyPagingItems.itemCount,
                key = videosLazyPagingItems.itemKey { it.id }
            ) { index ->
                val video = videosLazyPagingItems[index]
                if (video != null) {
                    VideoCardFullWidth(
                        video = video,
                        onClick = { onVideoClick(video.id) }
                    )
                }
            }
        } else if (uiState.selectedTab == 2) {
            item {
                AboutSection(channelInfo = channelInfo)
            }
        } else if (uiState.selectedTab == 1) {
             item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Playlists coming soon")
                }
            }
        }
    }
}

@Composable
private fun AboutSection(
    channelInfo: org.schabi.newpipe.extractor.channel.ChannelInfo
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "About",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        
        if (channelInfo.description != null && channelInfo.description.isNotEmpty()) {
            Text(
                text = channelInfo.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        } else {
            Text(
                text = "No description available",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        Divider()
        
        // Stats
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Stats",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Subscribers",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textSecondary
                )
                Text(
                    text = formatSubscriberCount(channelInfo.subscriberCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatSubscriberCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM subscribers", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK subscribers", count / 1_000.0)
        else -> "$count subscribers"
    }
}

private fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
