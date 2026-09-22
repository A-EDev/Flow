package io.github.aedev.flow.ui.components.layout.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailState
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.utils.LocalWindowSizeClass
import io.github.aedev.flow.ui.utils.isLargeWidth
import kotlinx.coroutines.launch

// Centres the 48 dp header button over the 96 dp collapsed rail's item column.
private val HeaderButtonStartPadding = 24.dp

/**
 * The start-edge navigation rail. Collapsed by default; on large windows its header offers an
 * expanded form with labels beside the icons. Items centre on the full height; the header stays on top.
 */
@Composable
internal fun FlowNavigationRail(
    tabs: List<FlowTab>,
    selectedTab: FlowTab?,
    onTabSelected: (FlowTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val expandable = LocalWindowSizeClass.current.isLargeWidth
    val state = rememberWideNavigationRailState()
    LaunchedEffect(expandable) {
        if (!expandable) state.snapTo(WideNavigationRailValue.Collapsed)
    }
    val railExpanded = state.targetValue == WideNavigationRailValue.Expanded

    WideNavigationRail(
        modifier = modifier.fillMaxHeight(),
        state = state,
        header = if (expandable) ({ RailToggle(state) }) else null,
        arrangement = Arrangement.Center,
    ) {
        tabs.forEach { tab ->
            val selected = tab == selectedTab
            WideNavigationRailItem(
                selected = selected,
                onClick = { onTabSelected(tab) },
                icon = { Icon(imageVector = tab.icon(selected), contentDescription = null) },
                label = { Text(text = stringResource(tab.labelRes)) },
                railExpanded = railExpanded,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RailToggle(state: WideNavigationRailState) {
    val scope = rememberCoroutineScope()
    val expanded = state.targetValue == WideNavigationRailValue.Expanded
    val description = stringResource(if (expanded) R.string.nav_rail_collapse else R.string.nav_rail_expand)
    val stateText = stringResource(if (expanded) R.string.nav_rail_state_expanded else R.string.nav_rail_state_collapsed)

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(text = description) } },
        state = rememberTooltipState(),
    ) {
        IconButton(
            onClick = { scope.launch { state.toggle() } },
            modifier =
                Modifier
                    .padding(start = HeaderButtonStartPadding)
                    .semantics { stateDescription = stateText },
        ) {
            Icon(
                imageVector = if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Filled.Menu,
                contentDescription = description,
            )
        }
    }
}
