package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.data.local.ViewHistory
import io.github.aedev.flow.data.local.WatchedThreshold
import io.github.aedev.flow.data.local.dao.WatchProgress
import io.github.aedev.flow.data.model.ShortVideo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * #979: a watched Short never left the feeds. It was saved at the 90 % tick (never at the end, since
 * a looping Short never ends), and its next playback overwrote that with a few percent.
 */
class ShortsWatchHistoryTest {
    private val short =
        ShortVideo(id = "reel", thumbnailUrl = "thumb", title = "Reel", channelName = "Chan", channelId = "UC", timestamp = 0L)

    /** Backs the mock with one row, updated by every write, like the real table. */
    private class Row {
        var progress: WatchProgress? = null
    }

    private fun history(row: Row): ViewHistory {
        val viewHistory: ViewHistory = mockk(relaxed = true)
        coEvery { viewHistory.getWatchProgress("reel") } answers { row.progress }
        coEvery {
            viewHistory.savePlaybackPosition("reel", any(), any(), any(), any(), any(), any(), any(), any(), any())
        } answers {
            row.progress = WatchProgress("reel", secondArg(), thirdArg(), 0L)
        }
        coEvery { viewHistory.markCompleted("reel", any()) } answers {
            row.progress = WatchProgress("reel", secondArg(), secondArg(), 0L)
        }
        return viewHistory
    }

    @Test
    fun `crossing ninety percent records the Short as finished`() {
        assertEquals(30_000L, shortHistoryPosition(27_000L, 30_000L))
        assertEquals(26_000L, shortHistoryPosition(26_000L, 30_000L))
        assertTrue(isShortComplete(30_000L, 30_000L))
        assertFalse(isShortComplete(26_999L, 30_000L))
        assertFalse(isShortComplete(1_000L, 0L))
    }

    @Test
    fun `a finished Short is watched under every threshold`() {
        val position = shortHistoryPosition(27_600L, 30_000L)

        WatchedThreshold.entries.forEach { threshold -> assertTrue(threshold.isWatched(position, 30_000L)) }
    }

    @Test
    fun `completion is written once and a later playback never lowers it`() =
        runTest {
            val row = Row()
            val viewHistory = history(row)
            val watchHistory = ShortsWatchHistory(viewHistory)

            watchHistory.save(short, positionMs = 1_500L, durationMs = 30_000L)
            watchHistory.save(short, positionMs = 27_500L, durationMs = 30_000L)
            watchHistory.save(short, positionMs = 1_500L, durationMs = 30_000L)
            watchHistory.save(short, positionMs = 9_000L, durationMs = 30_000L)

            assertEquals(30_000L, row.progress?.position)
            coVerify(exactly = 1) {
                viewHistory.savePlaybackPosition("reel", 30_000L, 30_000L, any(), any(), any(), any(), any(), any(), any())
            }
            coVerify(exactly = 2) {
                viewHistory.touchHistoryEntry("reel", "Reel", "thumb", "Chan", "UC", 30_000L, true)
            }
        }

    @Test
    fun `a Short an older build saved at 92 percent is stored as finished on its next playback`() =
        runTest {
            val row = Row().apply { progress = WatchProgress("reel", 27_600L, 30_000L, 0L) }
            val viewHistory = history(row)

            ShortsWatchHistory(viewHistory).save(short, positionMs = 1_500L, durationMs = 30_000L)

            assertEquals(30_000L, row.progress?.position)
            coVerify(exactly = 1) { viewHistory.markCompleted("reel", 30_000L) }
        }

    @Test
    fun `the Shorts filters hide a reel past ninety percent whatever the video threshold`() {
        val rows =
            listOf(
                WatchProgress("reel-92", 27_600L, 30_000L, 0L),
                WatchProgress("reel-done", 30_000L, 30_000L, 0L),
                WatchProgress("reel-half", 15_000L, 30_000L, 0L),
                WatchProgress("unknown-length", 5_000L, 0L, 0L),
            )

        assertEquals(setOf("reel-92", "reel-done"), rows.finishedShortIds())
        assertFalse(WatchedThreshold.PERCENT_99.isWatched(27_600L, 30_000L))
    }

    @Test
    fun `an unfinished Short keeps its real progress`() =
        runTest {
            val row = Row()
            val watchHistory = ShortsWatchHistory(history(row))

            watchHistory.save(short, positionMs = 12_000L, durationMs = 30_000L)
            watchHistory.save(short, positionMs = 6_000L, durationMs = 30_000L)

            assertEquals(6_000L, row.progress?.position)
        }
}
