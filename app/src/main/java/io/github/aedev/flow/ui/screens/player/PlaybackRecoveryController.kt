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
 * What the screen does when the URLs it is playing stop working: save where the viewer had got to,
 * drop the player's media, and start the load again — or, once the budget for that is spent, stop
 * and say so.
 *
 * [StreamExpiryRecoveryController] decides whether a reported expiry is worth another attempt; this
 * class owns that decision's effects, and is the only place the two abandon paths converge.
 */
internal class PlaybackRecoveryController(
    private val context: Context,
    private val uiState: MutableStateFlow<VideoPlayerUiState>,
    private val playerManager: EnhancedPlayerManager,
    private val playerPreferences: PlayerPreferences,
    private val watchSessions: WatchSessionTracker,
    private val scope: CoroutineScope,
    private val isLoadInFlight: () -> Boolean,
    private val cancelLoad: () -> Unit,
    private val reloadStreams: (videoId: String, resumePositionMs: Long) -> Unit,
) {
    private val expiry = StreamExpiryRecoveryController()

    /** Re-fetch streams whenever an expired URL is detected (HTTP 403/410 "data changed"). */
    fun collectPlayerEvents() {
        expiry.collectExpiryEvents(
            scope = scope,
            events = playerManager.streamExpiredEvent,
            videoIdInPlayback = { uiState.value.cachedVideo?.id },
            isLoadInFlight = isLoadInFlight,
            onReload = ::reloadExpiredStreams,
            onGiveUp = ::abandonExhaustedPlayback,
        )

        playerManager.playbackAbandonedEvent
            .onEach {
                uiState.value.cachedVideo
                    ?.id
                    ?.let { videoId -> abandonReportedPlayback(videoId) }
            }.launchIn(scope)
    }

    fun onPlaybackRequested() = expiry.onPlaybackRequested()

    fun onLoadStarted(videoId: String) = expiry.onLoadStarted(videoId)

    private suspend fun reloadExpiredStreams(
        videoId: String,
        reload: StreamExpiryRecoveryController.Decision.Reload,
    ) {
        var recoveryPositionMs = 0L
        playerManager.getPlayer()?.let { player ->
            val positionMs = player.currentPosition
            recoveryPositionMs = positionMs.coerceAtLeast(0L)
            val durationMs =
                player.duration.takeIf { it > 0L }
                    ?: ((uiState.value.cachedVideo?.duration ?: 0) * 1000L)
            if (positionMs > 0L && durationMs > 0L) {
                watchSessions.saveResumePosition(
                    videoId = videoId,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    video = uiState.value.cachedVideo,
                )
            }
            player.pause()
            player.stop()
            player.clearMediaItems()
        }

        if (reload.evictCache) {
            try {
                playerManager.clearCacheForCurrentVideo()
            } catch (e: Exception) {
                Log.w(TAG, "Cache eviction failed: ${e.message}")
            }
        }

        uiState.update { it.copy(error = null, errorHint = null, isLoading = true) }
        reloadStreams(videoId, recoveryPositionMs)
    }

    /** The expiry budget is spent: stop the player before the screen turns terminal. */
    private suspend fun abandonExhaustedPlayback(videoId: String) {
        playerPreferences.markVideoUnplayable(videoId)
        cancelLoad()
        playerManager.getPlayer()?.let { player ->
            player.stop()
            player.clearMediaItems()
        }
        surfaceTerminalStreamFailure()
    }

    /** The player itself gave up, so it needs no stopping — only the latch and the screen. */
    private suspend fun abandonReportedPlayback(videoId: String) {
        expiry.onPlaybackAbandoned(videoId)
        playerPreferences.markVideoUnplayable(videoId)
        cancelLoad()
        Log.w(TAG, "Playback abandoned for $videoId — surfacing terminal error")
        surfaceTerminalStreamFailure()
    }

    private fun surfaceTerminalStreamFailure() {
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
