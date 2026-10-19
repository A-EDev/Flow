package io.github.aedev.flow.player.error

import androidx.media3.common.PlaybackException

/**
 * Decides whether a decoder failure gets one in-place re-prepare. A codec can die for reasons
 * unrelated to the stream, such as losing its output surface, and Media3 keeps the item and the
 * position in IDLE, so prepare() rebuilds the codec without a network round trip.
 *
 * One attempt is allowed until a frame renders again, so a codec that fails straight away gives
 * up instead of looping; [maxPerVideo] bounds codecs that render a little and then fail again.
 */
internal class VideoDecoderRecoveryPolicy(
    private val maxPerVideo: Int = MAX_REPREPARES_PER_VIDEO,
) {
    enum class Decision { REPREPARE, GIVE_UP }

    private var awaitingFrame = false
    private var reprepares = 0

    fun onDecoderError(
        errorCode: Int,
        formatHandled: Boolean,
    ): Decision {
        val transient =
            errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED
        if (!transient || !formatHandled || awaitingFrame || reprepares >= maxPerVideo) {
            return Decision.GIVE_UP
        }
        awaitingFrame = true
        reprepares++
        return Decision.REPREPARE
    }

    fun onFirstFrameRendered() {
        awaitingFrame = false
    }

    fun reset() {
        awaitingFrame = false
        reprepares = 0
    }

    companion object {
        const val MAX_REPREPARES_PER_VIDEO = 3
    }
}
