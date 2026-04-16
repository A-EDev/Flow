package io.github.aedev.flow.ui.screens.channel

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.CompactVideoCard
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.theme.extendedColors
import kotlinx.coroutines.launch

private typealias SortedVideos = List<Video>?

enum class VideoFilter { Latest, Popular, Oldest }

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
    val shortsPagingFlow by viewModel.shortsPagingFlow.collectAsState()
    val playlistsPagingFlow by viewModel.playlistsPagingFlow.collectAsState()
    val allVideos by viewModel.videosAll.collectAsState()
    val allLiveVideos by viewModel.liveAll.collectAsState()
    val isLoadingAllVideos by viewModel.isLoadingAllVideos.collectAsState()

    val shortsLazyPagingItems = shortsPagingFlow?.collectAsLazyPagingItems()
    val playlistsLazyPagingItems = playlistsPagingFlow?.collectAsLazyPagingItems()

    LaunchedEffect(Unit) { viewModel.initialize(context) }
    LaunchedEffect(channelUrl) { viewModel.loadChannel(channelUrl) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.close)
                )
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                uiState.error != null -> {
                    ErrorState(
                        message = uiState.error ?: stringResource(R.string.failed_to_load_channel),
                        onRetry = { viewModel.loadChannel(channelUrl) },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                uiState.channelInfo != null -> {
                    ChannelContent(
                        uiState = uiState,
                        allVideos = allVideos,
                        isLoadingAllVideos = isLoadingAllVideos,
                        shortsLazyPagingItems = shortsLazyPagingItems,
                        allLiveVideos = allLiveVideos,
                        playlistsLazyPagingItems = playlistsLazyPagingItems,
                        onVideoClick = onVideoClick,
                        onShortClick = onShortClick,
                        onPlaylistClick = onPlaylistClick,
                        onSubscribeClick = { viewModel.toggleSubscription() },
                        onUnsubscribeClick = { viewModel.unsubscribe() },
                        onNotificationChange = { viewModel.setNotificationState(it) },
                        onTabSelected = { viewModel.selectTab(it) },
                        initialScrollIndex = viewModel.listScrollIndex,
                        initialScrollOffset = viewModel.listScrollOffset,
                        onScrollChanged = { idx, off -> viewModel.saveScrollPosition(idx, off) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChannelContent(
    uiState: ChannelUiState,
    allVideos: List<Video>,
    isLoadingAllVideos: Boolean,
    shortsLazyPagingItems: LazyPagingItems<Video>?,
    allLiveVideos: List<Video>,
    playlistsLazyPagingItems: LazyPagingItems<io.github.aedev.flow.data.model.Playlist>?,
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSubscribeClick: () -> Unit,
    onUnsubscribeClick: () -> Unit,
    onNotificationChange: (Boolean) -> Unit,
    onTabSelected: (Int) -> Unit,
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (index: Int, offset: Int) -> Unit = { _, _ -> }
) {
    val channelInfo = uiState.channelInfo ?: return

    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember { io.github.aedev.flow.data.local.PlayerPreferences(context) }
    val isGridView by preferences.channelIsGridView.collectAsState(initial = false)
    var selectedFilter by rememberSaveable { mutableStateOf(VideoFilter.Latest) }
    val coroutineScope = rememberCoroutineScope()

    val sortedVideos: List<Video> = when (selectedFilter) {
        VideoFilter.Latest -> allVideos
        VideoFilter.Popular -> allVideos.sortedByDescending { it.viewCount }
        VideoFilter.Oldest -> allVideos.reversed()
    }
    val sortedLive: List<Video> = when (selectedFilter) {
        VideoFilter.Latest -> allLiveVideos
        VideoFilter.Popular -> allLiveVideos.sortedByDescending { it.viewCount }
        VideoFilter.Oldest -> allLiveVideos.reversed()
    }

    val tabTitles = listOf(
        stringResource(R.string.tab_videos),
        stringResource(R.string.tab_shorts),
        stringResource(R.string.tab_live),
        stringResource(R.string.tab_playlists),
        stringResource(R.string.tab_about)
    )

    val pagerState = rememberPagerState(
        initialPage = uiState.selectedTab.coerceIn(0, tabTitles.lastIndex),
        pageCount = { tabTitles.size }
    )

    // Sync pager swipe → ViewModel tab selection
    LaunchedEffect(pagerState.currentPage) {
        onTabSelected(pagerState.currentPage)
    }

    // Sync ViewModel tab selection (e.g. deep-link) → pager
    LaunchedEffect(uiState.selectedTab) {
        if (pagerState.currentPage != uiState.selectedTab) {
            pagerState.animateScrollToPage(uiState.selectedTab)
        }
    }

    val showFilterBar = pagerState.currentPage == 0 || pagerState.currentPage == 2

    // Persist Videos-tab scroll position across navigation
    val videosListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset
    )
    LaunchedEffect(videosListState) {
        snapshotFlow { videosListState.firstVisibleItemIndex to videosListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onScrollChanged(index, offset) }
    }

    LazyColumn(
        state = videosListState,
        modifier = Modifier.fillMaxSize()
    ) {
        // ── Channel header — scrolls away naturally ───────────────────────────
        item(key = "channel_header") {
            ChannelHeader(
                channelInfo = channelInfo,
                isSubscribed = uiState.isSubscribed,
                isNotificationsEnabled = uiState.isNotificationsEnabled,
                onSubscribeClick = onSubscribeClick,
                onUnsubscribeClick = onUnsubscribeClick,
                onNotificationChange = onNotificationChange
            )
        }

        // ── Sticky tab row + filter bar ───────────────────────────────────────
        stickyHeader(key = "tab_row") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                ChannelTabRow(
                    selectedIndex = pagerState.currentPage,
                    tabs = tabTitles,
                    onTabSelected = { idx ->
                        coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                    }
                )
                if (showFilterBar) {
                    FilterAndToggleBar(
                        selectedFilter = selectedFilter,
                        isGridView = isGridView,
                        onFilterSelected = { selectedFilter = it },
                        onToggleGridView = { coroutineScope.launch { preferences.setChannelIsGridView(!isGridView) } }
                    )
                }
            }
        }

        // ── HorizontalPager fills the rest of the screen ──────────────────────
        item(key = "pager_content") {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillParentMaxSize(),
                verticalAlignment = Alignment.Top,
                userScrollEnabled = true
            ) { page ->
                when (page) {
                    0 -> {
                        if (isLoadingAllVideos && sortedVideos.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(top = 64.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                videosContent(null, sortedVideos, isGridView, onVideoClick)
                                item { Spacer(Modifier.height(16.dp)) }
                            }
                        }
                    }
                    1 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        shortsContent(shortsLazyPagingItems, onShortClick)
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                    2 -> {
                        if (isLoadingAllVideos && sortedLive.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(top = 64.dp),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                liveContent(null, sortedLive, isGridView, onVideoClick)
                                item { Spacer(Modifier.height(16.dp)) }
                            }
                        }
                    }
                    3 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        playlistsContent(playlistsLazyPagingItems, onPlaylistClick)
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                    4 -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item { AboutSection(channelInfo = channelInfo) }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

// Filter + grid toggle bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterAndToggleBar(
    selectedFilter: VideoFilter,
    isGridView: Boolean,
    onFilterSelected: (VideoFilter) -> Unit,
    onToggleGridView: () -> Unit
) {
    val filters = listOf(
        VideoFilter.Latest to "Latest",
        VideoFilter.Popular to "Popular",
        VideoFilter.Oldest to "Oldest"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filters.size) { idx ->
                val (filter, label) = filters[idx]
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { onFilterSelected(filter) },
                    label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                    shape = RoundedCornerShape(20.dp),
                    leadingIcon = if (selectedFilter == filter) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null
                )
            }
        }

        IconButton(onClick = onToggleGridView) {
            Icon(
                imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                contentDescription = if (isGridView) "List view" else "Grid view",
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// Channel header — banner + avatar + info + subscribe
@Composable
private fun ChannelHeader(
    channelInfo: org.schabi.newpipe.extractor.channel.ChannelInfo,
    isSubscribed: Boolean,
    isNotificationsEnabled: Boolean,
    onSubscribeClick: () -> Unit,
    onUnsubscribeClick: () -> Unit,
    onNotificationChange: (Boolean) -> Unit
) {
    val bannerUrl = try { channelInfo.banners.firstOrNull()?.url } catch (e: Exception) { null }
    // Use highest-res avatar available
    val avatarUrl = try {
        channelInfo.avatars.maxByOrNull { it.height }?.url
            ?: channelInfo.avatars.firstOrNull()?.url
    } catch (e: Exception) { null }

    Log.d("ChannelHeader", "channel=${channelInfo.name} avatarUrl=$avatarUrl bannerUrl=$bannerUrl")
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Banner ───────────────────────────────────────────────────────────
        if (!bannerUrl.isNullOrEmpty()) {
            AsyncImage(
                model = bannerUrl,
                contentDescription = stringResource(R.string.channel_banner),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(320f / 100f),
                contentScale = ContentScale.Crop,
                onError = {
                    Log.e("ChannelHeader", "Banner load failed for $bannerUrl: ${it.result.throwable?.message}")
                }
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(320f / 100f)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }

        // ── Avatar row + subscribe button ────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar: shows image with icon fallback on error or null URL
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!avatarUrl.isNullOrEmpty()) {
                    var avatarFailed by remember(avatarUrl) { mutableStateOf(false) }
                    var retryUrl by remember(avatarUrl) { mutableStateOf(avatarUrl) }
                    var didRetry by remember(avatarUrl) { mutableStateOf(false) }
                    if (avatarFailed) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(4.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        AsyncImage(
                            model = retryUrl,
                            contentDescription = stringResource(R.string.channel_avatar),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onError = { err ->
                                val msg = err.result.throwable?.message ?: "unknown"
                                if (!didRetry) {
                                    didRetry = true
                                    val lowRes = retryUrl.replace(Regex("=s\\d+"), "=s88")
                                    if (lowRes != retryUrl) {
                                        Log.w("ChannelHeader", "Avatar failed '$retryUrl' ($msg) → retrying '$lowRes'")
                                        retryUrl = lowRes
                                    } else {
                                        Log.e("ChannelHeader", "Avatar failed '$retryUrl' ($msg), no size param → icon")
                                        avatarFailed = true
                                    }
                                } else {
                                    Log.e("ChannelHeader", "Avatar retry failed '$retryUrl' ($msg) → icon")
                                    avatarFailed = true
                                }
                            }
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().padding(4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            SubscribeButton(
                isSubscribed = isSubscribed,
                isNotificationsEnabled = isNotificationsEnabled,
                onSubscribeClick = onSubscribeClick,
                onUnsubscribeClick = onUnsubscribeClick,
                onNotificationChange = onNotificationChange
            )
        }

        // ── Channel name + stats ─────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = channelInfo.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val subText = formatSubscriberCount(channelInfo.subscriberCount, context)
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textSecondary
            )
        }
    }
}

// Subscribe button
@Composable
fun SubscribeButton(
    isSubscribed: Boolean,
    isNotificationsEnabled: Boolean,
    onSubscribeClick: () -> Unit,
    onUnsubscribeClick: () -> Unit,
    onNotificationChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val containerColor by animateColorAsState(
        targetValue = if (isSubscribed) MaterialTheme.colorScheme.surfaceVariant
                      else MaterialTheme.colorScheme.onSurface,
        label = "subscribeBg"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSubscribed) MaterialTheme.colorScheme.onSurfaceVariant
                      else MaterialTheme.colorScheme.surface,
        label = "subscribeFg"
    )

    Box(modifier = modifier) {
        Button(
            onClick = {
                if (isSubscribed) {
                    expanded = true
                } else {
                    onSubscribeClick()
                }
            },
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 9.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.animateContentSize()
            ) {
                AnimatedVisibility(visible = isSubscribed) {
                    Icon(
                        imageVector = if (isNotificationsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = if (isSubscribed) stringResource(R.string.subscribed)
                           else stringResource(R.string.subscribe),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                AnimatedVisibility(visible = isSubscribed) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(200.dp)
        ) {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.on)) },
                leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) },
                onClick = {
                    onNotificationChange(true)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.off)) },
                leadingIcon = { Icon(Icons.Rounded.NotificationsOff, null) },
                onClick = {
                    onNotificationChange(false)
                    expanded = false
                }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
            DropdownMenuItem(
                text = { Text(stringResource(R.string.unsubscribe)) },
                leadingIcon = { Icon(Icons.Rounded.PersonRemove, null) },
                onClick = {
                    onUnsubscribeClick()
                    expanded = false
                }
            )
        }
    }
}

// Tab row
@Composable
private fun ChannelTabRow(
    selectedIndex: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = selectedIndex,
        edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
        indicator = { tabPositions ->
            TabRowDefaults.Indicator(
                modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                height = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        },
        divider = {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 0.5.dp
            )
        }
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                modifier = Modifier.height(44.dp),
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.extendedColors.textSecondary
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedIndex == index) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// Tab content helpers (LazyListScope)
private fun LazyListScope.videosContent(
    pagingItems: LazyPagingItems<Video>?,
    sortedItems: SortedVideos,
    isGridView: Boolean,
    onVideoClick: (Video) -> Unit
) {
    if (sortedItems != null) {
        if (sortedItems.isEmpty()) {
            item { EmptyState(message = "No videos found") }
            return
        }
        items(count = sortedItems.size, key = { sortedItems[it].id }) { idx ->
            val video = sortedItems[idx]
            if (isGridView) {
                VideoCardFullWidth(video = video, onClick = { onVideoClick(video) })
            } else {
                CompactVideoCard(video = video, onClick = { onVideoClick(video) })
            }
        }
        return
    }

    if (pagingItems == null ||
        (pagingItems.loadState.refresh is LoadState.NotLoading && pagingItems.itemCount == 0)) {
        item { EmptyState(message = "No videos found") }
        return
    }
    items(count = pagingItems.itemCount, key = pagingItems.itemKey { it.id }) { index ->
        pagingItems[index]?.let { video ->
            if (isGridView) {
                VideoCardFullWidth(video = video, onClick = { onVideoClick(video) })
            } else {
                CompactVideoCard(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}

private fun LazyListScope.shortsContent(
    pagingItems: LazyPagingItems<Video>?,
    onShortClick: (String) -> Unit
) {
    if (pagingItems == null ||
        (pagingItems.loadState.refresh is LoadState.NotLoading && pagingItems.itemCount == 0)) {
        item { EmptyState(message = "No Shorts found") }
        return
    }
    val count = pagingItems.itemCount
    val rowCount = (count + 1) / 2
    items(count = rowCount, key = { rowIdx -> "shorts_row_$rowIdx" }) { rowIdx ->
        val firstIdx = rowIdx * 2
        val secondIdx = rowIdx * 2 + 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            Box(modifier = Modifier.weight(1f)) {
                pagingItems[firstIdx]?.let { video ->
                    ShortsGridCard(video = video, onClick = { onShortClick(video.id) })
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                if (secondIdx < count) {
                    pagingItems[secondIdx]?.let { video ->
                        ShortsGridCard(video = video, onClick = { onShortClick(video.id) })
                    }
                }
            }
        }
    }
}

private fun LazyListScope.liveContent(
    pagingItems: LazyPagingItems<Video>?,
    sortedItems: SortedVideos,
    isGridView: Boolean,
    onVideoClick: (Video) -> Unit
) {
    if (sortedItems != null) {
        if (sortedItems.isEmpty()) {
            item { EmptyState(message = "No live videos found") }
            return
        }
        items(count = sortedItems.size, key = { sortedItems[it].id }) { idx ->
            val video = sortedItems[idx]
            if (isGridView) {
                VideoCardFullWidth(video = video, onClick = { onVideoClick(video) })
            } else {
                CompactVideoCard(video = video, onClick = { onVideoClick(video) })
            }
        }
        return
    }

    if (pagingItems == null ||
        (pagingItems.loadState.refresh is LoadState.NotLoading && pagingItems.itemCount == 0)) {
        item { EmptyState(message = "No live videos found") }
        return
    }
    items(count = pagingItems.itemCount, key = pagingItems.itemKey { it.id }) { index ->
        pagingItems[index]?.let { video ->
            if (isGridView) {
                VideoCardFullWidth(video = video, onClick = { onVideoClick(video) })
            } else {
                CompactVideoCard(video = video, onClick = { onVideoClick(video) })
            }
        }
    }
}

private fun LazyListScope.playlistsContent(
    pagingItems: LazyPagingItems<io.github.aedev.flow.data.model.Playlist>?,
    onPlaylistClick: (String) -> Unit
) {
    if (pagingItems == null ||
        (pagingItems.loadState.refresh is LoadState.NotLoading && pagingItems.itemCount == 0)) {
        item { EmptyState(message = "No playlists found") }
        return
    }
    items(count = pagingItems.itemCount, key = pagingItems.itemKey { it.id }) { index ->
        pagingItems[index]?.let { playlist ->
            PlaylistCard(playlist = playlist, onClick = { onPlaylistClick(playlist.id) })
        }
    }
}

// Shorts grid card (2-column)
@Composable
private fun ShortsGridCard(
    video: Video,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
            Text(
                text = formatViewCount(video.viewCount),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = video.title,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// Playlist card
@Composable
private fun PlaylistCard(
    playlist: io.github.aedev.flow.data.model.Playlist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = playlist.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(44.dp)
                    .align(Alignment.CenterEnd)
                    .background(Color.Black.copy(alpha = 0.65f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = playlist.videoCount.toString(),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyMedium,
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

// About section
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
        if (!channelInfo.description.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.about),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.extendedColors.textSecondary
                )
                Text(
                    text = channelInfo.description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.surfaceVariant,
            thickness = 0.5.dp
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = stringResource(R.string.stats),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.extendedColors.textSecondary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.subscribers),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.extendedColors.textSecondary
                )
                Text(
                    text = formatSubscriberCount(channelInfo.subscriberCount, context),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

// Error state
@Composable
private fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error
        )
        Button(onClick = onRetry) {
            Text(stringResource(R.string.retry))
        }
    }
}

// Empty state
@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.extendedColors.textSecondary
        )
    }
}

// Formatters
private fun formatSubscriberCount(count: Long, context: android.content.Context): String {
    val formatted = when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000     -> String.format("%.1fK", count / 1_000.0)
        else               -> count.toString()
    }
    return context.getString(R.string.subscribers_count_template, formatted)
}

private fun formatViewCount(count: Long): String = when {
    count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
    count >= 1_000     -> String.format("%.1fK", count / 1_000.0)
    else               -> count.toString()
}
