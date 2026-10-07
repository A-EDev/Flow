package io.github.aedev.flow.ui.components.videoplayer.motion

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import io.github.aedev.flow.ui.components.videoplayer.PlayerDraggableState
import io.github.aedev.flow.ui.components.videoplayer.canSwipeUpToFullscreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DRAG_MODE_FRACTION = 0
private const val DRAG_MODE_EXPAND_SCALE = 1

/** Screen-space movement under which a mini player press still counts as a tap. */
private const val MINI_TAP_MOVEMENT_PX = 24f

private enum class MiniPressOutcome { Tap, LongPress, Drag, Cancelled }

private class MiniPress(
    val outcome: MiniPressOutcome,
    val travel: Offset = Offset.Zero,
)

/**
 * The one-finger gesture on the video box: collapse drag and swipe-to-fullscreen while expanded,
 * free 2-D drag, tap, long press, corner fling and dismiss fling while mini.
 *
 * Hand-rolled on purpose. `anchoredDraggable` and `draggable2D` apply touch slop and report
 * deltas in local space, but this node sits under the morph's graphicsLayer scale, so the same
 * finger travel is 2-3x more local distance in the mini player and the collapse mapping changes
 * as the scale shrinks mid-drag. Every delta and slop here is scaled through
 * [DraggablePlayerGestureMetrics.liveGestureScale] at read time to keep the hand-tuned physics.
 *
 * Taps are classified inside this loop rather than by a separate `detectTapGestures` node: the
 * modifier chain on the video box must stay structurally constant, because inserting or removing
 * a pointer-input node while a finger is down re-pairs the remaining nodes by position, resets
 * their keys and cancels the drag coroutine mid-gesture, which leaves the sheet frozen wherever
 * the finger was.
 *
 * Velocity is measured on the finger's screen-space path (the scaled deltas summed from the
 * down), not on the raw local positions: the node moves with the finger, so local positions
 * barely change while the mini tracks it and then jump once it is pinned against a bound, which
 * read as a horizontal fling and dismissed the player on an ordinary diagonal throw.
 */
internal class DraggablePlayerGestureHandler(
    private val state: PlayerDraggableState,
    private val metrics: DraggablePlayerGestureMetrics,
) {
    private val velocityTracker = VelocityTracker()

    suspend fun AwaitPointerEventScope.handleGesture() {
        val gestureTargetMiniX = metrics.targetMiniX
        val gestureTargetMiniY = metrics.targetMiniY

        val down = awaitFirstDown(requireUnconsumed = false)
        val downConsumedByChild = down.isConsumed
        if (state.openOrigin != null) {
            awaitAllPointersUp()
            return
        }

        if (state.expandFraction.value > 0.8f) {
            handleMiniGesture(down, downConsumedByChild)
            return
        }
        val isCollapseDrag = state.expandFraction.value < 0.4f

        val canSwipeToFullscreen =
            isCollapseDrag &&
                canSwipeUpToFullscreen(
                    isFullscreen = metrics.isFullscreen,
                    isLandscape = metrics.isLandscape,
                    isLargeWindow = metrics.isLargeScreen,
                ) &&
                metrics.onFullscreenGesture != null

        var fingerPath = Offset.Zero
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, fingerPath)

        if (isCollapseDrag) {
            state.scope.launch {
                state.motion.stopFraction()
                state.motion.snapOffsets(x = gestureTargetMiniX, y = gestureTargetMiniY)
            }
        }

        val dragPointerId = down.id
        var hasCrossedSlop = !isCollapseDrag
        var startDragY = 0f
        var detectedDirection = 0

        if (isCollapseDrag) {
            val slop = viewConfiguration.touchSlop
            while (!hasCrossedSlop) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == dragPointerId }
                if (change == null || !change.pressed || change.isConsumed) {
                    break
                }
                val delta = (change.position - down.position) * metrics.liveGestureScale(state)
                velocityTracker.addPosition(change.uptimeMillis, delta)
                if (delta.y > slop && delta.y > abs(delta.x)) {
                    hasCrossedSlop = true
                    startDragY = delta.y
                    detectedDirection = 1
                    fingerPath = delta
                    change.consume()
                } else if (canSwipeToFullscreen && delta.y < -slop && abs(delta.y) > abs(delta.x)) {
                    hasCrossedSlop = true
                    startDragY = delta.y
                    detectedDirection = -1
                    fingerPath = delta
                    change.consume()
                } else if (abs(delta.x) > slop) {
                    break
                }
            }
        }

        var cumulativeDragY = startDragY
        val startFraction = state.expandFraction.value
        var totalUpwardDrag = 0f

        if (hasCrossedSlop) {
            state.isDragging = true
            val snapSignal = Channel<Unit>(Channel.CONFLATED)
            var pendingFraction = state.expandFraction.value
            var pendingMode = DRAG_MODE_FRACTION
            var pendingExpandScale = 1f
            val snapDriver =
                state.scope.launch {
                    for (ignored in snapSignal) {
                        when (pendingMode) {
                            DRAG_MODE_FRACTION -> {
                                state.expandFraction.snapTo(pendingFraction)
                            }

                            else -> {
                                state.expandDragScale.snapTo(pendingExpandScale)
                            }
                        }
                    }
                }
            try {
                drag(dragPointerId) { change ->
                    val delta = change.positionChange() * metrics.liveGestureScale(state)
                    fingerPath += delta
                    velocityTracker.addPosition(change.uptimeMillis, fingerPath)

                    if (isCollapseDrag && detectedDirection == 1) {
                        change.consume()
                        cumulativeDragY += delta.y
                        val collapseTravel = (metrics.targetMiniY - metrics.statusBarHeight).coerceAtLeast(1f)
                        pendingFraction = (startFraction + cumulativeDragY / collapseTravel).coerceIn(0f, 1f)
                        pendingMode = DRAG_MODE_FRACTION
                        snapSignal.trySend(Unit)
                    } else if (isCollapseDrag && detectedDirection == -1) {
                        change.consume()
                        totalUpwardDrag += -delta.y
                        pendingExpandScale = expandDragZoomFor(totalUpwardDrag)
                        pendingMode = DRAG_MODE_EXPAND_SCALE
                        snapSignal.trySend(Unit)
                    }
                }
            } finally {
                snapSignal.close()
                snapDriver.cancel()
                state.isDragging = false
                state.scope.launch { state.expandDragScale.animateTo(1f, dragReleaseSpringSpec) }
            }
        } else {
            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    if (event.changes.all { !it.pressed }) break
                }
            } finally {
                state.isDragging = false
            }
        }

        if (isCollapseDrag && detectedDirection == -1) {
            val velY = velocityTracker.calculateVelocity().y
            if (shouldEnterFullscreenFromSwipe(totalUpwardDrag, velY)) {
                metrics.onFullscreenGesture?.invoke()
            }
            return
        }

        if (isCollapseDrag) {
            val velY = velocityTracker.calculateVelocity().y
            val collapseTravel = metrics.targetMiniY - metrics.statusBarHeight
            if (shouldCollapseOnRelease(state.expandFraction.value, velY)) {
                metrics.onCollapseGesture?.invoke()
                state.collapse(fractionVelocity(velY, collapseTravel))
            } else {
                state.expand(fractionVelocity(velY, collapseTravel))
            }
            return
        }
    }

    /**
     * A press on the mini player: a release before it travels is a tap and expands at once, a hold
     * past the long-press timeout toggles wide mode, and travel starts a drag that follows the finger.
     */
    private suspend fun AwaitPointerEventScope.handleMiniGesture(
        down: PointerInputChange,
        downConsumedByChild: Boolean,
    ) {
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, Offset.Zero)
        val startedAtBottom = !state.corner.isTop
        state.scope.launch { state.motion.stopOffsets() }
        val press = awaitMiniPress(down)
        when (press.outcome) {
            MiniPressOutcome.Tap -> {
                if (!downConsumedByChild && metrics.tapToExpand) state.expand()
            }

            MiniPressOutcome.LongPress -> {
                if (!downConsumedByChild) {
                    metrics.onLongPress()
                    toggleWideMode()
                }
                awaitAllPointersUp()
            }

            MiniPressOutcome.Cancelled -> {
                Unit
            }

            MiniPressOutcome.Drag -> {
                val fingerAt = dragMini(down.id, press.travel)
                releaseMini(fingerAt, startedAtBottom)
            }
        }
    }

    private suspend fun AwaitPointerEventScope.awaitMiniPress(down: PointerInputChange): MiniPress =
        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed) return@withTimeoutOrNull MiniPress(MiniPressOutcome.Tap)
                if (change.isConsumed) return@withTimeoutOrNull MiniPress(MiniPressOutcome.Cancelled)
                val travel = (change.position - down.position) * metrics.liveGestureScale(state)
                velocityTracker.addPosition(change.uptimeMillis, travel)
                if (travel.getDistance() > MINI_TAP_MOVEMENT_PX) {
                    change.consume()
                    return@withTimeoutOrNull MiniPress(MiniPressOutcome.Drag, travel)
                }
            }
            @Suppress("UNREACHABLE_CODE")
            MiniPress(MiniPressOutcome.Cancelled)
        } ?: MiniPress(MiniPressOutcome.LongPress)

    /**
     * Moves the mini player with the finger, starting with the [initialTravel] the press already
     * covered, and returns where the finger would have put it with no bounds in the way.
     */
    private suspend fun AwaitPointerEventScope.dragMini(
        pointerId: PointerId,
        initialTravel: Offset,
    ): Offset {
        state.isDragging = true
        var fingerPath = initialTravel
        var rawX = state.offsetX.value + initialTravel.x
        var rawY = state.offsetY.value + initialTravel.y
        val snapSignal = Channel<Unit>(Channel.CONFLATED)
        val snapDriver =
            state.scope.launch {
                for (ignored in snapSignal) {
                    state.offsetX.snapTo(miniDragX(rawX))
                    state.offsetY.snapTo(miniDragY(rawY))
                }
            }
        snapSignal.trySend(Unit)
        try {
            drag(pointerId) { change ->
                val delta = change.positionChange() * metrics.liveGestureScale(state)
                fingerPath += delta
                velocityTracker.addPosition(change.uptimeMillis, fingerPath)
                change.consume()
                rawX += delta.x
                rawY += delta.y
                snapSignal.trySend(Unit)
            }
        } finally {
            snapSignal.close()
            snapDriver.cancel()
            state.isDragging = false
        }
        return Offset(rawX, rawY)
    }

    private fun miniDragX(rawX: Float): Float =
        if (state.isInlineMode && !metrics.isLargeScreen) {
            metrics.stablePhoneCenteredX
        } else {
            rubberBand(rawX, metrics.minX, metrics.maxX)
        }

    private fun miniDragY(rawY: Float): Float = rubberBand(rawY, metrics.minY, metrics.maxY)

    private suspend fun AwaitPointerEventScope.awaitAllPointersUp() {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            if (event.changes.all { !it.pressed }) return
        }
    }

    private fun toggleWideMode() {
        if (state.isInlineMode) {
            state.shrinkToCorner(
                baseMiniWidth = metrics.baseMiniWidth,
                screenWidth = metrics.screenWidth,
                margin = metrics.margin,
                minY = metrics.minY,
                screenHeight = metrics.screenHeight,
                bottomNavPad = metrics.bottomNavPad,
            )
        } else {
            state.expandWide(
                screenWidth = metrics.screenWidth,
                margin = metrics.margin,
                baseMiniWidth = metrics.baseMiniWidth,
                screenHeight = metrics.screenHeight,
                minY = metrics.minY,
                bottomNavPad = metrics.bottomNavPad,
                isLargeWindow = metrics.isLargeScreen,
            )
        }
    }

    private fun closeDownward(velocityY: Float) {
        state.scope.launch {
            launch {
                state.motion.moveOffsets {
                    state.offsetY.animateTo(
                        metrics.screenHeight + metrics.margin,
                        miniDismissSpringSpec,
                        initialVelocity = velocityY.coerceAtLeast(0f),
                    )
                }
            }
            delay(MINI_DISMISS_TEARDOWN_DELAY_MS)
            metrics.onDismiss()
        }
    }

    private fun releaseMini(
        fingerAt: Offset,
        startedAtBottom: Boolean,
    ) {
        val velocity = velocityTracker.calculateVelocity()
        val velY = velocity.y
        val velX = velocity.x
        if (shouldCloseMiniDownward(fingerAt.y, metrics.maxY, metrics.miniHeight, startedAtBottom, velX, velY)) {
            closeDownward(velY)
            return
        }
        val currentX = state.offsetX.value
        val currentY = state.offsetY.value
        val bounds = metrics.bounds
        val newCorner =
            resolveMiniPlayerCorner(
                current = state.corner,
                currentX = currentX,
                currentY = currentY,
                bounds = bounds,
                scaledVelocityX = velX,
                scaledVelocityY = velY,
            )
        val targetX = cornerTargetX(newCorner, bounds.minX, bounds.maxX)
        val targetY = cornerTargetY(newCorner, bounds.minY, bounds.maxY)

        if (state.isInlineMode) {
            state.corner = newCorner
            state.scope.launch {
                state.motion.moveOffsets {
                    if (metrics.isLargeScreen) {
                        launch { state.offsetX.animateTo(targetX, miniSnapSpringSpec, initialVelocity = velX) }
                    } else {
                        launch { state.offsetX.animateTo(metrics.stablePhoneCenteredX, miniSnapSpringSpec) }
                    }
                    launch { state.offsetY.animateTo(targetY, miniSnapSpringSpec, initialVelocity = velY) }
                }
            }
            return
        }

        val dismissOffsetX =
            resolveMiniPlayerDismissOffset(
                targetCorner = newCorner,
                currentX = currentX,
                bounds = bounds,
                scaledVelocityX = velX,
                scaledVelocityY = velY,
                screenWidth = metrics.screenWidth,
                miniWidth = metrics.miniWidth,
                margin = metrics.margin,
            )
        Log.w(
            "FlowVideoSheet",
            "miniRelease vel=(${velX.roundToInt()},${velY.roundToInt()}) pos=(${currentX.roundToInt()},${currentY.roundToInt()}) " +
                "corner=${state.corner}->$newCorner dismiss=${dismissOffsetX != null}",
        )
        if (dismissOffsetX != null) {
            state.scope.launch {
                launch {
                    state.motion.moveOffsets {
                        state.offsetX.animateTo(dismissOffsetX, miniDismissSpringSpec, initialVelocity = velX)
                    }
                }
                delay(MINI_DISMISS_TEARDOWN_DELAY_MS)
                metrics.onDismiss()
            }
        } else {
            state.corner = newCorner
            state.scope.launch {
                state.motion.moveOffsets {
                    launch { state.offsetX.animateTo(targetX, miniSnapSpringSpec, initialVelocity = velX) }
                    launch { state.offsetY.animateTo(targetY, miniSnapSpringSpec, initialVelocity = velY) }
                }
            }
        }
    }
}

internal fun Modifier.draggablePlayerGestures(handler: DraggablePlayerGestureHandler): Modifier =
    pointerInput(handler) {
        awaitEachGesture {
            with(handler) { handleGesture() }
        }
    }
