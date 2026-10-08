package io.github.aedev.flow.data.localmedia

import kotlinx.serialization.Serializable

/** The title, artist and album a file's container carries, exactly as written; null where it has none. */
@Serializable
data class EmbeddedText(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
) {
    val isEmpty: Boolean get() = title == null && artist == null && album == null
}

/**
 * The item as its own file describes it. MediaStore's copy of these fields comes from the
 * device's metadata reader, which some vendors' builds damage ("Snälla" stored as "Sn??lla"), so
 * whatever the file itself carries wins; fields it lacks keep MediaStore's value. A [titleless]
 * file is named by its file name: MediaStore copies the name into the title when it indexes the
 * file and never updates it on a rename (#1214).
 */
internal fun LocalMediaItem.withEmbeddedText(
    text: EmbeddedText,
    titleless: Boolean = false,
): LocalMediaItem {
    val untagged = if (titleless) fileTitle ?: title else title
    val updated = copy(title = text.title ?: untagged, artist = text.artist ?: artist, album = text.album ?: album)
    return if (updated == this) this else updated
}

/** The file's name without its extension, the title MediaStore gives a file that carries none. */
internal val LocalMediaItem.fileTitle: String? get() = fileNameTitle(fileName)

internal fun fileNameTitle(fileName: String?): String? = fileName?.substringBeforeLast('.')?.takeIf(String::isNotBlank)

/** Whether MediaStore's text looks damaged, so the file is read before the list is first shown. */
internal fun LocalMediaItem.looksDamaged(): Boolean = listOf(title, artist, album).any { field -> field.any { it == '?' || it == '�' } }

/** Changes whenever the file is rewritten, so a stored read is never applied to newer contents. */
internal val LocalMediaItem.fileStamp: String get() = "$modifiedMs:$sizeBytes"

/** A field's text with the padding writers leave around it removed, or null when nothing is left. */
internal fun embeddedField(value: CharSequence?): String? =
    value
        ?.toString()
        ?.trimEnd('\u0000')
        ?.trim()
        ?.ifEmpty { null }
