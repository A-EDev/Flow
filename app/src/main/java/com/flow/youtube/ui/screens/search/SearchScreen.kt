package com.flow.youtube.ui.screens.search

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.flow.youtube.data.local.SearchHistoryRepository
import com.flow.youtube.data.local.SearchType
import com.flow.youtube.data.model.*
import com.flow.youtube.ui.components.*
import com.flow.youtube.ui.theme.extendedColors
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.flow.youtube.data.search.SearchSuggestionsService

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
    
    var searchQuery by remember { mutableStateOf("") }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    
    val uiState by viewModel.uiState.collectAsState()
    val searchHistory by searchHistoryRepo.getSearchHistoryFlow().collectAsState(initial = emptyList())
    val suggestionsEnabled by searchHistoryRepo.isSearchSuggestionsEnabledFlow().collectAsState(initial = true)
    
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    
    val trendingTopics = remember { 
        listOf("Technology", "Gaming", "Music", "Education", "Entertainment", "Science", "Sports") 
    }
    
    // Live search suggestions from YouTube/Google API
    var liveSuggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoadingSuggestions by remember { mutableStateOf(false) }
    
    // Debounced live suggestions fetching
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2 && isSearchFocused && suggestionsEnabled) {
            isLoadingSuggestions = true
            delay(300) // Debounce
            try {
                val suggestions = SearchSuggestionsService.getSuggestions(searchQuery)
                liveSuggestions = suggestions
            } catch (e: Exception) {
                liveSuggestions = emptyList()
            }
            isLoadingSuggestions = false
        } else if (searchQuery.length < 2) {
            liveSuggestions = emptyList()
        }
    }
    
    // Infinite scroll detection
    val isScrolledToEnd by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && 
            lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
        }
    }
    
    // Trigger load more when scrolled near the end
    LaunchedEffect(isScrolledToEnd) {
        if (isScrolledToEnd && !uiState.isLoadingMore && uiState.hasMorePages && uiState.videos.isNotEmpty()) {
            viewModel.loadMoreResults()
        }
    }
    
    // Save search query when performed
    LaunchedEffect(uiState.query) {
        if (uiState.query.isNotBlank()) {
            searchHistoryRepo.saveSearchQuery(uiState.query)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Enhanced Search Bar with Filters
        EnhancedSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = {
                if (searchQuery.isNotBlank()) {
                    isSearchFocused = false
                    liveSuggestions = emptyList()
                    viewModel.search(searchQuery, uiState.filters)
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
        
        // Filter Chips Row
        AnimatedVisibility(
            visible = showFilters,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            SearchFiltersRow(
                filters = uiState.filters,
                onFiltersChange = { viewModel.updateFilters(it) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Show suggestions when focused and typing
        AnimatedVisibility(
            visible = isSearchFocused && searchQuery.isNotEmpty() && liveSuggestions.isNotEmpty(),
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                ) {
                    // Show loading indicator
                    if (isLoadingSuggestions && liveSuggestions.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                    
                    items(liveSuggestions) { suggestion ->
                        LiveSuggestionItem(
                            suggestion = suggestion,
                            searchQuery = searchQuery,
                            onClick = {
                                searchQuery = suggestion
                                isSearchFocused = false
                                liveSuggestions = emptyList()
                                viewModel.search(suggestion, uiState.filters)
                            },
                            onFillClick = {
                                searchQuery = suggestion
                            }
                        )
                    }
                }
            }
        }

        if (searchQuery.isEmpty() && uiState.videos.isEmpty()) {
            // Default State
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 80.dp)
            ) {
                // Search History
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
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        searchHistoryRepo.clearSearchHistory()
                                    }
                                }
                            ) {
                                Text("Clear all")
                            }
                        }
                    }
                    items(searchHistory.take(10)) { historyItem ->
                        SearchHistoryItemCard(
                            historyItem = historyItem,
                            onClick = {
                                searchQuery = historyItem.query
                                viewModel.search(historyItem.query, uiState.filters)
                            },
                            onDelete = {
                                scope.launch {
                                    searchHistoryRepo.deleteSearchItem(historyItem.id)
                                }
                            }
                        )
                    }
                }

                // Trending Topics
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "Trending",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                items(trendingTopics) { topic ->
                    TrendingTopicChip(
                        topic = topic,
                        onClick = {
                            searchQuery = topic
                            viewModel.search(topic, uiState.filters)
                        }
                    )
                }
            }
        } else {
            // Search Results
            when {
                uiState.isLoading && uiState.videos.isEmpty() -> {
                    // Initial loading
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(10) {
                            ShimmerVideoCardFullWidth()
                        }
                    }
                }
                
                uiState.error != null && uiState.videos.isEmpty() -> {
                    // Error state
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = uiState.error ?: "An error occurred",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(onClick = { viewModel.search(searchQuery) }) {
                                Text("Retry")
                            }
                        }
                    }
                }
                
                else -> {
                    // Results with channels and videos
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        // Channels Section
                        if (uiState.channels.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Channels",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                            
                            items(
                                items = uiState.channels,
                                key = { channel -> "channel_${channel.id}" }
                            ) { channel ->
                                ChannelSearchResultCard(
                                    channel = channel,
                                    onClick = { onChannelClick(channel) }
                                )
                            }
                            
                            // Divider between channels and videos
                            if (uiState.videos.isNotEmpty()) {
                                item {
                                    Divider(
                                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant
                                    )
                                    Text(
                                        text = "Videos",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                        
                        // Videos Section
                        items(
                            items = uiState.videos,
                            key = { video -> "video_${video.id}" }
                        ) { video ->
                            VideoCardFullWidth(
                                video = video,
                                onClick = { onVideoClick(video) }
                            )
                        }
                        
                        // Loading more indicator
                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                        }
                        
                        // End of results message
                        if (!uiState.hasMorePages && uiState.videos.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "End of results",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnhancedSearchBar(
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
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(28.dp))
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChange(it.isFocused) },
            placeholder = {
                Text(
                    text = "Search videos, channels...",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = "Search",
                    tint = if (isSearchFocused) 
                        MaterialTheme.colorScheme.primary 
                    else 
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                Row {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = onClear) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Search
            ),
            keyboardActions = KeyboardActions(
                onSearch = {
                    if (query.isNotBlank()) onSearch()
                }
            )
        )
        
        // Filter button
        IconButton(
            onClick = onFilterClick,
            modifier = Modifier
                .size(48.dp)
                .background(
                    color = if (hasActiveFilters) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Filled.FilterList,
                contentDescription = "Filters",
                tint = if (hasActiveFilters) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterChips(
    selectedFilter: SearchFilter,
    onFilterSelected: (SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(SearchFilter.values()) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.name.lowercase().capitalize()) }
            )
        }
    }
}

@Composable
private fun SearchHistoryItem(
    query: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = MaterialTheme.extendedColors.textSecondary
        )
        Text(
            text = query,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun TrendingTopicItem(
    topic: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = topic,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFiltersRow(
    filters: com.flow.youtube.data.local.SearchFilter?,
    onFiltersChange: (com.flow.youtube.data.local.SearchFilter) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentFilters = filters ?: com.flow.youtube.data.local.SearchFilter()
    
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Upload Date Filter
        item {
            var showDateMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = currentFilters.uploadDate != com.flow.youtube.data.local.UploadDate.ANY,
                    onClick = { showDateMenu = true },
                    label = {
                        Text(
                            when (currentFilters.uploadDate) {
                                com.flow.youtube.data.local.UploadDate.ANY -> "Upload date"
                                com.flow.youtube.data.local.UploadDate.LAST_HOUR -> "Last hour"
                                com.flow.youtube.data.local.UploadDate.TODAY -> "Today"
                                com.flow.youtube.data.local.UploadDate.THIS_WEEK -> "This week"
                                com.flow.youtube.data.local.UploadDate.THIS_MONTH -> "This month"
                                com.flow.youtube.data.local.UploadDate.THIS_YEAR -> "This year"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.CalendarToday, null, modifier = Modifier.size(16.dp))
                    }
                )
                DropdownMenu(
                    expanded = showDateMenu,
                    onDismissRequest = { showDateMenu = false }
                ) {
                    com.flow.youtube.data.local.UploadDate.values().forEach { date ->
                        DropdownMenuItem(
                            text = { Text(date.name.replace('_', ' ').lowercase().capitalize()) },
                            onClick = {
                                onFiltersChange(currentFilters.copy(uploadDate = date))
                                showDateMenu = false
                            }
                        )
                    }
                }
            }
        }
        
        // Duration Filter
        item {
            var showDurationMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = currentFilters.duration != com.flow.youtube.data.local.Duration.ANY,
                    onClick = { showDurationMenu = true },
                    label = {
                        Text(
                            when (currentFilters.duration) {
                                com.flow.youtube.data.local.Duration.ANY -> "Duration"
                                com.flow.youtube.data.local.Duration.UNDER_4_MINUTES -> "Under 4 min"
                                com.flow.youtube.data.local.Duration.FOUR_TO_20_MINUTES -> "4-20 min"
                                com.flow.youtube.data.local.Duration.OVER_20_MINUTES -> "Over 20 min"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Timer, null, modifier = Modifier.size(16.dp))
                    }
                )
                DropdownMenu(
                    expanded = showDurationMenu,
                    onDismissRequest = { showDurationMenu = false }
                ) {
                    com.flow.youtube.data.local.Duration.values().forEach { duration ->
                        DropdownMenuItem(
                            text = { Text(duration.name.replace('_', ' ').lowercase().capitalize()) },
                            onClick = {
                                onFiltersChange(currentFilters.copy(duration = duration))
                                showDurationMenu = false
                            }
                        )
                    }
                }
            }
        }
        
        // Sort By Filter
        item {
            var showSortMenu by remember { mutableStateOf(false) }
            Box {
                FilterChip(
                    selected = currentFilters.sortBy != com.flow.youtube.data.local.SortBy.RELEVANCE,
                    onClick = { showSortMenu = true },
                    label = {
                        Text(
                            when (currentFilters.sortBy) {
                                com.flow.youtube.data.local.SortBy.RELEVANCE -> "Sort by"
                                com.flow.youtube.data.local.SortBy.UPLOAD_DATE -> "Upload date"
                                com.flow.youtube.data.local.SortBy.VIEW_COUNT -> "View count"
                                com.flow.youtube.data.local.SortBy.RATING -> "Rating"
                            }
                        )
                    },
                    leadingIcon = {
                        Icon(Icons.Outlined.Sort, null, modifier = Modifier.size(16.dp))
                    }
                )
                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    com.flow.youtube.data.local.SortBy.values().forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.name.replace('_', ' ').lowercase().capitalize()) },
                            onClick = {
                                onFiltersChange(currentFilters.copy(sortBy = sort))
                                showSortMenu = false
                            }
                        )
                    }
                }
            }
        }
        
        // HD Filter
        item {
            FilterChip(
                selected = currentFilters.features.contains(com.flow.youtube.data.local.Feature.HD),
                onClick = {
                    val newFeatures = if (currentFilters.features.contains(com.flow.youtube.data.local.Feature.HD)) {
                        currentFilters.features - com.flow.youtube.data.local.Feature.HD
                    } else {
                        currentFilters.features + com.flow.youtube.data.local.Feature.HD
                    }
                    onFiltersChange(currentFilters.copy(features = newFeatures))
                },
                label = { Text("HD") },
                leadingIcon = {
                    Icon(Icons.Outlined.HighQuality, null, modifier = Modifier.size(16.dp))
                }
            )
        }
        
        // 4K Filter
        item {
            FilterChip(
                selected = currentFilters.features.contains(com.flow.youtube.data.local.Feature.FOUR_K),
                onClick = {
                    val newFeatures = if (currentFilters.features.contains(com.flow.youtube.data.local.Feature.FOUR_K)) {
                        currentFilters.features - com.flow.youtube.data.local.Feature.FOUR_K
                    } else {
                        currentFilters.features + com.flow.youtube.data.local.Feature.FOUR_K
                    }
                    onFiltersChange(currentFilters.copy(features = newFeatures))
                },
                label = { Text("4K") },
                leadingIcon = {
                    Icon(Icons.Outlined.HighQuality, null, modifier = Modifier.size(16.dp))
                }
            )
        }
        
        // Subtitles Filter
        item {
            FilterChip(
                selected = currentFilters.features.contains(com.flow.youtube.data.local.Feature.SUBTITLES),
                onClick = {
                    val newFeatures = if (currentFilters.features.contains(com.flow.youtube.data.local.Feature.SUBTITLES)) {
                        currentFilters.features - com.flow.youtube.data.local.Feature.SUBTITLES
                    } else {
                        currentFilters.features + com.flow.youtube.data.local.Feature.SUBTITLES
                    }
                    onFiltersChange(currentFilters.copy(features = newFeatures))
                },
                label = { Text("Subtitles") },
                leadingIcon = {
                    Icon(Icons.Outlined.Subtitles, null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

@Composable
private fun SearchHistoryItemCard(
    historyItem: com.flow.youtube.data.local.SearchHistoryItem,
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
            imageVector = when (historyItem.type) {
                SearchType.TEXT -> Icons.Filled.History
                SearchType.VOICE -> Icons.Filled.Mic
                SearchType.SUGGESTION -> Icons.Filled.TrendingUp
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        
        Text(
            text = historyItem.query,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        IconButton(
            onClick = onDelete,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

/**
 * Live suggestion item with highlighted matching text
 */
@Composable
private fun LiveSuggestionItem(
    suggestion: String,
    searchQuery: String,
    onClick: () -> Unit,
    onFillClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        
        // Highlighted suggestion text
        Text(
            text = buildAnnotatedString {
                val lowerSuggestion = suggestion.lowercase()
                val lowerQuery = searchQuery.lowercase()
                val startIndex = lowerSuggestion.indexOf(lowerQuery)
                
                if (startIndex >= 0) {
                    // Text before match
                    append(suggestion.substring(0, startIndex))
                    // Matched text (bold)
                    withStyle(
                        style = androidx.compose.ui.text.SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        append(suggestion.substring(startIndex, startIndex + searchQuery.length))
                    }
                    // Text after match
                    append(suggestion.substring(startIndex + searchQuery.length))
                } else {
                    append(suggestion)
                }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // Fill search box button
        IconButton(
            onClick = onFillClick,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.NorthWest,
                contentDescription = "Fill search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SuggestionItem(
    suggestion: com.flow.youtube.data.local.SearchSuggestion,
    onClick: () -> Unit,
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
            imageVector = when (suggestion.type) {
                com.flow.youtube.data.local.SuggestionType.VIDEO -> Icons.Outlined.OndemandVideo
                com.flow.youtube.data.local.SuggestionType.CHANNEL -> Icons.Outlined.AccountCircle
                com.flow.youtube.data.local.SuggestionType.PLAYLIST -> Icons.Outlined.PlaylistPlay
                com.flow.youtube.data.local.SuggestionType.TRENDING -> Icons.Outlined.TrendingUp
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        
        Text(
            text = suggestion.text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun TrendingTopicChip(
    topic: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = topic,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun PlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${playlist.videoCount} videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.extendedColors.textSecondary
            )
        }
    }
}

@Composable
private fun ChannelSearchResultCard(
    channel: Channel,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Avatar
            AsyncImage(
                model = channel.thumbnailUrl,
                contentDescription = channel.name,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            
            // Channel Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                if (channel.subscriberCount > 0) {
                    Text(
                        text = formatSubscriberCount(channel.subscriberCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                if (channel.description.isNotBlank()) {
                    Text(
                        text = channel.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            
            // Channel indicator
            Icon(
                imageVector = Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private fun formatSubscriberCount(count: Long): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM subscribers", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK subscribers", count / 1_000.0)
        count > 0 -> "$count subscribers"
        else -> ""
    }
}

