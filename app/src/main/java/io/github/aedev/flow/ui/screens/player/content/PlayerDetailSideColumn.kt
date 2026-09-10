package io.github.aedev.flow.ui.screens.player.content

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.components.videoplayer.sheet.LiveChatPreview
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerCommentsPanelHost
import io.github.aedev.flow.ui.screens.player.dialogs.PlayerLiveChatColumn
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.PlayerSheet
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState

/**
 * Right-hand column of the wide player layout. Comments take the column over when opened so
 * the video stays visible instead of being covered by the modal comments sheet (#918); otherwise it
 * shows live chat, falling back to the related-videos list.
 */
@Composable
internal fun PlayerDetailSideColumn(
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
        screenState.activeSheet == PlayerSheet.Comments() && commentsEnabled -> {
            BackHandler { screenState.closeSheet() }
            PlayerCommentsPanelHost(
                videoId = video.id,
                screenState = screenState,
                viewModel = viewModel,
                comments = comments,
                isLoading = isLoadingComments,
                isLoadingMore = isLoadingMoreComments,
                hasMore = hasMoreComments,
                onNavigateToChannel = onChannelClick,
                onClose = { screenState.closeSheet() },
                modifier = modifier,
            )
        }

        uiState.isLiveChatAvailable && screenState.showLiveChatPanel -> {
            PlayerLiveChatColumn(
                messages = uiState.liveChatMessages,
                isLoading = uiState.isLiveChatLoading,
                onClose = { screenState.showLiveChatPanel = false },
                modifier = modifier,
            )
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
