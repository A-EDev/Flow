package io.github.aedev.flow.ui.screens.subscriptions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * One [SubscriptionsViewModel] per activity — the same scoping the TV shell already uses via
 * `hiltViewModel(activity)`.
 *
 * A per-route instance was recreated on every visit to the Subscriptions tab, and because
 * `ensureStarted()` guards on an instance field, each new instance restarted five collectors and
 * another feed refresh. Sharing one instance also keeps feed and scroll state across tab switches.
 */
@Composable
fun sharedSubscriptionsViewModel(): SubscriptionsViewModel {
    val activity = LocalContext.current as? ComponentActivity
    return if (activity != null) hiltViewModel(activity) else hiltViewModel()
}
