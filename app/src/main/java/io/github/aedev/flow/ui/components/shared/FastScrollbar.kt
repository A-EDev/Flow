package io.github.aedev.flow.ui.components.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val TrackWidth = 24.dp
private val ThumbWidth = 4.dp
private val ThumbHeight = 52.dp
private val BubbleHorizontalPadding = 12.dp
private val BubbleVerticalPadding = 8.dp
private val BubbleSpacing = 8.dp
private const val MIN_ITEMS_FOR_SCROLLBAR = 40
private const val HIDE_DELAY_MS = 1_400L

@Composable
fun FastScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    minItems: Int = MIN_ITEMS_FOR_SCROLLBAR,
    bubble: @Composable (() -> Unit)? = null,
) {
    val enabled by remember(state, minItems) {
        derivedStateOf { state.layoutInfo.totalItemsCount >= minItems }
    }
    if (!enabled) return

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

    val thumbHeightPx = with(LocalDensity.current) { ThumbHeight.roundToPx() }
    var trackHeightPx by remember { mutableIntStateOf(0) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BubbleSpacing),
        ) {
            if (dragging && bubble != null) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(
                        modifier =
                            Modifier.padding(
                                horizontal = BubbleHorizontalPadding,
                                vertical = BubbleVerticalPadding,
                            ),
                    ) {
                        bubble()
                    }
                }
            }

            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .width(TrackWidth)
                        .onSizeChanged { trackHeightPx = it.height },
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset {
                                val travel = (trackHeightPx - thumbHeightPx).coerceAtLeast(0)
                                IntOffset(0, (travel * scrollFraction(state)).roundToInt())
                            }.width(TrackWidth)
                            .height(ThumbHeight)
                            .draggable(
                                orientation = Orientation.Vertical,
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
                                },
                                onDragStopped = { dragging = false },
                            ),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .width(ThumbWidth)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

private fun scrollFraction(state: LazyListState): Float {
    val info = state.layoutInfo
    val total = info.totalItemsCount
    if (total <= 0) return 0f
    val onScreen = info.visibleItemsInfo.size.coerceAtLeast(1)
    val lastStart = (total - onScreen).coerceAtLeast(1)
    return (state.firstVisibleItemIndex.toFloat() / lastStart).coerceIn(0f, 1f)
}
