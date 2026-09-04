package io.github.aedev.flow.ui.components.library

import androidx.annotation.StringRes
import io.github.aedev.flow.R
import io.github.aedev.flow.data.model.Video

private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_MINUTE = 60_000L
private const val MILLIS_PER_HOUR = 3_600_000L
private const val MILLIS_PER_DAY = 86_400_000L
private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH = 30L
private const val DAYS_PER_YEAR = 365L
private const val TIMESTAMP_TOLERANCE_MS = 30L * 60L * 1000L

private val DigitsRegex = Regex("""(\d+)""")
private val SecondsShorthand = Regex(""".*\d+\s*s$""")
private val MinutesShorthand = Regex(""".*\d+\s*m$""")
private val HoursShorthand = Regex(""".*\d+\s*h$""")
private val DaysShorthand = Regex(""".*\d+\s*d$""")
private val WeeksShorthand = Regex(""".*\d+\s*w$""")
private val MonthsShorthand = Regex(""".*\d+\s*mo$""")
private val YearsShorthand = Regex(""".*\d+\s*y$""")

enum class PlaylistSortOrder(
    val storageValue: String,
    @param:StringRes val labelRes: Int,
) {
    MANUAL("manual", R.string.playlist_sort_manual),
    DATE_ADDED_NEWEST("date_added_newest", R.string.playlist_sort_date_added_newest),
    DATE_ADDED_OLDEST("date_added_oldest", R.string.playlist_sort_date_added_oldest),
    MOST_POPULAR("most_popular", R.string.playlist_sort_most_popular),
    DATE_PUBLISHED_NEWEST("date_published_newest", R.string.playlist_sort_date_published_newest),
    DATE_PUBLISHED_OLDEST("date_published_oldest", R.string.playlist_sort_date_published_oldest),
    ;

    companion object {
        fun fromStorageValue(value: String?): PlaylistSortOrder = entries.firstOrNull { it.storageValue == value } ?: MANUAL
    }
}

internal fun List<Video>.sortedForPlaylist(sortOrder: PlaylistSortOrder): List<Video> =
    when (sortOrder) {
        PlaylistSortOrder.MANUAL,
        PlaylistSortOrder.DATE_ADDED_NEWEST,
        -> this

        PlaylistSortOrder.DATE_ADDED_OLDEST -> asReversed()

        PlaylistSortOrder.MOST_POPULAR -> sortedByDescending { it.viewCount }

        PlaylistSortOrder.DATE_PUBLISHED_NEWEST -> sortedByPublishDate(descending = true)

        PlaylistSortOrder.DATE_PUBLISHED_OLDEST -> sortedByPublishDate(descending = false)
    }

private fun List<Video>.sortedByPublishDate(descending: Boolean): List<Video> {
    val now = System.currentTimeMillis()
    val keyed = map { it to it.effectivePlaylistUploadTimestamp(now) }
    val ordered = if (descending) keyed.sortedByDescending { it.second } else keyed.sortedBy { it.second }
    return ordered.map { it.first }
}

private fun Video.effectivePlaylistUploadTimestamp(now: Long): Long {
    val relativeDuration = parseRelativeDurationMillis(uploadDate)
    if (timestamp <= 0L) {
        return relativeDuration?.let { now - it } ?: 0L
    }
    if (relativeDuration == null) return timestamp

    val timestampAge = now - timestamp
    return if (timestampAge > relativeDuration + TIMESTAMP_TOLERANCE_MS) {
        timestamp
    } else {
        now - relativeDuration
    }
}

private fun parseRelativeDurationMillis(text: String): Long? {
    val normalized =
        text
            .lowercase()
            .replace("streamed", "")
            .replace("premiered", "")
            .replace("ago", "")
            .trim()
    if (normalized.isBlank() || normalized == "unknown") return null
    if (normalized.contains("just now") || normalized == "today") return 0L
    if (normalized.contains("yesterday")) return MILLIS_PER_DAY

    val value =
        DigitsRegex
            .find(normalized)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: return null
    val unit =
        when {
            normalized.contains("second") || SecondsShorthand.matches(normalized) -> MILLIS_PER_SECOND
            normalized.contains("minute") || MinutesShorthand.matches(normalized) -> MILLIS_PER_MINUTE
            normalized.contains("hour") || HoursShorthand.matches(normalized) -> MILLIS_PER_HOUR
            normalized.contains("day") || DaysShorthand.matches(normalized) -> MILLIS_PER_DAY
            normalized.contains("week") || WeeksShorthand.matches(normalized) -> DAYS_PER_WEEK * MILLIS_PER_DAY
            normalized.contains("month") || MonthsShorthand.matches(normalized) -> DAYS_PER_MONTH * MILLIS_PER_DAY
            normalized.contains("year") || YearsShorthand.matches(normalized) -> DAYS_PER_YEAR * MILLIS_PER_DAY
            else -> return null
        }
    return value * unit
}
