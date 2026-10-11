package io.github.aedev.flow.ui.screens.player.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/** How long the window waits for a requested turn before it switches layout without one. */
private const val ROTATION_WAIT_MS = 800L

/**
 * Whether the window still has to turn for the fullscreen state it was asked for: a portrait phone
 * entering landscape fullscreen, or a landscape one leaving it.
 */
internal fun isAwaitingRotation(
    isFullscreen: Boolean,
    isFullscreenPortrait: Boolean,
    videoAspectRatio: Float,
    windowIsLandscape: Boolean,
    rotationHonoured: Boolean,
): Boolean =
    rotationHonoured &&
        if (isFullscreen) {
            !isFullscreenPortrait && videoAspectRatio >= 1f && !windowIsLandscape
        } else {
            windowIsLandscape
        }

/**
 * The fullscreen state the window shows. While the turn a change asked for is on its way, the
 * window keeps the layout it has, so the page, the system bars and the seek bar never switch in the
 * wrong orientation; after [ROTATION_WAIT_MS] it stops waiting for a turn that is not coming.
 */
@Composable
internal fun rememberShownFullscreen(
    isFullscreen: Boolean,
    isFullscreenPortrait: Boolean,
    videoAspectRatio: Float,
    windowIsLandscape: Boolean,
    rotationHonoured: Boolean,
): Boolean {
    val awaiting =
        isAwaitingRotation(isFullscreen, isFullscreenPortrait, videoAspectRatio, windowIsLandscape, rotationHonoured)
    var timedOut by remember(awaiting) { mutableStateOf(false) }
    LaunchedEffect(awaiting) {
        if (awaiting) {
            delay(ROTATION_WAIT_MS)
            timedOut = true
        }
    }
    return if (awaiting && !timedOut) !isFullscreen else isFullscreen
}
