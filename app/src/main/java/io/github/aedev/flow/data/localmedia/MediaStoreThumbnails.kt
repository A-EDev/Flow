package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size

private const val MEDIA_AUTHORITY = "media"

/**
 * Artwork for a file on the device: the supported `ContentResolver.loadThumbnail` on Android 10
 * and later (a video frame, or a song's embedded or album art), and the file's own embedded
 * picture or a frame read by [MediaMetadataRetriever] before that.
 */
object MediaStoreThumbnails {
    fun isMediaStoreUri(uri: Uri): Boolean = uri.scheme == "content" && uri.authority == MEDIA_AUTHORITY

    fun load(
        context: Context,
        uri: Uri,
        sizePx: Int,
    ): Bitmap? =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                retrieve(context, uri)
            }
        }.getOrNull()

    private fun retrieve(
        context: Context,
        uri: Uri,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } ?: retriever.frameAtTime
        } finally {
            retriever.release()
        }
    }
}
