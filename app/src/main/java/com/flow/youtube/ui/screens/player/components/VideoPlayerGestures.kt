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
                    
                    if (tapPosition < screenWidth * 0.3f || tapPosition > screenWidth * 0.7f) {
                        val player = EnhancedPlayerManager.getInstance().getPlayer()
                        if (player != null && !currentIsSpeedBoostActive) {
                            currentOnSpeedBoostChange(true)
                            player.setPlaybackSpeed(2.0f)
                        }
                    }
                },
                onPress = { offset ->
                    val screenWidth = elementSize.width
                    val tapPosition = offset.x
                    
                    if (tapPosition < screenWidth * 0.3f || tapPosition > screenWidth * 0.7f) {
                        tryAwaitRelease()
                        if (currentIsSpeedBoostActive) {
                            val player = EnhancedPlayerManager.getInstance().getPlayer()
                            player?.setPlaybackSpeed(currentNormalSpeed)
                            currentOnSpeedBoostChange(false)
                        }
                    }
                }
            )
        }
        .pointerInput(isFullscreen) {
            val elementSize = size
            var totalDragY = 0f
            val dragThreshold = 150f

            detectVerticalDragGestures(
                onDragStart = { offset ->
                    if (currentIsFullscreen) {
                        val screenWidth = elementSize.width
                        if (offset.x < screenWidth / 2) {
                            currentOnShowBrightnessChange(true)
                        } else {
                            currentOnShowVolumeChange(true)
                        }
                    } else {
                        totalDragY = 0f
                    }
                },
                onDragEnd = {
                    if (currentIsFullscreen) {
                        scope.launch {
                            delay(1000)
                            currentOnShowBrightnessChange(false)
                            currentOnShowVolumeChange(false)
                        }
                    } else {
                        if (totalDragY > dragThreshold) {
                            GlobalPlayerState.showMiniPlayer()
                            currentOnBack()
                        }
                        totalDragY = 0f
                    }
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    
                    if (currentIsFullscreen) {
                        val screenHeight = elementSize.height.toFloat()
                        val dragPosition = change.position.x
                        val screenWidth = elementSize.width
                        
                        if (screenHeight > 0) {
                            if (dragPosition < screenWidth / 2) {
                                // Left side - brightness
                                val newBrightness = (currentBrightnessLevel - dragAmount / screenHeight)
                                    .coerceIn(0f, 1f)
                                currentOnBrightnessChange(newBrightness)
                                
                                try {
                                    currentActivity?.window?.let { window ->
                                        val layoutParams = window.attributes
                                        layoutParams.screenBrightness = newBrightness
                                        window.attributes = layoutParams
                                    }
                                } catch (e: Exception) {
                                    // Ignore
                                }
                                
                                currentOnShowBrightnessChange(true)
                            } else {
                                // Right side - volume
                                val newVolumeLevel = (currentVolumeLevel - dragAmount / screenHeight)
                                    .coerceIn(0f, 1f)
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
                    } else {
                        totalDragY += dragAmount
                    }
                }
            )
        }
}