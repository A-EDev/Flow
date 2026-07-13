package io.github.aedev.flow.ui.screens.channel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.innertube.pages.CommunityPost
import io.github.aedev.flow.ui.components.CommunityPostCard

@Composable
internal fun ChannelCommunityPosts(
    posts: List<CommunityPost>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    hasError: Boolean,
    listState: LazyListState,
    contentPadding: PaddingValues,
    onAuthorClick: () -> Unit,
    onCommentsClick: (CommunityPost) -> Unit,
    onShareClick: (CommunityPost) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        when {
            isLoading && posts.isEmpty() -> item(key = "posts_loading") {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            hasError && posts.isEmpty() -> item(key = "posts_error") {
                Column(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.community_posts_load_failed),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRetry) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }

            posts.isEmpty() -> item(key = "posts_empty") {
                Box(
                    modifier = Modifier
                        .fillParentMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.community_posts_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            else -> {
                items(
                    items = posts,
                    key = CommunityPost::id,
                ) { post ->
                    CommunityPostCard(
                        post = post,
                        onAuthorClick = onAuthorClick,
                        onCommentsClick = { onCommentsClick(post) },
                        onShareClick = { onShareClick(post) },
                    )
                }
                if (hasMore) {
                    item(key = "posts_load_more") {
                        LaunchedEffect(posts.size) { onLoadMore() }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isLoadingMore) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}
