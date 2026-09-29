package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

/**
 * The watch page dates an upload twice: `dateText` is a date with no time ("Sep 17, 2026") and
 * `relativeDateText` is "4 hours ago". Resolving the display from the absolute one alone puts every
 * upload at midnight, so a video four hours old read as however long the day had been running.
 */
class DateOnlyUploadTest {
    private val zone = ZoneId.systemDefault()

    private fun millisAt(
        daysAgo: Long,
        hour: Int,
    ): Long =
        Instant
            .now()
            .atZone(zone)
            .minusDays(daysAgo)
            .withHour(hour)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()
            .toEpochMilli()

    private fun dateStringFor(millis: Long): String =
        java.text
            .SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
            .format(java.util.Date(millis))

    @Test
    fun `a stored time on the same day wins over the date-only parse`() {
        val published = millisAt(daysAgo = 0, hour = 7)

        val resolved = resolveDisplayUploadTimestamp(dateStringFor(published), published)

        assertThat(resolved).isEqualTo(published)
    }

    @Test
    fun `a stored time on another day does not override the date`() {
        val published = millisAt(daysAgo = 3, hour = 7)
        val staleStored = millisAt(daysAgo = 0, hour = 9)

        val resolved = resolveDisplayUploadTimestamp(dateStringFor(published), staleStored)

        assertThat(resolved).isNotEqualTo(staleStored)
        assertThat(resolved).isNotNull()
    }

    @Test
    fun `with no stored time the date still resolves`() {
        val published = millisAt(daysAgo = 2, hour = 7)

        assertThat(resolveDisplayUploadTimestamp(dateStringFor(published), 0L)).isNotNull()
    }

    @Test
    fun `an unparseable date falls back to the stored time`() {
        val stored = millisAt(daysAgo = 1, hour = 5)

        assertThat(resolveDisplayUploadTimestamp("not a date at all", stored)).isEqualTo(stored)
    }

    @Test
    fun `a relative date still wins, as the earlier of the two`() {
        val stored = millisAt(daysAgo = 0, hour = 9)

        val resolved = resolveDisplayUploadTimestamp("4 hours ago", stored)

        assertThat(resolved).isAtMost(stored)
    }
}
