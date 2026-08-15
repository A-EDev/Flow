package io.github.aedev.flow.player.renderer

import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer

/**
 * Built from a [MediaCodecVideoRenderer.Builder] rather than the direct constructor: every public
 * constructor is deprecated as of Media3 1.11, and options added since — including
 * `setEnableDurationToProgressUs`, which [io.github.aedev.flow.player.config.PlayerConfig]'s
 * dynamic-scheduling experiment needs — are reachable only through the builder.
 */
@UnstableApi
class CustomMediaCodecVideoRenderer(
    builder: Builder,
) : MediaCodecVideoRenderer(builder) {
    override fun codecNeedsSetOutputSurfaceWorkaround(name: String): Boolean = true
}
