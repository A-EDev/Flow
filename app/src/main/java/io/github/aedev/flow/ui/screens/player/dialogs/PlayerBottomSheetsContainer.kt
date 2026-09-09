package io.github.aedev.flow.ui.screens.player.dialogs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.SleepTimerManager
import io.github.aedev.flow.ui.components.VideoQuickActionsBottomSheet
import io.github.aedev.flow.ui.components.shared.FlowCommentsBottomSheet
import io.github.aedev.flow.ui.components.shared.FlowDescriptionBottomSheet
import io.github.aedev.flow.ui.components.shared.commentTimestampToMs
import io.github.aedev.flow.ui.components.shared.sortCommentsByFilter
import io.github.aedev.flow.ui.components.videoplayer.sheet.FlowLiveChatBottomSheet
import io.github.aedev.flow.ui.components.videoplayer.sheet.FlowPlaylistQueueBottomSheet
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.PlayerSheet

@Composable
internal fun PlayerBottomSheetsContainer(
    screenState: PlayerScreenState,
    uiState: VideoPlayerUiState,
    video: Video,
    completeVideo: Video,
    disableShortsPlayer: Boolean,
    showShortsPlayerPrompt: Boolean,
    comments: List<Comment>,
    commentsEnabled: Boolean = true,
    isLoadingComments: Boolean,
    isLoadingMoreComments: Boolean = false,
    hasMoreComments: Boolean = false,
    onLoadMoreComments: (videoId: String) -> Unit = {},
    mediaSheetExpandedHeight: Dp? = null,
    mediaSheetCollapsedHeight: Dp = 0.dp,
    context: Context,
    onPlayAsShort: (String) -> Unit,
    onLoadReplies: (Comment) -> Unit = {},
    onLoadMoreReplies: (Comment) -> Unit = {},
    onNavigateToChannel: ((String) -> Unit)? = null,
    renderCommentsSheet: Boolean = true,
    hostedInSidePanel: Boolean = false,
    onMediaSheetProgressChange: (Float) -> Unit = {},
) {
    val shareWithoutText by remember { PlayerPreferences(context).shareWithoutText }
        .collectAsStateWithLifecycle(initialValue = false)

    val sortedComments =
        remember(comments, screenState.commentSortFilter) {
            sortCommentsByFilter(comments, screenState.commentSortFilter)
        }

    val handleTimestampClick: (String) -> Unit =
        remember {
            { timestamp ->
                EnhancedPlayerManager.getInstance().seekTo(commentTimestampToMs(timestamp))
            }
        }

    LaunchedEffect(Unit) {
        SleepTimerManager.attachToPlayer(
            player = EnhancedPlayerManager.getInstance().getPlayer(),
        ) {
            EnhancedPlayerManager.getInstance().pause()
        }
        SleepTimerManager.attachExitCallback {
            EnhancedPlayerManager.getInstance().pause()
            EnhancedMusicPlayerManager.stop()
            context.stopService(
                android.content.Intent(context, io.github.aedev.flow.service.VideoPlayerService::class.java),
            )
            context.stopService(
                android.content.Intent(context, io.github.aedev.flow.service.Media3MusicService::class.java),
            )
            (context as? android.app.Activity)?.finishAndRemoveTask()
        }
    }

    // Quick actions sheet
    if (screenState.activeSheet == PlayerSheet.QuickActions) {
        VideoQuickActionsBottomSheet(
            video = completeVideo,
            onDismiss = { screenState.closeSheet() },
            onShare = {
                screenState.closeSheet()
                val shareText =
                    if (shareWithoutText) {
                        context.getString(R.string.share_link_only_template, completeVideo.id)
                    } else {
                        context.getString(R.string.check_out_video_template, completeVideo.title, completeVideo.id)
                    }
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, completeVideo.title)
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_video)))
            },
            onDownload = {
                screenState.open(PlayerSheet.Download)
            },
            onNotInterested = {
                screenState.closeSheet()
                Toast.makeText(context, context.getString(R.string.video_marked_not_interested), Toast.LENGTH_SHORT).show()
            },
            onChannelClick = onNavigateToChannel,
        )
    }

    // Comments Bottom Sheet
    if (screenState.activeSheet == PlayerSheet.Comments() && commentsEnabled && renderCommentsSheet) {
        FlowCommentsBottomSheet(
            comments = sortedComments,
            isLoading = isLoadingComments,
            selectedFilter = screenState.commentSortFilter,
            onFilterChanged = { filter ->
                screenState.commentSortFilter = filter
            },
            onLoadReplies = onLoadReplies,
            onLoadMoreReplies = onLoadMoreReplies,
            onTimestampClick = handleTimestampClick,
            isLoadingMore = isLoadingMoreComments,
            hasMore = hasMoreComments,
            onLoadMore = { onLoadMoreComments(video.id) },
            onAuthorClick = { authorChannelRef ->
                screenState.closeSheet()
                onNavigateToChannel?.invoke(authorChannelRef)
            },
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
            onDismiss = { screenState.closeSheet() },
        )
    }

    if (screenState.activeSheet == PlayerSheet.LiveChat() && uiState.isLiveChatAvailable) {
        FlowLiveChatBottomSheet(
            messages = uiState.liveChatMessages,
            isLoading = uiState.isLiveChatLoading,
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
            onDismiss = { screenState.closeSheet() },
        )
    }

    // Description Bottom Sheet
    if (screenState.activeSheet == PlayerSheet.Description) {
        val currentVideo =
            remember(uiState.streamInfo, video) {
                val streamInfo = uiState.streamInfo
                if (streamInfo != null) {
                    Video(
                        id = streamInfo.id ?: video.id,
                        title = streamInfo.name ?: video.title,
                        channelName = streamInfo.uploaderName ?: video.channelName,
                        channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId,
                        thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url ?: video.thumbnailUrl,
                        duration = streamInfo.duration.toInt(),
                        viewCount = streamInfo.viewCount,
                        likeCount = streamInfo.likeCount,
                        uploadDate =
                            streamInfo.textualUploadDate ?: streamInfo.uploadDate?.run {
                                try {
                                    val date = java.util.Date.from(offsetDateTime().toInstant())
                                    val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                                    sdf.format(date)
                                } catch (e: Exception) {
                                    video.uploadDate
                                }
                            } ?: video.uploadDate,
                        description = streamInfo.description?.content ?: video.description,
                        channelThumbnailUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl,
                    )
                } else {
                    video
                }
            }

        FlowDescriptionBottomSheet(
            video = currentVideo,
            tags = uiState.streamInfo?.tags ?: emptyList(),
            onTimestampClick = handleTimestampClick,
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
            onDismiss = { screenState.closeSheet() },
        )
    }

    // Chapters Bottom Sheet
    if (screenState.activeSheet == PlayerSheet.Chapters && !hostedInSidePanel) {
        PlayerChaptersSheetHost(
            screenState = screenState,
            chapters = uiState.chapters,
            thumbnailUrl = video.thumbnailUrl,
            asSidePanel = false,
            expandedHeight = mediaSheetExpandedHeight,
            onDismiss = { screenState.closeSheet() },
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
        )
    }

    // Playlist Queue Bottom Sheet
    if (screenState.activeSheet == PlayerSheet.Queue) {
        val queueVideos by EnhancedPlayerManager.getInstance().queueVideos.collectAsStateWithLifecycle(initialValue = emptyList())
        val currentQueueIndex by EnhancedPlayerManager.getInstance().currentQueueIndexState.collectAsStateWithLifecycle(initialValue = -1)
        val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()

        FlowPlaylistQueueBottomSheet(
            queueVideos = queueVideos,
            currentQueueIndex = currentQueueIndex,
            playlistTitle = playerState.queueTitle,
            isLooping = playerState.isQueueLooping,
            isShuffled = playerState.isQueueShuffled,
            onLoopToggle = EnhancedPlayerManager.getInstance()::toggleQueueLoop,
            onShuffleToggle = EnhancedPlayerManager.getInstance()::toggleQueueShuffle,
            onPlayVideoAtIndex = { index ->
                EnhancedPlayerManager.getInstance().playVideoAtIndex(index, loadStreamsInPlayer = false)
            },
            onRemoveVideoAtIndex = EnhancedPlayerManager.getInstance()::removeVideoAtIndex,
            onMoveVideoAtIndex = EnhancedPlayerManager.getInstance()::moveVideoAtIndex,
            onDismiss = { screenState.closeSheet() },
            expandedHeight = mediaSheetExpandedHeight,
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
        )
    }

    if (screenState.activeSheet == PlayerSheet.SleepTimer && !hostedInSidePanel) {
        PlayerSleepTimerSheetHost(
            asSidePanel = false,
            expandedHeight = mediaSheetExpandedHeight,
            onDismiss = { screenState.closeSheet() },
            collapsedHeight = mediaSheetCollapsedHeight,
            onSheetProgressChange = onMediaSheetProgressChange,
        )
    }

    // Shorts Suggestion Dialog
    if (screenState.showShortsPrompt && !disableShortsPlayer && showShortsPlayerPrompt) {
        ShortsSuggestionDialog(
            onPlayAsShort = {
                screenState.showShortsPrompt = false
                onPlayAsShort(completeVideo.id)
            },
            onDismiss = { screenState.showShortsPrompt = false },
        )
    }
}

/**
 * Dialog suggesting to play a short video in the Shorts player.
 */
@Composable
private fun ShortsSuggestionDialog(
    onPlayAsShort: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SmartDisplay, null) },
        title = {
            Text(
                text = stringResource(R.string.play_mode_suggestion_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(stringResource(R.string.play_mode_suggestion_body))
        },
        confirmButton = {
            TextButton(onClick = onPlayAsShort) {
                Text(stringResource(R.string.shorts_player))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        },
    )
}
