package io.github.aedev.flow.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.ui.theme.rememberFlowReduceMotion

/**
 * Keeps comments beside the video in fullscreen and tablet split layouts instead of covering playback.
 */
@Composable
fun PlayerCommentsPanel(
    comments: List<Comment>,
    isLoading: Boolean,
    isLoadingMore: Boolean,
    hasMore: Boolean,
    selectedFilter: CommentSortFilter,
    onFilterChanged: (CommentSortFilter) -> Unit,
    onTimestampClick: (String) -> Unit,
    onLoadReplies: (Comment) -> Unit,
    onLoadMoreReplies: (Comment) -> Unit,
    onAuthorClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sortedComments =
        remember(comments, selectedFilter) {
            sortCommentsByFilter(comments, selectedFilter)
        }
    val listState = rememberLazyListState()
    val filterScrollState = rememberScrollState()
    val reduceMotion = rememberFlowReduceMotion()

    LaunchedEffect(selectedFilter, reduceMotion) {
        if (reduceMotion) {
            listState.scrollToItem(0)
        } else {
            listState.animateScrollToItem(0)
        }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.comments),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }
        }
        CommentSortFilterChips(
            selectedFilter = selectedFilter,
            onFilterChanged = onFilterChanged,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(filterScrollState)
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FlowCommentsList(
            comments = sortedComments,
            isLoading = isLoading,
            listState = listState,
            selectedFilter = selectedFilter,
            onTimestampClick = onTimestampClick,
            onLoadReplies = onLoadReplies,
            onLoadMoreReplies = onLoadMoreReplies,
            onAuthorClick = onAuthorClick,
            onAvatarClick = {},
            isLoadingMore = isLoadingMore,
            onLoadMore = onLoadMore,
            hasMore = hasMore,
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
}
