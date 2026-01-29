package com.flow.youtube.ui.screens.player.content

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.flow.youtube.R
import com.flow.youtube.data.model.Video
import com.flow.youtube.ui.components.CommentsPreview
import com.flow.youtube.ui.components.VideoCardFullWidth
import com.flow.youtube.ui.screens.player.VideoPlayerUiState
import com.flow.youtube.ui.screens.player.VideoPlayerViewModel
import com.flow.youtube.data.model.Comment
import com.flow.youtube.ui.components.VideoInfoSection
import com.flow.youtube.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun VideoInfoContent(
    video: Video,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
    screenState: PlayerScreenState,
    comments: List<Comment>,
    context: Context,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    onChannelClick: (String) -> Unit
) {
    VideoInfoSection(
        video = video,
        title = uiState.streamInfo?.name ?: video.title,
        viewCount = uiState.streamInfo?.viewCount ?: video.viewCount,
        uploadDate = uiState.streamInfo?.uploadDate?.let { 
            try { 
                val date = java.util.Date.from(it.offsetDateTime().toInstant())
                val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                sdf.format(date)
            } catch(e: Exception) { null } 
        } ?: video.uploadDate,
        description = uiState.streamInfo?.description?.content ?: video.description,
        channelName = uiState.streamInfo?.uploaderName ?: video.channelName,
        channelAvatarUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl,
        subscriberCount = uiState.channelSubscriberCount,
        isSubscribed = uiState.isSubscribed,
        likeState = uiState.likeState ?: "NONE",
        dislikeCount = uiState.dislikeCount,
        onLikeClick = {
            val streamInfo = uiState.streamInfo
            val thumbnailUrl = streamInfo?.thumbnails?.maxByOrNull { it.height }?.url ?: video.thumbnailUrl
            
            when (uiState.likeState) {
                "LIKED" -> viewModel.removeLikeState(video.id)
                else -> viewModel.likeVideo(
                    video.id,
                    streamInfo?.name ?: video.title,
                    thumbnailUrl,
                    streamInfo?.uploaderName ?: video.channelName
                )
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
                val channelThumbSafe = streamInfo.uploaderUrl ?: video.thumbnailUrl
                
                viewModel.toggleSubscription(channelIdSafe, channelNameSafe, channelThumbSafe)
                
                scope.launch {
                    val message = if (uiState.isSubscribed) 
                        context.getString(R.string.unsubscribed_from, channelNameSafe) 
                    else 
                        context.getString(R.string.subscribed_to, channelNameSafe)
                        
                    val result = snackbarHostState.showSnackbar(
                        message, 
                        actionLabel = if (uiState.isSubscribed) context.getString(R.string.undo) else null
                    )
                    
                    if (result == SnackbarResult.ActionPerformed && uiState.isSubscribed) {
                        viewModel.toggleSubscription(channelIdSafe, channelNameSafe, channelThumbSafe)
                    }
                }
            }
        },
        onChannelClick = {
            uiState.streamInfo?.let { streamInfo ->
                val channelIdSafe = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId
                onChannelClick(channelIdSafe)
            } ?: onChannelClick(video.channelId)
        },
        onSaveClick = { screenState.showQuickActions = true },
        onShareClick = {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, video.title)
                putExtra(Intent.EXTRA_TEXT, context.getString(R.string.check_out_video_template, video.title, video.id))
            }
            context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_video)))
        },
        onDownloadClick = { screenState.showDownloadDialog = true },
        onDescriptionClick = { screenState.showDescriptionSheet = true }
    )

    // Comments Preview
    CommentsPreview(
        commentCount = uiState.commentCountText,
        latestComment = comments.firstOrNull()?.text,
        authorAvatar = comments.firstOrNull()?.authorThumbnail,
        onClick = { screenState.showCommentsSheet = true }
    )
}

/**
 * Related videos content for LazyListScope.
 */
fun LazyListScope.relatedVideosContent(
    relatedVideos: List<Video>,
    onVideoClick: (Video) -> Unit
) {
    // Header
    item {
        if (relatedVideos.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.related_videos),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }
    
    // Video items
    items(
        count = relatedVideos.size,
        key = { index -> relatedVideos[index].id }
    ) { index ->
        val relatedVideo = relatedVideos[index]
        VideoCardFullWidth(
            video = relatedVideo,
            onClick = { onVideoClick(relatedVideo) }
        )
    }
}

/**
 * Creates a complete Video object from StreamInfo if available
 */
fun createCompleteVideo(
    video: Video,
    uiState: VideoPlayerUiState
): Video {
    val streamInfo = uiState.streamInfo
    return if (streamInfo != null) {
        Video(
            id = streamInfo.id ?: video.id,
            title = streamInfo.name ?: video.title,
            channelName = streamInfo.uploaderName ?: video.channelName,
            channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId,
            thumbnailUrl = streamInfo.thumbnails.maxByOrNull { it.height }?.url ?: video.thumbnailUrl,
            duration = streamInfo.duration.toInt(),
            viewCount = streamInfo.viewCount,
            uploadDate = streamInfo.uploadDate?.toString() ?: video.uploadDate,
            description = streamInfo.description?.content ?: video.description,
            channelThumbnailUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl
        )
    } else {
        video
    }
}

/**
 * Remember-able version of createCompleteVideo
 */
@Composable
fun rememberCompleteVideo(
    video: Video,
    uiState: VideoPlayerUiState
): Video {
    return remember(uiState.streamInfo, video) {
        createCompleteVideo(video, uiState)
    }
}
