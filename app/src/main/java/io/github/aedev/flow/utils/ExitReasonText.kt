package io.github.aedev.flow.utils

import android.app.ApplicationExitInfo
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * One line of the diagnostics report per past process exit. A native abort or a system kill leaves
 * no crash report, so without this a reporter's "the app closed" cannot be told apart from a
 * swipe-away (#921).
 */
internal object ExitReasonText {
    private val TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.US)

    fun line(
        reason: Int,
        timestampMs: Long,
        importance: Int,
        description: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val time = TIME.format(Instant.ofEpochMilli(timestampMs).atZone(zone))
        val detail = description?.takeIf { it.isNotBlank() }?.let { "  $it" }.orEmpty()
        return "$time  ${reasonName(reason)}  importance=$importance$detail"
    }

    fun reasonName(reason: Int): String =
        when (reason) {
            ApplicationExitInfo.REASON_EXIT_SELF -> "exited itself"
            ApplicationExitInfo.REASON_SIGNALED -> "killed by a signal"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "killed for low memory"
            ApplicationExitInfo.REASON_CRASH -> "Java crash"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native crash"
            ApplicationExitInfo.REASON_ANR -> "not responding (ANR)"
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "failed to start"
            ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "permission changed"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "killed for excessive resource use"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "stopped by the user"
            ApplicationExitInfo.REASON_USER_STOPPED -> "user stopped"
            ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "a dependency died"
            ApplicationExitInfo.REASON_OTHER -> "killed by the system"
            ApplicationExitInfo.REASON_FREEZER -> "killed while frozen"
            ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "package state changed"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "app updated"
            else -> "unknown ($reason)"
        }
}
