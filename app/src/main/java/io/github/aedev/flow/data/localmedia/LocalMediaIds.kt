package io.github.aedev.flow.data.localmedia

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

/**
 * Ids for files on the device. A `local_<mediaStoreId>` id never names a YouTube video, so every
 * online lookup, sync write and engine signal must skip it.
 */
object LocalMediaIds {
    const val PREFIX = "local_"

    fun isLocal(id: String?): Boolean = id?.startsWith(PREFIX) == true

    fun of(mediaStoreId: Long): String = "$PREFIX$mediaStoreId"

    fun mediaStoreId(id: String): Long? = if (isLocal(id)) id.removePrefix(PREFIX).toLongOrNull() else null

    fun videoUri(id: String): Uri? = mediaStoreId(id)?.let { ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it) }

    fun audioUri(id: String): Uri? = mediaStoreId(id)?.let { ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it) }
}
