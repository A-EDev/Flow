package io.github.aedev.flow.ui.components.shared

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.lerp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

private enum class MiniBarSwipePhase { IDLE, TENSION, SNAPPING, FREE_DRAG }

/** A swipe commits past this share of the width, or on a fling faster than [COMMIT_FLING_VELOCITY]. */
private const val COMMIT_WIDTH_FRACTION = 1f / 3f
private const val COMMIT_FLING_VELOCITY = 1000f

/** What a committed swipe does with the card. */
enum class MiniBarSwipeMotion {
    /** Leaves the screen and the bar goes with it. */
    FlyOff,

    /** Leaves through one edge and comes back through the other with the next item on it. */
    FlyThrough,

    /** Returns to rest: the action happened in place. */
    SpringBack,
}

/** A committed swipe: how the card moves, and the action, run where that motion needs it. */
class MiniBarSwipeCommit(
    val motion: MiniBarSwipeMotion,
    val perform: () -> Unit,
)

/** Whether a released swipe went far or fast enough towards its side to commit. */
internal fun isMiniBarSwipeCommitted(
    travelX: Float,
    velocityX: Float,
    widthPx: Float,
): Boolean =
    abs(travelX) > widthPx * COMMIT_WIDTH_FRACTION ||
        (abs(velocityX) > COMMIT_FLING_VELOCITY && sign(velocityX) == sign(travelX))

/**
 * A mini bar's sideways swipe with a tension phase: the first stretch resists the finger, then the
 * card snaps to it with a haptic and tracks 1:1. On release [onCommit] says what the swipe does,
 * given whether it went towards the start (left); null springs the card back.
 */
class MediaMiniBarSwipeHandler(
    private val scope: CoroutineScope,
    private val density: Density,
    private val hapticFeedback: HapticFeedback,
    private val offsetAnimatable: Animatable<Float, AnimationVector1D>,
    private val screenWidthPx: Float,
    private val onCommit: (towardsStart: Boolean) -> MiniBarSwipeCommit?,
) {
    private var dragPhase: MiniBarSwipePhase = MiniBarSwipePhase.IDLE
    private var accumulatedDragX: Float = 0f
    private var offsetJob: Job? = null
    private val velocityTracker = VelocityTracker()

    fun onDragStart() {
        dragPhase = MiniBarSwipePhase.TENSION
        accumulatedDragX = 0f
        velocityTracker.resetTracking()
        offsetJob?.cancel()
        offsetJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                offsetAnimatable.stop()
            }
    }

    fun onHorizontalDrag(
        dragAmount: Float,
        uptimeMillis: Long,
    ) {
        accumulatedDragX += dragAmount
        velocityTracker.addPosition(uptimeMillis, Offset(accumulatedDragX, 0f))

        when (dragPhase) {
            MiniBarSwipePhase.TENSION -> {
                val snapThresholdPx = 100f * density.density
                if (abs(accumulatedDragX) < snapThresholdPx) {
                    val maxTensionOffsetPx = 30f * density.density
                    val dragFraction = (abs(accumulatedDragX) / snapThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = lerp(0f, maxTensionOffsetPx, dragFraction)
                    offsetJob?.cancel()
                    offsetJob =
                        scope.launch(start = CoroutineStart.UNDISPATCHED) {
                            offsetAnimatable.snapTo(tensionOffset * accumulatedDragX.sign)
                        }
                } else {
                    dragPhase = MiniBarSwipePhase.SNAPPING
                }
            }

            MiniBarSwipePhase.SNAPPING -> {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                offsetJob?.cancel()
                offsetJob =
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.animateTo(
                            targetValue = accumulatedDragX,
                            animationSpec =
                                spring(
                                    dampingRatio = 0.8f,
                                    stiffness = Spring.StiffnessLow,
                                ),
                        )
                    }
                dragPhase = MiniBarSwipePhase.FREE_DRAG
            }

            MiniBarSwipePhase.FREE_DRAG -> {
                offsetJob?.cancel()
                offsetJob =
                    scope.launch(start = CoroutineStart.UNDISPATCHED) {
                        offsetAnimatable.animateTo(
                            targetValue = accumulatedDragX,
                            animationSpec =
                                spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessHigh,
                                ),
                        )
                    }
            }

            MiniBarSwipePhase.IDLE -> {
                Unit
            }
        }
    }

    fun onDragEnd() {
        dragPhase = MiniBarSwipePhase.IDLE
        offsetJob?.cancel()
        val velocityX = velocityTracker.calculateVelocity().x
        val towardsStart = accumulatedDragX < 0
        val commit =
            if (isMiniBarSwipeCommitted(accumulatedDragX, velocityX, screenWidthPx)) onCommit(towardsStart) else null
        val exitOffset = if (towardsStart) -screenWidthPx else screenWidthPx
        offsetJob =
            scope.launch(start = CoroutineStart.UNDISPATCHED) {
                when (commit?.motion) {
                    MiniBarSwipeMotion.FlyOff -> {
                        offsetAnimatable.animateTo(exitOffset, tween(durationMillis = 200, easing = FastOutSlowInEasing))
                        commit.perform()
                        offsetAnimatable.snapTo(0f)
                    }

                    MiniBarSwipeMotion.FlyThrough -> {
                        offsetAnimatable.animateTo(exitOffset, tween(durationMillis = 180, easing = FastOutSlowInEasing))
                        commit.perform()
                        offsetAnimatable.snapTo(-exitOffset)
                        offsetAnimatable.animateTo(0f, spring(Spring.DampingRatioNoBouncy, Spring.StiffnessMediumLow))
                    }

                    MiniBarSwipeMotion.SpringBack -> {
                        commit.perform()
                        offsetAnimatable.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                    }

                    null -> {
                        offsetAnimatable.animateTo(0f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
                    }
                }
            }
    }
}

@Composable
fun rememberMediaMiniBarSwipeHandler(
    scope: CoroutineScope,
    density: Density,
    hapticFeedback: HapticFeedback,
    offsetAnimatable: Animatable<Float, AnimationVector1D>,
    screenWidthPx: Float,
    onCommit: (towardsStart: Boolean) -> MiniBarSwipeCommit?,
): MediaMiniBarSwipeHandler {
    val onCommitState = rememberUpdatedState(onCommit)
    return remember(scope, density, hapticFeedback, offsetAnimatable, screenWidthPx) {
        MediaMiniBarSwipeHandler(
            scope = scope,
            density = density,
            hapticFeedback = hapticFeedback,
            offsetAnimatable = offsetAnimatable,
            screenWidthPx = screenWidthPx,
            onCommit = { towardsStart -> onCommitState.value(towardsStart) },
        )
    }
}

fun Modifier.mediaMiniBarSwipe(
    enabled: Boolean,
    handler: MediaMiniBarSwipeHandler,
): Modifier {
    if (!enabled) return this
    return this.pointerInput(handler) {
        detectHorizontalDragGestures(
            onDragStart = { handler.onDragStart() },
            onHorizontalDrag = { change, dragAmount ->
                change.consume()
                handler.onHorizontalDrag(dragAmount, change.uptimeMillis)
            },
            onDragEnd = { handler.onDragEnd() },
        )
    }
}
