package io.github.aedev.flow.innertube.models.response

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Decoded from a captured VISIONOS `/player` response. These fields were added from a live probe,
 * so the fixture is the probe's own output — a hand-written one would only prove the model agrees
 * with itself.
 */
class PlayerResponseFieldsTest {
    // Mirrors the InnerTube client's own Json: explicitNulls=false is what lets a nullable field
    // with no default (PlayabilityStatus.reason) be absent on a clean OK response.
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private fun response(name: String): PlayerResponse =
        javaClass.classLoader!!
            .getResourceAsStream(name)!!
            .bufferedReader()
            .use { json.decodeFromString(it.readText()) }

    private fun vod() = response("player_visionos_vod.json")

    @Test
    fun `storyboard spec and level are read`() {
        val storyboards = vod().storyboards?.playerStoryboardSpecRenderer

        assertThat(storyboards?.spec).isNotNull()
        assertThat(storyboards!!.spec!!).contains("i.ytimg.com/sb/")
        assertThat(storyboards.recommendedLevel).isEqualTo(2)
    }

    @Test
    fun `keywords are read for the recommendation engine`() {
        val keywords = vod().videoDetails?.keywords.orEmpty()

        assertThat(keywords).isNotEmpty()
        assertThat(keywords).contains("linus tech tips")
    }

    @Test
    fun `aspect ratio is read`() {
        assertThat(vod().streamingData?.aspectRatio).isWithin(0.001f).of(1.7777778f)
    }

    @Test
    fun `loudness normalisation config is read`() {
        val audio = vod().playerConfig?.audioConfig

        assertThat(audio?.loudnessTargetLkfs).isEqualTo(-14.0)
        assertThat(audio?.trackAbsoluteLoudnessLkfs).isEqualTo(-17.0)
        assertThat(audio?.enablePerFormatLoudness).isTrue()
        assertThat(audio?.loudnessNormalizationConfig?.minimumLoudnessTargetLkfs).isEqualTo(-31.0)
    }

    @Test
    fun `hls proxy config carries youtube's own initial bitrate`() {
        assertThat(vod().playerConfig?.hlsProxyConfig?.defaultInitialBitrate).isEqualTo(1572864L)
    }

    @Test
    fun `projection type and colour info are read per format`() {
        val formats = vod().streamingData?.adaptiveFormats.orEmpty()

        assertThat(formats.mapNotNull { it.projectionType }).contains("RECTANGULAR")
        assertThat(formats.any { it.colorInfo != null }).isTrue()
        assertThat(formats.none { it.colorInfo?.isHdr == true }).isTrue()
    }

    @Test
    fun `an sdr ladder reports no hdr format`() {
        val hdr =
            vod()
                .streamingData
                ?.adaptiveFormats
                .orEmpty()
                .filter { it.colorInfo?.isHdr == true }

        assertThat(hdr).isEmpty()
    }

    @Test
    fun `caption audio tracks pair a dub with its caption tracks`() {
        val captions = response("player_visionos_dubbed.json").captions?.playerCaptionsTracklistRenderer

        assertThat(captions?.defaultAudioTrackIndex).isNotNull()
        val audioTracks = captions?.audioTracks.orEmpty()
        assertThat(audioTracks).isNotEmpty()
        val spanish = audioTracks.firstOrNull { it.audioTrackId == "es.3" }
        assertThat(spanish).isNotNull()
        assertThat(spanish!!.captionTrackIndices).isNotEmpty()
        assertThat(spanish.defaultCaptionTrackIndex).isNotNull()
    }

    @Test
    fun `microformat carries the category and exact like count when the client returns it`() {
        val micro = response("player_web_vod.json").microformat?.playerMicroformatRenderer

        assertThat(micro?.category).isEqualTo("Science & Technology")
        assertThat(micro?.likeCount).isEqualTo("20886")
        assertThat(micro?.publishDate).startsWith("2026-09-15")
        assertThat(micro?.availableCountries).hasSize(249)
    }

    @Test
    fun `a visionos response simply has no microformat`() {
        assertThat(vod().microformat).isNull()
    }
}
