package io.github.aedev.flow.widget.core.refresh

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.aedev.flow.widget.downloads.DownloadsSource
import io.github.aedev.flow.widget.onrepeat.OnRepeatSource
import io.github.aedev.flow.widget.playlist.PlaylistWidgetSource
import io.github.aedev.flow.widget.recent.RecentlyPlayedSource
import io.github.aedev.flow.widget.week.WeekSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

private val Context.widgetContentStore by preferencesDataStore(name = "widget_content")

/**
 * Re-renders a content widget when what it draws has changed, and only while one is placed.
 *
 * The last signature each kind was rendered with is persisted, for the same reason the theme's is
 * ([io.github.aedev.flow.widget.core.FlowWidgets]): a process start must not re-render every placed
 * widget for nothing, since each render holds a Glance session that taxes the app's frames.
 */
@Singleton
class WidgetContentSync
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        recentlyPlayed: RecentlyPlayedSource,
        downloads: DownloadsSource,
        onRepeat: OnRepeatSource,
        playlists: PlaylistWidgetSource,
        week: WeekSource,
    ) {
        private val sources = listOf(recentlyPlayed, downloads, onRepeat, playlists, week)
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private val placementChanged = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        private val requests = MutableSharedFlow<WidgetContentKey>(extraBufferCapacity = 8)
        private val renderLock = Mutex()
        private var job: Job? = null

        @Synchronized
        fun ensureStarted() {
            if (job?.isActive == true) return
            job = scope.launch { sources.forEach { source -> launch { follow(source) } } }
        }

        /** Starts following only when a content widget is on the home screen, which most installs never have. */
        suspend fun startIfPlaced() {
            if (sources.any { isPlaced(it) }) ensureStarted()
        }

        /** A widget of some kind was added or the last of a kind removed. */
        fun onPlacementChanged() {
            ensureStarted()
            placementChanged.tryEmit(Unit)
        }

        /** For sources with no change stream; settles like one, so a write still in flight lands first. */
        fun request(key: WidgetContentKey) {
            ensureStarted()
            requests.tryEmit(key)
        }

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        private suspend fun follow(source: WidgetContentSource) {
            placementChanged
                .map { isPlaced(source) }
                .distinctUntilChanged()
                .flatMapLatest { placed ->
                    if (placed) {
                        merge(source.changes(), requests.filter { it == source.key }.map { }).onStart { emit(Unit) }
                    } else {
                        emptyFlow()
                    }
                }.debounce(SETTLE_MS)
                .collect { renderIfChanged(source) }
        }

        private suspend fun isPlaced(source: WidgetContentSource): Boolean =
            GlanceAppWidgetManager(context).getGlanceIds(source.widget.javaClass).isNotEmpty()

        private suspend fun renderIfChanged(source: WidgetContentSource) {
            val signature =
                try {
                    source.signature()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not read ${source.key}: ${e.message}")
                    return
                }
            val key = stringPreferencesKey(source.key.name)
            renderLock.withLock {
                if (context.widgetContentStore.data.first()[key] == signature) return
                context.widgetContentStore.edit { it[key] = signature }
            }
            source.widget.updateAll(context)
        }

        private companion object {
            const val TAG = "WidgetContentSync"
            const val SETTLE_MS = 2_000L
        }
    }
