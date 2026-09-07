package io.github.aedev.flow.ui.screens.subscriptions

internal const val SUBSCRIPTION_FAILURE_LOG_LINES = 400

/**
 * The clipboard payload behind the failed-channels card. Kept pure so the format is testable and
 * so the composable only has to supply the device block and the log tail.
 */
internal fun buildSubscriptionFailureReport(
    deviceInfo: String,
    failedChannelNames: List<String>,
    failedChannelIds: Set<String>,
    sessionLogs: String,
): String =
    buildString {
        appendLine("Flow — subscription refresh failure")
        appendLine("=".repeat(60))
        appendLine()
        append(deviceInfo)
        appendLine()
        appendLine("Failed channels (${failedChannelIds.size})")
        appendLine("-".repeat(60))
        if (failedChannelIds.isEmpty()) {
            appendLine("(none recorded)")
        } else {
            failedChannelIds.forEachIndexed { index, id ->
                val name = failedChannelNames.getOrNull(index)
                if (name != null && name != id) {
                    appendLine("$name  ($id)")
                } else {
                    appendLine(id)
                }
            }
        }
        appendLine()
        appendLine("Session logs (W/E)")
        appendLine("-".repeat(60))
        append(sessionLogs.ifBlank { "(no session logs captured)" })
    }
