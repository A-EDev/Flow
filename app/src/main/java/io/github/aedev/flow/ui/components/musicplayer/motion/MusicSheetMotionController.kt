package io.github.aedev.flow.ui.components.musicplayer.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.MutatorMutex
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Drives the sheet's two coordinated animatables (vertical position + expansion fraction)
 * through a single [MutatorMutex] so a new gesture or settle always preempts a running one
 * instead of fighting it.
 */
internal class MusicSheetMotionController(
    private val translationY: Animatable<Float, AnimationVector1D>,
    private val expansionFraction: Animatable<Float, AnimationVector1D>,
    private val mutex: MutatorMutex,
    private val defaultAnimationSpec: AnimationSpec<Float>,
    private val expandedY: Float = 0f,
) {
    suspend fun animateTo(
        targetExpanded: Boolean,
        collapsedY: Float,
        animationSpec: AnimationSpec<Float> = defaultAnimationSpec,
        initialVelocity: Float = 0f,
    ) {
        val targetFraction = if (targetExpanded) 1f else 0f
        val targetY = if (targetExpanded) expandedY else collapsedY
        val velocityScale = (collapsedY - expandedY).coerceAtLeast(1f)

        if (
            translationY.value == targetY &&
            expansionFraction.value == targetFraction &&
            !translationY.isRunning &&
            !expansionFraction.isRunning
        ) {
            return
        }

        mutex.mutate {
            coroutineScope {
                launch {
                    translationY.animateTo(
                        targetValue = targetY,
                        initialVelocity = initialVelocity,
                        animationSpec = animationSpec,
                    )
                }
                launch {
                    expansionFraction.animateTo(
                        targetValue = targetFraction,
                        initialVelocity = -initialVelocity / velocityScale,
                        animationSpec = animationSpec,
                    )
                }
            }
        }
    }

    suspend fun stop() {
        translationY.stop()
        expansionFraction.stop()
    }

    suspend fun snapTo(
        translationYValue: Float,
        expansionFractionValue: Float,
    ) {
        mutex.mutate {
            translationY.snapTo(translationYValue)
            expansionFraction.snapTo(expansionFractionValue)
        }
    }
}
