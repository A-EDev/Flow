package io.github.aedev.flow.sync

import io.github.aedev.flow.sync.crypto.SyncCrypto
import io.github.aedev.flow.sync.protocol.FrameType
import io.github.aedev.flow.sync.protocol.SyncCodec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import javax.crypto.AEADBadTagException

class SyncCodecTest {

    private val master = SyncCrypto.randomMasterKey()
    private val sid = SyncCrypto.randomSessionId()
    private val keys = SyncCrypto.deriveKeys(master, sid)

    @Test
    fun gzip_roundtrip() {
        val data = "the quick brown fox ".repeat(500).toByteArray()
        val compressed = SyncCodec.gzip(data)
        assertTrue("should compress repetitive data", compressed.size < data.size)
        assertArrayEquals(data, SyncCodec.gunzip(compressed))
    }

    @Test
    fun long_be_roundtrip() {
        val buf = ByteArray(8)
        val values = longArrayOf(0L, 1L, 255L, 256L, 1781512000123L, Long.MAX_VALUE, -1L)
        for (v in values) {
            SyncCodec.writeLongBE(buf, 0, v)
            assertEquals(v, SyncCodec.readLongBE(buf, 0))
        }
        // Verify byte order is big-endian for a known value.
        SyncCodec.writeLongBE(buf, 0, 0x0102030405060708L)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), buf)
    }

    @Test
    fun aad_layout_is_26_bytes() {
        val aad = SyncCodec.buildAad(sid, FrameType.HELLO, 0L)
        assertEquals(SyncCodec.AAD_LEN, aad.size)
        assertEquals(SyncCodec.VERSION, aad[0])
        assertArrayEquals(sid, aad.copyOfRange(1, 17))
        assertEquals(FrameType.HELLO, aad[17])
    }

    @Test
    fun seal_open_roundtrip_host_to_client() {
        val plaintext = """{"deviceId":"abc","deviceName":"Pixel"}""".toByteArray()
        // Host seals with h2c; client opens with h2c.
        val message = SyncCodec.seal(keys.sealKey(isHost = true), sid, FrameType.HELLO_ACK, 3L, plaintext)
        assertTrue(message.size >= SyncCodec.MIN_FRAME_LEN)
        assertEquals(SyncCodec.VERSION, message[0])
        assertEquals(FrameType.HELLO_ACK, message[1])
        assertEquals(3L, SyncCodec.readLongBE(message, 2))

        val opened = SyncCodec.open(keys.openKey(isHost = false), sid, message)
        assertEquals(FrameType.HELLO_ACK, opened.frameType)
        assertEquals(3L, opened.seq)
        assertArrayEquals(plaintext, opened.plaintext)
    }

    @Test
    fun tampering_frame_type_header_breaks_auth() {
        val message = SyncCodec.seal(keys.sealKey(isHost = false), sid, FrameType.CHUNK, 9L, "x".toByteArray())
        message[1] = FrameType.COMPLETE // attacker rewrites the declared type
        try {
            SyncCodec.open(keys.openKey(isHost = true), sid, message)
            fail("expected auth failure — frame_type is bound into the AAD")
        } catch (e: AEADBadTagException) {
            // expected
        }
    }

    @Test
    fun tampering_seq_header_breaks_auth() {
        val message = SyncCodec.seal(keys.sealKey(isHost = false), sid, FrameType.CHUNK, 9L, "x".toByteArray())
        message[9] = 10 // bump the low byte of seq
        try {
            SyncCodec.open(keys.openKey(isHost = true), sid, message)
            fail("expected auth failure — seq is bound into the AAD")
        } catch (e: AEADBadTagException) {
            // expected
        }
    }

    @Test
    fun wrong_direction_key_fails() {
        val message = SyncCodec.seal(keys.sealKey(isHost = true), sid, FrameType.PING, 0L, "{}".toByteArray())
        // Opening an h2c frame with the c2h key (as if host opened its own send) must fail.
        try {
            SyncCodec.open(keys.sealKey(isHost = true).let { keys.clientToHost }, sid, message)
            fail("expected auth failure with wrong directional key")
        } catch (e: AEADBadTagException) {
            // expected
        }
    }
}
