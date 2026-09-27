package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.dao.WatchProgress
import org.junit.Test

/**
 * #979: the Shorts feed decided "watched" in SQL (99 % and under a minute left) while Home and
 * Subscriptions used [WatchedThreshold.isWatched] (under a minute left, any percent), so a Short
 * saved at 92 % was watched on one surface and not on the other. Both now read the one definition.
 */
class WatchedThresholdTest {
    private fun progress(
        id: String,
        positionMs: Long,
        durationMs: Long,
    ) = WatchProgress(videoId = id, position = positionMs, duration = durationMs, timestamp = 0L)

    private val matrix =
        listOf(
            progress("short-start", 1_500L, 30_000L),
            progress("short-half", 15_000L, 30_000L),
            progress("short-92", 27_600L, 30_000L),
            progress("short-done", 30_000L, 30_000L),
            progress("long-last-minute", 570_000L, 600_000L),
            progress("long-61s-left", 539_000L, 600_000L),
            progress("long-97", 5_820_000L, 6_000_000L),
            progress("feature-99", 11_900_000L, 12_000_000L),
            progress("unknown-length", 5_000L, 0L),
            progress("unplayed", 0L, 30_000L),
        )

    @Test
    fun `the id filter and isWatched agree for every threshold`() {
        WatchedThreshold.entries.forEach { threshold ->
            val expected = matrix.filter { threshold.isWatched(it.position, it.duration) }.map { it.videoId }.toSet()

            assertThat(matrix.watchedIds(threshold)).isEqualTo(expected)
        }
    }

    @Test
    fun `almost finished is the last minute of a long video and 90 percent of a short one`() {
        assertThat(matrix.watchedIds(WatchedThreshold.ALMOST_FINISHED))
            .containsExactly("short-92", "short-done", "long-last-minute")
    }

    @Test
    fun `a percent threshold ignores the time left`() {
        assertThat(matrix.watchedIds(WatchedThreshold.PERCENT_99)).containsExactly("short-done", "feature-99")
        assertThat(matrix.watchedIds(WatchedThreshold.PERCENT_90))
            .containsExactly("short-92", "short-done", "long-last-minute", "long-97", "feature-99")
    }

    @Test
    fun `a Short that only just started is never watched`() {
        WatchedThreshold.entries.forEach { threshold ->
            assertThat(threshold.isWatched(1_500L, 30_000L)).isFalse()
        }
    }
}
