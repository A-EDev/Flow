package io.github.aedev.flow.player.stream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

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
}
