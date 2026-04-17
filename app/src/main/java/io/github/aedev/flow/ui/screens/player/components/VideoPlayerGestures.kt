package io.github.aedev.flow.ui.screens.player.components

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import io.github.aedev.flow.player.EnhancedPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.ui.composed
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

fun Modifier.videoPlayerControls(
    isSpeedBoostActive: Boolean,
    onSpeedBoostChange: (Boolean) -> Unit,
    showControls: Boolean,
    onShowControlsChange: (Boolean) -> Unit,
    onShowSeekBackChange: (Boolean) -> Unit,
    onShowSeekForwardChange: (Boolean) -> Unit,
    onSeekAccumulate: (Int) -> Unit = {},
    currentPosition: Long,
    duration: Long,
    normalSpeed: Float,
    scope: CoroutineScope,
    isFullscreen: Boolean,
    onBrightnessChange: (Float) -> Unit,
    onShowBrightnessChange: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onShowVolumeChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    brightnessLevel: Float,
    volumeLevel: Float,
    maxVolume: Int,
    audioManager: AudioManager?,
    activity: Activity?,
    swipeGesturesEnabled: Boolean = true,
    doubleTapSeekMs: Long = 10_000L,
    onExitFullscreen: (() -> Unit)? = null
): Modifier = composed {
    val currentIsSpeedBoostActive by rememberUpdatedState(isSpeedBoostActive)
    val currentOnSpeedBoostChange by rememberUpdatedState(onSpeedBoostChange)
    val currentShowControls by rememberUpdatedState(showControls)
    val currentOnShowControlsChange by rememberUpdatedState(onShowControlsChange)
    val currentOnShowSeekBackChange by rememberUpdatedState(onShowSeekBackChange)
    val currentOnShowSeekForwardChange by rememberUpdatedState(onShowSeekForwardChange)
    val currentPositionValue by rememberUpdatedState(currentPosition)
    val currentDuration by rememberUpdatedState(duration)
    val currentNormalSpeed by rememberUpdatedState(normalSpeed)
    val currentIsFullscreen by rememberUpdatedState(isFullscreen)
    val currentOnBrightnessChange by rememberUpdatedState(onBrightnessChange)
    val currentOnShowBrightnessChange by rememberUpdatedState(onShowBrightnessChange)
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val currentOnShowVolumeChange by rememberUpdatedState(onShowVolumeChange)
    val currentOnBack by rememberUpdatedState(onBack)
    val currentBrightnessLevel by rememberUpdatedState(brightnessLevel)
    val currentVolumeLevel by rememberUpdatedState(volumeLevel)
    val currentMaxVolume by rememberUpdatedState(maxVolume)
    val currentAudioManager by rememberUpdatedState(audioManager)
    val currentActivity by rememberUpdatedState(activity)
    val currentSwipeGesturesEnabled by rememberUpdatedState(swipeGesturesEnabled)
    val currentDoubleTapSeekMs by rememberUpdatedState(doubleTapSeekMs)
    val currentOnSeekAccumulate by rememberUpdatedState(onSeekAccumulate)
    val currentOnExitFullscreen by rememberUpdatedState(onExitFullscreen)

    var accumulatedForwardMs by remember { mutableStateOf(0L) }
    var accumulatedBackMs by remember { mutableStateOf(0L) }
    var lastForwardTapTime by remember { mutableStateOf(0L) }
    var lastBackTapTime by remember { mutableStateOf(0L) }
    val accumulationWindowMs = 1000L

    this
        .pointerInput(Unit) {
            detectTapGestures(
                onTap = {
                    if (!currentIsSpeedBoostActive) {
                        currentOnShowControlsChange(!currentShowControls)
                    }
                },
                onDoubleTap = { offset ->
                    // Read size fresh on every tap — the player animates from mini to
                    // full while this coroutine is already running, so a pre-captured
                    // `elementSize` would be the halfway-animation size, not screen width.
                    val screenWidth = size.width
                    val tapPosition = offset.x
                    val now = System.currentTimeMillis()

                    val leftThreshold  = screenWidth / 3f
                    val rightThreshold = screenWidth * 2f / 3f

                    if (tapPosition < leftThreshold) {
                        // Seek backward — cancel any in-progress forward animation
                        currentOnShowSeekForwardChange(false)
                        accumulatedForwardMs = 0L
                        lastForwardTapTime = 0L

                        if (now - lastBackTapTime < accumulationWindowMs) {
                            accumulatedBackMs += currentDoubleTapSeekMs
                        } else {
                            accumulatedBackMs = currentDoubleTapSeekMs
                        }
                        lastBackTapTime = now
                        currentOnSeekAccumulate(-(accumulatedBackMs / 1000L).toInt())
                        currentOnShowSeekBackChange(true)
                        val actualBackBase = EnhancedPlayerManager.getInstance().getPlayer()?.currentPosition
                            ?: currentPositionValue
                        EnhancedPlayerManager.getInstance().seekTo(
                            (actualBackBase - currentDoubleTapSeekMs).coerceAtLeast(0)
                        )
                    } else if (tapPosition > rightThreshold) {
                        // Seek forward — cancel any in-progress backward animation
                        currentOnShowSeekBackChange(false)
                        accumulatedBackMs = 0L
                        lastBackTapTime = 0L

                        if (now - lastForwardTapTime < accumulationWindowMs) {
                            accumulatedForwardMs += currentDoubleTapSeekMs
                        } else {
                            accumulatedForwardMs = currentDoubleTapSeekMs
                        }
                        lastForwardTapTime = now
                        currentOnSeekAccumulate((accumulatedForwardMs / 1000L).toInt())
                        currentOnShowSeekForwardChange(true)
                        val actualForwardBase = EnhancedPlayerManager.getInstance().getPlayer()?.currentPosition
                            ?: currentPositionValue
                        EnhancedPlayerManager.getInstance().seekTo(
                            (actualForwardBase + currentDoubleTapSeekMs).coerceAtMost(currentDuration)
                        )
                    } else {
                        // Center double tap - play/pause
                        val player = EnhancedPlayerManager.getInstance().getPlayer()
                        if (player != null) {
                            if (player.isPlaying) {
                                EnhancedPlayerManager.getInstance().pause()
                            } else {
                                EnhancedPlayerManager.getInstance().play()
                            }
                        }
                    }
                },
                onLongPress = { offset ->
                    val screenWidth = size.width
                    val tapPosition = offset.x
                    
                    if (tapPosition < screenWidth * 0.2f || tapPosition > screenWidth * 0.8f) {
                    }
                    
                    val player = EnhancedPlayerManager.getInstance().getPlayer()
                    if (player != null && !currentIsSpeedBoostActive) {
                        currentOnSpeedBoostChange(true)
                        player.setPlaybackSpeed(2.0f)
                    }
                },
                onPress = { offset ->
                    tryAwaitRelease()
                    if (currentIsSpeedBoostActive) {
                        val player = EnhancedPlayerManager.getInstance().getPlayer()
                        player?.setPlaybackSpeed(currentNormalSpeed)
                        currentOnSpeedBoostChange(false)
                    }
                }
            )
        }
        .pointerInput(currentIsFullscreen) {
            var totalDragY = 0f
            var totalDragX = 0f
            var isDraggingVertical = false
            var shouldIgnoreGesture = false 
            val dragThreshold = 20f 
            val edgeIgnoreThreshold = 120f
            var startTouchX = 0f
            var isCenterZone = false
            var exitDragAccum = 0f

            if (currentIsFullscreen) {
                detectDragGestures(
                    onDragStart = { offset ->
                        totalDragY = 0f
                        totalDragX = 0f
                        isDraggingVertical = false
                        exitDragAccum = 0f
                        
                        val distanceFromTop = offset.y
                        val distanceFromBottom = size.height - offset.y
                        
                        shouldIgnoreGesture = distanceFromTop < edgeIgnoreThreshold || 
                                             distanceFromBottom < edgeIgnoreThreshold
                        
                        if (shouldIgnoreGesture) return@detectDragGestures

                        startTouchX = offset.x
                        val screenWidth = size.width
                        isCenterZone = startTouchX > screenWidth * 0.33f && startTouchX < screenWidth * 0.67f
                        val isEdge = offset.x < screenWidth * 0.2f || offset.x > screenWidth * 0.8f
                        
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
                             } else if (screenHeight > 0 && currentSwipeGesturesEnabled) {
                                 if (dragPosition < screenWidth / 2) {
                                     // Left side - brightness
                                     val sensitivity = 1.5f 
                                     val delta = -dragAmount.y / screenHeight * sensitivity
                                     
                                     val startLevel = if (currentBrightnessLevel < 0) 0f else currentBrightnessLevel
                                     val rawNewLevel = startLevel + delta
                                     
                                     // Auto brightness logic: if dragging down past -5%
                                     val newBrightness = if (rawNewLevel < -0.05f) {
                                         -1.0f // Auto mode
                                     } else {
                                         rawNewLevel.coerceIn(0f, 1f)
                                     }
                                     
                                     currentOnBrightnessChange(newBrightness)
                                     
                                     try {
                                         currentActivity?.window?.let { window ->
                                            val layoutParams = window.attributes
                                            layoutParams.screenBrightness = newBrightness
                                            window.attributes = layoutParams
                                         }
                                     } catch (e: Exception) {}
                                     currentOnShowBrightnessChange(true)
                                 } else {
                                     // Right side - volume
                                     val sensitivity = 1.5f
                                     val delta = -dragAmount.y / screenHeight * sensitivity
                                     
                                     val newVolumeLevel = (currentVolumeLevel + delta).coerceIn(0f, 2.0f)
                                     currentOnVolumeChange(newVolumeLevel)
                                     
                                     if (newVolumeLevel <= 1.0f) {
                                         val newVolume = (newVolumeLevel * currentMaxVolume).toInt()
                                         currentAudioManager?.setStreamVolume(
                                             AudioManager.STREAM_MUSIC,
                                             newVolume,
                                             0
                                         )
                                     }
                                     currentOnShowVolumeChange(true)
                                 }
                             }
                        }
                    }
                )
            }
        }
}