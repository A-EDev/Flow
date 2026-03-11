package com.flow.youtube.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.DraggableState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val DISMISSED_ANCHOR = 0
private const val COLLAPSED_ANCHOR = 1
private const val EXPANDED_ANCHOR = 2

@Stable
class MusicPlayerSheetState(
    private val draggableState: DraggableState,
    private val coroutineScope: CoroutineScope,
    private val animatable: Animatable<Dp, AnimationVector1D>,
    private val onAnchorChanged: (Int) -> Unit,
    val collapsedBound: Dp,
) : DraggableState by draggableState {

    val dismissedBound: Dp get() = animatable.lowerBound!!
    val expandedBound: Dp get() = animatable.upperBound!!

    val value by animatable.asState()

    val isDismissed: Boolean by derivedStateOf { value == dismissedBound }
    val isCollapsed: Boolean by derivedStateOf { value == collapsedBound }
    val isExpanded: Boolean by derivedStateOf { value == expandedBound }

    val progress: Float by derivedStateOf {
        if (expandedBound == collapsedBound) 1f
        else ((animatable.value - collapsedBound) / (expandedBound - collapsedBound))
            .coerceIn(0f, 1f)
    }

    fun collapse() {
        onAnchorChanged(COLLAPSED_ANCHOR)
        coroutineScope.launch {
            animatable.animateTo(collapsedBound, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    fun expand() {
        onAnchorChanged(EXPANDED_ANCHOR)
        coroutineScope.launch {
            animatable.animateTo(expandedBound, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    fun dismiss() {
        onAnchorChanged(DISMISSED_ANCHOR)
        coroutineScope.launch {
            animatable.animateTo(dismissedBound, spring(stiffness = Spring.StiffnessMediumLow))
        }
    }

    fun snapTo(value: Dp) {
        coroutineScope.launch { animatable.snapTo(value) }
    }

    fun performFling(velocity: Float, onDismiss: (() -> Unit)? = null) {
        when {
            velocity > 300f -> expand()
            velocity < -300f -> {
                if (animatable.value < collapsedBound && onDismiss != null) {
                    dismiss(); onDismiss()
                } else {
                    collapse()
                }
            }
            else -> {
                val mid = collapsedBound + (expandedBound - collapsedBound) * 0.5f
                if (animatable.value >= mid) expand() else collapse()
            }
        }
    }

    /** Nested scroll connection: drag-to-collapse when content is scrolled to top */
    val nestedScrollConnection: NestedScrollConnection get() = object : NestedScrollConnection {
        var isTopReached = false

        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (isExpanded && available.y < 0) isTopReached = false
            return if (isTopReached && available.y < 0 && source == NestedScrollSource.UserInput) {
                dispatchRawDelta(available.y); available
            } else Offset.Zero
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (!isTopReached) isTopReached = consumed.y == 0f && available.y > 0
            return if (isTopReached && source == NestedScrollSource.UserInput) {
                dispatchRawDelta(available.y); available
            } else Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            return if (isTopReached) {
                performFling(-available.y); available
            } else Velocity.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            isTopReached = false; return Velocity.Zero
        }
    }
}

@Composable
fun rememberMusicPlayerSheetState(
    expandedBound: Dp,
    collapsedBound: Dp,
    dismissedBound: Dp = 0.dp,
    initialAnchor: Int = DISMISSED_ANCHOR,
): MusicPlayerSheetState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var previousAnchor by rememberSaveable { mutableStateOf(initialAnchor) }
    val animatable = remember { Animatable(0.dp, Dp.VectorConverter) }

    return remember(dismissedBound, expandedBound, collapsedBound, scope) {
        val initialValue = when (previousAnchor) {
            EXPANDED_ANCHOR -> expandedBound
            COLLAPSED_ANCHOR -> collapsedBound
            else -> dismissedBound
        }
        animatable.updateBounds(
            dismissedBound.coerceAtMost(expandedBound),
            expandedBound
        )
        scope.launch { animatable.snapTo(initialValue) }

        MusicPlayerSheetState(
            draggableState = DraggableState { delta ->
                scope.launch {
                    animatable.snapTo(
                        (animatable.value - with(density) { delta.toDp() })
                            .coerceIn(dismissedBound.coerceAtMost(expandedBound), expandedBound)
                    )
                }
            },
            coroutineScope = scope,
            animatable = animatable,
            onAnchorChanged = { previousAnchor = it },
            collapsedBound = collapsedBound,
        )
    }
}

/**
 * A bottom-sheet overlay for the music player.
 *
 * – collapsed: only [collapsedContent] is visible (mini-player bar)
 * – expanded : [expandedContent] fills the screen
 * – dismissed: entirely off-screen
 *
 * Drag up to expand, drag down to collapse/dismiss.
 *
 * @param bottomPadding Extra bottom gap (e.g. nav bar + bottom nav height) that positions the
 *   collapsed mini player above the navigation bar. Animated by the caller so the mini player
 *   tracks nav-bar show/hide smoothly.
 */
@Composable
fun MusicPlayerBottomSheet(
    state: MusicPlayerSheetState,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onDismiss: (() -> Unit)? = null,
    collapsedContent: @Composable BoxScope.() -> Unit,
    expandedContent: @Composable BoxScope.() -> Unit,
) {
    if (state.isDismissed) return

    if (!state.isCollapsed) {
        BackHandler(onBack = state::collapse)
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
       val lift = bottomPadding * (1f - state.progress)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(state.value.coerceAtLeast(0.dp))
                .offset { IntOffset(x = 0, y = -lift.roundToPx()) }
                .clip(RectangleShape)
        ) {
            // ── Full player (expanded content) ──────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(state.nestedScrollConnection)
                    .graphicsLayer {
                        alpha = ((state.progress - 0.15f) * 4f).coerceIn(0f, 1f)
                    },
                content = expandedContent
            )

            // ── Mini player with drag gesture (collapsed content) ────────────
            if (!state.isExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(state.collapsedBound)
                        .pointerInput(state) {
                            val velocityTracker = VelocityTracker()
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    velocityTracker.addPointerInputChange(change)
                                    state.dispatchRawDelta(dragAmount)
                                },
                                onDragCancel = {
                                    velocityTracker.resetTracking()
                                    state.collapse()
                                },
                                onDragEnd = {
                                    val velocity = -velocityTracker.calculateVelocity().y
                                    velocityTracker.resetTracking()
                                    state.performFling(velocity, onDismiss)
                                }
                            )
                        }
                        .graphicsLayer {
                            alpha = (1f - state.progress * 4f).coerceIn(0f, 1f)
                        },
                    content = collapsedContent
                )
            }
        }
    }
}
