package io.github.aedev.flow.ui.screens.shorts

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.shorts.ShortVideoQuality
import org.junit.Test

class ShortsQualitySelectionTest {

    private val qualities = listOf(
        quality(height = 1080, codec = "vp9", url = "1080-vp9"),
        quality(height = 1080, codec = "h264", url = "1080-h264"),
        quality(height = 720, codec = "vp9", url = "720-vp9")
    )

    @Test
    fun `current player URL identifies the active stream first`() {
        val selected = findActiveShortQuality(
            qualities = qualities,
            currentVideoUrl = "720-vp9",
            activeVideoWidth = 1080,
            activeVideoHeight = 1920,
            activeCodecKey = "h264"
        )

        assertThat(selected?.videoUrl).isEqualTo("720-vp9")
    }

    @Test
    fun `vertical format uses its short edge as the resolution class`() {
        val selected = findActiveShortQuality(
            qualities = qualities,
            currentVideoUrl = null,
            activeVideoWidth = 1080,
            activeVideoHeight = 1920,
            activeCodecKey = "vp9"
        )

        assertThat(selected?.videoUrl).isEqualTo("1080-vp9")
    }

    @Test
    fun `resolution remains selected when codec metadata is unavailable`() {
        val selected = findActiveShortQuality(
            qualities = qualities,
            currentVideoUrl = null,
            activeVideoWidth = 1920,
            activeVideoHeight = 1080,
            activeCodecKey = null
        )

        assertThat(selected?.heightClass).isEqualTo(1080)
    }

    @Test
    fun `unknown active format does not invent a selected quality`() {
        val selected = findActiveShortQuality(
            qualities = qualities,
            currentVideoUrl = null,
            activeVideoWidth = 0,
            activeVideoHeight = 0,
            activeCodecKey = null
        )

        assertThat(selected).isNull()
    }

    private fun quality(height: Int, codec: String, url: String) = ShortVideoQuality(
        heightClass = height,
        label = "${height}p",
        videoUrl = url,
        codecLabel = codec.uppercase(),
        codecKey = codec
    )
}
