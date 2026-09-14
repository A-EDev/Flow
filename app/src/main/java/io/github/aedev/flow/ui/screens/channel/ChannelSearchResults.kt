package io.github.aedev.flow.ui.screens.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.CompactVideoCard
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.components.shared.FlowEmptyState

@Composable
internal fun ChannelSearchResults(
    uiState: ChannelUiState,
    listState: LazyListState,
    contentPadding: PaddingValues,
    topInset: Dp,
    isGridView: Boolean,
    onVideoClick: (Video) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        uiState.isSearching -> {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = topInset),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
        }

        uiState.searchErrorLog != null -> {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = topInset),
                contentAlignment = Alignment.Center,
            ) {
                ChannelRequestErrorState(
                    message = stringResource(R.string.channel_search_failed),
                    errorLog = uiState.searchErrorLog,
                    onRetry = onRetry,
                )
            }
        }

        uiState.searchResults.isEmpty() -> {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(top = topInset),
                contentAlignment = Alignment.Center,
            ) {
                FlowEmptyState(title = stringResource(R.string.channel_search_no_results, uiState.searchQuery))
            }
        }

        else -> {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
            ) {
                items(
                    count = uiState.searchResults.size,
                    key = { index -> "search_${uiState.searchResults[index].id}" },
                ) { index ->
                    val video = uiState.searchResults[index]
                    if (isGridView) {
                        VideoCardFullWidth(
                            video = video,
                            showChannelAvatar = false,
                            showChannelName = false,
                            onClick = { onVideoClick(video) },
                        )
                    } else {
                        CompactVideoCard(
                            video = video,
                            showChannelName = false,
                            onClick = { onVideoClick(video) },
                        )
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
