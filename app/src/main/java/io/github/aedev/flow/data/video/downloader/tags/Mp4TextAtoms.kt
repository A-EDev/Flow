package io.github.aedev.flow.data.video.downloader.tags

import java.nio.channels.FileChannel

/**
 * Reads text atoms from an MP4's `moov/udta/meta/ilst`. Hand-rolled only for the atoms Media3 1.11
 * skips as unknown (`MetadataUtil.parseIlstElement` has no branch for `desc` or `ldes`, which is
 * where yt-dlp and Flow itself write a video's description); everything else goes through Media3.
 */
internal object Mp4TextAtoms {
    const val DESCRIPTION = "desc"
    const val LONG_DESCRIPTION = "ldes"

    /** The UTF-8 text of each of [types] the file carries; empty when it is no MP4 or has none. */
    fun read(
        channel: FileChannel,
        types: Set<String>,
    ): Map<String, String> {
        val moovSpan = Mp4Boxes.topLevel(channel).firstOrNull { it.type == MOOV } ?: return emptyMap()
        if (moovSpan.size > MAX_MOOV_BYTES) return emptyMap()
        val moov = Mp4Boxes.readFully(channel, moovSpan.start, moovSpan.size.toInt())
        val udta = moov.child(Mp4Boxes.headerSize(moov), UDTA) ?: return emptyMap()
        val texts = linkedMapOf<String, String>()
        Mp4Boxes
            .children(udta, Mp4Boxes.headerSize(udta), udta.size)
            .filter { it.type == META }
            .forEach { metaSpan ->
                val meta = Mp4Boxes.slice(udta, metaSpan)
                val ilst = meta.child(Mp4Boxes.metaChildrenStart(meta), ILST) ?: return@forEach
                Mp4Boxes.children(ilst, Mp4Boxes.headerSize(ilst), ilst.size).forEach { entrySpan ->
                    if (entrySpan.type !in types) return@forEach
                    textOf(Mp4Boxes.slice(ilst, entrySpan))?.let { texts.putIfAbsent(entrySpan.type, it) }
                }
            }
        return texts
    }

    private fun textOf(entry: ByteArray): String? {
        val data = entry.child(Mp4Boxes.headerSize(entry), DATA) ?: return null
        val payload = data.copyOfRange(Mp4Boxes.headerSize(data), data.size)
        if (payload.size <= DATA_PREFIX_BYTES || payload[DATA_TYPE_BYTE].toInt() != DATA_TYPE_UTF8) return null
        return String(payload, DATA_PREFIX_BYTES, payload.size - DATA_PREFIX_BYTES, Charsets.UTF_8).trim().ifBlank { null }
    }

    private fun ByteArray.child(
        from: Int,
        type: String,
    ): ByteArray? = Mp4Boxes.children(this, from, size).firstOrNull { it.type == type }?.let { Mp4Boxes.slice(this, it) }

    private const val MOOV = "moov"
    private const val UDTA = "udta"
    private const val META = "meta"
    private const val ILST = "ilst"
    private const val DATA = "data"
    private const val DATA_PREFIX_BYTES = 8
    private const val DATA_TYPE_BYTE = 3
    private const val DATA_TYPE_UTF8 = 1
    private const val MAX_MOOV_BYTES = 64L * 1024 * 1024
}
