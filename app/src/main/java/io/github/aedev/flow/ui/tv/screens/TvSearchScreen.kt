package io.github.aedev.flow.ui.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import androidx.paging.LoadState
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.ContentType
import io.github.aedev.flow.data.local.SearchFilter
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.paging.SearchResultItem
import io.github.aedev.flow.ui.screens.search.SearchViewModel
import io.github.aedev.flow.ui.tv.components.TvFocusableCard
import io.github.aedev.flow.ui.tv.components.TvMessageState
import io.github.aedev.flow.ui.tv.components.TvLoadingState
import io.github.aedev.flow.ui.tv.components.TvVideoCard

@Composable
fun TvSearchScreen(
    viewModel: SearchViewModel,
    onVideoClick: (Video) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    val results = viewModel.searchResults.collectAsLazyPagingItems()
    val runSearch = {
        viewModel.search(
            query = query.trim(),
            filters = SearchFilter(contentType = ContentType.VIDEOS),
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(36.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            text = stringResource(R.string.search),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text(stringResource(R.string.tv_search_prompt)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { runSearch() }),
            )
            TvFocusableCard(onClick = runSearch) {
                Row(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(Icons.Outlined.Search, contentDescription = null)
                    Text(stringResource(R.string.tv_search_action))
                }
            }
        }

        if (results.loadState.refresh is LoadState.Loading && results.itemCount == 0) {
            TvLoadingState(modifier = Modifier.weight(1f))
        } else if (results.loadState.refresh is LoadState.Error && results.itemCount == 0) {
            TvMessageState(
                title = stringResource(R.string.tv_error_loading),
                modifier = Modifier.weight(1f),
            )
        } else if (results.loadState.refresh is LoadState.NotLoading && results.itemCount == 0) {
            TvMessageState(
                title = stringResource(R.string.tv_search_no_results),
                modifier = Modifier.weight(1f),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 280.dp),
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(
                    count = results.itemCount,
                    key = results.itemKey { item ->
                        when (item) {
                            is SearchResultItem.VideoResult -> "video:${item.video.id}"
                            is SearchResultItem.ChannelResult -> "channel:${item.channel.id}"
                            is SearchResultItem.PlaylistResult -> "playlist:${item.playlist.id}"
                            is SearchResultItem.ShortsShelfResult -> "shorts:${item.shorts.firstOrNull()?.id.orEmpty()}"
                        }
                    },
                ) { index ->
                    val video = (results[index] as? SearchResultItem.VideoResult)?.video
                    if (video != null) {
                        TvVideoCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                if (results.loadState.append is LoadState.Loading) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        TvLoadingState()
                    }
                }
            }
        }
    }
}
