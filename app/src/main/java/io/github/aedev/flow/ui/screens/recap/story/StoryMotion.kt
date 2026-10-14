@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.ui.screens.recap.story

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import io.github.aedev.flow.ui.components.shared.drawMorph

private const val BACKDROP_ALPHA_NEAR = 0.16f
private const val BACKDROP_ALPHA_FAR = 0.10f
private const val BACKDROP_SIZE = 0.95f
private const val BACKDROP_SIZE_FAR = 0.7f
private const val BACKDROP_TURN_NEAR = 70f
private const val BACKDROP_TURN_FAR = -50f

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

/** A one-shot spring that plays once when its page is first shown, then holds at 1. */
@Composable
internal fun rememberPagePop(): Animatable<Float, AnimationVector1D> {
    val pop = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { pop.animateTo(1f, spec) }
    return pop
}

/** A number that counts up with [pop] as its page lands; recomposes only while [pop] is moving. */
@Composable
internal fun CountUp(
    target: Long,
    pop: Animatable<Float, AnimationVector1D>,
    format: @Composable (Long) -> String,
): String = format((target * pop.value.coerceIn(0f, 1f)).toLong())
