package io.github.aedev.flow.widget.core

import android.content.Context
import androidx.glance.appwidget.updateAll
import io.github.aedev.flow.widget.downloads.DownloadsWidget
import io.github.aedev.flow.widget.nowplaying.NowPlayingWidget
import io.github.aedev.flow.widget.quickactions.QuickActionsWidget
import io.github.aedev.flow.widget.recent.RecentlyPlayedWidget
import io.github.aedev.flow.widget.recognize.RecognizeWidget
import io.github.aedev.flow.widget.turntable.TurntableWidget

/** Registry of every Flow widget — used to re-render all of them on app theme changes. */
object FlowWidgets {
    suspend fun updateAll(context: Context) {
        NowPlayingWidget().updateAll(context)
        TurntableWidget().updateAll(context)
        QuickActionsWidget().updateAll(context)
        RecognizeWidget().updateAll(context)
        RecentlyPlayedWidget().updateAll(context)
        DownloadsWidget().updateAll(context)
    }
}
