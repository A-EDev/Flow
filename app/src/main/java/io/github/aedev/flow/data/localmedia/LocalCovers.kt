package io.github.aedev.flow.data.localmedia

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Metadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.inspector.MetadataRetriever
import io.github.aedev.flow.data.video.downloader.tags.coverPicture
import io.github.aedev.flow.data.video.downloader.tags.metadataEntries
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Covers the platform reader misses. `MediaMetadataRetriever` (and so MediaStore's own thumbnail)
 * drops an Ogg or FLAC picture block unless it is typed as the front cover and its base64 is clean,
 * which many taggers don't do; Media3's parser takes either. Failing that, a cover image saved in the
 * song's folder. Each file is looked at once: the result, found or not, is kept until it changes.
 */
@OptIn(UnstableApi::class)
internal object LocalCovers {
    fun find(
        context: Context,
        uri: Uri,
    ): ByteArray? {
        val file = context.audioFileOf(uri) ?: return null
        val cached = File(File(context.cacheDir, CACHE_DIR), file.cacheName)
        val missing = File(cached.path + MISSING_SUFFIX)
        if (cached.isFile) return runCatching { cached.readBytes() }.getOrNull()
        if (missing.isFile) return null
        // A read that failed or timed out says nothing about the file, so only a completed one is kept.
        val entries = media3Entries(context, uri)
        val cover = entries?.coverPicture() ?: file.path?.let(::folderCover)
        runCatching {
            cached.parentFile?.mkdirs()
            if (cover != null) {
                cached.writeBytes(cover.shrunk())
            } else if (entries != null) {
                missing.createNewFile()
            }
        }
        return cover
    }

    private fun media3Entries(
        context: Context,
        uri: Uri,
    ): List<Metadata.Entry>? =
        runCatching {
            MetadataRetriever.Builder(context, MediaItem.fromUri(uri)).build().use { retriever ->
                retriever.retrieveTrackGroups().get(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS).metadataEntries()
            }
        }.getOrNull()

    /** Readable only with access to images, which on Android 13 and later means All files access. */
    private fun folderCover(audioPath: String): ByteArray? =
        runCatching {
            val folder = File(audioPath).parentFile ?: return null
            val name = folderCoverName(folder.list()?.toList().orEmpty()) ?: return null
            File(folder, name).takeIf { it.length() in 1..MAX_FOLDER_COVER_BYTES }?.readBytes()
        }.getOrNull()

    /** Kept covers are re-encoded at artwork size, so a 5 MB scan doesn't sit in the cache. */
    private fun ByteArray.shrunk(): ByteArray {
        if (size <= MAX_KEPT_BYTES) return this
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(this, 0, size, bounds)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, KEPT_SIZE_PX) }
        val bitmap = BitmapFactory.decodeByteArray(this, 0, size, options) ?: return this
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, KEPT_JPEG_QUALITY, out)
            bitmap.recycle()
            out.toByteArray()
        }
    }

    private class AudioFile(
        val path: String?,
        val cacheName: String,
    )

    @Suppress("DEPRECATION")
    private fun Context.audioFileOf(uri: Uri): AudioFile? =
        runCatching {
            val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.SIZE)
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = uri.lastPathSegment ?: return@use null
                // Granting All files access can reveal a folder cover that was unreadable before.
                val access = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) "a" else "m"
                AudioFile(path = cursor.getString(0), cacheName = "${id}_${cursor.getLong(1)}_${cursor.getLong(2)}_$access")
            }
        }.getOrNull()
}

/**
 * The cover image among a folder's [names], by the names players and rippers use, most specific
 * first; null when the folder has none.
 */
internal fun folderCoverName(names: List<String>): String? {
    val images = names.filter { it.substringAfterLast('.', "").lowercase() in FOLDER_COVER_EXTENSIONS }
    return FOLDER_COVER_STEMS.firstNotNullOfOrNull { stem ->
        images.firstOrNull { it.substringBeforeLast('.').lowercase() == stem }
    } ?: images.firstOrNull { it.lowercase().startsWith(ALBUM_ART_PREFIX) }
}

private val FOLDER_COVER_STEMS = listOf("cover", "folder", "front", "album")
private val FOLDER_COVER_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
private const val ALBUM_ART_PREFIX = "albumart"
private const val CACHE_DIR = "local_covers"
private const val MISSING_SUFFIX = ".none"
private const val READ_TIMEOUT_SECONDS = 5L
private const val MAX_FOLDER_COVER_BYTES = 10L * 1024 * 1024
private const val MAX_KEPT_BYTES = 512 * 1024
private const val KEPT_SIZE_PX = 1024
private const val KEPT_JPEG_QUALITY = 90
