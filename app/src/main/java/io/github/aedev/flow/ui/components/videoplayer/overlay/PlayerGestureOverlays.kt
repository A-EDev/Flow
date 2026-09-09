package io.github.aedev.flow.ui.components.videoplayer.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState

@Composable
fun PlayerGestureOverlays(
    screenState: PlayerScreenState,
    allowVolumeBoost: Boolean,
    speedBoostSpeed: Float,
    modifier: Modifier = Modifier,
) {
    // Force LTR so CenterStart/CenterEnd always map to physical left/right,
    // regardless of the device's system language direction.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier = modifier.fillMaxSize()) {
            SeekAnimationOverlay(
                showSeekBack = screenState.showSeekBackAnimation,
                showSeekForward = screenState.showSeekForwardAnimation,
                seekSeconds = screenState.seekAccumulation,
                modifier = Modifier.align(Alignment.Center),
            )

            BrightnessOverlay(
                isVisible = screenState.showBrightnessOverlay,
                brightnessLevel = { screenState.brightnessLevel },
                modifier =
                    Modifier
                        .align(Alignment.Center),
            )

            VolumeOverlay(
                isVisible = screenState.showVolumeOverlay,
                volumeLevel = { screenState.volumeLevel },
                maxVolumeLevel = if (allowVolumeBoost) 2f else 1f,
                modifier =
                    Modifier
                        .align(Alignment.Center),
            )

            SeekDragOverlay(
                isVisible = screenState.isSeekDragging,
                targetMs = { screenState.seekDragTargetMs },
                deltaMs = { screenState.seekDragDeltaMs },
                modifier = Modifier.align(Alignment.Center),
            )

            SpeedBoostOverlay(
                isVisible = screenState.isSpeedBoostActive,
                speed = speedBoostSpeed,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .then(
                            if (screenState.isFullscreen) {
                                Modifier
                                    .windowInsetsPadding(WindowInsets.displayCutout)
                                    .padding(top = 12.dp)
                            } else {
                                Modifier.padding(top = 12.dp)
                            },
                        ),
            )
        }
    }
}
