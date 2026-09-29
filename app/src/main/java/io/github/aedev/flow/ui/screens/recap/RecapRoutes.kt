package io.github.aedev.flow.ui.screens.recap

import io.github.aedev.flow.data.stats.LedgerTime
import io.github.aedev.flow.data.stats.RecapPeriod

/** Routes for the recap surfaces, with the period carried as "2026-09", "2026" or "all". */
object RecapRoutes {
    const val STATS = "recap?period={period}"
    const val STORY = "recap/story/{period}"
    const val ARG_PERIOD = "period"
    private const val ALL = "all"

    fun stats(period: RecapPeriod? = null): String = period?.let { "recap?period=${encode(it)}" } ?: "recap"

    fun story(period: RecapPeriod): String = "recap/story/${encode(period)}"

    fun encode(period: RecapPeriod): String =
        when (period) {
            is RecapPeriod.Month -> LedgerTime.monthKey(period.month)
            is RecapPeriod.Year -> period.year.toString()
            RecapPeriod.AllTime -> ALL
        }

    fun decode(value: String?): RecapPeriod? =
        when {
            value.isNullOrBlank() -> null
            value == ALL -> RecapPeriod.AllTime
            value.length == YEAR_LENGTH -> value.toIntOrNull()?.let { RecapPeriod.Year(it) }
            else -> LedgerTime.parseMonth(value)?.let { RecapPeriod.Month(it) }
        }

    private const val YEAR_LENGTH = 4
}
