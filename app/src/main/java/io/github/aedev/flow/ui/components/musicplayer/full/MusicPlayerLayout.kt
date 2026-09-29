package io.github.aedev.flow.ui.components.musicplayer.full

import androidx.window.core.layout.WindowSizeClass
import io.github.aedev.flow.ui.utils.isExpandedWidth
import io.github.aedev.flow.ui.utils.isMediumHeight
import io.github.aedev.flow.ui.utils.isMediumWidth

/** How the expanded music player arranges itself in the current window. */
internal enum class MusicPlayerLayout {
    /** Phones held upright: one column, lyrics and queue as sheets. */
    COMPACT,

    /** Short landscape windows (phones turned sideways, small tablets): cover left, the rest right. */
    SPLIT,

    /** Upright tablets and unfolded foldables: the phone column, centred and a little larger. */
    PORTRAIT_LARGE,

    /** Wide landscape windows: the player beside a pane holding Up next and Lyrics. */
    WIDE,
}

/**
 * Picks the layout from the window's size class and its real proportion. A size class alone cannot
 * tell an upright tablet from a landscape one (both report expanded by expanded), so the side pane
 * is earned only by a window that is actually wider than tall and at least medium height.
 */
internal fun musicPlayerLayoutFor(
    windowSizeClass: WindowSizeClass,
    isLandscapeWindow: Boolean,
): MusicPlayerLayout =
    when {
        isLandscapeWindow && windowSizeClass.isExpandedWidth && windowSizeClass.isMediumHeight -> MusicPlayerLayout.WIDE
        isLandscapeWindow && windowSizeClass.isMediumWidth -> MusicPlayerLayout.SPLIT
        !isLandscapeWindow && windowSizeClass.isMediumWidth -> MusicPlayerLayout.PORTRAIT_LARGE
        else -> MusicPlayerLayout.COMPACT
    }
