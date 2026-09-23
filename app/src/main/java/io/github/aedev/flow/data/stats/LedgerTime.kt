package io.github.aedev.flow.data.stats

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

/** Where a moment falls in the local calendar, as every ledger buckets it. */
class LedgerMoment(
    val monthKey: String,
    val dayOfMonth: Int,
    val hourOfDay: Int,
)

object LedgerTime {
    fun at(
        nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): LedgerMoment {
        val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(nowMs), zone)
        return LedgerMoment(monthKey(YearMonth.from(time)), time.dayOfMonth, time.hour)
    }

    /** "2026-09", sortable as text. */
    fun monthKey(month: YearMonth): String = "%04d-%02d".format(month.year, month.monthValue)

    fun parseMonth(key: String): YearMonth? = runCatching { YearMonth.parse(key) }.getOrNull()

    /** Drops the weakest entries of [counts] beyond [cap], and their companions in [names]. */
    fun <V : Comparable<V>> capWeakest(
        counts: MutableMap<String, V>,
        cap: Int,
        vararg names: MutableMap<String, *>,
    ) {
        if (counts.size <= cap) return
        counts.entries
            .sortedBy { it.value }
            .take(counts.size - cap)
            .map { it.key }
            .forEach { key ->
                counts.remove(key)
                names.forEach { it.remove(key) }
            }
    }

    /** Drops the oldest months beyond [max]. */
    fun <M> capMonths(
        months: MutableMap<String, M>,
        max: Int,
    ) {
        if (months.size <= max) return
        months.keys
            .sorted()
            .take(months.size - max)
            .forEach { months.remove(it) }
    }
}
