package io.github.aedev.flow.ui.screens.player.effects

import android.os.SystemClock
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

private const val LIVE_DISPLAY_BACKWARD_DRIFT_TOLERANCE_MS = 1_500L
private const val LIVE_DISPLAY_FORWARD_JUMP_THRESHOLD_MS = 5_000L
private const val LIVE_DISPLAY_MAX_TICK_MS = 1_000L
private const val LIVE_DISPLAY_RECENT_SEEK_MS = 2_000L
private const val ACTIVE_POSITION_TRACKING_INTERVAL_MS = 250L
private const val IDLE_POSITION_TRACKING_INTERVAL_MS = 1_000L

private var liveDisplayVideoId: String? = null
private var liveDisplayRawPositionMs: Long = 0L
private var liveDisplayUpdatedAtMs: Long = 0L
private var liveDisplayLastSeekAtMs: Long = 0L

private fun updateScreenPositionFromPlayer(
    player: Player,
    screenState: PlayerScreenState,
) {
    val managerState = EnhancedPlayerManager.getInstance().playerState.value
    if (managerState.isLive || player.isCurrentMediaItemLive) {
        updateLiveScreenPosition(player, screenState)
        return
    }

    screenState.currentPosition = player.currentPosition.coerceAtLeast(0L)
    screenState.bufferedPosition = player.bufferedPosition.coerceAtLeast(0L)

    val playerDuration = player.duration
    if (playerDuration > 0L && playerDuration != C.TIME_UNSET) {
        screenState.duration = playerDuration
    }
}

private fun updateLiveScreenPosition(
    player: Player,
    screenState: PlayerScreenState,
) {
    val manager = EnhancedPlayerManager.getInstance()
    val managerState = manager.playerState.value
    val videoId = managerState.currentVideoId ?: player.currentMediaItem?.mediaId
    val sameLiveItem = liveDisplayVideoId == videoId
    val now = SystemClock.elapsedRealtime()
    val elapsedMs =
        if (sameLiveItem) {
            (now - liveDisplayUpdatedAtMs).coerceIn(0L, LIVE_DISPLAY_MAX_TICK_MS)
        } else {
            0L
        }
    val rawPosition = player.currentPosition.coerceAtLeast(0L)
    val pendingSeekPosition = manager.consumeRecentLiveDisplaySeek()
    if (pendingSeekPosition != null) {
        liveDisplayLastSeekAtMs = now
    }
    val recentlySeeked = now - liveDisplayLastSeekAtMs <= LIVE_DISPLAY_RECENT_SEEK_MS
    val previousDisplayPosition =
        if (sameLiveItem) {
            screenState.currentPosition.takeIf { it > 0L } ?: liveDisplayRawPositionMs
        } else {
            rawPosition
        }
    val displayPosition =
        when {
            pendingSeekPosition != null -> {
                pendingSeekPosition
            }

            !sameLiveItem -> {
                rawPosition
            }

            !player.isPlaying -> {
                previousDisplayPosition
            }

            recentlySeeked -> {
                rawPosition
            }

            else -> {
                val rawDelta = rawPosition - previousDisplayPosition
                when {
                    rawDelta > LIVE_DISPLAY_FORWARD_JUMP_THRESHOLD_MS -> {
                        rawPosition
                    }

                    rawDelta < -LIVE_DISPLAY_BACKWARD_DRIFT_TOLERANCE_MS -> {
                        val speed = managerState.playbackSpeed.coerceAtLeast(0.1f)
                        previousDisplayPosition + (elapsedMs * speed).roundToLong()
                    }

                    else -> {
                        val speed = managerState.playbackSpeed.coerceAtLeast(0.1f)
                        maxOf(rawPosition, previousDisplayPosition + (elapsedMs * speed).roundToLong())
                    }
                }
            }
        }.coerceAtLeast(0L)

    val resolvedDuration = resolveLiveTimelineDuration(player) ?: 0L
    val liveDuration =
        maxOf(
            resolvedDuration,
            managerState.liveDurationMs,
            if (sameLiveItem) screenState.duration else 0L,
            displayPosition,
        )

    screenState.duration = liveDuration
    screenState.currentPosition = displayPosition.coerceAtMost(liveDuration.takeIf { it > 0L } ?: displayPosition)
    screenState.bufferedPosition =
        maxOf(
            player.bufferedPosition.coerceAtLeast(0L),
            screenState.currentPosition,
        ).let { buffered ->
            if (liveDuration > 0L) buffered.coerceAtMost(liveDuration) else buffered
        }

    liveDisplayVideoId = videoId
    liveDisplayRawPositionMs = rawPosition
    liveDisplayUpdatedAtMs = now
}

private fun resolveLiveTimelineDuration(player: Player): Long? {
    var liveDuration = 0L

    val timeline = player.currentTimeline
    val windowIndex = player.currentMediaItemIndex
    if (!timeline.isEmpty && windowIndex >= 0 && windowIndex < timeline.windowCount) {
        val window = Timeline.Window()
        timeline.getWindow(windowIndex, window)
        if (window.durationMs != C.TIME_UNSET && window.durationMs > 0L) {
            liveDuration = maxOf(liveDuration, window.durationMs)
        }
        if (window.defaultPositionMs != C.TIME_UNSET && window.defaultPositionMs > 0L) {
            liveDuration = maxOf(liveDuration, window.defaultPositionMs)
        }
    }

    val playerDuration = player.duration
    if (playerDuration != C.TIME_UNSET && playerDuration > 0L) {
        liveDuration = maxOf(liveDuration, playerDuration)
    }

    return liveDuration.takeIf { it > 0L }
}

/**
 * Polls the player position into [screenState].
 *
 * [showsPreciseProgress] must be true only while a surface renders a moving playhead — in practice
 * the expanded controls' seek bar. The mini-player's progress bar, the chapter list and the
 * comment timestamps all resolve to whole seconds, so outside that case the slow interval is
 * indistinguishable on screen while costing a quarter of the wakeups and recompositions.
 *
 * SponsorBlock skipping and watch-history writes deliberately do not depend on this: they run off
 * `PlaybackTracker` inside the player, so lowering the UI refresh rate cannot make a skip late.
 *
 * Paused with no precise surface showing (mini player, or fullscreen with the controls hidden)
 * the position cannot move on its own, so the loop reads it once and suspends until either key
 * flips instead of waking every second for the whole pause. A seek issued from the notification
 * while paused in the mini player is picked up on the next flip.
 *
 * Below STARTED nothing renders the position at all, but background audio keeps `isPlaying` true,
 * so the loop kept a 250 ms or 1 s wakeup running with the screen off. `repeatOnLifecycle` stops it
 * there and re-reads the position once on the way back, which is also what picks up a notification
 * seek made while backgrounded.
 */
@Composable
internal fun PositionTrackingEffect(
    isPlaying: Boolean,
    screenState: PlayerScreenState,
    showsPreciseProgress: Boolean,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(isPlaying, showsPreciseProgress, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            val keepPolling = isPlaying || showsPreciseProgress
            do {
                EnhancedPlayerManager.getInstance().getPlayer()?.let { player ->
                    if (player.playbackState != Player.STATE_IDLE) {
                        updateScreenPositionFromPlayer(player, screenState)
                    }
                }
                if (!keepPolling) break
                delay(
                    if (isPlaying && showsPreciseProgress) {
                        ACTIVE_POSITION_TRACKING_INTERVAL_MS
                    } else {
                        IDLE_POSITION_TRACKING_INTERVAL_MS
                    },
                )
            } while (true)
        }
    }
}
