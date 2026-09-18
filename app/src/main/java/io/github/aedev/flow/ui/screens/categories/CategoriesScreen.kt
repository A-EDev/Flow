package io.github.aedev.flow.ui.screens.categories

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.List
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.HomeFeedColumns
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.innertube.pages.explore.ExploreSectionKind
import io.github.aedev.flow.ui.components.FEED_MAX_AUTO_COLUMNS
import io.github.aedev.flow.ui.components.categories.CategoryChartGrid
import io.github.aedev.flow.ui.components.categories.CategoryPagedGrid
import io.github.aedev.flow.ui.components.categories.CategoryShelfPage
import io.github.aedev.flow.ui.components.categories.CategoryShimmer
import io.github.aedev.flow.ui.components.categories.CategorySubTabMenu
import io.github.aedev.flow.ui.components.categories.CategoryTabBar
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.rememberFeedGridLayout
import io.github.aedev.flow.ui.components.shared.FlowErrorState
import io.github.aedev.flow.ui.screens.settings.SearchablePickerDialog
import io.github.aedev.flow.ui.screens.settings.regionPickerOptions

/**
 * Explore: one tab per YouTube destination, each rendering whichever of the three shapes its source
 * actually serves — a page of shelves, a paged grid, or a ranked chart.
 */
@Composable
fun CategoriesScreen(
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit = {},
    onShortClick: (String) -> Unit = {},
    onPlaylistClick: (String) -> Unit = {},
    viewModel: CategoriesViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val trendingRegion by viewModel.trendingRegion.collectAsStateWithLifecycle()
    val pagingItems = viewModel.gridItems.collectAsLazyPagingItems()
    var showRegionDialog by rememberSaveable { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember(context) { PlayerPreferences(context) }
    val columnPreference by preferences.homeFeedColumns.collectAsStateWithLifecycle(HomeFeedColumns.AUTO)

    val shelfState = rememberLazyGridState()
    val gridState = rememberLazyGridState()

    BackHandler(enabled = uiState.openShelfTitle != null) { viewModel.closeShelf() }

    Scaffold(
        topBar = {
            FlowTopBar(
                title = uiState.openShelfTitle ?: stringResource(R.string.categories_title),
                onBack = uiState.openShelfTitle?.let { { viewModel.closeShelf() } },
                actions = {
                    IconButton(onClick = { showRegionDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Language,
                            contentDescription =
                                stringResource(R.string.categories_region_picker_desc, trendingRegion),
                        )
                    }
                    IconButton(onClick = viewModel::toggleViewMode) {
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
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.openShelfTitle == null) {
                CategoryTabBar(
                    selected = uiState.selected,
                    onSelect = viewModel::select,
                    modifier = Modifier.padding(vertical = ChipRowVerticalPadding),
                )
                CategorySubTabMenu(
                    subTabs = uiState.subTabs,
                    selected = uiState.selectedSubTab,
                    onSelect = viewModel::selectSubTab,
                    modifier = Modifier.padding(bottom = ChipRowVerticalPadding),
                )
            }

            BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val feedLayout = rememberFeedGridLayout(maxWidth, columnPreference, FEED_MAX_AUTO_COLUMNS)
                when {
                    uiState.isLoading -> {
                        CategoryShimmer(feedLayout = feedLayout, isListView = uiState.isListView)
                    }

                    uiState.error != null -> {
                        FlowErrorState(error = uiState.error!!, onRetry = viewModel::refresh)
                    }

                    uiState.sectionKind == ExploreSectionKind.CHART -> {
                        CategoryChartGrid(
                            entries = uiState.chartEntries,
                            gridState = gridState,
                            feedLayout = feedLayout,
                            isListView = uiState.isListView,
                            onVideoClick = onVideoClick,
                            onChannelClick = onChannelClick,
                        )
                    }

                    uiState.sectionKind == ExploreSectionKind.GRID -> {
                        CategoryPagedGrid(
                            pagingItems = pagingItems,
                            gridState = gridState,
                            feedLayout = feedLayout,
                            isListView = uiState.isListView,
                            onVideoClick = onVideoClick,
                            onChannelClick = onChannelClick,
                            onPlaylistClick = onPlaylistClick,
                        )
                    }

                    else -> {
                        CategoryShelfPage(
                            shelves = uiState.shelves,
                            isLoading = uiState.isLoading,
                            listState = shelfState,
                            columnPreference = columnPreference,
                            onVideoClick = onVideoClick,
                            onShortClick = onShortClick,
                            onChannelClick = onChannelClick,
                            onPlaylistClick = onPlaylistClick,
                            onShelfOpen = viewModel::openShelf,
                        )
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
            listMaxHeight = RegionDialogMaxHeight,
        )
    }
}

private val ChipRowVerticalPadding = 4.dp
private val RegionDialogMaxHeight = 260.dp
