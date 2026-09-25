package io.github.aedev.flow.ui.components.shared

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * M3 pads a modal sheet's content by the status bar it covers, so a sheet about as tall as the screen
 * re-anchors on every frame and turns each tap into a drag. Held below the status bar, it cannot.
 */
object FlowModalSheetDefaults {
    val modifier: Modifier
        @Composable get() = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))

    val contentWindowInsets: @Composable () -> WindowInsets = { WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom) }
}
