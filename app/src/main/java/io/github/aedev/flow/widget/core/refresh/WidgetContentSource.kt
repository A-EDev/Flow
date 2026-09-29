package io.github.aedev.flow.widget.core.refresh

import androidx.glance.appwidget.GlanceAppWidget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** The data behind one kind of content widget, as far as deciding when to re-render it. */
interface WidgetContentSource {
    val key: WidgetContentKey
    val widget: GlanceAppWidget

    /** Emits when what the widget shows may have changed. Sources without one are driven by [WidgetContentSync.request]. */
    fun changes(): Flow<Unit> = emptyFlow()

    /** A compact form of exactly what the widget draws; a re-render happens only when it changes. */
    suspend fun signature(): String
}

enum class WidgetContentKey {
    RECENTLY_PLAYED,
    DOWNLOADS,
    ON_REPEAT,
    PLAYLISTS,
    WEEK,
}
