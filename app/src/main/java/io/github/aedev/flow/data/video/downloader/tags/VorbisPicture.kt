package io.github.aedev.flow.data.video.downloader.tags

import java.util.Base64

private val PICTURE_MARKER = "METADATA_BLOCK_PICTURE=".encodeToByteArray()

/**
 * Opus and Vorbis store a cover as a base64 FLAC picture block in a `METADATA_BLOCK_PICTURE`
 * comment. [android.media.MediaMetadataRetriever.getEmbeddedPicture] does not return that block.
 *
 * The block layout is the one [androidx.media3.extractor.metadata.flac.PictureFrame.fromPictureBlock]
 * reads. That method is not used here: its parser initializes
 * [androidx.media3.common.util.ParsableByteArray], whose static check reads
 * `Build.FINGERPRINT` and throws in unit tests when the Android stub leaves it null.
 */
internal fun vorbisPictureBytes(container: ByteArray): ByteArray? {
    var from = 0
    while (from < container.size) {
        val start = container.indexOfSlice(PICTURE_MARKER, from)
        if (start < 0) return null
        val valueStart = start + PICTURE_MARKER.size
        var end = valueStart
        while (end < container.size && container[end].isPictureCommentByte()) end++
        if (end > valueStart) {
            pictureBytesFromComment(container.copyOfRange(valueStart, end).decodeToString())?.let { return it }
        }
        from = valueStart
    }
    return null
}

internal fun pictureBytesFromComment(value: String): ByteArray? {
    val decoded =
        try {
            Base64.getMimeDecoder().decode(value.trim())
        } catch (_: IllegalArgumentException) {
            return null
        }
    return flacPicturePayload(decoded)
}

internal fun flacPicturePayload(block: ByteArray): ByteArray? {
    var offset = 0

    fun readInt(): Int? {
        if (offset > block.size - 4) return null
        val value =
            ((block[offset].toInt() and 0xff) shl 24) or
                ((block[offset + 1].toInt() and 0xff) shl 16) or
                ((block[offset + 2].toInt() and 0xff) shl 8) or
                (block[offset + 3].toInt() and 0xff)
        offset += 4
        return value
    }

    fun skip(count: Int): Boolean {
        if (count < 0 || offset > block.size - count) return false
        offset += count
        return true
    }

    if (readInt() == null) return null
    val mimeLength = readInt() ?: return null
    if (!skip(mimeLength)) return null
    val descriptionLength = readInt() ?: return null
    if (!skip(descriptionLength)) return null
    repeat(4) { if (readInt() == null) return null }
    val dataLength = readInt() ?: return null
    if (dataLength <= 0 || offset > block.size - dataLength) return null
    return block.copyOfRange(offset, offset + dataLength)
}

private fun Byte.isPictureCommentByte(): Boolean {
    val char = toInt().toChar()
    return char.isLetterOrDigit() || char == '+' || char == '/' || char == '=' || char == '\n' || char == '\r' || char == ' '
}

private fun ByteArray.indexOfSlice(
    needle: ByteArray,
    from: Int,
): Int {
    if (needle.isEmpty() || from > size - needle.size) return -1
    for (index in from..size - needle.size) {
        if (needle.indices.all { offset -> this[index + offset] == needle[offset] }) return index
    }
    return -1
}
