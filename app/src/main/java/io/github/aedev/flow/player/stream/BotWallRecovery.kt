package io.github.aedev.flow.player.stream

internal object BotWallRecovery {
    const val ROTATION_COOLDOWN_MS = 5 * 60 * 1000L
    const val MAX_RETRIES_PER_LOAD = 1

    /**
     * A bot wall is a verdict on the viewer, so it only counts when no client gave a verdict on the
     * video itself: timeouts and exceptions are tolerated beside it, any other status is not.
     */
    fun isViewerLevelBlock(failureReasons: List<String>): Boolean {
        if (failureReasons.none { "BOT_WALL" in it }) return false
        return failureReasons.all { "BOT_WALL" in it || "timeout" in it || "exception=" in it }
    }

    /** @param msSinceLastRotation null when the identity has never been rotated. */
    fun shouldRotateAndRetry(
        failureReasons: List<String>,
        retriesDone: Int,
        msSinceLastRotation: Long?,
    ): Boolean =
        retriesDone < MAX_RETRIES_PER_LOAD &&
            isViewerLevelBlock(failureReasons) &&
            isCooldownElapsed(msSinceLastRotation)

    fun isCooldownElapsed(msSinceLastRotation: Long?): Boolean = msSinceLastRotation == null || msSinceLastRotation >= ROTATION_COOLDOWN_MS
}
