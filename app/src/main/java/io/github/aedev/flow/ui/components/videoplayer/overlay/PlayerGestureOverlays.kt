package io.github.aedev.flow.ui.components.videoplayer.overlay

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
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
            val edgeInset = HudSideInset + cutoutEdgeInset(isFullscreen)
            val topInset = HudTopInset + cutoutTopInset(screenState.isFullscreenPortrait)
            val centredAlignment = centredHudAlignment(style)
            // Only a read-out that actually sits on the top edge clears the punch-hole. The ring
            // reads out in the middle of the picture, nowhere near it.
            val centredTopInset = if (centredAlignment == Alignment.TopCenter) topInset else HudTopInset

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
                            .padding(horizontal = edgeInset)
                    } else {
                        Modifier
                            .align(centredAlignment)
                            .padding(top = centredTopInset)
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
                            .padding(horizontal = edgeInset)
                    } else {
                        Modifier
                            .align(centredAlignment)
                            .padding(top = centredTopInset)
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
                        .padding(top = topInset),
            )
        }
    }
}

/** The ring is a badge, not a bar: it belongs in the middle of the picture, where the eye is. */
private fun centredHudAlignment(style: GestureOverlayStyle): Alignment =
    if (style == GestureOverlayStyle.CIRCULAR) Alignment.Center else Alignment.TopCenter

@Composable
internal fun cutoutTopInset(isFullscreenPortrait: Boolean): Dp {
    if (!isFullscreenPortrait) return 0.dp
    val density = LocalDensity.current
    return with(density) { WindowInsets.displayCutout.getTop(this).toDp() }
}

@Composable
private fun cutoutEdgeInset(isFullscreen: Boolean): Dp {
    if (!isFullscreen) return 0.dp
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    return with(density) {
        maxOf(cutout.getLeft(this, direction), cutout.getRight(this, direction)).toDp()
    }
}
