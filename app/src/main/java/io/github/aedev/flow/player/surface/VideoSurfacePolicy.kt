package io.github.aedev.flow.player.surface

import android.os.Build
import androidx.media3.common.Player

object VideoSurfacePolicy {
    fun usesSurfaceView(sdkInt: Int): Boolean =
        sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    fun canRestoreVideoOutput(
        sdkInt: Int,
        isDisplayInteractive: Boolean,
        isSurfaceValid: Boolean
    ): Boolean =
        isDisplayInteractive && (!usesSurfaceView(sdkInt) || isSurfaceValid)

    /**
     * Whether a same-position seek is needed after a destroyed video surface comes back.
     *
     * Devices that cannot switch a codec's output surface in place (Media3's
     * setOutputSurface workaround list, e.g. many Xiaomi models) re-create the video
     * codec on every surface swap. A re-created codec resumes from the sample-stream
     * read position, not the playhead — while paused that read position can be several
     * seconds ahead, so resuming playback shows a frozen frame until the clock catches
     * up. A same-position seek flushes the codec and realigns video with the playhead.
     * Live streams are excluded: seeking there shifts the live-edge window.
     */
    fun shouldResyncOnSurfaceReattach(
        playWhenReady: Boolean,
        isLive: Boolean,
        playbackState: Int
    ): Boolean =
        !playWhenReady && !isLive &&
            (playbackState == Player.STATE_READY || playbackState == Player.STATE_BUFFERING)
}
