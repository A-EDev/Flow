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

private val HudSideInset = 16.dp
private val HudTopInset = 12.dp

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
            val isFullscreen = screenState.isFullscreen
            val isVertical = style == GestureOverlayStyle.VERTICAL

            SeekAnimationOverlay(
                showSeekBack = screenState.showSeekBackAnimation,
                showSeekForward = screenState.showSeekForwardAnimation,
                seekSeconds = screenState.seekAccumulation,
                modifier = Modifier.align(Alignment.Center),
            )

            // The standing bar goes to the side OPPOSITE the swipe: brightness is a left-edge
            // gesture, so it reads out on the right, and volume the other way round. Put it under
            // the thumb and the hand adjusting the level covers the number it is aiming for.
            BrightnessOverlay(
                isVisible = screenState.showBrightnessOverlay,
                brightnessLevel = { screenState.brightnessLevel },
                style = style,
                modifier =
                    if (isVertical) {
                        Modifier
                            .align(Alignment.CenterEnd)
                            .hudInsets(isFullscreen)
                            .padding(horizontal = HudSideInset)
                    } else {
                        Modifier
                            .align(Alignment.TopCenter)
                            .hudInsets(isFullscreen)
                            .padding(top = HudTopInset)
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
                            .align(Alignment.CenterStart)
                            .hudInsets(isFullscreen)
                            .padding(horizontal = HudSideInset)
                    } else {
                        Modifier
                            .align(Alignment.TopCenter)
                            .hudInsets(isFullscreen)
                            .padding(top = HudTopInset)
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
                        .hudInsets(isFullscreen)
                        .padding(top = HudTopInset),
            )
        }
    }
}

/**
 * Keeps a read-out clear of the display cutout. In landscape fullscreen the punch-hole sits on one
 * of the long edges, which is exactly where the standing bar wants to be.
 */
@Composable
private fun Modifier.hudInsets(isFullscreen: Boolean): Modifier =
    if (isFullscreen) this.windowInsetsPadding(WindowInsets.displayCutout) else this
