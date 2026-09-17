package io.github.aedev.flow.player.stream

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.VideoQuality
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import org.junit.Test

/**
 * Picking a quality from the menu used to need a NewPipe `StreamInfo`, so on every load InnerTube
 * resolved by itself — the common case — the menu did nothing at all.
 */
class MergedPlaybackQualityTest {
    private val videoFormats =
        listOf(
            videoFormat(itag = 137, height = 1080, label = "1080p"),
            videoFormat(itag = 136, height = 720, label = "720p"),
            videoFormat(itag = 135, height = 480, label = "480p"),
        )
    private val audioFormats = listOf(audioFormat(itag = 140))

    private fun select(quality: VideoQuality) =
        MergedPlaybackAssembly.selectQualityStreams(
            innerTubeVideoFormats = videoFormats,
            innerTubeAudioFormats = audioFormats,
            quality = quality,
            preferredAudioLanguage = "",
            preferredCodecKey = "h264",
        )

    @Test
    fun `a quality resolves from the innertube formats with no extractor info`() {
        val (video, audio) = select(VideoQuality.Q_720P)

        assertThat(video).isNotNull()
        assertThat(video!!.height).isEqualTo(720)
        assertThat(audio).isNotNull()
    }

    @Test
    fun `each offered quality picks its own stream`() {
        assertThat(select(VideoQuality.Q_1080P).first?.height).isEqualTo(1080)
        assertThat(select(VideoQuality.Q_480P).first?.height).isEqualTo(480)
    }

    @Test
    fun `no formats at all still resolves to nothing rather than throwing`() {
        val (video, audio) =
            MergedPlaybackAssembly.selectQualityStreams(
                innerTubeVideoFormats = emptyList(),
                innerTubeAudioFormats = emptyList(),
                quality = VideoQuality.Q_720P,
                preferredAudioLanguage = "",
                preferredCodecKey = "h264",
            )

        assertThat(video).isNull()
        assertThat(audio).isNull()
    }

    private fun videoFormat(
        itag: Int,
        height: Int,
        label: String,
    ) = PlayerResponse.StreamingData.Format(
        itag = itag,
        url = "https://example.invalid/$itag",
        mimeType = "video/mp4; codecs=\"avc1.640028\"",
        bitrate = height * 2_000,
        width = height * 16 / 9,
        height = height,
        contentLength = 1_000_000L,
        quality = label,
        fps = 30,
        qualityLabel = label,
        averageBitrate = height * 2_000,
        audioQuality = null,
        approxDurationMs = "600000",
        audioSampleRate = null,
        audioChannels = null,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
    )

    private fun audioFormat(itag: Int) =
        PlayerResponse.StreamingData.Format(
            itag = itag,
            url = "https://example.invalid/$itag",
            mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
            bitrate = 128_000,
            width = null,
            height = null,
            contentLength = 200_000L,
            quality = "tiny",
            fps = null,
            qualityLabel = null,
            averageBitrate = 128_000,
            audioQuality = "AUDIO_QUALITY_MEDIUM",
            approxDurationMs = "600000",
            audioSampleRate = 44_100,
            audioChannels = 2,
            loudnessDb = null,
            lastModified = null,
            signatureCipher = null,
        )
}
