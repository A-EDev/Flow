package io.github.aedev.flow.ui.components.videoplayer.gesture

import android.app.Activity
import android.media.AudioManager
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope

private val MinSideEdgeIgnore = 16.dp

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

    // The width the system itself reserves for the back gesture, so the player's dead zones match
    // the ones the user is already swiping against rather than a number of our own.
    val gestureDensity = LocalDensity.current
    val gestureLayoutDirection = LocalLayoutDirection.current
    val systemGestureInsets = WindowInsets.systemGestures
    val sideEdgeIgnorePxState =
        rememberUpdatedState(
            maxOf(
                systemGestureInsets.getLeft(gestureDensity, gestureLayoutDirection).toFloat(),
                systemGestureInsets.getRight(gestureDensity, gestureLayoutDirection).toFloat(),
                // Held open even on button navigation, where the system claims nothing: a swipe
                // starting within a fingertip of the edge is as likely to be aimed past the app.
                with(gestureDensity) { MinSideEdgeIgnore.toPx() },
            ),
        )

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
            sideEdgeIgnorePx = sideEdgeIgnorePxState,
            onExitFullscreen = onExitFullscreenState,
            onExitFullscreenDrag = onExitFullscreenDragState,
            haptics = haptics,
            lastBrightnessApplied = lastBrightnessApplied,
            lastBrightnessAppliedAt = lastBrightnessAppliedAt,
        )
}
