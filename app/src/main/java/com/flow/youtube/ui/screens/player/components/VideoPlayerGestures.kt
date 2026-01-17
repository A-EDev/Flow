package com.flow.youtube.ui.screens.player.components

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntSize
import com.flow.youtube.player.EnhancedPlayerManager
import com.flow.youtube.player.GlobalPlayerState
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
    activity: Activity?
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

    this
        .pointerInput(Unit) {
            val elementSize = size
            detectTapGestures(
                onTap = {
                    if (!currentIsSpeedBoostActive) {
                        currentOnShowControlsChange(!currentShowControls)
                    }
                },
                onDoubleTap = { offset ->
                    val screenWidth = elementSize.width
                    val tapPosition = offset.x
                    
                    if (tapPosition < screenWidth * 0.4f) {
                        // Seek backward 10s
                        currentOnShowSeekBackChange(true)
                        EnhancedPlayerManager.getInstance().seekTo(
                            (currentPositionValue - 10000).coerceAtLeast(0)
                        )
                    } else if (tapPosition > screenWidth * 0.6f) {
                        // Seek forward 10s
                        currentOnShowSeekForwardChange(true)
                        EnhancedPlayerManager.getInstance().seekTo(
                            (currentPositionValue + 10000).coerceAtMost(currentDuration)
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
                    val screenWidth = elementSize.width
                    val tapPosition = offset.x
                    
                    // Allow speed boost in center area or edges if determined
                    // Restricting usage to avoid conflict with edge swipes?
                    // Let's keep it mostly everywhere but edges
                    if (tapPosition < screenWidth * 0.2f || tapPosition > screenWidth * 0.8f) {
                         // Edge areas - maybe ignore to prevent conflict?
                         // Actually original code was: < 0.3 or > 0.7 used for speed boost? 
                         // No, original was: IF (tapPosition < 0.3 || tapPosition > 0.7) -> SpeedBoost. 
                         // That's weird. Usually speed boost is center hold.
                         // User asked to "Improve gestures". Speed boost on center hold is standard (like YouTube).
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
            val elementSize = size
            var totalDragY = 0f
            var totalDragX = 0f
            var isDraggingVertical = false
            var isDraggingHorizontal = false
            val dragThreshold = 50f // Lower threshold for better responsiveness

            if (currentIsFullscreen) {
                detectDragGestures(
                    onDragStart = { offset ->
                        totalDragY = 0f
                        totalDragX = 0f
                        isDraggingVertical = false
                        isDraggingHorizontal = false
                        
                        val screenWidth = elementSize.width
                        val isEdge = offset.x < screenWidth * 0.2f || offset.x > screenWidth * 0.8f
                        
                        // Hint visuals if needed
                    },
                    onDragEnd = {
                        scope.launch {
                            delay(500) // Delay hiding controls
                            currentOnShowBrightnessChange(false)
                            currentOnShowVolumeChange(false)
                        }
                        isDraggingVertical = false
                        isDraggingHorizontal = false
                    },
                    onDragCancel = {
                        scope.launch {
                            currentOnShowBrightnessChange(false)
                            currentOnShowVolumeChange(false)
                        }
                        isDraggingVertical = false
                        isDraggingHorizontal = false
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y
                        
                        // Determine direction if not yet established
                        if (!isDraggingVertical && !isDraggingHorizontal) {
                            if (abs(totalDragY) > dragThreshold && abs(totalDragY) > abs(totalDragX)) {
                                isDraggingVertical = true
                            } else if (abs(totalDragX) > dragThreshold && abs(totalDragX) > abs(totalDragY)) {
                                isDraggingHorizontal = true
                            }
                        }
                        
                        if (isDraggingVertical) {
                             val screenHeight = elementSize.height.toFloat()
                             val screenWidth = elementSize.width
                             val dragPosition = change.position.x
                             
                             if (screenHeight > 0) {
                                 if (dragPosition < screenWidth / 2) {
                                     // Left side - brightness
                                     // Invert dragAmount for natural scrolling (Up = increase brightness)
                                     // dy is positive when going down. So -dy to increase.
                                     val sensitivity = 1.5f // Multiplier
                                     val delta = -dragAmount.y / screenHeight * sensitivity
                                     
                                     val newBrightness = (currentBrightnessLevel + delta).coerceIn(0f, 1f)
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
                                     
                                     val newVolumeLevel = (currentVolumeLevel + delta).coerceIn(0f, 1f)
                                     currentOnVolumeChange(newVolumeLevel)
                                     
                                     val newVolume = (newVolumeLevel * currentMaxVolume).toInt()
                                     currentAudioManager?.setStreamVolume(
                                         AudioManager.STREAM_MUSIC,
                                         newVolume,
                                         0
                                     )
                                     currentOnShowVolumeChange(true)
                                 }
                             }
                        } else if (isDraggingHorizontal) {
                            // Horizontal Seek (Swipe)
                            // Every X pixels -> Seek Y seconds
                            // Simple implementation: Threshold based triggering
                            // Or accumulating
                            
                            val seekSensitivity = 100f // pixels per seek step
                            
                            if (totalDragX > seekSensitivity) {
                                // Right - Forward
                                currentOnShowSeekForwardChange(true)
                                EnhancedPlayerManager.getInstance().seekTo(
                                    (currentPositionValue + 5000).coerceAtMost(currentDuration)
                                )
                                totalDragX = 0f // Reset to require more drag for next seek
                            } else if (totalDragX < -seekSensitivity) {
                                // Left - Backward
                                currentOnShowSeekBackChange(true)
                                EnhancedPlayerManager.getInstance().seekTo(
                                    (currentPositionValue - 5000).coerceAtLeast(0)
                                )
                                totalDragX = 0f
                            }
                        }
                    }
                )
            } else {
                // Non-fullscreen: Minimize on drag down
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (totalDragY > dragThreshold) {
                            GlobalPlayerState.showMiniPlayer()
                            currentOnBack()
                        }
                        totalDragY = 0f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        totalDragY += dragAmount
                    }
                )
            }
        }
}