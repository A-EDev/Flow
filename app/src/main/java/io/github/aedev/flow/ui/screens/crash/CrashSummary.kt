package io.github.aedev.flow.ui.screens.crash

import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val NEW_ISSUE_URL = "https://github.com/A-EDev/Flow/issues/new"
private const val APP_PACKAGE = "io.github.aedev.flow"
private const val MAX_URL_LOG_CHARS = 1_500
private const val MAX_URL_ACTUAL_CHARS = 300
private const val UNKNOWN_STEPS = "Not known. Flow crashed during normal use; the stack trace is under Logs."
private const val EXPECTED = "Flow keeps running."
private const val TOP_FRAMES = 6
private val ReportTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

/** The facts a person needs from a crash report, read back from the text the crash handler saved. */
data class CrashSummary(
    val exception: String,
    val message: String?,
    val location: String?,
    val appVersion: String?,
    val android: String?,
    val device: String?,
    val time: LocalDateTime?,
    val topFrames: List<String>,
)

fun parseCrashSummary(report: String): CrashSummary {
    val lines = report.lines().map { it.trim() }

    fun value(label: String) =
        lines
            .firstOrNull { it.startsWith("$label:") }
            ?.substringAfter(':')
            ?.trim()
            ?.ifBlank { null }
    val frames = lines.filter { it.startsWith("at ") }
    val ownFrame = frames.firstOrNull { APP_PACKAGE in it } ?: frames.firstOrNull()
    val manufacturer = value("Manufacturer")
    val model = value("Model")
    return CrashSummary(
        exception = value("Exception")?.substringAfterLast('.') ?: "Crash",
        message = value("Message")?.takeUnless { it == "none" },
        location = ownFrame?.substringAfterLast('(')?.substringBefore(')'),
        appVersion = value("App Version"),
        android = value("Android"),
        device = listOfNotNull(manufacturer, model).joinToString(" ").ifBlank { null },
        time =
            lines.firstOrNull { it.startsWith("CRASH REPORT - ") }?.substringAfter(" - ")?.let {
                runCatching { LocalDateTime.parse(it, ReportTime) }.getOrNull()
            },
        topFrames = frames.take(TOP_FRAMES),
    )
}

/**
 * The bug form with everything that fits in a URL filled in. The full report is too long for a
 * URL, so the Logs field gets the top of it and the page copies the rest.
 *
 * The required text fields are answered from the crash itself, so someone who cannot say how it
 * happened can still send it in one tap and edit only what they know (#1189).
 */
fun CrashSummary.issueUrl(): String {
    val title = "[Bug]: $exception" + (location?.let { " in $it" } ?: "")
    val error = exception + (message?.let { ": $it" } ?: "")
    val log =
        buildString {
            append(error)
            topFrames.forEach { append('\n').append(it) }
        }.take(MAX_URL_LOG_CHARS)
    val fields =
        listOfNotNull(
            "template" to "bug_report.yml",
            "title" to title,
            appVersion?.let { "app-version" to it.substringBefore(' ') },
            android?.let { "android-version" to it },
            device?.let { "device" to it },
            "steps" to UNKNOWN_STEPS,
            "expected" to EXPECTED,
            "actual" to "Flow crashed with $error".take(MAX_URL_ACTUAL_CHARS),
            "logs" to log,
        )
    return NEW_ISSUE_URL + "?" +
        fields.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")}" }
}
