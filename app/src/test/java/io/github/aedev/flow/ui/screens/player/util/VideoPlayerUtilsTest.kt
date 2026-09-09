package io.github.aedev.flow.ui.screens.player.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.VideoStream

class VideoPlayerUtilsTest {
    @Test
    fun `formatTime renders minutes and seconds without hours`() {
        assertThat(VideoPlayerUtils.formatTime(0L)).isEqualTo("0:00")
        assertThat(VideoPlayerUtils.formatTime(59_000L)).isEqualTo("0:59")
        assertThat(VideoPlayerUtils.formatTime(59_999L)).isEqualTo("0:59")
        assertThat(VideoPlayerUtils.formatTime(61_000L)).isEqualTo("1:01")
        assertThat(VideoPlayerUtils.formatTime(600_000L)).isEqualTo("10:00")
    }

    @Test
    fun `padMinutes zero pads the minutes field only while there are no hours`() {
        assertThat(VideoPlayerUtils.formatTime(0L, padMinutes = true)).isEqualTo("00:00")
        assertThat(VideoPlayerUtils.formatTime(59_000L, padMinutes = true)).isEqualTo("00:59")
        assertThat(VideoPlayerUtils.formatTime(61_000L, padMinutes = true)).isEqualTo("01:01")
        assertThat(VideoPlayerUtils.formatTime(3_661_000L, padMinutes = true)).isEqualTo("1:01:01")
    }

    @Test
    fun `an hour or more adds an unpadded hours field`() {
        assertThat(VideoPlayerUtils.formatTime(3_600_000L)).isEqualTo("1:00:00")
        assertThat(VideoPlayerUtils.formatTime(3_661_000L)).isEqualTo("1:01:01")
        assertThat(VideoPlayerUtils.formatTime(36_000_000L)).isEqualTo("10:00:00")
        assertThat(VideoPlayerUtils.formatTime(36_000_000L, padMinutes = true)).isEqualTo("10:00:00")
    }

    @Test
    fun `negative durations leak a minus sign into the fields`() {
        // Pins current behaviour: nothing clamps a negative position, so the sign lands inside the
        // zero-padded fields instead of being dropped.
        assertThat(VideoPlayerUtils.formatTime(-999L)).isEqualTo("0:00")
        assertThat(VideoPlayerUtils.formatTime(-1_000L)).isEqualTo("0:-1")
        assertThat(VideoPlayerUtils.formatTime(-1_000L, padMinutes = true)).isEqualTo("00:-1")
        assertThat(VideoPlayerUtils.formatTime(-61_000L)).isEqualTo("-1:-1")
    }

    @Test
    fun `whole speeds drop the fraction`() {
        assertThat(VideoPlayerUtils.formatSpeedLabel(1.0f)).isEqualTo("1x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(2.0f)).isEqualTo("2x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(1.005f)).isEqualTo("1x")
    }

    @Test
    fun `fractional speeds keep up to two decimals with trailing zeros trimmed`() {
        assertThat(VideoPlayerUtils.formatSpeedLabel(0.25f)).isEqualTo("0.25x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(0.75f)).isEqualTo("0.75x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(1.25f)).isEqualTo("1.25x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(1.5f)).isEqualTo("1.5x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(1.1f)).isEqualTo("1.1x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(1.333f)).isEqualTo("1.33x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(1.999f)).isEqualTo("2x")
    }

    @Test
    fun `speed labels clamp to the given range`() {
        assertThat(VideoPlayerUtils.formatSpeedLabel(0f)).isEqualTo("0.1x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(0.05f)).isEqualTo("0.1x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(12f)).isEqualTo("10x")
        assertThat(VideoPlayerUtils.formatSpeedLabel(5f, maxSpeed = VideoPlayerUtils.MAX_BOOST_SPEED)).isEqualTo("4x")
    }

    @Test
    fun `the default label cap is 10x not the boost cap`() {
        // Pins current behaviour: MAX_BOOST_SPEED only bounds boostedPlaybackSpeed; the label
        // formatter defaults to 10x, so a 5x speed is printed as "5x".
        assertThat(VideoPlayerUtils.formatSpeedLabel(5f)).isEqualTo("5x")
    }

    @Test
    fun `a boost below the target jumps straight to the target`() {
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = 1.0f, targetSpeed = 2.0f)).isEqualTo(2.0f)
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = 1.5f, targetSpeed = 2.0f)).isEqualTo(2.0f)
    }

    @Test
    fun `a boost at or above the target steps up by half`() {
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = 2.0f, targetSpeed = 2.0f)).isEqualTo(2.5f)
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = 3.0f, targetSpeed = 2.0f)).isEqualTo(3.5f)
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = 3.7f, targetSpeed = 2.0f)).isEqualTo(4.0f)
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = 4.0f, targetSpeed = 2.0f)).isEqualTo(4.0f)
    }

    @Test
    fun `a non positive current speed is treated as 1x`() {
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = 0f, targetSpeed = 2.0f)).isEqualTo(2.0f)
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = -1f, targetSpeed = 2.0f)).isEqualTo(2.0f)
    }

    @Test
    fun `the target is clamped to the boost cap`() {
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = 1.0f, targetSpeed = 10f)).isEqualTo(4.0f)
    }

    @Test
    fun `a target below the current speed still steps up`() {
        // Pins current behaviour: a target slower than the current speed is not applied; the
        // current speed is bumped by the step instead.
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = 1.0f, targetSpeed = 0.5f)).isEqualTo(1.5f)
        assertThat(VideoPlayerUtils.boostedPlaybackSpeed(currentSpeed = 1.0f, targetSpeed = 0f)).isEqualTo(1.5f)
    }

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
        assertThat(VideoPlayerUtils.qualityLabelFromStream(stream)).isEqualTo("1080p60")
    }
}
