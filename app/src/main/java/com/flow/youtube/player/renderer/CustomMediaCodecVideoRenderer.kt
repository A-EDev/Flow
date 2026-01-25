package com.flow.youtube.player.renderer

import android.content.Context
import android.os.Handler
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * A [MediaCodecVideoRenderer] which always enables the output surface workaround.
 * This aligns with NewPipe's implementation to handle devices with incorrect
 * [android.media.MediaCodec.setOutputSurface] implementations.
 */
class CustomMediaCodecVideoRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    allowedJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: VideoRendererEventListener?,
    maxDroppedFramesToNotify: Int
) : MediaCodecVideoRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    allowedJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    maxDroppedFramesToNotify
) {

    override fun codecNeedsSetOutputSurfaceWorkaround(name: String): Boolean {
        return super.codecNeedsSetOutputSurfaceWorkaround(name)
    }
}
