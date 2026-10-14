package io.github.aedev.flow.widget.playlist

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.state.updateAppWidgetState
import io.github.aedev.flow.widget.core.widgetEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** The list header's Refresh: pulls the playlist again, with a spinner in place of the button meanwhile. */
class RefreshPlaylistAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val playlistId = PlaylistWidgetConfig.load(context, glanceId) ?: PlaylistWidgetSource.DEFAULT_PLAYLIST_ID
        setRefreshing(context, glanceId, true)
        try {
            // Callbacks run inside a broadcast, which the system stops waiting for after a while.
            withTimeoutOrNull(REFRESH_TIMEOUT_MS) { widgetEntryPoint(context).playlistWidgetSource().refresh(playlistId) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not refresh $playlistId: ${e.message}")
        } finally {
            withContext(NonCancellable) { setRefreshing(context, glanceId, false) }
        }
    }

    private suspend fun setRefreshing(
        context: Context,
        glanceId: GlanceId,
        refreshing: Boolean,
    ) {
        updateAppWidgetState(context, glanceId) { prefs ->
            if (refreshing) prefs[PlaylistWidget.REFRESHING] = true else prefs.remove(PlaylistWidget.REFRESHING)
        }
        PlaylistWidget().update(context, glanceId)
    }

    private companion object {
        const val TAG = "RefreshPlaylistAction"
        const val REFRESH_TIMEOUT_MS = 20_000L
    }
}
