package io.github.aedev.flow.widget.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.layout.size
import androidx.glance.unit.ColorProvider
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.min

/**
 * M3 Expressive decorative shapes for widgets, built on the same RoundedPolygon
 * geometry as the Material shape library. Rendered into bitmaps because
 * RemoteViews can only clip to rounded rectangles natively.
 */
enum class WidgetShape(internal val polygon: RoundedPolygon) {
    /** Scallop-edged disc — the expressive "cookie". Used for the turntable record. */
    COOKIE(
        RoundedPolygon.star(
            numVerticesPerRadius = 12,
            innerRadius = 0.85f,
            rounding = CornerRounding(radius = 0.18f, smoothing = 1f),
        ).normalized()
    ),

    /** Soft eight-point sun. Used for Now Playing artwork. */
    SUNNY(
        RoundedPolygon.star(
            numVerticesPerRadius = 8,
            innerRadius = 0.78f,
            rounding = CornerRounding(radius = 0.2f, smoothing = 1f),
        ).normalized()
    ),

    /** Four-leaf clover. Used for placeholder discs and decorative accents. */
    CLOVER(
        RoundedPolygon.star(
            numVerticesPerRadius = 4,
            innerRadius = 0.55f,
            rounding = CornerRounding(radius = 0.45f, smoothing = 1f),
            innerRounding = CornerRounding(radius = 0.45f, smoothing = 1f),
        ).normalized()
    ),
}

private fun WidgetShape.scaledPath(sizePx: Int): Path {
    val path = polygon.toPath()
    val matrix = Matrix().apply { setScale(sizePx.toFloat(), sizePx.toFloat()) }
    path.transform(matrix)
    return path
}

/** Coil transformation that clips an image to an expressive [WidgetShape]. */
class WidgetShapeTransformation(private val shape: WidgetShape) : Transformation {

    override val cacheKey: String = "widgetShape:${shape.name}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val side = min(input.width, input.height)
        val squared = Bitmap.createBitmap(
            input,
            (input.width - side) / 2,
            (input.height - side) / 2,
            side,
            side,
        )
        val output = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            shader = BitmapShader(squared, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        canvas.drawPath(shape.scaledPath(side), paint)
        if (squared !== input) squared.recycle()
        return output
    }
}

private fun solidShapeBitmap(shape: WidgetShape, argb: Int, sizePx: Int): Bitmap {
    val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb }
    canvas.drawPath(shape.scaledPath(sizePx), paint)
    return output
}

/**
 * A solid tonal expressive shape (decorative element per the M3 shape library).
 * Cheap to draw and cached per shape+color+size across recompositions.
 */
@Composable
fun ShapeDecor(shape: WidgetShape, color: ColorProvider, size: Dp) {
    val context: Context = LocalContext.current
    val argb = color.getColor(context).toArgb()
    val sizePx = (size.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val bitmap = remember(shape, argb, sizePx) { solidShapeBitmap(shape, argb, sizePx) }
    Image(
        provider = ImageProvider(bitmap),
        contentDescription = null,
        modifier = GlanceModifier.size(size),
    )
}
