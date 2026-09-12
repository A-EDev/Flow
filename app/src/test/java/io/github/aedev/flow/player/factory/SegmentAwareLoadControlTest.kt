package io.github.aedev.flow.player.factory

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class SegmentAwareLoadControlTest {
    private val sampleSegment =
        SponsorBlockSegment(
            category = "sponsor",
            actionType = "skip",
            segment = listOf(60.0f, 90.0f),
            uuid = "test-uuid-1",
        )

    private class FakeLoadControl(
        var continueLoadingValue: Boolean = true,
    ) : LoadControl {
        var shouldContinueLoadingCalls = 0

        override fun onPrepared() {}

        override fun onTracksSelected(
            timeline: Timeline,
            mediaPeriodId: MediaSource.MediaPeriodId,
            renderers: Array<out Renderer>,
            trackGroups: TrackGroupArray,
            trackSelections: Array<out ExoTrackSelection>,
        ) {}

        override fun onStopped() {}

        override fun onReleased() {}

        override fun getAllocator(playerId: androidx.media3.exoplayer.analytics.PlayerId): Allocator = throw UnsupportedOperationException()

        override fun getBackBufferDurationUs(): Long = 0L

        override fun retainBackBufferFromKeyframe(): Boolean = false

        override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
            shouldContinueLoadingCalls++
            return continueLoadingValue
        }

        override fun shouldStartPlayback(
            timeline: Timeline,
            mediaPeriodId: MediaSource.MediaPeriodId,
            bufferedDurationUs: Long,
            playbackSpeed: Float,
            rebuffering: Boolean,
            targetLiveOffsetUs: Long,
        ): Boolean = true
    }

    private fun createControl(
        fakeDelegate: FakeLoadControl,
        autoSkip: Boolean = true,
        action: SponsorBlockAction = SponsorBlockAction.SKIP,
    ): SegmentAwareLoadControl =
        SegmentAwareLoadControl(
            delegate = fakeDelegate,
            sponsorSegmentsProvider = { listOf(sampleSegment) },
            categoryActionsProvider = { mapOf("sponsor" to action) },
            isAutoSkipEnabledProvider = { autoSkip },
        )

    private fun createParameters(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
    ): LoadControl.Parameters =
        LoadControl.Parameters(
            androidx.media3.exoplayer.analytics.PlayerId.UNSET,
            Timeline.EMPTY,
            LoadControl.EMPTY_MEDIA_PERIOD_ID,
            playbackPositionUs,
            bufferedDurationUs,
            // playbackSpeed =
            1.0f,
            // playWhenReady =
            true,
            // rebuffering =
            false,
            // targetLiveOffsetUs =
            androidx.media3.common.C.TIME_UNSET,
            // lastRebufferRealtimeMs =
            0L,
        )

    @Test
    fun `when auto-skip is disabled, shouldContinueLoading does not throttle sponsor boundary`() {
        val fake = FakeLoadControl(continueLoadingValue = true)
        val loadControl = createControl(fake, autoSkip = false)

        // Playhead at 50s, buffer at 65s (inside sponsor)
        val shouldLoad = loadControl.shouldContinueLoading(createParameters(50_000_000L, 15_000_000L))
        assertTrue(shouldLoad)
        assertEquals(1, fake.shouldContinueLoadingCalls)
    }

    @Test
    fun `when buffer frontier reaches sponsor start, shouldContinueLoading returns false`() {
        val fake = FakeLoadControl(continueLoadingValue = true)
        val loadControl = createControl(fake, autoSkip = true)

        // Playhead at 55s, buffer at 60s (start of sponsor)
        val shouldLoad = loadControl.shouldContinueLoading(createParameters(55_000_000L, 5_000_000L))
        assertFalse(shouldLoad)
        // Delegate should not even be called when throttled
        assertEquals(0, fake.shouldContinueLoadingCalls)
    }

    @Test
    fun `when buffer frontier has not reached sponsor start, shouldContinueLoading allows loading`() {
        val fake = FakeLoadControl(continueLoadingValue = true)
        val loadControl = createControl(fake, autoSkip = true)

        // Playhead at 40s, buffer at 45s (well before 60s)
        val shouldLoad = loadControl.shouldContinueLoading(createParameters(40_000_000L, 5_000_000L))
        assertTrue(shouldLoad)
        assertEquals(1, fake.shouldContinueLoadingCalls)
    }

    @Test
    fun `when playhead has seeked past sponsor, shouldContinueLoading allows loading`() {
        val fake = FakeLoadControl(continueLoadingValue = true)
        val loadControl = createControl(fake, autoSkip = true)

        // Playhead seeked past 90s to 95s, buffer at 97s
        val shouldLoad = loadControl.shouldContinueLoading(createParameters(95_000_000L, 2_000_000L))
        assertTrue(shouldLoad)
        assertEquals(1, fake.shouldContinueLoadingCalls)
    }

    @Test
    fun `when action is IGNORE or MUTE, sponsor segment does not throttle loading`() {
        val fake = FakeLoadControl(continueLoadingValue = true)
        val loadControl = createControl(fake, autoSkip = true, action = SponsorBlockAction.IGNORE)

        // Playhead at 55s, buffer at 60s
        val shouldLoad = loadControl.shouldContinueLoading(createParameters(55_000_000L, 5_000_000L))
        assertTrue(shouldLoad)
        assertEquals(1, fake.shouldContinueLoadingCalls)
    }
}
