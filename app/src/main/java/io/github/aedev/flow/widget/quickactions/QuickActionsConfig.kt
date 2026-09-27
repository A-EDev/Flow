package io.github.aedev.flow.widget.quickactions

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

/** The shortcuts one Quick Actions widget shows, kept in that widget's own Glance state. */
object QuickActionsConfig {
    suspend fun load(
        context: Context,
        id: GlanceId,
    ): List<QuickShortcut> = QuickShortcut.from(getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id))

    suspend fun save(
        context: Context,
        id: GlanceId,
        shortcuts: List<QuickShortcut>,
    ) {
        updateAppWidgetState(context, id) { prefs -> prefs[QuickShortcut.KEY] = QuickShortcut.encode(shortcuts) }
        QuickActionsWidget().update(context, id)
    }
}
