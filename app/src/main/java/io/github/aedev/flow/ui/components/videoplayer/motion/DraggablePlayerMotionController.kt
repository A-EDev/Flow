package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.MutatorMutex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

/**
 * Serialises the draggable player's animated moves so a new intent always preempts the running
 * one as a whole. Position (expansion fraction plus the mini offsets) and size are separate
 * mutations on purpose: a mini drag takes over the position while a resize that is already in
 * flight is allowed to finish, which is what the hand-tuned gestures assume.
 */
internal class DraggablePlayerMotionController(
    private val offsetX: Animatable<Float, AnimationVector1D>,
    private val offsetY: Animatable<Float, AnimationVector1D>,
    private val expandFraction: Animatable<Float, AnimationVector1D>,
    private val miniSizeScale: Animatable<Float, AnimationVector1D>,
) {
    private val positionMutex = MutatorMutex()
    private val sizeMutex = MutatorMutex()

    /** Runs a coordinated fraction/offset move; cancels any position move already running. */
    suspend fun movePosition(block: suspend CoroutineScope.() -> Unit) {
        positionMutex.mutate { coroutineScope { block() } }
    }

    /** Runs a size move; cancels any resize already running. */
    suspend fun resize(block: suspend CoroutineScope.() -> Unit) {
        sizeMutex.mutate { coroutineScope { block() } }
    }

    /** Preempts a running position move, leaving every value where it is. */
    suspend fun stopPosition() {
        positionMutex.mutate { }
    }

    suspend fun snapPosition(
        fraction: Float? = null,
        x: Float? = null,
        y: Float? = null,
    ) {
        positionMutex.mutate {
            fraction?.let { expandFraction.snapTo(it) }
            x?.let { offsetX.snapTo(it) }
            y?.let { offsetY.snapTo(it) }
        }
    }

    suspend fun snapSize(scale: Float) {
        sizeMutex.mutate { miniSizeScale.snapTo(scale) }
    }
}
