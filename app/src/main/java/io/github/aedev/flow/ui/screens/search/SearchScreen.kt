package io.github.aedev.flow.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import io.github.aedev.flow.data.local.*
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.local.SearchHistoryItem
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.model.*
import io.github.aedev.flow.data.paging.SearchResultItem
import io.github.aedev.flow.data.recommendation.InterestProfile
import io.github.aedev.flow.data.search.SearchSuggestionsService
import io.github.aedev.flow.ui.components.*
import io.github.aedev.flow.utils.formatDuration
import io.github.aedev.flow.utils.formatViewCount
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch


@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val searchHistoryRepo = remember { SearchHistoryRepository(context) }
    val interestProfile = remember { InterestProfile.getInstance(context) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isGridMode by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var hasPerformedSearch by rememberSaveable { mutableStateOf(false) }
    var isNavigatingAway by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by searchHistoryRepo.getSearchHistoryFlow()
        .collectAsState(initial = emptyList())
    val suggestionsEnabled by searchHistoryRepo.isSearchSuggestionsEnabledFlow()
        .collectAsState(initial = true)
    val pagingItems = viewModel.searchResults.collectAsLazyPagingItems()

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var liveSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }

    val dismissKeyboard: () -> Unit = remember(focusManager, keyboardController) {
        {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
            isSearchFocused = false
        }
    }

    val voiceSearchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                searchQuery = spokenText
                dismissKeyboard()
                selectedTabIndex = 0
                viewModel.search(spokenText)
            }
        }
    }
    val launchVoiceSearch: () -> Unit = {
        val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak to search…")
        }
        try {
            voiceSearchLauncher.launch(intent)
        } catch (_: android.content.ActivityNotFoundException) { }
    }

    val navigateToVideo: (Video) -> Unit = remember(dismissKeyboard, onVideoClick) {
        { video ->
            isNavigatingAway = true
            hasPerformedSearch = true
            dismissKeyboard()
            onVideoClick(video)
        }
    }

    val navigateToChannel: (Channel) -> Unit = remember(dismissKeyboard, onChannelClick) {
        { channel ->
            isNavigatingAway = true
            hasPerformedSearch = true
            dismissKeyboard()
            onChannelClick(channel)
        }
    }

    val navigateToPlaylist: (Playlist) -> Unit = remember(dismissKeyboard, onPlaylistClick) {
        { playlist ->
            isNavigatingAway = true
            hasPerformedSearch = true
            dismissKeyboard()
            onPlaylistClick(playlist)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPerformedSearch) {
            delay(200)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(isNavigatingAway) {
        if (isNavigatingAway) {
            repeat(5) {
                delay(80)
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
            }
            isNavigatingAway = false
        }
    }

    LaunchedEffect(searchQuery, isSearchFocused) {
        if (searchQuery.length >= 2 && isSearchFocused && suggestionsEnabled) {
            isLoadingSuggestions = true
            delay(280)
            try {
                liveSuggestions = SearchSuggestionsService.getSuggestions(searchQuery)
            } catch (_: Exception) {
                liveSuggestions = emptyList()
            }
            isLoadingSuggestions = false
        } else if (searchQuery.length < 2) {
            liveSuggestions = emptyList()
        }
    }

    LaunchedEffect(uiState.query) {
        if (uiState.query.isNotBlank()) {
            searchHistoryRepo.saveSearchQuery(uiState.query)
            interestProfile.recordSearch(uiState.query)
        }
    }

    val tabContentTypes = listOf(
        ContentType.ALL, ContentType.VIDEOS, ContentType.CHANNELS,
        ContentType.PLAYLISTS, ContentType.LIVE
    )
    LaunchedEffect(selectedTabIndex) {
        if (uiState.query.isNotBlank()) {
            val base = uiState.filters ?: SearchFilter()
            viewModel.updateFilters(base.copy(contentType = tabContentTypes[selectedTabIndex]))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        SearchBarRow(
            query = searchQuery,
            onQueryChange = {
                if (!isNavigatingAway) {
                    searchQuery = it
                }
            },
            onSearch = {
                if (searchQuery.isNotBlank()) {
                    dismissKeyboard()
                    liveSuggestions = emptyList()
                    selectedTabIndex = 0

                    val videoId = extractVideoId(searchQuery)
                    if (videoId != null) {
                        navigateToVideo(
                            Video(
                                id = videoId,
                                title = "Shared Video",
                                channelName = "Shared Video",
                                channelId = "",
                                thumbnailUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                                duration = 0,
                                viewCount = 0L,
                                uploadDate = "",
                                channelThumbnailUrl = ""
                            )
                        )
                        return@SearchBarRow
                    }

                    viewModel.search(searchQuery)
                }
            },
            onClear = {
                searchQuery = ""
                liveSuggestions = emptyList()
                viewModel.clearSearch()
            },
            onVoiceSearch = launchVoiceSearch,
            isGridMode = isGridMode,
            onToggleGridMode = { isGridMode = !isGridMode },
            isSearchFocused = isSearchFocused,
            onFocusChange = { focused ->
                if (isNavigatingAway) return@SearchBarRow
                
                if (focused && hasPerformedSearch) {
                }
                
                isSearchFocused = focused
            },
            focusRequester = focusRequester,
            suppressAutoFocus = hasPerformedSearch,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        AnimatedVisibility(
            visible = isSearchFocused && searchQuery.isNotEmpty() &&
                    (liveSuggestions.isNotEmpty() || isLoadingSuggestions),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SuggestionsCard(
                query = searchQuery,
                suggestions = liveSuggestions,
                isLoading = isLoadingSuggestions,
                onSuggestionClick = { s ->
                    dismissKeyboard()
                    searchQuery = s
                    liveSuggestions = emptyList()
                    selectedTabIndex = 0

                    val videoId = extractVideoId(s)
                    if (videoId != null) {
                        navigateToVideo(
                            Video(
                                id = videoId,
                                title = "Shared Video",
                                channelName = "Shared Video",
                                channelId = "",
                                thumbnailUrl = "https://img.youtube.com/vi/$videoId/maxresdefault.jpg",
                                duration = 0,
                                viewCount = 0L,
                                uploadDate = "",
                                channelThumbnailUrl = ""
                            )
                        )
                    } else {
                        viewModel.search(s)
                    }
                },
                onFillClick = { searchQuery = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        val hasQuery = uiState.query.isNotBlank()

        if (!hasQuery) {
            DiscoverScreen(
                searchHistory = searchHistory,
                onHistoryClick = { q ->
                    dismissKeyboard()
                    searchQuery = q
                    selectedTabIndex = 0
                    viewModel.search(q)
                },
                onHistoryDelete = { item ->
                    scope.launch { searchHistoryRepo.deleteSearchItem(item.id) }
                },
                onClearHistory = {
                    scope.launch { searchHistoryRepo.clearSearchHistory() }
                }
            )
        } else {
            SearchTabRow(
                selectedTabIndex = selectedTabIndex,
                tabLabels = listOf("All", "Videos", "Channels", "Playlists", "Live"),
                onTabSelected = { selectedTabIndex = it }
            )

            if (selectedTabIndex == 0 || selectedTabIndex == 1 || selectedTabIndex == 4) {
                SearchFilterRow(
                    selectedDuration = uiState.filters?.duration ?: Duration.ANY,
                    onDurationSelected = { dur ->
                        val base = uiState.filters ?: SearchFilter()
                        viewModel.updateFilters(base.copy(duration = dur))
                    },
                    selectedUploadDate = uiState.filters?.uploadDate ?: UploadDate.ANY,
                    onUploadDateSelected = { date ->
                        val base = uiState.filters ?: SearchFilter()
                        viewModel.updateFilters(base.copy(uploadDate = date))
                    }
                )
            }

            val isInitialLoading =
                pagingItems.loadState.refresh is LoadState.Loading
            val isInitialError =
                pagingItems.loadState.refresh is LoadState.Error && pagingItems.itemCount == 0

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val responsiveColumns = when {
                    maxWidth < 700.dp -> 1
                    maxWidth < 900.dp -> 2
                    maxWidth < 1200.dp -> 3
                    else -> 4
                }

                val responsiveGridColumns = when {
                    maxWidth < 600.dp -> 2
                    maxWidth < 900.dp -> 3
                    maxWidth < 1200.dp -> 4
                    else -> 5
                }

                val columns = if (isGridMode) responsiveGridColumns else responsiveColumns

                when {
                    isInitialLoading -> ShimmerResultsScreen(isGridMode, columns)
                    isInitialError -> {
                        val err =
                            (pagingItems.loadState.refresh as LoadState.Error).error
                        SearchErrorState(
                            message = err.localizedMessage ?: "Search failed",
                            onRetry = pagingItems::retry
                        )
                    }
                    else -> {
                        if (isGridMode) {
                            SearchResultGrid(
                                pagingItems, gridState, columns,
                                navigateToVideo, navigateToChannel, navigateToPlaylist,
                                dismissKeyboard
                            )
                        } else {
                            SearchResultList(
                                pagingItems, gridState, columns,
                                navigateToVideo, navigateToChannel, navigateToPlaylist,
                                dismissKeyboard
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun extractVideoId(url: String): String? {
    if (!url.contains("youtube.com") && !url.contains("youtu.be")) return null
    val patterns = listOf(
        Regex("v=([^&]+)"),
        Regex("shorts/([^/?]+)"),
        Regex("youtu.be/([^/?]+)"),
        Regex("embed/([^/?]+)"),
        Regex("v/([^/?]+)")
    )
    for (pattern in patterns) {
        val match = pattern.find(url)
        if (match != null) return match.groupValues[1]
    }
    return url.substringAfterLast("/").substringBefore("?").ifEmpty { null }
}

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onVoiceSearch: () -> Unit,
    isGridMode: Boolean,
    onToggleGridMode: () -> Unit,
    isSearchFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    suppressAutoFocus: Boolean = false,
    modifier: Modifier = Modifier
) {
    val focusAnim by animateFloatAsState(
        targetValue = if (isSearchFocused) 1f else 0f,
        animationSpec = tween(300),
        label = "focus"
    )
    val primary = MaterialTheme.colorScheme.primary
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var userInitiatedFocus by remember { mutableStateOf(false) }
    
    var tapCounter by remember { mutableIntStateOf(0) }
    var lastProcessedTap by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .drawBehind {
                if (focusAnim > 0f) {
                    drawRoundRect(
                        brush = Brush.sweepGradient(
                            listOf(
                                primary.copy(alpha = focusAnim * 0.9f),
                                primary.copy(alpha = focusAnim * 0.3f),
                                primary.copy(alpha = focusAnim * 0.9f)
                            )
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                            23.dp.toPx()
                        ),
                        style = Stroke(width = (2.5f * focusAnim).dp.toPx())
                    )
                }
            }
            .background(
                color = if (isSearchFocused)
                    MaterialTheme.colorScheme.surface
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(23.dp)
            )
            .clickable(
                indication = null,
                interactionSource = remember { 
                    androidx.compose.foundation.interaction.MutableInteractionSource() 
                }
            ) {
                userInitiatedFocus = true
                tapCounter++
                focusRequester.requestFocus()
                keyboardController?.show()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                tint = if (isSearchFocused) primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))

            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused && suppressAutoFocus) {
                            val isUserTap = tapCounter > lastProcessedTap
                            if (isUserTap) {
                                lastProcessedTap = tapCounter
                                userInitiatedFocus = false
                                onFocusChange(true)
                            } else {
                                focusManager.clearFocus(force = true)
                                keyboardController?.hide()
                            }
                        } else {
                            onFocusChange(state.isFocused)
                        }
                    },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = { if (query.isNotBlank()) onSearch() }
                ),
                cursorBrush = SolidColor(primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (query.isEmpty()) {
                            Text(
                                "Search videos, channels\u2026",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                    alpha = 0.55f
                                ),
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            if (query.isEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onVoiceSearch),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = "Voice search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleGridMode),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isGridMode) Icons.Outlined.ViewList else Icons.Outlined.GridView,
                    contentDescription = "Toggle view",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchTabRow(
    selectedTabIndex: Int,
    tabLabels: List<String>,
    onTabSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LazyRow(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(tabLabels.size) { i ->
                val isSelected = selectedTabIndex == i
                val bgColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    tween(200), label = "tabBg"
                )
                val txtColor by animateColorAsState(
                    if (isSelected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    tween(200), label = "tabTxt"
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(bgColor)
                        .clickable { onTabSelected(i) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tabLabels[i],
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold
                        else FontWeight.Normal,
                        color = txtColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultList(
    pagingItems: androidx.paging.compose.LazyPagingItems<SearchResultItem>,
    gridState: LazyGridState,
    columns: Int,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    dismissKeyboard: () -> Unit
) {
    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(
                            pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                        )
                        if (event.changes.any { it.pressed }) {
                            dismissKeyboard()
                        }
                    }
                }
            },
        contentPadding = PaddingValues(
            start = if (columns == 1) 0.dp else 16.dp,
            end = if (columns == 1) 0.dp else 16.dp,
            top = 8.dp,
            bottom = 90.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(
            if (columns == 1) 0.dp else 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(
            if (columns == 1) 0.dp else 12.dp
        )
    ) {
        items(
            count = pagingItems.itemCount,
            key = { i ->
                val prefix = when (val it = pagingItems.peek(i)) {
                    is SearchResultItem.VideoResult -> "v_${it.video.id}"
                    is SearchResultItem.ChannelResult -> "c_${it.channel.id}"
                    is SearchResultItem.PlaylistResult -> "p_${it.playlist.id}"
                    null -> "null"
                }
                "${prefix}_$i"
            }
        ) { i ->
            val item = pagingItems[i] ?: return@items
            AnimatedVisibility(
                visible = true,
                enter = fadeIn(tween(250, (i % 10) * 30)) +
                        slideInVertically(initialOffsetY = { it / 4 })
            ) {
                when (item) {
                    is SearchResultItem.VideoResult ->
                        VideoCardFullWidth(
                            video = item.video,
                            modifier = Modifier.padding(vertical = 6.dp),
                            onClick = {
                                onVideoClick(item.video)
                            },
                            onChannelClick = { channelId ->
                                onChannelClick(
                                    Channel(
                                        id = channelId,
                                        name = item.video.channelName,
                                        thumbnailUrl = item.video.channelThumbnailUrl
                                            ?: "",
                                        subscriberCount = 0,
                                        url = "https://www.youtube.com/channel/$channelId"
                                    )
                                )
                            }
                        )
                    is SearchResultItem.ChannelResult ->
                        SearchChannelCard(
                            item.channel,
                            onClick = {
                                onChannelClick(item.channel)
                            }
                        )
                    is SearchResultItem.PlaylistResult ->
                        SearchPlaylistCard(
                            item.playlist,
                            onClick = {
                                onPlaylistClick(item.playlist)
                            }
                        )
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            PagingFooter(
                pagingItems.loadState.append,
                pagingItems::retry,
                pagingItems.itemCount
            )
        }
    }
}

@Composable
private fun SearchResultGrid(
    pagingItems: androidx.paging.compose.LazyPagingItems<SearchResultItem>,
    gridState: LazyGridState,
    columns: Int,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    dismissKeyboard: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = gridState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(
                            pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial
                        )
                        if (event.changes.any { it.pressed }) {
                            dismissKeyboard()
                        }
                    }
                }
            },
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(
            count = pagingItems.itemCount,
            key = { i ->
                val prefix = when (val it = pagingItems.peek(i)) {
                    is SearchResultItem.VideoResult -> "v_${it.video.id}"
                    is SearchResultItem.ChannelResult -> "c_${it.channel.id}"
                    is SearchResultItem.PlaylistResult -> "p_${it.playlist.id}"
                    null -> "null"
                }
                "${prefix}_$i"
            }
        ) { i ->
            val item = pagingItems[i] ?: return@items
            when (item) {
                is SearchResultItem.VideoResult ->
                    CompactVideoCard(
                        video = item.video,
                        onClick = {
                            onVideoClick(item.video)
                        },
                        onChannelClick = { channelId ->
                            onChannelClick(
                                Channel(
                                    id = channelId,
                                    name = item.video.channelName,
                                    thumbnailUrl = item.video.channelThumbnailUrl
                                        ?: "",
                                    subscriberCount = 0,
                                    url = "https://www.youtube.com/channel/$channelId"
                                )
                            )
                        }
                    )
                is SearchResultItem.ChannelResult ->
                    SearchChannelCardCompact(
                        item.channel,
                        onClick = {
                            onChannelClick(item.channel)
                        }
                    )
                is SearchResultItem.PlaylistResult ->
                    SearchPlaylistCardCompact(
                        item.playlist,
                        onClick = {
                            onPlaylistClick(item.playlist)
                        }
                    )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            PagingFooter(
                pagingItems.loadState.append,
                pagingItems::retry,
                pagingItems.itemCount
            )
        }
    }
}

@Composable
private fun PagingFooter(
    appendState: LoadState,
    onRetry: () -> Unit,
    itemCount: Int
) {
    when {
        appendState is LoadState.Loading -> {
            Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Loading more\u2026",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        appendState is LoadState.Error -> {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    appendState.error.localizedMessage ?: "Load failed",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2
                )
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retry", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        appendState.endOfPaginationReached && itemCount > 0 -> {
            Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(Modifier.weight(1f))
                    Text(
                        "End of results",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = 0.5f
                        )
                    )
                    HorizontalDivider(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ShimmerResultsScreen(isGrid: Boolean, columns: Int) {
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(8) { ShimmerGridVideoCard() }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = if (columns == 1) 0.dp else 16.dp,
                end = if (columns == 1) 0.dp else 16.dp,
                top = 8.dp,
                bottom = 80.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(
                if (columns == 1) 0.dp else 12.dp
            ),
            verticalArrangement = Arrangement.spacedBy(
                if (columns == 1) 0.dp else 12.dp
            )
        ) {
            items(8) {
                if (columns == 1) ShimmerVideoCardFullWidth()
                else ShimmerGridVideoCard()
            }
        }
    }
}

@Composable
private fun DiscoverScreen(
    searchHistory: List<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        if (searchHistory.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Recent Searches",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    TextButton(onClick = onClearHistory) {
                        Text("Clear all", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            items(searchHistory.take(8), key = { it.id }) { item ->
                HistoryRow(
                    item = item,
                    onClick = { onHistoryClick(item.query) },
                    onDelete = { onHistoryDelete(item) }
                )
            }
            item {
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }

        if (searchHistory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(bottom = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.2f
                            )
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Search for videos, music and more",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.6f
                            )
                        )
                    }
                }
            }
        }
    }
}


@Composable
private fun HistoryRow(
    item: SearchHistoryItem,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (item.type == SearchType.VOICE) Icons.Filled.Mic
            else Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            item.query,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Filled.Close, "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SuggestionsCard(
    query: String,
    suggestions: List<String>,
    isLoading: Boolean,
    onSuggestionClick: (String) -> Unit,
    onFillClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(10.dp),
        colors = CardDefaults.cardColors(MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        LazyColumn(Modifier.heightIn(max = 300.dp)) {
            if (isLoading && suggestions.isEmpty()) {
                item {
                    Box(
                        Modifier.fillMaxWidth().padding(16.dp),
                        Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
            items(suggestions) { s ->
                SuggestionRow(
                    s, query,
                    { onSuggestionClick(s) },
                    { onFillClick(s) }
                )
            }
        }
    }
}

@Composable
private fun SuggestionRow(
    suggestion: String,
    query: String,
    onClick: () -> Unit,
    onFill: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Search, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Text(
            buildAnnotatedString {
                val lo = suggestion.lowercase()
                val qlo = query.lowercase()
                val idx = lo.indexOf(qlo)
                if (idx >= 0) {
                    append(suggestion.substring(0, idx))
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        append(
                            suggestion.substring(idx, idx + query.length)
                        )
                    }
                    append(suggestion.substring(idx + query.length))
                } else append(suggestion)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = onFill, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Outlined.NorthWest, "Fill",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}


@Composable
private fun SearchVideoCard(
    video: Video,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource =
        remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.98f else 1f,
        spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh),
        label = "sc"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(interactionSource, null, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                video.thumbnailUrl, video.title, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(0.55f)
                            )
                        )
                    )
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(0.78f)
            ) {
                Text(
                    formatDuration(video.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        horizontal = 6.dp,
                        vertical = 3.dp
                    )
                )
            }

            if (video.isShort) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF1565C0)
                ) {
                    Text(
                        "SHORT",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(
                            horizontal = 6.dp,
                            vertical = 3.dp
                        )
                    )
                }
            }
            if (video.isLive) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFD32F2F)
                ) {
                    Text(
                        "\u25CF LIVE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(
                            horizontal = 6.dp,
                            vertical = 3.dp
                        )
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 4.dp,
                    end = 4.dp,
                    top = 10.dp,
                    bottom = 6.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = video.channelThumbnailUrl.takeIf { it.isNotEmpty() }
                    ?: Icons.Default.AccountCircle,
                contentDescription = video.channelName,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        video.channelName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Dot()
                    Text(
                        formatViewCount(video.viewCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (video.uploadDate.isNotBlank()) {
                        Dot()
                        Text(
                            video.uploadDate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchVideoCardCompact(
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
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                video.thumbnailUrl, video.title, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                shape = RoundedCornerShape(5.dp),
                color = Color.Black.copy(0.78f)
            ) {
                Text(
                    formatDuration(video.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        horizontal = 5.dp,
                        vertical = 2.dp
                    )
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            video.title,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            video.channelName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 1.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            Arrangement.spacedBy(14.dp), Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(72.dp)
                        .background(
                            Brush.sweepGradient(
                                listOf(
                                    primary,
                                    primary.copy(0.3f),
                                    primary
                                )
                            ), CircleShape
                        )
                )
                AsyncImage(
                    channel.thumbnailUrl, channel.name,
                    Modifier
                        .size(66.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (channel.subscriberCount > 0) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        formatSubs(channel.subscriberCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (channel.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun SearchChannelCardCompact(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AsyncImage(
            channel.thumbnailUrl, channel.name,
            Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop
        )
        Text(
            channel.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        Arrangement.spacedBy(14.dp)
    ) {
        Box(
            Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                playlist.thumbnailUrl, playlist.name,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .align(Alignment.CenterEnd)
                    .background(Color.Black.copy(0.65f)),
                Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.PlaylistPlay, null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        playlist.videoCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = Color.White
                    )
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${playlist.videoCount} videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                Modifier.padding(top = 6.dp),
                RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    "Playlist",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(
                        horizontal = 8.dp,
                        vertical = 3.dp
                    )
                )
            }
        }
    }
}

@Composable
private fun SearchPlaylistCardCompact(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                playlist.thumbnailUrl, playlist.name,
                Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            playlist.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            "${playlist.videoCount} videos",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SearchErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                Icons.Outlined.WifiOff, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
            Text(
                "Search Failed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}

@Composable
private fun Dot() {
    Text(
        "\u00B7",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    )
}

private fun formatSubs(count: Long): String = when {
    count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M subscribers"
    count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K subscribers"
    count > 0 -> "$count subscribers"
    else -> ""
}

@Composable
private fun SearchFilterRow(
    selectedDuration: Duration,
    onDurationSelected: (Duration) -> Unit,
    selectedUploadDate: UploadDate,
    onUploadDateSelected: (UploadDate) -> Unit
) {
    val durationLabels = listOf(
        Duration.ANY to "Any Length",
        Duration.UNDER_4_MINUTES to "< 4 mins",
        Duration.FROM_4_TO_20_MINUTES to "4-20 mins",
        Duration.OVER_20_MINUTES to "> 20 mins"
    )
    val uploadDateLabels = listOf(
        UploadDate.ANY to "Any Time",
        UploadDate.TODAY to "Today",
        UploadDate.THIS_WEEK to "This Week",
        UploadDate.THIS_MONTH to "This Month",
        UploadDate.THIS_YEAR to "This Year"
    )

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item {
            Icon(
                imageVector = Icons.Default.FilterList,
                contentDescription = "Filters",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(4.dp))
        }

        item {
            var durationExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = selectedDuration != Duration.ANY,
                    onClick = { durationExpanded = true },
                    label = {
                        Text(
                            durationLabels.first { it.first == selectedDuration }.second
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                )
                DropdownMenu(
                    expanded = durationExpanded,
                    onDismissRequest = { durationExpanded = false }
                ) {
                    durationLabels.forEach { (dur, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onDurationSelected(dur)
                                durationExpanded = false
                            }
                        )
                    }
                }
            }
        }

        item {
            var dateExpanded by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = selectedUploadDate != UploadDate.ANY,
                    onClick = { dateExpanded = true },
                    label = {
                        Text(
                            uploadDateLabels.first {
                                it.first == selectedUploadDate
                            }.second
                        )
                    },
                    trailingIcon = {
                        Icon(Icons.Default.ArrowDropDown, null)
                    }
                )
                DropdownMenu(
                    expanded = dateExpanded,
                    onDismissRequest = { dateExpanded = false }
                ) {
                    uploadDateLabels.forEach { (date, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                onUploadDateSelected(date)
                                dateExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}