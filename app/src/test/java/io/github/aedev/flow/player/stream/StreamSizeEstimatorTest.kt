package io.github.aedev.flow.player.stream

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the download dialog's size column. Both extraction stacks feed it and neither is
 * guaranteed to carry `contentLength`, so the cases below pin the fallbacks rather than the happy
 * path alone.
 */
class StreamSizeEstimatorTest {
    private val audioMp4 = audioFormat(itag = 140, mimeType = MP4_AUDIO, bitrate = 128_000, contentLength = 1_000_000L)
    private val audioWebm = audioFormat(itag = 251, mimeType = WEBM_AUDIO, bitrate = 160_000, contentLength = 1_500_000L)

    @Test
    fun `video size includes the audio track it will be muxed with`() {
        val video = videoFormat(itag = 137, mimeType = MP4_VIDEO, height = 1080, contentLength = 50_000_000L)

        val sizes = StreamSizeEstimator.fromInnerTubeFormats(listOf(video), listOf(audioMp4, audioWebm))

        assertEquals(51_000_000L, sizes[VideoCodecUtils.streamSizeKey(1080, "h264")])
    }

    @Test
    fun `webm video pairs with the webm audio track`() {
        val video = videoFormat(itag = 248, mimeType = WEBM_VIDEO, height = 1080, contentLength = 40_000_000L)

        val sizes = StreamSizeEstimator.fromInnerTubeFormats(listOf(video), listOf(audioMp4, audioWebm))

        assertEquals(41_500_000L, sizes[VideoCodecUtils.streamSizeKey(1080, "vp9")])
    }

    @Test
    fun `a SABR format with no contentLength still gets an estimate from bitrate and duration`() {
        val video =
            videoFormat(
                itag = 137,
                mimeType = MP4_VIDEO,
                height = 1080,
                contentLength = null,
                bitrate = 4_000_000,
                approxDurationMs = "60000",
            )
        val audio = audioFormat(itag = 140, mimeType = MP4_AUDIO, bitrate = 128_000, contentLength = null, approxDurationMs = "60000")

        val sizes = StreamSizeEstimator.fromInnerTubeFormats(listOf(video), listOf(audio))

        // 4 Mbit/s + 128 kbit/s over 60 s.
        assertEquals(30_000_000L + 960_000L, sizes[VideoCodecUtils.streamSizeKey(1080, "h264")])
    }

    @Test
    fun `duration falls back to the caller's value when the format omits its own`() {
        val video =
            videoFormat(
                itag = 137,
                mimeType = MP4_VIDEO,
                height = 1080,
                contentLength = null,
                bitrate = 4_000_000,
                approxDurationMs = null,
            )

        val sizes = StreamSizeEstimator.fromInnerTubeFormats(listOf(video), emptyList(), fallbackDurationMs = 60_000L)

        assertEquals(30_000_000L, sizes[VideoCodecUtils.streamSizeKey(1080, "h264")])
    }

    @Test
    fun `a format with neither a length nor a usable bitrate is skipped rather than shown as zero`() {
        val video = videoFormat(itag = 137, mimeType = MP4_VIDEO, height = 1080, contentLength = null, bitrate = 0)

        val sizes = StreamSizeEstimator.fromInnerTubeFormats(listOf(video), emptyList())

        assertTrue(sizes.isEmpty())
        assertNull(sizes[VideoCodecUtils.streamSizeKey(1080, "h264")])
    }

    @Test
    fun `a portrait Short is keyed by its quality label, not by its long side`() {
        val video =
            videoFormat(
                itag = 137,
                mimeType = MP4_VIDEO,
                height = 1920,
                width = 1080,
                qualityLabel = "1080p",
                contentLength = 20_000_000L,
            )

        val sizes = StreamSizeEstimator.fromInnerTubeFormats(listOf(video), emptyList())

        assertEquals(20_000_000L, sizes[VideoCodecUtils.streamSizeKey(1080, "h264")])
        assertNull(sizes[VideoCodecUtils.streamSizeKey(1920, "h264")])
    }

    @Test
    fun `merge keeps the largest estimate for a resolution and codec pair`() {
        val key = VideoCodecUtils.streamSizeKey(1080, "h264")

        val merged = StreamSizeEstimator.merge(mapOf(key to 10L), mapOf(key to 25L), mapOf(key to 5L))

        assertEquals(25L, merged[key])
    }

    @Test
    fun `sizes derived from converted InnerTube streams match the ones derived from the formats`() {
        val video = videoFormat(itag = 137, mimeType = MP4_VIDEO, height = 1080, contentLength = 50_000_000L)
        val formatSizes = StreamSizeEstimator.fromInnerTubeFormats(listOf(video), listOf(audioMp4))

        val streamSizes =
            StreamSizeEstimator.fromExtractorStreams(
                InnerTubeStreamBridge.convertVideoFormats(listOf(video)),
                InnerTubeStreamBridge.convertAudioFormats(listOf(audioMp4)),
            )

        assertEquals(formatSizes, streamSizes)
    }

    private companion object {
        const val MP4_VIDEO = "video/mp4; codecs=\"avc1.640028\""
        const val WEBM_VIDEO = "video/webm; codecs=\"vp9\""
        const val MP4_AUDIO = "audio/mp4; codecs=\"mp4a.40.2\""
        const val WEBM_AUDIO = "audio/webm; codecs=\"opus\""
    }

    private fun videoFormat(
        itag: Int,
        mimeType: String,
        height: Int,
        width: Int = 1920,
        contentLength: Long?,
        bitrate: Int = 4_000_000,
        qualityLabel: String? = "${height}p",
        approxDurationMs: String? = null,
    ) = format(
        itag = itag,
        mimeType = mimeType,
        width = width,
        height = height,
        contentLength = contentLength,
        bitrate = bitrate,
        qualityLabel = qualityLabel,
        approxDurationMs = approxDurationMs,
    )

    private fun audioFormat(
        itag: Int,
        mimeType: String,
        bitrate: Int,
        contentLength: Long?,
        approxDurationMs: String? = null,
    ) = format(
        itag = itag,
        mimeType = mimeType,
        width = null,
        height = null,
        contentLength = contentLength,
        bitrate = bitrate,
        qualityLabel = null,
        approxDurationMs = approxDurationMs,
    )

    private fun format(
        itag: Int,
        mimeType: String,
        width: Int?,
        height: Int?,
        contentLength: Long?,
        bitrate: Int,
        qualityLabel: String?,
        approxDurationMs: String?,
    ) = PlayerResponse.StreamingData.Format(
        itag = itag,
        url = "https://example.invalid/$itag",
        mimeType = mimeType,
        bitrate = bitrate,
        width = width,
        height = height,
        contentLength = contentLength,
        quality = qualityLabel ?: "tiny",
        fps = null,
        qualityLabel = qualityLabel,
        averageBitrate = bitrate,
        audioQuality = null,
        approxDurationMs = approxDurationMs,
        audioSampleRate = null,
        audioChannels = null,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
    )
}
