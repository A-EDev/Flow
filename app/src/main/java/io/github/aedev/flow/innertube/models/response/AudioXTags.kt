package io.github.aedev.flow.innertube.models.response

import java.util.Base64

/**
 * Decoder for the `xtags` blob YouTube attaches to adaptive audio formats.
 *
 * It is a base64url protobuf of repeated `{key, value}` string pairs — in practice `acont`
 * (`original`/`dubbed`), `drc` (`1` when dynamic-range compressed) and `lang`. Those are the only
 * authoritative signals separating an original track from a dub, and a DRC copy from its normal
 * twin (which are otherwise identical down to a few bytes/s of bitrate), so the three scalars are
 * read directly here instead of pulling in a proto schema for them.
 */
internal object AudioXTags {

    private const val TAG_FIELD = 0x0a      // top level: repeated tag, wire type 2
    private const val KEY_FIELD = 0x0a      // submessage field 1
    private const val VALUE_FIELD = 0x12    // submessage field 2
    private const val MAX_VARINT_SHIFT = 28

    fun decode(raw: String): Map<String, String> = runCatching {
        val normalized = raw.replace('+', '-').replace('/', '_').trimEnd('=')
        parse(Base64.getUrlDecoder().decode(normalized))
    }.getOrDefault(emptyMap())

    private fun parse(bytes: ByteArray): Map<String, String> {
        val tags = mutableMapOf<String, String>()
        var offset = 0
        while (offset < bytes.size) {
            if (bytes[offset].toInt() and 0xFF != TAG_FIELD) break
            val length = readVarint(bytes, offset + 1) ?: break
            val end = length.offset + length.value
            if (end > bytes.size) break
            parsePair(bytes, length.offset, end)?.let { tags[it.first] = it.second }
            offset = end
        }
        return tags
    }

    private fun parsePair(bytes: ByteArray, start: Int, end: Int): Pair<String, String>? {
        var offset = start
        var key: String? = null
        var value: String? = null
        while (offset < end) {
            val field = bytes[offset].toInt() and 0xFF
            if (field != KEY_FIELD && field != VALUE_FIELD) return null
            val length = readVarint(bytes, offset + 1) ?: return null
            val stop = length.offset + length.value
            if (stop > end) return null
            val text = String(bytes, length.offset, length.value, Charsets.UTF_8)
            if (field == KEY_FIELD) key = text else value = text
            offset = stop
        }
        return key?.let { parsedKey -> value?.let { parsedKey to it } }
    }

    private data class Varint(val value: Int, val offset: Int)

    private fun readVarint(bytes: ByteArray, start: Int): Varint? {
        var result = 0
        var shift = 0
        var offset = start
        while (offset < bytes.size && shift <= MAX_VARINT_SHIFT) {
            val byte = bytes[offset].toInt() and 0xFF
            result = result or ((byte and 0x7F) shl shift)
            offset++
            if (byte and 0x80 == 0) return Varint(result, offset)
            shift += 7
        }
        return null
    }
}
