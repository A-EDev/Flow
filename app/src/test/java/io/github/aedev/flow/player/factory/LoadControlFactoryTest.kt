package io.github.aedev.flow.player.factory

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import io.github.aedev.flow.player.config.PlayerConfig
import io.mockk.mockk
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Shorts and music profiles are fixed constants rather than user preferences, so nothing else
 * re-checks them: [io.github.aedev.flow.data.local.BufferDurationsTest] only covers the video path,
 * whose durations come from DataStore. These tests are what stops an edit to the constants from
 * reaching a device as the #788 class of failure — `setBufferDurationsMs` throws while the player is
 * being built, which surfaces as a crash on every launch rather than as degraded buffering.
 */
@UnstableApi
class LoadControlFactoryTest {
    @Test
    fun `shorts profile is accepted by the load control`() {
        LoadControlFactory.forShorts()
    }

    @Test
    fun `music profile is accepted by the load control`() {
        LoadControlFactory.forMusic()
    }

    @Test
    fun `shorts constants satisfy the load control contract before any coercion`() {
        assertContractHolds(
            profile = "shorts",
            minMs = PlayerConfig.SHORTS_MIN_BUFFER_MS,
            maxMs = PlayerConfig.SHORTS_MAX_BUFFER_MS,
            playbackMs = PlayerConfig.SHORTS_BUFFER_FOR_PLAYBACK_MS,
            rebufferMs = PlayerConfig.SHORTS_BUFFER_FOR_REBUFFER_MS,
        )
    }

    @Test
    fun `music constants satisfy the load control contract before any coercion`() {
        assertContractHolds(
            profile = "music",
            minMs = PlayerConfig.MUSIC_MIN_BUFFER_MS,
            maxMs = PlayerConfig.MUSIC_MAX_BUFFER_MS,
            playbackMs = PlayerConfig.MUSIC_BUFFER_FOR_PLAYBACK_MS,
            rebufferMs = PlayerConfig.MUSIC_BUFFER_FOR_REBUFFER_MS,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the load control still rejects a profile that inverts min and rebuffer`() {
        // Keeps the checks above from passing vacuously.
        DefaultLoadControl.Builder().setBufferDurationsMs(1_500, 8_000, 250, 5_000)
    }

    @Test
    fun `video keeps loading across an upcoming skip boundary below its minimum buffer`() {
        val control = LoadControlFactory.forVideo(mockk<Context>(relaxed = true), 20_000, 60_000, 1_000, 2_000)
        control.onPrepared(PlayerId.UNSET)
        try {
            assertTrue(control.shouldContinueLoading(parameters(55_000_000L, 4_800_000L)))
            assertTrue(control.shouldContinueLoading(parameters(55_000_000L, 5_000_000L)))
            assertTrue(control.shouldContinueLoading(parameters(90_000_000L, 500_000L)))
        } finally {
            control.onReleased(PlayerId.UNSET)
        }
    }

    @Test
    fun `video stops loading at its maximum buffer`() {
        val control = LoadControlFactory.forVideo(mockk<Context>(relaxed = true), 20_000, 60_000, 1_000, 2_000)
        control.onPrepared(PlayerId.UNSET)
        try {
            assertFalse(control.shouldContinueLoading(parameters(55_000_000L, 60_000_000L)))
        } finally {
            control.onReleased(PlayerId.UNSET)
        }
    }

    private fun parameters(
        positionUs: Long,
        bufferedUs: Long,
    ): LoadControl.Parameters {
        val timeline = SinglePeriodTimeline(120_000_000L, true, false, false, null, MediaItem.EMPTY)
        return LoadControl.Parameters(
            PlayerId.UNSET,
            timeline,
            MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)),
            positionUs,
            bufferedUs,
            1f,
            true,
            false,
            C.TIME_UNSET,
            0L,
        )
    }

    /**
     * Asserts the raw constants, not the coerced output of [LoadControlFactory.build] — the coercion
     * is a backstop, and a profile that only survives because of it has drifted from its intent.
     */
    private fun assertContractHolds(
        profile: String,
        minMs: Int,
        maxMs: Int,
        playbackMs: Int,
        rebufferMs: Int,
    ) {
        assertTrue("$profile: max ($maxMs) must be >= min ($minMs)", maxMs >= minMs)
        assertTrue("$profile: min ($minMs) must be >= playback ($playbackMs)", minMs >= playbackMs)
        assertTrue("$profile: min ($minMs) must be >= rebuffer ($rebufferMs)", minMs >= rebufferMs)

        DefaultLoadControl.Builder().setBufferDurationsMs(minMs, maxMs, playbackMs, rebufferMs)
    }
}
