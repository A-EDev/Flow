package io.github.aedev.flow.ui.utils

import android.content.res.Configuration

/**
 * Material 3 medium window-size-class breakpoint. Below it only the single-column phone layout
 * fits; at or above it the split list/detail layouts are used.
 */
const val TABLET_SMALLEST_WIDTH_DP = 600

/**
 * Whether this window is wide enough for the tablet layouts, independent of the current rotation.
 * Uses the shortest width so a phone in landscape is never mistaken for a tablet.
 */
val Configuration.isTabletFormFactor: Boolean
    get() = smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP
