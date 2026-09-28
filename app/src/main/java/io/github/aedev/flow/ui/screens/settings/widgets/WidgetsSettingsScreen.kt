package io.github.aedev.flow.ui.screens.settings.widgets

import android.appwidget.AppWidgetManager
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.glance.appwidget.GlanceAppWidgetManager
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.settings.nav
import io.github.aedev.flow.ui.components.settings.notice
import io.github.aedev.flow.ui.screens.settings.index.WidgetsIndex
import io.github.aedev.flow.widget.core.FlowWidgets
import kotlinx.coroutines.launch

/** Every Flow widget, each one tap from the launcher's own add-widget dialog. */
@Composable
internal fun WidgetsSettingsScreen(
    onBack: (() -> Unit)?,
    highlight: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val canPin = remember(context) { AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported }
    val unsupported = stringResource(R.string.settings_widgets_unsupported)

    SettingsPage(
        title = stringResource(R.string.settings_widgets_title),
        onBack = onBack,
        highlight = highlight,
        snackbarHostState = snackbarHostState,
    ) {
        if (!canPin) notice("widgets.unsupported", text = { unsupported })
        group(key = "widgets.add", header = R.string.settings_widgets_add_header, footer = R.string.settings_widgets_footer) {
            FlowWidgets.catalog.forEach { widget ->
                nav(
                    WidgetsIndex.entryFor(widget),
                    onClick = {
                        scope.launch {
                            val requested = GlanceAppWidgetManager(context).requestPinGlanceAppWidget(widget.receiver.java)
                            if (!requested) snackbarHostState.showSnackbar(unsupported)
                        }
                    },
                    enabled = canPin,
                    showChevron = false,
                    iconRes = widget.icon,
                )
            }
        }
    }
}
