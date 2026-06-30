package io.github.aedev.flow.sync.protocol

import io.github.aedev.flow.sync.crypto.SyncCrypto
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * The FLOW-SYNC/1 wire envelope. Each WebSocket binary message is:
 *
 * ```
 * ver:u8 │ frame_type:u8 │ seq:u64 BE │ nonce:12 │ ciphertext ∥ tag(16)
 * ```
 *
 * The 10-byte cleartext header is authenticated by the AAD. The payload is gzip-compressed
 * before sealing. [seal] and [open] are pure transforms; sequence ordering is enforced by
 * the protocol layer ([SyncProtocol]).
 */
object SyncCodec {

    const val VERSION: Byte = 0x01
    const val HEADER_LEN = 10 // ver(1) + frame_type(1) + seq(8)
    const val AAD_LEN = 26 // ver(1) + session_id(16) + frame_type(1) + seq(8)
    val MIN_FRAME_LEN = HEADER_LEN + SyncCrypto.NONCE_LEN + SyncCrypto.TAG_LEN // 38

    // --- gzip (RFC 1952), always-on in v1 ---

    fun gzip(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(maxOf(32, data.size / 2))
        GZIPOutputStream(bos).use { it.write(data) }
        return bos.toByteArray()
    }

    fun gunzip(data: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }

    // --- AAD ---

    fun buildAad(sessionId: ByteArray, frameType: Byte, seq: Long): ByteArray {
        require(sessionId.size == SyncCrypto.SESSION_ID_LEN) { "session id must be 16 bytes" }
        val aad = ByteArray(AAD_LEN)
        aad[0] = VERSION
        System.arraycopy(sessionId, 0, aad, 1, 16)
        aad[17] = frameType
        writeLongBE(aad, 18, seq)
        return aad
    }

    // --- seal / open ---

    /** Build a full wire message: `gzip(plaintext)` → AES-256-GCM → `header ∥ nonce ∥ ct∥tag`. */
    fun seal(sealKey: ByteArray, sessionId: ByteArray, frameType: Byte, seq: Long, plaintext: ByteArray): ByteArray {
        val compressed = gzip(plaintext)
        val nonce = SyncCrypto.randomNonce()
        val aad = buildAad(sessionId, frameType, seq)
        val ctTag = SyncCrypto.seal(sealKey, nonce, compressed, aad)

        val out = ByteArray(HEADER_LEN + SyncCrypto.NONCE_LEN + ctTag.size)
        out[0] = VERSION
        out[1] = frameType
        writeLongBE(out, 2, seq)
        System.arraycopy(nonce, 0, out, HEADER_LEN, SyncCrypto.NONCE_LEN)
        System.arraycopy(ctTag, 0, out, HEADER_LEN + SyncCrypto.NONCE_LEN, ctTag.size)
        return out
    }

    data class Opened(val frameType: Byte, val seq: Long, val plaintext: ByteArray)

    /**
     * Parse + decrypt + gunzip a wire message. Throws [IllegalArgumentException] for a malformed
     * envelope and [javax.crypto.AEADBadTagException] when authentication fails (caller drops the
     * socket). Does NOT enforce the expected sequence — that is the protocol layer's job.
     */
    fun open(openKey: ByteArray, sessionId: ByteArray, message: ByteArray): Opened {
        require(message.size >= MIN_FRAME_LEN) { "frame too short: ${message.size}" }
        require(message[0] == VERSION) { "unsupported frame version: ${message[0]}" }
        val frameType = message[1]
        val seq = readLongBE(message, 2)
        val nonce = message.copyOfRange(HEADER_LEN, HEADER_LEN + SyncCrypto.NONCE_LEN)
        val ctTag = message.copyOfRange(HEADER_LEN + SyncCrypto.NONCE_LEN, message.size)
        val aad = buildAad(sessionId, frameType, seq)
        val compressed = SyncCrypto.open(openKey, nonce, ctTag, aad)
        val plaintext = gunzip(compressed)
        return Opened(frameType, seq, plaintext)
    }

    // --- big-endian u64 helpers ---

    fun writeLongBE(buf: ByteArray, offset: Int, value: Long) {
        buf[offset] = (value ushr 56).toByte()
        buf[offset + 1] = (value ushr 48).toByte()
        buf[offset + 2] = (value ushr 40).toByte()
        buf[offset + 3] = (value ushr 32).toByte()
        buf[offset + 4] = (value ushr 24).toByte()
        buf[offset + 5] = (value ushr 16).toByte()
        buf[offset + 6] = (value ushr 8).toByte()
        buf[offset + 7] = value.toByte()
    }

    fun readLongBE(buf: ByteArray, offset: Int): Long {
        var v = 0L
        for (i in 0 until 8) {
            v = (v shl 8) or (buf[offset + i].toLong() and 0xFF)
        }
        return v
    }
}
