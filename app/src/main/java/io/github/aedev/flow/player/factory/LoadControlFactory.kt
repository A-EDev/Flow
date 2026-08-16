package io.github.aedev.flow.player.factory

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.upstream.DefaultAllocator
import io.github.aedev.flow.data.local.BufferDurations
import io.github.aedev.flow.player.config.PlayerConfig

/**
 * The app's three `DefaultLoadControl` profiles — video, Shorts and music — in one place.
 *
 * `setBufferDurationsMs` asserts `max >= min`, `min >= playback` and `min >= rebuffer`, and it throws
 * while the player is being constructed rather than falling back, which is how #788 became a
 * bootloop. Routing every profile through [build] enforces that invariant once instead of trusting
 * three independently-maintained sets of numbers to keep satisfying it.
 */
@UnstableApi
object LoadControlFactory {
    private const val TAG = "LoadControlFactory"

    /**
     * Main video player: stored user preferences, re-capped against what this device's heap can
     * afford. The caps are applied at read time because a combination saved as valid on one device
     * can exceed the budget on another.
     */
    fun forVideo(
        context: Context,
        minMs: Int,
        maxMs: Int,
        playbackMs: Int,
        rebufferMs: Int,
    ): DefaultLoadControl {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClassMb = activityManager?.memoryClass ?: 256
        val isLowMemoryDevice = activityManager?.isLowRamDevice == true || memoryClassMb <= 256
        val isConstrainedHeap = isLowMemoryDevice || memoryClassMb <= 384

        val maxSafeMinBufferMs =
            if (isLowMemoryDevice) {
                PlayerConfig.LOW_MEMORY_MAX_SAFE_MAIN_MIN_BUFFER_MS
            } else {
                PlayerConfig.MAX_SAFE_MAIN_MIN_BUFFER_MS
            }
        val maxSafeBufferMs =
            if (isLowMemoryDevice) {
                PlayerConfig.LOW_MEMORY_MAX_SAFE_MAIN_BUFFER_MS
            } else {
                PlayerConfig.MAX_SAFE_MAIN_BUFFER_MS
            }
        val targetBufferBytes =
            when {
                isLowMemoryDevice -> PlayerConfig.LOW_MEMORY_MAIN_TARGET_BUFFER_BYTES
                isConstrainedHeap -> PlayerConfig.MID_MEMORY_MAIN_TARGET_BUFFER_BYTES
                else -> PlayerConfig.MAIN_TARGET_BUFFER_BYTES
            }
        val backBufferMs =
            if (isConstrainedHeap) {
                PlayerConfig.LOW_MEMORY_BACK_BUFFER_DURATION_MS
            } else {
                PlayerConfig.BACK_BUFFER_DURATION_MS
            }

        val buffers =
            BufferDurations.sanitize(
                minMs = minMs,
                maxMs = maxMs,
                playbackMs = playbackMs,
                rebufferMs = rebufferMs,
                maxSafeMinMs = maxSafeMinBufferMs,
                maxSafeMaxMs = maxSafeBufferMs,
            )

        Log.d(
            TAG,
            "Buffer config: min=${buffers.minMs}ms, max=${buffers.maxMs}ms, playback=${buffers.playbackMs}ms, " +
                "rebuffer=${buffers.rebufferMs}ms, target=${targetBufferBytes / 1024 / 1024}MB, " +
                "back=${backBufferMs}ms, heap=${memoryClassMb}MB",
        )

        return build(
            minMs = buffers.minMs,
            maxMs = buffers.maxMs,
            playbackMs = buffers.playbackMs,
            rebufferMs = buffers.rebufferMs,
            backBufferMs = backBufferMs,
            retainBackBufferFromKeyframe = true,
            targetBufferBytes = targetBufferBytes,
        )
    }

    /**
     * Shorts pool: a deliberately small window so a swipe starts the next clip without a visible
     * wait. These are not user-tunable and stay below [BufferDurations]' product floors on purpose.
     */
    fun forShorts(): DefaultLoadControl =
        build(
            minMs = PlayerConfig.SHORTS_MIN_BUFFER_MS,
            maxMs = PlayerConfig.SHORTS_MAX_BUFFER_MS,
            playbackMs = PlayerConfig.SHORTS_BUFFER_FOR_PLAYBACK_MS,
            rebufferMs = PlayerConfig.SHORTS_BUFFER_FOR_REBUFFER_MS,
            backBufferMs = PlayerConfig.SHORTS_BACK_BUFFER_MS,
            retainBackBufferFromKeyframe = true,
            targetBufferBytes = PlayerConfig.SHORTS_TARGET_BUFFER_BYTES,
        )

    /**
     * Music service: audio-only, so a long window costs little memory and a low playback threshold
     * gets the first note out quickly. No back buffer, and no byte cap — the duration window is the
     * only budget it needs.
     */
    fun forMusic(): DefaultLoadControl =
        build(
            minMs = PlayerConfig.MUSIC_MIN_BUFFER_MS,
            maxMs = PlayerConfig.MUSIC_MAX_BUFFER_MS,
            playbackMs = PlayerConfig.MUSIC_BUFFER_FOR_PLAYBACK_MS,
            rebufferMs = PlayerConfig.MUSIC_BUFFER_FOR_REBUFFER_MS,
            backBufferMs = 0,
            retainBackBufferFromKeyframe = false,
            targetBufferBytes = C.LENGTH_UNSET,
        )

    /**
     * @param targetBufferBytes `C.LENGTH_UNSET` lets `DefaultLoadControl` derive the cap from the
     *   duration window, which is what a profile wants when time, not memory, is its budget.
     */
    private fun build(
        minMs: Int,
        maxMs: Int,
        playbackMs: Int,
        rebufferMs: Int,
        backBufferMs: Int,
        retainBackBufferFromKeyframe: Boolean,
        targetBufferBytes: Int,
    ): DefaultLoadControl {
        // Widen rather than throw: a profile that violates the contract should cost buffer, not a crash loop.
        val resolvedMin = maxOf(minMs, playbackMs, rebufferMs)
        val resolvedMax = maxOf(maxMs, resolvedMin)

        return DefaultLoadControl
            .Builder()
            .setAllocator(DefaultAllocator(true, PlayerConfig.ALLOCATOR_BUFFER_SIZE))
            .setBufferDurationsMs(resolvedMin, resolvedMax, playbackMs, rebufferMs)
            .setBackBuffer(backBufferMs, retainBackBufferFromKeyframe)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(targetBufferBytes)
            .build()
    }
}
