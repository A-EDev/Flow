package io.github.aedev.flow.ui.components.videoplayer.gesture

import android.app.Activity
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.CoroutineScope

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
    val isSpeedBoostActiveState = rememberUpdatedState(isSpeedBoostActive)
    val onSpeedBoostChangeState = rememberUpdatedState(onSpeedBoostChange)
    val showControlsState = rememberUpdatedState(showControls)
    val onShowControlsChangeState = rememberUpdatedState(onShowControlsChange)
    val onShowSeekBackChangeState = rememberUpdatedState(onShowSeekBackChange)
    val onShowSeekForwardChangeState = rememberUpdatedState(onShowSeekForwardChange)
    val currentPositionState = rememberUpdatedState(currentPosition)
    val durationState = rememberUpdatedState(duration)
    val onNormalSpeedChangeState = rememberUpdatedState(onNormalSpeedChange)
    val isFullscreenState = rememberUpdatedState(isFullscreen)
    val onBrightnessChangeState = rememberUpdatedState(onBrightnessChange)
    val onShowBrightnessChangeState = rememberUpdatedState(onShowBrightnessChange)
    val onVolumeChangeState = rememberUpdatedState(onVolumeChange)
    val onShowVolumeChangeState = rememberUpdatedState(onShowVolumeChange)
    val onSeekDragChangeState = rememberUpdatedState(onSeekDragChange)
    val onSeekDragUpdateState = rememberUpdatedState(onSeekDragUpdate)
    val brightnessLevelState = rememberUpdatedState(brightnessLevel)
    val volumeLevelState = rememberUpdatedState(volumeLevel)
    val maxVolumeState = rememberUpdatedState(maxVolume)
    val audioManagerState = rememberUpdatedState(audioManager)
    val activityState = rememberUpdatedState(activity)
    val brightnessSwipeGesturesEnabledState = rememberUpdatedState(brightnessSwipeGesturesEnabled)
    val volumeSwipeGesturesEnabledState = rememberUpdatedState(volumeSwipeGesturesEnabled)
    val seekSwipeGesturesEnabledState = rememberUpdatedState(seekSwipeGesturesEnabled)
    val allowVolumeBoostState = rememberUpdatedState(allowVolumeBoost)
    val doubleTapSeekMsState = rememberUpdatedState(doubleTapSeekMs)
    val longPressPlaybackSpeedState = rememberUpdatedState(longPressPlaybackSpeed)
    val onSeekAccumulateState = rememberUpdatedState(onSeekAccumulate)
    val onExitFullscreenState = rememberUpdatedState(onExitFullscreen)
    val onExitFullscreenDragState = rememberUpdatedState(onExitFullscreenDrag)
    val isSeekForwardActiveState = rememberUpdatedState(isSeekForwardActive)
    val isSeekBackActiveState = rememberUpdatedState(isSeekBackActive)

    val haptics = LocalHapticFeedback.current

    val lastBrightnessApplied = remember { floatArrayOf(-2f) }
    val lastBrightnessAppliedAt = remember { longArrayOf(0L) }

    return this
        .playerTapGestures(
            isSpeedBoostActive = isSpeedBoostActiveState,
            onSpeedBoostChange = onSpeedBoostChangeState,
            showControls = showControlsState,
            onShowControlsChange = onShowControlsChangeState,
            onShowSeekBackChange = onShowSeekBackChangeState,
            onShowSeekForwardChange = onShowSeekForwardChangeState,
            onSeekAccumulate = onSeekAccumulateState,
            currentPosition = currentPositionState,
            duration = durationState,
            onNormalSpeedChange = onNormalSpeedChangeState,
            isFullscreen = isFullscreenState,
            doubleTapSeekMs = doubleTapSeekMsState,
            longPressPlaybackSpeed = longPressPlaybackSpeedState,
            isSeekForwardActive = isSeekForwardActiveState,
            isSeekBackActive = isSeekBackActiveState,
            haptics = haptics,
        ).playerDragGestures(
            currentPosition = currentPositionState,
            duration = durationState,
            scope = scope,
            isFullscreen = isFullscreenState,
            onBrightnessChange = onBrightnessChangeState,
            onShowBrightnessChange = onShowBrightnessChangeState,
            onVolumeChange = onVolumeChangeState,
            onShowVolumeChange = onShowVolumeChangeState,
            onSeekDragChange = onSeekDragChangeState,
            onSeekDragUpdate = onSeekDragUpdateState,
            brightnessLevel = brightnessLevelState,
            volumeLevel = volumeLevelState,
            maxVolume = maxVolumeState,
            audioManager = audioManagerState,
            activity = activityState,
            brightnessSwipeGesturesEnabled = brightnessSwipeGesturesEnabledState,
            volumeSwipeGesturesEnabled = volumeSwipeGesturesEnabledState,
            seekSwipeGesturesEnabled = seekSwipeGesturesEnabledState,
            allowVolumeBoost = allowVolumeBoostState,
            onExitFullscreen = onExitFullscreenState,
            onExitFullscreenDrag = onExitFullscreenDragState,
            haptics = haptics,
            lastBrightnessApplied = lastBrightnessApplied,
            lastBrightnessAppliedAt = lastBrightnessAppliedAt,
        )
}
