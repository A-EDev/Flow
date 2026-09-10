package io.github.aedev.flow.ui.components.videoplayer.gesture

import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.media3.common.Player
import io.github.aedev.flow.player.EnhancedPlayerManager

/** How long a further tap in the same zone keeps adding to the running double-tap seek total. */
private const val SEEK_ACCUMULATION_WINDOW_MS = 1_000L

/** Screen fraction on each side that maps to a seek zone; the middle third is play/pause. */
private const val SEEK_ZONE_FRACTION = 1f / 3f

private const val ZONE_LEFT = -1
private const val ZONE_CENTER = 0
private const val ZONE_RIGHT = 1

internal fun Modifier.playerTapGestures(
    isSpeedBoostActive: State<Boolean>,
    onSpeedBoostChange: State<(Boolean) -> Unit>,
    showControls: State<Boolean>,
    onShowControlsChange: State<(Boolean) -> Unit>,
    onShowSeekBackChange: State<(Boolean) -> Unit>,
    onShowSeekForwardChange: State<(Boolean) -> Unit>,
    onSeekAccumulate: State<(Int) -> Unit>,
    currentPosition: State<() -> Long>,
    duration: State<Long>,
    onNormalSpeedChange: State<(Float) -> Unit>,
    isFullscreen: State<Boolean>,
    doubleTapSeekMs: State<Long>,
    longPressPlaybackSpeed: State<Float>,
    isSeekForwardActive: State<Boolean>,
    isSeekBackActive: State<Boolean>,
    haptics: HapticFeedback,
): Modifier {
    val currentIsSpeedBoostActive by isSpeedBoostActive
    val currentOnSpeedBoostChange by onSpeedBoostChange
    val currentShowControls by showControls
    val currentOnShowControlsChange by onShowControlsChange
    val currentOnShowSeekBackChange by onShowSeekBackChange
    val currentOnShowSeekForwardChange by onShowSeekForwardChange
    val currentOnSeekAccumulate by onSeekAccumulate
    val currentPositionProvider by currentPosition
    val currentDuration by duration
    val currentOnNormalSpeedChange by onNormalSpeedChange
    val currentIsFullscreen by isFullscreen
    val currentDoubleTapSeekMs by doubleTapSeekMs
    val currentLongPressPlaybackSpeed by longPressPlaybackSpeed
    val currentIsSeekForwardActive by isSeekForwardActive
    val currentIsSeekBackActive by isSeekBackActive

    return this.pointerInput(Unit) {
        var accumulatedForwardMs = 0L
        var accumulatedBackMs = 0L
        var lastForwardTapTime = 0L
        var lastBackTapTime = 0L
        var pendingForwardTargetMs: Long? = null
        var pendingBackTargetMs: Long? = null
        var speedBeforeLongPress: Float? = null

        var revealedOnTap = false
        var hidePending = false

        fun zoneOf(x: Float): Int {
            val width = size.width.toFloat()
            if (width <= 0f) return ZONE_CENTER
            return when {
                x < width * SEEK_ZONE_FRACTION -> ZONE_LEFT
                x > width * (1f - SEEK_ZONE_FRACTION) -> ZONE_RIGHT
                else -> ZONE_CENTER
            }
        }

        fun applyZoneSeek(forward: Boolean) {
            val manager = EnhancedPlayerManager.getInstance()
            val player = manager.getPlayer()
            val isLive = manager.playerState.value.isLive || player?.isCurrentMediaItemLive == true
            val playerPosition = player?.currentPosition ?: currentPositionProvider()
            val step = currentDoubleTapSeekMs
            val now = SystemClock.uptimeMillis()

            val target =
                if (forward) {
                    currentOnShowSeekBackChange(false)
                    accumulatedBackMs = 0L
                    lastBackTapTime = 0L
                    pendingBackTargetMs = null

                    val continuing = now - lastForwardTapTime < SEEK_ACCUMULATION_WINDOW_MS
                    accumulatedForwardMs = if (continuing) accumulatedForwardMs + step else step
                    lastForwardTapTime = now
                    val base = pendingForwardTargetMs?.takeIf { continuing } ?: playerPosition
                    (base + step).coerceAtMost(currentDuration).also {
                        pendingForwardTargetMs = it
                        currentOnSeekAccumulate((accumulatedForwardMs / 1000L).toInt())
                        currentOnShowSeekForwardChange(true)
                    }
                } else {
                    currentOnShowSeekForwardChange(false)
                    accumulatedForwardMs = 0L
                    lastForwardTapTime = 0L
                    pendingForwardTargetMs = null

                    val continuing = now - lastBackTapTime < SEEK_ACCUMULATION_WINDOW_MS
                    accumulatedBackMs = if (continuing) accumulatedBackMs + step else step
                    lastBackTapTime = now
                    val base = pendingBackTargetMs?.takeIf { continuing } ?: playerPosition
                    (base - step).coerceAtLeast(0L).also {
                        pendingBackTargetMs = it
                        currentOnSeekAccumulate(-(accumulatedBackMs / 1000L).toInt())
                        currentOnShowSeekBackChange(true)
                    }
                }

            if (isLive) manager.seekToLiveTimeline(target) else manager.seekTo(target)
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }

        fun togglePlayPause() {
            val manager = EnhancedPlayerManager.getInstance()
            val player = manager.getPlayer() ?: return
            when {
                player.playbackState == Player.STATE_ENDED -> manager.replay()
                player.isPlaying -> manager.pause()
                else -> manager.play()
            }
        }

        detectPlayerTaps(
            onTapUp = { offset ->
                if (!currentIsSpeedBoostActive) {
                    val zone = zoneOf(offset.x)
                    val continuesActiveSeek =
                        (zone == ZONE_LEFT && currentIsSeekBackActive) ||
                            (zone == ZONE_RIGHT && currentIsSeekForwardActive)
                    when {
                        continuesActiveSeek -> {
                            applyZoneSeek(forward = zone == ZONE_RIGHT)
                        }

                        !currentShowControls -> {
                            currentOnShowControlsChange(true)
                            revealedOnTap = true
                            hidePending = false
                        }

                        else -> {
                            hidePending = true
                            revealedOnTap = false
                        }
                    }
                }
            },
            onSingleTapConfirmed = {
                if (hidePending) currentOnShowControlsChange(false)
                hidePending = false
                revealedOnTap = false
            },
            onDoubleTap = { offset ->
                hidePending = false
                val zone = zoneOf(offset.x)
                if (zone != ZONE_CENTER && revealedOnTap) {
                    currentOnShowControlsChange(false)
                }
                revealedOnTap = false

                when (zone) {
                    ZONE_LEFT -> applyZoneSeek(forward = false)
                    ZONE_RIGHT -> applyZoneSeek(forward = true)
                    else -> togglePlayPause()
                }
            },
            onLongPress = { offset ->
                if (currentLongPressPlaybackSpeed <= 0f) return@detectPlayerTaps

                val bottomExclusionZone = if (currentIsFullscreen) 80f else 120f
                if (offset.y > size.height - bottomExclusionZone) return@detectPlayerTaps

                val manager = EnhancedPlayerManager.getInstance()
                val player = manager.getPlayer()
                if (player != null && !currentIsSpeedBoostActive) {
                    val restoreSpeed =
                        manager.playerState.value.playbackSpeed
                            .takeIf { it > 0f }
                            ?: player.playbackParameters.speed
                    speedBeforeLongPress = restoreSpeed
                    currentOnNormalSpeedChange(restoreSpeed)
                    currentOnSpeedBoostChange(true)
                    manager.setPlaybackSpeed(
                        PlayerSpeedBoost.boostedPlaybackSpeed(
                            currentSpeed = restoreSpeed,
                            targetSpeed = currentLongPressPlaybackSpeed,
                        ),
                    )
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            },
            onLongPressReleased = {
                val restoreSpeed = speedBeforeLongPress
                if (restoreSpeed != null) {
                    EnhancedPlayerManager.getInstance().setPlaybackSpeed(restoreSpeed)
                    currentOnNormalSpeedChange(restoreSpeed)
                    speedBeforeLongPress = null
                    currentOnSpeedBoostChange(false)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
        )
    }
}

/**
 * Tap detection that reports the up event before the double-tap window has resolved.
 *
 * `detectTapGestures` withholds its tap callback until the double-tap timeout expires whenever an
 * `onDoubleTap` is supplied. The player needs the opposite ordering: [onTapUp] runs the instant the
 * finger lifts, so the controls reveal and a running seek accumulation continues without the wait,
 * while [onSingleTapConfirmed] still fires only once the window closes with no second touch.
 */
private suspend fun PointerInputScope.detectPlayerTaps(
    onTapUp: (Offset) -> Unit,
    onSingleTapConfirmed: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    onLongPress: (Offset) -> Unit,
    onLongPressReleased: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)

        val firstUp =
            try {
                withTimeout(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation()
                }
            } catch (_: PointerEventTimeoutCancellationException) {
                onLongPress(down.position)
                waitForUpOrCancellation()
                onLongPressReleased()
                return@awaitEachGesture
            } ?: return@awaitEachGesture

        onTapUp(firstUp.position)

        val secondDown =
            withTimeoutOrNull(viewConfiguration.doubleTapTimeoutMillis) {
                awaitFirstDown(requireUnconsumed = false)
            }
        if (secondDown == null) {
            onSingleTapConfirmed()
            return@awaitEachGesture
        }

        onDoubleTap(secondDown.position)
        waitForUpOrCancellation()
    }
}
