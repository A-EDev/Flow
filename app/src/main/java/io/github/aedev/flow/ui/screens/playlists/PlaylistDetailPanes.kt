package io.github.aedev.flow.ui.screens.playlists

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.ui.components.layout.rememberFlowPaneScaffoldDirective
import io.github.aedev.flow.ui.components.shared.rememberMediaArtworkTint

// The desktop app's header column, which keeps the artwork a header rather than a poster.
private val HeaderPaneWidth = 360.dp

/** The pane layout for this window; keeps the adaptive library's experimental types out of the screen. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
internal class PlaylistPaneState(
    val navigator: ThreePaneScaffoldNavigator<Any>,
) {
    /** Whether the window has room for the header beside the list. */
    val showsHeaderPane: Boolean
        get() =
            navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded &&
                navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun rememberPlaylistPaneState(): PlaylistPaneState {
    val navigator = rememberListDetailPaneScaffoldNavigator<Any>(scaffoldDirective = rememberFlowPaneScaffoldDirective())
    return remember(navigator) { PlaylistPaneState(navigator) }
}

/**
 * On a window with two partitions, the header as a pane on the leading side and the list beside it,
 * as on the desktop app; otherwise [mainPane] alone. The list-detail scaffold is used for its pane
 * order (the supporting-pane scaffold always trails); it also keeps both panes off a foldable's hinge.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun PlaylistDetailPanes(
    panes: PlaylistPaneState,
    artworkUrl: String,
    headerPane: @Composable () -> Unit,
    mainPane: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!panes.showsHeaderPane) {
        mainPane()
        return
    }
    ListDetailPaneScaffold(
        directive = panes.navigator.scaffoldDirective,
        value = panes.navigator.scaffoldValue,
        modifier = modifier,
        listPane = {
            AnimatedPane(modifier = Modifier.preferredWidth(HeaderPaneWidth)) {
                val tint = rememberMediaArtworkTint(artworkUrl.takeIf(String::isNotBlank))
                Surface(
                    color = tint.container,
                    contentColor = tint.onContainer,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp, bottom = 16.dp),
                ) {
                    headerPane()
                }
            }
        },
        detailPane = { AnimatedPane { mainPane() } },
    )
}
