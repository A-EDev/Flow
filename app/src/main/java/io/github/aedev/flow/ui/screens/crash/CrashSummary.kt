package io.github.aedev.flow.ui.screens.crash

import java.net.URLEncoder
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val NEW_ISSUE_URL = "https://github.com/A-EDev/Flow/issues/new"
private const val APP_PACKAGE = "io.github.aedev.flow"
private const val MAX_URL_LOG_CHARS = 1_500
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
 */
fun CrashSummary.issueUrl(): String {
    val title = "[Bug]: $exception" + (location?.let { " in $it" } ?: "")
    val log =
        buildString {
            append(exception)
            message?.let { append(": ").append(it) }
            topFrames.forEach { append('\n').append(it) }
        }.take(MAX_URL_LOG_CHARS)
    val fields =
        listOfNotNull(
            "template" to "bug_report.yml",
            "title" to title,
            appVersion?.let { "app-version" to it.substringBefore(' ') },
            android?.let { "android-version" to it },
            device?.let { "device" to it },
            "logs" to log,
        )
    return NEW_ISSUE_URL + "?" +
        fields.joinToString("&") { (key, value) -> "$key=${URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")}" }
}
