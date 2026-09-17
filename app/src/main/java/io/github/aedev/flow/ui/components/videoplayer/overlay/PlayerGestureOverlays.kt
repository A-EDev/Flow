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
                            .align(centredHudAlignment(style))
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
                            .padding(horizontal = edgeInset)
                    } else {
                        Modifier
                            .align(centredHudAlignment(style))
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
                        .padding(top = HudTopInset),
            )
        }
    }
}

/** The ring is a badge, not a bar: it belongs in the middle of the picture, where the eye is. */
private fun centredHudAlignment(style: GestureOverlayStyle): Alignment =
    if (style == GestureOverlayStyle.CIRCULAR) Alignment.Center else Alignment.TopCenter

/**
 * How far the standing bars sit in from the long edges in fullscreen.
 *
 * The widest cutout on either edge, applied to both, rather than each side taking its own inset
 * (#1029): the punch-hole is on one edge only, so per-side insets put the two bars at visibly
 * different distances from the picture. Only the edge-aligned bars take it at all — a centred
 * read-out given a one-sided inset is simply pushed off centre, which is what moved every other HUD
 * and the speed badge away from the middle.
 */
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
