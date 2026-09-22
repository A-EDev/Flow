package io.github.aedev.flow.ui.components.layout.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private val HideThreshold = 32.dp

/**
 * Hides the bottom bar while the user scrolls content down and brings it back on the way up.
 * Material ships scroll behaviours only for app bars and toolbars, not for the navigation bar.
 */
@Stable
class FlowNavigationScrollState internal constructor(
    private val thresholdPx: Float,
) {
    var isBarVisible by mutableStateOf(true)
        private set

    internal var hideOnScroll = true
    internal var locked = false
    private var accumulated = 0f

    val nestedScrollConnection: NestedScrollConnection =
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (!hideOnScroll || locked || source != NestedScrollSource.UserInput) return Offset.Zero
                val delta = available.y
                if (delta == 0f) return Offset.Zero
                if (accumulated != 0f && (accumulated > 0f) != (delta > 0f)) accumulated = 0f
                accumulated += delta
                when {
                    accumulated <= -thresholdPx && isBarVisible -> {
                        isBarVisible = false
                        accumulated = 0f
                    }

                    accumulated >= thresholdPx && !isBarVisible -> {
                        isBarVisible = true
                        accumulated = 0f
                    }
                }
                return Offset.Zero
            }
        }

    internal fun reset() {
        isBarVisible = true
        accumulated = 0f
    }
}

/**
 * @param routeKey the bar comes back whenever this changes.
 * @param locked keeps the bar where it is, for surfaces that page vertically themselves.
 */
@Composable
fun rememberFlowNavigationScrollState(
    hideOnScroll: Boolean,
    routeKey: String,
    locked: Boolean,
): FlowNavigationScrollState {
    val thresholdPx = with(LocalDensity.current) { HideThreshold.toPx() }
    val state = remember(thresholdPx) { FlowNavigationScrollState(thresholdPx) }
    SideEffect {
        state.hideOnScroll = hideOnScroll
        state.locked = locked
    }
    LaunchedEffect(state, routeKey, hideOnScroll) { state.reset() }
    return state
}
