package io.github.aedev.flow.ui.screens.player.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState

/**
 * Creates a complete Video object from StreamInfo if available
 */
private fun createCompleteVideo(
    video: Video,
    uiState: VideoPlayerUiState,
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
            channelThumbnailUrl = uiState.channelAvatarUrl ?: video.channelThumbnailUrl,
        )
    } else {
        video
    }
}

/**
 * Remember-able version of createCompleteVideo
 */
@Composable
internal fun rememberCompleteVideo(
    video: Video,
    uiState: VideoPlayerUiState,
): Video =
    remember(uiState.streamInfo, video) {
        createCompleteVideo(video, uiState)
    }
