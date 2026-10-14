package io.github.aedev.flow.ui.screens.notifications

import io.github.aedev.flow.data.local.entity.NotificationEntity
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

private const val LAST_WEEK_DAYS = 6L

/** The inbox's sections, in the order they are shown. */
internal enum class NotificationBucket { NEW, TODAY, YESTERDAY, LAST_WEEK, EARLIER }

internal data class NotificationSection(
    val bucket: NotificationBucket,
    val items: List<NotificationEntity>,
)

/** When a notification arrived, in the form its row shows it. */
internal sealed interface NotificationTime {
    data class Relative(
        val timestampMs: Long,
    ) : NotificationTime

    data class TimeOfDay(
        val time: LocalTime,
    ) : NotificationTime

    data class Weekday(
        val day: DayOfWeek,
    ) : NotificationTime

    data class Date(
        val date: LocalDate,
    ) : NotificationTime
}

/** The calendar-day section for [timestampMs]; anything stamped after [today] still counts as today. */
internal fun dayBucket(
    timestampMs: Long,
    today: LocalDate,
    zone: ZoneId,
): NotificationBucket {
    val day = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
    return when {
        !day.isBefore(today) -> NotificationBucket.TODAY
        day == today.minusDays(1) -> NotificationBucket.YESTERDAY
        !day.isBefore(today.minusDays(LAST_WEEK_DAYS)) -> NotificationBucket.LAST_WEEK
        else -> NotificationBucket.EARLIER
    }
}

/**
 * Splits [items] (newest first) into sections: everything in [newIds] first, then the rest by
 * calendar day. Order inside a section is kept.
 */
internal fun groupNotifications(
    items: List<NotificationEntity>,
    newIds: Set<Int>,
    today: LocalDate,
    zone: ZoneId,
): List<NotificationSection> {
    val (fresh, seen) = items.partition { it.id in newIds }
    return buildList {
        if (fresh.isNotEmpty()) add(NotificationSection(NotificationBucket.NEW, fresh))
        seen
            .groupBy { dayBucket(it.timestamp, today, zone) }
            .toSortedMap()
            .forEach { (bucket, sectionItems) -> add(NotificationSection(bucket, sectionItems)) }
    }
}

internal fun notificationTime(
    timestampMs: Long,
    today: LocalDate,
    zone: ZoneId,
): NotificationTime {
    val moment = Instant.ofEpochMilli(timestampMs).atZone(zone)
    return when (dayBucket(timestampMs, today, zone)) {
        NotificationBucket.NEW, NotificationBucket.TODAY -> NotificationTime.Relative(timestampMs)
        NotificationBucket.YESTERDAY -> NotificationTime.TimeOfDay(moment.toLocalTime())
        NotificationBucket.LAST_WEEK -> NotificationTime.Weekday(moment.dayOfWeek)
        NotificationBucket.EARLIER -> NotificationTime.Date(moment.toLocalDate())
    }
}
