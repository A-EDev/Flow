package io.github.aedev.flow.widget.recent

import androidx.glance.appwidget.GlanceAppWidget
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.widget.core.refresh.WidgetContentKey
import io.github.aedev.flow.widget.core.refresh.WidgetContentSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecentlyPlayedSource
    @Inject
    constructor(
        private val viewHistory: ViewHistory,
    ) : WidgetContentSource {
        override val key = WidgetContentKey.RECENTLY_PLAYED
        override val widget: GlanceAppWidget get() = RecentlyPlayedWidget()

        suspend fun entries(): List<VideoHistoryEntry> = viewHistory.getRecentVideoHistory(MAX_ITEMS, includeShorts = true)

        // Progress is saved every few seconds while a video plays; only a visible step on the bar counts.
        override fun changes(): Flow<Unit> =
            viewHistory
                .observeRecentVideoHistory(MAX_ITEMS, includeShorts = true)
                .map(::signatureOf)
                .distinctUntilChanged()
                .map { }

        override suspend fun signature(): String = signatureOf(entries())

        companion object {
            const val MAX_ITEMS = 8

            internal fun signatureOf(entries: List<VideoHistoryEntry>): String =
                entries.joinToString(",") { "${it.videoId}:${progressStep(it.position, it.duration)}" }

            internal fun progressStep(
                positionMs: Long,
                durationMs: Long,
            ): Int = if (durationMs <= 0L) 0 else (positionMs.coerceIn(0L, durationMs) * PROGRESS_STEPS / durationMs).toInt()

            private const val PROGRESS_STEPS = 20L
        }
    }
