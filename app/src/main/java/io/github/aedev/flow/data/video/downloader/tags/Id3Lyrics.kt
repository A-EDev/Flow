package io.github.aedev.flow.data.video.downloader.tags

/**
 * The text of an ID3 `USLT` frame. Media3 1.11's `Id3Decoder` has no decoder for it and hands the
 * frame back as raw bytes: an encoding byte, a language code, a descriptor ending in a null, then
 * the lyrics.
 */
internal object Id3Lyrics {
    const val FRAME_ID = "USLT"

    fun decode(frame: ByteArray): String? {
        if (frame.size <= HEADER_BYTES) return null
        val (charset, unit) =
            when (frame[0].toInt()) {
                0 -> Charsets.ISO_8859_1 to 1
                1 -> Charsets.UTF_16 to 2
                2 -> Charsets.UTF_16BE to 2
                3 -> Charsets.UTF_8 to 1
                else -> return null
            }
        val textStart = descriptorEnd(frame, HEADER_BYTES, unit) ?: return null
        return String(frame, textStart, frame.size - textStart, charset)
            .trimEnd('\u0000')
            .trim()
            .ifBlank { null }
    }

    private fun descriptorEnd(
        frame: ByteArray,
        from: Int,
        unit: Int,
    ): Int? {
        var index = from
        while (index + unit <= frame.size) {
            if ((0 until unit).all { frame[index + it] == 0.toByte() }) return index + unit
            index += unit
        }
        return null
    }

    private const val HEADER_BYTES = 4
}
