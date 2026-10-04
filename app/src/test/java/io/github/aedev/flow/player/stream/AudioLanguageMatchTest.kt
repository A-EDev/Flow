package io.github.aedev.flow.player.stream

import io.github.aedev.flow.innertube.models.response.PlayerResponse
import io.github.aedev.flow.utils.MusicPlayerUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLanguageMatchTest {
    @Test
    fun `a tag matches its own language and its regional forms`() {
        assertTrue(AudioStreamSelector.languageMatches("en", "en"))
        assertTrue(AudioStreamSelector.languageMatches("en-US", "en"))
        assertTrue(AudioStreamSelector.languageMatches("pt-BR", "pt-br"))
    }

    @Test
    fun `a tag never matches a different language that shares letters`() {
        assertFalse(AudioStreamSelector.languageMatches("fr-FR", "en"))
        assertFalse(AudioStreamSelector.languageMatches("zh", "hi"))
        assertFalse(AudioStreamSelector.languageMatches("pt", "pt-BR"))
        assertFalse(AudioStreamSelector.languageMatches(null, "en"))
        assertFalse(AudioStreamSelector.languageMatches("en", ""))
    }

    @Test
    fun `music keeps the English original over a louder French dub`() {
        val english = audioFormat(id = "en.4", name = "English original", original = true, itag = 140, bitrate = 128_000)
        val french = audioFormat(id = "fr-FR.3", name = "French (FR)", original = false, itag = 141, bitrate = 130_000)

        val picked = MusicPlayerUtils.preferredAudioFormats(listOf(english, french), "en")

        assertEquals(listOf(english), picked)
    }

    @Test
    fun `music matches a regional dub by its track id`() {
        val english = audioFormat(id = "en.4", name = "English original", original = true, itag = 140)
        val brazilian = audioFormat(id = "pt-BR.3", name = "Portuguese (Brazil)", original = false, itag = 141)

        assertEquals(listOf(brazilian), MusicPlayerUtils.preferredAudioFormats(listOf(english, brazilian), "pt"))
    }

    private fun audioFormat(
        id: String,
        name: String,
        original: Boolean,
        itag: Int,
        bitrate: Int = 130_000,
    ) = PlayerResponse.StreamingData.Format(
        itag = itag,
        url = "https://example.invalid/$id/$itag",
        mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
        bitrate = bitrate,
        width = null,
        height = null,
        contentLength = null,
        quality = "tiny",
        fps = null,
        qualityLabel = null,
        averageBitrate = bitrate,
        audioQuality = "AUDIO_QUALITY_MEDIUM",
        approxDurationMs = "1000",
        audioSampleRate = 44_100,
        audioChannels = 2,
        loudnessDb = null,
        lastModified = null,
        signatureCipher = null,
        audioTrack =
            PlayerResponse.StreamingData.Format.AudioTrack(
                displayName = name,
                id = id,
                isAutoDubbed = !original,
                audioIsDefault = original,
            ),
    )
}
