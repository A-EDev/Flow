package io.github.aedev.flow.widget.nowplaying

import io.github.aedev.flow.widget.core.component.WIDGET_PROGRESS_MAX
import io.github.aedev.flow.widget.core.component.widgetProgressAt
import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingProgressTest {
    @Test
    fun aPausedTrackStaysWhereItWasRead() {
        assertEquals(
            250,
            widgetProgressAt(45_000L, 180_000L, capturedAtElapsedMs = 1_000L, isPlaying = false, speed = 1f, nowElapsedMs = 60_000L),
        )
    }

    @Test
    fun aPlayingTrackRunsOnFromWhenItWasRead() {
        assertEquals(
            500,
            widgetProgressAt(45_000L, 180_000L, capturedAtElapsedMs = 1_000L, isPlaying = true, speed = 1f, nowElapsedMs = 46_000L),
        )
    }

    @Test
    fun playbackSpeedScalesTheRun() {
        assertEquals(
            750,
            widgetProgressAt(45_000L, 180_000L, capturedAtElapsedMs = 1_000L, isPlaying = true, speed = 2f, nowElapsedMs = 46_000L),
        )
    }

    @Test
    fun theBarNeverRunsPastTheEndOrWithoutADuration() {
        assertEquals(WIDGET_PROGRESS_MAX, widgetProgressAt(170_000L, 180_000L, 1_000L, true, 1f, nowElapsedMs = 600_000L))
        assertEquals(0, widgetProgressAt(45_000L, 0L, 1_000L, true, 1f, nowElapsedMs = 46_000L))
    }

    @Test
    fun ticksFollowTheTrackLengthWithinTheirBounds() {
        assertEquals(2_000L, NowPlayingProgressTicker.tickIntervalMs(durationMs = 180_000L, speed = 1f))
        assertEquals(3_600L, NowPlayingProgressTicker.tickIntervalMs(durationMs = 360_000L, speed = 1f))
        assertEquals(10_000L, NowPlayingProgressTicker.tickIntervalMs(durationMs = 3_600_000L, speed = 1f))
        assertEquals(2_000L, NowPlayingProgressTicker.tickIntervalMs(durationMs = 360_000L, speed = 2f))
    }
}
