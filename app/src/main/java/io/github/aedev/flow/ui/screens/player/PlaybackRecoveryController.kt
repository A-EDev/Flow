package io.github.aedev.flow.ui.screens.player

import android.content.Context
import android.util.Log
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update

/**
 * What the screen does when the player gives up on the streams it is playing: stop its own load and
 * say so. Reloading refused streams is the player's job ([EnhancedPlayerManager]), so it happens
 * with or without this screen; the screen only tells the player when the viewer asks for playback,
 * which starts the player's retry budget over.
 */
internal class PlaybackRecoveryController(
    private val context: Context,
    private val uiState: MutableStateFlow<VideoPlayerUiState>,
    private val playerManager: EnhancedPlayerManager,
    private val playerPreferences: PlayerPreferences,
    private val scope: CoroutineScope,
    private val cancelLoad: () -> Unit,
) {
    fun collectPlayerEvents() {
        playerManager.playbackAbandonedEvent
            .onEach {
                uiState.value.cachedVideo
                    ?.id
                    ?.let { videoId -> abandonReportedPlayback(videoId) }
            }.launchIn(scope)
    }

    fun onPlaybackRequested() = playerManager.resetStreamRecovery()

    fun onLoadStarted(videoId: String) = playerManager.onStreamLoadStarted(videoId)

    /** The player itself gave up, so it needs no stopping — only the screen. */
    private suspend fun abandonReportedPlayback(videoId: String) {
        playerPreferences.markVideoUnplayable(videoId)
        cancelLoad()
        Log.w(TAG, "Playback abandoned for $videoId — surfacing terminal error")
        uiState.update {
            it.copy(
                isLoading = false,
                error = context.getString(R.string.error_all_stream_sources_failed),
                errorHint = context.getString(R.string.error_playback_retry_hint),
            )
        }
    }

    private companion object {
        const val TAG = "PlaybackRecoveryController"
    }
}
