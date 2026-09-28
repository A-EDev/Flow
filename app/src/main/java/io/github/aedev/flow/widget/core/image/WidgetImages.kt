package io.github.aedev.flow.widget.core.image

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

/** One image a widget draws, keyed so a layout can find its bitmap. */
data class WidgetImageSpec(
    val key: String,
    val url: String?,
    val widthPx: Int,
    val heightPx: Int = widthPx,
    val cornerRadiusPx: Float = 0f,
)

/** Loads every spec as one parallel round; a list widget holds at most a dozen. */
suspend fun loadWidgetImages(
    context: Context,
    specs: List<WidgetImageSpec>,
): Map<String, Bitmap> =
    coroutineScope {
        specs
            .map { spec ->
                async { spec.key to WidgetImageLoader.load(context, spec.url, spec.widthPx, spec.heightPx, spec.cornerRadiusPx) }
            }.awaitAll()
            .mapNotNull { (key, bitmap) -> bitmap?.let { key to it } }
            .toMap()
    }

/**
 * What loads within [PRELOAD_MS], so a re-render from Coil's memory cache draws its images at once
 * instead of flashing placeholders; a cold cache renders text first and fills in after.
 */
suspend fun preloadWidgetImages(
    context: Context,
    specs: List<WidgetImageSpec>,
): Map<String, Bitmap> = withTimeoutOrNull(PRELOAD_MS) { loadWidgetImages(context, specs) } ?: emptyMap()

@Composable
fun rememberWidgetImages(
    context: Context,
    specs: List<WidgetImageSpec>,
    preloaded: Map<String, Bitmap>,
): State<Map<String, Bitmap>> =
    produceState(preloaded, specs) {
        if (specs.any { it.url != null && it.key !in value }) value = loadWidgetImages(context, specs)
    }

private const val PRELOAD_MS = 1_500L
