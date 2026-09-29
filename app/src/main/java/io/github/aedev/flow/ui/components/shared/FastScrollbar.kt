package io.github.aedev.flow.ui.components.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlin.math.roundToInt

private val TouchTargetWidth = 24.dp
private val ThumbWidth = 4.dp
private val ThumbWidthActive = 10.dp
private val ThumbHeight = 52.dp
private val BubbleHorizontalPadding = 14.dp
private val BubbleVerticalPadding = 8.dp
private val BubbleSpacing = 10.dp
private const val MIN_ITEMS_FOR_SCROLLBAR = 40
private const val HIDE_DELAY_MS = 1_400L
private const val BUBBLE_INITIAL_SCALE = 0.7f

@Composable
fun FastScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    minItems: Int = MIN_ITEMS_FOR_SCROLLBAR,
    tickKey: (() -> Any?)? = null,
    bubble: @Composable (() -> Unit)? = null,
) {
    val enabled by remember(state, minItems) {
        derivedStateOf { state.layoutInfo.totalItemsCount >= minItems }
    }
    if (!enabled) return

    val haptics = LocalHapticFeedback.current
    val motion = MaterialTheme.motionScheme

    var dragging by remember { mutableStateOf(false) }
    var visible by remember { mutableStateOf(false) }
    val active by remember { derivedStateOf { state.isScrollInProgress || dragging } }

    LaunchedEffect(active) {
        if (active) {
            visible = true
        } else {
            delay(HIDE_DELAY_MS)
            visible = false
        }
    }

    if (dragging && tickKey != null) {
        LaunchedEffect(Unit) {
            snapshotFlow { tickKey() }
                .distinctUntilChanged()
                .drop(1)
                .collect { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
        }
    }

    val alpha =
        animateFloatAsState(
            targetValue = if (visible) 1f else 0f,
            animationSpec = motion.defaultEffectsSpec(),
            label = "fastScrollbarAlpha",
        )
    val thumbWidth by animateDpAsState(
        targetValue = if (dragging) ThumbWidthActive else ThumbWidth,
        animationSpec = motion.fastSpatialSpec(),
        label = "fastScrollbarThumbWidth",
    )

    val thumbHeightPx = with(LocalDensity.current) { ThumbHeight.roundToPx() }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    Box(modifier = modifier.onSizeChanged { trackHeightPx = it.height }) {
        Row(
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(0)
                        IntOffset(0, (travel * scrollFraction(state)).roundToInt())
                    }.graphicsLayer { this.alpha = alpha.value },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BubbleSpacing),
        ) {
            AnimatedVisibility(
                visible = dragging && bubble != null,
                enter =
                    scaleIn(
                        animationSpec = motion.defaultSpatialSpec(),
                        initialScale = BUBBLE_INITIAL_SCALE,
                        transformOrigin = TransformOrigin(1f, 0.5f),
                    ) + fadeIn(animationSpec = motion.fastEffectsSpec()),
                exit =
                    scaleOut(
                        animationSpec = motion.fastSpatialSpec(),
                        targetScale = BUBBLE_INITIAL_SCALE,
                        transformOrigin = TransformOrigin(1f, 0.5f),
                    ) + fadeOut(animationSpec = motion.fastEffectsSpec()),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shadowElevation = BubbleElevation,
                ) {
                    Box(
                        modifier =
                            Modifier.padding(
                                horizontal = BubbleHorizontalPadding,
                                vertical = BubbleVerticalPadding,
                            ),
                    ) {
                        bubble?.invoke()
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .width(TouchTargetWidth)
                        .height(ThumbHeight)
                        .draggable(
                            orientation = Orientation.Vertical,
                            enabled = visible,
                            state =
                                rememberDraggableState { delta ->
                                    val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(1)
                                    dragFraction = (dragFraction + delta / travel).coerceIn(0f, 1f)
                                    val lastIndex = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                    state.requestScrollToItem((lastIndex * dragFraction).roundToInt())
                                },
                            onDragStarted = {
                                dragFraction = scrollFraction(state)
                                dragging = true
                                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                            },
                            onDragStopped = {
                                dragging = false
                                haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
                            },
                        ),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    modifier =
                        Modifier
                            .width(thumbWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

private val BubbleElevation = 3.dp

private fun scrollFraction(state: LazyListState): Float {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total <= 0) return 0f
    val onScreen = info.visibleItemsInfo.size.coerceAtLeast(1)
    val lastStart = (total - onScreen).coerceAtLeast(1)
    return (state.firstVisibleItemIndex.toFloat() / lastStart).coerceIn(0f, 1f)
}
