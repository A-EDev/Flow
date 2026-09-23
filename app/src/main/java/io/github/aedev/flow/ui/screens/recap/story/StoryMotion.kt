@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.ui.screens.recap.story

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay

private const val BACKDROP_ALPHA_NEAR = 0.16f
private const val BACKDROP_ALPHA_FAR = 0.10f
private const val BACKDROP_SIZE = 0.95f
private const val BACKDROP_SIZE_FAR = 0.7f
private const val BACKDROP_TURN_NEAR = 70f
private const val BACKDROP_TURN_FAR = -50f
private const val PORTRAIT_MORPH_SHARE = 0.3f
private const val STAGGER_MS = 70L

/** Pairs of Material shapes a page's backdrop morphs between while the page is on screen. */
private val BackdropShapes: List<Pair<RoundedPolygon, RoundedPolygon>> by lazy {
    listOf(
        MaterialShapes.Cookie12Sided to MaterialShapes.Clover8Leaf,
        MaterialShapes.Sunny to MaterialShapes.SoftBurst,
        MaterialShapes.Cookie9Sided to MaterialShapes.Flower,
        MaterialShapes.Puffy to MaterialShapes.Cookie6Sided,
        MaterialShapes.Clover4Leaf to MaterialShapes.Burst,
        MaterialShapes.Pentagon to MaterialShapes.Cookie7Sided,
    ).map { (from, to) -> from.normalized() to to.normalized() }
}

private val PortraitMorph: Morph by lazy { Morph(MaterialShapes.Circle.normalized(), MaterialShapes.Cookie9Sided.normalized()) }

/**
 * A page's ground: its colour, and two large Material shapes that slowly turn and morph as the
 * page's clock runs. [clock] is read only while drawing, so the page never recomposes for it, and
 * the shapes stand still whenever the story is paused.
 */
internal fun Modifier.storyBackdrop(
    container: Color,
    ink: Color,
    seed: Int,
    clock: () -> Float,
): Modifier =
    drawWithCache {
        val (nearFrom, nearTo) = BackdropShapes[seed % BackdropShapes.size]
        val (farFrom, farTo) = BackdropShapes[(seed + 1) % BackdropShapes.size]
        val near = Morph(nearFrom, nearTo)
        val far = Morph(farFrom, farTo)
        val path = Path()
        val nearSize = size.minDimension * BACKDROP_SIZE
        val farSize = size.minDimension * BACKDROP_SIZE_FAR
        onDrawBehind {
            drawRect(container)
            val t = clock().coerceIn(0f, 1f)
            drawMorph(near, t, path, Offset(size.width, 0f), nearSize, BACKDROP_TURN_NEAR * t, ink.copy(alpha = BACKDROP_ALPHA_NEAR))
            drawMorph(far, 1f - t, path, Offset(0f, size.height), farSize, BACKDROP_TURN_FAR * t, ink.copy(alpha = BACKDROP_ALPHA_FAR))
        }
    }

private fun DrawScope.drawMorph(
    morph: Morph,
    progress: Float,
    path: Path,
    center: Offset,
    extent: Float,
    degrees: Float,
    color: Color,
) {
    morph.toPath(progress, path)
    withTransform({
        translate(center.x - extent / 2f, center.y - extent / 2f)
        rotate(degrees, pivot = Offset(extent / 2f, extent / 2f))
        scale(extent, extent, pivot = Offset.Zero)
    }) { drawPath(path, color) }
}

/** A one-shot spring that plays once when its page is first shown, then holds at 1. */
@Composable
internal fun rememberPagePop(): Animatable<Float, AnimationVector1D> {
    val pop = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { pop.animateTo(1f, spec) }
    return pop
}

/**
 * A portrait that pops in on a spring and morphs from a circle into the artist cookie as it lands.
 * With no [imageUrl], or while it loads, [fallback] initials sit on the accent colour.
 */
@Composable
internal fun MorphingPortrait(
    imageUrl: String,
    fallback: String,
    diameter: Dp,
    accent: Color,
    onAccent: Color,
    modifier: Modifier = Modifier,
    delayIndex: Int = 0,
) {
    val pop = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(delayIndex * STAGGER_MS)
        pop.animateTo(1f, spec)
    }
    Box(
        modifier =
            modifier
                .size(diameter)
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }.drawWithCache {
                    val path = Path()
                    onDrawWithContent {
                        PortraitMorph.toPath((pop.value / PORTRAIT_MORPH_SHARE).coerceIn(0f, 1f), path)
                        clipPath(path.scaledTo(size.width, size.height)) { this@onDrawWithContent.drawContent() }
                    }
                }.background(accent),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fallback.initials(),
            color = onAccent,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (imageUrl.isNotBlank()) {
            AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
    }
}

private fun Path.scaledTo(
    width: Float,
    height: Float,
): Path {
    transform(Matrix().apply { scale(width, height) })
    return this
}

/** Content that springs in after [index] siblings, for lists that should land one by one. */
@Composable
internal fun PopIn(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val pop = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(Unit) {
        delay(index * STAGGER_MS)
        pop.animateTo(1f, spec)
    }
    Box(
        modifier.graphicsLayer {
            val value = pop.value
            scaleX = value
            scaleY = value
            alpha = value.coerceIn(0f, 1f)
        },
    ) { content() }
}

/** The first letters of up to two words, for a portrait with no image. */
internal fun String.initials(): String =
    split(' ', '-', '_')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "?" }

/** A number that counts up with [pop] as its page lands; recomposes only while [pop] is moving. */
@Composable
internal fun CountUp(
    target: Long,
    pop: Animatable<Float, AnimationVector1D>,
    format: @Composable (Long) -> String,
): String = format((target * pop.value.coerceIn(0f, 1f)).toLong())
