package io.github.aedev.flow.ui.screens.settings.diagnostics

import androidx.compose.runtime.Immutable

enum class LogLevel { ERROR, WARN, INFO, QUIET, HEADING }

@Immutable
data class LogLine(
    val text: String,
    val level: LogLevel,
)

/** A log as the page draws it: still loading, empty, or lines in chunks of [LOG_CHUNK_LINES]. */
@Immutable
sealed interface LogState {
    data object Loading : LogState

    data object Empty : LogState

    data class Lines(
        val chunks: List<List<LogLine>>,
    ) : LogState
}

internal const val LOG_CHUNK_LINES = 30

private val ErrorLevel = Regex("""\s[EF]\s""")
private val WarnLevel = Regex("""\sW\s""")
private val InfoLevel = Regex("""\sI\s""")

/** The level of a logcat line in threadtime format ("date time pid tid LEVEL tag: message"). */
internal fun logcatLevel(line: String): LogLevel =
    when {
        ErrorLevel.containsMatchIn(line) -> LogLevel.ERROR
        WarnLevel.containsMatchIn(line) -> LogLevel.WARN
        InfoLevel.containsMatchIn(line) -> LogLevel.INFO
        else -> LogLevel.QUIET
    }

/** Crash reports are plain stack traces between "=" rules, so the level comes from the text. */
internal fun crashLevel(line: String): LogLevel =
    when {
        line.startsWith("=") -> LogLevel.HEADING
        "Exception" in line || "CRASH" in line -> LogLevel.ERROR
        else -> LogLevel.INFO
    }

internal fun parseLog(
    text: String?,
    level: (String) -> LogLevel,
): LogState {
    val lines = text.orEmpty().lines().dropLastWhile { it.isBlank() }
    if (lines.all { it.isBlank() }) return LogState.Empty
    return LogState.Lines(lines.map { LogLine(it, level(it)) }.chunked(LOG_CHUNK_LINES))
}
