package io.github.aedev.flow.data.video.storage

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/**
 * The system prompt that deletes [paths] on the viewer's say-so. Files an earlier install saved
 * belong to that install as far as Android is concerned, so Flow may read them but not delete them
 * itself. Null before Android 11, or when none of the files is in the media index.
 */
fun Context.deleteRequestFor(paths: List<String>): IntentSender? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
    val uris = paths.mapNotNull(::mediaUriOf)
    if (uris.isEmpty()) return null
    return runCatching { MediaStore.createDeleteRequest(contentResolver, uris).intentSender }.getOrNull()
}

@Suppress("DEPRECATION")
private fun Context.mediaUriOf(path: String): Uri? =
    listOf(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI).firstNotNullOfOrNull { collection ->
        runCatching {
            contentResolver
                .query(collection, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.DATA} = ?", arrayOf(path), null)
                ?.use { cursor -> if (cursor.moveToFirst()) ContentUris.withAppendedId(collection, cursor.getLong(0)) else null }
        }.getOrNull()
    }
