package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import io.github.aedev.flow.data.model.SponsorBlockSegment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import org.schabi.newpipe.extractor.stream.StreamSegment
import kotlin.math.abs
import kotlin.math.roundToInt

// Custom seekbar drawing buffer, SponsorBlock segments and chapter gaps over the progress track.
@Composable
fun SeekbarWithPreview(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    chapters: List<StreamSegment> = emptyList(),
    sponsorSegments: List<SponsorBlockSegment> = emptyList(),
    duration: Long = 0L,
    bufferedValue: Float = 0f,
    edgeAligned: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
    val bufferedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f)
    val thumbFillColor = MaterialTheme.colorScheme.surface
    val thumbStateLayerColor = primaryColor.copy(alpha = 0.18f)

    var edgePointerActive by remember { mutableStateOf(false) }

    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged || edgePointerActive

    // Internal value to keep the thumb following the finger smoothly
    var internalValue by remember { mutableFloatStateOf(value) }

    // Sync internal value with external value when not interacting
    LaunchedEffect(value) {
        if (!isInteracting) {
            internalValue = value
        }
    }

    val trackHeight by animateDpAsState(
        targetValue = if (isInteracting) 10.dp else 5.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "trackHeight"
    )

    val thumbScale by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "thumbScale"
    )

    Box(
        modifier = modifier
            .fillMaxWidth(),
        contentAlignment = if (edgeAligned) Alignment.BottomCenter else Alignment.TopStart
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (edgeAligned) 14.dp else 32.dp),
            contentAlignment = if (edgeAligned) Alignment.BottomCenter else Alignment.Center
        ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (edgeAligned) 14.dp else trackHeight)
        ) {
            val trackHeightPx = trackHeight.toPx()
            val width = size.width
            val trackTop = if (edgeAligned) size.height - trackHeightPx else 0f
            val trackBottom = trackTop + trackHeightPx
            val trackCenterY = trackTop + trackHeightPx / 2f
            val capRadius = CornerRadius(trackHeightPx / 2f)

            val gapWidth = if (isInteracting) 5.dp.toPx() else 4.dp.toPx()
            val boundaries = if (chapters.isNotEmpty() && duration > 0) {
                chapters.asSequence()
                    .map { it.startTimeSeconds }
                    .filter { it > 0 }
                    .map { (it * 1000f) / duration.toFloat() }
                    .filter { it in 0f..1f }
                    .map { it * width }
                    .sorted()
                    .toList()
            } else {
                emptyList()
            }

            val trackPath = Path().apply {
                var segStart = 0f
                for (boundary in boundaries) {
                    val segEnd = boundary - gapWidth / 2f
                    if (segEnd > segStart) {
                        addRoundRect(RoundRect(Rect(segStart, trackTop, segEnd, trackBottom), capRadius))
                    }
                    segStart = (boundary + gapWidth / 2f).coerceAtMost(width)
                }
                if (width > segStart) {
                    addRoundRect(RoundRect(Rect(segStart, trackTop, width, trackBottom), capRadius))
                }
            }

            clipPath(trackPath) {
                drawRect(
                    color = trackColor,
                    topLeft = Offset(0f, trackTop),
                    size = Size(width, trackHeightPx)
                )

                if (bufferedValue > 0f) {
                    drawRect(
                        color = bufferedTrackColor,
                        topLeft = Offset(0f, trackTop),
                        size = Size(width * bufferedValue.coerceIn(0f, 1f), trackHeightPx)
                    )
                }

                // Active track (progress)
                drawRect(
                    color = primaryColor,
                    topLeft = Offset(0f, trackTop),
                    size = Size(width * internalValue, trackHeightPx)
                )

                // SponsorBlock segments above progress so they remain visible after playback passes them.
                if (duration > 0) {
                    sponsorSegments.forEach { segment ->
                        val startRatio = (segment.startTime.toFloat() * 1000f / duration.toFloat()).coerceIn(0f, 1f)
                        val endRatio = (segment.endTime.toFloat() * 1000f / duration.toFloat()).coerceIn(0f, 1f)

                        if (endRatio > startRatio) {
                            val startX = startRatio * width
                            val segWidth = (endRatio * width) - startX

                            val segmentColor = when (segment.category) {
                                "sponsor" -> Color(0xFF00D100) // Green
                                "selfpromo" -> Color(0xFFFFFF00) // Yellow
                                "interaction" -> Color(0xFFFF00FF) // Magenta
                                "intro" -> Color(0xFF00FFFF) // Cyan
                                "outro" -> Color(0xFF00FFFF) // Cyan
                                "music_offtopic" -> Color(0xFFFF8000) // Orange
                                else -> Color(0xFF00D100)
                            }.copy(alpha = 0.78f)

                            drawRect(
                                color = segmentColor,
                                topLeft = Offset(startX, trackTop),
                                size = Size(segWidth, trackHeightPx)
                            )
                        }
                    }
                }
            }

            if (edgeAligned && thumbScale > 0f) {
                val thumbRadius = 7.dp.toPx() * thumbScale
                val thumbX = if (width > thumbRadius * 2f) {
                    (width * internalValue).coerceIn(thumbRadius, width - thumbRadius)
                } else {
                    width * internalValue
                }
                drawCircle(
                    color = primaryColor.copy(alpha = 0.24f),
                    radius = thumbRadius + 8.dp.toPx(),
                    center = Offset(thumbX, trackCenterY)
                )
                drawCircle(
                    color = Color.White,
                    radius = thumbRadius,
                    center = Offset(thumbX, trackCenterY)
                )
                drawCircle(
                    color = primaryColor,
                    radius = thumbRadius,
                    center = Offset(thumbX, trackCenterY),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())
                )
            }
        }

        // The actual slider
        @OptIn(ExperimentalMaterial3Api::class)
        Slider(
            value = internalValue,
            onValueChange = { newValue ->
                internalValue = newValue
                onValueChange(newValue)
            },
            onValueChangeFinished = {
                onValueChangeFinished?.invoke()
            },
            modifier = if (edgeAligned) Modifier.fillMaxSize() else Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = thumbFillColor,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent,
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = Color.Transparent,
                disabledActiveTrackColor = Color.Transparent,
                disabledInactiveTrackColor = Color.Transparent,
                disabledActiveTickColor = Color.Transparent,
                disabledInactiveTickColor = Color.Transparent
            ),
            thumb = {
                if (edgeAligned) {
                    Spacer(modifier = Modifier.size(0.dp))
                } else {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .scale(thumbScale),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isInteracting) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(thumbStateLayerColor, CircleShape)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(if (isInteracting) 16.dp else 14.dp)
                                .background(thumbFillColor, CircleShape)
                                .border(3.dp, primaryColor, CircleShape)
                        )
                    }
                }
            }
        )

        if (edgeAligned) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(enabled, valueRange, steps) {
                        if (!enabled) return@pointerInput

                        fun valueForX(x: Float): Float {
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            val fraction = (x / width).coerceIn(0f, 1f)
                            val steppedFraction = if (steps > 0) {
                                val intervals = steps + 1
                                (fraction * intervals).roundToInt()
                                    .coerceIn(0, intervals)
                                    .toFloat() / intervals.toFloat()
                            } else {
                                fraction
                            }
                            return valueRange.start +
                                (valueRange.endInclusive - valueRange.start) * steppedFraction
                        }

                        fun updateValueFromX(x: Float) {
                            val newValue = valueForX(x)
                            if (abs(newValue - internalValue) > 0.0001f) {
                                internalValue = newValue
                                onValueChange(newValue)
                            }
                        }

                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            edgePointerActive = true
                            down.consume()
                            updateValueFromX(down.position.x)

                            try {
                                var activePointerId = down.id
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == activePointerId }
                                        ?: event.changes.firstOrNull { it.pressed }
                                        ?: break

                                    activePointerId = change.id
                                    if (!change.pressed) {
                                        change.consume()
                                        break
                                    }

                                    if (change.positionChange() != Offset.Zero) {
                                        updateValueFromX(change.position.x)
                                    }
                                    change.consume()
                                }
                            } finally {
                                edgePointerActive = false
                                onValueChangeFinished?.invoke()
                            }
                        }
                    }
            )
        }
        }
    }
}
