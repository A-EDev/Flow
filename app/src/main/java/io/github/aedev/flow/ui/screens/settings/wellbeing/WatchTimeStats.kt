package io.github.aedev.flow.ui.screens.settings.wellbeing

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

internal const val WEEK_DAYS = 7

/** Time watched on one calendar day. */
@Immutable
data class DayWatchTime(
    val date: LocalDate,
    val millis: Long,
)

/** The last seven days of watch time, oldest first and ending today. */
@Immutable
data class WatchTimeSummary(
    val days: List<DayWatchTime>,
) {
    val todayMillis: Long get() = days.lastOrNull()?.millis ?: 0L
    val weekMillis: Long get() = days.sumOf { it.millis }
    val dailyAverageMillis: Long get() = weekMillis / WEEK_DAYS
    val isEmpty: Boolean get() = days.all { it.millis == 0L }

    companion object {
        fun empty(today: LocalDate) = WatchTimeSummary((WEEK_DAYS - 1 downTo 0).map { DayWatchTime(today.minusDays(it.toLong()), 0L) })
    }
}

/** A watched item: when it was last watched and how far into it playback got. */
data class WatchRecord(
    val timestamp: Long,
    val watchedMillis: Long,
)

/**
 * Buckets [records] into the seven calendar days ending [today] in [zone]. A record counts on the
 * day it was last watched, for as far as playback got, which is the only duration history keeps.
 */
internal fun summarizeWatchTime(
    records: List<WatchRecord>,
    today: LocalDate,
    zone: ZoneId,
): WatchTimeSummary {
    val first = today.minusDays((WEEK_DAYS - 1).toLong())
    val totals = LongArray(WEEK_DAYS)
    records.forEach { record ->
        val day = Instant.ofEpochMilli(record.timestamp).atZone(zone).toLocalDate()
        if (day < first || day > today) return@forEach
        totals[(day.toEpochDay() - first.toEpochDay()).toInt()] += record.watchedMillis.coerceAtLeast(0L)
    }
    return WatchTimeSummary(totals.mapIndexed { index, millis -> DayWatchTime(first.plusDays(index.toLong()), millis) })
}
