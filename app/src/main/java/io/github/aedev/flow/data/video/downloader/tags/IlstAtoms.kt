package io.github.aedev.flow.data.video.downloader.tags

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/** Builds the iTunes-style `ilst` atom list (the only MP4 tag form Android's MediaStore reads). */
internal object IlstAtoms {
    const val ENCODER_NAME = "Flow"
    const val MAX_COVER_BYTES = 1_000_000

    private const val DATA_TYPE_IMPLICIT = 0
    private const val DATA_TYPE_UTF8 = 1
    private const val DATA_TYPE_JPEG = 13
    private const val DATA_TYPE_PNG = 14
    private const val COPYRIGHT_SIGN = 0xA9.toByte()

    fun build(
        tags: DownloadTags,
        cover: ByteArray?,
    ): ByteArray {
        val atoms = ByteArrayOutputStream()
        atoms.writeText(copyrightType("nam"), tags.title)
        atoms.writeText(copyrightType("ART"), tags.displayArtist())
        atoms.writeText(copyrightType("alb"), tags.album)
        atoms.writeText(type("aART"), tags.albumArtist)
        tags.trackNumber?.takeIf { it > 0 }?.let { atoms.write(trackNumberAtom(it, tags.trackTotal ?: 0)) }
        atoms.writeText(copyrightType("day"), tags.releaseDate)
        atoms.writeText(copyrightType("cmt"), tags.sourceUrl)
        atoms.writeText(type("desc"), tags.description?.let { FlowTagFields.truncate(it, FlowTagFields.DESCRIPTION_MAX_CHARS) })
        atoms.writeText(copyrightType("lyr"), tags.lyrics)
        atoms.writeText(copyrightType("too"), ENCODER_NAME)
        cover?.let(::coverAtom)?.let(atoms::write)
        FlowTagFields.encode(tags).forEach { (name, value) -> atoms.write(freeformAtom(name, value)) }
        return box(type("ilst"), atoms.toByteArray())
    }

    /** JPEG → 13, PNG → 14, anything else (or an oversized image) → not embedded. */
    fun coverDataType(bytes: ByteArray): Int? =
        when {
            bytes.size > MAX_COVER_BYTES -> null
            bytes.startsWith(0xFF, 0xD8, 0xFF) -> DATA_TYPE_JPEG
            bytes.startsWith(0x89, 0x50, 0x4E, 0x47) -> DATA_TYPE_PNG
            else -> null
        }

    fun box(
        type: ByteArray,
        payload: ByteArray,
    ): ByteArray =
        ByteBuffer
            .allocate(Mp4Boxes.HEADER_SIZE + payload.size)
            .putInt(Mp4Boxes.HEADER_SIZE + payload.size)
            .put(type)
            .put(payload)
            .array()

    fun type(name: String): ByteArray = name.toByteArray(Charsets.ISO_8859_1).also { require(it.size == 4) }

    private fun copyrightType(name: String): ByteArray = byteArrayOf(COPYRIGHT_SIGN) + name.toByteArray(Charsets.ISO_8859_1)

    private fun ByteArrayOutputStream.writeText(
        type: ByteArray,
        value: String?,
    ) {
        if (value.isNullOrEmpty()) return
        write(box(type, dataAtom(DATA_TYPE_UTF8, value.toByteArray(Charsets.UTF_8))))
    }

    private fun dataAtom(
        dataType: Int,
        value: ByteArray,
    ): ByteArray =
        box(
            type("data"),
            ByteBuffer
                .allocate(8 + value.size)
                .putInt(dataType)
                .putInt(0)
                .put(value)
                .array(),
        )

    private fun trackNumberAtom(
        number: Int,
        total: Int,
    ): ByteArray {
        val payload =
            ByteBuffer
                .allocate(8)
                .putShort(0)
                .putShort(number.coerceAtMost(0xFFFF).toShort())
                .putShort(total.coerceIn(0, 0xFFFF).toShort())
                .putShort(0)
                .array()
        return box(type("trkn"), dataAtom(DATA_TYPE_IMPLICIT, payload))
    }

    private fun coverAtom(bytes: ByteArray): ByteArray? {
        val dataType = coverDataType(bytes) ?: return null
        return box(type("covr"), dataAtom(dataType, bytes))
    }

    private fun freeformAtom(
        name: String,
        value: String,
    ): ByteArray =
        box(
            type("----"),
            fullBox(type("mean"), FlowTagFields.NAMESPACE.toByteArray(Charsets.UTF_8)) +
                fullBox(type("name"), name.toByteArray(Charsets.UTF_8)) +
                dataAtom(DATA_TYPE_UTF8, value.toByteArray(Charsets.UTF_8)),
        )

    private fun fullBox(
        type: ByteArray,
        payload: ByteArray,
    ): ByteArray = box(type, ByteArray(4) + payload)

    private fun ByteArray.startsWith(vararg prefix: Int): Boolean =
        size >= prefix.size && prefix.indices.all { this[it] == prefix[it].toByte() }
}
