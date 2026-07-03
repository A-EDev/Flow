package io.github.aedev.flow.ui.screens.player

private const val RELATED_PREWARM_MIN_POSITION_MS = 20_000L
private const val RELATED_PREWARM_MIN_PROGRESS = 0.35

internal fun shouldPrewarmRelatedPlayback(
    videoId: String,
    positionMs: Long,
    durationMs: Long,
    isShort: Boolean,
    isLocal: Boolean,
    alreadyPrewarmed: Boolean
): Boolean {
    if (videoId.isBlank() || isShort || isLocal || alreadyPrewarmed) return false
    if (positionMs >= RELATED_PREWARM_MIN_POSITION_MS) return true
    if (durationMs <= 0L) return false
    return positionMs.toDouble() / durationMs.toDouble() >= RELATED_PREWARM_MIN_PROGRESS
}
