package io.github.aedev.flow.player.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType

private fun audioStream(
    trackId: String?,
    trackName: String? = null,
    bitrate: Int = 131_000,
    trackType: AudioTrackType? = trackId?.let {
        if (it.endsWith(".4")) AudioTrackType.ORIGINAL else AudioTrackType.DUBBED
    },
): AudioStream = AudioStream.Builder()
    .setId(trackId ?: "140")
    .setContent("https://example.invalid/${trackId ?: "default"}-$bitrate", true)
    .setMediaFormat(MediaFormat.M4A)
    .setAverageBitrate(bitrate)
    .apply {
        trackId?.let { setAudioTrackId(it) }
        trackName?.let { setAudioTrackName(it) }
        trackType?.let { setAudioTrackType(it) }
    }
    .build()

class StreamProcessorTest {
    @Test
    fun `different language tracks with the same role suffix remain distinct`() {
        val english = StreamProcessor.audioTrackGroupingKey("en.4", null, null, null)
        val spanish = StreamProcessor.audioTrackGroupingKey("es.4", null, null, null)

        assertNotEquals(english, spanish)
    }

    @Test
    fun `audio track ids retain their language instead of the shared role suffix`() {
        assertEquals("en", StreamProcessor.audioTrackLanguageTag("en.4"))
        assertEquals("es-US", StreamProcessor.audioTrackLanguageTag("es-US.4"))
        assertNotEquals(
            StreamProcessor.audioTrackLanguageTag("en.4"),
            StreamProcessor.audioTrackLanguageTag("es.4")
        )
    }

    @Test
    fun `newpipe alternate audio track ids expose their language`() {
        assertEquals("hi", StreamProcessor.audioTrackLanguageTag("A_hi"))
        assertEquals("pt-BR", StreamProcessor.audioTrackLanguageTag("pt_BR"))
    }

    @Test
    fun `every dubbed track survives processing as its own row`() {
        val streams = listOf(
            audioStream("en.4", "English original"),
            audioStream("en.4", "English original", bitrate = 50_000),
            audioStream("ar.3", "Arabic"),
            audioStream("es.3", "Spanish"),
            audioStream("zh-Hans.3", "Chinese (Simplified)")
        )

        val processed = StreamProcessor.processAudioStreams(streams)

        assertEquals(4, processed.size)
        // Alternate bitrates of one track collapse; the best is kept.
        assertEquals(131_000, processed.single { it.audioTrackId == "en.4" }.averageBitrate)
    }

    @Test
    fun `untagged audio is dropped once named tracks are present`() {
        val processed = StreamProcessor.processAudioStreams(
            listOf(
                audioStream(null),
                audioStream("en.4", "English original"),
                audioStream("ar.3", "Arabic")
            )
        )

        assertEquals(2, processed.size)
        assertTrue(processed.all { it.audioTrackId != null })
    }

    @Test
    fun `untagged audio is kept when it is all we have`() {
        val processed = StreamProcessor.processAudioStreams(listOf(audioStream(null)))

        assertEquals(1, processed.size)
    }

    @Test
    fun `selected track index is resolved by identity not instance`() {
        val tracks = StreamProcessor.processAudioStreams(
            listOf(
                audioStream("en.4", "English original"),
                audioStream("ar.3", "Arabic"),
                audioStream("es.3", "Spanish")
            )
        )
        // A distinct instance of the same logical track, as produced by the selection pass.
        val playing = audioStream("ar.3", "Arabic", bitrate = 50_000)

        val index = StreamProcessor.indexOfAudioTrack(tracks, playing)

        assertEquals("ar.3", tracks[index].audioTrackId)
    }

    @Test
    fun `only non original tracks override the default audio`() {
        assertTrue(StreamProcessor.overridesDefaultAudioTrack(audioStream("ar.3", "Arabic")))
        assertFalse(
            StreamProcessor.overridesDefaultAudioTrack(audioStream("en.4", "English original"))
        )
        assertFalse(StreamProcessor.overridesDefaultAudioTrack(null))
        assertFalse(StreamProcessor.overridesDefaultAudioTrack(audioStream(null, trackType = null)))
    }
}
