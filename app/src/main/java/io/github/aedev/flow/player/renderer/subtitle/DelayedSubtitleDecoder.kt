package io.github.aedev.flow.player.renderer.subtitle

import androidx.annotation.OptIn
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import androidx.media3.extractor.text.SubtitleParser
import io.github.aedev.flow.player.subtitle.SubtitleDelay

/**
 * Decodes a caption track with a Media3 [SubtitleParser] for the legacy text renderer, shifting it
 * by [delay]. Every sidecar track arrives through `SingleSampleMediaSource`, and device files are
 * extracted without transcoding, so this is where every track's timing passes.
 */
@OptIn(UnstableApi::class)
class DelayedSubtitleDecoder(
    name: String,
    private val parser: SubtitleParser,
    private val delay: SubtitleDelay,
) : SimpleSubtitleDecoder(name) {
    override fun decode(
        data: ByteArray,
        length: Int,
        reset: Boolean,
    ): Subtitle {
        if (reset) parser.reset()
        return DelayedSubtitle(parser.parseToLegacySubtitle(data, 0, length)) { delay.offsetUs }
    }
}

/** [subtitle] moved by [offsetUs], read on every call so the shift can change while it plays. */
@OptIn(UnstableApi::class)
internal class DelayedSubtitle(
    private val subtitle: Subtitle,
    private val offsetUs: () -> Long,
) : Subtitle {
    override fun getNextEventTimeIndex(timeUs: Long): Int = subtitle.getNextEventTimeIndex(timeUs - offsetUs())

    override fun getEventTimeCount(): Int = subtitle.eventTimeCount

    override fun getEventTime(index: Int): Long = subtitle.getEventTime(index) + offsetUs()

    override fun getCues(timeUs: Long): List<Cue> = subtitle.getCues(timeUs - offsetUs())
}
