package io.github.aedev.flow.ui.screens.player.components

import android.app.Activity
import android.media.AudioManager
import android.os.SystemClock
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.media3.common.Player
import io.github.aedev.flow.player.EnhancedPlayerManager
import kotlinx.coroutines.CoroutineScope
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
    brightnessLevel: () -> Float,
    volumeLevel: () -> Float,
    maxVolume: Int,
    audioManager: AudioManager?,
    activity: Activity?,
    brightnessSwipeGesturesEnabled: Boolean = true,
    volumeSwipeGesturesEnabled: Boolean = true,
    allowVolumeBoost: Boolean = false,
    doubleTapSeekMs: Long = 10_000L,
    longPressPlaybackSpeed: Float = 2.0f,
    onExitFullscreen: (() -> Unit)? = null,
    isSeekForwardActive: Boolean = false,
    isSeekBackActive: Boolean = false,
): Modifier =
    composed {
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
        val currentBrightnessLevel by rememberUpdatedState(brightnessLevel)
        val currentVolumeLevel by rememberUpdatedState(volumeLevel)
        val currentMaxVolume by rememberUpdatedState(maxVolume)
        val currentAudioManager by rememberUpdatedState(audioManager)
        val currentActivity by rememberUpdatedState(activity)
        val currentBrightnessSwipeGesturesEnabled by rememberUpdatedState(brightnessSwipeGesturesEnabled)
        val currentVolumeSwipeGesturesEnabled by rememberUpdatedState(volumeSwipeGesturesEnabled)
        val currentAllowVolumeBoost by rememberUpdatedState(allowVolumeBoost)
        val currentDoubleTapSeekMs by rememberUpdatedState(doubleTapSeekMs)
        val currentLongPressPlaybackSpeed by rememberUpdatedState(longPressPlaybackSpeed)
        val currentOnSeekAccumulate by rememberUpdatedState(onSeekAccumulate)
        val currentOnExitFullscreen by rememberUpdatedState(onExitFullscreen)
        val currentIsSeekForwardActive by rememberUpdatedState(isSeekForwardActive)
        val currentIsSeekBackActive by rememberUpdatedState(isSeekBackActive)

        val haptics = LocalHapticFeedback.current

        val lastBrightnessApplied = remember { floatArrayOf(-2f) }
        val lastBrightnessAppliedAt = remember { longArrayOf(0L) }

        this
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
                            manager.setPlaybackSpeed(currentLongPressPlaybackSpeed.coerceIn(0.1f, 4.0f))
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
                var totalDragY = 0f
                var totalDragX = 0f
                var isDraggingVertical = false
                var shouldIgnoreGesture = false
                val dragThreshold = 20f
                val edgeIgnoreThreshold = 120f
                var startTouchX = 0f
                var isCenterZone = false
                var exitDragAccum = 0f
                var lastVolumeStep = -1
                var lastBrightnessEdge = 0

                if (currentIsFullscreen) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            totalDragY = 0f
                            totalDragX = 0f
                            isDraggingVertical = false
                            exitDragAccum = 0f
                            lastVolumeStep = -1
                            lastBrightnessEdge = 0

                            val distanceFromTop = offset.y
                            val distanceFromBottom = size.height - offset.y

                            shouldIgnoreGesture = distanceFromTop < edgeIgnoreThreshold ||
                                distanceFromBottom < edgeIgnoreThreshold

                            if (shouldIgnoreGesture) return@detectDragGestures

                            startTouchX = offset.x
                            val screenWidth = size.width
                            isCenterZone = startTouchX > screenWidth * 0.33f && startTouchX < screenWidth * 0.67f
                        },
                        onDragEnd = {
                            shouldIgnoreGesture = false
                            if (isCenterZone && exitDragAccum > 80f) {
                                currentOnExitFullscreen?.invoke()
                            }
                            isCenterZone = false
                            exitDragAccum = 0f
                            scope.launch {
                                delay(500) // Delay hiding controls
                                currentOnShowBrightnessChange(false)
                                currentOnShowVolumeChange(false)
                            }
                            isDraggingVertical = false
                        },
                        onDragCancel = {
                            shouldIgnoreGesture = false
                            isCenterZone = false
                            exitDragAccum = 0f
                            scope.launch {
                                currentOnShowBrightnessChange(false)
                                currentOnShowVolumeChange(false)
                            }
                            isDraggingVertical = false
                        },
                        onDrag = { change, dragAmount ->
                            if (shouldIgnoreGesture) return@detectDragGestures

                            change.consume()
                            totalDragX += dragAmount.x
                            totalDragY += dragAmount.y

                            if (!isDraggingVertical) {
                                if (abs(totalDragY) > dragThreshold && abs(totalDragY) > abs(totalDragX)) {
                                    isDraggingVertical = true
                                }
                            }

                            if (isDraggingVertical) {
                                val screenHeight = size.height.toFloat()
                                val screenWidth = size.width
                                val dragPosition = change.position.x

                                if (isCenterZone) {
                                    if (dragAmount.y > 0) {
                                        exitDragAccum += dragAmount.y
                                    }
                                } else if (screenHeight > 0) {
                                    if (dragPosition < screenWidth / 2 && currentBrightnessSwipeGesturesEnabled) {
                                        // Left side - brightness
                                        val sensitivity = 1.5f
                                        val delta = -dragAmount.y / screenHeight * sensitivity

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
                                    } else if (dragPosition >= screenWidth / 2 && currentVolumeSwipeGesturesEnabled) {
                                        // Right side - volume
                                        val sensitivity = 1.5f
                                        val delta = -dragAmount.y / screenHeight * sensitivity

                                        val ceiling = if (currentAllowVolumeBoost) 2.0f else 1.0f
                                        val newVolumeLevel = (currentVolumeLevel() + delta).coerceIn(0f, ceiling)
                                        currentOnVolumeChange(newVolumeLevel)

                                        if (newVolumeLevel <= 1.0f) {
                                            val newVolume = (newVolumeLevel * currentMaxVolume).toInt()
                                            currentAudioManager?.setStreamVolume(
                                                AudioManager.STREAM_MUSIC,
                                                newVolume,
                                                0,
                                            )
                                            if (newVolume != lastVolumeStep) {
                                                if (lastVolumeStep >= 0) haptics.playerTick()
                                                lastVolumeStep = newVolume
                                            }
                                        }
                                        currentOnShowVolumeChange(true)
                                    }
                                }
                            }
                        },
                    )
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
