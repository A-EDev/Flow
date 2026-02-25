package com.flow.youtube.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import com.flow.youtube.data.local.*
import com.flow.youtube.data.local.SearchFilter
import com.flow.youtube.data.local.SearchHistoryItem
import com.flow.youtube.data.local.UploadDate
import com.flow.youtube.data.local.Duration
import com.flow.youtube.data.local.SortBy
import com.flow.youtube.data.local.Feature
import com.flow.youtube.data.local.ContentType
import com.flow.youtube.data.model.*
import com.flow.youtube.data.paging.SearchResultItem
import com.flow.youtube.data.recommendation.InterestProfile
import com.flow.youtube.data.search.SearchSuggestionsService
import com.flow.youtube.ui.components.*
import com.flow.youtube.utils.formatDuration
import com.flow.youtube.utils.formatViewCount
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch



private data class DiscoverCategory(
    val label: String,
    val emoji: String,
    val gradientStart: Int,
    val gradientEnd: Int,
    val query: String
)

private val discoverCategories = listOf(
    DiscoverCategory("Music",        "\uD83C\uDFB5", 0xFF7C4DFF.toInt(), 0xFFB388FF.toInt(), "music 2024"),
    DiscoverCategory("Gaming",       "\uD83C\uDFAE", 0xFF00BCD4.toInt(), 0xFF00E5FF.toInt(), "gaming highlights"),
    DiscoverCategory("Technology",   "\uD83D\uDCBB", 0xFF1565C0.toInt(), 0xFF42A5F5.toInt(), "tech reviews 2024"),
    DiscoverCategory("Sports",       "\u26BD",        0xFF2E7D32.toInt(), 0xFF66BB6A.toInt(), "sports highlights"),
    DiscoverCategory("Comedy",       "\uD83D\uDE02", 0xFFE65100.toInt(), 0xFFFFA726.toInt(), "funny videos"),
    DiscoverCategory("Education",    "\uD83D\uDCDA", 0xFF4CAF50.toInt(), 0xFF8BC34A.toInt(), "educational"),
    DiscoverCategory("Food",         "\uD83C\uDF55", 0xFFC62828.toInt(), 0xFFEF5350.toInt(), "food recipes"),
    DiscoverCategory("Travel",       "\u2708\uFE0F",  0xFF0277BD.toInt(), 0xFF29B6F6.toInt(), "travel vlog"),
    DiscoverCategory("Fitness",      "\uD83C\uDFCB", 0xFF558B2F.toInt(), 0xFF9CCC65.toInt(), "workout fitness"),
    DiscoverCategory("Science",      "\uD83D\uDD2C", 0xFF6A1B9A.toInt(), 0xFFCE93D8.toInt(), "science explained"),
)

@OptIn(FlowPreview::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel()
) {
    val context = LocalContext.current
    val searchHistoryRepo = remember { SearchHistoryRepository(context) }
    val interestProfile = remember { InterestProfile.getInstance(context) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var isGridMode by remember { mutableStateOf(false) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by searchHistoryRepo.getSearchHistoryFlow().collectAsState(initial = emptyList())
    val suggestionsEnabled by searchHistoryRepo.isSearchSuggestionsEnabledFlow().collectAsState(initial = true)
    val pagingItems = viewModel.searchResults.collectAsLazyPagingItems()

    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var liveSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, isSearchFocused) {
        if (searchQuery.length >= 2 && isSearchFocused && suggestionsEnabled) {
            isLoadingSuggestions = true
            delay(280)
            try {
                liveSuggestions = SearchSuggestionsService.getSuggestions(searchQuery)
            } catch (_: Exception) { liveSuggestions = emptyList() }
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

    val tabContentTypes = listOf(ContentType.ALL, ContentType.VIDEOS, ContentType.CHANNELS, ContentType.PLAYLISTS)
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
            onQueryChange = { searchQuery = it },
            onSearch = {
                if (searchQuery.isNotBlank()) {
                    isSearchFocused = false
                    liveSuggestions = emptyList()
                    selectedTabIndex = 0
                    viewModel.search(searchQuery)
                }
            },
            onClear = {
                searchQuery = ""
                liveSuggestions = emptyList()
                viewModel.clearSearch()
            },
            onFilterClick = { showFilters = !showFilters },
            hasActiveFilters = uiState.filters != null && viewModel.hasActiveFilters(uiState.filters),
            isSearchFocused = isSearchFocused,
            onFocusChange = { isSearchFocused = it },
            focusRequester = focusRequester,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        )

        AnimatedVisibility(
            visible = showFilters,
            enter = expandVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
            exit = shrinkVertically(spring(stiffness = Spring.StiffnessMediumLow)) + fadeOut()
        ) {
            SearchFiltersRow(
                filters = uiState.filters,
                onFiltersChange = { viewModel.updateFilters(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

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
                    searchQuery = s
                    isSearchFocused = false
                    liveSuggestions = emptyList()
                    selectedTabIndex = 0
                    viewModel.search(s)
                },
                onFillClick = { searchQuery = it },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        val hasQuery = uiState.query.isNotBlank()

        if (!hasQuery) {
            DiscoverScreen(
                searchHistory = searchHistory,
                onHistoryClick = { q -> searchQuery = q; selectedTabIndex = 0; viewModel.search(q) },
                onHistoryDelete = { item -> scope.launch { searchHistoryRepo.deleteSearchItem(item.id) } },
                onClearHistory = { scope.launch { searchHistoryRepo.clearSearchHistory() } },
                onCategoryClick = { q -> searchQuery = q; selectedTabIndex = 0; viewModel.search(q) }
            )
        } else {
            SearchTabRow(
                selectedTabIndex = selectedTabIndex,
                tabLabels = listOf("All", "Videos", "Channels", "Playlists"),
                onTabSelected = { selectedTabIndex = it },
                isGridMode = isGridMode,
                onToggleGridMode = { isGridMode = !isGridMode }
            )

            val isInitialLoading = pagingItems.loadState.refresh is LoadState.Loading
            val isInitialError = pagingItems.loadState.refresh is LoadState.Error && pagingItems.itemCount == 0

            when {
                isInitialLoading -> ShimmerResultsScreen(isGridMode)
                isInitialError -> {
                    val err = (pagingItems.loadState.refresh as LoadState.Error).error
                    SearchErrorState(
                        message = err.localizedMessage ?: "Search failed",
                        onRetry = pagingItems::retry
                    )
                }
                else -> {
                    if (isGridMode) {
                        SearchResultGrid(pagingItems, gridState, onVideoClick, onChannelClick)
                    } else {
                        SearchResultList(pagingItems, listState, onVideoClick, onChannelClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchBarRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onFilterClick: () -> Unit,
    hasActiveFilters: Boolean,
    isSearchFocused: Boolean,
    onFocusChange: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val focusAnim by animateFloatAsState(
        targetValue = if (isSearchFocused) 1f else 0f,
        animationSpec = tween(300),
        label = "focus"
    )
    val primary = MaterialTheme.colorScheme.primary

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
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
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(26.dp.toPx()),
                            style = Stroke(width = (2.5f * focusAnim).dp.toPx())
                        )
                    }
                }
                .background(
                    color = if (isSearchFocused)
                        MaterialTheme.colorScheme.surface
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(26.dp)
                )
        ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onFocusChanged { onFocusChange(it.isFocused) },
                placeholder = {
                    Text(
                        "Search videos, channels\u2026",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.Search,
                        contentDescription = null,
                        tint = if (isSearchFocused) primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onSearch() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (hasActiveFilters) primary.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
                .clickable(onClick = onFilterClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.FilterList,
                contentDescription = "Filters",
                tint = if (hasActiveFilters) primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            if (hasActiveFilters) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(primary, CircleShape)
                        .align(Alignment.TopEnd)
                        .offset((-12).dp, 12.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchTabRow(
    selectedTabIndex: Int,
    tabLabels: List<String>,
    onTabSelected: (Int) -> Unit,
    isGridMode: Boolean,
    onToggleGridMode: () -> Unit
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
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = txtColor
                    )
                }
            }
        }

        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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

@Composable
private fun SearchResultList(
    pagingItems: androidx.paging.compose.LazyPagingItems<SearchResultItem>,
    listState: LazyListState,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 90.dp)
    ) {
        items(
            count = pagingItems.itemCount,
            key = { i ->
                when (val it = pagingItems.peek(i)) {
                    is SearchResultItem.VideoResult -> "v_${it.video.id}"
                    is SearchResultItem.ChannelResult -> "c_${it.channel.id}"
                    is SearchResultItem.PlaylistResult -> "p_${it.playlist.id}"
                    null -> "null_$i"
                }
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
                            onClick = { onVideoClick(item.video) }
                        )
                    is SearchResultItem.ChannelResult ->
                        SearchChannelCard(item.channel, onClick = { onChannelClick(item.channel) })
                    is SearchResultItem.PlaylistResult ->
                        SearchPlaylistCard(item.playlist, onClick = {})
                }
            }
        }

        item { PagingFooter(pagingItems.loadState.append, pagingItems::retry, pagingItems.itemCount) }
    }
}

@Composable
private fun SearchResultGrid(
    pagingItems: androidx.paging.compose.LazyPagingItems<SearchResultItem>,
    gridState: LazyGridState,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (Channel) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(
            count = pagingItems.itemCount,
            key = { i ->
                when (val it = pagingItems.peek(i)) {
                    is SearchResultItem.VideoResult -> "v_${it.video.id}"
                    is SearchResultItem.ChannelResult -> "c_${it.channel.id}"
                    is SearchResultItem.PlaylistResult -> "p_${it.playlist.id}"
                    null -> "null_$i"
                }
            }
        ) { i ->
            val item = pagingItems[i] ?: return@items
            when (item) {
                is SearchResultItem.VideoResult ->
                    CompactVideoCard(
                        video = item.video,
                        onClick = { onVideoClick(item.video) }
                    )
                is SearchResultItem.ChannelResult ->
                    SearchChannelCardCompact(item.channel, onClick = { onChannelClick(item.channel) })
                is SearchResultItem.PlaylistResult ->
                    SearchPlaylistCardCompact(item.playlist, onClick = {})
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            PagingFooter(pagingItems.loadState.append, pagingItems::retry, pagingItems.itemCount)
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
                    Text("Loading more\u2026", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    HorizontalDivider(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ShimmerResultsScreen(isGrid: Boolean) {
    if (isGrid) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(8) { ShimmerGridVideoCard() }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            items(8) { ShimmerVideoCardFullWidth() }
        }
    }
}

@Composable
private fun DiscoverScreen(
    searchHistory: List<SearchHistoryItem>,
    onHistoryClick: (String) -> Unit,
    onHistoryDelete: (SearchHistoryItem) -> Unit,
    onClearHistory: () -> Unit,
    onCategoryClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 90.dp)
    ) {
        if (searchHistory.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
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
            item { HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) }
        }

        item {
            Text(
                "Discover",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        items(discoverCategories.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { cat ->
                    DiscoverCategoryTile(
                        category = cat,
                        onClick = { onCategoryClick(cat.query) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DiscoverCategoryTile(
    category: DiscoverCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(78.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(category.gradientStart), Color(category.gradientEnd))
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(category.emoji, fontSize = 20.sp)
            Text(
                category.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
            if (item.type == SearchType.VOICE) Icons.Filled.Mic else Icons.Filled.History,
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
                    Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                }
            }
            items(suggestions) { s ->
                SuggestionRow(s, query, { onSuggestionClick(s) }, { onFillClick(s) })
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
        Icon(Icons.Outlined.Search, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp))
        Text(
            buildAnnotatedString {
                val lo = suggestion.lowercase(); val qlo = query.lowercase()
                val idx = lo.indexOf(qlo)
                if (idx >= 0) {
                    append(suggestion.substring(0, idx))
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)) {
                        append(suggestion.substring(idx, idx + query.length))
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
            Icon(Icons.Outlined.NorthWest, "Fill", tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp))
        }
    }
}


@Composable
private fun SearchVideoCard(video: Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) 0.98f else 1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessHigh), label = "sc"
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
            AsyncImage(video.thumbnailUrl, video.title, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)

            Box(
                Modifier.fillMaxWidth().height(60.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.55f))))
            )

            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                shape = RoundedCornerShape(6.dp),
                color = Color.Black.copy(0.78f)
            ) {
                Text(formatDuration(video.duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
            }

            if (video.isShort) {
                Surface(modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = RoundedCornerShape(6.dp), color = Color(0xFF1565C0)) {
                    Text("SHORT", style = MaterialTheme.typography.labelSmall,
                        color = Color.White, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
            if (video.isLive) {
                Surface(modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = RoundedCornerShape(6.dp), color = Color(0xFFD32F2F)) {
                    Text("\u25CF LIVE", style = MaterialTheme.typography.labelSmall,
                        color = Color.White, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = video.channelThumbnailUrl.takeIf { it.isNotEmpty() } ?: Icons.Default.AccountCircle,
                contentDescription = video.channelName,
                modifier = Modifier.size(34.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Column(Modifier.weight(1f)) {
                Text(video.title, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(video.channelName, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false))
                    Dot()
                    Text(formatViewCount(video.viewCount), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    if (video.uploadDate.isNotBlank()) {
                        Dot()
                        Text(video.uploadDate, style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchVideoCardCompact(video: Video, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(video.thumbnailUrl, video.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp),
                shape = RoundedCornerShape(5.dp), color = Color.Black.copy(0.78f)) {
                Text(formatDuration(video.duration), style = MaterialTheme.typography.labelSmall,
                    color = Color.White, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(video.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        Text(video.channelName, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchChannelCard(channel: Channel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        tonalElevation = 1.dp
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp),
            Arrangement.spacedBy(14.dp), Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(72.dp).background(
                    Brush.sweepGradient(listOf(primary, primary.copy(0.3f), primary)), CircleShape))
                AsyncImage(channel.thumbnailUrl, channel.name,
                    Modifier.size(66.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop)
            }
            Column(Modifier.weight(1f)) {
                Text(channel.name, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (channel.subscriberCount > 0) {
                    Spacer(Modifier.height(3.dp))
                    Text(formatSubs(channel.subscriberCount), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (channel.description.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(channel.description, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Icon(Icons.Default.ChevronRight, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SearchChannelCardCompact(channel: Channel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.35f))
            .clickable(onClick = onClick).padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AsyncImage(channel.thumbnailUrl, channel.name,
            Modifier.size(60.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
            contentScale = ContentScale.Crop)
        Text(channel.name, style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchPlaylistCard(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 8.dp), Arrangement.spacedBy(14.dp)) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(playlist.thumbnailUrl, playlist.name, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxHeight().aspectRatio(1f).align(Alignment.CenterEnd)
                .background(Color.Black.copy(0.65f)), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PlaylistPlay, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Text(playlist.videoCount.toString(), style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
        Column(Modifier.weight(1f)) {
            Text(playlist.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text("${playlist.videoCount} videos", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(Modifier.padding(top = 6.dp), RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer) {
                Text("Playlist", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}

@Composable
private fun SearchPlaylistCardCompact(playlist: Playlist, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)) {
            AsyncImage(playlist.thumbnailUrl, playlist.name, Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop)
        }
        Spacer(Modifier.height(6.dp))
        Text(playlist.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        Text("${playlist.videoCount} videos", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Outlined.WifiOff, null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
            Text("Search Failed", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
            Text(message, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3,
                overflow = TextOverflow.Ellipsis)
            Button(onClick = onRetry) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Retry")
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFiltersRow(
    filters: SearchFilter?,
    onFiltersChange: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val cur = filters ?: SearchFilter()
    LazyRow(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            var show by remember { mutableStateOf(false) }
            Box {
                FilterChip(cur.uploadDate != UploadDate.ANY, { show = true },
                    label = { Text(cur.uploadDate.label()) },
                    leadingIcon = { Icon(Icons.Outlined.CalendarToday, null, Modifier.size(16.dp)) })
                DropdownMenu(show, { show = false }) {
                    UploadDate.values().forEach { d ->
                        DropdownMenuItem({ Text(d.label()) },
                            { onFiltersChange(cur.copy(uploadDate = d)); show = false })
                    }
                }
            }
        }
        item {
            var show by remember { mutableStateOf(false) }
            Box {
                FilterChip(cur.duration != Duration.ANY, { show = true },
                    label = { Text(cur.duration.label()) },
                    leadingIcon = { Icon(Icons.Outlined.Timer, null, Modifier.size(16.dp)) })
                DropdownMenu(show, { show = false }) {
                    Duration.values().forEach { d ->
                        DropdownMenuItem({ Text(d.label()) },
                            { onFiltersChange(cur.copy(duration = d)); show = false })
                    }
                }
            }
        }
        item {
            var show by remember { mutableStateOf(false) }
            Box {
                FilterChip(cur.sortBy != SortBy.RELEVANCE, { show = true },
                    label = { Text(cur.sortBy.label()) },
                    leadingIcon = { Icon(Icons.Outlined.Sort, null, Modifier.size(16.dp)) })
                DropdownMenu(show, { show = false }) {
                    SortBy.values().forEach { s ->
                        DropdownMenuItem({ Text(s.label()) },
                            { onFiltersChange(cur.copy(sortBy = s)); show = false })
                    }
                }
            }
        }
        item {
            FilterChip(cur.features.contains(Feature.HD), {
                onFiltersChange(cur.copy(features = cur.features.toggle(Feature.HD)))
            }, label = { Text("HD") }, leadingIcon = { Icon(Icons.Outlined.HighQuality, null, Modifier.size(16.dp)) })
        }
        item {
            FilterChip(cur.features.contains(Feature.FOUR_K), {
                onFiltersChange(cur.copy(features = cur.features.toggle(Feature.FOUR_K)))
            }, label = { Text("4K") }, leadingIcon = { Icon(Icons.Outlined.HighQuality, null, Modifier.size(16.dp)) })
        }
        item {
            FilterChip(cur.features.contains(Feature.SUBTITLES), {
                onFiltersChange(cur.copy(features = cur.features.toggle(Feature.SUBTITLES)))
            }, label = { Text("Subtitles") }, leadingIcon = { Icon(Icons.Outlined.Subtitles, null, Modifier.size(16.dp)) })
        }
    }
}

@Composable
private fun Dot() {
    Text("\u00B7", style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
}

private fun <T> Set<T>.toggle(item: T): Set<T> = if (contains(item)) this - item else this + item

private fun UploadDate.label() = when (this) {
    UploadDate.ANY -> "Date"
    UploadDate.LAST_HOUR -> "Last hour"
    UploadDate.TODAY -> "Today"
    UploadDate.THIS_WEEK -> "This week"
    UploadDate.THIS_MONTH -> "This month"
    UploadDate.THIS_YEAR -> "This year"
}

private fun Duration.label() = when (this) {
    Duration.ANY -> "Duration"
    Duration.UNDER_4_MINUTES -> "< 4 min"
    Duration.FOUR_TO_20_MINUTES -> "4\u201320 min"
    Duration.OVER_20_MINUTES -> "> 20 min"
}

private fun SortBy.label() = when (this) {
    SortBy.RELEVANCE -> "Sort"
    SortBy.UPLOAD_DATE -> "Upload date"
    SortBy.VIEW_COUNT -> "View count"
    SortBy.RATING -> "Rating"
}

private fun formatSubs(count: Long): String = when {
    count >= 1_000_000 -> "${"%.1f".format(count / 1_000_000.0)}M subscribers"
    count >= 1_000 -> "${"%.1f".format(count / 1_000.0)}K subscribers"
    count > 0 -> "$count subscribers"
    else -> ""
}
