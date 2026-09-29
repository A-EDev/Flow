package io.github.aedev.flow.data.video.storage

/**
 * The names downloads and their folders get on disk: readable titles, legal on every filesystem a
 * phone writes to (ext4, f2fs, FUSE, FAT32 and exFAT cards, document providers), and never longer
 * than a name may be. Pure, so every rule is unit tested.
 */
object DownloadNaming {
    /** NAME_MAX on every Android filesystem, counted in UTF-8 bytes rather than characters. */
    const val MAX_NAME_BYTES = 255

    // Room for a " (999)" collision suffix and the longest extension a download is given.
    private const val SUFFIX_RESERVE_BYTES = 16

    private val ILLEGAL = Regex("""[\\/:*?"<>|\x00-\x1F\x7F]""")
    private val SPACES = Regex("""\s+""")

    /** [raw] with the characters no filesystem accepts removed; [fallback] when nothing is left. */
    fun sanitize(
        raw: String,
        fallback: String,
    ): String {
        val cleaned =
            raw
                .replace(ILLEGAL, " ")
                .replace(SPACES, " ")
                .trim()
                .trimEnd('.', ' ')
                .trimStart('.', ' ')
        val bounded = truncateToBytes(cleaned, MAX_NAME_BYTES - SUFFIX_RESERVE_BYTES).trimEnd('.', ' ')
        return bounded.ifEmpty { truncateToBytes(sanitizeFallback(fallback), MAX_NAME_BYTES - SUFFIX_RESERVE_BYTES) }
    }

    /**
     * The file name for one download. Inside an album the track number leads, zero-padded to the
     * width of [trackTotal], so the folder sorts in album order.
     */
    fun fileName(
        title: String,
        fallback: String,
        extension: String,
        trackNumber: Int? = null,
        trackTotal: Int? = null,
    ): String {
        val base = sanitize(title, fallback)
        val numbered =
            if (trackNumber != null && trackNumber > 0) {
                val width =
                    (trackTotal ?: trackNumber)
                        .coerceAtLeast(trackNumber)
                        .toString()
                        .length
                        .coerceAtLeast(2)
                "${trackNumber.toString().padStart(width, '0')} - $base"
            } else {
                base
            }
        return "${truncateToBytes(numbered, MAX_NAME_BYTES - SUFFIX_RESERVE_BYTES)}.${extension.trimStart('.')}"
    }

    /** A collection's folder: "Artist - Album" for an album, the playlist's own title otherwise. */
    fun folderName(
        title: String,
        author: String?,
        isAlbum: Boolean,
        fallback: String,
    ): String {
        val named = if (isAlbum && !author.isNullOrBlank()) "$author - $title" else title
        return sanitize(named, fallback)
    }

    /**
     * [name] itself when nothing holds it yet, otherwise "Name (2).ext", "Name (3).ext" and so on.
     * [isTaken] is asked about each candidate, so a caller can check the disk and the database. A
     * folder name passes [hasExtension] false, so a dot in its title is not read as one.
     */
    inline fun unique(
        name: String,
        hasExtension: Boolean = true,
        isTaken: (String) -> Boolean,
    ): String {
        if (!isTaken(name)) return name
        val dot = name.lastIndexOf('.').takeIf { hasExtension && it > 0 }
        val stem = dot?.let { name.substring(0, it) } ?: name
        val extension = dot?.let { name.substring(it) }.orEmpty()
        var index = 2
        while (true) {
            val candidate = "$stem ($index)$extension"
            if (!isTaken(candidate)) return candidate
            index++
        }
    }

    /** The longest prefix of [text] that fits in [maxBytes] of UTF-8, never splitting a character. */
    fun truncateToBytes(
        text: String,
        maxBytes: Int,
    ): String {
        if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
        val builder = StringBuilder()
        var used = 0
        var index = 0
        while (index < text.length) {
            val codePoint = text.codePointAt(index)
            val chars = Character.charCount(codePoint)
            val bytes = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (used + bytes > maxBytes) break
            builder.appendCodePoint(codePoint)
            used += bytes
            index += chars
        }
        return builder.toString().trimEnd()
    }

    private fun sanitizeFallback(fallback: String): String = fallback.replace(ILLEGAL, "_").ifBlank { "download" }
}
