package io.github.aedev.flow.ui.components.musicplayer.queue

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The queue sheet that is pulled up over the compact player. Its top edge rests [hiddenY] px below
 * the player while closed and at 0 when open; [fraction] is how far open it is.
 */
@Stable
internal class QueuePullUpState internal constructor(
    private val scope: CoroutineScope,
    private val enabledState: State<Boolean>,
    private val hiddenYState: State<Float>,
    private val densityState: State<Density>,
) {
    internal val offsetY = Animatable(hiddenYState.value)

    var isShown by mutableStateOf(false)
        internal set

    val hiddenY: Float get() = hiddenYState.value

    val isActive: Boolean get() = enabledState.value && isShown

    fun offset(): Float = if (!isActive) hiddenY else offsetY.value.coerceIn(0f, hiddenY)

    fun fraction(): Float = if (hiddenY != 0f) (1f - offset() / hiddenY).coerceIn(0f, 1f) else 0f

    fun open() {
        if (!enabledState.value) return
        isShown = true
        scope.launch { animateTo(0f) }
    }

    fun close() {
        scope.launch { animateTo(hiddenY) }
    }

    internal fun claim() {
        isShown = true
    }

    internal fun dragBy(delta: Float) {
        scope.launch { offsetY.snapTo((offsetY.value + delta).coerceIn(0f, hiddenY)) }
    }

    internal suspend fun reset() {
        isShown = false
        offsetY.snapTo(hiddenY)
    }

    internal suspend fun animateTo(
        target: Float,
        initialVelocity: Float = 0f,
    ) {
        if (target < hiddenY && enabledState.value) isShown = true
        offsetY.stop()
        offsetY.animateTo(
            targetValue = target.coerceIn(0f, hiddenY),
            initialVelocity = initialVelocity,
            animationSpec =
                spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
        )
        if (target >= hiddenY) {
            offsetY.snapTo(hiddenY)
            isShown = false
        }
    }

    internal suspend fun settle(
        velocity: Float,
        totalDrag: Float = 0f,
    ) {
        val distance = hiddenY
        val progress = if (distance > 0f) (offsetY.value / distance).coerceIn(0f, 1f) else 1f
        val density = densityState.value
        val dragThresholdPx =
            (distance * 0.05f).coerceIn(
                with(density) { 14.dp.toPx() },
                with(density) { 56.dp.toPx() },
            )
        val target =
            when {
                velocity < -520f -> 0f
                velocity > 450f -> hiddenY
                totalDrag < -dragThresholdPx -> 0f
                totalDrag > dragThresholdPx -> hiddenY
                progress < 0.5f -> 0f
                else -> hiddenY
            }
        animateTo(target, velocity)
    }

    internal fun launchSettle(
        velocity: Float,
        totalDrag: Float,
    ) {
        scope.launch { settle(velocity, totalDrag) }
    }
}

@Composable
internal fun rememberQueuePullUpState(
    enabled: Boolean,
    hiddenY: Float,
): QueuePullUpState {
    val scope = rememberCoroutineScope()
    val enabledState = rememberUpdatedState(enabled)
    val hiddenYState = rememberUpdatedState(hiddenY.coerceAtLeast(0f))
    val densityState = rememberUpdatedState(LocalDensity.current)
    val state = remember { QueuePullUpState(scope, enabledState, hiddenYState, densityState) }
    LaunchedEffect(enabled, hiddenYState.value) {
        if (!enabled) state.reset()
    }
    LaunchedEffect(hiddenYState.value) {
        val bottom = hiddenYState.value
        state.offsetY.updateBounds(lowerBound = 0f, upperBound = bottom)
        state.offsetY.snapTo(if (state.isShown) state.offsetY.value.coerceIn(0f, bottom) else bottom)
    }
    return state
}

/**
 * Claims only clearly upward drags on the player (the queue pull-up); anything else stays
 * unconsumed so the sheet's collapse drag underneath keeps working.
 */
internal fun Modifier.queuePullUpGesture(
    state: QueuePullUpState,
    enabled: Boolean,
): Modifier =
    pointerInput(enabled, state.hiddenY) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val velocityTracker = VelocityTracker()
            var totalDy = 0f
            var claimed = false
            while (true) {
                val event = awaitPointerEvent()
                val change =
                    event.changes.firstOrNull { it.id == down.id }
                        ?: event.changes.firstOrNull { it.pressed }
                        ?: break
                if (!change.pressed) {
                    if (claimed) state.launchSettle(velocityTracker.calculateVelocity().y, totalDy)
                    break
                }
                val dy = change.positionChange().y
                totalDy += dy
                if (!claimed) {
                    if (change.isConsumed) break
                    if (totalDy <= -viewConfiguration.touchSlop) {
                        claimed = true
                        state.claim()
                        velocityTracker.resetTracking()
                    } else if (totalDy >= viewConfiguration.touchSlop) {
                        break
                    } else {
                        continue
                    }
                }
                change.consume()
                velocityTracker.addPointerInputChange(change)
                state.dragBy(dy)
            }
        }
    }

/** Draws the queue sheet at the state's offset and wires its handle, nested scroll and Back. */
@Composable
internal fun QueuePullUpSheet(
    state: QueuePullUpState,
    content: @Composable (cornerRadius: Dp, dragHandleModifier: Modifier) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val queueFraction = state.fraction()
    val clampedOffset = state.offset()

    BackHandler(enabled = state.isActive && queueFraction > 0.05f) {
        state.close()
    }

    val cornerRadius = 28.dp * (1f - queueFraction)

    var handleDragActive by remember { mutableStateOf(false) }
    var handleDragTotal by remember { mutableFloatStateOf(0f) }
    val draggableState =
        rememberDraggableState { delta ->
            handleDragTotal += delta
            scope.launch {
                // A delta queued behind the release must not cancel the settle animation.
                if (handleDragActive) {
                    state.offsetY.snapTo((state.offsetY.value + delta).coerceIn(0f, state.hiddenY))
                }
            }
        }
    val dragHandleModifier =
        Modifier.draggable(
            orientation = Orientation.Vertical,
            state = draggableState,
            onDragStarted = {
                handleDragActive = true
                handleDragTotal = 0f
                scope.launch { state.offsetY.stop() }
            },
            onDragStopped = { velocity ->
                handleDragActive = false
                state.settle(velocity, handleDragTotal)
            },
        )

    val nestedScrollConnection =
        remember(state) {
            object : NestedScrollConnection {
                override fun onPreScroll(
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val offset = state.offsetY.value
                    if (source == NestedScrollSource.UserInput && available.y < 0f && offset > 0f) {
                        val toMove = maxOf(available.y, -offset)
                        state.dragBy(toMove)
                        return Offset(0f, toMove)
                    }
                    return Offset.Zero
                }

                override fun onPostScroll(
                    consumed: Offset,
                    available: Offset,
                    source: NestedScrollSource,
                ): Offset {
                    val offset = state.offsetY.value
                    if (source == NestedScrollSource.UserInput && available.y > 0f && offset < state.hiddenY) {
                        val toMove = minOf(available.y, state.hiddenY - offset)
                        state.dragBy(toMove)
                        return Offset(0f, toMove)
                    }
                    return Offset.Zero
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (state.offsetY.value > 0f && state.offsetY.value < state.hiddenY) {
                        state.settle(available.y)
                        return available
                    }
                    return Velocity.Zero
                }

                override suspend fun onPostFling(
                    consumed: Velocity,
                    available: Velocity,
                ): Velocity {
                    if (state.offsetY.value > 0f && state.offsetY.value < state.hiddenY) {
                        state.settle(available.y)
                        return available
                    }
                    return Velocity.Zero
                }
            }
        }

    if (state.isActive || clampedOffset < state.hiddenY - 1f) {
        Box(
            modifier =
                Modifier
                    .offset { IntOffset(0, clampedOffset.roundToInt()) }
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .shadow(
                        elevation = (18.dp * queueFraction),
                        shape = RoundedCornerShape(topStart = cornerRadius, topEnd = cornerRadius),
                        clip = false,
                    ).nestedScroll(nestedScrollConnection),
        ) {
            content(cornerRadius, dragHandleModifier)
        }
    }
}
