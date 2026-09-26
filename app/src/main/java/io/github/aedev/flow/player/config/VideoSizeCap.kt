package io.github.aedev.flow.player.config

import android.app.ActivityManager
import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

/** The largest frame the track selector may pick: never above 4K, lower on a small app heap. */
data class VideoSizeCap(
    val maxWidth: Int,
    val maxHeight: Int,
) {
    companion object {
        val UHD = VideoSizeCap(PlayerConfig.MAX_VIDEO_WIDTH, PlayerConfig.MAX_VIDEO_HEIGHT)

        fun forHeap(
            memoryClassMb: Int,
            isLowRamDevice: Boolean,
        ): VideoSizeCap =
            when {
                isLowRamDevice || memoryClassMb <= 256 -> VideoSizeCap(1920, 1080)
                memoryClassMb <= 384 -> VideoSizeCap(2560, 1440)
                else -> UHD
            }

        fun forDevice(context: Context): VideoSizeCap {
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            return forHeap(
                memoryClassMb = activityManager?.memoryClass ?: 256,
                isLowRamDevice = activityManager?.isLowRamDevice == true,
            )
        }
    }
}

/**
 * Drops every per-quality size constraint and puts the device limits back. Clearing alone would also
 * lift the heap cap, and a local file clears the screen viewport, so an automatic pick could climb to
 * a size the device can neither decode comfortably nor show.
 */
@UnstableApi
fun DefaultTrackSelector.Parameters.Builder.resetVideoSizeTo(cap: VideoSizeCap): DefaultTrackSelector.Parameters.Builder =
    apply {
        clearVideoSizeConstraints()
        setMinVideoSize(0, 0)
        setMaxVideoSize(cap.maxWidth, cap.maxHeight)
        setViewportSizeToPhysicalDisplaySize(true)
    }
