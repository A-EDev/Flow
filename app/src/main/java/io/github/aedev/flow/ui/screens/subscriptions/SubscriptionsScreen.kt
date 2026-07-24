package io.github.aedev.flow.ui.screens.subscriptions


import androidx.compose.animation.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.*
import io.github.aedev.flow.ui.theme.extendedColors
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import io.github.aedev.flow.ui.TabScrollEventBus
import kotlinx.coroutines.delay

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onVideoClick: (Video) -> Unit,
    onShortClick: (String) -> Unit = {},
    onChannelClick: (Channel) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedGridState = rememberLazyGridState()
    
    // Import Launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            viewModel.importNewPipeBackup(it, context)
            scope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.importing_from_backup))
            }
        }
    }
    
    var isManagingSubs by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    var showGroupsDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<SubscriptionGroup?>(null) }
    
    // Initialize view model
    LaunchedEffect(Unit) {
        viewModel.initialize(context)
        viewModel.refreshIfStaleOrMissedUploads()
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3 * 60 * 1000L)
            viewModel.refreshIfStaleOrMissedUploads()
        }
    }

    // Scroll to top and refresh when tapping the subscriptions tab while already on this screen
    LaunchedEffect(Unit) {
        TabScrollEventBus.scrollToTopEvents
            .filter { it == "subscriptions" }
            .collectLatest {
                feedGridState.animateScrollToItem(0)
                viewModel.refreshFeed()
            }
    }
    
    val subscribedChannels = uiState.subscribedChannels
    val sortedChannels = remember(subscribedChannels, uiState.sortMode, uiState.recentVideos) {
        when (uiState.sortMode) {
            SubscriptionSortMode.DEFAULT -> subscribedChannels
            SubscriptionSortMode.NAME_ASC -> subscribedChannels.sortedBy { it.name.lowercase() }
            SubscriptionSortMode.RECENTLY_UPDATED -> {
                val latestUploadByChannel = uiState.recentVideos
                    .groupBy { it.channelId }
                    .mapValues { (_, videos) -> videos.maxOf { it.timestamp } }
                subscribedChannels.sortedByDescending { latestUploadByChannel[it.id] ?: 0L }
            }
        }
    }
    val videoSubscriptions = remember(sortedChannels) { sortedChannels.filterNot { it.isMusic } }
    val musicSubscriptions = remember(sortedChannels) { sortedChannels.filter { it.isMusic } }
    val topChannels = remember(videoSubscriptions, musicSubscriptions) {
        (videoSubscriptions + musicSubscriptions)
            .distinctBy(Channel::id)
            .take(15)
    }
    val openChannel: (Channel) -> Unit = remember(onChannelClick) { { channel -> onChannelClick(channel) } }
    val openVideoChannel: (String) -> Unit = remember(subscribedChannels, onChannelClick) {
        { channelRef ->
            val matchedChannel = subscribedChannels.firstOrNull { channel ->
                channel.id == channelRef || channel.url == channelRef || channelRef.endsWith(channel.id)
            } ?: Channel(
                id = channelRef.substringAfterLast('/'),
                name = "",
                thumbnailUrl = "",
                subscriberCount = 0L,
                url = channelRef
            )
            onChannelClick(matchedChannel)
        }
    }
    val videos = uiState.recentVideos

    Scaffold(
        topBar = {
            if (isManagingSubs) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text(androidx.compose.ui.res.stringResource(R.string.subscriptions_search_placeholder), style = MaterialTheme.typography.bodyLarge) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { isManagingSubs = false; searchQuery = "" }) {
                            Icon(Icons.Default.ArrowBack, stringResource(R.string.close))
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.subscriptions_sort_label))
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                Text(
                                    text = stringResource(R.string.subscriptions_sort_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                SubscriptionSortMode.entries.forEach { mode ->
                                    DropdownMenuItem(
                                        text = { Text(stringResource(mode.labelRes())) },
                                        onClick = {
                                            viewModel.setSortMode(mode)
                                            showSortMenu = false
                                        },
                                        trailingIcon = {
                                            if (uiState.sortMode == mode) {
                                                Icon(Icons.Default.Check, contentDescription = null)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { launcher.launch("application/json") }) {
                            Icon(Icons.Default.Upload, stringResource(R.string.import_newpipe_backup))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),

                    windowInsets = WindowInsets(0.dp)

                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = androidx.compose.ui.res.stringResource(R.string.top_bar_subscriptions_title),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Row {
                            IconButton(
                                onClick = { viewModel.toggleViewMode() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (uiState.isFullWidthView) Icons.Default.ViewList else Icons.Default.GridView,
                                    contentDescription = stringResource(R.string.toggle_view_mode),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            IconButton(
                                onClick = { isManagingSubs = true },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Search,
                                    stringResource(R.string.search_subscriptions),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            
            AnimatedContent(targetState = isManagingSubs) { manageMode ->
                if (manageMode) {
                    var selectedTabIndex by remember { mutableIntStateOf(0) }
                
                    val activeList = remember(sortedChannels, selectedTabIndex) {
                        if (selectedTabIndex == 0) {
                            sortedChannels.filterNot { it.isMusic }
                        } else {
                            sortedChannels.filter { it.isMusic }
                        }
                    }
                
                    val filteredChannels = remember(activeList, searchQuery) {
                        if (searchQuery.isBlank()) activeList
                        else activeList.filter { it.name.contains(searchQuery, ignoreCase = true) }
                    }
                
                    Column(modifier = Modifier.fillMaxSize()) {
                        SingleChoiceSegmentedButtonRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp) // Fixed empty space by reducing vertical padding
                        ) {
                            SegmentedButton(
                                selected = selectedTabIndex == 0,
                                onClick = { selectedTabIndex = 0 },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                                icon = { } 
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.OndemandVideo,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.subscriptions_video_section_title))
                                }
                            }
                            SegmentedButton(
                                selected = selectedTabIndex == 1,
                                onClick = { selectedTabIndex = 1 },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                                icon = { } 
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(stringResource(R.string.subscriptions_music_section_title))
                                }
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Text(
                                    text = pluralStringResource(
                                        id = R.plurals.channels_count,
                                        count = filteredChannels.size,
                                        filteredChannels.size
                                    ),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                
                            items(filteredChannels, key = { it.id }) { channel ->
                                SubscriptionManagerItem(
                                    channel = channel,
                                    onClick = { openChannel(channel) },
                                    isNotificationsEnabled = uiState.notificationStates[channel.id] ?: false,
                                    areShortsExcluded = channel.id in uiState.excludedShortsChannelIds,
                                    onNotificationChange = { enabled ->
                                        viewModel.updateNotificationState(channel.id, enabled)
                                    },
                                    onShortsExcludeChange = { excluded ->
                                        viewModel.setShortsChannelExcluded(channel.id, excluded)
                                    },
                                    onUnsubscribe = {
                                        scope.launch {
                                            val sub = viewModel.getSubscriptionOnce(channel.id)
                                            viewModel.unsubscribe(channel.id)
                                            val result = snackbarHostState.showSnackbar(
                                                context.getString(R.string.unsubscribed_from_template, channel.name),
                                                actionLabel = context.getString(R.string.undo),
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                sub?.let { viewModel.subscribeChannel(it) }
                                            }
                                        }
                                    }
                                )
                            }
                
                            if (filteredChannels.isEmpty()) {                
                                item {
                                    Text(
                                        text = stringResource(R.string.no_subscriptions_found),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 16.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {


                    // FEED MODE
                    val pullRefreshState = rememberPullToRefreshState()

                    LaunchedEffect(uiState.isLoading) {
                        if (!uiState.isLoading) {
                            pullRefreshState.animateToHidden()
                        }
                    }

                    PullToRefreshBox(
                        isRefreshing = uiState.isLoading,
                        onRefresh = { viewModel.refreshFeed() },
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (subscribedChannels.isEmpty()) {
                            EmptySubscriptionsState(modifier = Modifier.fillMaxSize())
                        } else {
                            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val feedLayout = rememberFeedGridLayout(maxWidth)
                            LazyVerticalGrid(
                                columns = if (uiState.isFullWidthView) GridCells.Fixed(feedLayout.columns) else GridCells.Fixed(1),
                                state = feedGridState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = if (uiState.isFullWidthView) feedLayout.contentPadding else 0.dp,
                                    end = if (uiState.isFullWidthView) feedLayout.contentPadding else 0.dp,
                                    top = 4.dp,
                                    bottom = 80.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(if (uiState.isFullWidthView) feedLayout.cardSpacing else 0.dp),
                                horizontalArrangement = Arrangement.spacedBy(if (uiState.isFullWidthView) feedLayout.cardSpacing else 0.dp)
                            ) {
                                // Channel Chips Row
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Column {
                                        CompactSubscriptionsHeader(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 8.dp, bottom = 12.dp),
                                            channels = topChannels,
                                            onChannelClick = openChannel,
                                            onViewAllClick = { isManagingSubs = true }
                                        )
                                        
                                        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

                                        if (uiState.groups.isNotEmpty() || true) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .horizontalScroll(rememberScrollState())
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                FilterChip(
                                                    selected = uiState.selectedGroupName == null,
                                                    onClick = { viewModel.selectGroup(null) },
                                                    label = { Text(stringResource(R.string.group_all)) }
                                                )
                                                uiState.groups.forEach { group ->
                                                    FilterChip(
                                                        selected = uiState.selectedGroupName == group.name,
                                                        onClick = { viewModel.selectGroup(group.name) },
                                                        label = { Text(group.name) }
                                                    )
                                                }
                                                IconButton(
                                                    onClick = { showGroupsDialog = true },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Default.Edit,
                                                        contentDescription = stringResource(R.string.manage_groups),
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        if (uiState.isLoading && uiState.refreshTotalChannels > 0) {
                                            val progress = uiState.refreshProcessedChannels.toFloat() /
                                                uiState.refreshTotalChannels.toFloat().coerceAtLeast(1f)
                                            LinearProgressIndicator(
                                                progress = { progress.coerceIn(0f, 1f) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                            )
                                            Text(
                                                text = stringResource(
                                                    R.string.subscriptions_refresh_progress_template,
                                                    uiState.refreshProcessedChannels,
                                                    uiState.refreshTotalChannels
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                            )
                                        } else if (uiState.lastRefreshText != null) {
                                            Text(
                                                text = if (uiState.showLastRefreshVideoCount) {
                                                    stringResource(
                                                        R.string.subscriptions_last_refreshed_template,
                                                        uiState.lastRefreshText!!,
                                                        uiState.lastRefreshVideoCount
                                                    )
                                                } else {
                                                    stringResource(
                                                        R.string.subscriptions_last_refreshed_time_only_template,
                                                        uiState.lastRefreshText!!
                                                    )
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
    
                                if (uiState.isShortsShelfEnabled && uiState.shorts.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        Column {
                                            
                                            ShortsShelf(
                                                shorts = uiState.shorts,
                                                onShortClick = { short -> onShortClick(short.id) }
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            HorizontalDivider(thickness = 4.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                        }
                                    }
                                }
    
                                items(videos, key = { it.id }) { video ->
                                    if (uiState.isFullWidthView) {
                                        VideoCardFullWidth(
                                            video = video,
                                            onClick = { onVideoClick(video) },
                                            onChannelClick = openVideoChannel,
                                            useInternalPadding = false
                                        )
                                    } else {
                                        VideoCardHorizontal(
                                            video = video,
                                            onClick = { onVideoClick(video) },
                                            onChannelClick = openVideoChannel
                                        )
                                    }
                                }
                                
                                item(span = { GridItemSpan(maxLineSpan) }) {
                                    Spacer(modifier = Modifier.height(80.dp))
                                }
                            }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showGroupsDialog) {
        GroupsManagerDialog(
            groups = uiState.groups,
            onDismiss = { showGroupsDialog = false },
            onCreateNew = {
                editingGroup = null
                showGroupsDialog = false
                showCreateGroupDialog = true
            },
            onEdit = { group ->
                editingGroup = group
                showGroupsDialog = false
                showCreateGroupDialog = true
            },
            onDelete = { group ->
                viewModel.deleteGroup(group.name)
            },
            onMoveUp = { group ->
                viewModel.moveGroup(group.name, -1)
            },
            onMoveDown = { group ->
                viewModel.moveGroup(group.name, 1)
            }
        )
    }

    if (showCreateGroupDialog) {
        CreateEditGroupDialog(
            existingGroup = editingGroup,
            allChannels = uiState.subscribedChannels,
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { name, channelIds ->
                val existing = editingGroup
                if (existing == null) {
                    viewModel.createGroup(name, channelIds)
                } else {
                    viewModel.updateGroup(existing.name, name, channelIds)
                }
                showCreateGroupDialog = false
            }
        )
    }
}

private fun SubscriptionSortMode.labelRes(): Int = when (this) {
    SubscriptionSortMode.DEFAULT -> R.string.subscriptions_sort_default
    SubscriptionSortMode.NAME_ASC -> R.string.subscriptions_sort_name
    SubscriptionSortMode.RECENTLY_UPDATED -> R.string.subscriptions_sort_recent
}

@Composable
private fun CompactSubscriptionsHeader(
    channels: List<Channel>,
    onChannelClick: (Channel) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.subscriptions_quick_access_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            if (channels.any { it.isMusic }) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.subscriptions_music_chip_label),
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(start = 12.dp, end = 8.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                ChannelAvatarItem(
                    channel = channel,
                    isSelected = false,
                    onClick = { onChannelClick(channel) }
                )
            }
            item(key = "view_all") {
                AllSubscriptionsAvatarItem(onClick = onViewAllClick)
            }
        }
    }
}

@Composable
private fun SubscriptionSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun GroupsManagerDialog(
    groups: List<SubscriptionGroup>,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onEdit: (SubscriptionGroup) -> Unit,
    onDelete: (SubscriptionGroup) -> Unit,
    onMoveUp: (SubscriptionGroup) -> Unit,
    onMoveDown: (SubscriptionGroup) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_groups)) },
        text = {
            Column {
                if (groups.isEmpty()) {
                    Text(
                        text = stringResource(R.string.no_groups_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                } else {
                    groups.forEachIndexed { index, group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = pluralStringResource(
                                    R.plurals.channels_count,
                                    group.channelIds.size,
                                    group.channelIds.size
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            IconButton(
                                onClick = { onMoveUp(group) },
                                enabled = index > 0,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onMoveDown(group) },
                                enabled = index < groups.lastIndex,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onEdit(group) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp))
                            }
                            IconButton(onClick = { onDelete(group) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    Icons.Default.Delete,
                                    null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCreateNew) {
                Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.new_group))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun CreateEditGroupDialog(
    existingGroup: SubscriptionGroup?,
    allChannels: List<Channel>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, channelIds: List<String>) -> Unit
) {
    var groupName by remember { mutableStateOf(existingGroup?.name ?: "") }
    val selectedChannelIds = remember {
        mutableStateOf(existingGroup?.channelIds?.toMutableSet() ?: mutableSetOf())
    }
    var searchQuery by remember { mutableStateOf("") }

    val filteredChannels = remember(allChannels, searchQuery) {
        if (searchQuery.isBlank()) allChannels
        else allChannels.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (existingGroup == null) stringResource(R.string.new_group)
                else stringResource(R.string.edit_group)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it },
                    label = { Text(stringResource(R.string.group_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(stringResource(R.string.search_channels_hint)) },
                    leadingIcon = { Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredChannels, key = { it.id }) { channel ->
                        val isChecked = channel.id in selectedChannelIds.value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val updated = selectedChannelIds.value.toMutableSet()
                                    if (isChecked) updated.remove(channel.id) else updated.add(channel.id)
                                    selectedChannelIds.value = updated
                                }
                                .padding(vertical = 4.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val updated = selectedChannelIds.value.toMutableSet()
                                    if (checked) updated.add(channel.id) else updated.remove(channel.id)
                                    selectedChannelIds.value = updated
                                }
                            )
                            AsyncImage(
                                model = channel.thumbnailUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = channel.name,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(groupName.trim(), selectedChannelIds.value.toList()) },
                enabled = groupName.isNotBlank() && selectedChannelIds.value.isNotEmpty()
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun ChannelAvatarItem(
    channel: Channel,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .then(if (isSelected) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(if (isSelected) 48.dp else 56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            if (channel.isMusic) {
                ChannelTypeBadge(
                    icon = Icons.Default.MusicNote,
                    contentDescription = stringResource(R.string.subscriptions_music_badge_cd),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier.matchParentSize().clip(CircleShape).background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White)
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AllSubscriptionsAvatarItem(onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = stringResource(R.string.view_all_button_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.view_all_button_label),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ChannelTypeBadge(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 2.dp,
        tonalElevation = 2.dp
    ) {
        Box(
            modifier = Modifier.padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

@Composable
fun SubscriptionManagerItem(
    channel: Channel,
    onClick: () -> Unit,
    onUnsubscribe: () -> Unit,
    isNotificationsEnabled: Boolean = false,
    areShortsExcluded: Boolean = false,
    onNotificationChange: (Boolean) -> Unit = {},
    onShortsExcludeChange: (Boolean) -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = channel.thumbnailUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        
        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (channel.isMusic) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = stringResource(R.string.subscriptions_music_chip_label),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
        
        Box {
            var expanded by remember { mutableStateOf(false) }
            FilledTonalButton(
                onClick = { expanded = true },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (isNotificationsEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.subscribed))
                Spacer(modifier = Modifier.width(2.dp))
                Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(R.string.notifications),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                DropdownMenuItem(
                    text = { Text(androidx.compose.ui.res.stringResource(R.string.on)) },
                    onClick = {
                        onNotificationChange(true)
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Rounded.NotificationsActive, null) }
                )
                DropdownMenuItem(
                    text = { Text(androidx.compose.ui.res.stringResource(R.string.off)) },
                    onClick = {
                        onNotificationChange(false)
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Rounded.NotificationsOff, null) }
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                DropdownMenuItem(
                    text = {
                        Text(
                            androidx.compose.ui.res.stringResource(
                                if (areShortsExcluded) R.string.show_channel_shorts
                                else R.string.hide_channel_shorts
                            )
                        )
                    },
                    onClick = {
                        onShortsExcludeChange(!areShortsExcluded)
                        expanded = false
                    },
                    leadingIcon = {
                        Icon(
                            if (areShortsExcluded) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            null
                        )
                    }
                )
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.surfaceVariant)
                DropdownMenuItem(
                    text = { Text(androidx.compose.ui.res.stringResource(R.string.unsubscribe)) },
                    onClick = {
                        onUnsubscribe()
                        expanded = false
                    },
                    leadingIcon = { Icon(Icons.Rounded.PersonRemove, null) }
                )
            }
        }
    }
}

@Composable
private fun EmptySubscriptionsState(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Subscriptions,
            contentDescription = null,
            modifier = Modifier.size(80.dp).padding(bottom = 16.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Text(
            text = context.getString(R.string.no_subscriptions_yet),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = context.getString(R.string.empty_subscriptions_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.extendedColors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

