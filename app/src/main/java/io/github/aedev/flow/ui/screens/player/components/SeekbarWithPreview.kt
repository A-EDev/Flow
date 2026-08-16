package io.github.aedev.flow.ui.screens.player.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import io.github.aedev.flow.data.model.SponsorBlockSegment
import io.github.aedev.flow.ui.theme.SPONSOR_BLOCK_SEGMENT_ALPHA
import io.github.aedev.flow.ui.theme.defaultSponsorBlockColor
import org.schabi.newpipe.extractor.stream.StreamSegment
import kotlin.math.abs
import kotlin.math.roundToInt

private val RestTrackHeight = 5.dp
private val ActiveTrackHeight = 10.dp
private val EdgeAlignedHeight = 14.dp
private val ExpandedRowHeight = 32.dp

/** Colours the track needs, resolved once in composition so the draw lambda stays theme-free. */
private data class SeekTrackColors(
    val track: Color,
    val buffered: Color,
    val active: Color,
    val playhead: Color,
)

/**
 * Seek bar with buffered progress, chapter gaps and SponsorBlock segments painted over the track.
 *
 * Two shapes, and they are built differently on purpose:
 *
 * - Expanded (in the controls overlay) is a real [Slider]. Its custom look lives in the `track`
 *   slot, so Material owns dragging, press-to-position, keyboard and accessibility.
 * - Edge-aligned (the thin strip under the video when controls are hidden) cannot be a [Slider]:
 *   `SliderImpl` applies `minimumInteractiveComponentSize()`, so it would claim 48dp of touch
 *   height against a bar that sits flush with the video and is 14dp tall. It draws the same track
 *   and runs its own pointer handling at its real size.
 */
@Composable
fun SeekbarWithPreview(
    /**
     * Progress provider rather than a value: the playhead is written several times a second, and
     * reading it at the call site subscribed the whole player-controls overlay to that tick.
     * Invoking it here confines the recomposition to this component.
     */
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    chapters: List<StreamSegment> = emptyList(),
    sponsorSegments: List<SponsorBlockSegment> = emptyList(),
    /**
     * Per-category colour overrides chosen by the user in SponsorBlock settings. Categories absent
     * from the map fall back to the shared defaults.
     */
    sponsorColors: Map<String, Color> = emptyMap(),
    duration: Long = 0L,
    bufferedValue: Float = 0f,
    edgeAligned: Boolean = false,
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val thumbFillColor = MaterialTheme.colorScheme.surface
    val trackColors =
        SeekTrackColors(
            track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
            buffered = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.56f),
            active = primaryColor,
            playhead = thumbFillColor,
        )
    val thumbStateLayerColor = primaryColor.copy(alpha = 0.18f)

    var edgePointerActive by remember { mutableStateOf(false) }

    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged || edgePointerActive

    val progress = value()

    // While the thumb is held the component shows the dragged position; otherwise it follows the
    // playhead directly. `isScrubbing` is only ever set in the same handler that writes
    // `scrubValue`, so the displayed value can never be a stale leftover from a previous drag.
    var scrubValue by remember { mutableFloatStateOf(progress) }
    var isScrubbing by remember { mutableStateOf(false) }
    val displayValue = if (isScrubbing) scrubValue else progress

    // Animated in the draw phase rather than through Modifier.height: the track used to grow by
    // animating a layout constraint, which forced a layout pass on every frame of the touch
    // response. The Canvas is a fixed box and only the painted band changes size.
    val trackExpansion = remember { Animatable(0f) }
    val thumbScale = remember { Animatable(0f) }

    LaunchedEffect(isInteracting) {
        trackExpansion.animateTo(
            targetValue = if (isInteracting) 1f else 0f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
        )
    }

    LaunchedEffect(isInteracting) {
        thumbScale.animateTo(
            targetValue = if (isInteracting) 1f else 0f,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
        )
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = if (edgeAligned) Alignment.BottomCenter else Alignment.TopStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(if (edgeAligned) EdgeAlignedHeight else ExpandedRowHeight),
            contentAlignment = if (edgeAligned) Alignment.BottomCenter else Alignment.Center,
        ) {
            if (edgeAligned) {
                EdgeAlignedSeekbar(
                    displayValue = displayValue,
                    enabled = enabled,
                    valueRange = valueRange,
                    steps = steps,
                    chapters = chapters,
                    sponsorSegments = sponsorSegments,
                    sponsorColors = sponsorColors,
                    duration = duration,
                    bufferedValue = bufferedValue,
                    colors = trackColors,
                    expansionProvider = { trackExpansion.value },
                    thumbScaleProvider = { thumbScale.value },
                    onScrub = { newValue ->
                        scrubValue = newValue
                        isScrubbing = true
                        onValueChange(newValue)
                    },
                    onPointerActiveChange = { active ->
                        edgePointerActive = active
                        if (!active) {
                            isScrubbing = false
                            onValueChangeFinished?.invoke()
                        }
                    },
                )
            } else {
                @OptIn(ExperimentalMaterial3Api::class)
                Slider(
                    value = displayValue,
                    onValueChange = { newValue ->
                        scrubValue = newValue
                        isScrubbing = true
                        onValueChange(newValue)
                    },
                    onValueChangeFinished = {
                        isScrubbing = false
                        onValueChangeFinished?.invoke()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    valueRange = valueRange,
                    steps = steps,
                    interactionSource = interactionSource,
                    colors = SliderDefaults.colors(thumbColor = thumbFillColor),
                    track = { state ->
                        Canvas(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(ActiveTrackHeight),
                        ) {
                            drawSeekTrack(
                                fraction = state.coercedValueAsFraction,
                                expansion = trackExpansion.value,
                                chapters = chapters,
                                sponsorSegments = sponsorSegments,
                                sponsorColors = sponsorColors,
                                duration = duration,
                                bufferedValue = bufferedValue,
                                colors = trackColors,
                                bottomAligned = false,
                            )
                        }
                    },
                    thumb = {
                        Box(
                            modifier =
                                Modifier
                                    .size(16.dp)
                                    .graphicsLayer {
                                        val scale = thumbScale.value
                                        scaleX = scale
                                        scaleY = scale
                                    }.drawBehind {
                                        if (isInteracting) {
                                            drawCircle(
                                                color = thumbStateLayerColor,
                                                radius = 20.dp.toPx(),
                                            )
                                        }
                                    },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .background(thumbFillColor, CircleShape)
                                        .border(3.dp, primaryColor, CircleShape),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EdgeAlignedSeekbar(
    displayValue: Float,
    enabled: Boolean,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    chapters: List<StreamSegment>,
    sponsorSegments: List<SponsorBlockSegment>,
    sponsorColors: Map<String, Color>,
    duration: Long,
    bufferedValue: Float,
    colors: SeekTrackColors,
    expansionProvider: () -> Float,
    thumbScaleProvider: () -> Float,
    onScrub: (Float) -> Unit,
    onPointerActiveChange: (Boolean) -> Unit,
) {
    val thumbFillColor = colors.playhead
    val accentColor = colors.active

    Canvas(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(enabled, valueRange, steps) {
                    if (!enabled) return@pointerInput

                    fun valueForX(x: Float): Float {
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = (x / width).coerceIn(0f, 1f)
                        val steppedFraction =
                            if (steps > 0) {
                                val intervals = steps + 1
                                (fraction * intervals)
                                    .roundToInt()
                                    .coerceIn(0, intervals)
                                    .toFloat() / intervals.toFloat()
                            } else {
                                fraction
                            }
                        return valueRange.start +
                            (valueRange.endInclusive - valueRange.start) * steppedFraction
                    }

                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        onPointerActiveChange(true)
                        down.consume()
                        onScrub(valueForX(down.position.x))

                        try {
                            var activePointerId = down.id
                            var lastValue = valueForX(down.position.x)
                            while (true) {
                                val event = awaitPointerEvent()
                                val change =
                                    event.changes.firstOrNull { it.id == activePointerId }
                                        ?: event.changes.firstOrNull { it.pressed }
                                        ?: break

                                activePointerId = change.id
                                if (!change.pressed) {
                                    change.consume()
                                    break
                                }

                                if (change.positionChange() != Offset.Zero) {
                                    val newValue = valueForX(change.position.x)
                                    if (abs(newValue - lastValue) > 0.0001f) {
                                        lastValue = newValue
                                        onScrub(newValue)
                                    }
                                }
                                change.consume()
                            }
                        } finally {
                            onPointerActiveChange(false)
                        }
                    }
                },
    ) {
        val expansion = expansionProvider()
        drawSeekTrack(
            fraction = displayValue,
            expansion = expansion,
            chapters = chapters,
            sponsorSegments = sponsorSegments,
            sponsorColors = sponsorColors,
            duration = duration,
            bufferedValue = bufferedValue,
            colors = colors,
            bottomAligned = true,
        )

        val scale = thumbScaleProvider()
        if (scale > 0f) {
            val trackHeightPx = lerp(RestTrackHeight.toPx(), ActiveTrackHeight.toPx(), expansion)
            val trackCenterY = size.height - trackHeightPx / 2f
            val width = size.width
            val thumbRadius = 7.dp.toPx() * scale
            val thumbX =
                if (width > thumbRadius * 2f) {
                    (width * displayValue).coerceIn(thumbRadius, width - thumbRadius)
                } else {
                    width * displayValue
                }
            drawCircle(
                color = accentColor.copy(alpha = 0.24f),
                radius = thumbRadius + 8.dp.toPx(),
                center = Offset(thumbX, trackCenterY),
            )
            drawCircle(
                color = thumbFillColor,
                radius = thumbRadius,
                center = Offset(thumbX, trackCenterY),
            )
            drawCircle(
                color = accentColor,
                radius = thumbRadius,
                center = Offset(thumbX, trackCenterY),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
}

/**
 * Paints the track: base, buffered, progress, chapter gaps and SponsorBlock segments.
 *
 * Shared by both shapes so their appearance cannot drift apart — the earlier version drew the same
 * thing twice, once in a sibling Canvas and once for the locked variant.
 */
private fun DrawScope.drawSeekTrack(
    fraction: Float,
    expansion: Float,
    chapters: List<StreamSegment>,
    sponsorSegments: List<SponsorBlockSegment>,
    sponsorColors: Map<String, Color>,
    duration: Long,
    bufferedValue: Float,
    colors: SeekTrackColors,
    bottomAligned: Boolean,
) {
    val trackHeightPx = lerp(RestTrackHeight.toPx(), ActiveTrackHeight.toPx(), expansion)
    val width = size.width
    val trackTop =
        if (bottomAligned) {
            size.height - trackHeightPx
        } else {
            (size.height - trackHeightPx) / 2f
        }
    val trackBottom = trackTop + trackHeightPx
    val capRadius = CornerRadius(trackHeightPx / 2f)

    val gapWidth = lerp(2.dp.toPx(), 3.dp.toPx(), expansion)
    val boundaries =
        if (chapters.isNotEmpty() && duration > 0) {
            chapters
                .asSequence()
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

    val segments =
        buildList {
            var segStart = 0f
            for (boundary in boundaries) {
                val segEnd = boundary - gapWidth / 2f
                if (segEnd > segStart) {
                    add(segStart to segEnd)
                }
                segStart = (boundary + gapWidth / 2f).coerceAtMost(width)
            }
            if (width > segStart) {
                add(segStart to width)
            }
        }

    val trackPath =
        Path().apply {
            segments.forEachIndexed { index, (segStart, segEnd) ->
                val startRadius = if (index == 0) capRadius else CornerRadius.Zero
                val endRadius = if (index == segments.lastIndex) capRadius else CornerRadius.Zero
                addRoundRect(
                    RoundRect(
                        rect = Rect(segStart, trackTop, segEnd, trackBottom),
                        topLeft = startRadius,
                        topRight = endRadius,
                        bottomRight = endRadius,
                        bottomLeft = startRadius,
                    ),
                )
            }
        }

    clipPath(trackPath) {
        drawRect(
            color = colors.track,
            topLeft = Offset(0f, trackTop),
            size = Size(width, trackHeightPx),
        )

        if (bufferedValue > 0f) {
            drawRect(
                color = colors.buffered,
                topLeft = Offset(0f, trackTop),
                size = Size(width * bufferedValue.coerceIn(0f, 1f), trackHeightPx),
            )
        }

        drawRect(
            color = colors.active,
            topLeft = Offset(0f, trackTop),
            size = Size(width * fraction, trackHeightPx),
        )

        // Drawn above progress so a segment stays visible after playback passes it.
        if (duration > 0) {
            sponsorSegments.forEach { segment ->
                val startRatio = (segment.startTime * 1000f / duration.toFloat()).coerceIn(0f, 1f)
                val endRatio = (segment.endTime * 1000f / duration.toFloat()).coerceIn(0f, 1f)

                if (endRatio > startRatio) {
                    val startX = startRatio * width
                    val segWidth = (endRatio * width) - startX
                    val segmentColor =
                        (
                            sponsorColors[segment.category]
                                ?: defaultSponsorBlockColor(segment.category)
                        ).copy(alpha = SPONSOR_BLOCK_SEGMENT_ALPHA)

                    drawRect(
                        color = segmentColor,
                        topLeft = Offset(startX, trackTop),
                        size = Size(segWidth, trackHeightPx),
                    )
                }
            }

            val currentTimeSeconds = fraction.coerceIn(0f, 1f) * duration / 1000f
            val isInsideSponsorSegment =
                sponsorSegments.any { segment ->
                    currentTimeSeconds >= segment.startTime && currentTimeSeconds < segment.endTime
                }
            if (isInsideSponsorSegment) {
                val playheadX = width * fraction.coerceIn(0f, 1f)
                val outerWidth = minOf(4.dp.toPx(), width)
                val innerWidth = minOf(2.dp.toPx(), width)
                drawRect(
                    color = colors.playhead,
                    topLeft =
                        Offset(
                            x = (playheadX - outerWidth / 2f).coerceIn(0f, width - outerWidth),
                            y = trackTop,
                        ),
                    size = Size(outerWidth, trackHeightPx),
                )
                drawRect(
                    color = colors.active,
                    topLeft =
                        Offset(
                            x = (playheadX - innerWidth / 2f).coerceIn(0f, width - innerWidth),
                            y = trackTop,
                        ),
                    size = Size(innerWidth, trackHeightPx),
                )
            }
        }
    }
}
