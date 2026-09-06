package io.github.aedev.flow.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.List
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
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.components.VideoCardHorizontal
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.shared.ShimmerGridVideoCard
import io.github.aedev.flow.ui.components.shared.ShimmerVideoCardFullWidth
import io.github.aedev.flow.ui.components.shared.ShimmerVideoCardHorizontal
import io.github.aedev.flow.ui.screens.settings.SearchablePickerDialog
import io.github.aedev.flow.ui.screens.settings.regionPickerOptions
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private data class CategoryTab(
    val category: TrendingCategory,
    val labelRes: Int,
    val iconRes: ImageVector? = null,
    val iconResId: Int? = null,
)

private data class CategoriesLayoutConfig(
    val columns: Int,
    val contentPadding: Dp,
    val cardSpacing: Dp,
)

@Composable
private fun rememberCategoriesLayoutConfig(maxWidth: Dp): CategoriesLayoutConfig =
    remember(maxWidth) {
        when {
            maxWidth < 480.dp -> CategoriesLayoutConfig(columns = 1, contentPadding = 0.dp, cardSpacing = 12.dp)
            maxWidth < 700.dp -> CategoriesLayoutConfig(columns = 1, contentPadding = 12.dp, cardSpacing = 14.dp)
            maxWidth < 900.dp -> CategoriesLayoutConfig(columns = 2, contentPadding = 16.dp, cardSpacing = 12.dp)
            maxWidth < 1200.dp -> CategoriesLayoutConfig(columns = 3, contentPadding = 20.dp, cardSpacing = 14.dp)
            else -> CategoriesLayoutConfig(columns = 4, contentPadding = 24.dp, cardSpacing = 16.dp)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit = {},
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trendingRegion by viewModel.trendingRegion.collectAsStateWithLifecycle()
    val showRegionPicker by viewModel.showRegionPickerInExplore.collectAsStateWithLifecycle()
    var showRegionDialog by remember { mutableStateOf(false) }

    val tabs =
        remember {
            listOf(
                CategoryTab(TrendingCategory.ALL, R.string.category_all),
                CategoryTab(TrendingCategory.GAMING, R.string.category_gaming),
                CategoryTab(TrendingCategory.MUSIC, R.string.category_music),
                CategoryTab(TrendingCategory.MOVIES, R.string.category_movies),
                CategoryTab(TrendingCategory.LIVE, R.string.category_live),
            )
        }

    Scaffold(
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.categories_title),
                actions = {
                    if (showRegionPicker) {
                        IconButton(onClick = { showRegionDialog = true }) {
                            Icon(
                                imageVector = Icons.Outlined.Language,
                                contentDescription = stringResource(R.string.categories_region_picker_desc, trendingRegion),
                            )
                        }
                    }
                    IconButton(onClick = { viewModel.toggleViewMode() }) {
                        Icon(
                            imageVector = if (uiState.isListView) Icons.Outlined.GridView else Icons.Outlined.List,
                            contentDescription =
                                if (uiState.isListView) {
                                    stringResource(R.string.categories_switch_to_grid)
                                } else {
                                    stringResource(R.string.categories_switch_to_list)
                                },
                        )
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background),
        ) {
            // Category filter chips row
            LazyRow(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(tabs) { tab ->
                    val selected = uiState.selectedCategory == tab.category
                    ContentFilterChip(
                        title = stringResource(tab.labelRes),
                        isSelected = selected,
                        onClick = { viewModel.selectCategory(tab.category) },
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp,
            )

            // Content area
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                val layoutConfig = rememberCategoriesLayoutConfig(maxWidth)
                when {
                    uiState.isLoading -> {
                        ShimmerContent(isListView = uiState.isListView, layoutConfig = layoutConfig)
                    }

                    uiState.error != null && uiState.videos.isEmpty() -> {
                        ErrorContent(
                            message = uiState.error!!,
                            onRetry = { viewModel.refresh() },
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
                                onLoadMore = { viewModel.loadMore() },
                            )
                        } else {
                            GridContent(
                                videos = uiState.displayedVideos,
                                canLoadMore = uiState.canLoadMore,
                                isLoadingMore = uiState.isLoadingMore,
                                onVideoClick = onVideoClick,
                                onChannelClick = onChannelClick,
                                onLoadMore = { viewModel.loadMore() },
                                layoutConfig = layoutConfig,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRegionDialog) {
        val regionOptions = remember { regionPickerOptions() }
        SearchablePickerDialog(
            title = stringResource(R.string.settings_region_dialog_title),
            options = regionOptions,
            selectedKey = trendingRegion,
            onSelect = { code ->
                viewModel.setRegion(code)
                showRegionDialog = false
            },
            onDismiss = { showRegionDialog = false },
            listMaxHeight = 260.dp,
        )
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
    layoutConfig: CategoriesLayoutConfig,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState) {
        snapshotFlow { gridState.layoutInfo }
            .distinctUntilChanged()
            .filter { layoutInfo ->
                if (layoutInfo.totalItemsCount == 0) return@filter false
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= layoutInfo.totalItemsCount - 4
            }.collect { if (canLoadMore && !isLoadingMore) onLoadMore() }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(layoutConfig.columns),
        state = gridState,
        contentPadding =
            PaddingValues(
                horizontal = layoutConfig.contentPadding,
                vertical = 12.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
        verticalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(videos, key = { it.id }) { video ->
            VideoCardFullWidth(
                video = video,
                useInternalPadding = false,
                modifier = Modifier.fillMaxWidth(),
                onChannelClick = onChannelClick,
                onClick = { onVideoClick(video) },
            )
        }

        if (canLoadMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
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
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .distinctUntilChanged()
            .filter { layoutInfo ->
                if (layoutInfo.totalItemsCount == 0) return@filter false
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= layoutInfo.totalItemsCount - 3
            }.collect { if (canLoadMore && !isLoadingMore) onLoadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(vertical = 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(videos, key = { it.id }) { video ->
            VideoCardHorizontal(
                video = video,
                modifier = Modifier.fillMaxWidth(),
                onChannelClick = onChannelClick,
                onClick = { onVideoClick(video) },
            )
        }

        if (canLoadMore) {
            item {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ShimmerContent(
    isListView: Boolean,
    layoutConfig: CategoriesLayoutConfig,
) {
    if (isListView) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
        ) {
            items(8) {
                ShimmerVideoCardHorizontal()
            }
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(layoutConfig.columns),
            contentPadding =
                PaddingValues(
                    horizontal = layoutConfig.contentPadding,
                    vertical = 12.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
            verticalArrangement = Arrangement.spacedBy(layoutConfig.cardSpacing),
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = false,
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
private fun ErrorContent(
    message: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(stringResource(R.string.retry))
        }
    }
}
