package io.github.aedev.flow.innertube.models.response

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures captured from a live /player response for a video carrying 26 audio tracks. */
private const val ORIGINAL_DRC = "ChEKBWFjb250EghvcmlnaW5hbAoICgNkcmMSATEKCgoEbGFuZxICZW4"
private const val ORIGINAL = "ChEKBWFjb250EghvcmlnaW5hbAoKCgRsYW5nEgJlbg"
private const val DUBBED_ARABIC = "Cg8KBWFjb250EgZkdWJiZWQKCgoEbGFuZxICYXI"
private const val DUBBED_CHINESE = "Cg8KBWFjb250EgZkdWJiZWQKDwoEbGFuZxIHemgtSGFucw"

class AudioXTagsTest {

    @Test
    fun `decodes the original track tags`() {
        assertEquals(
            mapOf("acont" to "original", "lang" to "en"),
            AudioXTags.decode(ORIGINAL)
        )
    }

    @Test
    fun `decodes the drc marker alongside the content role`() {
        assertEquals(
            mapOf("acont" to "original", "drc" to "1", "lang" to "en"),
            AudioXTags.decode(ORIGINAL_DRC)
        )
    }

    @Test
    fun `decodes dubbed tracks including multi byte language tags`() {
        assertEquals(mapOf("acont" to "dubbed", "lang" to "ar"), AudioXTags.decode(DUBBED_ARABIC))
        assertEquals(
            mapOf("acont" to "dubbed", "lang" to "zh-Hans"),
            AudioXTags.decode(DUBBED_CHINESE)
        )
    }

    @Test
    fun `malformed input decodes to no tags instead of throwing`() {
        assertTrue(AudioXTags.decode("not-base64!!").isEmpty())
        assertTrue(AudioXTags.decode("").isEmpty())
        assertTrue(AudioXTags.decode("AAAA").isEmpty())
    }
}

class AudioFormatMetadataTest {

    private fun audioFormat(
        itag: Int = 140,
        xtags: String? = null,
        isDrc: Boolean? = null,
        trackId: String? = null,
        displayName: String? = null,
        audioIsDefault: Boolean? = null,
    ) = PlayerResponse.StreamingData.Format(
        itag = itag,
        url = "https://example.invalid/stream",
        mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
        bitrate = 131_000,
        width = null,
        height = null,
        contentLength = null,
        quality = "tiny",
        fps = null,
        qualityLabel = null,
        averageBitrate = 131_000,
        audioQuality = "AUDIO_QUALITY_MEDIUM",
        approxDurationMs = null,
        audioSampleRate = 44_100,
        audioChannels = 2,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
        audioTrack = trackId?.let {
            PlayerResponse.StreamingData.Format.AudioTrack(
                displayName = displayName,
                id = it,
                audioIsDefault = audioIsDefault,
            )
        },
        isDrc = isDrc,
        xtags = xtags,
    )

    @Test
    fun `xtags decides the content role`() {
        assertTrue(audioFormat(xtags = ORIGINAL, trackId = "en.4").isOriginal)
        assertTrue(!audioFormat(xtags = DUBBED_ARABIC, trackId = "ar.3").isOriginal)
    }

    @Test
    fun `xtags outranks a misleading id suffix`() {
        // A dub whose id ends in the "original" suffix must still be reported as a dub.
        assertTrue(!audioFormat(xtags = DUBBED_ARABIC, trackId = "ar.4").isOriginal)
    }

    @Test
    fun `falls back to audioIsDefault then the id suffix when xtags is absent`() {
        assertTrue(audioFormat(trackId = "en.4", audioIsDefault = true).isOriginal)
        assertTrue(!audioFormat(trackId = "es.3", audioIsDefault = false).isOriginal)
        assertTrue(audioFormat(trackId = "en.4").isOriginal)
        assertTrue(!audioFormat(trackId = "es.3").isOriginal)
    }

    @Test
    fun `audio with no track metadata at all counts as original`() {
        assertTrue(audioFormat().isOriginal)
    }

    @Test
    fun `drc variants are identified from either the flag or the tags`() {
        assertTrue(audioFormat(isDrc = true).isDynamicRangeCompressed)
        assertTrue(audioFormat(xtags = ORIGINAL_DRC).isDynamicRangeCompressed)
        assertTrue(!audioFormat(xtags = ORIGINAL).isDynamicRangeCompressed)
        assertTrue(!audioFormat().isDynamicRangeCompressed)
    }

    @Test
    fun `language tag comes from the track id and falls back to xtags`() {
        assertEquals("zh-Hans", audioFormat(trackId = "zh-Hans.3").audioLanguageTag)
        assertEquals("ar", audioFormat(xtags = DUBBED_ARABIC).audioLanguageTag)
    }
}
