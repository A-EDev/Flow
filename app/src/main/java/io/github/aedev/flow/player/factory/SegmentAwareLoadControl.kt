package io.github.aedev.flow.player.factory

import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.TrackGroupArray
import androidx.media3.exoplayer.trackselection.ExoTrackSelection
import androidx.media3.exoplayer.upstream.Allocator
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.player.sponsorblock.resolveSponsorBlockAction

/**
 * A [LoadControl] wrapper that prevents ExoPlayer from downloading chunks
 * inside upcoming skippable sponsor segments when auto-skip is enabled.
 *
 * Once the player's buffer has loaded media up to the start of an upcoming
 * auto-skip segment, this load control pauses further readahead so bandwidth
 * is not wasted fetching ad content that will be skipped upon arrival.
 *
 * All methods of [LoadControl] are explicitly delegated to [delegate] because
 * Java 8 default interface methods are not automatically forwarded by Kotlin's
 * `by` delegation when the default method throws `IllegalStateException`.
 */
@UnstableApi
class SegmentAwareLoadControl(
    private val delegate: LoadControl,
    private val sponsorSegmentsProvider: () -> List<SponsorBlockSegment> = { emptyList() },
    private val categoryActionsProvider: () -> Map<String, SponsorBlockAction> = { emptyMap() },
    private val isAutoSkipEnabledProvider: () -> Boolean = { false },
) : LoadControl {
    companion object {
        /**
         * Margin in microseconds before the segment start where we allow buffering to pause.
         * 200ms ensures we buffer virtually the entire content before the sponsor without
         * overshooting into the sponsor itself.
         */
        private const val SEGMENT_START_MARGIN_US = 200_000L
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onPrepared() {
        delegate.onPrepared()
    }

    override fun onPrepared(playerId: PlayerId) {
        delegate.onPrepared(playerId)
    }

    override fun onTracksSelected(
        parameters: LoadControl.Parameters,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) {
        delegate.onTracksSelected(parameters, trackGroups, trackSelections)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onTracksSelected(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) {
        delegate.onTracksSelected(playerId, timeline, mediaPeriodId, renderers, trackGroups, trackSelections)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onTracksSelected(
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) {
        delegate.onTracksSelected(timeline, mediaPeriodId, renderers, trackGroups, trackSelections)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onTracksSelected(
        renderers: Array<out Renderer>,
        trackGroups: TrackGroupArray,
        trackSelections: Array<out ExoTrackSelection?>,
    ) {
        delegate.onTracksSelected(renderers, trackGroups, trackSelections)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onStopped() {
        delegate.onStopped()
    }

    override fun onStopped(playerId: PlayerId) {
        delegate.onStopped(playerId)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun onReleased() {
        delegate.onReleased()
    }

    override fun onReleased(playerId: PlayerId) {
        delegate.onReleased(playerId)
    }

    override fun getAllocator(playerId: PlayerId): Allocator = delegate.getAllocator(playerId)

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun getBackBufferDurationUs(): Long = delegate.backBufferDurationUs

    override fun getBackBufferDurationUs(playerId: PlayerId): Long = delegate.getBackBufferDurationUs(playerId)

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun retainBackBufferFromKeyframe(): Boolean = delegate.retainBackBufferFromKeyframe()

    override fun retainBackBufferFromKeyframe(playerId: PlayerId): Boolean = delegate.retainBackBufferFromKeyframe(playerId)

    override fun shouldContinueLoading(parameters: LoadControl.Parameters): Boolean {
        if (!shouldBufferGivenSponsorSegments(parameters.playbackPositionUs, parameters.bufferedDurationUs)) {
            return false
        }
        return delegate.shouldContinueLoading(parameters)
    }

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun shouldContinueLoading(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
        playbackSpeed: Float,
    ): Boolean {
        if (!shouldBufferGivenSponsorSegments(playbackPositionUs, bufferedDurationUs)) {
            return false
        }
        return delegate.shouldContinueLoading(playbackPositionUs, bufferedDurationUs, playbackSpeed)
    }

    override fun shouldContinuePreloading(
        playerId: PlayerId,
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        bufferedDurationUs: Long,
    ): Boolean = delegate.shouldContinuePreloading(playerId, timeline, mediaPeriodId, bufferedDurationUs)

    override fun shouldStartPlayback(parameters: LoadControl.Parameters): Boolean = delegate.shouldStartPlayback(parameters)

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun shouldStartPlayback(
        timeline: Timeline,
        mediaPeriodId: MediaSource.MediaPeriodId,
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long,
    ): Boolean =
        delegate.shouldStartPlayback(
            timeline,
            mediaPeriodId,
            bufferedDurationUs,
            playbackSpeed,
            rebuffering,
            targetLiveOffsetUs,
        )

    @Deprecated("Deprecated in Java")
    @Suppress("DEPRECATION")
    override fun shouldStartPlayback(
        bufferedDurationUs: Long,
        playbackSpeed: Float,
        rebuffering: Boolean,
        targetLiveOffsetUs: Long,
    ): Boolean = delegate.shouldStartPlayback(bufferedDurationUs, playbackSpeed, rebuffering, targetLiveOffsetUs)

    private fun shouldBufferGivenSponsorSegments(
        playbackPositionUs: Long,
        bufferedDurationUs: Long,
    ): Boolean {
        if (!isAutoSkipEnabledProvider()) return true

        val segments = sponsorSegmentsProvider()
        if (segments.isEmpty()) return true

        val bufferedPositionUs = playbackPositionUs + bufferedDurationUs
        val categoryActions = categoryActionsProvider()

        // Check if the current buffer frontier is approaching or inside an upcoming skippable segment,
        // while the playback head is still before the segment start.
        for (seg in segments) {
            val action = resolveSponsorBlockAction(seg, categoryActions)
            if (action != SponsorBlockAction.SKIP) continue

            val segStartUs = (seg.startTime * 1_000_000L).toLong()

            // If the playhead is before this segment and the buffer has already loaded
            // up to the start of the segment (minus safety margin), do not load deeper into the sponsor.
            if (playbackPositionUs < segStartUs && bufferedPositionUs >= (segStartUs - SEGMENT_START_MARGIN_US)) {
                return false
            }
        }
        return true
    }
}
