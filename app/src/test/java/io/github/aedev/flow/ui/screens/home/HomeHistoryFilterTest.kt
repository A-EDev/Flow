package io.github.aedev.flow.ui.screens.home

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.VideoHistoryEntry
import io.github.aedev.flow.data.local.WatchedThreshold
import org.junit.Test

class HomeHistoryFilterTest {

    @Test
    fun `configured threshold filters recommendations and continue watching`() {
        val history = listOf(
            entry("watched", position = 570_000, duration = 600_000, timestamp = 3),
            entry("in-progress", position = 300_000, duration = 600_000, timestamp = 2),
            entry("too-early", position = 10_000, duration = 600_000, timestamp = 1),
            entry("short", position = 300_000, duration = 600_000, timestamp = 4, isShort = true)
        )

        val result = filterHomeHistory(
            history = history,
            hideWatchedVideos = true,
            watchedThreshold = WatchedThreshold.ALMOST_FINISHED,
            continueWatchingEnabled = true
        )

        assertThat(result.watchedVideoIds).containsExactly("watched")
        assertThat(result.continueWatchingVideos.map { it.videoId }).containsExactly("in-progress")
    }

    @Test
    fun `disabled watched filtering preserves the existing continue watching range`() {
        val result = filterHomeHistory(
            history = listOf(
                entry("ninety", position = 90, duration = 100, timestamp = 2),
                entry("ninety-five", position = 95, duration = 100, timestamp = 1)
            ),
            hideWatchedVideos = false,
            watchedThreshold = WatchedThreshold.PERCENT_90,
            continueWatchingEnabled = true
        )

        assertThat(result.watchedVideoIds).isEmpty()
        assertThat(result.continueWatchingVideos.map { it.videoId }).containsExactly("ninety")
    }

    @Test
    fun `almost finished threshold means less than one minute remains`() {
        assertThat(WatchedThreshold.ALMOST_FINISHED.isWatched(570_000, 600_000)).isTrue()
        assertThat(WatchedThreshold.ALMOST_FINISHED.isWatched(539_000, 600_000)).isFalse()
        assertThat(WatchedThreshold.ALMOST_FINISHED.isWatched(0, 30_000)).isFalse()
    }

    private fun entry(
        id: String,
        position: Long,
        duration: Long,
        timestamp: Long,
        isShort: Boolean = false
    ) = VideoHistoryEntry(
        videoId = id,
        position = position,
        duration = duration,
        timestamp = timestamp,
        title = id,
        thumbnailUrl = "",
        isShort = isShort
    )
}
