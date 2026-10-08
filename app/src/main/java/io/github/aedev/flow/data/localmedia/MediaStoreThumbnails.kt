package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size

private const val MEDIA_AUTHORITY = "media"
private const val AUDIO_PATH = "/audio/"

/**
 * Artwork for a file on the device. A song shows the cover embedded in its own file first: the
 * platform thumbnail for audio is its album's art, and MediaStore files untagged songs under their
 * folder's name as the album, so every such song in one artist's folder would show one picture
 * (#807). Otherwise the supported `ContentResolver.loadThumbnail` on Android 10 and later, and the
 * file's own embedded picture or a frame read by [MediaMetadataRetriever] before that.
 */
object MediaStoreThumbnails {
    fun isMediaStoreUri(uri: Uri): Boolean = uri.scheme == "content" && uri.authority == MEDIA_AUTHORITY

    fun load(
        context: Context,
        uri: Uri,
        sizePx: Int,
    ): Bitmap? =
        runCatching {
            if (isAudio(uri)) runCatching { embeddedCover(context, uri, sizePx) }.getOrNull()?.let { return@runCatching it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                retrieve(context, uri)
            }
        }.getOrNull()

    private fun isAudio(uri: Uri): Boolean = uri.path?.contains(AUDIO_PATH) == true

    private fun embeddedCover(
        context: Context,
        uri: Uri,
        sizePx: Int,
    ): Bitmap? {
        val retriever = MediaMetadataRetriever()
        val picture =
            try {
                retriever.setDataSource(context, uri)
                retriever.embeddedPicture
            } finally {
                retriever.release()
            } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(picture, 0, picture.size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, sizePx) }
        return BitmapFactory.decodeByteArray(picture, 0, picture.size, options)
    }

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

/** The largest power of two that still leaves both sides of a [width] by [height] image at least [targetPx]. */
internal fun sampleSize(
    width: Int,
    height: Int,
    targetPx: Int,
): Int {
    if (width <= 0 || height <= 0 || targetPx <= 0) return 1
    var sample = 1
    while (width / (sample * 2) >= targetPx && height / (sample * 2) >= targetPx) sample *= 2
    return sample
}
