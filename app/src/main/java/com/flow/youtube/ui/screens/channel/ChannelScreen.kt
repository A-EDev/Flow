package com.flow.youtube.ui.screens.channel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material.icons.rounded.Check
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.AnimatedVisibility
import com.flow.youtube.R
import androidx.compose.ui.res.stringResource


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelUrl: String,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val videosPagingFlow by viewModel.videosPagingFlow.collectAsState()
    val shortsPagingFlow by viewModel.shortsPagingFlow.collectAsState()
    val playlistsPagingFlow by viewModel.playlistsPagingFlow.collectAsState()
    
    // Collect paging items when flow is available
    val videosLazyPagingItems = videosPagingFlow?.collectAsLazyPagingItems()
    val shortsLazyPagingItems = shortsPagingFlow?.collectAsLazyPagingItems()
    val playlistsLazyPagingItems = playlistsPagingFlow?.collectAsLazyPagingItems()
    
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
                        text = uiState.channelInfo?.name ?: stringResource(R.string.channel_default_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
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
                            text = uiState.error ?: context.getString(R.string.failed_to_load_channel),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        
                        Button(onClick = { viewModel.loadChannel(channelUrl) }) {
                            Text(androidx.compose.ui.res.stringResource(R.string.retry))
                        }
                    }
                }
                
                uiState.channelInfo != null -> {
                    ChannelContent(
                        uiState = uiState,
                        videosLazyPagingItems = videosLazyPagingItems,
                        shortsLazyPagingItems = shortsLazyPagingItems,
                        playlistsLazyPagingItems = playlistsLazyPagingItems,
                        onVideoClick = onVideoClick,
                        onShortClick = onShortClick,
                        onPlaylistClick = onPlaylistClick,
                        onSubscribeClick = { viewModel.toggleSubscription() },
                        onTabSelected = { viewModel.selectTab(it) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelContent(
    uiState: ChannelUiState,
    videosLazyPagingItems: LazyPagingItems<Video>?,
    shortsLazyPagingItems: LazyPagingItems<Video>?,
    playlistsLazyPagingItems: LazyPagingItems<com.flow.youtube.data.model.Playlist>?,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSubscribeClick: () -> Unit,
    onTabSelected: (Int) -> Unit
) {
    val channelInfo = uiState.channelInfo ?: return
    
    LazyVerticalGrid(
        columns = if (uiState.selectedTab == 1) GridCells.Adaptive(150.dp) else GridCells.Adaptive(320.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Banner & Header Combined
        item(span = { GridItemSpan(maxLineSpan) }) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Banner
                val bannerUrl = try { channelInfo.banners.firstOrNull()?.url } catch (e: Exception) { null }
                if (!bannerUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = bannerUrl,
                        contentDescription = stringResource(R.string.channel_banner),
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(3f)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }
                
                // Gradient Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(3f)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY
                            )
                        )
                )
            }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Bottom
                ) {
                    val avatarUrl = try { channelInfo.avatars.firstOrNull()?.url } catch (e: Exception) { null }
                    
                    // Avatar with border
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .padding(4.dp) // Border width
                    ) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = stringResource(R.string.channel_avatar),
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                
                Text(
                    text = channelInfo.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formatSubscriberCount(channelInfo.subscriberCount, LocalContext.current),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.textSecondary
                    )
                    
                    // Dot separator
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.extendedColors.textSecondary)
                    )
                    
                    Text(
                        text = stringResource(R.string.videos_count_template, videosLazyPagingItems?.itemCount ?: 0),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.extendedColors.textSecondary
                    )
                }
                
                Spacer(Modifier.height(16.dp))
                
                AnimatedSubscribeButton(
                    isSubscribed = uiState.isSubscribed,
                    onClick = onSubscribeClick,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        
        // Tabs
        item(span = { GridItemSpan(maxLineSpan) }) {
            ScrollableTabRow(
                selectedTabIndex = uiState.selectedTab,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.Indicator(
                        Modifier.tabIndicatorOffset(tabPositions[uiState.selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                },
                divider = {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                }
            ) {
                val tabTitles = listOf(
                    stringResource(R.string.tab_videos),
                    stringResource(R.string.tab_shorts),
                    stringResource(R.string.tab_playlists),
                    stringResource(R.string.tab_about)
                )
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTab == index,
                        onClick = { onTabSelected(index) },
                        text = { 
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (uiState.selectedTab == index) FontWeight.Bold else FontWeight.Normal
                            ) 
                        },
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Content
        when (uiState.selectedTab) {
            0 -> { // Videos
                if (videosLazyPagingItems != null) {
                    if (videosLazyPagingItems.loadState.refresh is LoadState.NotLoading && videosLazyPagingItems.itemCount == 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyStateMessage(stringResource(R.string.no_videos_found))
                        }
                    } else {
                        items(
                            count = videosLazyPagingItems.itemCount,
                            key = videosLazyPagingItems.itemKey { it.id }
                        ) { index ->
                            val video = videosLazyPagingItems[index]
                            if (video != null) {
                                VideoCardFullWidth(
                                    video = video,
                                    onClick = { onVideoClick(video) }
                                )
                            }
                        }
                    }
                }
            }
            1 -> { // Shorts
                if (shortsLazyPagingItems != null) {
                    if (shortsLazyPagingItems.loadState.refresh is LoadState.NotLoading && shortsLazyPagingItems.itemCount == 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyStateMessage(stringResource(R.string.no_shorts_found))
                        }
                    } else {
                        items(
                            count = shortsLazyPagingItems.itemCount,
                            key = shortsLazyPagingItems.itemKey { it.id }
                        ) { index ->
                            val video = shortsLazyPagingItems[index]
                            if (video != null) {
                                ShortsCard(
                                    video = video,
                                    onClick = { onShortClick(video.id) }
                                )
                            }
                        }
                    }
                }
            }
            2 -> { // Playlists
                if (playlistsLazyPagingItems != null) {
                    if (playlistsLazyPagingItems.loadState.refresh is LoadState.NotLoading && playlistsLazyPagingItems.itemCount == 0) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            EmptyStateMessage(stringResource(R.string.no_playlists_found))
                        }
                    } else {
                        items(
                            count = playlistsLazyPagingItems.itemCount,
                            key = playlistsLazyPagingItems.itemKey { it.id }
                        ) { index ->
                            val playlist = playlistsLazyPagingItems[index]
                            if (playlist != null) {
                                PlaylistCard(
                                    playlist = playlist,
                                    onClick = { onPlaylistClick(playlist.id) }
                                )
                            }
                        }
                    }
                }
            }
            3 -> { // About
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AboutSection(channelInfo = channelInfo)
                }
            }
        }
    }
}

@Composable
fun AnimatedSubscribeButton(
    isSubscribed: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
        label = "bgColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary,
        label = "contentColor"
    )
    
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = backgroundColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.animateContentSize()
        ) {
            AnimatedVisibility(visible = isSubscribed) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp).size(18.dp)
                )
            }
            Text(
                text = if (isSubscribed) stringResource(R.string.subscribed) else stringResource(R.string.subscribe),
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShortsCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(9f / 16f)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = video.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // View count overlay at bottom
            Text(
                text = stringResource(R.string.views_count_short_template, formatViewCount(video.viewCount)),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
        
        Text(
            text = video.title,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PlaylistCard(
    playlist: com.flow.youtube.data.model.Playlist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .width(160.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Playlist overlay
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(40.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = playlist.videoCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.videos_count_template, playlist.videoCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textSecondary
            )
        }
    }
}

@Composable
private fun AboutSection(
    channelInfo: org.schabi.newpipe.extractor.channel.ChannelInfo
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = context.getString(R.string.about),
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
                text = context.getString(R.string.empty_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        HorizontalDivider()
        
        // Stats
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = context.getString(R.string.stats),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = context.getString(R.string.subscribers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textSecondary
                )
                Text(
                    text = formatSubscriberCount(channelInfo.subscriberCount, context),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private fun formatSubscriberCount(count: Long, context: android.content.Context): String {
    return when {
        count >= 1_000_000 -> context.getString(R.string.subscribers_count_template, String.format("%.1fM", count / 1_000_000.0))
        count >= 1_000 -> context.getString(R.string.subscribers_count_template, String.format("%.1fK", count / 1_000.0))
        else -> context.getString(R.string.subscribers_count_template, count.toString())
    }
}

private fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
