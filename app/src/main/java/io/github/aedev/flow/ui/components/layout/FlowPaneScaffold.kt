package io.github.aedev.flow.ui.components.layout

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember

/**
 * The library's pane directive for this window, including the hinge on a book-posture foldable,
 * with auto-focus off. On every navigation the scaffold would otherwise request focus on the
 * destination pane, which lands on its first text field and opens the keyboard.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun rememberFlowPaneScaffoldDirective(): PaneScaffoldDirective {
    val defaults = calculatePaneScaffoldDirective(currentWindowAdaptiveInfoV2())
    return remember(defaults) {
        PaneScaffoldDirective(
            maxHorizontalPartitions = defaults.maxHorizontalPartitions,
            horizontalPartitionSpacerSize = defaults.horizontalPartitionSpacerSize,
            maxVerticalPartitions = defaults.maxVerticalPartitions,
            verticalPartitionSpacerSize = defaults.verticalPartitionSpacerSize,
            defaultPanePreferredWidth = defaults.defaultPanePreferredWidth,
            defaultPanePreferredHeight = defaults.defaultPanePreferredHeight,
            excludedBounds = defaults.excludedBounds,
            shouldAutoFocusCurrentDestination = false,
        )
    }
}
