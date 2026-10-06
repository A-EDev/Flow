package io.github.aedev.flow.player.sabr.ump

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UmpFrameDecoderTest {
    @Test
    fun fragmentedFrameIsRetainedUntilComplete() {
        val decoder = UmpFrameDecoder(maxRetainedBytes = 64)
        val expectedPayload = byteArrayOf(4, 5, 6, 7)
        val encoded = frame(type = 20, payload = expectedPayload)

        decoder.feed(encoded, length = 2)
        assertFalse(decoder.hasNext())
        decoder.feed(encoded, offset = 2, length = 1)
        assertFalse(decoder.hasNext())
        decoder.feed(encoded, offset = 3, length = encoded.size - 3)

        assertTrue(decoder.hasNext())
        val decoded = decoder.next()
        assertEquals(20, decoded.type)
        assertArrayEquals(expectedPayload, decoded.payload)
        assertFalse(decoder.hasNext())
    }

    @Test
    fun oversizedDeclaredFrameFailsAndDecoderCanBeResetForNextResponse() {
        val decoder = UmpFrameDecoder(maxRetainedBytes = 8)

        assertThrows(UmpFrameDecoder.ResourceLimitExceeded::class.java) {
            decoder.feed(byteArrayOf(20, 9))
        }
        assertFalse(decoder.hasNext())

        decoder.reset()
        val expectedPayload = byteArrayOf(1, 2)
        decoder.feed(frame(type = 21, payload = expectedPayload))
        val decoded = decoder.next()
        assertEquals(21, decoded.type)
        assertArrayEquals(expectedPayload, decoded.payload)
    }

    @Test
    fun queuedPayloadBytesAreIncludedInRetainedCapacity() {
        val decoder = UmpFrameDecoder(maxRetainedBytes = 16)
        decoder.feed(frame(type = 20, payload = ByteArray(12)))

        assertThrows(UmpFrameDecoder.ResourceLimitExceeded::class.java) {
            decoder.feed(frame(type = 21, payload = ByteArray(3)))
        }
        assertFalse(decoder.hasNext())
    }

    private fun frame(
        type: Int,
        payload: ByteArray,
    ): ByteArray = UmpVarInt.encode(type.toLong()) + UmpVarInt.encode(payload.size.toLong()) + payload
}
