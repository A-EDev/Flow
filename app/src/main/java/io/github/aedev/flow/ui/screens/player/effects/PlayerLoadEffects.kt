package io.github.aedev.flow.ui.screens.player.effects

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.*
import io.github.aedev.flow.R
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.screens.player.VideoPlayerUiState
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.SubtitleSelection
import io.github.aedev.flow.utils.NetworkState
import kotlinx.coroutines.delay

@Composable
internal fun VideoLoadEffect(
    videoId: String,
    context: Context,
    screenState: PlayerScreenState,
    viewModel: VideoPlayerViewModel,
) {
    LaunchedEffect(videoId) {
        screenState.resetForNewVideo()

        viewModel.loadVideoInfo(videoId, NetworkState.isOnWifi(context))
    }
}

@Composable
internal fun ShortVideoPromptEffect(
    videoDuration: Int,
    screenState: PlayerScreenState,
    isInQueue: Boolean,
    disableShortsPlayer: Boolean,
    showShortsPlayerPrompt: Boolean,
) {
    LaunchedEffect(videoDuration, screenState.hasShownShortsPrompt, isInQueue, disableShortsPlayer, showShortsPlayerPrompt) {
        if (disableShortsPlayer || !showShortsPlayerPrompt) {
            screenState.showShortsPrompt = false
            return@LaunchedEffect
        }

        if (!isInQueue && !screenState.hasShownShortsPrompt && videoDuration > 0 && videoDuration <= 80) {
            delay(1000)
            if (!disableShortsPlayer && showShortsPlayerPrompt) {
                screenState.showShortsPrompt = true
                screenState.hasShownShortsPrompt = true
            }
        }
    }
}

@Composable
internal fun SubscriptionAndLikeEffect(
    videoId: String,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
) {
    LaunchedEffect(uiState.streamInfo) {
        uiState.streamInfo?.let { streamInfo ->
            val channelId = streamInfo.uploaderUrl?.substringAfterLast("/") ?: ""
            if (channelId.isNotEmpty()) {
                viewModel.loadSubscriptionAndLikeState(channelId, videoId)
            }
        }
    }
}

@Composable
internal fun SponsorSkipEffect(context: Context) {
    LaunchedEffect(Unit) {
        EnhancedPlayerManager.getInstance().skipEvent.collect { segment ->
            Toast.makeText(context, context.getString(R.string.ui_skipped_segment, segment.category), Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
internal fun SubtitleLoadErrorEffect(
    context: Context,
    screenState: PlayerScreenState,
) {
    LaunchedEffect(Unit) {
        EnhancedPlayerManager.getInstance().subtitleLoadFailedEvent.collect { label ->
            SubtitleSelection.disable(screenState)
            Toast.makeText(context, context.getString(R.string.subtitle_load_failed, label), Toast.LENGTH_SHORT).show()
        }
    }
}
