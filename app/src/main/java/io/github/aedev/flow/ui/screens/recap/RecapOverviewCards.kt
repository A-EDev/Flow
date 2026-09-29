package io.github.aedev.flow.ui.screens.recap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.R
import io.github.aedev.flow.data.stats.ActivityPattern
import io.github.aedev.flow.data.stats.RecapInsight
import io.github.aedev.flow.data.stats.RecapPeriod
import io.github.aedev.flow.data.stats.RecapSummary
import io.github.aedev.flow.ui.components.stats.StatBarStrip
import io.github.aedev.flow.ui.components.stats.StatBigNumber
import io.github.aedev.flow.ui.components.stats.StatCalendar
import io.github.aedev.flow.ui.components.stats.StatCard
import io.github.aedev.flow.ui.components.stats.StatClock
import io.github.aedev.flow.ui.components.stats.rememberStatEntrance
import io.github.aedev.flow.ui.components.stats.spentTimeLabel
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val FigureSpacing = 24.dp
private val InsightSpacing = 12.dp
private const val SAME_SHARE = 0.05
private const val MONTHS_PER_YEAR = 12
private const val RECENT_MONTHS = 12

/** The headline: total time against the period before, and the counts behind it. */
@Composable
internal fun OverviewCard(
    summary: RecapSummary,
    activity: ActivityPattern,
    source: RecapSource,
    onPlayStory: (() -> Unit)?,
) {
    StatCard(title = stringResource(R.string.recap_total_time)) {
        StatBigNumber(
            value = spentTimeLabel(activity.totalMs),
            label = deltaLabel(activity.totalMs, summary.previousTotalMs),
            emphasized = true,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(FigureSpacing), verticalArrangement = Arrangement.spacedBy(InsightSpacing)) {
            if (source != RecapSource.MUSIC) StatBigNumber(summary.video.views.toString(), stringResource(R.string.recap_views))
            if (source != RecapSource.VIDEO) StatBigNumber(summary.music.plays.toString(), stringResource(R.string.recap_plays))
            StatBigNumber(activity.activeDays.toString(), stringResource(R.string.recap_active_days))
            StatBigNumber(
                pluralStringResource(R.plurals.recap_days, activity.longestStreak, activity.longestStreak),
                stringResource(R.string.recap_streak),
            )
        }
        onPlayStory?.let { play ->
            Button(onClick = play) {
                Icon(Icons.Outlined.AutoStories, contentDescription = null)
                Text(stringResource(R.string.recap_play_story), modifier = Modifier.padding(start = ButtonIconGap))
            }
        }
    }
}

private val ButtonIconGap = 8.dp

@Composable
private fun deltaLabel(
    totalMs: Long,
    previousMs: Long?,
): String {
    if (previousMs == null || previousMs <= 0L) return stringResource(R.string.recap_total_time)
    val difference = totalMs - previousMs
    return when {
        abs(difference) <= previousMs * SAME_SHARE -> stringResource(R.string.recap_delta_same)
        difference > 0 -> stringResource(R.string.recap_delta_up, spentTimeLabel(difference))
        else -> stringResource(R.string.recap_delta_down, spentTimeLabel(-difference))
    }
}

@Composable
internal fun InsightsCard(insights: List<RecapInsight>) {
    StatCard(title = stringResource(R.string.recap_insights_title)) {
        insights.forEach { insight ->
            val (title, body) = insight.labels()
            Column {
                Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun RecapInsight.labels(): Pair<Int, Int> =
    when (this) {
        RecapInsight.NIGHT_OWL -> R.string.recap_insight_night_owl to R.string.recap_insight_night_owl_body
        RecapInsight.EARLY_BIRD -> R.string.recap_insight_early_bird to R.string.recap_insight_early_bird_body
        RecapInsight.WEEKEND_WATCHER -> R.string.recap_insight_weekend to R.string.recap_insight_weekend_body
        RecapInsight.LOYAL -> R.string.recap_insight_loyal to R.string.recap_insight_loyal_body
        RecapInsight.EXPLORER -> R.string.recap_insight_explorer to R.string.recap_insight_explorer_body
        RecapInsight.MARATHON -> R.string.recap_insight_marathon to R.string.recap_insight_marathon_body
        RecapInsight.TIME_SAVER -> R.string.recap_insight_time_saver to R.string.recap_insight_time_saver_body
    }

/** Minutes per day for a month, per month for a year or for all time. */
@Composable
internal fun TimeCard(
    period: RecapPeriod,
    activity: ActivityPattern,
    locale: Locale,
) {
    val series = remember(period, activity, locale) { timeSeries(period, activity, locale) }
    val entrance = rememberStatEntrance(period)
    val title = if (period is RecapPeriod.Month) R.string.recap_daily_title else R.string.recap_monthly_title
    val description = remember(series) { series.labels.zip(series.values).joinToString { (label, minutes) -> "$label ${minutes.toInt()}" } }
    StatCard(title = stringResource(title)) {
        StatBarStrip(
            values = series.values,
            entrance = entrance,
            description = description,
            labels = series.axis,
            edgeLabels = series.edges,
            highlight = series.values.indices.maxByOrNull { series.values[it] },
        )
        activity.busiestDay?.let { (day, ms) ->
            Text(
                text = stringResource(R.string.recap_busiest_day, day.format(dayFormatter(locale)), spentTimeLabel(ms)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private class TimeSeries(
    val values: List<Float>,
    val labels: List<String>,
    val axis: List<String>,
    val edges: List<String> = emptyList(),
)

private fun timeSeries(
    period: RecapPeriod,
    activity: ActivityPattern,
    locale: Locale,
): TimeSeries {
    val minute = 60_000f
    return when (period) {
        is RecapPeriod.Month -> {
            val days = (1..period.month.lengthOfMonth()).map { period.month.atDay(it) }
            TimeSeries(
                values = days.map { (activity.dayMs[it] ?: 0L) / minute },
                labels = days.map { it.dayOfMonth.toString() },
                axis = emptyList(),
                edges = listOf(1, (days.size + 1) / 2, days.size).map(Int::toString),
            )
        }

        else -> {
            val months =
                if (period is RecapPeriod.Year) {
                    (1..MONTHS_PER_YEAR).map { YearMonth.of(period.year, it) }
                } else {
                    val last =
                        activity.dayMs.keys
                            .maxOrNull()
                            ?.let(YearMonth::from) ?: YearMonth.now()
                    (RECENT_MONTHS - 1 downTo 0).map { last.minusMonths(it.toLong()) }
                }
            val totals = activity.dayMs.entries.groupBy({ YearMonth.from(it.key) }, { it.value })
            TimeSeries(
                values = months.map { (totals[it]?.sum() ?: 0L) / minute },
                labels = months.map { it.month.getDisplayName(TextStyle.SHORT_STANDALONE, locale) },
                axis = months.map { it.month.getDisplayName(TextStyle.NARROW_STANDALONE, locale) },
            )
        }
    }
}

@Composable
internal fun ClockCard(
    activity: ActivityPattern,
    key: Any,
) {
    val entrance = rememberStatEntrance(key)
    val description = activity.hourCounts.mapIndexed { hour, count -> "$hour:00 $count" }.joinToString()
    StatCard(title = stringResource(R.string.recap_clock_title), subtitle = stringResource(R.string.recap_clock_subtitle)) {
        StatClock(
            hourCounts = activity.hourCounts,
            entrance = entrance,
            description = description,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
internal fun WeekdayCard(
    activity: ActivityPattern,
    key: Any,
    locale: Locale,
) {
    val entrance = rememberStatEntrance(key)
    val days = DayOfWeek.entries
    val names = days.map { it.getDisplayName(TextStyle.SHORT_STANDALONE, locale) }
    StatCard(title = stringResource(R.string.recap_weekdays_title)) {
        StatBarStrip(
            values = activity.weekdayCounts.map { it.toFloat() },
            entrance = entrance,
            description = names.zip(activity.weekdayCounts).joinToString { (day, count) -> "$day $count" },
            labels = names,
            highlight = activity.weekdayCounts.indices.maxByOrNull { activity.weekdayCounts[it] },
        )
    }
}

@Composable
internal fun CalendarCard(
    month: YearMonth,
    activity: ActivityPattern,
    locale: Locale,
) {
    val entrance = rememberStatEntrance(month)
    val active = activity.dayMs.count { it.value > 0L && YearMonth.from(it.key) == month }
    StatCard(
        title = stringResource(R.string.recap_calendar_title),
        subtitle = pluralStringResource(R.plurals.recap_days, active, active),
    ) {
        StatCalendar(
            month = month,
            dayMs = activity.dayMs,
            entrance = entrance,
            description = pluralStringResource(R.plurals.recap_days, active, active),
            modifier = Modifier.fillMaxWidth(),
            locale = locale,
        )
    }
}

private fun dayFormatter(locale: Locale) = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
