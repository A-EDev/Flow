package io.github.aedev.flow.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ScheduledStartTest {
    private val now = 1_800_000_000_000L

    @Test
    fun `a feed timestamp still ahead is the release time`() {
        assertThat(upcomingReleaseMs(now + 3_600_000L, "", now)).isEqualTo(now + 3_600_000L)
    }

    @Test
    fun `a premiere label is read when the timestamp is an upload date`() {
        val label = premiereDateText(now + 86_400_000L)

        assertThat(upcomingReleaseMs(now - 86_400_000L, label, now)).isEqualTo(parsePremiereTimestamp(label))
    }

    @Test
    fun `a start already behind is no release time`() {
        assertThat(upcomingReleaseMs(now - 60_000L, premiereDateText(now - 60_000L), now)).isNull()
    }

    @Test
    fun `the premiere text round trips to the minute`() {
        val releaseMs = now + 5_400_000L

        assertThat(formatPremiereDate(premiereDateText(releaseMs))).isEqualTo(formatPremiereDate(releaseMs))
    }

    @Test
    fun `the exact mode shows the date and time`() {
        val releaseMs = now + 5_400_000L

        assertThat(formatScheduledStart(releaseMs, DateDisplayMode.EXACT, now)).isEqualTo(formatPremiereDate(releaseMs))
    }
}
