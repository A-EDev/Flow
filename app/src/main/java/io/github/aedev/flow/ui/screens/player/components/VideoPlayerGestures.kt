package io.github.aedev.flow.ui.screens.player.components

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
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.media3.common.Player
import io.github.aedev.flow.player.EnhancedPlayerManager
import io.github.aedev.flow.ui.screens.player.util.VideoPlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** How long a further tap in the same zone keeps adding to the running double-tap seek total. */
private const val SEEK_ACCUMULATION_WINDOW_MS = 1_000L

/** Screen fraction on each side that maps to a seek zone; the middle third is play/pause. */
private const val SEEK_ZONE_FRACTION = 1f / 3f

private const val ZONE_LEFT = -1
private const val ZONE_CENTER = 0
private const val ZONE_RIGHT = 1

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

@Composable
fun Modifier.videoPlayerControls(
    isSpeedBoostActive: Boolean,
    onSpeedBoostChange: (Boolean) -> Unit,
    showControls: Boolean,
    onShowControlsChange: (Boolean) -> Unit,
    onShowSeekBackChange: (Boolean) -> Unit,
    onShowSeekForwardChange: (Boolean) -> Unit,
    onSeekAccumulate: (Int) -> Unit = {},
    currentPosition: () -> Long,
    duration: Long,
    onNormalSpeedChange: (Float) -> Unit = {},
    scope: CoroutineScope,
    isFullscreen: Boolean,
    onBrightnessChange: (Float) -> Unit,
    onShowBrightnessChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onShowVolumeChange: (Boolean) -> Unit,
    onSeekDragChange: (Boolean) -> Unit = {},
    onSeekDragUpdate: (targetMs: Long, deltaMs: Long) -> Unit = { _, _ -> },
    brightnessLevel: () -> Float,
    volumeLevel: () -> Float,
    maxVolume: Int,
    audioManager: AudioManager?,
    activity: Activity?,
    brightnessSwipeGesturesEnabled: Boolean = true,
    volumeSwipeGesturesEnabled: Boolean = true,
    seekSwipeGesturesEnabled: Boolean = true,
    allowVolumeBoost: Boolean = false,
    doubleTapSeekMs: Long = 10_000L,
    longPressPlaybackSpeed: Float = 2.0f,
    onExitFullscreen: (() -> Unit)? = null,
    onExitFullscreenDrag: (offsetPx: Float, progress: Float) -> Unit = { _, _ -> },
    isSeekForwardActive: Boolean = false,
    isSeekBackActive: Boolean = false,
): Modifier {
    val currentIsSpeedBoostActive by rememberUpdatedState(isSpeedBoostActive)
    val currentOnSpeedBoostChange by rememberUpdatedState(onSpeedBoostChange)
    val currentShowControls by rememberUpdatedState(showControls)
    val currentOnShowControlsChange by rememberUpdatedState(onShowControlsChange)
    val currentOnShowSeekBackChange by rememberUpdatedState(onShowSeekBackChange)
    val currentOnShowSeekForwardChange by rememberUpdatedState(onShowSeekForwardChange)
    val currentPositionProvider by rememberUpdatedState(currentPosition)
    val currentDuration by rememberUpdatedState(duration)
    val currentOnNormalSpeedChange by rememberUpdatedState(onNormalSpeedChange)
    val currentIsFullscreen by rememberUpdatedState(isFullscreen)
    val currentOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val currentOnShowBrightnessChange by rememberUpdatedState(onShowBrightnessChange)
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val currentOnShowVolumeChange by rememberUpdatedState(onShowVolumeChange)
    val currentOnSeekDragChange by rememberUpdatedState(onSeekDragChange)
    val currentOnSeekDragUpdate by rememberUpdatedState(onSeekDragUpdate)
    val currentBrightnessLevel by rememberUpdatedState(brightnessLevel)
    val currentVolumeLevel by rememberUpdatedState(volumeLevel)
    val currentMaxVolume by rememberUpdatedState(maxVolume)
    val currentAudioManager by rememberUpdatedState(audioManager)
    val currentActivity by rememberUpdatedState(activity)
    val currentBrightnessSwipeGesturesEnabled by rememberUpdatedState(brightnessSwipeGesturesEnabled)
    val currentVolumeSwipeGesturesEnabled by rememberUpdatedState(volumeSwipeGesturesEnabled)
    val currentSeekSwipeGesturesEnabled by rememberUpdatedState(seekSwipeGesturesEnabled)
    val currentAllowVolumeBoost by rememberUpdatedState(allowVolumeBoost)
    val currentDoubleTapSeekMs by rememberUpdatedState(doubleTapSeekMs)
    val currentLongPressPlaybackSpeed by rememberUpdatedState(longPressPlaybackSpeed)
    val currentOnSeekAccumulate by rememberUpdatedState(onSeekAccumulate)
    val currentOnExitFullscreen by rememberUpdatedState(onExitFullscreen)
    val currentOnExitFullscreenDrag by rememberUpdatedState(onExitFullscreenDrag)
    val currentIsSeekForwardActive by rememberUpdatedState(isSeekForwardActive)
    val currentIsSeekBackActive by rememberUpdatedState(isSeekBackActive)

    val haptics = LocalHapticFeedback.current

    val lastBrightnessApplied = remember { floatArrayOf(-2f) }
    val lastBrightnessAppliedAt = remember { longArrayOf(0L) }

    return this
        .pointerInput(Unit) {
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
                haptics.playerTick()
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
                            VideoPlayerUtils.boostedPlaybackSpeed(
                                currentSpeed = restoreSpeed,
                                targetSpeed = currentLongPressPlaybackSpeed,
                            ),
                        )
                        haptics.playerPress()
                    }
                },
                onLongPressReleased = {
                    val restoreSpeed = speedBeforeLongPress
                    if (restoreSpeed != null) {
                        EnhancedPlayerManager.getInstance().setPlaybackSpeed(restoreSpeed)
                        currentOnNormalSpeedChange(restoreSpeed)
                        speedBeforeLongPress = null
                        currentOnSpeedBoostChange(false)
                        haptics.playerTick()
                    }
                },
            )
        }.pointerInput(currentIsFullscreen) {
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
                    if (edge != 0) haptics.playerTick()
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
                        if (lastVolumeStep >= 0) haptics.playerTick()
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
                    haptics.playerTick()
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
                    haptics.playerTick()
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
