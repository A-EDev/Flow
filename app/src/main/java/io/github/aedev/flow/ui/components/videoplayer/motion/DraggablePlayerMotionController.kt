package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.MutatorMutex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

/**
 * Serialises the draggable player's animated moves so a new intent always preempts the running
 * one as a whole. Three independent mutations on purpose: the expansion fraction, the mini
 * offsets (x and y together) and the size. Almost every intent touches only one of them, and the
 * offsets are re-targeted by the corner re-snap while a collapse is still animating the fraction,
 * so grouping the fraction with the offsets froze the sheet mid-morph. A mini drag likewise takes
 * over the offsets while an in-flight resize is allowed to finish.
 */
internal class DraggablePlayerMotionController(
    private val offsetX: Animatable<Float, AnimationVector1D>,
    private val offsetY: Animatable<Float, AnimationVector1D>,
    private val expandFraction: Animatable<Float, AnimationVector1D>,
    private val miniSizeScale: Animatable<Float, AnimationVector1D>,
) {
    private val fractionMutex = MutatorMutex()
    private val offsetMutex = MutatorMutex()
    private val sizeMutex = MutatorMutex()
    private val dipMutex = MutatorMutex()

    /** Animates the expansion fraction; cancels any fraction move already running. */
    suspend fun animateFraction(block: suspend () -> Unit) {
        fractionMutex.mutate { block() }
    }

    /** Animates the settle dip; cancels any dip already running. */
    suspend fun animateDip(block: suspend () -> Unit) {
        dipMutex.mutate { block() }
    }

    /** Runs a coordinated x/y move; cancels any offsets move already running. */
    suspend fun moveOffsets(block: suspend CoroutineScope.() -> Unit) {
        offsetMutex.mutate { coroutineScope { block() } }
    }

    /** Runs a size move; cancels any resize already running. */
    suspend fun resize(block: suspend CoroutineScope.() -> Unit) {
        sizeMutex.mutate { coroutineScope { block() } }
    }

    /** Preempts a running fraction move, leaving the value where it is. */
    suspend fun stopFraction() {
        fractionMutex.mutate { }
    }

    /** Preempts a running offsets move, leaving both values where they are. */
    suspend fun stopOffsets() {
        offsetMutex.mutate { }
    }

    suspend fun snapFraction(fraction: Float) {
        fractionMutex.mutate { expandFraction.snapTo(fraction) }
    }

    suspend fun snapOffsets(
        x: Float,
        y: Float,
    ) {
        offsetMutex.mutate {
            offsetX.snapTo(x)
            offsetY.snapTo(y)
        }
    }

    suspend fun snapSize(scale: Float) {
        sizeMutex.mutate { miniSizeScale.snapTo(scale) }
    }
}
