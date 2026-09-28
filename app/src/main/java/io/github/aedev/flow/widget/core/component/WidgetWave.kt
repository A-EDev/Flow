package io.github.aedev.flow.widget.core.component

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.ContentScale
import androidx.glance.layout.height
import kotlin.math.PI
import kotlin.math.sin

/**
 * The M3 Expressive wavy line as a playing indicator: waved while music plays, flat while paused.
 * It is not a progress bar, since a widget cannot move it; the elapsed time beside it is the clock.
 * The mask is wider than any widget and cropped, so the wavelength never stretches.
 */
@Composable
internal fun WidgetWave(
    playing: Boolean,
    modifier: GlanceModifier,
) {
    val density = LocalContext.current.resources.displayMetrics.density
    val mask = remember(playing, density) { waveMask(playing, density) }
    Image(
        provider = ImageProvider(mask),
        contentDescription = null,
        modifier = modifier.height(WaveHeight),
        contentScale = ContentScale.Crop,
        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
    )
}

private fun waveMask(
    playing: Boolean,
    density: Float,
): Bitmap {
    val width = (MASK_WIDTH_DP * density).toInt()
    val height = (WaveHeight.value * density).toInt()
    val stroke = STROKE_DP * density
    val mid = height / 2f
    val amplitude = if (playing) AMPLITUDE_DP * density else 0f
    val wavelength = WAVELENGTH_DP * density
    val path =
        Path().apply {
            moveTo(0f, mid)
            var x = 0f
            while (x <= width) {
                lineTo(x, mid + amplitude * sin(2 * PI * x / wavelength).toFloat())
                x += density
            }
        }
    val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = stroke
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { Canvas(it).drawPath(path, paint) }
}

private val WaveHeight = 12.dp
private const val MASK_WIDTH_DP = 640f
private const val STROKE_DP = 3.5f
private const val AMPLITUDE_DP = 3f
private const val WAVELENGTH_DP = 28f
