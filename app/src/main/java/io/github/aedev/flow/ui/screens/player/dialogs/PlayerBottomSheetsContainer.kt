package io.github.aedev.flow.ui.screens.player.dialogs

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.stringResource
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedMusicPlayerManager
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.SleepTimerManager
import io.github.aedev.flow.ui.components.FlowChaptersBottomSheet
import io.github.aedev.flow.ui.components.FlowCommentsBottomSheet
import io.github.aedev.flow.ui.components.FlowDescriptionBottomSheet
import io.github.aedev.flow.ui.components.FlowPlaylistQueueBottomSheet
import io.github.aedev.flow.ui.components.SleepTimerSheet
import io.github.aedev.flow.ui.components.VideoQuickActionsBottomSheet
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.aedev.flow.data.model.Comment
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState

@Composable
fun PlayerBottomSheetsContainer(
    screenState: PlayerScreenState,
    uiState: VideoPlayerUiState,
    video: Video,
    completeVideo: Video,
    comments: List<Comment>,
    isLoadingComments: Boolean,
    context: Context,
    onPlayAsShort: (String) -> Unit,
    onPlayAsMusic: (String) -> Unit,
    onLoadReplies: (Comment) -> Unit = {},
    onNavigateToChannel: ((String) -> Unit)? = null
) {
    // Sorted comments based on filter
    val sortedComments = remember(comments, screenState.isTopComments) {
        if (screenState.isTopComments) {
            comments.sortedByDescending { it.likeCount }
        } else {
            comments
        }
    }
    
    val handleTimestampClick: (String) -> Unit = remember {
        { timestamp ->
            val parts = timestamp.split(":").map { it.toLongOrNull() ?: 0L }
            val seconds = when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                else -> 0L
            }
            val ms = seconds * 1000L
            EnhancedPlayerManager.getInstance().seekTo(ms)
            
            screenState.showCommentsSheet = false
            screenState.showDescriptionSheet = false
        }
    }
    
    LaunchedEffect(Unit) {
        SleepTimerManager.attachToPlayer(
            player = EnhancedPlayerManager.getInstance().getPlayer()
        ) {
            EnhancedPlayerManager.getInstance().pause()
        }
        SleepTimerManager.attachExitCallback {
            EnhancedPlayerManager.getInstance().pause()
            EnhancedMusicPlayerManager.stop()
            context.stopService(
                android.content.Intent(context, io.github.aedev.flow.service.VideoPlayerService::class.java)
            )
            context.stopService(
                android.content.Intent(context, io.github.aedev.flow.service.Media3MusicService::class.java)
            )
            (context as? android.app.Activity)?.finishAndRemoveTask()
        }
    }

    // Quick actions sheet
    if (screenState.showQuickActions) {
        VideoQuickActionsBottomSheet(
            video = completeVideo,
            onDismiss = { screenState.showQuickActions = false },
            onShare = {
                screenState.showQuickActions = false
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, completeVideo.title)
                    putExtra(Intent.EXTRA_TEXT, context.getString(R.string.check_out_video_template, completeVideo.title, completeVideo.id))
                }
                context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.share_video)))
            },
            onDownload = {
                screenState.showQuickActions = false
                screenState.showDownloadDialog = true
            },
            onNotInterested = {
                screenState.showQuickActions = false
                Toast.makeText(context, context.getString(R.string.video_marked_not_interested), Toast.LENGTH_SHORT).show()
            },
            onChannelClick = onNavigateToChannel,
        )
    }

    // Comments Bottom Sheet
    if (screenState.showCommentsSheet) {
        FlowCommentsBottomSheet(
            comments = sortedComments,
            commentCount = uiState.commentCountText,
            isLoading = isLoadingComments,
            isTopSelected = screenState.isTopComments,
            onFilterChanged = { isTop ->
                screenState.isTopComments = isTop
            },
            onLoadReplies = onLoadReplies,
            onTimestampClick = handleTimestampClick,
            onDismiss = { screenState.showCommentsSheet = false }
        )
    }

    // Description Bottom Sheet
    if (screenState.showDescriptionSheet) {
        val currentVideo = remember(uiState.streamInfo, video) {
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
                    uploadDate = streamInfo.textualUploadDate ?: streamInfo.uploadDate?.run { 
                        try {
                            val date = java.util.Date.from(offsetDateTime().toInstant())
                            val sdf = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                            sdf.format(date)
                        } catch (e: Exception) {
                            video.uploadDate
                        }
                    } ?: video.uploadDate,
                    description = streamInfo.description?.content ?: video.description,
                    channelThumbnailUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl
                )
            } else {
                video
            }
        }

        FlowDescriptionBottomSheet(
            video = currentVideo,
            onTimestampClick = handleTimestampClick,
            onDismiss = { screenState.showDescriptionSheet = false }
        )
    }

    // Chapters Bottom Sheet
    if (screenState.showChaptersSheet) {
        FlowChaptersBottomSheet(
            chapters = uiState.chapters,
            currentPosition = screenState.currentPosition,
            onChapterClick = { newPosition ->
                EnhancedPlayerManager.getInstance().seekTo(newPosition)
            },
            onDismiss = { screenState.showChaptersSheet = false }
        )
    }

    // Playlist Queue Bottom Sheet
    if (screenState.showPlaylistQueueSheet) {
        val queueVideos by EnhancedPlayerManager.getInstance().queueVideos.collectAsStateWithLifecycle(initialValue = emptyList())
        val currentQueueIndex by EnhancedPlayerManager.getInstance().currentQueueIndexState.collectAsStateWithLifecycle(initialValue = -1)
        val playerState by EnhancedPlayerManager.getInstance().playerState.collectAsStateWithLifecycle()

        FlowPlaylistQueueBottomSheet(
            queueVideos = queueVideos,
            currentQueueIndex = currentQueueIndex,
            playlistTitle = playerState.queueTitle,
            onPlayVideoAtIndex = { index ->
                EnhancedPlayerManager.getInstance().playVideoAtIndex(index)
            },
            onDismiss = { screenState.showPlaylistQueueSheet = false }
        )
    }

    if (screenState.showSleepTimerSheet) {
        SleepTimerSheet(
            onDismiss = { screenState.showSleepTimerSheet = false }
        )
    }

    // Shorts/Music Suggestion Dialog
    if (screenState.showShortsPrompt) {
        ShortsSuggestionDialog(
            isMusic = completeVideo.isMusic || 
                     completeVideo.title.contains("Official Audio", true) || 
                     completeVideo.title.contains("Lyrics", true),
            onPlayAsShort = {
                screenState.showShortsPrompt = false
                onPlayAsShort(completeVideo.id)
            },
            onPlayAsMusic = {
                screenState.showShortsPrompt = false
                onPlayAsMusic(completeVideo.id)
            },
            onDismiss = { screenState.showShortsPrompt = false }
        )
    }
}

/**
 * Dialog suggesting to play short video as Shorts or Music
 */
@Composable
fun ShortsSuggestionDialog(
    isMusic: Boolean,
    onPlayAsShort: () -> Unit,
    onPlayAsMusic: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.SmartDisplay, null) },
        title = {
            Text(
                text = stringResource(R.string.play_mode_suggestion_title), 
                style = MaterialTheme.typography.titleLarge
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
            Row {
                if (isMusic) {
                    TextButton(onClick = onPlayAsMusic) {
                        Text(stringResource(R.string.music_player))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.close))
                }
            }
        }
    )
}
