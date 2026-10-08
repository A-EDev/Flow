package io.github.aedev.flow.ui.components.videoplayer.motion

import androidx.compose.runtime.MonotonicFrameClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/** The most animation time one frame may move the sheet on: about a frame and a half at 60 Hz. */
internal const val MAX_SHEET_FRAME_STEP_NANOS = 24_000_000L

/**
 * Hands the animations it clocks at most [maxStepNanos] of progress per frame. Springs run on frame
 * time, so a frame the UI thread spends composing the player used to land the sheet 100 ms further on
 * in a single step; under this clock that frame only pauses the motion.
 */
internal class SteadyFrameClock(
    private val frameClock: MonotonicFrameClock,
    private val maxStepNanos: Long = MAX_SHEET_FRAME_STEP_NANOS,
) : MonotonicFrameClock {
    private var lastFrameNanos = -1L
    private var playTimeNanos = 0L

    override suspend fun <R> withFrameNanos(onFrame: (frameTimeNanos: Long) -> R): R =
        frameClock.withFrameNanos { frameTimeNanos ->
            if (lastFrameNanos >= 0L) {
                playTimeNanos += (frameTimeNanos - lastFrameNanos).coerceIn(0L, maxStepNanos)
            }
            lastFrameNanos = frameTimeNanos
            onFrame(playTimeNanos)
        }
}

/** Runs [block] with every animation it starts clocked by one shared [SteadyFrameClock]. */
internal suspend fun <T> withSteadyFrames(block: suspend CoroutineScope.() -> T): T {
    val frameClock = coroutineContext[MonotonicFrameClock] ?: return withContext(coroutineContext, block)
    return withContext(SteadyFrameClock(frameClock), block)
}
