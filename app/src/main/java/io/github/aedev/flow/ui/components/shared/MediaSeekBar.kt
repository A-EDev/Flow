package io.github.aedev.flow.ui.components.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.aedev.flow.data.model.SponsorBlockSegment
import org.schabi.newpipe.extractor.stream.StreamSegment

private val EdgeAlignedHeight = 14.dp
private val ExpandedRowHeight = 32.dp

/** Colours the track needs, resolved once in composition so the draw lambda stays theme-free. */
internal data class SeekTrackColors(
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
 * - Edge-aligned (the thin strip under the video when controls are hidden) is
 *   [EdgeAlignedSeekbar], which cannot be a [Slider] — its documentation records why.
 */
@Composable
fun MediaSeekBar(
    /**
     * Progress provider rather than a value: the playhead is written several times a second, and
     * reading it at the call site subscribed the whole player-controls overlay to that tick.
     * Invoking it here confines the recomposition to this component.
     */
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
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
