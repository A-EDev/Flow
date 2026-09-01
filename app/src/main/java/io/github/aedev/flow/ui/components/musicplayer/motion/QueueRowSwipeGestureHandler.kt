package io.github.aedev.flow.ui.components.musicplayer.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

internal enum class QueueSwipeAction { PLAY_NEXT, ADD_TO_QUEUE }

/**
 * Bidirectional four-phase horizontal swipe for queue rows: a tension zone that compresses the
 * first 60dp of finger travel into 20dp of visual travel, a haptic break-through that springs the
 * row out to the finger, 1:1 tracking with commit-zone haptics, and an elastic settle. Swiping
 * toward the start commits PLAY_NEXT; toward the end commits ADD_TO_QUEUE.
 */
internal class QueueRowSwipeGestureHandler(
    private val scope: CoroutineScope,
    private val offset: Animatable<Float, AnimationVector1D>,
    private val density: Float,
    private val itemWidthPx: () -> Float,
    private val haptics: HapticFeedback,
    private val flyOffOnCommit: Boolean,
    private val onCommit: (QueueSwipeAction) -> Unit,
) {
    private enum class Phase { IDLE, TENSION, FREE_DRAG }

    private var phase = Phase.IDLE
    private var accumulated = 0f
    private var settling = false

    var isInCommitZone by mutableStateOf(false)
        private set

    fun onDragStart() {
        if (settling) return
        phase = Phase.TENSION
        accumulated = 0f
        isInCommitZone = false
        scope.launch { offset.stop() }
    }

    fun onDrag(delta: Float) {
        if (settling || phase == Phase.IDLE) return
        accumulated += delta
        when (phase) {
            Phase.TENSION -> {
                val tensionThresholdPx = TENSION_TRAVEL_DP * density
                if (abs(accumulated) < tensionThresholdPx) {
                    val maxTensionOffsetPx = TENSION_VISUAL_DP * density
                    val fraction = (abs(accumulated) / tensionThresholdPx).coerceIn(0f, 1f)
                    val tensionOffset = sign(accumulated) * maxTensionOffsetPx * fraction
                    scope.launch { offset.snapTo(tensionOffset) }
                } else {
                    phase = Phase.FREE_DRAG
                    haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
                    scope.launch {
                        offset.animateTo(
                            targetValue = accumulated,
                            animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessLow),
                        )
                    }
                }
            }

            Phase.FREE_DRAG -> {
                val width = itemWidthPx()
                val nowInZone = width > 0f && abs(accumulated) > width * COMMIT_FRACTION
                if (nowInZone != isInCommitZone) {
                    isInCommitZone = nowInZone
                    haptics.performHapticFeedback(
                        if (nowInZone) {
                            HapticFeedbackType.GestureThresholdActivate
                        } else {
                            HapticFeedbackType.SegmentTick
                        },
                    )
                }
                scope.launch {
                    offset.animateTo(
                        targetValue = accumulated,
                        animationSpec =
                            spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                    )
                }
            }

            Phase.IDLE -> {
                Unit
            }
        }
    }

    fun onDragEnd() {
        if (settling || phase == Phase.IDLE) return
        val width = itemWidthPx()
        val committed = width > 0f && abs(accumulated) > width * COMMIT_FRACTION
        val direction = sign(accumulated)
        phase = Phase.IDLE
        if (committed) {
            val action = if (direction < 0f) QueueSwipeAction.PLAY_NEXT else QueueSwipeAction.ADD_TO_QUEUE
            haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
            settling = true
            scope.launch {
                if (flyOffOnCommit) {
                    offset.animateTo(
                        targetValue = direction * width,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                    )
                    onCommit(action)
                    // If the row leaves the list this coroutine dies with it; otherwise heal back.
                    delay(1000)
                } else {
                    offset.animateTo(
                        targetValue = direction * width * 0.5f,
                        animationSpec = tween(durationMillis = 110, easing = FastOutSlowInEasing),
                    )
                    onCommit(action)
                }
                offset.animateTo(targetValue = 0f, animationSpec = settleSpring())
                isInCommitZone = false
                settling = false
            }
        } else {
            isInCommitZone = false
            scope.launch { offset.animateTo(targetValue = 0f, animationSpec = settleSpring()) }
        }
        accumulated = 0f
    }

    fun onDragCancel() {
        if (settling) return
        phase = Phase.IDLE
        accumulated = 0f
        isInCommitZone = false
        scope.launch { offset.animateTo(targetValue = 0f, animationSpec = settleSpring()) }
    }

    private fun settleSpring() =
        spring<Float>(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        )

    private companion object {
        const val TENSION_TRAVEL_DP = 60f
        const val TENSION_VISUAL_DP = 20f
        const val COMMIT_FRACTION = 0.40f
    }
}
