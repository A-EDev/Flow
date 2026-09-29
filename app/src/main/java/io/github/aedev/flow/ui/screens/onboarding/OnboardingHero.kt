@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package io.github.aedev.flow.ui.screens.onboarding

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material.icons.outlined.MoveToInbox
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import io.github.aedev.flow.ui.components.shared.FlowLogo
import io.github.aedev.flow.ui.components.shared.drawMorph
import kotlinx.coroutines.flow.first

private const val HERO_TURN = -45f
private const val HERO_ICON_SHARE = 0.44f
private const val LOGO_SHARE = 0.46f
private const val ENTRY_SCALE_FROM = 0.86f
private const val BACKDROP_NEAR = 0.95f
private const val BACKDROP_FAR = 0.7f
private const val BACKDROP_TURN_NEAR = 40f
private const val BACKDROP_TURN_FAR = -30f

private fun heroShape(step: OnboardingStep): RoundedPolygon =
    when (step) {
        OnboardingStep.WELCOME -> MaterialShapes.SoftBurst
        OnboardingStep.INTERESTS -> MaterialShapes.Clover4Leaf
        OnboardingStep.CHANNELS -> MaterialShapes.Cookie9Sided
        OnboardingStep.ALERTS -> MaterialShapes.Sunny
        OnboardingStep.IMPORT -> MaterialShapes.Cookie4Sided
        OnboardingStep.READY -> MaterialShapes.Cookie12Sided
    }.normalized()

private fun backdropShapes(step: OnboardingStep): Pair<RoundedPolygon, RoundedPolygon> =
    when (step) {
        OnboardingStep.WELCOME -> MaterialShapes.Flower to MaterialShapes.Cookie6Sided
        OnboardingStep.INTERESTS -> MaterialShapes.Clover8Leaf to MaterialShapes.Cookie4Sided
        OnboardingStep.CHANNELS -> MaterialShapes.Cookie9Sided to MaterialShapes.Sunny
        OnboardingStep.ALERTS -> MaterialShapes.Burst to MaterialShapes.Cookie12Sided
        OnboardingStep.IMPORT -> MaterialShapes.Cookie4Sided to MaterialShapes.Clover4Leaf
        OnboardingStep.READY -> MaterialShapes.SoftBurst to MaterialShapes.Flower
    }.let { (near, far) -> near.normalized() to far.normalized() }

private val PetalMorph: Morph by lazy { Morph(MaterialShapes.Clover4Leaf.normalized(), MaterialShapes.Clover8Leaf.normalized()) }

/**
 * The step's shape with its icon inside. It morphs from [fromStep]'s shape when the step appears,
 * and on Interests grows from four leaves to eight as [petals] goes from 0 to 1. Every value is
 * read while drawing, and nothing runs once the shape has settled.
 */
@Composable
internal fun OnboardingHero(
    step: OnboardingStep,
    fromStep: OnboardingStep,
    size: Dp,
    modifier: Modifier = Modifier,
    petals: Float = 0f,
) {
    val entry = remember { Animatable(if (step == fromStep) 1f else 0f) }
    val petal = remember { Animatable(0f) }
    val entrySpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val petalSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val shapes = remember { Morph(heroShape(fromStep), heroShape(step)) }
    val container = MaterialTheme.colorScheme.primaryContainer

    LaunchedEffect(Unit) { entry.animateTo(1f, entrySpec) }
    LaunchedEffect(petals) {
        snapshotFlow { entry.value >= 1f }.first { it }
        petal.animateTo(petals, petalSpec)
    }

    Box(
        modifier =
            modifier
                .size(size)
                .graphicsLayer {
                    val scale = ENTRY_SCALE_FROM + (1f - ENTRY_SCALE_FROM) * entry.value
                    scaleX = scale
                    scaleY = scale
                }.drawWithCache {
                    val path = Path()
                    onDrawBehind {
                        val settled = entry.value >= 1f
                        val morph = if (step == OnboardingStep.INTERESTS && settled) PetalMorph else shapes
                        val progress = if (morph === PetalMorph) petal.value else entry.value
                        drawMorph(
                            morph = morph,
                            progress = progress.coerceIn(0f, 1f),
                            path = path,
                            center = center,
                            extent = this.size.minDimension,
                            degrees = HERO_TURN * (1f - entry.value),
                            color = container,
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        HeroMark(step)
    }
}

@Composable
private fun HeroMark(step: OnboardingStep) {
    val tint = MaterialTheme.colorScheme.onPrimaryContainer
    val icon =
        when (step) {
            OnboardingStep.WELCOME -> null
            OnboardingStep.INTERESTS -> Icons.Outlined.Interests
            OnboardingStep.CHANNELS -> Icons.Outlined.Subscriptions
            OnboardingStep.ALERTS -> Icons.Outlined.NotificationsActive
            OnboardingStep.IMPORT -> Icons.Outlined.MoveToInbox
            OnboardingStep.READY -> Icons.Rounded.Check
        }
    if (icon != null) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.fillMaxWidth(HERO_ICON_SHARE).aspectRatio(1f))
    } else {
        FlowLogo(Modifier.fillMaxWidth(LOGO_SHARE))
    }
}

/**
 * Two large shapes behind the whole setup that morph from [fromStep]'s pair into [step]'s as
 * [progress] runs, drawn in a solid container colour.
 */
internal fun Modifier.onboardingBackdrop(
    fromStep: OnboardingStep,
    step: OnboardingStep,
    color: Color,
    progress: () -> Float,
): Modifier =
    drawWithCache {
        val (nearFrom, farFrom) = backdropShapes(fromStep)
        val (nearTo, farTo) = backdropShapes(step)
        val near = Morph(nearFrom, nearTo)
        val far = Morph(farFrom, farTo)
        val path = Path()
        val nearSize = size.minDimension * BACKDROP_NEAR
        val farSize = size.minDimension * BACKDROP_FAR
        onDrawBehind {
            val t = progress().coerceIn(0f, 1f)
            val turn = fromStep.index + (step.index - fromStep.index) * t
            drawMorph(near, t, path, Offset(size.width, 0f), nearSize, BACKDROP_TURN_NEAR * turn, color)
            drawMorph(far, t, path, Offset(0f, size.height), farSize, BACKDROP_TURN_FAR * turn, color)
        }
    }

internal val HeroSmall: Dp = 72.dp
internal val HeroLarge: Dp = 220.dp
internal val HeroReady: Dp = 180.dp
internal val HeroSide: Dp = 132.dp
