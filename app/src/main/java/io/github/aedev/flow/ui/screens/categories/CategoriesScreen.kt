package io.github.aedev.flow.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository.TrendingCategory
import io.github.aedev.flow.ui.components.ContentFilterChip
import io.github.aedev.flow.ui.components.ShimmerGridVideoCard
import io.github.aedev.flow.ui.components.ShimmerVideoCardFullWidth
import io.github.aedev.flow.ui.components.ShimmerVideoCardHorizontal
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.components.VideoCardHorizontal
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private data class CategoryTab(
    val category: TrendingCategory,
    val labelRes: Int,
    val iconRes: ImageVector? = null,
    val iconResId: Int? = null
)

private data class CategoriesLayoutConfig(
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp
)

@Composable
private fun rememberCategoriesLayoutConfig(maxWidth: Dp): CategoriesLayoutConfig {
    return remember(maxWidth) {
        when {
            maxWidth < 480.dp  -> CategoriesLayoutConfig(columns = 1, contentPadding = 0.dp,  cardSpacing = 12.dp)
            maxWidth < 700.dp  -> CategoriesLayoutConfig(columns = 1, contentPadding = 12.dp, cardSpacing = 14.dp)
            maxWidth < 900.dp  -> CategoriesLayoutConfig(columns = 2, contentPadding = 16.dp, cardSpacing = 12.dp)
            maxWidth < 1200.dp -> CategoriesLayoutConfig(columns = 3, contentPadding = 20.dp, cardSpacing = 14.dp)
            else               -> CategoriesLayoutConfig(columns = 4, contentPadding = 24.dp, cardSpacing = 16.dp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBackClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit = {},
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trendingRegion by viewModel.trendingRegion.collectAsStateWithLifecycle()
    val showRegionPicker by viewModel.showRegionPickerInExplore.collectAsStateWithLifecycle()
    var showRegionDialog by remember { mutableStateOf(false) }

    val tabs = remember {
        listOf(
            CategoryTab(TrendingCategory.ALL,     R.string.category_all),
            CategoryTab(TrendingCategory.GAMING,   R.string.category_gaming),
            CategoryTab(TrendingCategory.MUSIC,    R.string.category_music),
            CategoryTab(TrendingCategory.MOVIES,   R.string.category_movies),
            CategoryTab(TrendingCategory.LIVE,     R.string.category_live),
        )
    }

    Scaffold(
        topBar = {
            CategoriesTopBar(
                isListView = uiState.isListView,
                currentRegion = trendingRegion,
                showRegionPicker = showRegionPicker,
                onBackClick = onBackClick,
                onToggleViewMode = { viewModel.toggleViewMode() },
                onRegionPickerClick = { showRegionDialog = true }
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Category filter chips row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { tab ->
                    val selected = uiState.selectedCategory == tab.category
                    ContentFilterChip(
                        title = stringResource(tab.labelRes),
                        isSelected = selected,
                        onClick = { viewModel.selectCategory(tab.category) }
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )

            // Content area
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val layoutConfig = rememberCategoriesLayoutConfig(maxWidth)
                when {
                    uiState.isLoading -> {
                        ShimmerContent(isListView = uiState.isListView, layoutConfig = layoutConfig)
                    }
                    uiState.error != null && uiState.videos.isEmpty() -> {
                        ErrorContent(
                            message = uiState.error!!,
                            onRetry = { viewModel.refresh() }
                        )
                    }
                    else -> {
                        if (uiState.isListView) {
                            ListContent(
                                videos = uiState.displayedVideos,
                                canLoadMore = uiState.canLoadMore,
                                isLoadingMore = uiState.isLoadingMore,
                                onVideoClick = onVideoClick,
                                onChannelClick = onChannelClick,
                                onLoadMore = { viewModel.loadMore() }
                            )
                        } else {
                            GridContent(
                                videos = uiState.displayedVideos,
                                canLoadMore = uiState.canLoadMore,
                                isLoadingMore = uiState.isLoadingMore,
                                onVideoClick = onVideoClick,
                                onChannelClick = onChannelClick,
                                onLoadMore = { viewModel.loadMore() },
                                layoutConfig = layoutConfig
                            )
                        }
                    }
                }
            }
        }
    }

    // Region picker dialog
    if (showRegionDialog) {
        var regionSearchQuery by remember { mutableStateOf("") }
        val allRegions = remember { REGION_NAMES.toList() }
        val filteredRegions = remember(regionSearchQuery) {
            if (regionSearchQuery.isBlank()) allRegions
            else allRegions.filter { (code, name) ->
                name.contains(regionSearchQuery, ignoreCase = true) ||
                code.contains(regionSearchQuery, ignoreCase = true)
            }
        }
        AlertDialog(
            onDismissRequest = { showRegionDialog = false },
            title = { Text(stringResource(R.string.settings_region_dialog_title)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = regionSearchQuery,
                        onValueChange = { regionSearchQuery = it },
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 260.dp)) {
                        items(filteredRegions.size) { index ->
                            val (code, name) = filteredRegions[index]
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setRegion(code)
                                        showRegionDialog = false
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = trendingRegion == code, onClick = null)
                                Spacer(Modifier.width(8.dp))
                                Text(name)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRegionDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
private fun CategoriesTopBar(
    isListView: Boolean,
    currentRegion: String,
    showRegionPicker: Boolean,
    onBackClick: () -> Unit,
    onToggleViewMode: () -> Unit,
    onRegionPickerClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = stringResource(R.string.btn_back)
                )
            }
            Text(
                text = stringResource(R.string.categories_title),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            if (showRegionPicker) {
                IconButton(onClick = onRegionPickerClick) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = stringResource(R.string.categories_region_picker_desc, currentRegion),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onToggleViewMode) {
                Icon(
                    imageVector = if (isListView) Icons.Outlined.GridView else Icons.Outlined.List,
                    contentDescription = if (isListView)
                        stringResource(R.string.categories_switch_to_grid)
                    else
                        stringResource(R.string.categories_switch_to_list),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GridContent(
    videos: List<Video>,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    layoutConfig: CategoriesLayoutConfig
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo }
            .distinctUntilChanged()
            .filter { layoutInfo ->
                if (layoutInfo.totalItemsCount == 0) return@filter false
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= layoutInfo.totalItemsCount - 4
            }
            .collect { if (canLoadMore && !isLoadingMore) onLoadMore() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(layoutConfig.columns),
        state = gridState,
        contentPadding = PaddingValues(
            horizontal = layoutConfig.contentPadding,
            vertical = 12.dp
        ),
        horizontalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
        modifier = Modifier.fillMaxSize()
    ) {
        items(videos, key = { it.id }) { video ->
            VideoCardFullWidth(
                video = video,
                useInternalPadding = false,
                modifier = Modifier.fillMaxWidth(),
                onChannelClick = onChannelClick,
                onClick = { onVideoClick(video) }
            )
        }

        if (canLoadMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ListContent(
    videos: List<Video>,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .distinctUntilChanged()
            .filter { layoutInfo ->
                if (layoutInfo.totalItemsCount == 0) return@filter false
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= layoutInfo.totalItemsCount - 3
            }
            .collect { if (canLoadMore && !isLoadingMore) onLoadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(videos, key = { it.id }) { video ->
            VideoCardHorizontal(
                video = video,
                modifier = Modifier.fillMaxWidth(),
                onChannelClick = onChannelClick,
                onClick = { onVideoClick(video) }
            )
        }

        if (canLoadMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerContent(isListView: Boolean, layoutConfig: CategoriesLayoutConfig) {
    if (isListView) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            items(8) {
                ShimmerVideoCardHorizontal()
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(layoutConfig.columns),
            contentPadding = PaddingValues(
                horizontal = layoutConfig.contentPadding,
                vertical = 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false
        ) {
            items(12) {
                if (layoutConfig.columns == 1) {
                    ShimmerVideoCardFullWidth(modifier = Modifier.fillMaxWidth())
                } else {
                    ShimmerGridVideoCard()
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}

private val REGION_NAMES = mapOf(
    "DZ" to "Algeria", "AS" to "American Samoa", "AI" to "Anguilla", "AR" to "Argentina",
    "AW" to "Aruba", "AU" to "Australia", "AT" to "Austria", "AZ" to "Azerbaijan",
    "BH" to "Bahrain", "BD" to "Bangladesh", "BY" to "Belarus", "BE" to "Belgium",
    "BM" to "Bermuda", "BO" to "Bolivia", "BA" to "Bosnia and Herzegovina", "BR" to "Brazil",
    "IO" to "British Indian Ocean Territory", "VG" to "British Virgin Islands", "BG" to "Bulgaria", "KH" to "Cambodia",
    "CA" to "Canada", "KY" to "Cayman Islands", "CL" to "Chile", "CO" to "Colombia",
    "CR" to "Costa Rica", "HR" to "Croatia", "CY" to "Cyprus", "CZ" to "Czech Republic",
    "DK" to "Denmark", "DO" to "Dominican Republic", "EC" to "Ecuador", "EG" to "Egypt",
    "SV" to "El Salvador", "EE" to "Estonia", "FK" to "Falkland Islands", "FO" to "Faroe Islands",
    "FI" to "Finland", "FR" to "France", "GF" to "French Guiana", "PF" to "French Polynesia",
    "GE" to "Georgia", "DE" to "Germany", "GH" to "Ghana", "GI" to "Gibraltar",
    "GR" to "Greece", "GL" to "Greenland", "GP" to "Guadeloupe", "GU" to "Guam",
    "GT" to "Guatemala", "HN" to "Honduras", "HK" to "Hong Kong", "HU" to "Hungary",
    "IS" to "Iceland", "IN" to "India", "ID" to "Indonesia", "IQ" to "Iraq",
    "IE" to "Ireland", "IL" to "Israel", "IT" to "Italy", "JM" to "Jamaica",
    "JP" to "Japan", "JO" to "Jordan", "KZ" to "Kazakhstan", "KE" to "Kenya",
    "KW" to "Kuwait", "LA" to "Laos", "LV" to "Latvia", "LB" to "Lebanon",
    "LY" to "Libya", "LI" to "Liechtenstein", "LT" to "Lithuania", "LU" to "Luxembourg",
    "MY" to "Malaysia", "MT" to "Malta", "MQ" to "Martinique", "YT" to "Mayotte",
    "MX" to "Mexico", "MD" to "Moldova", "ME" to "Montenegro", "MS" to "Montserrat",
    "MA" to "Morocco", "NP" to "Nepal", "NL" to "Netherlands", "NC" to "New Caledonia",
    "NZ" to "New Zealand", "NI" to "Nicaragua", "NG" to "Nigeria", "NF" to "Norfolk Island",
    "MP" to "Northern Mariana Islands", "NO" to "Norway", "OM" to "Oman", "PK" to "Pakistan",
    "PA" to "Panama", "PG" to "Papua New Guinea", "PY" to "Paraguay", "PE" to "Peru",
    "PH" to "Philippines", "PL" to "Poland", "PT" to "Portugal", "PR" to "Puerto Rico",
    "QA" to "Qatar", "RE" to "Reunion", "RO" to "Romania", "RU" to "Russia",
    "SH" to "Saint Helena", "PM" to "Saint Pierre and Miquelon", "SA" to "Saudi Arabia", "SN" to "Senegal",
    "RS" to "Serbia", "SG" to "Singapore", "SK" to "Slovakia", "SI" to "Slovenia",
    "ZA" to "South Africa", "KR" to "South Korea", "ES" to "Spain", "LK" to "Sri Lanka",
    "SJ" to "Svalbard and Jan Mayen", "SE" to "Sweden", "CH" to "Switzerland", "TW" to "Taiwan",
    "TZ" to "Tanzania", "TH" to "Thailand", "TN" to "Tunisia", "TR" to "Turkey",
    "TC" to "Turks and Caicos Islands", "UG" to "Uganda", "UA" to "Ukraine", "AE" to "United Arab Emirates",
    "GB" to "United Kingdom", "US" to "United States", "VI" to "U.S. Virgin Islands", "UY" to "Uruguay",
    "VE" to "Venezuela", "VN" to "Vietnam"
).toList().sortedBy { it.second }.toMap()
