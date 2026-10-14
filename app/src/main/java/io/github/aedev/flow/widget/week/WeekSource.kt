package io.github.aedev.flow.widget.week

import androidx.glance.appwidget.GlanceAppWidget
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.stats.RecapAggregates
import io.github.aedev.flow.data.stats.RecapImageResolver
import io.github.aedev.flow.data.stats.RecapPeriod
import io.github.aedev.flow.data.stats.VideoStatsRecorder
import io.github.aedev.flow.data.stats.withImages
import io.github.aedev.flow.widget.core.refresh.WidgetContentKey
import io.github.aedev.flow.widget.core.refresh.WidgetContentSource
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/** The last seven days, oldest first, and the month's top artist. */
data class WidgetWeek(
    val days: List<WeekDay>,
    val topArtist: String?,
    val topArtistImage: String?,
) {
    val totalMs: Long get() = days.sumOf { it.ms }
}

data class WeekDay(
    val date: LocalDate,
    val ms: Long,
)

/** Reads the Recap ledgers. Watching saves announce themselves; a finished listen requests a refresh. */
@Singleton
class WeekSource
    @Inject
    constructor(
        private val videoStats: VideoStatsRecorder,
        private val musicBrain: MusicBrainEngine,
        private val images: RecapImageResolver,
    ) : WidgetContentSource {
        override val key = WidgetContentKey.WEEK
        override val widget: GlanceAppWidget get() = WeekWidget()

        override fun changes(): Flow<Unit> = videoStats.changes

        suspend fun load(today: LocalDate = LocalDate.now()): WidgetWeek {
            val video = videoStats.snapshot()
            val music = musicBrain.listeningStats()
            val from = today.minusDays(DAYS - 1L)
            val daily = RecapAggregates.dailyTime(video, music, from, today)
            val artist =
                RecapAggregates
                    .summarize(RecapPeriod.Month(YearMonth.from(today)), video, music)
                    .withImages(images.localImages())
                    .music.topArtists
                    .firstOrNull()
            return WidgetWeek(
                days = (0 until DAYS).map { offset -> from.plusDays(offset.toLong()).let { WeekDay(it, daily[it] ?: 0L) } },
                topArtist = artist?.name,
                topArtistImage = artist?.imageUrl?.takeIf { it.isNotBlank() },
            )
        }

        // Whole minutes are all the widget draws, so a second of listening is no reason to render.
        override suspend fun signature(): String =
            load().let { week -> week.days.joinToString(",") { "${it.date}:${it.ms / 60_000}" } + "|" + week.topArtist }

        companion object {
            const val DAYS = 7
        }
    }
