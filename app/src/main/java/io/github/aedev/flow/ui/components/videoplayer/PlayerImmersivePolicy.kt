package io.github.aedev.flow.ui.components.videoplayer

/**
 * A compact window in landscape has no room for the page under the video, so the expanded player
 * is immersive there even when the user never asked for fullscreen.
 */
internal fun isImmersivePlayer(
    isExpanded: Boolean,
    isFullscreen: Boolean,
    isLandscape: Boolean,
    isLargeWindow: Boolean,
): Boolean = isExpanded && (isFullscreen || (isLandscape && !isLargeWindow))

/**
 * Swiping up on the expanded video enters fullscreen unless the player is already immersive: a
 * landscape phone is, but a landscape tablet shows the two-pane player and still needs the gesture.
 */
internal fun canSwipeUpToFullscreen(
    isFullscreen: Boolean,
    isLandscape: Boolean,
    isLargeWindow: Boolean,
): Boolean = !isImmersivePlayer(isExpanded = true, isFullscreen = isFullscreen, isLandscape = isLandscape, isLargeWindow = isLargeWindow)
