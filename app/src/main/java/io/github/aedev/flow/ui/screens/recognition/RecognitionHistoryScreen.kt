package io.github.aedev.flow.ui.screens.recognition

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.entity.RecognitionHistoryEntity
import io.github.aedev.flow.ui.components.layout.topbar.FlowTopBar
import io.github.aedev.flow.ui.components.music.common.MusicThumbnail
import io.github.aedev.flow.ui.components.shared.FlowEmptyState

private val HistoryArtworkSize = 56.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionHistoryScreen(
    onBackClick: () -> Unit,
    onItemClick: (RecognitionHistoryEntity) -> Unit,
    viewModel: RecognitionViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.recognition_clear_history)) },
            text = { Text(stringResource(R.string.recognition_clear_history_body)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearHistory()
                    showClearDialog = false
                }) { Text(stringResource(R.string.clear_all)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    Scaffold(
        topBar = {
            FlowTopBar(
                title = stringResource(R.string.recognition_history),
                onBack = onBackClick,
                actions = {
                    if (history.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Rounded.DeleteSweep, stringResource(R.string.clear_all))
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp),
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSearch = {},
                expanded = false,
                onExpandedChange = {},
                placeholder = {
                    Text(
                        text = stringResource(R.string.recognition_search_history),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon =
                    if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { viewModel.onQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.clear),
                                )
                            }
                        }
                    } else {
                        null
                    },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (history.isEmpty()) {
                FlowEmptyState(
                    title = stringResource(R.string.recognition_history_empty),
                    icon = Icons.Rounded.History,
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(history, key = { it.id }) { item ->
                        HistoryRow(
                            item = item,
                            onClick = { onItemClick(item) },
                            onDelete = { viewModel.delete(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HistoryRow(
    item: RecognitionHistoryEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    ListItem(
        onClick = onClick,
        leadingContent = {
            MusicThumbnail(
                thumbnailUrl = item.coverArtHqUrl ?: item.coverArtUrl,
                size = HistoryArtworkSize,
                shape = MaterialTheme.shapes.small,
            )
        },
        supportingContent = {
            Column {
                Text(
                    text = item.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = DateUtils.getRelativeTimeSpanString(item.recognizedAt).toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, stringResource(R.string.delete))
            }
        },
    ) {
        Text(
            text = item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
