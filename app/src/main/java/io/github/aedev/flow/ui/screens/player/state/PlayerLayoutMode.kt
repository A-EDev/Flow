package io.github.aedev.flow.ui.screens.player.state

import android.content.res.Configuration
import io.github.aedev.flow.ui.utils.isTabletFormFactor

/** Which of the player detail layouts the current window can host. */
enum class PlayerLayoutMode {
    /** Single column: phones, plus any window currently hosting fullscreen video or PiP. */
    COMPACT,

    /** Tablet held upright: single column with a two-column related-videos grid. */
    TABLET_PORTRAIT,

    /** Tablet in landscape: video info on the left, related videos / comments / live chat on the right. */
    WIDE,
}

/**
 * Fullscreen and PiP hand the whole window to the video surface, so the detail layout collapses to
 * [PlayerLayoutMode.COMPACT] regardless of how much room the device otherwise has.
 */
fun playerLayoutModeFor(
    configuration: Configuration,
    isFullscreen: Boolean,
    isInPipMode: Boolean,
): PlayerLayoutMode =
    when {
        isFullscreen || isInPipMode -> PlayerLayoutMode.COMPACT
        !configuration.isTabletFormFactor -> PlayerLayoutMode.COMPACT
        configuration.orientation == Configuration.ORIENTATION_LANDSCAPE -> PlayerLayoutMode.WIDE
        else -> PlayerLayoutMode.TABLET_PORTRAIT
    }
