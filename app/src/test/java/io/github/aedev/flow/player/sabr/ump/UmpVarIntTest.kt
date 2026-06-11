package io.github.aedev.flow.player.sabr.ump

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Vectors derived from the canonical UMP varint layout
 * (gsuberland/UMP_Format + LuanRT/googlevideo UmpReader):
 * prefix bits of the first byte are the LOW-order bits, following bytes little-endian.
 */
class UmpVarIntTest {

    @Test
    fun `decode known 2-byte vectors`() {
        // 128 → b0 = 0x80 | (128 & 0x3F) = 0x80, b1 = 128 >> 6 = 2
        assertEquals(128L, UmpVarInt.decode(byteArrayOf(0x80.toByte(), 0x02), 0))
        // (0x88 & 0x3F) | (0x03 << 6) = 8 + 192 = 200
        assertEquals(200L, UmpVarInt.decode(byteArrayOf(0x88.toByte(), 0x03), 0))
        // max 2-byte value
        assertEquals(16383L, UmpVarInt.decode(byteArrayOf(0xBF.toByte(), 0xFF.toByte()), 0))
    }

    @Test
    fun `decode known 3-byte vectors`() {
        // 16384 → b0 = 0xC0, rest = 16384 >> 5 = 512 → LE [0x00, 0x02]
        assertEquals(16384L, UmpVarInt.decode(byteArrayOf(0xC0.toByte(), 0x00, 0x02), 0))
        assertEquals(2097151L, UmpVarInt.decode(byteArrayOf(0xDF.toByte(), 0xFF.toByte(), 0xFF.toByte()), 0))
    }

    @Test
    fun `decode known 4-byte vectors`() {
        // 0x200000 → b0 = 0xE0, rest = 0x200000 >> 4 = 0x20000 → LE [0x00, 0x00, 0x02]
        assertEquals(0x200000L, UmpVarInt.decode(byteArrayOf(0xE0.toByte(), 0x00, 0x00, 0x02), 0))
    }

    @Test
    fun `decode known 5-byte vectors`() {
        // 0x10000000 → b0 = 0xF0, LE32 [0x00, 0x00, 0x00, 0x10]
        assertEquals(
            0x10000000L,
            UmpVarInt.decode(byteArrayOf(0xF0.toByte(), 0x00, 0x00, 0x00, 0x10), 0)
        )
        assertEquals(
            0xFFFFFFFFL,
            UmpVarInt.decode(
                byteArrayOf(0xF0.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()), 0
            )
        )
    }

    @Test
    fun `encode produces canonical bytes`() {
        assertArrayEquals(byteArrayOf(0x00), UmpVarInt.encode(0))
        assertArrayEquals(byteArrayOf(0x7F), UmpVarInt.encode(127))
        assertArrayEquals(byteArrayOf(0x80.toByte(), 0x02), UmpVarInt.encode(128))
        assertArrayEquals(byteArrayOf(0x88.toByte(), 0x03), UmpVarInt.encode(200))
        assertArrayEquals(byteArrayOf(0xC0.toByte(), 0x00, 0x02), UmpVarInt.encode(16384))
    }

    @Test
    fun `round-trip across all size boundaries`() {
        val values = longArrayOf(
            0, 1, 63, 64, 127, 128, 200, 16383, 16384, 65535,
            2097151, 2097152, 268435455, 268435456, 0xFFFFFFFFL
        )
        for (v in values) {
            val encoded = UmpVarInt.encode(v)
            assertEquals("size of $v", UmpVarInt.encodedSize(v), encoded.size)
            assertEquals("decode(encode($v))", v, UmpVarInt.decode(encoded, 0))
            assertEquals("read(encode($v))", v, UmpVarInt.read(ByteArrayInputStream(encoded)))
        }
    }

    @Test
    fun `frame decoder handles frames split across feeds`() {
        val payload = ByteArray(300) { (it % 251).toByte() }
        val frameBytes = UmpVarInt.encode(UmpPartType.MEDIA.toLong()) +
            UmpVarInt.encode(payload.size.toLong()) + payload

        val decoder = UmpFrameDecoder()
        val mid = frameBytes.size / 2
        decoder.feed(frameBytes, 0, mid)
        decoder.feed(frameBytes, mid, frameBytes.size - mid)

        assertEquals(true, decoder.hasNext())
        val frame = decoder.next()
        assertEquals(UmpPartType.MEDIA, frame.type)
        assertArrayEquals(payload, frame.payload)
        assertEquals(false, decoder.hasNext())
    }
}
