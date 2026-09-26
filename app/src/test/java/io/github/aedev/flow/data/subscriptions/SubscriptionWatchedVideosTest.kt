package io.github.aedev.flow.data.subscriptions

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.AppDatabase
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.WatchedThreshold
import io.github.aedev.flow.data.local.dao.WatchProgress
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

/** #979 (owner decision D1): the Shorts queues and shelves hide watched reels under their own setting. */
class SubscriptionWatchedVideosTest {
    private val history =
        listOf(
            WatchProgress("finished-reel", position = 30_000L, duration = 30_000L, timestamp = 0L),
            WatchProgress("started-reel", position = 1_500L, duration = 30_000L, timestamp = 0L),
        )

    private fun watched(
        hideWatchedShorts: Boolean,
        hideWatchedVideos: Boolean,
    ): SubscriptionWatchedVideos {
        val viewHistory: ViewHistory = mockk(relaxed = true)
        every { viewHistory.getVideoWatchProgress() } returns flowOf(history)
        val preferences: PlayerPreferences = mockk(relaxed = true)
        every { preferences.hideWatchedShorts } returns flowOf(hideWatchedShorts)
        every { preferences.hideWatchedVideosFromSubscriptions } returns flowOf(hideWatchedVideos)
        every { preferences.watchedThreshold } returns flowOf(WatchedThreshold.ALMOST_FINISHED)
        val database: AppDatabase = mockk(relaxed = true)
        every { database.downloadDao().getVideoDownloads() } returns flowOf(emptyList())
        return SubscriptionWatchedVideos(viewHistory, preferences, database)
    }

    @Test
    fun `finished reels are hidden while the Shorts setting is on`() =
        runTest {
            assertThat(watched(hideWatchedShorts = true, hideWatchedVideos = false).shortIds.first()).containsExactly("finished-reel")
        }

    @Test
    fun `switching the Shorts setting off shows watched reels even when videos are hidden`() =
        runTest {
            val watched = watched(hideWatchedShorts = false, hideWatchedVideos = true)

            assertThat(watched.shortIds.first()).isEmpty()
            assertThat(watched.ids.first()).containsExactly("finished-reel")
        }
}
