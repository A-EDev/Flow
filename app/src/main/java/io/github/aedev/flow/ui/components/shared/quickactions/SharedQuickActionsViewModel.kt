package io.github.aedev.flow.ui.components.shared.quickactions

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * The activity's [QuickActionsViewModel]. Cards, menus and search share it, so a route that opens a
 * menu never builds a second copy with its own subscription observers.
 */
@Composable
fun sharedQuickActionsViewModel(): QuickActionsViewModel {
    val activity = LocalContext.current as? ComponentActivity
    return if (activity != null) hiltViewModel(activity) else hiltViewModel()
}
