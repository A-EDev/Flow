

// ============================================================================
// THIS IMPLEMENTATION WAS INSPIRED BY METROLIST
// ============================================================================

package io.github.aedev.flow.ui.components.musicplayer

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.local.SliderStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// ============================================================================
// EXPRESSIVE SLIDER (M3 handle thumb + gapped track + stop indicator)
// ============================================================================

@Immutable
data class ExpressiveSliderSpec(
    val trackHeight: Dp,
    val thumbHeight: Dp,
    val thumbTrackGap: Dp,
)

/** Per-style anatomy for the M3 expressive slider; SQUIGGLY draws its own wave instead. */
fun expressiveSliderSpec(style: SliderStyle): ExpressiveSliderSpec =
    when (style) {
        SliderStyle.THICK -> {
            ExpressiveSliderSpec(trackHeight = 26.dp, thumbHeight = 44.dp, thumbTrackGap = 6.dp)
        }

        SliderStyle.COMPACT -> {
            ExpressiveSliderSpec(trackHeight = 8.dp, thumbHeight = 26.dp, thumbTrackGap = 4.dp)
        }

        SliderStyle.SLIM -> {
            ExpressiveSliderSpec(trackHeight = 4.dp, thumbHeight = 0.dp, thumbTrackGap = 0.dp)
        }

        SliderStyle.DEFAULT, SliderStyle.SQUIGGLY, SliderStyle.EXPRESSIVE_WAVY -> {
            ExpressiveSliderSpec(trackHeight = 16.dp, thumbHeight = 36.dp, thumbTrackGap = 6.dp)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressivePlayerSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    trackHeight: Dp,
    thumbHeight: Dp,
    thumbTrackGap: Dp,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val colors =
        SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primary,
            activeTrackColor = MaterialTheme.colorScheme.primary,
            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
        )
    Slider(
        value = value,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        valueRange = valueRange,
        interactionSource = interactionSource,
        thumb = {
            if (thumbHeight > 0.dp) {
                SliderDefaults.Thumb(
                    interactionSource = interactionSource,
                    colors = colors,
                    thumbSize = DpSize(width = 4.dp, height = thumbHeight),
                )
            }
        },
        track = { sliderState ->
            SliderDefaults.Track(
                sliderState = sliderState,
                colors = colors,
                thumbTrackGapSize = thumbTrackGap,
                modifier = Modifier.height(trackHeight),
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}

// ============================================================================
// EXPRESSIVE WAVY SLIDER (seekable LinearWavyProgressIndicator + round thumb)
// ============================================================================
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveWavySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val range = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.001f)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction =
        if (isDragging) {
            dragFraction
        } else {
            ((value - valueRange.start) / range).coerceIn(0f, 1f)
        }
    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isPlaying && !isDragging) 1f else 0f,
        label = "expressiveWaveAmplitude",
    )
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
    val density = LocalDensity.current
    val thumbRadius = 7.dp
    val stroke = remember(density) { Stroke(width = with(density) { 5.dp.toPx() }, cap = StrokeCap.Round) }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .pointerInput(valueRange) {
                    detectTapGestures { offset ->
                        val tapped = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(valueRange.start + tapped * range)
                        onValueChangeFinished()
                    }
                }.pointerInput(valueRange) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            isDragging = true
                            dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onValueChange(valueRange.start + dragFraction * range)
                        },
                        onDragEnd = {
                            isDragging = false
                            onValueChangeFinished()
                        },
                        onDragCancel = { isDragging = false },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            dragFraction = (dragFraction + dragAmount / size.width).coerceIn(0f, 1f)
                            onValueChange(valueRange.start + dragFraction * range)
                        },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        LinearWavyProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth(),
            color = activeColor,
            trackColor = inactiveColor,
            stroke = stroke,
            trackStroke = stroke,
            gapSize = thumbRadius + 4.dp,
            amplitude = { progress -> if (progress > 0f) animatedAmplitude else 0f },
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = activeColor,
                radius = thumbRadius.toPx(),
                center = Offset(size.width * fraction, size.height / 2f),
            )
        }
    }
}

// ============================================================================
// SQUIGGLY SLIDER
// ============================================================================
@Composable
fun SquigglySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
) {
    val primaryColor = colors.activeTrackColor
    val inactiveColor = colors.inactiveTrackColor

    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(value) }

    val currentValue = if (isDragging) dragPosition else value
    val duration = valueRange.endInclusive - valueRange.start
    val position = currentValue - valueRange.start

    // Animation state
    var phaseOffset by remember { mutableFloatStateOf(0f) }
    var heightFraction by remember { mutableFloatStateOf(if (isPlaying) 1f else 0f) }

    val scope = rememberCoroutineScope()

    // Wave parameters
    val waveLength = 80f
    val lineAmplitude = 6f
    val phaseSpeed = 24f
    val transitionPeriods = 1.5f
    val minWaveEndpoint = 0f
    val matchedWaveEndpoint = 1f
    val transitionEnabled = true

    // Animate height fraction based on playing state and dragging state
    LaunchedEffect(isPlaying, isDragging) {
        scope.launch {
            val shouldFlatten = !isPlaying || isDragging
            val targetHeight = if (shouldFlatten) 0f else 1f
            val animDuration = if (shouldFlatten) 150 else 200
            val startDelay = if (shouldFlatten) 0L else 30L

            delay(startDelay)

            val animator = Animatable(heightFraction)
            animator.animateTo(
                targetValue = targetHeight,
                animationSpec =
                    tween(
                        durationMillis = animDuration,
                        easing = LinearEasing,
                    ),
            ) {
                heightFraction = this.value
            }
        }
    }

    // Animate wave movement only when playing
    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect

        var lastFrameTime = withFrameMillis { it }
        while (isActive) {
            withFrameMillis { frameTimeMillis ->
                val deltaTime = (frameTimeMillis - lastFrameTime) / 1000f
                phaseOffset += deltaTime * phaseSpeed
                phaseOffset %= waveLength
                lastFrameTime = frameTimeMillis
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(48.dp)
                .then(
                    if (enabled) {
                        Modifier
                            .pointerInput(valueRange) {
                                detectTapGestures { offset ->
                                    val newPosition = (offset.x / size.width) * duration
                                    val mappedValue = valueRange.start + newPosition.coerceIn(0f, duration)
                                    onValueChange(mappedValue)
                                    onValueChangeFinished?.invoke()
                                }
                            }.pointerInput(valueRange) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        isDragging = true
                                        val newPosition = (offset.x / size.width) * duration
                                        dragPosition = valueRange.start + newPosition.coerceIn(0f, duration)
                                        onValueChange(dragPosition)
                                    },
                                    onDragEnd = {
                                        isDragging = false
                                        onValueChangeFinished?.invoke()
                                    },
                                    onDragCancel = {
                                        isDragging = false
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val newPosition = (change.position.x / size.width) * duration
                                        dragPosition = valueRange.start + newPosition.coerceIn(0f, duration)
                                        onValueChange(dragPosition)
                                    },
                                )
                            }
                    } else {
                        Modifier
                    },
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
        ) {
            val strokeWidth = 5.dp.toPx()
            val progress = if (duration > 0f) (position / duration).coerceIn(0f, 1f) else 0f
            val totalWidth = size.width
            val totalProgressPx = totalWidth * progress
            val centerY = size.height / 2f

            val waveProgressPx =
                if (!transitionEnabled || progress > matchedWaveEndpoint) {
                    totalWidth * progress
                } else {
                    val t = (progress / matchedWaveEndpoint).coerceIn(0f, 1f)
                    totalWidth * (minWaveEndpoint + (matchedWaveEndpoint - minWaveEndpoint) * t)
                }

            fun computeAmplitude(
                x: Float,
                sign: Float,
            ): Float =
                if (transitionEnabled) {
                    val length = transitionPeriods * waveLength
                    val coeff = ((waveProgressPx + length / 2f - x) / length).coerceIn(0f, 1f)
                    sign * heightFraction * lineAmplitude * coeff
                } else {
                    sign * heightFraction * lineAmplitude
                }

            val path = Path()
            val waveStart = -phaseOffset - waveLength / 2f
            val waveEnd = if (transitionEnabled) totalWidth else waveProgressPx

            path.moveTo(waveStart, centerY)

            var currentX = waveStart
            var waveSign = 1f
            var currentAmp = computeAmplitude(currentX, waveSign)
            val dist = waveLength / 2f

            while (currentX < waveEnd) {
                waveSign = -waveSign
                val nextX = currentX + dist
                val midX = currentX + dist / 2f
                val nextAmp = computeAmplitude(nextX, waveSign)

                path.cubicTo(
                    midX,
                    centerY + currentAmp,
                    midX,
                    centerY + nextAmp,
                    nextX,
                    centerY + nextAmp,
                )

                currentAmp = nextAmp
                currentX = nextX
            }

            val clipTop = lineAmplitude + strokeWidth

            val disabledAlpha = 77f / 255f
            val inactiveTrackColor = primaryColor.copy(alpha = disabledAlpha)
            val capRadius = strokeWidth / 2f

            fun drawPathSegment(
                startX: Float,
                endX: Float,
                color: Color,
            ) {
                if (endX <= startX) return
                clipRect(
                    left = startX,
                    top = centerY - clipTop,
                    right = endX,
                    bottom = centerY + clipTop,
                ) {
                    drawPath(
                        path = path,
                        color = color,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    )
                }
            }

            drawPathSegment(0f, totalProgressPx, primaryColor)

            drawPathSegment(totalProgressPx, totalWidth, inactiveTrackColor)

            fun getWaveY(x: Float): Float {
                val phase = (x - waveStart) / waveLength
                val waveCycle = phase - kotlin.math.floor(phase)
                val waveValue = kotlin.math.cos(waveCycle * 2f * kotlin.math.PI.toFloat())

                val ampCoeff =
                    if (transitionEnabled) {
                        val length = transitionPeriods * waveLength
                        ((waveProgressPx + length / 2f - x) / length).coerceIn(0f, 1f)
                    } else {
                        1f
                    }

                return centerY + waveValue * lineAmplitude * heightFraction * ampCoeff
            }

            drawCircle(
                color = primaryColor,
                radius = capRadius,
                center = Offset(0f, getWaveY(0f)),
            )

            val endWaveY = getWaveY(totalWidth)
            clipRect(
                left = totalWidth,
                top = centerY - clipTop,
                right = totalWidth + capRadius,
                bottom = centerY + clipTop,
            ) {
                drawCircle(
                    color = inactiveTrackColor,
                    radius = capRadius,
                    center = Offset(totalWidth, endWaveY),
                )
            }

            // Vertical Bar Thumb
            val barHalfHeight = (lineAmplitude + strokeWidth)
            val barWidth = 5.dp.toPx()

            if (barHalfHeight > 0.5f) {
                drawLine(
                    color = primaryColor,
                    start = Offset(totalProgressPx, centerY - barHalfHeight),
                    end = Offset(totalProgressPx, centerY + barHalfHeight),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
