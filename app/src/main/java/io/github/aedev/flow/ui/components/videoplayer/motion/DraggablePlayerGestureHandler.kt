package io.github.aedev.flow.ui.components.videoplayer.motion

import android.util.Log
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import io.github.aedev.flow.ui.components.videoplayer.PlayerDraggableState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private const val DRAG_MODE_FRACTION = 0
private const val DRAG_MODE_POSITION = 1
private const val DRAG_MODE_EXPAND_SCALE = 2

/** Screen-space movement under which a mini player press still counts as a tap. */
private const val MINI_TAP_MOVEMENT_PX = 24f

/**
 * The one-finger gesture on the video box: collapse drag and swipe-to-fullscreen while expanded,
 * free 2-D drag, tap, double tap, corner fling and dismiss fling while mini.
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
    private val tapDecider = MiniPlayerTapDecider()
    private var singleTapJob: Job? = null

    suspend fun AwaitPointerEventScope.handleGesture() {
        val gestureTargetMiniX = metrics.targetMiniX
        val gestureTargetMiniY = metrics.targetMiniY

        val down = awaitFirstDown(requireUnconsumed = false)
        val downConsumedByChild = down.isConsumed

        val isCollapseDrag = state.expandFraction.value < 0.4f
        val isMiniDrag = state.expandFraction.value > 0.8f

        val canSwipeToFullscreen =
            isCollapseDrag &&
                !metrics.isLandscape &&
                !metrics.isFullscreen &&
                metrics.onFullscreenGesture != null

        var fingerPath = Offset.Zero
        velocityTracker.resetTracking()
        velocityTracker.addPosition(down.uptimeMillis, fingerPath)

        if (isCollapseDrag) {
            state.scope.launch {
                state.motion.stopFraction()
                state.motion.snapOffsets(x = gestureTargetMiniX, y = gestureTargetMiniY)
            }
        } else if (isMiniDrag) {
            state.scope.launch {
                state.motion.stopOffsets()
                state.dragScale.animateTo(0.97f, dragPressSpringSpec)
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
        var totalMovement = 0f
        val startFraction = state.expandFraction.value
        var totalUpwardDrag = 0f

        if (hasCrossedSlop) {
            state.isDragging = true
            val snapSignal = Channel<Unit>(Channel.CONFLATED)
            var pendingFraction = state.expandFraction.value
            var pendingX = state.offsetX.value
            var pendingY = state.offsetY.value
            var pendingMode = DRAG_MODE_FRACTION
            var pendingExpandScale = 1f
            val snapDriver =
                state.scope.launch {
                    for (ignored in snapSignal) {
                        when (pendingMode) {
                            DRAG_MODE_FRACTION -> {
                                state.expandFraction.snapTo(pendingFraction)
                            }

                            DRAG_MODE_EXPAND_SCALE -> {
                                state.expandDragScale.snapTo(pendingExpandScale)
                            }

                            else -> {
                                state.offsetX.snapTo(pendingX)
                                state.offsetY.snapTo(pendingY)
                            }
                        }
                    }
                }
            try {
                drag(dragPointerId) { change ->
                    val delta = change.positionChange() * metrics.liveGestureScale(state)
                    totalMovement += delta.getDistance()
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
                    } else if (isMiniDrag) {
                        if (totalMovement > viewConfiguration.touchSlop * 0.5f) {
                            change.consume()
                            val clampedY = (state.offsetY.value + delta.y).coerceIn(metrics.minY, metrics.maxY)
                            if (state.isInlineMode && !metrics.isLargeScreen) {
                                pendingX = metrics.stablePhoneCenteredX
                            } else {
                                pendingX = (state.offsetX.value + delta.x).coerceIn(metrics.minX, metrics.maxX)
                            }
                            pendingY = clampedY
                            pendingMode = DRAG_MODE_POSITION
                            snapSignal.trySend(Unit)
                        }
                    }
                }
            } finally {
                snapSignal.close()
                snapDriver.cancel()
                state.isDragging = false
                state.scope.launch { state.dragScale.animateTo(1f, dragReleaseSpringSpec) }
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

        if (isMiniDrag && totalMovement < MINI_TAP_MOVEMENT_PX) {
            if (!downConsumedByChild && metrics.tapToExpand) {
                onMiniTap(down.uptimeMillis, viewConfiguration.doubleTapTimeoutMillis)
            }
            return
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
            if (shouldCollapseOnRelease(state.expandFraction.value, velY)) {
                metrics.onCollapseGesture?.invoke()
                state.collapse()
            } else {
                state.expand()
            }
            return
        }

        if (!isMiniDrag) return

        releaseMini()
    }

    private fun onMiniTap(
        uptimeMillis: Long,
        doubleTapTimeoutMillis: Long,
    ) {
        when (tapDecider.onTap(uptimeMillis, doubleTapTimeoutMillis)) {
            MiniPlayerTap.DOUBLE -> {
                singleTapJob?.cancel()
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

            MiniPlayerTap.SINGLE_PENDING -> {
                singleTapJob?.cancel()
                singleTapJob =
                    state.scope.launch {
                        delay(doubleTapTimeoutMillis)
                        state.expand()
                    }
            }
        }
    }

    private fun releaseMini() {
        val velocity = velocityTracker.calculateVelocity()
        val velY = velocity.y
        val velX = velocity.x
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
