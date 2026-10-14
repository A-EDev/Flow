package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import io.github.aedev.flow.ui.components.videoplayer.MiniPlayerCorner
import io.github.aedev.flow.ui.components.videoplayer.PlayerDraggableState
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

/**
 * Two-finger resize of the floating mini player.
 *
 * Hand-rolled on purpose: `detectTransformGestures` reports zoom as the ratio of local-space
 * finger distances, but this node sits under the morph's graphicsLayer scale, and that scale grows
 * with the very size the pinch is changing. In local space the fingers then barely move, so the
 * platform detector feeds back into itself and stalls. Tracking screen-space distance through
 * [DraggablePlayerGestureMetrics.liveGestureScale] is what makes the pinch track the fingers.
 *
 * The player stays pinned to its corner while it resizes, so letting go only finishes the size
 * and never sends it across the screen.
 */
internal class MiniPlayerPinchGestureHandler(
    private val state: PlayerDraggableState,
    private val metrics: DraggablePlayerGestureMetrics,
) {
    suspend fun AwaitPointerEventScope.handlePinch() {
        awaitFirstDown(requireUnconsumed = false)
        val pressed = awaitSecondFinger() ?: return
        if (state.expandFraction.value < 0.8f || state.tuckedSide != null) return

        val ptr1Id = pressed[0].id
        val ptr2Id = pressed[1].id
        val initialDist =
            (
                (pressed[0].position - pressed[1].position)
                    .getDistance() * metrics.liveGestureScale(state)
            ).coerceAtLeast(1f)
        val startScale = state.miniSizeScale.value
        val maxScale = (metrics.maxWideWidth / metrics.baseMiniWidth.coerceAtLeast(1f)).coerceAtLeast(1f)
        val pin =
            MiniPinchPin(
                corner = state.corner,
                start = Offset(state.offsetX.value, state.offsetY.value),
                startScale = startScale,
                maxScale = maxScale,
            )
        val snapSignal = Channel<Unit>(Channel.CONFLATED)
        var pScale = startScale
        var pOffset = pin.start
        val pinchDriver =
            state.scope.launch {
                for (ignored in snapSignal) {
                    state.miniSizeScale.snapTo(pScale)
                    state.offsetX.snapTo(pOffset.x)
                    state.offsetY.snapTo(pOffset.y)
                }
            }

        state.isDragging = true
        try {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Main)
                val p1 = e.changes.firstOrNull { it.id == ptr1Id } ?: break
                val p2 = e.changes.firstOrNull { it.id == ptr2Id } ?: break
                if (!p1.pressed || !p2.pressed) break
                p1.consume()
                p2.consume()
                val currentDist = (p1.position - p2.position).getDistance() * metrics.liveGestureScale(state)
                pScale = (startScale * currentDist / initialDist).coerceIn(1f, maxScale)
                pOffset = metrics.pinchedMiniOffset(pScale, pin)
                snapSignal.trySend(Unit)
            }
        } finally {
            snapSignal.close()
            pinchDriver.cancel()
            state.isDragging = false
            settle(pin)
        }
    }

    /** Waits for a second finger, which may land after the first has started to move. */
    private suspend fun AwaitPointerEventScope.awaitSecondFinger(): List<PointerInputChange>? {
        while (true) {
            val pressed = awaitPointerEvent(PointerEventPass.Main).changes.filter { it.pressed }
            if (pressed.isEmpty()) return null
            if (pressed.size >= 2) return pressed
        }
    }

    private fun settle(pin: MiniPinchPin) {
        val targetScale = if (state.isInlineMode) pin.maxScale else 1f
        val target =
            if (targetScale > 1f && metrics.isLargeScreen) {
                metrics.pinchedMiniOffset(targetScale, pin)
            } else {
                metrics.miniPinchAnchor(targetScale, pin.corner, pin.maxScale)
            }
        state.scope.launch {
            launch { state.motion.resize { state.miniSizeScale.animateTo(targetScale, miniResizeSpringSpec) } }
            launch {
                state.motion.moveOffsets {
                    launch { state.offsetX.animateTo(target.x, miniResizeSpringSpec) }
                    launch { state.offsetY.animateTo(target.y, miniResizeSpringSpec) }
                }
            }
        }
    }
}

/** What a pinch started from: the corner it is pinned to and where the player was. */
internal class MiniPinchPin(
    val corner: MiniPlayerCorner,
    val start: Offset,
    val startScale: Float,
    val maxScale: Float,
)

/**
 * Where [corner] holds the mini player at [scale]: its corner edges stay put as it grows, and on a
 * phone it drifts to the centre as it reaches the wide size, where wide mode rests.
 */
internal fun DraggablePlayerGestureMetrics.miniPinchAnchor(
    scale: Float,
    corner: MiniPlayerCorner,
    maxScale: Float,
): Offset {
    val width = miniBoxWidth(baseMiniWidth * scale).coerceAtMost(maxWideWidth)
    val height = width / clampedAspect.coerceAtLeast(0.01f)
    val maxX = (screenWidth - width - margin).coerceAtLeast(minX)
    val maxY = (screenHeight - height - bottomNavPad - margin).coerceAtLeast(minY)
    val cornerX = if (corner.isLeft) minX else maxX
    val x =
        if (isLargeScreen || maxScale <= 1f) {
            cornerX
        } else {
            lerpClamped(cornerX, stablePhoneCenteredX, (scale - 1f) / (maxScale - 1f))
        }
    return Offset(x, if (corner.isTop) minY else maxY)
}

/** The player's offset mid-pinch: moved as its corner anchor moves, so it never jumps from where it was. */
internal fun DraggablePlayerGestureMetrics.pinchedMiniOffset(
    scale: Float,
    pin: MiniPinchPin,
): Offset {
    val moved =
        pin.start + miniPinchAnchor(scale, pin.corner, pin.maxScale) -
            miniPinchAnchor(pin.startScale, pin.corner, pin.maxScale)
    val width = miniBoxWidth(baseMiniWidth * scale).coerceAtMost(maxWideWidth)
    val height = width / clampedAspect.coerceAtLeast(0.01f)
    val maxX = (screenWidth - width - margin).coerceAtLeast(minX)
    val maxY = (screenHeight - height - bottomNavPad - margin).coerceAtLeast(minY)
    return Offset(moved.x.coerceIn(minX, maxX), moved.y.coerceIn(minY, maxY))
}

internal fun Modifier.miniPlayerPinchGesture(handler: MiniPlayerPinchGestureHandler): Modifier =
    pointerInput(handler) {
        awaitEachGesture {
            with(handler) { handlePinch() }
        }
    }
