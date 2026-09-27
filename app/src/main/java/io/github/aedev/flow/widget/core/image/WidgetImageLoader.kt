package io.github.aedev.flow.widget.core.image

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Scale
import coil3.toBitmap
import coil3.transform.RoundedCornersTransformation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads artwork/thumbnails for widgets as software bitmaps (RemoteViews cannot render hardware
 * bitmaps). Coil's memory cache keys on URL, size and transformations, so re-renders reuse decodes.
 */
object WidgetImageLoader {
    private const val TAG = "WidgetImageLoader"

    suspend fun load(
        context: Context,
        url: String?,
        widthPx: Int,
        heightPx: Int = widthPx,
        cornerRadiusPx: Float = 0f,
        shape: WidgetShape? = null,
        holeFraction: Float = 0f,
    ): Bitmap? {
        if (url.isNullOrBlank()) return null
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
                                shape != null -> transformations(WidgetShapeTransformation(shape, holeFraction))
                                cornerRadiusPx > 0f -> transformations(RoundedCornersTransformation(cornerRadiusPx))
                            }
                        }.build()
                SingletonImageLoader
                    .get(context)
                    .execute(request)
                    .image
                    ?.toBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Artwork load failed: ${e.message}")
                null
            }
        }
    }
}
