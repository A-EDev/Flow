package io.github.aedev.flow.player.surface

import android.os.Build
import androidx.media3.common.Player
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class VideoSurfacePolicyTest {
    @Test
    fun `surface view restore waits for a valid holder`() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                isDisplayInteractive = true,
                isSurfaceValid = false,
            ),
        ).isFalse()
    }

    @Test
    fun `surface view restores once its holder is valid`() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                isDisplayInteractive = true,
                isSurfaceValid = true,
            ),
        ).isTrue()
    }

    @Test
    fun `texture view restore does not require a surface holder`() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = Build.VERSION_CODES.TIRAMISU,
                isDisplayInteractive = true,
                isSurfaceValid = false,
            ),
        ).isTrue()
    }

    @Test
    fun `video output stays disabled while display is not interactive`() {
        assertThat(
            VideoSurfacePolicy.canRestoreVideoOutput(
                sdkInt = Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                isDisplayInteractive = false,
                isSurfaceValid = true,
            ),
        ).isFalse()
    }

    @Test
    fun `paused ready playback resyncs after a surface reattach`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = Player.STATE_READY,
            ),
        ).isTrue()
    }

    @Test
    fun `paused buffering playback resyncs after a surface reattach`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = Player.STATE_BUFFERING,
            ),
        ).isTrue()
    }

    @Test
    fun `active playback is not interrupted by a resync seek`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = true,
                isLive = false,
                playbackState = Player.STATE_READY,
            ),
        ).isFalse()
    }

    @Test
    fun `live streams never resync via seek`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = true,
                playbackState = Player.STATE_READY,
            ),
        ).isFalse()
    }

    @Test
    fun `idle and ended players are not resynced`() {
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = Player.STATE_IDLE,
            ),
        ).isFalse()
        assertThat(
            VideoSurfacePolicy.shouldResyncOnSurfaceReattach(
                playWhenReady = false,
                isLive = false,
                playbackState = Player.STATE_ENDED,
            ),
        ).isFalse()
    }

    @Test
    fun `the resync target never lands on the position the player already reports`() {
        // A seek resolving to the current millisecond returns before the renderers are disabled,
        // so the target has to differ by at least one.
        assertThat(VideoSurfacePolicy.resyncSeekTargetMs(315066L)).isEqualTo(315065L)
        assertThat(VideoSurfacePolicy.resyncSeekTargetMs(1L)).isEqualTo(0L)
    }

    @Test
    fun `a player at the very start nudges forward instead of below zero`() {
        assertThat(VideoSurfacePolicy.resyncSeekTargetMs(0L)).isEqualTo(1L)
    }
}
