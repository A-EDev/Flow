package io.github.aedev.flow.player.dlna

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.stream.AudioStream

class CastStreamVariantTest {
    private fun audioStream(itagItem: ItagItem?): AudioStream =
        AudioStream
            .Builder()
            .setId("140")
            .setContent("https://example.com/audio.m4a", true)
            .setMediaFormat(MediaFormat.M4A)
            .setAverageBitrate(128)
            .apply { itagItem?.let(::setItagItem) }
            .build()

    private fun itag(
        initStart: Int,
        initEnd: Int,
        indexStart: Int,
        indexEnd: Int,
    ): ItagItem =
        ItagItem(ItagItem.getItag(140)).apply {
            setInitStart(initStart)
            setInitEnd(initEnd)
            setIndexStart(indexStart)
            setIndexEnd(indexEnd)
        }

    @Test
    fun `segment ranges are the itag's inclusive init and index ranges`() {
        val ranges = audioStream(itag(0, 722, 723, 2962)).castSegmentRanges()

        assertThat(ranges).isEqualTo(CastSegmentRanges(init = 0L..722L, index = 723L..2962L))
        assertThat(ranges!!.init.length).isEqualTo(723)
    }

    @Test
    fun `streams without an itag or with empty ranges have no segment ranges`() {
        assertThat(audioStream(null).castSegmentRanges()).isNull()
        assertThat(audioStream(itag(-1, -1, 723, 2962)).castSegmentRanges()).isNull()
        assertThat(audioStream(itag(0, 722, 0, 0)).castSegmentRanges()).isNull()
    }
}
