package io.github.aedev.flow.data.stats

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** The span a recap covers. */
sealed interface RecapPeriod {
    data class Month(
        val month: YearMonth,
    ) : RecapPeriod

    data class Year(
        val year: Int,
    ) : RecapPeriod

    data object AllTime : RecapPeriod

    /** The span before this one, for "compared with last month". */
    fun previous(): RecapPeriod? =
        when (this) {
            is Month -> Month(month.minusMonths(1))
            is Year -> Year(year - 1)
            AllTime -> null
        }

    fun contains(month: YearMonth): Boolean =
        when (this) {
            is Month -> this.month == month
            is Year -> month.year == year
            AllTime -> true
        }
}

/** One row of a ranked list: a channel, video, artist, track, topic or genre. */
data class RankedItem(
    val id: String,
    val name: String,
    val count: Int,
    val durationMs: Long = 0L,
    /** A secondary line: the channel of a video, or the artist of a track. */
    val detail: String = "",
    /** A portrait for the item: a channel avatar, video thumbnail or track artwork; blank when unknown. */
    val imageUrl: String = "",
)

/** Watching or listening folded over a period, down to the day. */
data class ActivityPattern(
    val totalMs: Long,
    val dayMs: Map<LocalDate, Long>,
    /** Counted views or plays per hour of day, 0..23. */
    val hourCounts: List<Int>,
    /** Counted views or plays per weekday, Monday first. */
    val weekdayCounts: List<Int>,
) {
    val activeDays: Int get() = dayMs.count { it.value > 0L }

    val busiestDay: Pair<LocalDate, Long>? get() = dayMs.maxByOrNull { it.value }?.toPair()

    /** The longest run of consecutive days with any activity. */
    val longestStreak: Int
        get() {
            val days = dayMs.filterValues { it > 0L }.keys.sorted()
            var best = 0
            var run = 0
            var previous: LocalDate? = null
            for (day in days) {
                run = if (previous != null && previous.plusDays(1) == day) run + 1 else 1
                best = maxOf(best, run)
                previous = day
            }
            return best
        }

    companion object {
        val Empty = ActivityPattern(0L, emptyMap(), List(24) { 0 }, List(7) { 0 })

        fun weekdayIndex(day: DayOfWeek): Int = day.value - 1
    }
}

data class VideoRecap(
    val views: Int,
    val sessions: Int,
    val activity: ActivityPattern,
    val formatViews: Map<ViewFormat, Int>,
    val formatMs: Map<ViewFormat, Long>,
    val topChannels: List<RankedItem>,
    val topVideos: List<RankedItem>,
    val topTopics: List<RankedItem>,
    /** Topics in this period's top list that were absent from the previous period. */
    val newTopics: List<String>,
    val discoveredChannels: List<RankedItem>,
    val skippedVideos: List<RankedItem>,
    val skippedChannels: List<RankedItem>,
    val dislikes: List<DislikedVideo>,
    val actions: Map<LedgerAction, Int>,
    val topQueries: List<RankedItem>,
    val sponsorSkippedMs: Map<String, Long>,
) {
    val sponsorSavedMs: Long get() = sponsorSkippedMs.values.sum()
    val isEmpty: Boolean get() = views == 0 && activity.totalMs == 0L
}

data class MusicRecap(
    val plays: Int,
    val sessions: Int,
    val activity: ActivityPattern,
    val topArtists: List<RankedItem>,
    val topTracks: List<RankedItem>,
    val topGenres: List<RankedItem>,
    val discoveredArtists: List<RankedItem>,
    val skippedTracks: List<RankedItem>,
    val skippedArtists: List<RankedItem>,
    val dislikedArtists: List<RankedItem>,
    val blockedArtists: List<RankedItem>,
) {
    val isEmpty: Boolean get() = plays == 0 && activity.totalMs == 0L
}

/** Traits a recap can honestly claim from its own numbers. */
enum class RecapInsight { NIGHT_OWL, EARLY_BIRD, WEEKEND_WATCHER, LOYAL, EXPLORER, MARATHON, TIME_SAVER }

data class RecapSummary(
    val period: RecapPeriod,
    val video: VideoRecap,
    val music: MusicRecap,
    /** Both sources together, for the headline numbers, the clock and the calendar. */
    val combined: ActivityPattern,
    /** The previous period's combined time, or null when there is nothing to compare with. */
    val previousTotalMs: Long?,
    val insights: List<RecapInsight>,
) {
    val isEmpty: Boolean get() = video.isEmpty && music.isEmpty
}
