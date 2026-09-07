package io.github.aedev.flow.ui.screens.subscriptions

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Channel
import io.github.aedev.flow.data.model.SubscriptionGroup
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.ui.TabScrollEventBus
import io.github.aedev.flow.ui.components.layout.topbar.FlowSearchTopBar
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

private const val QUICK_ACCESS_CHANNEL_LIMIT = 15
private const val STALE_REFRESH_INTERVAL_MS = 3 * 60 * 1000L

@Composable
fun SubscriptionsScreen(
    onVideoClick: (Video) -> Unit,
    onShortClick: (ShortsQueueSource) -> Unit = {},
    onChannelClick: (Channel) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: SubscriptionsViewModel = sharedSubscriptionsViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val feedGridState = rememberLazyGridState()

    var isManagingSubs by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }
    var showGroupsDialog by remember { mutableStateOf(false) }
    var showCreateGroupDialog by remember { mutableStateOf(false) }
    var editingGroup by remember { mutableStateOf<SubscriptionGroup?>(null) }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                viewModel.importNewPipeBackup(it, context)
                scope.launch {
                    snackbarHostState.showSnackbar(context.getString(R.string.importing_from_backup))
                }
            }
        }

    LaunchedEffect(viewModel) { viewModel.ensureStarted() }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(viewModel, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshIfStaleOrMissedUploads()
            while (true) {
                delay(STALE_REFRESH_INTERVAL_MS)
                viewModel.refreshIfStaleOrMissedUploads()
            }
        }
    }

    LaunchedEffect(Unit) {
        TabScrollEventBus.scrollToTopEvents
            .filter { it == "subscriptions" }
            .collectLatest {
                feedGridState.animateScrollToItem(0)
                viewModel.refreshFeed()
            }
    }

    val sortedChannels =
        remember(uiState.subscribedChannels, uiState.sortMode, uiState.recentVideos) {
            sortSubscriptions(uiState.subscribedChannels, uiState.sortMode, uiState.recentVideos)
        }
    val topChannels =
        remember(sortedChannels) {
            quickAccessOrder(sortedChannels).take(QUICK_ACCESS_CHANNEL_LIMIT)
        }
    val openVideoChannel: (String) -> Unit =
        remember(uiState.subscribedChannels, onChannelClick) {
            { channelRef -> onChannelClick(resolveChannel(uiState.subscribedChannels, channelRef)) }
        }
    val videos = uiState.recentVideos

    LaunchedEffect(feedGridState, videos, isManagingSubs) {
        if (isManagingSubs) {
            viewModel.updateVisibleVideoIds(emptySet())
            return@LaunchedEffect
        }

        val feedVideoIds = videos.mapTo(HashSet(videos.size)) { it.id }
        snapshotFlow {
            feedGridState.layoutInfo.visibleItemsInfo
                .mapNotNull { item -> item.key as? String }
                .toSet()
        }.collectLatest { visibleKeys ->
            viewModel.updateVisibleVideoIds(visibleKeys.filterTo(HashSet()) { it in feedVideoIds })
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.updateVisibleVideoIds(emptySet()) }
    }

    Scaffold(
        topBar = {
            if (isManagingSubs) {
                FlowSearchTopBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onClose = {
                        isManagingSubs = false
                        searchQuery = ""
                    },
                    placeholder = stringResource(R.string.subscriptions_search_placeholder),
                    // Manage mode opens a browsable channel list; popping the keyboard would cover it.
                    autoFocus = false,
                    actions = {
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.subscriptions_sort_label),
                                )
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                Text(
                                    text = stringResource(R.string.subscriptions_sort_label),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
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
                                        },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { importLauncher.launch("application/json") }) {
                            Icon(
                                imageVector = Icons.Default.Upload,
                                contentDescription = stringResource(R.string.import_newpipe_backup),
                            )
                        }
                    },
                )
            } else {
                FlowTopBar(
                    title = stringResource(R.string.top_bar_subscriptions_title),
                    actions = {
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                imageVector =
                                    if (uiState.isFullWidthView) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = stringResource(R.string.toggle_view_mode),
                            )
                        }
                        IconButton(onClick = { isManagingSubs = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = stringResource(R.string.search_subscriptions),
                            )
                        }
                    },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            AnimatedContent(targetState = isManagingSubs, label = "subscriptionsMode") { manageMode ->
                if (manageMode) {
                    SubscriptionsManageContent(
                        channels = sortedChannels,
                        searchQuery = searchQuery,
                        notificationStates = uiState.notificationStates,
                        excludedShortsChannelIds = uiState.excludedShortsChannelIds,
                        onChannelClick = onChannelClick,
                        onNotificationChange = viewModel::updateNotificationState,
                        onShortsExcludeChange = viewModel::setShortsChannelExcluded,
                        onUnsubscribe = { channel ->
                            scope.launch {
                                unsubscribeWithUndo(
                                    viewModel = viewModel,
                                    channel = channel,
                                    snackbarHostState = snackbarHostState,
                                    unsubscribedMessage =
                                        context.getString(R.string.unsubscribed_from_template, channel.name),
                                    undoLabel = context.getString(R.string.undo),
                                )
                            }
                        },
                    )
                } else if (uiState.subscribedChannels.isEmpty()) {
                    SubscriptionsEmptyState(modifier = Modifier.fillMaxSize())
                } else {
                    SubscriptionsFeedContent(
                        state = uiState,
                        videos = videos,
                        topChannels = topChannels,
                        gridState = feedGridState,
                        onRefresh = viewModel::refreshFeed,
                        onVideoClick = onVideoClick,
                        onShortClick = onShortClick,
                        onChannelClick = onChannelClick,
                        onVideoChannelClick = openVideoChannel,
                        onViewAllClick = { isManagingSubs = true },
                        onGroupSelected = viewModel::selectGroup,
                        onManageGroups = { showGroupsDialog = true },
                        onRetryFailedChannels = viewModel::retryFailedChannels,
                        onDismissFailedChannels = viewModel::dismissFailedChannels,
                    )
                }
            }
        }
    }

    if (showGroupsDialog) {
        SubscriptionGroupsManagerDialog(
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
            onDelete = { group -> viewModel.deleteGroup(group.name) },
            onReorder = viewModel::reorderGroups,
        )
    }

    if (showCreateGroupDialog) {
        SubscriptionCreateEditGroupDialog(
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
            },
        )
    }
}

private suspend fun unsubscribeWithUndo(
    viewModel: SubscriptionsViewModel,
    channel: Channel,
    snackbarHostState: SnackbarHostState,
    unsubscribedMessage: String,
    undoLabel: String,
) {
    val subscription = viewModel.getSubscriptionOnce(channel.id)
    viewModel.unsubscribe(channel.id)
    val result =
        snackbarHostState.showSnackbar(
            message = unsubscribedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Short,
        )
    if (result == SnackbarResult.ActionPerformed) {
        subscription?.let { viewModel.subscribeChannel(it) }
    }
}
