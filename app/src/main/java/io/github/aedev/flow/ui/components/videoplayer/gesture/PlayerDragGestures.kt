package io.github.aedev.flow.ui.components.videoplayer.gesture

import android.app.Activity
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import io.github.aedev.flow.player.EnhancedPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val SEEK_DRAG_SPAN_MS = 90_000L

/** Target movement between haptic ticks while dragging to seek. */
private const val SEEK_DRAG_HAPTIC_STEP_MS = 5_000L

/** Distance from the top and bottom edges where drags are left to the controls beneath them. */
private const val DRAG_EDGE_IGNORE_PX = 120f

/** Downward travel in the centre zone that commits to leaving fullscreen. */
private const val EXIT_FULLSCREEN_DRAG_PX = 80f

private const val EXIT_FULLSCREEN_OVERSHOOT_PX = 140f

private const val VERTICAL_DRAG_SENSITIVITY = 1.5f

private fun resistedTravel(
    distance: Float,
    limit: Float,
): Float = limit * distance / (limit + distance)

internal fun Modifier.playerDragGestures(
    currentPosition: State<() -> Long>,
    duration: State<Long>,
    scope: CoroutineScope,
    isFullscreen: State<Boolean>,
    onBrightnessChange: State<(Float) -> Unit>,
    onShowBrightnessChange: State<(Boolean) -> Unit>,
    onVolumeChange: State<(Float) -> Unit>,
    onShowVolumeChange: State<(Boolean) -> Unit>,
    onSeekDragChange: State<(Boolean) -> Unit>,
    onSeekDragUpdate: State<(targetMs: Long, deltaMs: Long) -> Unit>,
    brightnessLevel: State<() -> Float>,
    volumeLevel: State<() -> Float>,
    maxVolume: State<Int>,
    audioManager: State<AudioManager?>,
    activity: State<Activity?>,
    brightnessSwipeGesturesEnabled: State<Boolean>,
    volumeSwipeGesturesEnabled: State<Boolean>,
    seekSwipeGesturesEnabled: State<Boolean>,
    allowVolumeBoost: State<Boolean>,
    onExitFullscreen: State<(() -> Unit)?>,
    onExitFullscreenDrag: State<(offsetPx: Float, progress: Float) -> Unit>,
    haptics: HapticFeedback,
    lastBrightnessApplied: FloatArray,
    lastBrightnessAppliedAt: LongArray,
): Modifier {
    val currentPositionProvider by currentPosition
    val currentDuration by duration
    val currentIsFullscreen by isFullscreen
    val currentOnBrightnessChange by onBrightnessChange
    val currentOnShowBrightnessChange by onShowBrightnessChange
    val currentOnVolumeChange by onVolumeChange
    val currentOnShowVolumeChange by onShowVolumeChange
    val currentOnSeekDragChange by onSeekDragChange
    val currentOnSeekDragUpdate by onSeekDragUpdate
    val currentBrightnessLevel by brightnessLevel
    val currentVolumeLevel by volumeLevel
    val currentMaxVolume by maxVolume
    val currentAudioManager by audioManager
    val currentActivity by activity
    val currentBrightnessSwipeGesturesEnabled by brightnessSwipeGesturesEnabled
    val currentVolumeSwipeGesturesEnabled by volumeSwipeGesturesEnabled
    val currentSeekSwipeGesturesEnabled by seekSwipeGesturesEnabled
    val currentAllowVolumeBoost by allowVolumeBoost
    val currentOnExitFullscreen by onExitFullscreen
    val currentOnExitFullscreenDrag by onExitFullscreenDrag

    return this.pointerInput(currentIsFullscreen) {
        if (!currentIsFullscreen) return@pointerInput

        var isCenterZone = false
        var exitDragTravel = 0f
        var exitDragPastCommit = false
        var exitSettleJob: Job? = null
        var lastVolumeStep = -1
        var lastBrightnessEdge = 0

        var seekDragStarted = false
        var seekDragBaseMs = 0L
        var seekDragTargetMs = 0L
        var seekDragTravelPx = 0f
        var lastSeekHapticMs = 0L

        fun applyBrightnessDrag(dy: Float) {
            val screenHeight = size.height.toFloat()
            if (screenHeight <= 0f) return

            val delta = -dy / screenHeight * VERTICAL_DRAG_SENSITIVITY
            val level = currentBrightnessLevel()
            val startLevel = if (level < 0) 0f else level
            val rawNewLevel = startLevel + delta

            // Auto brightness logic: if dragging down past -5%
            val newBrightness =
                if (rawNewLevel < -0.05f) {
                    -1.0f // Auto mode
                } else {
                    rawNewLevel.coerceIn(0f, 1f)
                }

            currentOnBrightnessChange(newBrightness)

            val edge =
                when {
                    newBrightness < 0f -> -1
                    newBrightness >= 1f -> 1
                    else -> 0
                }
            if (edge != lastBrightnessEdge) {
                if (edge != 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                lastBrightnessEdge = edge
            }

            val now = SystemClock.uptimeMillis()
            val brightnessDelta = abs(newBrightness - lastBrightnessApplied[0])
            val timeDelta = now - lastBrightnessAppliedAt[0]
            // Apply window brightness only when the change is perceptible
            // or 16 ms has elapsed; this keeps WindowManager relayouts off
            // every drag tick so the video pipeline doesn't drop frames.
            if (brightnessDelta > 0.004f || timeDelta >= 16L) {
                try {
                    currentActivity?.window?.let { window ->
                        val layoutParams = window.attributes
                        layoutParams.screenBrightness = newBrightness
                        window.attributes = layoutParams
                    }
                    lastBrightnessApplied[0] = newBrightness
                    lastBrightnessAppliedAt[0] = now
                } catch (e: Exception) {
                }
            }
            currentOnShowBrightnessChange(true)
        }

        fun applyVolumeDrag(dy: Float) {
            val screenHeight = size.height.toFloat()
            if (screenHeight <= 0f) return

            val delta = -dy / screenHeight * VERTICAL_DRAG_SENSITIVITY
            val ceiling = if (currentAllowVolumeBoost) 2.0f else 1.0f
            val newVolumeLevel = (currentVolumeLevel() + delta).coerceIn(0f, ceiling)
            currentOnVolumeChange(newVolumeLevel)

            if (newVolumeLevel <= 1.0f) {
                val newVolume = (newVolumeLevel * currentMaxVolume).toInt()
                currentAudioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
                if (newVolume != lastVolumeStep) {
                    if (lastVolumeStep >= 0) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastVolumeStep = newVolume
                }
            }
            currentOnShowVolumeChange(true)
        }

        fun publishExitDrag() {
            val offset =
                if (exitDragTravel > EXIT_FULLSCREEN_DRAG_PX) {
                    EXIT_FULLSCREEN_DRAG_PX +
                        resistedTravel(
                            exitDragTravel - EXIT_FULLSCREEN_DRAG_PX,
                            EXIT_FULLSCREEN_OVERSHOOT_PX,
                        )
                } else {
                    exitDragTravel
                }
            currentOnExitFullscreenDrag(
                offset,
                (exitDragTravel / EXIT_FULLSCREEN_DRAG_PX).coerceAtMost(1f),
            )
        }

        fun applyExitDrag(dy: Float) {
            exitSettleJob?.cancel()
            exitDragTravel = (exitDragTravel + dy).coerceAtLeast(0f)

            val pastCommit = exitDragTravel >= EXIT_FULLSCREEN_DRAG_PX
            if (pastCommit != exitDragPastCommit) {
                exitDragPastCommit = pastCommit
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            publishExitDrag()
        }

        fun endExitDrag(commit: Boolean) {
            val exiting = commit && exitDragPastCommit
            exitDragPastCommit = false

            if (exiting) {
                exitSettleJob?.cancel()
                exitDragTravel = 0f
                publishExitDrag()
                currentOnExitFullscreen?.invoke()
                return
            }
            if (exitDragTravel == 0f || exitSettleJob?.isActive == true) return

            val from = exitDragTravel
            exitSettleJob =
                scope.launch {
                    animate(
                        initialValue = from,
                        targetValue = 0f,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMediumLow,
                            ),
                    ) { travel, _ ->
                        exitDragTravel = travel
                        publishExitDrag()
                    }
                }
        }

        fun beginSeekDrag() {
            val manager = EnhancedPlayerManager.getInstance()
            seekDragBaseMs = manager.getPlayer()?.currentPosition ?: currentPositionProvider()
            seekDragTargetMs = seekDragBaseMs
            lastSeekHapticMs = seekDragBaseMs
            seekDragTravelPx = 0f
            currentOnSeekDragUpdate(seekDragTargetMs, 0L)
            currentOnSeekDragChange(true)
        }

        fun updateSeekDrag(dx: Float) {
            val width = size.width.toFloat()
            if (width <= 0f || currentDuration <= 0L) return

            seekDragTravelPx += dx
            val spanMs = minOf(SEEK_DRAG_SPAN_MS, currentDuration)
            val rawTarget = seekDragBaseMs + (seekDragTravelPx / width * spanMs).toLong()
            val clamped = rawTarget.coerceIn(0L, currentDuration)
            if (clamped != rawTarget) {
                seekDragTravelPx = (clamped - seekDragBaseMs).toFloat() / spanMs * width
            }
            seekDragTargetMs = clamped

            currentOnSeekDragUpdate(clamped, clamped - seekDragBaseMs)

            if (abs(clamped - lastSeekHapticMs) >= SEEK_DRAG_HAPTIC_STEP_MS) {
                lastSeekHapticMs = clamped
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }

        fun endSeekDrag(commit: Boolean) {
            if (commit && seekDragTargetMs != seekDragBaseMs) {
                val manager = EnhancedPlayerManager.getInstance()
                val player = manager.getPlayer()
                val isLive = manager.playerState.value.isLive || player?.isCurrentMediaItemLive == true
                if (isLive) {
                    manager.seekToLiveTimeline(seekDragTargetMs)
                } else {
                    manager.seekTo(seekDragTargetMs)
                }
            }
            seekDragStarted = false
            currentOnSeekDragChange(false)
        }

        try {
            detectPlayerDrags(
                onDragStart = { offset ->
                    lastVolumeStep = -1
                    lastBrightnessEdge = 0
                    seekDragStarted = false

                    val nearEdge =
                        offset.y < DRAG_EDGE_IGNORE_PX ||
                            size.height - offset.y < DRAG_EDGE_IGNORE_PX
                    if (nearEdge) {
                        false
                    } else {
                        val width = size.width
                        isCenterZone = offset.x > width * 0.33f && offset.x < width * 0.67f
                        true
                    }
                },
                onAxisAccepted = { axis ->
                    when (axis) {
                        PlayerDragAxis.HORIZONTAL -> currentSeekSwipeGesturesEnabled && currentDuration > 0L
                        PlayerDragAxis.VERTICAL -> true
                    }
                },
                onDrag = { change, delta, axis ->
                    when (axis) {
                        PlayerDragAxis.HORIZONTAL -> {
                            if (!seekDragStarted) {
                                seekDragStarted = true
                                beginSeekDrag()
                            }
                            updateSeekDrag(delta.x)
                        }

                        PlayerDragAxis.VERTICAL -> {
                            val width = size.width
                            when {
                                isCenterZone -> {
                                    applyExitDrag(delta.y)
                                }

                                change.position.x < width / 2 -> {
                                    if (currentBrightnessSwipeGesturesEnabled) applyBrightnessDrag(delta.y)
                                }

                                else -> {
                                    if (currentVolumeSwipeGesturesEnabled) applyVolumeDrag(delta.y)
                                }
                            }
                        }
                    }
                },
                onDragEnd = { axis ->
                    when (axis) {
                        PlayerDragAxis.HORIZONTAL -> {
                            if (seekDragStarted) endSeekDrag(commit = true)
                            endExitDrag(commit = false)
                        }

                        PlayerDragAxis.VERTICAL -> {
                            endExitDrag(commit = isCenterZone)
                            scope.launch {
                                delay(500) // Delay hiding controls
                                currentOnShowBrightnessChange(false)
                                currentOnShowVolumeChange(false)
                            }
                        }
                    }
                    isCenterZone = false
                },
                onDragCancel = { axis ->
                    endExitDrag(commit = false)
                    when (axis) {
                        PlayerDragAxis.HORIZONTAL -> {
                            if (seekDragStarted) endSeekDrag(commit = false)
                        }

                        PlayerDragAxis.VERTICAL -> {
                            scope.launch {
                                currentOnShowBrightnessChange(false)
                                currentOnShowVolumeChange(false)
                            }
                        }
                    }
                    isCenterZone = false
                },
            )
        } finally {
            // A system takeover (the status bar sliding in over the video) cancels this
            // coroutine outright, so neither onDragEnd nor onDragCancel runs (#906).
            exitSettleJob?.cancel()
            currentOnExitFullscreenDrag(0f, 0f)
            if (seekDragStarted) currentOnSeekDragChange(false)
        }
    }
}

internal enum class PlayerDragAxis { HORIZONTAL, VERTICAL }

/**
 * Axis-locked drag detection.
 *
 * `detectDragGestures` cannot express this: it consumes the pointer the moment any drag begins, so
 * it claimed horizontal movement the player had no use for, and it resolved the axis from a running
 * total that could flip mid-gesture. Here the axis is decided once, at the instant touch slop is
 * crossed, and the pointer is only consumed when [onAxisAccepted] wants that axis — a drag we do
 * not handle stays unconsumed and remains available to the parent (the draggable player sheet).
 *
 * [onDragStart] returns false to ignore the gesture entirely, which is how the edge exclusion zones
 * keep their hands off the controls sitting under them.
 */
private suspend fun PointerInputScope.detectPlayerDrags(
    onDragStart: (Offset) -> Boolean,
    onAxisAccepted: (PlayerDragAxis) -> Boolean,
    onDrag: (change: PointerInputChange, delta: Offset, axis: PlayerDragAxis) -> Unit,
    onDragEnd: (PlayerDragAxis) -> Unit,
    onDragCancel: (PlayerDragAxis) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!onDragStart(down.position)) return@awaitEachGesture

        var axis: PlayerDragAxis? = null
        val dragStart =
            awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                val candidate =
                    if (abs(overSlop.x) > abs(overSlop.y)) {
                        PlayerDragAxis.HORIZONTAL
                    } else {
                        PlayerDragAxis.VERTICAL
                    }
                // Consuming is what claims the gesture. Leaving it unconsumed makes
                // awaitTouchSlopOrCancellation keep waiting, so a rejected axis neither steals the
                // pointer nor ends the gesture.
                if (onAxisAccepted(candidate)) {
                    axis = candidate
                    change.consume()
                }
            } ?: return@awaitEachGesture

        val lockedAxis = axis ?: return@awaitEachGesture

        val completed =
            drag(dragStart.id) { change ->
                onDrag(change, change.positionChange(), lockedAxis)
                change.consume()
            }

        if (completed) onDragEnd(lockedAxis) else onDragCancel(lockedAxis)
    }
}
