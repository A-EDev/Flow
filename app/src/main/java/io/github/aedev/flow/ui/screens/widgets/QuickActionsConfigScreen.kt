package io.github.aedev.flow.ui.screens.widgets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.glance.GlanceId
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.components.settings.SettingsPage
import io.github.aedev.flow.ui.components.shared.FlowSwitchRow
import io.github.aedev.flow.widget.quickactions.QuickActionsConfig
import io.github.aedev.flow.widget.quickactions.QuickShortcut
import kotlinx.coroutines.launch

/** Picks the shortcuts one Quick Actions widget shows; every change lands on the widget at once. */
@Composable
internal fun QuickActionsConfigScreen(
    glanceId: GlanceId,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var chosen by remember { mutableStateOf<List<QuickShortcut>?>(null) }
    LaunchedEffect(glanceId) { chosen = QuickActionsConfig.load(context, glanceId) }
    val current = chosen ?: return

    fun toggle(
        shortcut: QuickShortcut,
        on: Boolean,
    ) {
        val next = if (on) current + shortcut else current - shortcut
        chosen = next
        scope.launch { QuickActionsConfig.save(context, glanceId, next) }
    }

    SettingsPage(title = stringResource(R.string.widget_quick_actions_label), onBack = onDone) {
        group(
            key = "quick_actions.shortcuts",
            header = R.string.widget_config_shortcuts,
            footer = R.string.widget_config_shortcuts_footer,
        ) {
            QuickShortcut.entries.forEach { shortcut ->
                row("quick_actions.${shortcut.key}") { shape ->
                    val checked = shortcut in current
                    FlowSwitchRow(
                        title = stringResource(shortcut.label),
                        checked = checked,
                        onCheckedChange = { toggle(shortcut, it) },
                        leadingPainter = painterResource(shortcut.icon),
                        enabled = checked || current.size < QuickShortcut.MAX,
                        shape = shape,
                    )
                }
            }
        }
    }
}
