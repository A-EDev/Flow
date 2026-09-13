package io.github.aedev.flow.ui.screens.channel

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.SubscriptionGroup
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.channel.ChannelHeader
import io.github.aedev.flow.innertube.pages.channel.ChannelTabKind
import io.github.aedev.flow.innertube.pages.channel.CommunityPost
import io.github.aedev.flow.ui.components.ChannelAvatarImage
import io.github.aedev.flow.ui.components.ChannelBanner
import io.github.aedev.flow.ui.components.SortChipRow
import io.github.aedev.flow.ui.components.channel.ChannelTabItems
import io.github.aedev.flow.ui.components.shared.CollectionEditDialog
import io.github.aedev.flow.ui.components.shared.CollectionSheetEntry
import io.github.aedev.flow.ui.components.shared.CommentSortFilter
import io.github.aedev.flow.ui.components.shared.FlowCommentsBottomSheet
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.components.shared.FlowSubscribeButton
import io.github.aedev.flow.ui.components.shared.FullSizeImageDialog
import io.github.aedev.flow.ui.components.shared.SaveToCollectionSheet
import io.github.aedev.flow.ui.components.shared.sortCommentsByFilter
import io.github.aedev.flow.ui.theme.extendedColors
import io.github.aedev.flow.ui.youtubeChannelUrl
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelUrl: String,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    onShortClick: (videoId: String, sortIndex: Int) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val communityUiState by viewModel.communityUiState.collectAsState()
    val tabStates by viewModel.tabStates.collectAsStateWithLifecycle()
    val subscriptionGroups by viewModel.subscriptionGroups.collectAsStateWithLifecycle()
    var showGroupSheet by rememberSaveable { mutableStateOf(false) }
    var showCreateGroupDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(channelUrl) { viewModel.loadChannel(channelUrl) }

    var showCollapsedChannelTitle by remember(channelUrl) { mutableStateOf(false) }
    val collapsedChannelTitle = uiState.header?.title.orEmpty()
    var communityCommentSort by rememberSaveable { mutableStateOf(CommentSortFilter.TOP) }
    val sortedCommunityComments =
        remember(communityUiState.comments, communityCommentSort) {
            sortCommentsByFilter(communityUiState.comments, communityCommentSort)
        }

    LaunchedEffect(communityUiState.activePost?.id) {
        communityCommentSort = CommentSortFilter.TOP
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.close),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = showCollapsedChannelTitle && collapsedChannelTitle.isNotBlank(),
                        modifier = Modifier.align(Alignment.CenterStart),
                    ) {
                        Text(
                            text = collapsedChannelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = {
                    // channelUrl may already be a full URL, so it must be normalized rather than
                    // pasted behind /channel/ — that produced a nested, unopenable share link.
                    val shareUrl = youtubeChannelUrl(uiState.header?.id ?: channelUrl) ?: channelUrl
                    val shareIntent =
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                }) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = stringResource(R.string.share),
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    uiState.error != null -> {
                        FlowErrorState(
                            error = uiState.error ?: stringResource(R.string.failed_to_load_channel),
                            onRetry = { viewModel.loadChannel(channelUrl) },
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }

                    uiState.header != null -> {
                        ChannelContent(
                            uiState = uiState,
                            communityUiState = communityUiState,
                            tabStates = tabStates,
                            onFilterSelected = viewModel::selectTabFilter,
                            onVideoClick = onVideoClick,
                            onChannelClick = onChannelClick,
                            onShortClick = { videoId ->
                                onShortClick(videoId, tabStates[ChannelTabKind.Shorts]?.selectedFilter ?: 0)
                            },
                            onPlaylistClick = onPlaylistClick,
                            onSubscribeClick = { viewModel.toggleSubscription() },
                            onUnsubscribeClick = { viewModel.unsubscribe() },
                            onNotificationChange = { viewModel.setNotificationState(it) },
                            onManageGroups = { showGroupSheet = true },
                            onTabSelected = { viewModel.selectTab(it) },
                            onSearchToggle = { viewModel.setSearchActive(!uiState.searchActive) },
                            onSearchQueryChange = { viewModel.searchInChannel(it) },
                            onCommunityPostComments = viewModel::openCommunityPostComments,
                            onCommunityPostShare = { post ->
                                val shareIntent =
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/post/${post.id}")
                                    }
                                context.startActivity(
                                    Intent.createChooser(
                                        shareIntent,
                                        context.getString(R.string.share_community_post),
                                    ),
                                )
                            },
                            onLoadMoreCommunityPosts = viewModel::loadMoreCommunityPosts,
                            onRetryCommunityPosts = viewModel::retryCommunityPosts,
                            initialScrollIndex = viewModel.listScrollIndex,
                            initialScrollOffset = viewModel.listScrollOffset,
                            onScrollChanged = { idx, off -> viewModel.saveScrollPosition(idx, off) },
                            onCollapsedTitleVisibilityChange = { showCollapsedChannelTitle = it },
                        )
                    }
                }
            }
        }

        if (communityUiState.activePost != null) {
            FlowCommentsBottomSheet(
                comments = sortedCommunityComments,
                isLoading = communityUiState.isLoadingComments,
                onDismiss = viewModel::closeCommunityPostComments,
                selectedFilter = communityCommentSort,
                onFilterChanged = { communityCommentSort = it },
                isLoadingMore = communityUiState.isLoadingMoreComments,
                onLoadMore = viewModel::loadMoreCommunityPostComments,
                hasMore = communityUiState.commentsContinuation != null,
                onLoadReplies = viewModel::loadCommunityCommentReplies,
                onLoadMoreReplies = viewModel::loadMoreCommunityCommentReplies,
                onAuthorClick = { authorChannelId ->
                    if (authorChannelId.isNotBlank()) onChannelClick(authorChannelId)
                },
            )
        }
    }

    val channelId = uiState.header?.id.orEmpty()
    if (showGroupSheet && channelId.isNotBlank()) {
        ChannelGroupSheet(
            groups = subscriptionGroups,
            channelId = channelId,
            onToggle = { groupName, inGroup -> viewModel.setChannelInGroup(groupName, channelId, inGroup) },
            onCreateNew = {
                showGroupSheet = false
                showCreateGroupDialog = true
            },
            onDismiss = { showGroupSheet = false },
        )
    }

    if (showCreateGroupDialog && channelId.isNotBlank()) {
        CollectionEditDialog(
            title = stringResource(R.string.new_group),
            confirmLabel = stringResource(R.string.save),
            onDismiss = { showCreateGroupDialog = false },
            onConfirm = { name, _ ->
                viewModel.createGroupWithChannel(name, channelId)
                showCreateGroupDialog = false
            },
            icon = Icons.Rounded.Folder,
            showDescription = false,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ChannelContent(
    uiState: ChannelUiState,
    onManageGroups: (() -> Unit)?,
    communityUiState: ChannelCommunityUiState,
    tabStates: Map<ChannelTabKind, ChannelTabState>,
    onFilterSelected: (ChannelTabKind, Int) -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    onShortClick: (String) -> Unit,
    onPlaylistClick: (String) -> Unit,
    onSubscribeClick: () -> Unit,
    onUnsubscribeClick: () -> Unit,
    onNotificationChange: (Boolean) -> Unit,
    onTabSelected: (ChannelTabKind) -> Unit,
    onSearchToggle: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onCommunityPostComments: (CommunityPost) -> Unit,
    onCommunityPostShare: (CommunityPost) -> Unit,
    onLoadMoreCommunityPosts: () -> Unit,
    onRetryCommunityPosts: () -> Unit,
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (index: Int, offset: Int) -> Unit = { _, _ -> },
    onCollapsedTitleVisibilityChange: (Boolean) -> Unit = {},
) {
    val header = uiState.header ?: return

    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences =
        remember {
            io.github.aedev.flow.data.local
                .PlayerPreferences(context)
        }
    val isGridView by preferences.channelIsGridView.collectAsState(initial = false)
    val shortsContentEnabled by preferences.shortsContentEnabled.collectAsState(initial = true)
    val coroutineScope = rememberCoroutineScope()

    val aboutTitle = stringResource(R.string.tab_about)
    val visibleTabs =
        remember(uiState.tabs, uiState.header, shortsContentEnabled, aboutTitle) {
            channelScreenTabs(uiState.tabs, uiState.header, shortsContentEnabled, aboutTitle)
        }
    if (visibleTabs.isEmpty()) return

    // Keyed on the resolved list: the tab count changes once, when the header lands, and a pager
    // holding a stale count indexes out of bounds on the first swipe.
    val pagerState =
        key(visibleTabs) {
            rememberPagerState(
                initialPage = visibleTabs.indexOfFirst { it.kind == uiState.selectedTab }.coerceAtLeast(0),
                pageCount = { visibleTabs.size },
            )
        }

    val settledTab = visibleTabs.getOrElse(pagerState.settledPage) { visibleTabs.first() }

    // Persist only fully settled pages so an in-progress swipe cannot trigger a competing animation.
    LaunchedEffect(header.id, settledTab) {
        onTabSelected(settledTab.kind)
    }

    val activeState = tabStates[settledTab.kind]
    val activeSorts = activeState?.filters.orEmpty()
    val activeSortIndex = activeState?.selectedFilter ?: 0
    val showFilterBar = !settledTab.isAbout && settledTab.kind != ChannelTabKind.Posts

    var collapsingHeaderHeightPx by remember { mutableFloatStateOf(0f) }
    var stickySectionHeightPx by remember { mutableFloatStateOf(0f) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    val collapseTitleThresholdPx = with(density) { 2.dp.toPx() }
    val visibleHeaderHeightDp =
        with(density) {
            (collapsingHeaderHeightPx + stickySectionHeightPx + headerOffsetPx)
                .coerceAtLeast(stickySectionHeightPx)
                .toDp()
        }
    val headerMeasured by remember { derivedStateOf { collapsingHeaderHeightPx > 0f } }
    val showCollapsedTopBarTitle by remember(collapseTitleThresholdPx) {
        derivedStateOf {
            headerMeasured &&
                headerOffsetPx <= -collapsingHeaderHeightPx + collapseTitleThresholdPx
        }
    }

    LaunchedEffect(showCollapsedTopBarTitle) {
        onCollapsedTitleVisibilityChange(showCollapsedTopBarTitle)
    }

    DisposableEffect(Unit) {
        onDispose { onCollapsedTitleVisibilityChange(false) }
    }

    LaunchedEffect(collapsingHeaderHeightPx) {
        headerOffsetPx = headerOffsetPx.coerceIn(-collapsingHeaderHeightPx, 0f)
    }

    val nestedScrollConnection =
        remember(collapsingHeaderHeightPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y >= 0f) return Offset.Zero
                    val previous = headerOffsetPx
                    val next = (previous + available.y).coerceIn(-collapsingHeaderHeightPx, 0f)
                    headerOffsetPx = next
                    return Offset(x = 0f, y = next - previous)
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    if (available.y <= 0f) return Offset.Zero
                    val previous = headerOffsetPx
                    val next = (previous + available.y).coerceIn(-collapsingHeaderHeightPx, 0f)
                    headerOffsetPx = next
                    return Offset(x = 0f, y = next - previous)
                }
            }
        }

    // Persist Videos-tab scroll position across navigation
    val videosListState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = initialScrollIndex,
            initialFirstVisibleItemScrollOffset = initialScrollOffset,
        )
    val shortsListState = rememberLazyListState()
    val liveListState = rememberLazyListState()
    val playlistsListState = rememberLazyListState()
    val postsListState = rememberLazyListState()
    val aboutListState = rememberLazyListState()
    val genericListState = rememberLazyListState()

    fun listStateFor(kind: ChannelTabKind) =
        when (kind) {
            ChannelTabKind.Videos -> videosListState
            ChannelTabKind.Shorts -> shortsListState
            ChannelTabKind.Live -> liveListState
            ChannelTabKind.Playlists -> playlistsListState
            else -> genericListState
        }

    LaunchedEffect(videosListState) {
        snapshotFlow { videosListState.firstVisibleItemIndex to videosListState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> onScrollChanged(index, offset) }
    }

    LaunchedEffect(activeSortIndex, settledTab.kind) { listStateFor(settledTab.kind).scrollToItem(0) }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .nestedScroll(nestedScrollConnection),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (headerMeasured) 1f else 0f },
            verticalAlignment = Alignment.Top,
            userScrollEnabled = true,
        ) { page ->
            val listPadding = PaddingValues(top = visibleHeaderHeightDp)
            val tab = visibleTabs.getOrElse(page) { visibleTabs.first() }

            when {
                tab.isAbout -> {
                    LazyColumn(
                        state = aboutListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = listPadding,
                    ) {
                        item { AboutSection(header = header) }
                        item { Spacer(Modifier.height(16.dp)) }
                    }
                }

                tab.kind == ChannelTabKind.Posts -> {
                    ChannelCommunityPosts(
                        posts = communityUiState.posts,
                        isLoading = communityUiState.isLoadingPosts,
                        isLoadingMore = communityUiState.isLoadingMorePosts,
                        hasMore = communityUiState.postsContinuation != null,
                        errorLog = communityUiState.postsErrorLog,
                        listState = postsListState,
                        contentPadding = listPadding,
                        onAuthorClick = { onChannelClick(header.id) },
                        onCommentsClick = onCommunityPostComments,
                        onShareClick = onCommunityPostShare,
                        onLoadMore = onLoadMoreCommunityPosts,
                        onRetry = onRetryCommunityPosts,
                    )
                }

                uiState.searchActive && uiState.searchQuery.isNotBlank() && tab.kind == ChannelTabKind.Videos -> {
                    ChannelSearchResults(
                        uiState = uiState,
                        listState = videosListState,
                        contentPadding = listPadding,
                        topInset = visibleHeaderHeightDp,
                        isGridView = isGridView,
                        onVideoClick = onVideoClick,
                        onRetry = { onSearchQueryChange(uiState.searchQuery) },
                    )
                }

                else -> {
                    val items = tabStates[tab.kind]?.items?.collectAsLazyPagingItems()
                    ChannelTabItems(
                        pagingItems = items,
                        kind = tab.kind,
                        isGridView = isGridView,
                        listState = listStateFor(tab.kind),
                        contentPadding = listPadding,
                        topInset = visibleHeaderHeightDp,
                        onVideoClick = onVideoClick,
                        onShortClick = onShortClick,
                        onPlaylistClick = onPlaylistClick,
                        onChannelClick = onChannelClick,
                    )
                }
            }
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(x = 0, y = headerOffsetPx.roundToInt()) },
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .onSizeChanged { collapsingHeaderHeightPx = it.height.toFloat() },
            ) {
                ChannelHeaderSection(
                    header = header,
                    isSubscribed = uiState.isSubscribed,
                    isNotificationsEnabled = uiState.isNotificationsEnabled,
                    onSubscribeClick = onSubscribeClick,
                    onUnsubscribeClick = onUnsubscribeClick,
                    onNotificationChange = onNotificationChange,
                    onManageGroups = onManageGroups.takeIf { uiState.isSubscribed },
                )
            }

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .onSizeChanged { stickySectionHeightPx = it.height.toFloat() },
            ) {
                ChannelTabRow(
                    selectedIndex = pagerState.currentPage,
                    tabs = visibleTabs.map { it.title },
                    onTabSelected = { idx ->
                        coroutineScope.launch { pagerState.animateScrollToPage(idx) }
                    },
                )
                if (showFilterBar) {
                    FilterAndToggleBar(
                        sortOptions = activeSorts,
                        selectedSort = activeSortIndex,
                        isGridView = isGridView,
                        searchActive = uiState.searchActive,
                        searchQuery = uiState.searchQuery,
                        // The Shorts tab is a fixed portrait grid and has no in-channel search.
                        showListControls = settledTab.kind != ChannelTabKind.Shorts,
                        onSortSelected = { index -> onFilterSelected(settledTab.kind, index) },
                        onToggleGridView = { coroutineScope.launch { preferences.setChannelIsGridView(!isGridView) } },
                        onSearchToggle = onSearchToggle,
                        onSearchQueryChange = onSearchQueryChange,
                    )
                }
            }
        }
    }
}

// Filter + grid toggle bar
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterAndToggleBar(
    sortOptions: List<String>,
    selectedSort: Int,
    isGridView: Boolean,
    searchActive: Boolean = false,
    searchQuery: String = "",
    showListControls: Boolean = true,
    onSortSelected: (Int) -> Unit,
    onToggleGridView: () -> Unit,
    onSearchToggle: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (searchActive && showListControls) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                placeholder = { Text(stringResource(R.string.channel_search_hint), style = MaterialTheme.typography.bodySmall) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onSearch = { onSearchQueryChange(searchQuery) },
                    ),
                shape = RoundedCornerShape(20.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
            )
            IconButton(onClick = onSearchToggle) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.channel_search_close),
                    modifier = Modifier.size(22.dp),
                )
            }
        } else {
            Box(modifier = Modifier.weight(1f)) {
                SortChipRow(
                    options = sortOptions,
                    selectedIndex = selectedSort,
                    onSelected = onSortSelected,
                )
            }
            if (!showListControls) return@Row
            IconButton(onClick = onSearchToggle) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.channel_search_open),
                    modifier = Modifier.size(22.dp),
                )
            }
            IconButton(onClick = onToggleGridView) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = if (isGridView) stringResource(R.string.ui_list_view) else stringResource(R.string.ui_grid_view),
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

// Channel header — banner + avatar + info + subscribe
@Composable
private fun ChannelGroupSheet(
    groups: List<SubscriptionGroup>,
    channelId: String,
    onToggle: (String, Boolean) -> Unit,
    onCreateNew: () -> Unit,
    onDismiss: () -> Unit,
) {
    val entries =
        remember(groups, channelId) {
            groups.map { group ->
                CollectionSheetEntry(
                    id = group.name,
                    name = group.name,
                    supporting = "",
                    thumbnailUrl = "",
                    isSaved = channelId in group.channelIds,
                )
            }
        }

    SaveToCollectionSheet(
        title = stringResource(R.string.channel_groups_sheet_title),
        entries = entries,
        placeholderIcon = Icons.Rounded.Folder,
        createLabel = stringResource(R.string.new_group),
        emptyLabel = stringResource(R.string.channel_groups_empty),
        onToggle = { entry -> onToggle(entry.id, !entry.isSaved) },
        onCreateNew = onCreateNew,
        onDismiss = onDismiss,
    )
}

@Composable
private fun ChannelHeaderSection(
    header: ChannelHeader,
    isSubscribed: Boolean,
    isNotificationsEnabled: Boolean,
    onSubscribeClick: () -> Unit,
    onUnsubscribeClick: () -> Unit,
    onNotificationChange: (Boolean) -> Unit,
    onManageGroups: (() -> Unit)?,
) {
    val bannerUrl = remember(header.bannerUrl) { ThumbnailUrlResolver.resolveChannelBanner(header.bannerUrl, targetWidth = 2048) }
    var showFullSizeAvatar by remember(header.id) { mutableStateOf(false) }

    if (showFullSizeAvatar && header.avatarUrl.isNotEmpty()) {
        FullSizeImageDialog(
            imageUrl = header.avatarUrl,
            onDismiss = { showFullSizeAvatar = false },
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background),
    ) {
        if (!bannerUrl.isNullOrBlank()) {
            ChannelBanner(imageUrl = bannerUrl)
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelAvatarImage(
                url = header.avatarUrl,
                contentDescription = stringResource(R.string.channel_avatar),
                modifier =
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = header.avatarUrl.isNotEmpty()) { showFullSizeAvatar = true },
            )

            Spacer(modifier = Modifier.weight(1f))

            FlowSubscribeButton(
                isSubscribed = isSubscribed,
                isNotificationsEnabled = isNotificationsEnabled,
                onSubscribeClick = onSubscribeClick,
                onUnsubscribeClick = onUnsubscribeClick,
                onNotificationChange = onNotificationChange,
                onManageGroups = onManageGroups,
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = header.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            val metadata = remember(header) { listOfNotNull(header.handle, header.subscriberCountText, header.videoCountText) }
            if (metadata.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    metadata.forEach { entry ->
                        Text(
                            text = entry,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.extendedColors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

// Subscribe button
@Composable
private fun ChannelTabRow(
    selectedIndex: Int,
    tabs: List<String>,
    onTabSelected: (Int) -> Unit,
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
                color = MaterialTheme.colorScheme.primary,
            )
        },
        divider = {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                thickness = 0.5.dp,
            )
        },
    ) {
        tabs.forEachIndexed { index, title ->
            Tab(
                selected = selectedIndex == index,
                onClick = { onTabSelected(index) },
                modifier = Modifier.height(44.dp),
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.extendedColors.textSecondary,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selectedIndex == index) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun AboutSection(header: ChannelHeader) {
    val uriHandler = LocalUriHandler.current
    val rows =
        remember(header) {
            listOfNotNull(
                header.handle?.let { R.string.channel_about_handle to it },
                header.subscriberCountText?.let { R.string.subscribers to it },
                header.videoCountText?.let { R.string.channel_about_videos to it },
                header.viewCountText?.let { R.string.views to it },
                header.joinedDateText?.let { R.string.channel_about_joined to it },
                header.countryText?.let { R.string.channel_about_country to it },
            )
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!header.description.isNullOrBlank()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AboutHeading(stringResource(R.string.about))
                Text(
                    text = header.description,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (rows.isNotEmpty()) {
            if (!header.description.isNullOrBlank()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutHeading(stringResource(R.string.stats))
                rows.forEach { (labelRes, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.extendedColors.textSecondary,
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }

        if (header.links.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant, thickness = 0.5.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AboutHeading(stringResource(R.string.channel_about_links))
                header.links.forEach { link ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { uriHandler.openUri(link.url) },
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!link.iconUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = link.iconUrl,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = link.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.extendedColors.textSecondary,
    )
}
