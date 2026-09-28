package io.github.aedev.flow.widget.core

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import io.github.aedev.flow.BuildConfig
import io.github.aedev.flow.R
import io.github.aedev.flow.widget.core.theme.lastWidgetPreviewStamp
import io.github.aedev.flow.widget.core.theme.lastWidgetThemeSignature
import io.github.aedev.flow.widget.core.theme.widgetThemeSignatureFlow
import io.github.aedev.flow.widget.core.theme.writeWidgetPreviewStamp
import io.github.aedev.flow.widget.core.theme.writeWidgetThemeSignature
import io.github.aedev.flow.widget.downloads.DownloadsWidget
import io.github.aedev.flow.widget.downloads.DownloadsWidgetReceiver
import io.github.aedev.flow.widget.nowplaying.NowPlayingWidget
import io.github.aedev.flow.widget.nowplaying.NowPlayingWidgetReceiver
import io.github.aedev.flow.widget.onrepeat.OnRepeatWidget
import io.github.aedev.flow.widget.onrepeat.OnRepeatWidgetReceiver
import io.github.aedev.flow.widget.playlist.PlaylistWidget
import io.github.aedev.flow.widget.playlist.PlaylistWidgetReceiver
import io.github.aedev.flow.widget.quickactions.QuickActionsWidget
import io.github.aedev.flow.widget.quickactions.QuickActionsWidgetReceiver
import io.github.aedev.flow.widget.recent.RecentlyPlayedWidget
import io.github.aedev.flow.widget.recent.RecentlyPlayedWidgetReceiver
import io.github.aedev.flow.widget.recognize.RecognizeWidget
import io.github.aedev.flow.widget.recognize.RecognizeWidgetReceiver
import io.github.aedev.flow.widget.turntable.TurntableWidget
import io.github.aedev.flow.widget.turntable.TurntableWidgetReceiver
import io.github.aedev.flow.widget.week.WeekWidget
import io.github.aedev.flow.widget.week.WeekWidgetReceiver
import kotlin.reflect.KClass

/** Registry of every Flow widget: re-renders them on theme changes, publishes previews, lists them in Settings. */
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
            publishPreviewsIfStale(appContext, persisted)
            if (persisted == lastSignature) return@collect
            lastSignature = persisted
            appContext.writeWidgetThemeSignature(persisted)
            updateAll(appContext)
        }
    }

    suspend fun updateAll(context: Context) {
        catalog.forEach { it.widget().updateAll(context) }
    }

    /**
     * Re-renders the picker previews (Android 15+) after an update or a palette change. The platform
     * rate-limits the call, so it runs once per version and theme, never on every launch.
     */
    private suspend fun publishPreviewsIfStale(
        context: Context,
        themeSignature: String,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) return
        val stamp = "${BuildConfig.VERSION_CODE}|$themeSignature"
        if (context.lastWidgetPreviewStamp() == stamp) return
        val manager = GlanceAppWidgetManager(context)
        val published =
            catalog.all { entry ->
                manager.setWidgetPreviews(entry.receiver) == GlanceAppWidgetManager.SET_WIDGET_PREVIEWS_RESULT_SUCCESS
            }
        Log.i(TAG, "Widget previews published: $published")
        if (published) context.writeWidgetPreviewStamp(stamp)
    }

    private const val TAG = "FlowWidgets"

    val catalog: List<FlowWidgetEntry> =
        listOf(
            FlowWidgetEntry(
                id = "now_playing",
                receiver = NowPlayingWidgetReceiver::class,
                widget = ::NowPlayingWidget,
                label = R.string.widget_now_playing_label,
                description = R.string.widget_now_playing_description,
                icon = R.drawable.ic_widget_music,
            ),
            FlowWidgetEntry(
                id = "turntable",
                receiver = TurntableWidgetReceiver::class,
                widget = ::TurntableWidget,
                label = R.string.widget_turntable_label,
                description = R.string.widget_turntable_description,
                icon = R.drawable.ic_music_note,
            ),
            FlowWidgetEntry(
                id = "on_repeat",
                receiver = OnRepeatWidgetReceiver::class,
                widget = ::OnRepeatWidget,
                label = R.string.widget_on_repeat_label,
                description = R.string.widget_on_repeat_description,
                icon = R.drawable.ic_repeat,
            ),
            FlowWidgetEntry(
                id = "playlist",
                receiver = PlaylistWidgetReceiver::class,
                widget = ::PlaylistWidget,
                label = R.string.widget_playlist_label,
                description = R.string.widget_playlist_description,
                icon = R.drawable.ic_widget_library,
            ),
            FlowWidgetEntry(
                id = "week",
                receiver = WeekWidgetReceiver::class,
                widget = ::WeekWidget,
                label = R.string.widget_week_label,
                description = R.string.widget_week_description,
                icon = R.drawable.ic_widget_history,
            ),
            FlowWidgetEntry(
                id = "continue_watching",
                receiver = RecentlyPlayedWidgetReceiver::class,
                widget = ::RecentlyPlayedWidget,
                label = R.string.widget_recently_played_label,
                description = R.string.widget_recently_played_description,
                icon = R.drawable.ic_widget_history,
            ),
            FlowWidgetEntry(
                id = "downloads",
                receiver = DownloadsWidgetReceiver::class,
                widget = ::DownloadsWidget,
                label = R.string.widget_downloads_label,
                description = R.string.widget_downloads_description,
                icon = R.drawable.ic_widget_download,
            ),
            FlowWidgetEntry(
                id = "quick_actions",
                receiver = QuickActionsWidgetReceiver::class,
                widget = ::QuickActionsWidget,
                label = R.string.widget_quick_actions_label,
                description = R.string.widget_quick_actions_description,
                icon = R.drawable.ic_widget_search,
            ),
            FlowWidgetEntry(
                id = "recognize",
                receiver = RecognizeWidgetReceiver::class,
                widget = ::RecognizeWidget,
                label = R.string.widget_recognize_label,
                description = R.string.widget_recognize_description,
                icon = R.drawable.ic_widget_mic,
            ),
        )
}

/** One widget as Settings lists it: what to pin, and how to describe it. */
class FlowWidgetEntry(
    val id: String,
    val receiver: KClass<out GlanceAppWidgetReceiver>,
    val widget: () -> GlanceAppWidget,
    @StringRes val label: Int,
    @StringRes val description: Int,
    @DrawableRes val icon: Int,
)
