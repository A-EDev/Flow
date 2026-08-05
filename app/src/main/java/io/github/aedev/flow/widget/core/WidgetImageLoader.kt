package io.github.aedev.flow.widget.core

import android.content.Context
import android.graphics.Bitmap
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Scale
import coil3.toBitmap
import coil3.transform.RoundedCornersTransformation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads artwork/thumbnails for widgets as software bitmaps (RemoteViews cannot render
 * hardware bitmaps). Results are memory-capped and LRU-cached so transport events that
 * re-render a widget don't re-decode the same artwork.
 */
object WidgetImageLoader {
    private const val MAX_CACHE_ENTRIES = 8

    private val cache =
        object : LinkedHashMap<String, Bitmap>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > MAX_CACHE_ENTRIES
        }

    suspend fun load(
        context: Context,
        url: String?,
        widthPx: Int,
        heightPx: Int = widthPx,
        cornerRadiusPx: Float = 0f,
        shape: WidgetShape? = null,
    ): Bitmap? {
        if (url.isNullOrBlank()) return null
        val key = "$url|$widthPx|$heightPx|$cornerRadiusPx|${shape?.name}"
        synchronized(cache) { cache[key] }?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val request =
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(widthPx, heightPx)
                        .scale(Scale.FILL)
                        .allowHardware(false)
                        .apply {
                            when {
                                shape != null -> {
                                    transformations(WidgetShapeTransformation(shape))
                                }

                                cornerRadiusPx > 0f -> {
                                    transformations(RoundedCornersTransformation(cornerRadiusPx))
                                }
                            }
                        }.build()
                val bitmap =
                    SingletonImageLoader
                        .get(context)
                        .execute(request)
                        .image
                        ?.toBitmap()
                if (bitmap != null) {
                    synchronized(cache) { cache[key] = bitmap }
                }
                bitmap
            } catch (e: Exception) {
                null
            }
        }
    }
}
