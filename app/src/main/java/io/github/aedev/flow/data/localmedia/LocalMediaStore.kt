package io.github.aedev.flow.data.localmedia

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

private val AlbumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")
private const val ROTATED_90 = 90
private const val ROTATED_270 = 270
private const val MILLIS_PER_SECOND = 1_000L

/**
 * Reads the device's videos and songs from MediaStore. Every file is returned; what the library
 * hides (voice notes, short clips, hidden folders) is decided later from the viewer's settings.
 */
internal class LocalMediaStore(
    private val resolver: ContentResolver,
) {
    fun videos(): List<LocalMediaItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val columns =
            buildList {
                add(MediaStore.Video.Media._ID)
                add(MediaStore.Video.Media.TITLE)
                add(MediaStore.Video.Media.DISPLAY_NAME)
                add(MediaStore.Video.Media.DURATION)
                add(MediaStore.Video.Media.SIZE)
                add(MediaStore.Video.Media.DATE_ADDED)
                add(MediaStore.Video.Media.WIDTH)
                add(MediaStore.Video.Media.HEIGHT)
                add(MediaStore.Video.Media.MIME_TYPE)
                add(MediaStore.Video.Media.BUCKET_ID)
                add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                addAll(pathColumns())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Video.Media.ORIENTATION)
            }
        return query(collection, columns, selection = null) { cursor ->
            val id = cursor.long(MediaStore.Video.Media._ID)
            val size = cursor.long(MediaStore.Video.Media.SIZE)
            if (size <= 0L) return@query null
            val rotated = cursor.intOrNull(MediaStore.Video.Media.ORIENTATION).let { it == ROTATED_90 || it == ROTATED_270 }
            val width = cursor.int(MediaStore.Video.Media.WIDTH)
            val height = cursor.int(MediaStore.Video.Media.HEIGHT)
            LocalMediaItem(
                id = id,
                isVideo = true,
                contentUri = ContentUris.withAppendedId(collection, id).toString(),
                title = cursor.title(MediaStore.Video.Media.TITLE, MediaStore.Video.Media.DISPLAY_NAME) ?: return@query null,
                durationMs = cursor.long(MediaStore.Video.Media.DURATION),
                sizeBytes = size,
                dateAddedMs = cursor.long(MediaStore.Video.Media.DATE_ADDED) * MILLIS_PER_SECOND,
                width = if (rotated) height else width,
                height = if (rotated) width else height,
                mimeType = cursor.string(MediaStore.Video.Media.MIME_TYPE).orEmpty(),
                folderId = cursor.string(MediaStore.Video.Media.BUCKET_ID).orEmpty(),
                folderName = cursor.string(MediaStore.Video.Media.BUCKET_DISPLAY_NAME).orEmpty(),
                path = cursor.folderPath(),
            )
        }
    }

    fun music(): List<LocalMediaItem> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val columns =
            buildList {
                add(MediaStore.Audio.Media._ID)
                add(MediaStore.Audio.Media.TITLE)
                add(MediaStore.Audio.Media.DISPLAY_NAME)
                add(MediaStore.Audio.Media.ARTIST)
                add(MediaStore.Audio.Media.ALBUM)
                add(MediaStore.Audio.Media.ALBUM_ID)
                add(MediaStore.Audio.Media.DURATION)
                add(MediaStore.Audio.Media.SIZE)
                add(MediaStore.Audio.Media.DATE_ADDED)
                add(MediaStore.Audio.Media.MIME_TYPE)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Audio.Media.BUCKET_ID)
                    add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                }
                addAll(pathColumns())
            }
        val selection =
            buildList {
                add("${MediaStore.Audio.Media.IS_MUSIC} != 0")
                add("${MediaStore.Audio.Media.IS_NOTIFICATION} = 0")
                add("${MediaStore.Audio.Media.IS_ALARM} = 0")
                add("${MediaStore.Audio.Media.IS_RINGTONE} = 0")
                add("${MediaStore.Audio.Media.IS_PODCAST} = 0")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) add("${MediaStore.Audio.Media.IS_RECORDING} = 0")
            }.joinToString(" AND ")
        return query(collection, columns, selection) { cursor ->
            val id = cursor.long(MediaStore.Audio.Media._ID)
            val size = cursor.long(MediaStore.Audio.Media.SIZE)
            if (size <= 0L) return@query null
            val albumId = cursor.long(MediaStore.Audio.Media.ALBUM_ID)
            LocalMediaItem(
                id = id,
                isVideo = false,
                contentUri = ContentUris.withAppendedId(collection, id).toString(),
                title = cursor.title(MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DISPLAY_NAME) ?: return@query null,
                durationMs = cursor.long(MediaStore.Audio.Media.DURATION),
                sizeBytes = size,
                dateAddedMs = cursor.long(MediaStore.Audio.Media.DATE_ADDED) * MILLIS_PER_SECOND,
                mimeType = cursor.string(MediaStore.Audio.Media.MIME_TYPE).orEmpty(),
                folderId = cursor.stringOrNull(MediaStore.Audio.Media.BUCKET_ID).orEmpty(),
                folderName = cursor.stringOrNull(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME).orEmpty(),
                path = cursor.folderPath(),
                artist =
                    cursor
                        .string(
                            MediaStore.Audio.Media.ARTIST,
                        )?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
                        .orEmpty(),
                album =
                    cursor
                        .string(
                            MediaStore.Audio.Media.ALBUM,
                        )?.takeUnless { it.isBlank() || it == MediaStore.UNKNOWN_STRING }
                        .orEmpty(),
                artworkUri = songArtwork(collection, id, albumId),
            )
        }
    }

    /**
     * The song's own URI on Android 10 and later, where Coil loads it through `loadThumbnail`; the
     * album-art path, deprecated there, only before that.
     */
    private fun songArtwork(
        collection: Uri,
        id: Long,
        albumId: Long,
    ): String? =
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ContentUris.withAppendedId(collection, id).toString()
            albumId > 0 -> ContentUris.withAppendedId(AlbumArtBase, albumId).toString()
            else -> null
        }

    private fun pathColumns(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            listOf(MediaStore.MediaColumns.RELATIVE_PATH)
        } else {
            @Suppress("DEPRECATION")
            listOf(MediaStore.MediaColumns.DATA)
        }

    private fun <T> query(
        collection: Uri,
        columns: List<String>,
        selection: String?,
        read: (Cursor) -> T?,
    ): List<T> =
        resolver
            .query(collection, columns.toTypedArray(), selection, null, "${MediaStore.MediaColumns.DATE_ADDED} DESC")
            ?.use { cursor -> generateSequence { if (cursor.moveToNext()) cursor else null }.mapNotNull(read).toList() }
            .orEmpty()
}

private fun Cursor.long(column: String): Long = getColumnIndex(column).takeIf { it >= 0 }?.let(::getLong) ?: 0L

private fun Cursor.int(column: String): Int = getColumnIndex(column).takeIf { it >= 0 }?.let(::getInt) ?: 0

private fun Cursor.intOrNull(column: String): Int? = getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getInt)

private fun Cursor.string(column: String): String? = stringOrNull(column)

private fun Cursor.stringOrNull(column: String): String? = getColumnIndex(column).takeIf { it >= 0 }?.let(::getString)

private fun Cursor.title(
    titleColumn: String,
    nameColumn: String,
): String? = string(titleColumn)?.takeIf(String::isNotBlank) ?: string(nameColumn)?.substringBeforeLast('.')?.takeIf(String::isNotBlank)

@Suppress("DEPRECATION")
private fun Cursor.folderPath(): String =
    stringOrNull(MediaStore.MediaColumns.RELATIVE_PATH)
        ?: stringOrNull(MediaStore.MediaColumns.DATA)?.substringBeforeLast('/')?.plus('/')
        ?: ""
