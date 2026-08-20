package io.github.aedev.flow.data.shorts

import io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format
import io.github.aedev.flow.innertube.models.response.PlayerResponse.StreamingData.Format.Range
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The contract these guard is #917: a Shorts stream must reach the player as a ranged DASH source
 * whenever YouTube gave us the byte ranges to build one, because an unranged progressive fetch is
 * throttled below what H.264 needs.
 */
class ShortsDashManifestTest {
    private val url = "https://rr3---sn-example.googlevideo.com/videoplayback?expire=1&itag=137"

    private fun videoFormat(
        itag: Int = 137,
        initRange: Range? = Range(start = "0", end = "740"),
        indexRange: Range? = Range(start = "741", end = "1560"),
        contentLength: Long? = 4_812_345L,
    ) = Format(
        itag = itag,
        url = url,
        mimeType = "video/mp4; codecs=\"avc1.640028\"",
        bitrate = 2_500_000,
        width = 1080,
        height = 1920,
        contentLength = contentLength,
        quality = "hd1080",
        fps = 30,
        qualityLabel = "1080p",
        averageBitrate = 2_100_000,
        audioQuality = null,
        approxDurationMs = "28000",
        audioSampleRate = null,
        audioChannels = null,
        loudnessDb = null,
        lastModified = 1_700_000_000_000_000L,
        signatureCipher = null,
        initRange = initRange,
        indexRange = indexRange,
    )

    @Test
    fun `wraps a ranged format in a manifest that carries the stream url`() {
        val manifest = ShortsDashManifest.forVideo(videoFormat(), url, durationMs = 28_000L)

        assertNotNull("a format with init and index ranges must produce a manifest", manifest)
        assertTrue("the manifest must point at the resolved url", manifest!!.contains("googlevideo.com"))
        assertTrue("ranged playback is the whole point", manifest.contains("indexRange", ignoreCase = true))
    }

    // Without the ranges there is nothing to segment on, so the caller has to fall back to a plain
    // progressive fetch — the pre-#917 behaviour — rather than hand the player a broken manifest.
    @Test
    fun `declines a format with no byte ranges`() {
        val manifest =
            ShortsDashManifest.forVideo(
                videoFormat(initRange = null, indexRange = null),
                url,
                durationMs = 28_000L,
            )

        assertNull(manifest)
    }

    @Test
    fun `declines when the duration is unknown`() {
        assertNull(ShortsDashManifest.forVideo(videoFormat(), url, durationMs = null))
    }

    // A sub-second duration floors to zero, and a zero-length MPD would make the player report the
    // short as instantly ended.
    @Test
    fun `declines a duration that rounds down to zero seconds`() {
        assertNull(ShortsDashManifest.forVideo(videoFormat(), url, durationMs = 400L))
    }

    @Test
    fun `builds from the resolved url rather than the one on the format`() {
        val deobfuscated = "$url&sig=deobfuscated"
        val manifest =
            ShortsDashManifest.forVideo(
                videoFormat().copy(url = null),
                deobfuscated,
                durationMs = 28_000L,
            )

        assertNotNull("a ciphered format still resolves once the url is deobfuscated", manifest)
        assertTrue(manifest!!.contains("sig=deobfuscated"))
    }
}
