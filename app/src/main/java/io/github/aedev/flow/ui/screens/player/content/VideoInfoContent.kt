package io.github.aedev.flow.ui.screens.player.content

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.data.local.PlayerRelatedCardStyle
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.components.CommentsPreview
import io.github.aedev.flow.ui.components.CompactVideoCard
import io.github.aedev.flow.ui.components.VideoCardFullWidth
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.ui.components.VideoInfoSection
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
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
    // ── Error details panel ─────────────────────────────────────────────────
    if (uiState.error != null) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
            tonalElevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (!uiState.errorHint.isNullOrBlank()) {
                    Text(
                        text = uiState.errorHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)
                    )
                }
                // Row 1: Retry + Copy Logs
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.retryLoadVideo() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF0000),
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Retry", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val ok = PlayerDiagnostics.copyToClipboard(context)
                            Toast.makeText(
                                context,
                                if (ok) "Logs copied to clipboard" else "Failed to copy logs",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Copy Logs", fontSize = 13.sp)
                    }
                }
                // Row 2: Open in YouTube (full width)
                OutlinedButton(
                    onClick = {
                        val intent = Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://www.youtube.com/watch?v=${video.id}")
                        )
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInBrowser,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Open in YouTube", fontSize = 13.sp)
                }
            }
        }
    }

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
        isNotificationsEnabled = uiState.isNotificationsEnabled,
        likeState = uiState.likeState ?: "NONE",
        likeCount = uiState.streamInfo?.likeCount ?: video.likeCount,
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
                // Use the fetched channel avatar URL if available, otherwise fallback to existing video thumbnail as last resort 
                // but checking for uploaderUrl is wrong as it is a web link.
                val channelThumbSafe = uiState.channelAvatarUrl?.takeIf { it.isNotEmpty() } 
                    ?: video.channelThumbnailUrl?.takeIf { it.isNotEmpty() }
                    ?: ""
                
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
        onUnsubscribeClick = {
            uiState.streamInfo?.let { streamInfo ->
                val channelIdSafe = streamInfo.uploaderUrl?.substringAfterLast("/") ?: video.channelId
                val channelNameSafe = streamInfo.uploaderName ?: video.channelName
                val channelThumbSafe = uiState.channelAvatarUrl?.takeIf { it.isNotEmpty() }
                    ?: video.channelThumbnailUrl?.takeIf { it.isNotEmpty() }
                    ?: ""
                viewModel.toggleSubscription(channelIdSafe, channelNameSafe, channelThumbSafe)
                scope.launch {
                    snackbarHostState.showSnackbar(
                        context.getString(R.string.unsubscribed_from, channelNameSafe)
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
        onBackgroundPlayClick = { viewModel.startBackgroundService() },
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
        onDescriptionClick = { screenState.showDescriptionSheet = true }
    )

    // Comments Preview
    CommentsPreview(
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
    onVideoClick: (Video) -> Unit,
    cardStyle: PlayerRelatedCardStyle = PlayerRelatedCardStyle.FULL_WIDTH
) {
    // Header
    item {
        if (relatedVideos.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
               
            }
        }
    }
    
    // Video items
    items(
        count = relatedVideos.size,
        key = { index -> relatedVideos[index].id }
    ) { index ->
        val relatedVideo = relatedVideos[index]
        when (cardStyle) {
            PlayerRelatedCardStyle.COMPACT -> CompactVideoCard(
                video = relatedVideo,
                onClick = { onVideoClick(relatedVideo) }
            )
            PlayerRelatedCardStyle.FULL_WIDTH -> VideoCardFullWidth(
                video = relatedVideo,
                onClick = { onVideoClick(relatedVideo) }
            )
        }
    }
}

/**
 * Related videos grid content for LazyListScope.
 */
fun LazyListScope.relatedVideosGridContent(
    relatedVideos: List<Video>,
    columns: Int,
    onVideoClick: (Video) -> Unit,
    cardStyle: PlayerRelatedCardStyle = PlayerRelatedCardStyle.FULL_WIDTH
) {
    item {
        if (relatedVideos.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
               
            }
        }
    }
    
    val chunkedVideos = relatedVideos.chunked(columns)
    
    items(
        count = chunkedVideos.size,
        key = { index -> chunkedVideos[index].joinToString { it.id } }
    ) { index ->
        val rowVideos = chunkedVideos[index]
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            for (video in rowVideos) {
                Box(modifier = Modifier.weight(1f)) {
                    when (cardStyle) {
                        PlayerRelatedCardStyle.COMPACT -> CompactVideoCard(
                            video = video,
                            onClick = { onVideoClick(video) }
                        )
                        PlayerRelatedCardStyle.FULL_WIDTH -> VideoCardFullWidth(
                            video = video,
                            onClick = { onVideoClick(video) }
                        )
                    }
                }
            }
            val emptySpaces = columns - rowVideos.size
            for (i in 0 until emptySpaces) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
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
