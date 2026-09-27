package io.github.aedev.flow.widget.playlist

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition

/** Which playlist one Playlist widget shows, kept in that widget's own Glance state. */
object PlaylistWidgetConfig {
    suspend fun load(
        context: Context,
        id: GlanceId,
    ): String? = getAppWidgetState<Preferences>(context, PreferencesGlanceStateDefinition, id)[PlaylistWidget.PLAYLIST_ID]

    suspend fun save(
        context: Context,
        id: GlanceId,
        playlistId: String,
    ) {
        updateAppWidgetState(context, id) { prefs -> prefs[PlaylistWidget.PLAYLIST_ID] = playlistId }
        PlaylistWidget().update(context, id)
    }
}
