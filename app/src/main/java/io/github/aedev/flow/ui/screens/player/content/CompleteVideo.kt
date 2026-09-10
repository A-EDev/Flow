package io.github.aedev.flow.ui.screens.player.content

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.model.toVideo
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState

/**
 * The played video rebuilt from the loaded stream, for the surfaces that only need the extractor's
 * own values: the quick-actions sheet, the shorts prompt and the download dialog.
 */
@Composable
internal fun rememberCompleteVideo(
    video: Video,
    uiState: VideoPlayerUiState,
): Video =
    remember(uiState.streamInfo, video) {
        uiState.streamInfo?.toVideo(
            base = video,
            channelAvatarUrl = uiState.channelAvatarUrl,
        ) ?: video
    }
