package io.github.aedev.flow.player.datasource

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.innertube.models.response.PlayerResponse
import org.junit.Test

class StreamSwapTableTest {
    private val table = StreamSwapTable(maxSwapsPerVideo = 3)

    private val walledVideo = url(set = "visionos-set", client = "VISIONOS", itag = 136, lmt = 1742990106743076, clen = 5161046)
    private val walledAudio = url(set = "visionos-set", client = "VISIONOS", itag = 140, lmt = 1742990083211795, clen = 4151075)

    private val tizen =
        listOf(
            format(136, url(set = "tizen-set", client = "TVHTML5", itag = 136, lmt = 1742990106743076, clen = 5161046)),
            format(140, url(set = "tizen-set", client = "TVHTML5", itag = 140, lmt = 1742990083211795, clen = 4151075)),
        )

    @Test
    fun `a refused file is rewritten to the same file from the replacement set`() {
        table.register("wDQ0KNV3Tpc", listOf(walledVideo, walledAudio))

        assertThat(table.record(walledAudio, tizen)).isTrue()

        assertThat(table.rewrite(walledAudio)).isEqualTo(tizen[1].url)
        assertThat(table.rewrite(walledVideo)).isEqualTo(tizen[0].url)
    }

    @Test
    fun `each file under a shared itag goes to its own twin, as dubbed and DRC copies do`() {
        val original = url(set = "visionos-set", client = "VISIONOS", itag = 251, lmt = 100, clen = 4003141)
        val dubbed = url(set = "visionos-set", client = "VISIONOS", itag = 251, lmt = 200, clen = 3900000)
        table.register("pCJ9JGG0GQI", listOf(original, dubbed))
        val answer =
            listOf(
                format(251, url(set = "tizen-set", client = "TVHTML5", itag = 251, lmt = 100, clen = 4003141)),
                format(251, url(set = "tizen-set", client = "TVHTML5", itag = 251, lmt = 200, clen = 3900000)),
            )

        assertThat(table.record(original, answer)).isTrue()

        assertThat(table.rewrite(original)).isEqualTo(answer[0].url)
        assertThat(table.rewrite(dubbed)).isEqualTo(answer[1].url)
    }

    @Test
    fun `nothing is rewritten before a refusal`() {
        table.register("wDQ0KNV3Tpc", listOf(walledVideo, walledAudio))

        assertThat(table.rewrite(walledAudio)).isNull()
        assertThat(table.videoFor(walledAudio)).isEqualTo("wDQ0KNV3Tpc")
    }

    @Test
    fun `a replacement of a different encode is never used, since its byte ranges differ`() {
        table.register("wDQ0KNV3Tpc", listOf(walledAudio))
        val reencoded = listOf(format(140, url(set = "tizen-set", client = "TVHTML5", itag = 140, lmt = 1, clen = 4151075)))

        assertThat(table.record(walledAudio, reencoded)).isFalse()
        assertThat(table.rewrite(walledAudio)).isNull()
    }

    @Test
    fun `a url the player never registered is left to the player`() {
        assertThat(table.videoFor(walledAudio)).isNull()
        assertThat(table.record(walledAudio, tizen)).isFalse()
    }

    @Test
    fun `a refused replacement can be swapped again, and the original follows the chain`() {
        table.register("wDQ0KNV3Tpc", listOf(walledAudio))
        table.record(walledAudio, tizen)
        val again = listOf(format(140, url(set = "fresh-set", client = "TVHTML5", itag = 140, lmt = 1742990083211795, clen = 4151075)))

        assertThat(table.videoFor(tizen[1].url)).isEqualTo("wDQ0KNV3Tpc")
        assertThat(table.record(tizen[1].url!!, again)).isTrue()

        assertThat(table.rewrite(walledAudio)).isEqualTo(again[0].url)
    }

    @Test
    fun `audio and video recording one shared resolve spend one swap`() {
        val single = StreamSwapTable(maxSwapsPerVideo = 1)
        single.register("wDQ0KNV3Tpc", listOf(walledVideo, walledAudio))

        assertThat(single.record(walledVideo, tizen)).isTrue()
        assertThat(single.record(walledAudio, tizen)).isTrue()

        assertThat(single.hasSwapsLeft("wDQ0KNV3Tpc")).isFalse()
        assertThat(single.rewrite(walledAudio)).isEqualTo(tizen[1].url)
    }

    @Test
    fun `a video stops being swapped once its budget is spent`() {
        val small = StreamSwapTable(maxSwapsPerVideo = 1)
        small.register("wDQ0KNV3Tpc", listOf(walledVideo, walledAudio))

        assertThat(small.record(walledAudio, tizen)).isTrue()
        assertThat(small.record(tizen[1].url!!, tizen)).isFalse()
    }

    private fun url(
        set: String,
        client: String,
        itag: Int,
        lmt: Long,
        clen: Long,
    ): String = "https://rr6.googlevideo.com/videoplayback?expire=9999999999&id=$set&itag=$itag&c=$client&lmt=$lmt&clen=$clen"

    private fun format(
        itag: Int,
        url: String,
    ): PlayerResponse.StreamingData.Format {
        val lmt = url.substringAfter("lmt=").substringBefore('&').toLong()
        val clen = url.substringAfter("clen=").substringBefore('&').toLong()
        return PlayerResponse.StreamingData.Format(
            itag = itag,
            url = url,
            mimeType = if (itag == 140) "audio/mp4; codecs=\"mp4a.40.2\"" else "video/mp4; codecs=\"avc1.4d401f\"",
            bitrate = 128_000,
            width = if (itag == 140) null else 720,
            height = if (itag == 140) null else 720,
            contentLength = clen,
            quality = "medium",
            fps = null,
            qualityLabel = null,
            averageBitrate = null,
            audioQuality = null,
            approxDurationMs = null,
            audioSampleRate = null,
            audioChannels = null,
            loudnessDb = null,
            lastModified = lmt,
            signatureCipher = null,
        )
    }
}
