package io.github.aedev.flow.player.renderer.subtitle

import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle

/**
 * Bridges [Srv3SubtitleParser] (the modern [androidx.media3.extractor.text.SubtitleParser] API)
 * into the legacy [SimpleSubtitleDecoder] flow, since this app's
 * [io.github.aedev.flow.player.renderer.CustomRenderersFactory] runs its [TextRenderer][
 * androidx.media3.exoplayer.text.TextRenderer] with legacy decoding enabled.
 */
class Srv3SubtitleDecoder : SimpleSubtitleDecoder("Srv3SubtitleDecoder") {
    private val parser = Srv3SubtitleParser()

    override fun decode(
        data: ByteArray,
        length: Int,
        reset: Boolean,
    ): Subtitle {
        if (reset) parser.reset()
        return parser.parseToLegacySubtitle(data, 0, length)
    }
}
