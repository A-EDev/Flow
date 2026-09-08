package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import io.github.aedev.flow.ui.components.videoplayer.PlayerDraggableState

/**
 * Tap expands the mini player, double tap toggles wide mode. The platform detector already waits
 * the system double-tap timeout before committing a single tap and drops the tap when the drag
 * handler consumes a move, which is the behaviour the old hand-rolled timer reproduced.
 */
internal fun Modifier.miniPlayerTapGestures(
    enabled: Boolean,
    state: PlayerDraggableState,
    metrics: DraggablePlayerGestureMetrics,
): Modifier {
    if (!enabled) return this
    return pointerInput(state, metrics) {
        detectTapGestures(
            onTap = { state.expand() },
            onDoubleTap = {
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
                        isTablet = metrics.isTablet,
                        isFoldable = metrics.isFoldable,
                    )
                }
            },
        )
    }
}
