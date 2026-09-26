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
