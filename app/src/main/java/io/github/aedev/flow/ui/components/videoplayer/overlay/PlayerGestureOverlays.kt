package io.github.aedev.flow.ui.components.videoplayer.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.GestureOverlayStyle
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState

private val VerticalHudSideInset = 20.dp

@Composable
fun PlayerGestureOverlays(
    screenState: PlayerScreenState,
    allowVolumeBoost: Boolean,
    speedBoostSpeed: Float,
    style: GestureOverlayStyle,
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

            // The vertical style exists to keep the middle of the frame clear, so it sits against
            // the edge its own gesture came from; every other style stays centred.
            val isVertical = style == GestureOverlayStyle.VERTICAL

            BrightnessOverlay(
                isVisible = screenState.showBrightnessOverlay,
                brightnessLevel = { screenState.brightnessLevel },
                style = style,
                modifier =
                    if (isVertical) {
                        Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = VerticalHudSideInset)
                    } else {
                        Modifier.align(Alignment.Center)
                    },
            )

            VolumeOverlay(
                isVisible = screenState.showVolumeOverlay,
                volumeLevel = { screenState.volumeLevel },
                style = style,
                maxVolumeLevel = if (allowVolumeBoost) 2f else 1f,
                modifier =
                    if (isVertical) {
                        Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = VerticalHudSideInset)
                    } else {
                        Modifier.align(Alignment.Center)
                    },
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
