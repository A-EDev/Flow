package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * The file path MediaStore records for [uri]. Deprecated for writing, but still the only way to
 * find the files that sit beside a media file, which scoped storage lets Flow read by path.
 */
@Suppress("DEPRECATION")
internal fun Context.mediaStoreDataPath(uri: Uri): String? =
    runCatching {
        contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

/** Up to [limit] + 1 bytes, so a caller can tell a file that is too large from one that fits. */
internal fun InputStream.readAtMost(limit: Int): ByteArray {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(BUFFER_BYTES)
    while (out.size() <= limit) {
        val read = read(buffer)
        if (read < 0) break
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

/**
 * The external storage provider's document id for [path]: `primary:Movies/Film` for the shared
 * storage, `1A2B-3C4D:Movies` for an SD card, or null for anywhere else.
 */
internal fun externalStorageDocumentId(path: String): String? {
    val parts = path.trimEnd('/').removePrefix("/storage/").split('/', limit = 3)
    if (!path.startsWith("/storage/") || parts.isEmpty()) return null
    return if (parts[0] == "emulated") {
        if (parts.size < 2 || parts[1] != "0") return null
        "primary:" + parts.getOrElse(2) { "" }
    } else {
        parts[0] + ":" + parts.drop(1).joinToString("/")
    }
}

private const val BUFFER_BYTES = 8 * 1024
