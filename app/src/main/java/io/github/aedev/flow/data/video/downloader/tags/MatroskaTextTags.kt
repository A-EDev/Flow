package io.github.aedev.flow.data.video.downloader.tags

import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/**
 * Reads a Matroska or WebM file's own title (`Segment/Info/Title`) and the `SimpleTag`s of the
 * tags that describe the whole file. Hand-rolled because Media3 1.11's `MatroskaExtractor` has no
 * element ids for `Title` or `Tags` and its EBML reader is not public, so Media3 drops both.
 */
internal object MatroskaTextTags {
    const val TITLE = "TITLE"
    const val ARTIST = "ARTIST"
    const val ALBUM = "ALBUM"

    /**
     * The title and file-wide tags by upper-case tag name, the title first, or null when [channel]
     * holds no Matroska file. Only the header area is read; `Tags` written after the clusters are
     * reached through the `SeekHead`.
     */
    fun read(channel: FileChannel): Map<String, String>? {
        val fileSize = channel.size()
        val ebml = element(channel, 0, fileSize)?.takeIf { it.id == EBML } ?: return null
        val segment = element(channel, ebml.end, fileSize)?.takeIf { it.id == SEGMENT } ?: return emptyMap()
        val segmentEnd = if (segment.unknownSize) fileSize else minOf(segment.end, fileSize)
        val found = mutableMapOf<Long, Element>()
        val seeks = mutableMapOf<Long, Long>()
        var position = segment.dataStart
        var scanned = 0
        while (position < segmentEnd && scanned++ < MAX_TOP_LEVEL_ELEMENTS) {
            val child = element(channel, position, segmentEnd) ?: break
            if (child.id == CLUSTER) break
            if (child.id == SEEK_HEAD) body(channel, child, MAX_SEEK_HEAD_BYTES)?.let { seeks.putAll(seekPositions(it)) }
            if (child.id == INFO || child.id == TAGS) found.putIfAbsent(child.id, child)
            if (child.unknownSize) break
            position = child.end
        }
        for (id in listOf(INFO, TAGS)) {
            if (id in found) continue
            val target = seeks[id]?.let { segment.dataStart + it } ?: continue
            element(channel, target, segmentEnd)?.takeIf { it.id == id }?.let { found[id] = it }
        }
        val texts = linkedMapOf<String, String>()
        found[INFO]?.let { body(channel, it, MAX_INFO_BYTES) }?.let { info ->
            children(info).firstOrNull { it.id == INFO_TITLE }?.let { text(info, it) }?.let { texts[TITLE] = it }
        }
        found[TAGS]?.let { body(channel, it, MAX_TAGS_BYTES) }?.let { tags ->
            globalTags(tags).forEach { (name, value) -> texts.putIfAbsent(name, value) }
        }
        return texts
    }

    /** The tags that target no single track, edition, chapter or attachment, the most specific level first. */
    private fun globalTags(tags: ByteArray): List<Pair<String, String>> =
        children(tags)
            .filter { it.id == TAG }
            .mapNotNull { tag ->
                val fields = children(tags, tag)
                val targets = fields.firstOrNull { it.id == TARGETS }?.let { children(tags, it) }.orEmpty()
                if (targets.any { it.id in TargetUids && unsigned(tags, it) != 0L }) return@mapNotNull null
                val level = targets.firstOrNull { it.id == TARGET_TYPE_VALUE }?.let { unsigned(tags, it) } ?: DEFAULT_TARGET_LEVEL
                level to
                    fields.filter { it.id == SIMPLE_TAG }.mapNotNull { simple ->
                        val parts = children(tags, simple)
                        val name = parts.firstOrNull { it.id == TAG_NAME }?.let { text(tags, it) } ?: return@mapNotNull null
                        val value = parts.firstOrNull { it.id == TAG_STRING }?.let { text(tags, it) } ?: return@mapNotNull null
                        name.uppercase() to value
                    }
            }.sortedBy { it.first }
            .flatMap { it.second }

    private fun seekPositions(seekHead: ByteArray): Map<Long, Long> =
        children(seekHead)
            .filter { it.id == SEEK }
            .mapNotNull { seek ->
                val parts = children(seekHead, seek)
                val id = parts.firstOrNull { it.id == SEEK_ID }?.let { unsigned(seekHead, it) } ?: return@mapNotNull null
                val position = parts.firstOrNull { it.id == SEEK_POSITION }?.let { unsigned(seekHead, it) } ?: return@mapNotNull null
                id to position
            }.toMap()

    /** One element: its id with the length marker kept, as Matroska spells ids, and where its data lies. */
    private data class Element(
        val id: Long,
        val dataStart: Long,
        val dataSize: Long,
        val unknownSize: Boolean,
    ) {
        val end: Long get() = dataStart + dataSize
    }

    private fun element(
        channel: FileChannel,
        position: Long,
        limit: Long,
    ): Element? {
        val available = minOf(MAX_HEADER_BYTES.toLong(), limit - position).toInt()
        if (available < 2) return null
        val buffer = ByteBuffer.allocate(available)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, position + buffer.position()) < 0) break
        }
        return header(buffer.array(), 0, buffer.position(), position)
    }

    private fun header(
        bytes: ByteArray,
        offset: Int,
        available: Int,
        absoluteStart: Long,
    ): Element? {
        val idLength = vintLength(bytes, offset, available)?.takeIf { it <= MAX_ID_BYTES } ?: return null
        val id = number(bytes, offset, idLength, keepMarker = true)
        val sizeLength = vintLength(bytes, offset + idLength, available - idLength) ?: return null
        val size = number(bytes, offset + idLength, sizeLength, keepMarker = false)
        val unknownSize = size == (1L shl (VINT_VALUE_BITS * sizeLength)) - 1
        return Element(id, absoluteStart + idLength + sizeLength, if (unknownSize) 0L else size, unknownSize)
    }

    private fun vintLength(
        bytes: ByteArray,
        offset: Int,
        available: Int,
    ): Int? {
        if (available < 1) return null
        val length = Integer.numberOfLeadingZeros(bytes[offset].toInt() and BYTE_MASK) - LEADING_ZEROS_OF_A_BYTE + 1
        return length.takeIf { it in 1..MAX_VINT_BYTES && it <= available }
    }

    private fun number(
        bytes: ByteArray,
        offset: Int,
        length: Int,
        keepMarker: Boolean,
    ): Long {
        var value = (bytes[offset].toInt() and BYTE_MASK).let { if (keepMarker) it else it and (BYTE_MASK shr length) }.toLong()
        for (index in 1 until length) value = (value shl Byte.SIZE_BITS) or (bytes[offset + index].toLong() and BYTE_MASK.toLong())
        return value
    }

    private fun body(
        channel: FileChannel,
        element: Element,
        maxBytes: Int,
    ): ByteArray? {
        if (element.unknownSize || element.dataSize > maxBytes) return null
        val buffer = ByteBuffer.allocate(element.dataSize.toInt())
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, element.dataStart + buffer.position()) < 0) return null
        }
        return buffer.array()
    }

    private fun children(
        bytes: ByteArray,
        parent: Element? = null,
    ): List<Element> {
        val from = parent?.dataStart?.toInt() ?: 0
        val to = parent?.end?.toInt() ?: bytes.size
        val elements = mutableListOf<Element>()
        var position = from
        while (position < to) {
            val child = header(bytes, position, to - position, position.toLong()) ?: break
            if (child.unknownSize || child.end > to) break
            elements += child
            position = child.end.toInt()
        }
        return elements
    }

    private fun unsigned(
        bytes: ByteArray,
        element: Element,
    ): Long? {
        if (element.dataSize !in 1..Long.SIZE_BYTES) return null
        var value = 0L
        for (index in 0 until element.dataSize.toInt()) {
            value = (value shl Byte.SIZE_BITS) or (bytes[element.dataStart.toInt() + index].toLong() and BYTE_MASK.toLong())
        }
        return value
    }

    // Writers pad strings with NULs (ffmpeg's DURATION tags end in one).
    private fun text(
        bytes: ByteArray,
        element: Element,
    ): String? =
        String(bytes, element.dataStart.toInt(), element.dataSize.toInt(), Charsets.UTF_8).trimEnd('\u0000').trim().ifEmpty { null }

    private const val EBML = 0x1A45DFA3L
    private const val SEGMENT = 0x18538067L
    private const val SEEK_HEAD = 0x114D9B74L
    private const val SEEK = 0x4DBBL
    private const val SEEK_ID = 0x53ABL
    private const val SEEK_POSITION = 0x53ACL
    private const val INFO = 0x1549A966L
    private const val INFO_TITLE = 0x7BA9L
    private const val TAGS = 0x1254C367L
    private const val TAG = 0x7373L
    private const val TARGETS = 0x63C0L
    private const val TARGET_TYPE_VALUE = 0x68CAL
    private const val SIMPLE_TAG = 0x67C8L
    private const val TAG_NAME = 0x45A3L
    private const val TAG_STRING = 0x4487L
    private const val CLUSTER = 0x1F43B675L
    private val TargetUids = setOf(0x63C5L, 0x63C9L, 0x63C4L, 0x63C6L)
    private const val DEFAULT_TARGET_LEVEL = 50L
    private const val MAX_TOP_LEVEL_ELEMENTS = 32
    private const val MAX_SEEK_HEAD_BYTES = 64 * 1024
    private const val MAX_INFO_BYTES = 64 * 1024
    private const val MAX_TAGS_BYTES = 1024 * 1024
    private const val MAX_HEADER_BYTES = 12
    private const val MAX_ID_BYTES = 4
    private const val MAX_VINT_BYTES = 8
    private const val VINT_VALUE_BITS = 7
    private const val BYTE_MASK = 0xFF
    private const val LEADING_ZEROS_OF_A_BYTE = 24
}
