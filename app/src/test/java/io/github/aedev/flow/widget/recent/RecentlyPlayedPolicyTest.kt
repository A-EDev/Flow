package io.github.aedev.flow.widget.recent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RecentlyPlayedPolicyTest {
    @Test
    fun unstartedFinishedAndUntimedVideosShowNoProgress() {
        assertNull(watchedFraction(positionMs = 0L, durationMs = 600_000L))
        assertNull(watchedFraction(positionMs = 590_000L, durationMs = 600_000L))
        assertNull(watchedFraction(positionMs = 30_000L, durationMs = 0L))
    }

    @Test
    fun progressIsTheWatchedShare() {
        assertEquals(0.25f, watchedFraction(positionMs = 150_000L, durationMs = 600_000L)!!, 0.0001f)
    }

    @Test
    fun minutesLeftRoundUpAndNeverReadZero() {
        assertEquals(10, minutesLeft(positionMs = 30_000L, durationMs = 600_000L))
        assertEquals(1, minutesLeft(positionMs = 1_000L, durationMs = 20_000L))
        assertNull(minutesLeft(positionMs = 0L, durationMs = 600_000L))
    }

    @Test
    fun theSignatureMovesOnlyOnAVisibleProgressStep() {
        assertEquals(RecentlyPlayedSource.progressStep(100_000L, 600_000L), RecentlyPlayedSource.progressStep(110_000L, 600_000L))
        assertEquals(20, RecentlyPlayedSource.progressStep(600_000L, 600_000L))
        assertEquals(0, RecentlyPlayedSource.progressStep(5_000L, 0L))
    }
}
