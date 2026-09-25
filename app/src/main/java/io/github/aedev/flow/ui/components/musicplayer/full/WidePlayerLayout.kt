package io.github.aedev.flow.ui.components.musicplayer.full

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffold
import androidx.compose.material3.adaptive.layout.SupportingPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.ThreePaneScaffoldNavigator
import androidx.compose.material3.adaptive.navigation.rememberSupportingPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import io.github.aedev.flow.ui.components.layout.rememberFlowPaneScaffoldDirective

private val SidePaneWidth = 400.dp
private val MainPanePadding = 32.dp
private val MainPaneGap = 32.dp
private val MainPaneRowGap = 12.dp
private val SidePaneEdgePadding = 16.dp

/** Whether this window gives the player a pane beside it; wraps the adaptive library's experimental types. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Stable
internal class PlayerPanes(
    val navigator: ThreePaneScaffoldNavigator<Any>,
) {
    val showsSidePane: Boolean
        get() =
            navigator.scaffoldValue[SupportingPaneScaffoldRole.Main] == PaneAdaptedValue.Expanded &&
                navigator.scaffoldValue[SupportingPaneScaffoldRole.Supporting] == PaneAdaptedValue.Expanded
}

/** Hinge-aware: on a book-posture foldable the scaffold puts the split on the hinge. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun rememberPlayerPanes(): PlayerPanes {
    val navigator = rememberSupportingPaneScaffoldNavigator<Any>(scaffoldDirective = rememberFlowPaneScaffoldDirective())
    return remember(navigator) { PlayerPanes(navigator) }
}

/**
 * Wide landscape windows: the top bar across the window, the player in the main pane (cover and
 * controls side by side when the pane is wide enough, stacked otherwise) and [sidePane] trailing.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
internal fun WidePlayerLayout(
    slots: NowPlayingSlots,
    panes: PlayerPanes,
    sidePane: @Composable (Modifier) -> Unit,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)),
    ) {
        slots.topBar(Modifier)
        SupportingPaneScaffold(
            directive = panes.navigator.scaffoldDirective,
            value = panes.navigator.scaffoldValue,
            modifier = Modifier.weight(1f),
            mainPane = {
                AnimatedPane {
                    WideMainPane(
                        slots = slots,
                        modifier = Modifier.padding(start = MainPanePadding, end = MainPanePadding, bottom = bottomInset + 16.dp),
                    )
                }
            },
            supportingPane = {
                AnimatedPane(modifier = Modifier.preferredWidth(SidePaneWidth)) {
                    sidePane(Modifier.fillMaxSize().padding(end = SidePaneEdgePadding, bottom = bottomInset + SidePaneEdgePadding))
                }
            },
        )
    }
}

@Composable
private fun WideMainPane(
    slots: NowPlayingSlots,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val sideBySide = maxWidth >= maxHeight * 1.1f
        if (sideBySide) {
            val artworkSize = min(maxHeight, (maxWidth - MainPaneGap) / 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(MainPaneGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                slots.artwork(Modifier.size(artworkSize))
                NowPlayingDetails(slots, Modifier.weight(1f))
            }
        } else {
            val artworkSize = min(maxWidth, maxHeight - StackedDetailsHeight).coerceAtLeast(0.dp)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(MainPaneRowGap, Alignment.CenterVertically),
            ) {
                slots.artwork(Modifier.size(artworkSize))
                NowPlayingDetails(slots, Modifier.fillMaxWidth())
            }
        }
    }
}

/** Title, seek bar, transport and actions, the part every layout stacks under or beside the cover. */
@Composable
private fun NowPlayingDetails(
    slots: NowPlayingSlots,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(MainPaneRowGap)) {
        slots.header(Modifier)
        slots.progress(Modifier)
        slots.controls(Modifier)
        slots.actions(Modifier)
    }
}

/** Roughly what the details take under a stacked cover: header, seek bar, transport, actions, gaps. */
private val StackedDetailsHeight = 300.dp
