package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * The watch page dates an archived livestream as "Streamed live on Jun 19, 2020" and a premiere as
 * "Premiered on …". Neither parsed, so the date fell back to whatever timestamp the card that
 * opened the video happened to carry — which for a card with none reads as a few minutes ago.
 */
class StreamedDateParsingTest {
    private fun midnightUtc(
        year: Int,
        month: Int,
        day: Int,
    ): Long =
        Calendar
            .getInstance(TimeZone.getDefault(), Locale.US)
            .apply {
                clear()
                set(year, month, day)
            }.timeInMillis

    @Test
    fun `an archived livestream date is read through its prefix`() {
        assertThat(parseToTimestamp("Streamed live on Jun 19, 2020"))
            .isEqualTo(midnightUtc(2020, Calendar.JUNE, 19))
    }

    @Test
    fun `a premiere date is read through its prefix`() {
        assertThat(parseToTimestamp("Premiered on Jan 1, 2020"))
            .isEqualTo(midnightUtc(2020, Calendar.JANUARY, 1))
        assertThat(parseToTimestamp("Premiered Jan 1, 2020"))
            .isEqualTo(midnightUtc(2020, Calendar.JANUARY, 1))
    }

    @Test
    fun `a plain date is unaffected`() {
        assertThat(parseToTimestamp("Jun 19, 2020")).isEqualTo(midnightUtc(2020, Calendar.JUNE, 19))
        assertThat(parseToTimestamp("2020-06-19")).isEqualTo(midnightUtc(2020, Calendar.JUNE, 19))
    }

    @Test
    fun `the relative form still resolves, through the same prefix strip`() {
        val sixYears = 6L * 365 * 24 * 60 * 60 * 1000
        val resolved = parseToTimestamp("Streamed 6 years ago")

        assertThat(resolved).isNotNull()
        assertThat(System.currentTimeMillis() - resolved!!).isAtLeast(sixYears - 7 * 24 * 60 * 60 * 1000)
    }

    @Test
    fun `nothing parseable stays null`() {
        assertThat(parseToTimestamp("Streamed live on")).isNull()
        assertThat(parseToTimestamp("")).isNull()
        assertThat(parseToTimestamp(null)).isNull()
    }
}
