package io.github.aedev.flow.ui.screens.player.state

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The width the supporting pane and its spacer take from the row the video shares with the main
 * pane in the WIDE layout. The scaffold measures the supporting pane at the directive's preferred
 * width and hands the main pane the rest, so the video above the main pane is sized from the same
 * numbers instead of a second fraction that could drift from the scaffold's.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal fun PaneScaffoldDirective.supportingPaneReserve(): Dp =
    if (maxHorizontalPartitions >= 2) defaultPanePreferredWidth + horizontalPartitionSpacerSize else 0.dp
