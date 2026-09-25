package io.github.aedev.flow.data.localmedia

import android.content.Context
import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

private const val ARTWORK_SIZE_PX = 512

/**
 * Loads a song file's artwork through [MediaStoreThumbnails], so `AsyncImage(model = songUri)` shows
 * its cover. Video files keep going through Coil's video frame decoder.
 */
class MediaStoreThumbnailFetcher(
    private val context: Context,
    private val uri: android.net.Uri,
) : Fetcher {
    override suspend fun fetch(): FetchResult =
        withContext(Dispatchers.IO) {
            val bitmap = MediaStoreThumbnails.load(context, uri, ARTWORK_SIZE_PX) ?: throw IOException("No artwork for $uri")
            ImageFetchResult(image = bitmap.asImage(), isSampled = true, dataSource = DataSource.DISK)
        }

    class Factory(
        private val context: Context,
    ) : Fetcher.Factory<Uri> {
        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            if (data.scheme != "content" || data.authority != "media" || data.path?.contains("/audio/") != true) return null
            return MediaStoreThumbnailFetcher(context.applicationContext, android.net.Uri.parse(data.toString()))
        }
    }
}
