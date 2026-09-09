package io.github.aedev.flow.ui.screens.player.effects

import androidx.compose.runtime.*
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import kotlinx.coroutines.delay

private fun resolveHistoryChannelName(
    video: Video,
    extractedName: String?,
): String {
    val cachedName = video.channelName
    val normalized = " ${cachedName.trim().lowercase()} "
    val isCollaboration =
        normalized.contains(" and ") ||
            normalized.contains(" & ") ||
            normalized.contains(" x ") ||
            normalized.contains(" with ")

    return when {
        isCollaboration && cachedName.isNotBlank() -> cachedName
        !extractedName.isNullOrBlank() -> extractedName
        else -> cachedName
    }
}

@Composable
internal fun WatchProgressSaveEffect(
    videoId: String,
    video: Video,
    isPlaying: Boolean,
    currentPosition: () -> Long,
    duration: () -> Long,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
) {
    val currentPosProvider by rememberUpdatedState(currentPosition)
    val currentDurProvider by rememberUpdatedState(duration)
    val currentUi by rememberUpdatedState(uiState)

    LaunchedEffect(videoId) {
        delay(3000)
        val streamInfo = currentUi.streamInfo
        if (currentUi.isCurrentLiveStream()) return@LaunchedEffect
        val channelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
        val channelName = resolveHistoryChannelName(video, streamInfo?.uploaderName)
        val thumbnailUrl =
            streamInfo?.thumbnails?.maxByOrNull { it.height }?.url
                ?: video.thumbnailUrl.takeIf { it.isNotEmpty() }
                ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
        val title = streamInfo?.name ?: video.title
        val durationMs = currentDurProvider()
        if (title.isNotEmpty() && durationMs > 0) {
            viewModel.savePlaybackPosition(
                videoId = videoId,
                position = currentPosProvider(),
                duration = durationMs,
                title = title,
                thumbnailUrl = thumbnailUrl,
                channelName = channelName,
                channelId = channelId,
                isShort = video.isShort,
            )
        }
    }

    LaunchedEffect(videoId, isPlaying) {
        while (isPlaying) {
            delay(10000)
            val streamInfo = currentUi.streamInfo
            if (currentUi.isCurrentLiveStream()) continue
            val channelId = streamInfo?.uploaderUrl?.substringAfterLast("/") ?: video.channelId
            val channelName = resolveHistoryChannelName(video, streamInfo?.uploaderName)
            val thumbnailUrl =
                streamInfo?.thumbnails?.maxByOrNull { it.height }?.url
                    ?: video.thumbnailUrl.takeIf { it.isNotEmpty() }
                    ?: "https://i.ytimg.com/vi/$videoId/hq720.jpg"
            val title = streamInfo?.name ?: video.title
            val durationMs = currentDurProvider()
            if (durationMs > 0 && title.isNotEmpty()) {
                viewModel.savePlaybackPosition(
                    videoId = videoId,
                    position = currentPosProvider(),
                    duration = durationMs,
                    title = title,
                    thumbnailUrl = thumbnailUrl,
                    channelName = channelName,
                    channelId = channelId,
                    isShort = video.isShort,
                )
            }
        }
    }
}
