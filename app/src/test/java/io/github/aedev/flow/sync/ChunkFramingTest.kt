package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.protocol.ChunkFraming
import io.github.aedev.flow.sync.protocol.ChunkHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FLOW-SYNC/1 §7 chunk framing. The header/body separator is mandatory, so an **empty** collection
 * must still go on the wire as `header` + `\n` — emitting a bare header is what made a phone with
 * no likes / no history / an empty playlist die on the desktop with
 * `codec: chunk frame missing header newline`.
 */
class ChunkFramingTest {

    private val header = ChunkHeader("likes", 0, true)
    private val headerJson = """{"collection":"likes","seq":0,"last":true}"""

    @Test
    fun empty_collection_still_terminates_the_header() {
        val bytes = ChunkFraming.encode(header, emptyList())
        assertEquals(headerJson + "\n", String(bytes, Charsets.UTF_8))
    }

    @Test
    fun empty_chunk_round_trips_to_an_empty_body() {
        val (decodedHeader, body) = ChunkFraming.decode(ChunkFraming.encode(header, emptyList()))
        assertEquals(header, decodedHeader)
        assertTrue("an empty collection must decode to no records", body.isEmpty())
    }

    @Test
    fun non_empty_chunk_bytes_are_unchanged() {
        val lines = listOf("""{"id":"a"}""", """{"id":"b"}""")
        val bytes = ChunkFraming.encode(header, lines)
        assertEquals(headerJson + "\n" + """{"id":"a"}""" + "\n" + """{"id":"b"}""", String(bytes, Charsets.UTF_8))

        val (decodedHeader, body) = ChunkFraming.decode(bytes)
        assertEquals(header, decodedHeader)
        assertEquals(lines, body)
    }

    @Test
    fun decoder_still_tolerates_a_peer_sending_a_bare_header() {
        // Deliberate leniency: this is why desktop -> phone kept working throughout the bug.
        val (decodedHeader, body) = ChunkFraming.decode(headerJson.toByteArray(Charsets.UTF_8))
        assertEquals(header, decodedHeader)
        assertTrue(body.isEmpty())
    }
}
