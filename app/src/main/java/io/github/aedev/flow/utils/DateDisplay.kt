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
    val timestamp = parseToTimestamp(date) ?: timestampFallbackMs.takeIf { it > 0L }
    val relative = relativeString(date, timestamp)
    val exact = if (timestamp != null && timestamp > 0L) formatExactDate(timestamp, style, locale) else ""
    return when (mode) {
        DateDisplayMode.RELATIVE -> relative
        DateDisplayMode.EXACT -> exact.ifBlank { relative }
        DateDisplayMode.BOTH -> when {
            relative.isNotBlank() && exact.isNotBlank() -> "$relative • $exact"
            exact.isNotBlank() -> exact
            else -> relative
        }
    }
}

private fun relativeString(date: String?, timestamp: Long?): String {
    val s = date?.trim().orEmpty()
    if (s.isNotEmpty() && (
            s.contains("ago", ignoreCase = true) ||
            s.contains("前") ||
            s.equals("just now", ignoreCase = true) ||
            s.contains("yesterday", ignoreCase = true) ||
            s.contains("today", ignoreCase = true) ||
            s.contains("streamed", ignoreCase = true) ||
            s.contains("premier", ignoreCase = true)
        )
    ) {
        return s
    }
    if (timestamp != null && timestamp > 0L) return relativeFromTimestamp(timestamp)
    return formatTimeAgo(date)
}

private fun relativeFromTimestamp(timestampMs: Long, nowMs: Long = System.currentTimeMillis()): String {
    val diff = nowMs - timestampMs
    if (diff < 0L) return "Just now"
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    val months = days / 30
    val years = days / 365
    return when {
        years > 0 -> "${years}y ago"
        months > 0 -> "${months}mo ago"
        days > 0 -> "${days}d ago"
        hours > 0 -> "${hours}h ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
    }
}

fun parseToTimestamp(text: String?): Long? {
    val raw = text?.trim().orEmpty()
    if (raw.isEmpty()) return null

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
                val d = sdf.parse(raw)
                if (d != null) return d.time
            } catch (_: Exception) {}
        }
    }
    return parseRelativeToTimestamp(raw)
}

private fun parseRelativeToTimestamp(text: String, now: Long = System.currentTimeMillis()): Long? {
    val n = text.lowercase(Locale.US)
        .replace("streamed", "")
        .replace("premiered", "")
        .replace("live", "")
        .replace("ago", "")
        .trim()
    if (n.isBlank()) return null
    if (n.contains("just now") || n.contains("today")) return now
    if (n.contains("yesterday")) return now - 86_400_000L

    val value = Regex("(\\d+)").find(n)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return null
    val unitMillis = when {
        n.contains("second") -> 1_000L
        n.contains("minute") -> 60_000L
        n.contains("hour") -> 3_600_000L
        n.contains("day") -> 86_400_000L
        n.contains("week") -> 7L * 86_400_000L
        n.contains("month") -> 30L * 86_400_000L
        n.contains("year") -> 365L * 86_400_000L
        else -> return null
    }
    return now - value * unitMillis
}
