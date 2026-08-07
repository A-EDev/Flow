package io.github.aedev.flow.player.stream

import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Awaits [probe] for at most [graceMs], returning null once that budget is spent.
 *
 * For work that improves a playback result but is not required to start playing — a fuller audio
 * track list, a richer quality ladder. Two properties matter and neither comes from
 * `withTimeoutOrNull` on its own:
 *
 *  - the caller gets a definite answer within [graceMs], so optional work can never hold first
 *    frame open indefinitely;
 *  - the probe is *cancelled* when the budget runs out, because `withTimeoutOrNull` abandons only
 *    the await — the probe itself would keep running and keep competing for bandwidth with the
 *    media buffer that it lost the race to.
 */
internal suspend fun <T : Any> awaitWithinGrace(
    probe: Deferred<T>,
    graceMs: Long,
): T? {
    withTimeoutOrNull(graceMs) { probe.await() }?.let { return it }
    probe.cancel()
    return null
}
