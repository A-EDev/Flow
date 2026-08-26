package io.github.aedev.flow.ui.screens.player.content

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.components.LiveChatList
import io.github.aedev.flow.ui.components.LiveChatPreview
import io.github.aedev.flow.ui.components.PlayerCommentsPanel
import io.github.aedev.flow.ui.components.commentTimestampToMs
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState

/**
 * Right-hand column of the tablet landscape player. Comments take the column over when opened so
 * the video stays visible instead of being covered by the modal comments sheet (#918); otherwise it
 * shows live chat, falling back to the related-videos list.
 */
@Composable
fun PlayerDetailSideColumn(
    video: Video,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    screenState: PlayerScreenState,
    comments: List<Comment>,
    commentsEnabled: Boolean,
    showRelatedVideos: Boolean,
    relatedCardStyle: PlayerRelatedCardStyle,
    onVideoClick: (Video) -> Unit,
    onChannelClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isLoadingComments by viewModel.isLoadingComments.collectAsStateWithLifecycle()
    val hasMoreComments by viewModel.hasMoreComments.collectAsStateWithLifecycle()
    val isLoadingMoreComments by viewModel.isLoadingMoreComments.collectAsStateWithLifecycle()

    when {
        screenState.showCommentsSheet && commentsEnabled -> {
            BackHandler { screenState.showCommentsSheet = false }
            PlayerCommentsPanel(
                comments = comments,
                isLoading = isLoadingComments,
                isLoadingMore = isLoadingMoreComments,
                hasMore = hasMoreComments,
                selectedFilter = screenState.commentSortFilter,
                onFilterChanged = { screenState.commentSortFilter = it },
                onTimestampClick = { EnhancedPlayerManager.getInstance().seekTo(commentTimestampToMs(it)) },
                onLoadReplies = { viewModel.loadCommentReplies(it) },
                onLoadMoreReplies = { viewModel.loadMoreCommentReplies(it) },
                onAuthorClick = { authorChannelRef ->
                    screenState.showCommentsSheet = false
                    onChannelClick(authorChannelRef)
                },
                onLoadMore = { viewModel.loadMoreComments(video.id) },
                onClose = { screenState.showCommentsSheet = false },
                modifier = modifier,
            )
        }

        uiState.isLiveChatAvailable && screenState.showLiveChatPanel -> {
            Column(modifier.fillMaxHeight()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.live_chat),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { screenState.showLiveChatPanel = false }) {
                        Text(stringResource(R.string.live_chat_hide))
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                LiveChatList(
                    messages = uiState.liveChatMessages,
                    isLoading = uiState.isLiveChatLoading,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(vertical = 8.dp, horizontal = 0.dp),
                )
            }
        }

        else -> {
            LazyColumn(
                modifier = modifier,
                contentPadding = PaddingValues(bottom = 80.dp),
            ) {
                if (uiState.isLiveChatAvailable) {
                    item {
                        LiveChatPreview(onClick = { screenState.showLiveChatPanel = true })
                    }
                }
                if (showRelatedVideos) {
                    relatedVideosContent(
                        relatedVideos = uiState.relatedVideos,
                        onVideoClick = onVideoClick,
                        onChannelClick = onChannelClick,
                        cardStyle = relatedCardStyle,
                    )
                }
            }
        }
    }
}
