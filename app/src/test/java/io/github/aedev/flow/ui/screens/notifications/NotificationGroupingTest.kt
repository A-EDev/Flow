package io.github.aedev.flow.ui.screens.notifications

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.entity.NotificationEntity
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class NotificationGroupingTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 9, 24)

    private fun at(
        date: LocalDate,
        hour: Int,
        minute: Int = 0,
    ): Long =
        LocalDateTime
            .of(date, LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun notification(
        id: Int,
        timestamp: Long,
    ) = NotificationEntity(id = id, videoId = "v$id", title = "t$id", channelName = "c", thumbnailUrl = null, timestamp = timestamp)

    @Test
    fun `late last night is yesterday, not today`() {
        assertThat(dayBucket(at(today.minusDays(1), 23, 30), today, zone)).isEqualTo(NotificationBucket.YESTERDAY)
        assertThat(dayBucket(at(today, 0, 5), today, zone)).isEqualTo(NotificationBucket.TODAY)
    }

    @Test
    fun `the last seven days end six days back`() {
        assertThat(dayBucket(at(today.minusDays(6), 9), today, zone)).isEqualTo(NotificationBucket.LAST_WEEK)
        assertThat(dayBucket(at(today.minusDays(7), 23), today, zone)).isEqualTo(NotificationBucket.EARLIER)
    }

    @Test
    fun `a timestamp ahead of the clock counts as today`() {
        assertThat(dayBucket(at(today.plusDays(1), 1), today, zone)).isEqualTo(NotificationBucket.TODAY)
    }

    @Test
    fun `days are calendar days across a daylight saving change`() {
        val afterChange = LocalDate.of(2026, 3, 30)
        assertThat(dayBucket(at(afterChange.minusDays(1), 23, 59), afterChange, zone)).isEqualTo(NotificationBucket.YESTERDAY)
        assertThat(dayBucket(at(afterChange.minusDays(2), 23, 59), afterChange, zone)).isEqualTo(NotificationBucket.LAST_WEEK)
    }

    @Test
    fun `new notifications lead and the rest follow by day in order`() {
        val items =
            listOf(
                notification(1, at(today, 18)),
                notification(2, at(today, 9)),
                notification(3, at(today.minusDays(1), 20)),
                notification(4, at(today.minusDays(3), 8)),
                notification(5, at(today.minusDays(40), 8)),
            )

        val sections = groupNotifications(items, newIds = setOf(1, 3), today = today, zone = zone)

        assertThat(sections.map { it.bucket })
            .containsExactly(
                NotificationBucket.NEW,
                NotificationBucket.TODAY,
                NotificationBucket.LAST_WEEK,
                NotificationBucket.EARLIER,
            ).inOrder()
        assertThat(sections.first().items.map { it.id }).containsExactly(1, 3).inOrder()
    }

    @Test
    fun `older rows carry a weekday or a date instead of a clock time`() {
        assertThat(notificationTime(at(today, 18), today, zone)).isInstanceOf(NotificationTime.Relative::class.java)
        assertThat(notificationTime(at(today.minusDays(1), 18, 24), today, zone))
            .isEqualTo(NotificationTime.TimeOfDay(LocalTime.of(18, 24)))
        assertThat(notificationTime(at(LocalDate.of(2026, 9, 21), 12), today, zone))
            .isEqualTo(NotificationTime.Weekday(DayOfWeek.MONDAY))
        assertThat(notificationTime(at(LocalDate.of(2026, 9, 2), 14, 2), today, zone))
            .isEqualTo(NotificationTime.Date(LocalDate.of(2026, 9, 2)))
    }
}
