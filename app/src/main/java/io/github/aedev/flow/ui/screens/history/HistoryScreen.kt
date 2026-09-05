package io.github.aedev.flow.ui.screens.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.shorts.queue.ShortsQueueSource
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBarMenuItem
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBarOverflow
import io.github.aedev.flow.ui.components.library.HistoryFilterRow
import io.github.aedev.flow.ui.components.library.HistoryList
import io.github.aedev.flow.ui.components.shared.FlowEmptyState
import io.github.aedev.flow.ui.components.shared.FlowSearchField
import kotlinx.coroutines.launch

private val SearchFieldPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

@Composable
fun HistoryScreen(
    onVideoClick: (MusicTrack) -> Unit,
    onBackClick: () -> Unit,
    onShortsQueue: (ShortsQueueSource) -> Unit = {},
    onMusicClick: (MusicTrack, List<MusicTrack>) -> Unit = { track, _ -> onVideoClick(track) },
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayEntries by viewModel.displayEntries.collectAsStateWithLifecycle()
    val availableYears by viewModel.availableYears.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val contentFilter by viewModel.contentFilter.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val yearFilter by viewModel.yearFilter.collectAsStateWithLifecycle()

    var showClearDialog by remember { mutableStateOf(false) }
    var showClearShortsDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val removedLabel = stringResource(R.string.removed_from_history)
    val undoLabel = stringResource(R.string.action_undo)
    val onRemove: (VideoHistoryEntry) -> Unit = { entry ->
        viewModel.removeFromHistory(entry.videoId)
        scope.launch {
            val result =
                snackbarHostState.showSnackbar(
                    message = removedLabel,
                    actionLabel = undoLabel,
                )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreHistoryEntry(entry)
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.library_history_label),
                onBack = onBackClick,
                actions = {
                    FlowTopBarOverflow(
                        items =
                            buildList {
                                if (uiState.shortsEnabled) {
                                    add(
                                        FlowTopBarMenuItem(
                                            label = stringResource(R.string.history_delete_shorts),
                                            enabled = uiState.historyEntries.any { it.isShort },
                                            onClick = { showClearShortsDialog = true },
                                        ),
                                    )
                                }
                                add(
                                    FlowTopBarMenuItem(
                                        label = stringResource(R.string.clear_all),
                                        enabled = uiState.historyEntries.isNotEmpty(),
                                        onClick = { showClearDialog = true },
                                    ),
                                )
                            },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { paddingValues ->
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues),
        ) {
            FlowSearchField(
                query = searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                placeholder = stringResource(R.string.search_watch_history),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(SearchFieldPadding),
                onClear = { viewModel.setSearchQuery("") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                    )
                },
            )

            HistoryFilterRow(
                shortsEnabled = uiState.shortsEnabled,
                selectedFilter = contentFilter,
                onFilterSelected = viewModel::setContentFilter,
                selectedSort = sortOrder,
                onSortSelected = viewModel::setSortOrder,
                selectedYear = yearFilter,
                availableYears = availableYears,
                onYearSelected = viewModel::setYearFilter,
            )

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                displayEntries.isEmpty() -> {
                    FlowEmptyState(
                        modifier = Modifier.fillMaxSize(),
                        title =
                            if (uiState.historyEntries.isEmpty()) {
                                stringResource(R.string.empty_watch_history)
                            } else {
                                stringResource(R.string.history_no_results)
                            },
                        subtitle =
                            if (uiState.historyEntries.isEmpty()) {
                                stringResource(R.string.empty_watch_history_body)
                            } else {
                                stringResource(R.string.history_no_results_body)
                            },
                        icon = Icons.Outlined.History,
                    )
                }

                else -> {
                    HistoryList(
                        entries = displayEntries,
                        shortVideos = uiState.shortVideos,
                        selectedFilter = contentFilter,
                        onVideoClick = onVideoClick,
                        onShortClick = { row, tapped -> onShortsQueue(viewModel.shortsRowSource(row, tapped)) },
                        onMusicClick = onMusicClick,
                        onRemove = onRemove,
                    )
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_watch_history_alert_title)) },
            text = { Text(stringResource(R.string.clear_watch_history_alert_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearDialog = false
                    },
                ) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (showClearShortsDialog) {
        AlertDialog(
            onDismissRequest = { showClearShortsDialog = false },
            title = { Text(stringResource(R.string.history_delete_shorts_title)) },
            text = { Text(stringResource(R.string.history_delete_shorts_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearShortsHistory()
                        showClearShortsDialog = false
                    },
                ) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearShortsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}
