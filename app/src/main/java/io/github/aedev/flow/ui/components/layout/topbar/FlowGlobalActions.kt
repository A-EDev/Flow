package io.github.aedev.flow.ui.components.layout.topbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.StateFlow

/**
 * App-shell destinations that must stay reachable from every root screen.
 *
 * Before this existed, `notifications` had a single entry point — the Home top bar — so turning
 * Home off in navigation settings orphaned the screen entirely. Root tabs 3 (Subscriptions) and 4
 * (Library) can never be hidden, so rendering these actions on every backless destination
 * guarantees reachability for any navigation configuration.
 *
 * [unreadNotifications] is the flow rather than the current value so that a new notification
 * invalidates only the badge that collects it, instead of the whole shell that provides it.
 */
@Immutable
data class FlowGlobalActions(
    val unreadNotifications: StateFlow<Int>,
    val onOpenNotifications: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/**
 * Static because the provided value is remembered for the lifetime of the shell and never changes;
 * reading it must never invalidate a screen.
 */
val LocalFlowGlobalActions = staticCompositionLocalOf<FlowGlobalActions?> { null }

/**
 * Publishes the shell's global actions to every [FlowTopBar] below it. Call once, around the
 * `NavHost`.
 */
@Composable
fun ProvideFlowGlobalActions(
    unreadNotifications: StateFlow<Int>,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit,
) {
    val actions =
        remember(unreadNotifications, onOpenNotifications, onOpenSettings) {
            FlowGlobalActions(
                unreadNotifications = unreadNotifications,
                onOpenNotifications = onOpenNotifications,
                onOpenSettings = onOpenSettings,
            )
        }
    CompositionLocalProvider(LocalFlowGlobalActions provides actions, content = content)
}
