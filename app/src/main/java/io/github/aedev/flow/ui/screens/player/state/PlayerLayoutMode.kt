package io.github.aedev.flow.ui.screens.player.state

import androidx.window.core.layout.WindowSizeClass
import io.github.aedev.flow.ui.utils.isExpandedWidth
import io.github.aedev.flow.ui.utils.isMediumHeight
import io.github.aedev.flow.ui.utils.isMediumWidth

/** Which of the player detail layouts the current window can host. */
internal enum class PlayerLayoutMode {
    /** Single column: compact windows, plus any window currently hosting fullscreen video or PiP. */
    COMPACT,

    /** Medium window: single column with a two-column related-videos grid. */
    MEDIUM,

    /** Expanded window: video info on the left, related videos / comments / live chat on the right. */
    WIDE,
}

/**
 * The layout the window itself can host, before fullscreen or PiP take it away. The video keeps the
 * top of the window whatever the width, so a window below the medium height breakpoint has no room
 * left for a detail pane and stays single-column however wide it is.
 *
 * Width alone does not earn the side pane. A tablet held upright is an expanded-width window, but
 * splitting it leaves a narrow video above a half-width page and a single column of related videos
 * down one edge; upright it wants the full width and a grid, which is [PlayerLayoutMode.MEDIUM] and
 * is what YouTube does on the same device. The pane is for windows that are actually wider than
 * they are tall.
 */
internal fun playerWindowLayoutModeFor(
    windowSizeClass: WindowSizeClass,
    isLandscapeWindow: Boolean,
): PlayerLayoutMode =
    when {
        !windowSizeClass.isMediumHeight -> PlayerLayoutMode.COMPACT
        windowSizeClass.isExpandedWidth && isLandscapeWindow -> PlayerLayoutMode.WIDE
        windowSizeClass.isMediumWidth -> PlayerLayoutMode.MEDIUM
        else -> PlayerLayoutMode.COMPACT
    }

/**
 * Fullscreen and PiP hand the whole window to the video surface, so the detail layout collapses to
 * [PlayerLayoutMode.COMPACT] regardless of how much room the window otherwise has.
 */
internal fun playerLayoutModeFor(
    windowSizeClass: WindowSizeClass,
    isLandscapeWindow: Boolean,
    isFullscreen: Boolean,
    isInPipMode: Boolean,
): PlayerLayoutMode =
    if (isFullscreen || isInPipMode) {
        PlayerLayoutMode.COMPACT
    } else {
        playerWindowLayoutModeFor(windowSizeClass, isLandscapeWindow)
    }
