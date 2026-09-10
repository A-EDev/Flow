package io.github.aedev.flow.ui.components.videoplayer.overlay

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.theme.PlayerScrimContent

// Fraction of the player width each seek zone covers; mirrors SEEK_ZONE_FRACTION in the gesture layer.
private const val SEEK_ZONE_WIDTH_FRACTION = 1f / 3f
private const val SEEK_RIPPLE_ALPHA = 0.15f
private const val SEEK_RIPPLE_PULSE_ALPHA = 0.28f
private val ChevronTravel = 24.dp

@Composable
internal fun SeekAnimationOverlay(
    showSeekBack: Boolean,
    showSeekForward: Boolean,
    seekSeconds: Int = 10,
    modifier: Modifier = Modifier,
) {
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    Box(modifier = modifier.fillMaxSize()) {
        SeekZoneRipple(
            visible = showSeekBack,
            forward = false,
            pulseKey = seekSeconds,
            modifier =
                Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(SEEK_ZONE_WIDTH_FRACTION),
        )

        SeekZoneRipple(
            visible = showSeekForward,
            forward = true,
            pulseKey = seekSeconds,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(SEEK_ZONE_WIDTH_FRACTION),
        )

        AnimatedVisibility(
            visible = showSeekBack,
            enter = fadeIn(fadeSpec),
            // Exit instantly when switching to forward (no overlap), otherwise fade normally.
            exit = if (showSeekForward) fadeOut(snap()) else fadeOut(fadeSpec),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 48.dp),
        ) {
            SeekChevronLabel(forward = false, seconds = seekSeconds)
        }

        AnimatedVisibility(
            visible = showSeekForward,
            enter = fadeIn(fadeSpec),
            // Exit instantly when switching to backward (no overlap), otherwise fade normally.
            exit = if (showSeekBack) fadeOut(snap()) else fadeOut(fadeSpec),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 48.dp),
        ) {
            SeekChevronLabel(forward = true, seconds = seekSeconds)
        }
    }
}

/**
 * The tinted zone that flashes behind a double-tap seek.
 *
 * Drawn as an oversized circle clipped to the zone so the outer edge sits flush against the screen
 * while the inner edge bulges — the shape reads as "this side of the player reacted" without any
 * shadow or glow. The animated alpha is read inside `graphicsLayer`, so repeated taps repaint
 * without recomposing anything.
 */
@Composable
private fun SeekZoneRipple(
    visible: Boolean,
    forward: Boolean,
    pulseKey: Int,
    modifier: Modifier = Modifier,
) {
    val rippleAlpha = remember { Animatable(0f) }
    val fadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()

    LaunchedEffect(visible, pulseKey) {
        if (visible) {
            rippleAlpha.snapTo(SEEK_RIPPLE_PULSE_ALPHA)
            rippleAlpha.animateTo(SEEK_RIPPLE_ALPHA, fadeSpec)
        } else {
            rippleAlpha.animateTo(0f, fadeSpec)
        }
    }

    Canvas(
        modifier = modifier.graphicsLayer { alpha = rippleAlpha.value },
    ) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        // Smallest radius whose circle still covers both corners on the flush edge, so the shape
        // never leaves a sliver of untinted video at the screen border.
        val radius = w / 2f + (h * h) / (8f * w)
        val centerX = if (forward) radius else w - radius

        clipRect(left = 0f, top = 0f, right = w, bottom = h) {
            drawCircle(
                color = PlayerScrimContent,
                radius = radius,
                center = Offset(centerX, h / 2f),
            )
        }
    }
}

/**
 * The chevrons that chase the seek direction.
 *
 * The transition lives inside the `AnimatedVisibility` content, so it is cancelled with the label
 * once the seek indicator leaves; while it runs, its travel and alpha are applied in
 * `graphicsLayer` so the 800ms loop repaints without recomposing or re-laying out the row.
 */
@Composable
private fun SeekChevronLabel(
    forward: Boolean,
    seconds: Int,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "chevron")

    val progress =
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "chevronProgress",
        )

    val chevronModifier =
        Modifier.graphicsLayer {
            val value = progress.value
            val travel = ChevronTravel.toPx() * LinearOutSlowInEasing.transform(value)
            translationX = if (forward) travel else -travel
            alpha =
                when {
                    value < 0.2f -> value * 5f
                    value > 0.5f -> (1f - value) * 2f
                    else -> 1f
                }.coerceIn(0f, 1f)
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!forward) {
            Text(
                text = stringResource(R.string.player_seek_chevron_back),
                color = PlayerScrimContent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = chevronModifier,
            )
        }
        Text(
            text =
                stringResource(
                    if (forward) R.string.player_seek_seconds_forward else R.string.player_seek_seconds_back,
                    seconds,
                ),
            color = PlayerScrimContent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        if (forward) {
            Text(
                text = stringResource(R.string.player_seek_chevron_forward),
                color = PlayerScrimContent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = chevronModifier,
            )
        }
    }
}
