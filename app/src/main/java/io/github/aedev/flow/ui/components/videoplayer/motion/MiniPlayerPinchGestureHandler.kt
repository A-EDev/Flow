package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
 */
internal class MiniPlayerPinchGestureHandler(
    private val state: PlayerDraggableState,
    private val metrics: DraggablePlayerGestureMetrics,
) {
    suspend fun AwaitPointerEventScope.handlePinch() {
        awaitFirstDown(requireUnconsumed = false)
        val evt = awaitPointerEvent(PointerEventPass.Main)
        val pressed = evt.changes.filter { it.pressed }
        if (pressed.size < 2) return
        if (state.expandFraction.value < 0.8f) return

        val ptr1Id = pressed[0].id
        val ptr2Id = pressed[1].id
        val initialDist =
            (
                (pressed[0].position - pressed[1].position)
                    .getDistance() * metrics.liveGestureScale(state)
            ).coerceAtLeast(1f)
        val startScale = state.miniSizeScale.value
        val wideCapWidth = metrics.maxWideWidth
        val maxScale = (wideCapWidth / metrics.baseMiniWidth).coerceAtLeast(1f)
        val snapSignal = Channel<Unit>(Channel.CONFLATED)
        var pScale = startScale
        var pX = state.offsetX.value
        var pY = state.offsetY.value
        val pinchDriver =
            state.scope.launch {
                for (ignored in snapSignal) {
                    state.miniSizeScale.snapTo(pScale)
                    state.offsetX.snapTo(pX)
                    state.offsetY.snapTo(pY)
                }
            }

        try {
            while (true) {
                val e = awaitPointerEvent(PointerEventPass.Main)
                val p1 = e.changes.firstOrNull { it.id == ptr1Id } ?: break
                val p2 = e.changes.firstOrNull { it.id == ptr2Id } ?: break
                if (!p1.pressed || !p2.pressed) {
                    snapSignal.close()
                    pinchDriver.cancel()
                    settle(maxScale = maxScale, wideCapWidth = wideCapWidth)
                    break
                }
                p1.consume()
                p2.consume()
                val currentDist = (p1.position - p2.position).getDistance() * metrics.liveGestureScale(state)
                val gestureScale = currentDist / initialDist
                val newScale = (startScale * gestureScale).coerceIn(1f, maxScale)
                val newMiniW = (metrics.baseMiniWidth * newScale).coerceAtMost(wideCapWidth)
                val newMiniH = newMiniW * (9f / 16f)
                val newMaxX = (metrics.screenWidth - newMiniW - metrics.margin).coerceAtLeast(metrics.margin)
                val newMaxY =
                    (metrics.screenHeight - newMiniH - metrics.bottomNavPad - metrics.margin)
                        .coerceAtLeast(metrics.minY)
                val clampedX =
                    when {
                        metrics.isLargeScreen -> state.offsetX.value.coerceIn(metrics.margin, newMaxX)
                        newScale > 1.5f -> metrics.stablePhoneCenteredX
                        else -> state.offsetX.value.coerceIn(metrics.minX, newMaxX)
                    }
                val clampedY = state.offsetY.value.coerceIn(metrics.minY, newMaxY)
                pScale = newScale
                pX = clampedX
                pY = clampedY
                snapSignal.trySend(Unit)
            }
        } finally {
            snapSignal.close()
            pinchDriver.cancel()
        }
    }

    private fun settle(
        maxScale: Float,
        wideCapWidth: Float,
    ) {
        val targetScale = if (state.miniSizeScale.value > 1.5f) maxScale else 1f
        state.scope.launch {
            state.motion.resize { state.miniSizeScale.animateTo(targetScale, miniResizeSpringSpec) }
            state.motion.moveOffsets {
                if (targetScale <= 1f) {
                    state.offsetX.animateTo(state.cachedTargetX, miniResizeSpringSpec)
                    state.offsetY.animateTo(state.cachedTargetY, miniResizeSpringSpec)
                } else if (metrics.isLargeScreen) {
                    val newMiniW = (metrics.baseMiniWidth * targetScale).coerceAtMost(wideCapWidth)
                    val newMaxX = (metrics.screenWidth - newMiniW - metrics.margin).coerceAtLeast(metrics.margin)
                    val clampedX = state.offsetX.value.coerceIn(metrics.margin, newMaxX)
                    state.offsetX.animateTo(clampedX, miniResizeSpringSpec)
                } else {
                    state.offsetX.animateTo(metrics.stablePhoneCenteredX, miniResizeSpringSpec)
                }
            }
        }
    }
}

internal fun Modifier.miniPlayerPinchGesture(handler: MiniPlayerPinchGestureHandler): Modifier =
    pointerInput(handler) {
        awaitEachGesture {
            with(handler) { handlePinch() }
        }
    }
