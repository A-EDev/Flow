package io.github.aedev.flow.ui.components.videoplayer.motion

import io.github.aedev.flow.ui.components.videoplayer.PlayerDraggableState
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Where a settled mini player should rest for the current bounds and mode. */
internal data class MiniPlayerResnapTargets(
    val isWideMode: Boolean,
    val isLargeScreen: Boolean,
    val targetMiniX: Float,
    val targetMiniY: Float,
    val minX: Float,
    val maxX: Float,
    val minY: Float,
    val stableWideMaxY: Float,
    val stablePhoneCenteredX: Float,
    val stableWideTargetY: Float,
)

internal fun DraggablePlayerGeometry.resnapTargets(isLargeScreen: Boolean): MiniPlayerResnapTargets =
    MiniPlayerResnapTargets(
        isWideMode = isWideMode,
        isLargeScreen = isLargeScreen,
        targetMiniX = targetMiniX,
        targetMiniY = targetMiniY,
        minX = minX,
        maxX = maxX,
        minY = minY,
        stableWideMaxY = stableWideMaxY,
        stablePhoneCenteredX = stablePhoneCenteredX,
        stableWideTargetY = stableWideTargetY,
    )

/**
 * Moves a settled (or settling) mini player onto its resting position after the bounds changed
 * under it. Touches the offsets only: a collapse that is still animating the expansion fraction
 * must keep running underneath, otherwise the sheet freezes mid-morph with the invisible body
 * panel still over the screen.
 */
internal suspend fun resnapMiniPlayer(
    state: PlayerDraggableState,
    targets: MiniPlayerResnapTargets,
) {
    if (targets.isWideMode && !targets.isLargeScreen) {
        state.motion.moveOffsets {
            launch { state.offsetX.animateTo(targets.stablePhoneCenteredX, miniSnapSpringSpec) }
            launch { state.offsetY.animateTo(targets.stableWideTargetY, miniSnapSpringSpec) }
        }
    } else if (targets.isWideMode && targets.isLargeScreen) {
        val clampedX = state.offsetX.value.coerceIn(targets.minX, targets.maxX)
        val clampedY = state.offsetY.value.coerceIn(targets.minY, targets.stableWideMaxY)
        val moveX = abs(state.offsetX.value - clampedX) > 1f
        val moveY = abs(state.offsetY.value - clampedY) > 1f
        if (moveX || moveY) {
            state.motion.moveOffsets {
                if (moveX) launch { state.offsetX.animateTo(clampedX, miniSnapSpringSpec) }
                if (moveY) launch { state.offsetY.animateTo(clampedY, miniSnapSpringSpec) }
            }
        }
    } else {
        val needsSnap =
            state.offsetX.value == 0f &&
                state.offsetY.value == 0f &&
                targets.targetMiniX > 0f && targets.targetMiniY > 0f
        if (needsSnap) {
            state.motion.snapOffsets(x = targets.targetMiniX, y = targets.targetMiniY)
        } else {
            state.motion.moveOffsets {
                launch { state.offsetX.animateTo(targets.targetMiniX, miniSnapSpringSpec) }
                launch { state.offsetY.animateTo(targets.targetMiniY, miniSnapSpringSpec) }
            }
        }
    }
}
