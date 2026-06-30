package io.github.aedev.flow.sync.crypto

import java.io.ByteArrayOutputStream

/**
 * Byte-encoding helpers for FLOW-SYNC/1. Hand-rolled (no `android.util.Base64`, no
 * `java.util.Base64`) so they are byte-exact, portable across all minSdk-21 devices, and run in
 * plain JVM unit tests without Robolectric.
 *
 * - base64 is **URL-safe, no padding** (`b64url`)
 * - hex is lowercase; used by the shared golden vectors.
 */
object SyncBytes {

    private const val B64URL_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

    private val B64URL_DECODE: IntArray = IntArray(128) { -1 }.also { table ->
        for (i in B64URL_ALPHABET.indices) table[B64URL_ALPHABET[i].code] = i
        table['+'.code] = 62
        table['/'.code] = 63
    }

    private val HEX_DIGITS = "0123456789abcdef".toCharArray()

    // --- base64url ---

    fun b64urlEncode(data: ByteArray): String {
        val sb = StringBuilder((data.size + 2) / 3 * 4)
        var i = 0
        while (i + 3 <= data.size) {
            val n = ((data[i].toInt() and 0xFF) shl 16) or
                ((data[i + 1].toInt() and 0xFF) shl 8) or
                (data[i + 2].toInt() and 0xFF)
            sb.append(B64URL_ALPHABET[(n ushr 18) and 0x3F])
            sb.append(B64URL_ALPHABET[(n ushr 12) and 0x3F])
            sb.append(B64URL_ALPHABET[(n ushr 6) and 0x3F])
            sb.append(B64URL_ALPHABET[n and 0x3F])
            i += 3
        }
        when (data.size - i) {
            1 -> {
                val n = (data[i].toInt() and 0xFF) shl 16
                sb.append(B64URL_ALPHABET[(n ushr 18) and 0x3F])
                sb.append(B64URL_ALPHABET[(n ushr 12) and 0x3F])
            }
            2 -> {
                val n = ((data[i].toInt() and 0xFF) shl 16) or ((data[i + 1].toInt() and 0xFF) shl 8)
                sb.append(B64URL_ALPHABET[(n ushr 18) and 0x3F])
                sb.append(B64URL_ALPHABET[(n ushr 12) and 0x3F])
                sb.append(B64URL_ALPHABET[(n ushr 6) and 0x3F])
            }
        }
        return sb.toString()
    }

    fun b64urlDecode(s: String): ByteArray {
        val out = ByteArrayOutputStream(s.length * 3 / 4 + 1)
        var buffer = 0
        var bits = 0
        for (ch in s) {
            if (ch == '=' || ch == '\n' || ch == '\r') continue // tolerate padding/whitespace
            val code = ch.code
            val v = if (code < 128) B64URL_DECODE[code] else -1
            require(v >= 0) { "Invalid base64url character: '$ch'" }
            buffer = (buffer shl 6) or v
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer ushr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    // --- hex ---

    fun toHex(data: ByteArray): String {
        val sb = StringBuilder(data.size * 2)
        for (b in data) {
            val v = b.toInt() and 0xFF
            sb.append(HEX_DIGITS[v ushr 4])
            sb.append(HEX_DIGITS[v and 0x0F])
        }
        return sb.toString()
    }

    fun fromHex(hex: String): ByteArray {
        val clean = hex.trim()
        require(clean.length % 2 == 0) { "Hex string must have even length" }
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val hi = Character.digit(clean[i * 2], 16)
            val lo = Character.digit(clean[i * 2 + 1], 16)
            require(hi >= 0 && lo >= 0) { "Invalid hex string" }
            out[i] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    /** Overwrite a key buffer with zeros. Best-effort key hygiene. */
    fun zeroize(vararg arrays: ByteArray?) {
        for (a in arrays) a?.fill(0)
    }
}
