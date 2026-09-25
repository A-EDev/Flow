package io.github.aedev.flow.player.stream

private val StatusPattern = Regex("""status=(\w+)""")
private val GoneStatuses = setOf("ERROR", "UNPLAYABLE")

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
}
