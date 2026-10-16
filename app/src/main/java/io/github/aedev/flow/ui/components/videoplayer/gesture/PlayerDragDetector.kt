package io.github.aedev.flow.ui.components.videoplayer.gesture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs

internal enum class PlayerDragAxis { HORIZONTAL, VERTICAL }

/**
 * Axis-locked drag detection.
 *
 * `detectDragGestures` cannot express this: it consumes the pointer the moment any drag begins, so
 * it claimed horizontal movement the player had no use for, and it resolved the axis from a running
 * total that could flip mid-gesture. Here the axis is decided once, at the instant touch slop is
 * crossed, and the pointer is only consumed when [onAxisAccepted] wants that axis — a drag we do
 * not handle stays unconsumed and remains available to the parent (the draggable player sheet).
 *
 * [onDragStart] returns false to ignore the gesture entirely, which is how the edge exclusion zones
 * keep their hands off the controls sitting under them.
 */
internal suspend fun PointerInputScope.detectPlayerDrags(
    onDragStart: (Offset) -> Boolean,
    onAxisAccepted: (PlayerDragAxis) -> Boolean,
    onDrag: (change: PointerInputChange, delta: Offset, axis: PlayerDragAxis) -> Unit,
    onDragEnd: (PlayerDragAxis) -> Unit,
    onDragCancel: (PlayerDragAxis) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!onDragStart(down.position)) return@awaitEachGesture

        var axis: PlayerDragAxis? = null
        val dragStart =
            awaitTouchSlopOrCancellation(down.id) { change, overSlop ->
                val candidate =
                    if (abs(overSlop.x) > abs(overSlop.y)) {
                        PlayerDragAxis.HORIZONTAL
                    } else {
                        PlayerDragAxis.VERTICAL
                    }
                // Consuming is what claims the gesture. Leaving it unconsumed makes
                // awaitTouchSlopOrCancellation keep waiting, so a rejected axis neither steals the
                // pointer nor ends the gesture.
                if (onAxisAccepted(candidate)) {
                    axis = candidate
                    change.consume()
                }
            } ?: return@awaitEachGesture

        val lockedAxis = axis ?: return@awaitEachGesture

        val completed =
            drag(dragStart.id) { change ->
                onDrag(change, change.positionChange(), lockedAxis)
                change.consume()
            }

        if (completed) onDragEnd(lockedAxis) else onDragCancel(lockedAxis)
    }
}
