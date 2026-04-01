package io.github.aedev.flow.ui.screens.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.repository.YouTubeRepository.TrendingCategory
import io.github.aedev.flow.ui.components.ContentFilterChip
import io.github.aedev.flow.ui.components.ShimmerVideoCardFullWidth
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    onBackClick: () -> Unit,
    onVideoClick: (Video) -> Unit,
    viewModel: CategoriesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

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
                onBackClick = onBackClick,
                onToggleViewMode = { viewModel.toggleViewMode() }
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
            when {
                uiState.isLoading -> {
                    ShimmerContent(isListView = uiState.isListView)
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
                            onLoadMore = { viewModel.loadMore() }
                        )
                    } else {
                        GridContent(
                            videos = uiState.displayedVideos,
                            canLoadMore = uiState.canLoadMore,
                            isLoadingMore = uiState.isLoadingMore,
                            onVideoClick = onVideoClick,
                            onLoadMore = { viewModel.loadMore() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoriesTopBar(
    isListView: Boolean,
    onBackClick: () -> Unit,
    onToggleViewMode: () -> Unit
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
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .distinctUntilChanged()
            .filter { layoutInfo ->
                if (layoutInfo.totalItemsCount == 0) return@filter false
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisible >= layoutInfo.totalItemsCount - 4
            }
            .collect { if (canLoadMore && !isLoadingMore) onLoadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(videos, key = { it.id }) { video ->
            VideoCardFullWidth(
                video = video,
                useInternalPadding = false,
                modifier = Modifier.fillMaxWidth(),
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
private fun ListContent(
    videos: List<Video>,
    canLoadMore: Boolean,
    isLoadingMore: Boolean,
    onVideoClick: (Video) -> Unit,
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
private fun ShimmerContent(isListView: Boolean) {
    if (isListView) {
        LazyColumn(
            contentPadding = PaddingValues(vertical = 8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(8) {
                ShimmerVideoCardFullWidth(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(8) {
                ShimmerVideoCardFullWidth(modifier = Modifier.fillMaxWidth())
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
