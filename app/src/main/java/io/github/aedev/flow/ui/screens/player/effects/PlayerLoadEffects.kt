package io.github.aedev.flow.ui.screens.player.effects

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.*
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.SubtitleSelection
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
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

/**
 * The load pipeline a genuinely new video needs. A restored session already has a prepared
 * player, so the host composes none of this for one.
 */
@Composable
internal fun PlayerFreshSessionEffects(
    videoId: String,
    context: Context,
    screenState: PlayerScreenState,
    uiState: VideoPlayerUiState,
    viewModel: VideoPlayerViewModel,
) {
    VideoLoadEffect(
        videoId = videoId,
        context = context,
        screenState = screenState,
        viewModel = viewModel,
    )

    LaunchedEffect(
        videoId,
        uiState.isLoading,
        uiState.error,
        uiState.streamInfo,
        uiState.audioStream,
        uiState.localFilePath,
    ) {
        viewModel.ensurePlaybackPrepared(videoId)
    }

    PlaybackStartupRecoveryEffect(
        videoId = videoId,
        uiState = uiState,
        screenState = screenState,
        viewModel = viewModel,
    )
}

/**
 * Adopts whatever the player singleton is actually playing when it changes underneath the UI.
 *
 * The comments fetch is deliberately not part of that adoption. Only the expanded body renders
 * comments — the preview under the video and the comments sheet — so while the player is collapsed
 * to the mini bar a queue advance used to spend a network round trip on a surface nobody can see.
 * [expandedBodyVisible] defers it, and the per-id latch is what makes expanding later fetch the
 * comments the collapsed advances skipped without re-fetching the ones already held.
 */
@Composable
internal fun GlobalVideoSyncEffect(
    currentVideoId: String?,
    currentVideo: () -> Video?,
    uiState: VideoPlayerUiState,
    commentsEnabled: Boolean,
    expandedBodyVisible: Boolean,
    viewModel: VideoPlayerViewModel,
) {
    LaunchedEffect(currentVideoId) {
        val current = currentVideo()
        if (current != null && !uiState.isRestoredSession) {
            if (current.id != uiState.cachedVideo?.id || uiState.streamInfo?.id != current.id) {
                viewModel.syncWithCurrentPlayerVideo(current)
            }
        }
    }

    var commentsLoadedFor by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(currentVideoId, expandedBodyVisible) {
        if (!commentsEnabled || !expandedBodyVisible) return@LaunchedEffect
        val current = currentVideo() ?: return@LaunchedEffect
        if (uiState.isRestoredSession || commentsLoadedFor == current.id) return@LaunchedEffect
        commentsLoadedFor = current.id
        viewModel.loadComments(current.id)
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
