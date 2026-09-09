package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.ui.components.shared.FlowBottomSheet
import io.github.aedev.flow.ui.components.shared.defaultSheetExpandedHeight
import io.github.aedev.flow.ui.components.shared.rememberFlowBottomSheetState

@Composable
fun FlowCommentsBottomSheet(
    comments: List<Comment>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onTimestampClick: (String) -> Unit = {},
    onFilterChanged: (CommentSortFilter) -> Unit = {},
    onLoadReplies: (Comment) -> Unit = {},
    onLoadMoreReplies: (Comment) -> Unit = {},
    selectedFilter: CommentSortFilter = CommentSortFilter.TOP,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    hasMore: Boolean = false,
    onAuthorClick: (String) -> Unit = {},
    onAvatarClick: (String) -> Unit = {},
    expandedHeight: Dp? = null,
    collapsedHeight: Dp = 0.dp,
    onSheetProgressChange: (Float) -> Unit = {},
    dismissOnOutsideTap: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberFlowBottomSheetState()
    val commentsListState = rememberLazyListState()

    LaunchedEffect(selectedFilter) {
        commentsListState.scrollToItem(0)
    }

    FlowBottomSheet(
        onDismiss = onDismiss,
        modifier = modifier,
        state = sheetState,
        expandedHeight = expandedHeight ?: defaultSheetExpandedHeight(),
        collapsedHeight = collapsedHeight,
        dismissOnOutsideTap = dismissOnOutsideTap,
        shape = RectangleShape,
        containerColor = MaterialTheme.colorScheme.surface,
        onProgressChange = onSheetProgressChange,
        header = { dragModifier ->
            // Not FlowSheetHeader: the sort chips sit under the title row, inside the same padding
            // and above the divider, which no header parameter can express.
            CommentsSheetHeader(
                selectedFilter = selectedFilter,
                onFilterChanged = onFilterChanged,
                onClose = { sheetState.dismiss() },
                dragModifier = dragModifier,
            )
        },
    ) {
        FlowCommentsList(
            comments = comments,
            isLoading = isLoading,
            listState = commentsListState,
            selectedFilter = selectedFilter,
            onTimestampClick = onTimestampClick,
            onLoadReplies = onLoadReplies,
            onLoadMoreReplies = onLoadMoreReplies,
            onAuthorClick = onAuthorClick,
            onAvatarClick = onAvatarClick,
            isLoadingMore = isLoadingMore,
            onLoadMore = onLoadMore,
            hasMore = hasMore,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommentsSheetHeader(
    selectedFilter: CommentSortFilter,
    onFilterChanged: (CommentSortFilter) -> Unit,
    onClose: () -> Unit,
    dragModifier: Modifier,
) {
    Column(modifier = dragModifier) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            BottomSheetDefaults.DragHandle()
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.comments),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
                }
            }
            CommentSortFilterChips(
                selectedFilter = selectedFilter,
                onFilterChanged = onFilterChanged,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
    }
}

@Composable
fun CommentSortFilterChips(
    selectedFilter: CommentSortFilter,
    onFilterChanged: (CommentSortFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selectedFilter == CommentSortFilter.TOP,
            onClick = { onFilterChanged(CommentSortFilter.TOP) },
            label = { Text(stringResource(R.string.filter_top)) },
        )
        FilterChip(
            selected = selectedFilter == CommentSortFilter.NEWEST,
            onClick = { onFilterChanged(CommentSortFilter.NEWEST) },
            label = { Text(stringResource(R.string.filter_newest)) },
        )
        FilterChip(
            selected = selectedFilter == CommentSortFilter.OLDEST,
            onClick = { onFilterChanged(CommentSortFilter.OLDEST) },
            label = { Text(stringResource(R.string.filter_oldest)) },
        )
    }
}
