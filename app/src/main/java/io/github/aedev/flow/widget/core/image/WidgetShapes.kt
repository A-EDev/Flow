package io.github.aedev.flow.widget.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import coil3.size.Size
import coil3.transform.Transformation
import kotlin.math.min

/**
 * The Material 3 Expressive shapes the app uses, rasterised because RemoteViews can only clip to
 * rounded rectangles. Cookie12 marks actions, Cookie9 artists, as in FlowShapes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
enum class WidgetShape {
    COOKIE_12,
    COOKIE_9,
    SUNNY,
    CLOVER,
    ;

    internal val polygon: RoundedPolygon by lazy {
        when (this) {
            COOKIE_12 -> MaterialShapes.Cookie12Sided
            COOKIE_9 -> MaterialShapes.Cookie9Sided
            SUNNY -> MaterialShapes.Sunny
            CLOVER -> MaterialShapes.Clover4Leaf
        }.normalized()
    }
}

private fun WidgetShape.scaledPath(sizePx: Int): Path {
    val path = polygon.toPath()
    val matrix = Matrix().apply { setScale(sizePx.toFloat(), sizePx.toFloat()) }
    path.transform(matrix)
    return path
}

/**
 * Clips an image to [shape]. A [holeFraction] above zero punches a transparent spindle hole, so the
 * widget's own day/night background shows through instead of a colour baked into the bitmap.
 */
class WidgetShapeTransformation(
    private val shape: WidgetShape,
    private val holeFraction: Float = 0f,
) : Transformation() {
    override val cacheKey: String = "widgetShape:${shape.name}:$holeFraction"

    override suspend fun transform(
        input: Bitmap,
        size: Size,
    ): Bitmap {
        val side = min(input.width, input.height)
        val squared =
            Bitmap.createBitmap(
                input,
                (input.width - side) / 2,
                (input.height - side) / 2,
                side,
                side,
            )
        val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                shader = BitmapShader(squared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            }
        canvas.drawPath(shape.scaledPath(side), paint)
        if (holeFraction > 0f) {
            val clear = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
            canvas.drawCircle(side / 2f, side / 2f, side * holeFraction / 2f, clear)
        }
        if (squared !== input) squared.recycle()
        return output
    }
}

private fun shapeMask(
    shape: WidgetShape,
    sizePx: Int,
): Bitmap {
    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
    Canvas(output).drawPath(shape.scaledPath(sizePx), paint)
    return output
}

/**
 * A solid tonal expressive shape. The bitmap is a white mask tinted at render time, so the colour
 * stays a day/night pair the launcher switches with dark mode instead of one baked-in value.
 */
@Composable
fun ShapeDecor(
    shape: WidgetShape,
    color: ColorProvider,
    size: Dp,
) {
    val context: Context = LocalContext.current
    val sizePx = (size.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val mask = remember(shape, sizePx) { shapeMask(shape, sizePx) }
    Image(
        provider = ImageProvider(mask),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
        colorFilter = ColorFilter.tint(color),
    )
}
