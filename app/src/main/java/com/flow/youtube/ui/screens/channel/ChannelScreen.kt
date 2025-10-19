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
import coil.compose.AsyncImage
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.theme.extendedColors
import org.schabi.newpipe.extractor.stream.StreamInfoItem

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
    onVideoClick: (String) -> Unit,
    onSubscribeClick: () -> Unit,
    onTabSelected: (Int) -> Unit
) {
    val channelInfo = uiState.channelInfo ?: return
    
    LazyColumn(
        modifier = Modifier.fillMaxSize()
    ) {
        // Channel Header
        item {
            ChannelHeader(
                channelInfo = channelInfo,
                isSubscribed = uiState.isSubscribed,
                onSubscribeClick = onSubscribeClick
            )
        }
        
        // Tabs
        item {
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
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
            
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        // Tab content
        when (uiState.selectedTab) {
            0 -> {
                // Videos tab - Coming soon placeholder
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Channel videos - Coming Soon",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            1 -> {
                // Playlists tab
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Playlists - Coming Soon",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            
            2 -> {
                // About tab
                item {
                    AboutSection(channelInfo = channelInfo)
                }
            }
        }
    }
}

@Composable
private fun ChannelHeader(
    channelInfo: org.schabi.newpipe.extractor.channel.ChannelInfo,
    isSubscribed: Boolean,
    onSubscribeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        // Banner
        val bannerUrl = try { channelInfo.banners?.firstOrNull()?.url } catch (e: Exception) { null }
        if (bannerUrl != null) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = "Channel Banner",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                contentScale = ContentScale.Crop
            )
        } else {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }
        
        // Channel info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar
            val avatarUrl = try { channelInfo.avatars?.firstOrNull()?.url } catch (e: Exception) { null }
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Channel Avatar",
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentScale = ContentScale.Crop
            )
            
            // Channel details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = channelInfo.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = formatSubscriberCount(channelInfo.subscriberCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textSecondary
                )
            }
            
            // Subscribe button
            Button(
                onClick = onSubscribeClick,
                colors = if (isSubscribed) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = if (isSubscribed) "Subscribed" else "Subscribe",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun VideoCard(
    videoItem: StreamInfoItem,
    onClick: () -> Unit
) {
    val thumbnailUrl = try { videoItem.thumbnails?.firstOrNull()?.url } catch (e: Exception) { null }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .width(150.dp)
                .height(85.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        
        // Video info
        Column(
            modifier = Modifier
                .weight(1f)
                .height(85.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = videoItem.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${formatViewCount(videoItem.viewCount)} views",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary
                )
                
                val uploadDate = videoItem.uploadDate
                if (uploadDate != null) {
                    Text(
                        text = uploadDate.date().toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textSecondary
                    )
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
