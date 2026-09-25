package io.github.aedev.flow.ui.components.shared

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import io.github.aedev.flow.ui.components.layout.rememberFlowPaneScaffoldDirective

/** The pane layout for this window; keeps the adaptive library's experimental types out of screens. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class FlowPaneState(
    val navigator: ThreePaneScaffoldNavigator<Any>,
) {
    /** Whether the window has room for a pane beside the main one. */
    val showsSidePane: Boolean
        get() =
            navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
                navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun rememberFlowPaneState(): FlowPaneState {
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>(scaffoldDirective = rememberFlowPaneScaffoldDirective())
    return remember(navigator) { FlowPaneState(navigator) }
}

/**
 * On a window with two partitions, [sidePane] on the leading side at [sidePaneWidth] and
 * [mainPane] beside it; otherwise [mainPane] alone. The list-detail scaffold is used for its pane
 * order (the supporting-pane scaffold always trails); it also keeps both panes off a foldable's hinge.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun FlowSidePanes(
    panes: FlowPaneState,
    sidePaneWidth: Dp,
    sidePane: @Composable () -> Unit,
    mainPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!panes.showsSidePane) {
        mainPane()
        return
    }
    ListDetailPaneScaffold(
        directive = panes.navigator.scaffoldDirective,
        value = panes.navigator.scaffoldValue,
        modifier = modifier,
        listPane = { AnimatedPane(modifier = Modifier.preferredWidth(sidePaneWidth)) { sidePane() } },
        detailPane = { AnimatedPane { mainPane() } },
    )
}
