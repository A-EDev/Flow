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
 * whatever the file itself carries wins; fields it lacks keep MediaStore's value.
 */
internal fun LocalMediaItem.withEmbeddedText(text: EmbeddedText): LocalMediaItem {
    val updated = copy(title = text.title ?: title, artist = text.artist ?: artist, album = text.album ?: album)
    return if (updated == this) this else updated
}

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
