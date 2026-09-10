package io.github.aedev.flow.ui.screens.player.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

/**
 * What is left of the player's util object after the formatters moved to `utils/FormatUtils.kt`
 * and the speed-boost ladder to `gesture/PlayerSpeedBoost.kt`: a thin cover over VideoCodecUtils.
 */
class VideoPlayerUtilsTest {
    @Test
    fun `codec keys come from the codecs parameter of the mime type`() {
        assertThat(VideoPlayerUtils.codecKeyFromMimeType("video/mp4; codecs=\"avc1.640028\"")).isEqualTo("h264")
        assertThat(VideoPlayerUtils.codecKeyFromMimeType("video/webm; codecs=\"vp09.00.40.08\"")).isEqualTo("vp9")
        assertThat(VideoPlayerUtils.codecKeyFromMimeType("video/mp4; codecs=\"av01.0.08M.08\"")).isEqualTo("av1")
        assertThat(VideoPlayerUtils.codecKeyFromMimeType("video/mp4; codecs=\"hev1.1.6.L93.B0\"")).isEqualTo("hevc")
        assertThat(VideoPlayerUtils.codecKeyFromMimeType("video/webm")).isEqualTo("vp9")
        assertThat(VideoPlayerUtils.codecKeyFromMimeType("")).isEqualTo("h264")
    }

    @Test
    fun `codec labels and size keys are simple mappings`() {
        assertThat(VideoPlayerUtils.codecLabelFromKey("av1")).isEqualTo("AV1")
        assertThat(VideoPlayerUtils.codecLabelFromKey("h264")).isEqualTo("H264")
        assertThat(VideoPlayerUtils.codecLabelFromKey("custom")).isEqualTo("CUSTOM")
        assertThat(VideoPlayerUtils.streamSizeKey(1080, "vp9")).isEqualTo("1080_vp9")
    }

    @Test
    fun `stream helpers read the resolution label and the container`() {
        val stream =
            VideoStream
                .Builder()
                .setId("248")
                .setContent("https://example.invalid/248.webm", true)
                .setMediaFormat(MediaFormat.WEBM)
                .setResolution("1080p60")
                .setIsVideoOnly(true)
                .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                .build()

        assertThat(VideoPlayerUtils.codecKeyFromStream(stream)).isEqualTo("vp9")
        assertThat(VideoPlayerUtils.qualityHeightFromStream(stream)).isEqualTo(1080)
    }
}
