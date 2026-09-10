package io.github.aedev.flow.widget.core

import android.content.Context
import androidx.glance.appwidget.updateAll
import io.github.aedev.flow.widget.downloads.DownloadsWidget
import io.github.aedev.flow.widget.nowplaying.NowPlayingWidget
import io.github.aedev.flow.widget.onrepeat.OnRepeatWidget
import io.github.aedev.flow.widget.quickactions.QuickActionsWidget
import io.github.aedev.flow.widget.recent.RecentlyPlayedWidget
import io.github.aedev.flow.widget.recognize.RecognizeWidget
import io.github.aedev.flow.widget.turntable.TurntableWidget

/** Registry of every Flow widget — used to re-render all of them on app theme changes. */
object FlowWidgets {
    /**
     * Re-renders every placed widget whenever the app's palette actually changes.
     *
     * The baseline is persisted rather than positional. Glance turns each `updateAll` into one
     * `SessionWorker` per placed instance, and a session lives about fifty seconds registering a
     * global snapshot write observer that taxes every frame the app draws in that window — so a
     * launch that re-renders widgets for nothing is expensive, and ten to twenty of them was the
     * measured cost of a `drop(1)` that could only ever mean "first value I have seen this
     * process", never "same as last time".
     *
     * Persisting it also closes the opposite hole: a theme change made while this Activity is not
     * alive (the TV appearance settings) used to be swallowed by that same `drop(1)`.
     */
    suspend fun observeThemeChanges(context: Context) {
        val appContext = context.applicationContext
        var lastSignature = appContext.lastWidgetThemeSignature()
        widgetThemeSignatureFlow(appContext).collect { signature ->
            val persisted = signature.persistedForm()
            if (persisted == lastSignature) return@collect
            lastSignature = persisted
            appContext.writeWidgetThemeSignature(persisted)
            updateAll(appContext)
        }
    }

    suspend fun updateAll(context: Context) {
        NowPlayingWidget().updateAll(context)
        TurntableWidget().updateAll(context)
        QuickActionsWidget().updateAll(context)
        RecognizeWidget().updateAll(context)
        RecentlyPlayedWidget().updateAll(context)
        DownloadsWidget().updateAll(context)
        OnRepeatWidget().updateAll(context)
    }
}
