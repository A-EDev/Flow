package io.github.aedev.flow.ui.screens.player.effects

import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.player.error.PlayerDiagnostics
import io.github.aedev.flow.ui.screens.player.VideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import io.github.aedev.flow.ui.screens.player.state.VideoPlayerUiState
import kotlinx.coroutines.delay

private const val TAG = "PlayerEffects"
private const val STARTUP_RECOVERY_DELAY_MS = 5_000L
private const val STARTUP_BUFFERING_GRACE_MS = 4_000L

private data class StartupRecoverySnapshot(
    val belongsToVideo: Boolean,
    val hasMedia: Boolean,
    val isIdle: Boolean,
    val hasDuration: Boolean,
    val hasStarted: Boolean,
    val isActivelyBuffering: Boolean,
    val playbackState: Int?,
    val position: Long,
    val duration: Long,
    val bufferedPosition: Long,
)

private fun captureStartupRecoverySnapshot(
    manager: EnhancedPlayerManager,
    videoId: String,
    uiState: VideoPlayerUiState,
    screenState: PlayerScreenState,
): StartupRecoverySnapshot {
    val player = manager.getPlayer()
    val playerState = manager.playerState.value
    val playerDuration = player?.duration ?: 0L
    val playerPosition = player?.currentPosition ?: 0L

    return StartupRecoverySnapshot(
        belongsToVideo = playerState.currentVideoId == videoId || uiState.cachedVideo?.id == videoId,
        hasMedia = player?.currentMediaItem != null,
        isIdle = player == null || player.playbackState == Player.STATE_IDLE,
        hasDuration = screenState.duration > 0L || playerDuration > 0L,
        hasStarted = playerPosition > 500L || playerState.isPlaying,
        isActivelyBuffering =
            (
                player?.playbackState == Player.STATE_BUFFERING &&
                    player.playWhenReady
            ) || playerState.isBuffering,
        playbackState = player?.playbackState,
        position = playerPosition,
        duration = playerDuration,
        bufferedPosition = player?.bufferedPosition ?: 0L,
    )
}

/**
 * Observes lifecycle ON_RESUME events and recovers player state after screen-off/on.
 *
 * On some devices (notably Samsung running Android 16), the activity goes through
 * onStop()/onStart() when the screen is turned off and back on. This causes:
 *  - collectAsStateWithLifecycle() to briefly stop, then resume with potentially stale state
 *  - ExoPlayer to reset its reported duration to TIME_UNSET during re-buffering
 *  - The UI to display 0:00 / 0:00 even though playback is still live
 *
 * This effect detects ON_RESUME, waits for ExoPlayer to report a valid duration,
 * then restores the screenState and re-triggers playback if needed.
 */
@Composable
internal fun PlaybackRefocusEffect(
    screenState: PlayerScreenState,
    lifecycleOwner: LifecycleOwner,
) {
    var resumeTrigger by remember { mutableIntStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME, lifecycleOwner) { resumeTrigger++ }

    LaunchedEffect(resumeTrigger) {
        if (resumeTrigger == 0) return@LaunchedEffect
        val mgr = EnhancedPlayerManager.getInstance()
        val player = mgr.getPlayer() ?: return@LaunchedEffect
        if (mgr.isInAudioOnlyMode() || mgr.isVideoSurfaceRestorePending()) {
            mgr.restoreVideoOutput()
        }
        if (mgr.recoverClearedMediaAfterForeground()) return@LaunchedEffect

        delay(150L)

        val playerMgrState = mgr.playerState.value

        if (!playerMgrState.hasEnded &&
            player.playbackState in listOf(Player.STATE_READY, Player.STATE_BUFFERING) &&
            player.duration > 0L
        ) {
            screenState.duration = player.duration
            screenState.currentPosition = player.currentPosition.coerceAtLeast(0L)
            if (playerMgrState.playWhenReady && !player.isPlaying) {
                player.play()
            }
            return@LaunchedEffect
        }

        if (!playerMgrState.hasEnded &&
            player.playbackState == Player.STATE_BUFFERING
        ) {
            if (playerMgrState.playWhenReady && !player.isPlaying) {
                player.play()
            }
            return@LaunchedEffect
        }

        if (playerMgrState.currentVideoId != null) {
            mgr.beginBackgroundRecovery()
            try {
                val savedPosition =
                    player.currentPosition.takeIf { it > 500L }
                        ?: screenState.currentPosition.takeIf { it > 500L }

                var attempts = 0
                while (attempts < 25 && player.duration <= 0L) {
                    delay(100L)
                    attempts++
                }

                val validDuration = player.duration
                if (validDuration > 0L) {
                    screenState.duration = validDuration
                    screenState.currentPosition = player.currentPosition.coerceAtLeast(0L)
                } else {
                    PlayerDiagnostics.logRefocusGlitch(
                        TAG,
                        "No valid duration after $attempts polls; state=${player.playbackState} pos=${player.currentPosition}",
                    )
                    mgr.handleRefocusStuck(playerMgrState.currentVideoId)
                    return@LaunchedEffect
                }

                if (player.playbackState == Player.STATE_IDLE && playerMgrState.currentVideoId != null) {
                    Log.d(TAG, "PlaybackRefocusEffect: player in IDLE after resume, calling prepare()")
                    player.prepare()
                    if (savedPosition != null && savedPosition > 500L) {
                        player.seekTo(savedPosition)
                    }
                    delay(300L)
                }

                if (playerMgrState.playWhenReady && !player.isPlaying &&
                    player.playbackState != Player.STATE_ENDED
                ) {
                    player.play()
                }
            } finally {
                mgr.endBackgroundRecovery()
            }
        }
    }
}

@Composable
internal fun PlaybackStartupRecoveryEffect(
    videoId: String,
    uiState: VideoPlayerUiState,
    screenState: PlayerScreenState,
    viewModel: VideoPlayerViewModel,
) {
    var recoveredVideoId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(videoId) {
        recoveredVideoId = null
    }

    LaunchedEffect(videoId, uiState.isLoading, uiState.streamInfo, uiState.localFilePath, uiState.error) {
        if (uiState.isLoading || uiState.error != null || uiState.isRestoredSession) return@LaunchedEffect
        if (uiState.streamInfo == null && uiState.localFilePath == null) return@LaunchedEffect

        delay(STARTUP_RECOVERY_DELAY_MS)

        val manager = EnhancedPlayerManager.getInstance()
        if (manager.hasAbandonedPlayback()) {
            Log.w(TAG, "Startup recovery: playback abandoned for $videoId — not re-preparing")
            return@LaunchedEffect
        }
        val player = manager.getPlayer()
        var snapshot = captureStartupRecoverySnapshot(manager, videoId, uiState, screenState)
        if (!snapshot.belongsToVideo) return@LaunchedEffect

        if (snapshot.hasMedia && snapshot.isIdle) {
            Log.w(TAG, "Startup recovery: player idle for $videoId, preparing again")
            player?.prepare()
            player?.play()
            delay(2_000L)
            snapshot = captureStartupRecoverySnapshot(manager, videoId, uiState, screenState)
        }

        if (snapshot.isActivelyBuffering && !snapshot.hasDuration && !snapshot.hasStarted) {
            delay(STARTUP_BUFFERING_GRACE_MS)
            snapshot = captureStartupRecoverySnapshot(manager, videoId, uiState, screenState)
        }

        val unresolvedStartup = !snapshot.hasDuration && !snapshot.hasStarted
        val stillStuck =
            snapshot.belongsToVideo &&
                recoveredVideoId != videoId &&
                (
                    (
                        !snapshot.isActivelyBuffering &&
                            (!manager.isPreparedForPlayback(videoId) || unresolvedStartup)
                    ) ||
                        (snapshot.isActivelyBuffering && unresolvedStartup)
                )

        if (stillStuck) {
            recoveredVideoId = videoId
            Log.w(
                TAG,
                "Startup recovery: reloading stuck playback for $videoId " +
                    "(state=${snapshot.playbackState}, pos=${snapshot.position}, " +
                    "dur=${snapshot.duration}, buff=${snapshot.bufferedPosition}, " +
                    "activeBuffering=${snapshot.isActivelyBuffering})",
            )
            viewModel.retryLoadVideo()
        }
    }
}
