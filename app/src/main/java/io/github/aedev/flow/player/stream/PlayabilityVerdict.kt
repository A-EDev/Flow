package io.github.aedev.flow.player.stream

private val StatusPattern = Regex("""status=(\w+)""")
private val GoneStatuses = setOf("ERROR", "UNPLAYABLE")
private val BotWallReason = Regex("""not a bot|confirm you.re""", RegexOption.IGNORE_CASE)

/** Why YouTube refused a video to this viewer, when the refusal is about the viewer and not the video. */
enum class PlaybackBlock {
    /** "Sign in to confirm you're not a bot": the network or visitor is flagged. */
    BOT_WALL,

    /** The video needs a signed-in account, for its age or its audience. */
    LOGIN_REQUIRED,
}

/** A failed extraction whose [block] explains it better than a generic error. */
class PlaybackBlockedException(
    val block: PlaybackBlock,
) : Exception("YouTube refused playback: $block")

/**
 * Whether a failed extraction means the video itself is gone (removed, unavailable here), as
 * opposed to a failure worth retrying. Only playability statuses count: reasons are localized.
 * A bot check, a timeout or an exception anywhere means the answer is not known, so it is false.
 */
internal object PlayabilityVerdict {
    fun isGone(failureReasons: List<String>): Boolean {
        if (failureReasons.any { "BOT_WALL" in it || "timeout" in it || "exception=" in it }) return false
        val statuses = failureReasons.mapNotNull { StatusPattern.find(it)?.groupValues?.get(1) }
        return statuses.isNotEmpty() && statuses.all { it in GoneStatuses }
    }

    /** Extraction runs in en-US, so the reason is English; "confirm your age" is not a bot wall. */
    fun isBotWall(reason: String?): Boolean = reason != null && BotWallReason.containsMatchIn(reason)

    fun block(failureReasons: List<String>): PlaybackBlock? =
        when {
            failureReasons.any { "BOT_WALL" in it } -> PlaybackBlock.BOT_WALL
            failureReasons.any { StatusPattern.find(it)?.groupValues?.get(1) == "LOGIN_REQUIRED" } -> PlaybackBlock.LOGIN_REQUIRED
            else -> null
        }
}
