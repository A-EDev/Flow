package io.github.aedev.flow.data.video.downloader.tags

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** ISO BMFF box headers, read from a file (top level) or from an in-memory container box. */
internal object Mp4Boxes {
    const val HEADER_SIZE = 8
    const val LARGE_HEADER_SIZE = 16
    private const val FULL_BOX_FIELDS = 4
    private const val HDLR = "hdlr"

    /**
     * One box: [start] is the offset of its header, [size] includes the header. [extendsToEnd]
     * marks a box whose size field is 0, meaning "runs to the end of its container".
     */
    data class Span(
        val type: String,
        val start: Long,
        val headerSize: Int,
        val size: Long,
        val extendsToEnd: Boolean,
    ) {
        val end: Long get() = start + size
        val payloadStart: Long get() = start + headerSize
    }

    fun topLevel(channel: FileChannel): List<Span> {
        val fileSize = channel.size()
        val spans = mutableListOf<Span>()
        var position = 0L
        val header = ByteBuffer.allocate(LARGE_HEADER_SIZE)
        while (position < fileSize) {
            if (fileSize - position < HEADER_SIZE) throw IOException("Trailing bytes after the last box at $position")
            header.clear()
            header.limit(minOf(LARGE_HEADER_SIZE.toLong(), fileSize - position).toInt())
            while (header.hasRemaining()) {
                if (channel.read(header, position + header.position()) < 0) break
            }
            val span = parseHeader(header.array(), 0, header.position(), position, fileSize)
            spans += span
            position = span.end
        }
        return spans
    }

    fun children(
        bytes: ByteArray,
        from: Int,
        to: Int,
    ): List<Span> {
        val spans = mutableListOf<Span>()
        var position = from
        while (position < to) {
            if (to - position < HEADER_SIZE) {
                if (bytes.copyOfRange(position, to).all { it.toInt() == 0 }) break
                throw IOException("Truncated child box at $position")
            }
            val span = parseHeader(bytes, position, to - position, position.toLong(), to.toLong())
            spans += span
            position = span.end.toInt()
        }
        return spans
    }

    /** The header size of a box held in memory from its own first byte. */
    fun headerSize(box: ByteArray): Int = if (ByteBuffer.wrap(box).int == 1) LARGE_HEADER_SIZE else HEADER_SIZE

    /** Where a `meta` box's children start: ISO `meta` is a FullBox, QuickTime's is not; both put `hdlr` first. */
    fun metaChildrenStart(meta: ByteArray): Int {
        val header = headerSize(meta)
        if (meta.size < header + HEADER_SIZE) return header
        val firstType = String(meta, header + 4, 4, Charsets.ISO_8859_1)
        return if (firstType == HDLR) header else header + FULL_BOX_FIELDS
    }

    fun readFully(
        channel: FileChannel,
        position: Long,
        length: Int,
    ): ByteArray {
        val buffer = ByteBuffer.allocate(length)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, position + buffer.position()) < 0) throw IOException("Unexpected end of file")
        }
        return buffer.array()
    }

    fun childPayload(
        bytes: ByteArray,
        span: Span,
    ): ByteArray = bytes.copyOfRange(span.payloadStart.toInt(), span.end.toInt())

    fun slice(
        bytes: ByteArray,
        span: Span,
    ): ByteArray = bytes.copyOfRange(span.start.toInt(), span.end.toInt())

    private fun parseHeader(
        bytes: ByteArray,
        offset: Int,
        available: Int,
        absoluteStart: Long,
        containerEnd: Long,
    ): Span {
        val buffer = ByteBuffer.wrap(bytes, offset, available)
        val size32 = buffer.int.toLong() and 0xFFFFFFFFL
        val type = String(bytes, offset + 4, 4, Charsets.ISO_8859_1)
        val span =
            when (size32) {
                0L -> {
                    Span(type, absoluteStart, HEADER_SIZE, containerEnd - absoluteStart, extendsToEnd = true)
                }

                1L -> {
                    if (available < LARGE_HEADER_SIZE) throw IOException("Truncated largesize header for '$type'")
                    buffer.position(offset + HEADER_SIZE)
                    Span(type, absoluteStart, LARGE_HEADER_SIZE, buffer.long, extendsToEnd = false)
                }

                else -> {
                    Span(type, absoluteStart, HEADER_SIZE, size32, extendsToEnd = false)
                }
            }
        if (span.size < span.headerSize || span.end > containerEnd) {
            throw IOException("Box '$type' at $absoluteStart has invalid size ${span.size}")
        }
        return span
    }
}
