package io.github.aedev.flow.service.auto

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Serves song artwork to the car. Android Auto only loads browse artwork from `content://` URIs,
 * so each web image is handed over as one of these, fetched through Coil's disk cache.
 *
 * The car app is granted read access when it connects ([grantTo]). The provider answers only for
 * YouTube's own image hosts, so a grant can never be used to fetch anything else.
 */
class AutoArtworkProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun openFile(
        uri: Uri,
        mode: String,
    ): ParcelFileDescriptor {
        val context = context ?: throw FileNotFoundException(uri.toString())
        val url = uri.getQueryParameter(URL_PARAM)?.takeIf(::isAllowedArtworkUrl) ?: throw FileNotFoundException(uri.toString())
        val loader = SingletonImageLoader.get(context)
        val diskCache = loader.diskCache ?: throw FileNotFoundException(url)

        diskCache.openSnapshot(url)?.use { return ParcelFileDescriptor.open(it.data.toFile(), ParcelFileDescriptor.MODE_READ_ONLY) }
        // openFile runs on a binder thread and the car waits for the file, so the load is awaited here.
        runBlocking { loader.execute(ImageRequest.Builder(context).data(url).build()) }
        diskCache.openSnapshot(url)?.use { return ParcelFileDescriptor.open(it.data.toFile(), ParcelFileDescriptor.MODE_READ_ONLY) }
        Log.w(TAG, "No artwork for $url")
        throw FileNotFoundException(url)
    }

    override fun getType(uri: Uri): String = "image/*"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(
        uri: Uri,
        values: ContentValues?,
    ): Uri? = null

    override fun delete(
        uri: Uri,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    companion object {
        private const val TAG = "AutoArtwork"
        private const val URL_PARAM = "u"
        private const val ART_PATH = "art"
        private const val AUTHORITY_SUFFIX = ".auto.artwork"

        private val ALLOWED_HOST_SUFFIXES = listOf(".ytimg.com", ".googleusercontent.com", ".ggpht.com")

        /** Lets [packageName] read every artwork URI this provider hands out, with one prefix grant. */
        fun grantTo(
            context: Context,
            packageName: String,
        ) {
            val prefix =
                Uri
                    .Builder()
                    .scheme("content")
                    .authority(context.packageName + AUTHORITY_SUFFIX)
                    .appendPath(ART_PATH)
                    .build()
            runCatching {
                context.grantUriPermission(
                    packageName,
                    prefix,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
                )
            }.onFailure { Log.w(TAG, "Could not grant artwork to $packageName", it) }
        }

        /** [url] as the car can load it: web artwork through this provider, local artwork as it is. */
        fun uriFor(
            context: Context,
            url: String,
        ): Uri? {
            if (url.isBlank()) return null
            val parsed = Uri.parse(url)
            return when {
                parsed.scheme == "content" || parsed.scheme == "android.resource" -> {
                    parsed
                }

                isAllowedArtworkUrl(url) -> {
                    Uri
                        .Builder()
                        .scheme("content")
                        .authority(context.packageName + AUTHORITY_SUFFIX)
                        .appendPath(ART_PATH)
                        .appendQueryParameter(URL_PARAM, url)
                        .build()
                }

                else -> {
                    null
                }
            }
        }

        internal fun isAllowedArtworkUrl(url: String): Boolean {
            val parsed = runCatching { java.net.URI(url) }.getOrNull() ?: return false
            val host = parsed.host?.lowercase() ?: return false
            return parsed.scheme == "https" && ALLOWED_HOST_SUFFIXES.any { host.endsWith(it) }
        }
    }
}
