package io.github.aedev.flow.utils

import java.util.Date
import java.util.Locale

enum class DateDisplayMode {
    RELATIVE,
    EXACT,
    BOTH;

    companion object {
        fun fromString(value: String?): DateDisplayMode =
            entries.firstOrNull { it.name == value } ?: RELATIVE
    }
}

enum class DateContextMode {
    DEFAULT,
    RELATIVE,
    EXACT,
    BOTH;

    fun toDisplayMode(): DateDisplayMode? = when (this) {
        DEFAULT -> null
        RELATIVE -> DateDisplayMode.RELATIVE
        EXACT -> DateDisplayMode.EXACT
        BOTH -> DateDisplayMode.BOTH
    }

    companion object {
        fun fromString(value: String?): DateContextMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

enum class DateFormatStyle(val pattern: String?) {
    SYSTEM(null),
    MMM_D_YYYY("MMM d, yyyy"),
    D_MMM_YYYY("d MMM yyyy"),
    ISO("yyyy-MM-dd"),
    DMY_SLASH("dd/MM/yyyy"),
    MDY_SLASH("MM/dd/yyyy");

    companion object {
        fun fromString(value: String?): DateFormatStyle =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

enum class DateContext { LISTS, WATCH, DESCRIPTION }

data class DateDisplaySettings(
    val globalMode: DateDisplayMode = DateDisplayMode.RELATIVE,
    val formatStyle: DateFormatStyle = DateFormatStyle.SYSTEM,
    val listsMode: DateContextMode = DateContextMode.DEFAULT,
    val watchMode: DateContextMode = DateContextMode.DEFAULT,
    val descriptionMode: DateContextMode = DateContextMode.DEFAULT,
) {
    fun resolve(context: DateContext): DateDisplayMode {
        val override = when (context) {
            DateContext.LISTS -> listsMode
            DateContext.WATCH -> watchMode
            DateContext.DESCRIPTION -> descriptionMode
        }
        return override.toDisplayMode() ?: globalMode
    }

    fun format(
        date: String?,
        context: DateContext,
        timestampFallbackMs: Long = 0L,
        locale: Locale = Locale.getDefault(),
    ): String = formatUploadDateConfigured(date, resolve(context), formatStyle, timestampFallbackMs, locale)
}

fun formatExactDate(
    timestampMs: Long,
    style: DateFormatStyle,
    locale: Locale = Locale.getDefault(),
): String {
    if (timestampMs <= 0L) return ""
    val date = Date(timestampMs)
    return try {
        val pattern = style.pattern
            ?: android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMd")
        java.text.SimpleDateFormat(pattern, locale).format(date)
    } catch (e: Exception) {
        java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM, locale).format(date)
    }
}

fun formatUploadDateConfigured(
    date: String?,
    mode: DateDisplayMode,
    style: DateFormatStyle,
    timestampFallbackMs: Long = 0L,
    locale: Locale = Locale.getDefault(),
): String {
    if (date?.trim().equals("live", ignoreCase = true)) return "LIVE"

    val timestamp = resolveDisplayUploadTimestamp(date, timestampFallbackMs)
    val prefix = relativePrefix(date)
    val relative = applyRelativePrefix(relativeString(date, timestamp, locale), prefix)
    val exact = if (timestamp != null && timestamp > 0L) formatExactDate(timestamp, style, locale) else ""
    val exactWithPrefix = applyExactPrefix(exact, prefix)
    return when (mode) {
        DateDisplayMode.RELATIVE -> relative
        DateDisplayMode.EXACT -> exactWithPrefix.ifBlank { relative }
        DateDisplayMode.BOTH -> when {
            relative.isNotBlank() && exact.isNotBlank() -> "$relative • $exact"
            exactWithPrefix.isNotBlank() -> exactWithPrefix
            else -> relative
        }
    }
}

private fun relativeString(date: String?, timestamp: Long?, locale: Locale): String {
    val s = date?.trim().orEmpty()
    if (s.equals("live", ignoreCase = true)) return "LIVE"
    if (timestamp != null && timestamp > 0L) {
        return formatYouTubeRelativeTime(timestamp, locale = locale)
    }
    return formatTimeAgo(date, locale)
}

private fun relativePrefix(date: String?): String? {
    val text = date?.trim().orEmpty().lowercase(Locale.US)
    return when {
        text.startsWith("streamed ") -> "Streamed"
        text.startsWith("premiered ") -> "Premiered"
        else -> null
    }
}

private fun applyRelativePrefix(value: String, prefix: String?): String {
    if (prefix == null || value.isBlank() || value == "LIVE") return value
    val normalizedValue = if (value == "Just now") "just now" else value
    return "$prefix $normalizedValue"
}

private fun applyExactPrefix(value: String, prefix: String?): String {
    if (prefix == null || value.isBlank()) return value
    return "$prefix $value"
}

fun parseToTimestamp(text: String?): Long? {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty()) return null
    raw.toLongOrNull()?.takeIf { it > 100_000_000_000L }?.let { return it }

    val cleanRaw = raw
        .replace(Regex("(?i)^(streamed|premiered)\\s+"), "")
        .trim()

    val absFormats = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ssX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd", "MMM dd, yyyy", "MMM d, yyyy", "d MMM yyyy", "dd MMM yyyy"
    )
    for (f in absFormats) {
        for (loc in listOf(Locale.US, Locale.getDefault())) {
            try {
                val sdf = java.text.SimpleDateFormat(f, loc)
                sdf.isLenient = false
                val d = sdf.parse(cleanRaw)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
    }
    return parseRelativeToTimestamp(cleanRaw)
}

internal fun resolveDisplayUploadTimestamp(
    date: String?,
    timestampFallbackMs: Long,
    nowMillis: Long = System.currentTimeMillis(),
): Long? {
    val storedTimestamp = timestampFallbackMs.takeIf { it > 0L }
    val relativeTimestamp = parseRelativeToTimestamp(date.orEmpty(), nowMillis)
    return when {
        storedTimestamp != null && relativeTimestamp != null -> minOf(storedTimestamp, relativeTimestamp)
        relativeTimestamp != null -> relativeTimestamp
        else -> parseToTimestamp(date) ?: storedTimestamp
    }
}

internal fun parseRelativeToTimestamp(text: String, now: Long = System.currentTimeMillis()): Long? {
    val n = text.lowercase(Locale.US)
        .replace("streamed", "")
        .replace("premiered", "")
        .replace("live", "")
        .replace("ago", "")
        .trim()
    if (n.isBlank()) return null
    if (n.contains("just now") || n.contains("today")) return now
    if (n.contains("yesterday")) return now - 86_400_000L

    val compactMatch = Regex("""(\d+)\s*(mo|sec|secs|second|seconds|min|mins|minute|minutes|hr|hrs|hour|hours|[smhdwy])\b""")
        .find(n)
    val value = compactMatch?.groupValues?.getOrNull(1)?.toLongOrNull()
        ?: Regex("(\\d+)").find(n)?.groupValues?.getOrNull(1)?.toLongOrNull()
        ?: return null
    val compactUnit = compactMatch?.groupValues?.getOrNull(2)
    val unitMillis = when {
        compactUnit in listOf("s", "sec", "secs", "second", "seconds") || n.contains("second") -> 1_000L
        compactUnit in listOf("m", "min", "mins", "minute", "minutes") || n.contains("minute") -> 60_000L
        compactUnit in listOf("h", "hr", "hrs", "hour", "hours") || n.contains("hour") -> 3_600_000L
        compactUnit == "d" || n.contains("day") -> 86_400_000L
        compactUnit == "w" || n.contains("week") -> 7L * 86_400_000L
        compactUnit == "mo" || n.contains("month") -> 30L * 86_400_000L
        compactUnit == "y" || n.contains("year") -> 365L * 86_400_000L
        else -> return null
    }
    return now - value * unitMillis
}
