package io.github.aedev.flow.ui.screens.player.content

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.components.AddToPlaylistDialog
import io.github.aedev.flow.ui.components.videoplayer.info.CommentsPreview
import io.github.aedev.flow.ui.components.videoplayer.info.VideoInfoSection
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.PlayerSheet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun VideoInfoContent(
    video: Video,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    screenState: PlayerScreenState,
    comments: List<Comment>,
    commentsEnabled: Boolean = true,
    showCommentsPreview: Boolean = true,
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onChannelClick: (String) -> Unit,
) {
    var showAddToPlaylistDialog by remember(video.id) { mutableStateOf(false) }
    val playerPrefs = remember { PlayerPreferences(context) }
    val shareWithoutText by playerPrefs.shareWithoutText.collectAsState(initial = false)
    val deArrowEnabled by playerPrefs.deArrowEnabled.collectAsState(initial = false)
    val metadata =
        rememberPlayerVideoMetadata(
            video = video,
            uiState = uiState,
            deArrowEnabled = deArrowEnabled,
            context = context,
        )
    val resolvedVideoTitle = metadata.resolvedVideoTitle
    val resolvedCollaborators = metadata.resolvedCollaborators
    val resolvedChannelName = metadata.resolvedChannelName
    val streamUploadDate = metadata.streamUploadDate
    val dialogVideo = metadata.dialogVideo

    // ── Error details panel ─────────────────────────────────────────────────
    if (uiState.error != null) {
        PlayerErrorPanel(
            errorHint = uiState.errorHint,
            videoId = video.id,
            context = context,
            onRetryClick = { viewModel.retryLoadVideo() },
        )
    }

    val downloadedVideoIds by viewModel.downloadedVideoIds.collectAsState()
    val isVideoDownloaded = remember(downloadedVideoIds, video.id) { downloadedVideoIds.contains(video.id) }
    val isVideoSaved by remember(video.id) { viewModel.isVideoSavedToAnyPlaylist(video.id) }
        .collectAsState(initial = false)

    if (showAddToPlaylistDialog) {
        AddToPlaylistDialog(
            video = dialogVideo,
            onDismiss = { showAddToPlaylistDialog = false },
        )
    }

    VideoInfoSection(
        video = video,
        title = resolvedVideoTitle,
        viewCount = uiState.streamInfo?.viewCount ?: video.viewCount,
        uploadDate = streamUploadDate ?: video.uploadDate,
        description = uiState.streamInfo?.description?.content ?: video.description,
        isUpcoming = uiState.isUpcoming,
        channelName = resolvedChannelName,
        channelAvatarUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl,
        channelAvatarUrls = video.channelThumbnailUrls,
        collaborators = resolvedCollaborators,
        subscriberCount = uiState.channelSubscriberCount,
        isSubscribed = uiState.isSubscribed,
        isNotificationsEnabled = uiState.isNotificationsEnabled,
        likeState = uiState.likeState ?: "NONE",
        likeCount = uiState.streamInfo?.likeCount ?: video.likeCount,
        dislikeCount = uiState.dislikeCount,
        onLikeClick = {
            val streamInfo = uiState.streamInfo
            val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url ?: video.thumbnailUrl

            when (uiState.likeState) {
                "LIKED" -> {
                    viewModel.removeLikeState(video.id)
                }

                else -> {
                    viewModel.likeVideo(
                        video.id,
                        resolvedVideoTitle,
                        thumbnailUrl,
                        streamInfo?.uploaderName ?: video.channelName,
                    )
                }
            }
        },
        onDislikeClick = {
            when (uiState.likeState) {
                "DISLIKED" -> viewModel.removeLikeState(video.id)
                else -> viewModel.dislikeVideo(video.id)
            }
        },
        onSubscribeClick = {
            uiState.streamInfo?.let { streamInfo ->
                val channelIdSafe = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId
                val channelNameSafe = streamInfo.uploaderName ?: video.channelName
                // Use the fetched channel avatar URL if available, otherwise fallback to existing video thumbnail as last resort
                // but checking for uploaderUrl is wrong as it is a web link.
                val channelThumbSafe =
                    uiState.channelAvatarUrl?.takeIf { it.isNotEmpty() }
                        ?: video.channelThumbnailUrl?.takeIf { it.isNotEmpty() }
                        ?: ""

                viewModel.toggleSubscription(channelIdSafe, channelNameSafe, channelThumbSafe)

                scope.launch {
                    val message =
                        if (uiState.isSubscribed) {
                            context.getString(R.string.unsubscribed_from, channelNameSafe)
                        } else {
                            context.getString(R.string.subscribed_to, channelNameSafe)
                        }

                    val result =
                        snackbarHostState.showSnackbar(
                            message,
                            actionLabel = if (uiState.isSubscribed) context.getString(R.string.undo) else null,
                        )

                    if (result == SnackbarResult.ActionPerformed && uiState.isSubscribed) {
                        viewModel.toggleSubscription(channelIdSafe, channelNameSafe, channelThumbSafe)
                    }
                }
            }
        },
        onUnsubscribeClick = {
            uiState.streamInfo?.let { streamInfo ->
                val channelIdSafe = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId
                val channelNameSafe = streamInfo.uploaderName ?: video.channelName
                val channelThumbSafe =
                    uiState.channelAvatarUrl?.takeIf { it.isNotEmpty() }
                        ?: video.channelThumbnailUrl?.takeIf { it.isNotEmpty() }
                        ?: ""
                viewModel.toggleSubscription(channelIdSafe, channelNameSafe, channelThumbSafe)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.unsubscribed_from, channelNameSafe),
                    )
                }
            }
        },
        onNotificationChange = { enabled ->
            val channelIdSafe = uiState.streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
            viewModel.setNotificationEnabled(channelIdSafe, enabled)
        },
        onChannelClick = {
            uiState.streamInfo?.let { streamInfo ->
                val channelIdSafe = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId
                onChannelClick(channelIdSafe)
            } ?: onChannelClick(video.channelId)
        },
        onCollaboratorClick = onChannelClick,
        onSaveClick = { showAddToPlaylistDialog = true },
        onShareClick = {
            val shareText =
                if (shareWithoutText) {
                    context.getString(R.string.share_link_only_template, video.id)
                } else {
                    context.getString(R.string.check_out_video_template, resolvedVideoTitle, video.id)
                }
            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, resolvedVideoTitle)
                    putExtra(Intent.EXTRA_TEXT, shareText)
                }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_video)))
        },
        onDownloadClick = { screenState.open(PlayerSheet.Download) },
        isSaved = isVideoSaved,
        isDownloaded = isVideoDownloaded,
        onBackgroundPlayClick = { viewModel.startBackgroundPlayback() },
        onCopyLinkClick = {
            val url = "https://www.youtube.com/watch?v=${video.id}"
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("video_link", url))
            Toast.makeText(context, context.getString(R.string.link_copied), Toast.LENGTH_SHORT).show()
        },
        onCopyLinkAtTimeClick = {
            val positionMs = EnhancedPlayerManager.getInstance().getCurrentPosition()
            val positionSeconds = positionMs / 1000L
            val url = "https://www.youtube.com/watch?v=${video.id}&t=${positionSeconds}s"
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("video_link_at_time", url))
            Toast.makeText(context, context.getString(R.string.link_with_timestamp_copied), Toast.LENGTH_SHORT).show()
        },
        onDescriptionClick = { screenState.open(PlayerSheet.Description) },
    )

    if (uiState.isLiveChatAvailable) {
        io.github.aedev.flow.ui.components.videoplayer.sheet.LiveChatPreview(
            onClick = { screenState.open(PlayerSheet.LiveChat()) },
        )
    }

    if (commentsEnabled) {
        CommentsPreview(
            latestComment = if (showCommentsPreview) comments.firstOrNull()?.text else null,
            authorAvatar = if (showCommentsPreview) comments.firstOrNull()?.authorThumbnail else null,
            showPreviewText = showCommentsPreview,
            onClick = { screenState.open(PlayerSheet.Comments()) },
        )
    }
}
