package io.github.aedev.flow.utils

import kotlin.math.roundToInt

fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, secs)
    } else {
        String.format("%d:%02d", minutes, secs)
    }
}

fun formatViewCount(count: Long): String {
    return when {
        count >= 1_000_000_000 -> "${(count / 1_000_000_000.0).roundToInt()}B"
        count >= 1_000_000 -> "${(count / 1_000_000.0).roundToInt()}M"
        count >= 1_000 -> "${(count / 1_000.0).roundToInt()}K"
        else -> "$count"
    }
}

fun formatSubscriberCount(count: Long): String {
    if (count <= 0L) return ""
    return when {
        count >= 1_000_000_000 -> "${(count / 1_000_000_000.0 * 10).roundToInt() / 10.0}B"
        count >= 1_000_000 -> "${(count / 1_000_000.0 * 10).roundToInt() / 10.0}M"
        count >= 1_000 -> "${(count / 1_000.0 * 10).roundToInt() / 10.0}K"
        else -> "$count"
    }
}

fun formatYouTubeRelativeTime(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diff = (nowMillis - timestampMillis).coerceAtLeast(0L)
    val seconds = diff / 1000L
    val minutes = seconds / 60L
    val hours = minutes / 60L
    val days = hours / 24L
    val weeks = days / 7L
    val months = days / 30L
    val years = days / 365L

    fun unit(value: Long, name: String): String =
        "$value $name${if (value == 1L) "" else "s"} ago"

    return when {
        years > 0L -> unit(years, "year")
        months > 0L -> unit(months, "month")
        weeks > 0L -> unit(weeks, "week")
        days > 0L -> unit(days, "day")
        hours > 0L -> unit(hours, "hour")
        minutes > 0L -> unit(minutes, "minute")
        else -> "Just now"
    }
}

fun formatTimeAgo(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    
    normalizeRelativeTimeText(dateString)?.let { return it }
    if (dateString.contains("前")) return dateString

    val formats = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd"
    )

    var date: java.util.Date? = null
    for (format in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(format, java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            date = sdf.parse(dateString)
            if (date != null) break
        } catch (e: Exception) {}
    }
    
    if (date == null) return dateString

    return try {
        formatYouTubeRelativeTime(date.time)
    } catch (e: Exception) {
        dateString
    }
}

private fun normalizeRelativeTimeText(value: String): String? {
    val text = value.trim()
    val lower = text.lowercase(java.util.Locale.US)
    if (!lower.contains("ago") && !lower.contains("just now")) return null
    val prefix = when {
        lower.startsWith("streamed ") -> "Streamed"
        lower.startsWith("premiered ") -> "Premiered"
        else -> null
    }
    if (lower.contains("just now")) return if (prefix != null) "$prefix just now" else "Just now"

    val match = Regex("""(\d+)\s*(mo|sec|secs|second|seconds|min|mins|minute|minutes|hr|hrs|hour|hours|day|days|week|weeks|month|months|year|years|[smhdwy])\b""")
        .find(lower) ?: return text
    val count = match.groupValues[1].toLongOrNull() ?: return text
    val unit = when (match.groupValues[2]) {
        "s", "sec", "secs", "second", "seconds" -> "second"
        "m", "min", "mins", "minute", "minutes" -> "minute"
        "h", "hr", "hrs", "hour", "hours" -> "hour"
        "d", "day", "days" -> "day"
        "w", "week", "weeks" -> "week"
        "mo", "month", "months" -> "month"
        "y", "year", "years" -> "year"
        else -> return text
    }
    val relative = "$count $unit${if (count == 1L) "" else "s"} ago"
    return if (prefix != null) "$prefix $relative" else relative
}

fun formatLikeCount(count: Int): String {
    return when {
        count >= 1_000_000 -> "${(count / 1_000_000.0 * 10).roundToInt() / 10.0}M"
        count >= 1_000 -> "${(count / 1_000.0 * 10).roundToInt() / 10.0}K"
        else -> "$count"
    }
}

/**
 * Formats a scheduled premiere date string (from NewPipe extractor) into YouTube-style:
 * "Premieres M/d/yy, h:mm a"  e.g. "Premieres 4/1/26, 9:00 AM"
 *
 * Returns "Premieres soon" if the date cannot be parsed.
 */
fun formatPremiereDate(dateString: String): String? {
    if (dateString.isBlank()) return null
    val date = parsePremiereDate(dateString) ?: return null
    val out = java.text.SimpleDateFormat("M/d/yy, h:mm a", java.util.Locale.US)
    out.timeZone = java.util.TimeZone.getDefault()
    return out.format(date)
}

fun parsePremiereTimestamp(dateString: String): Long? =
    parsePremiereDate(dateString)?.time

private fun parsePremiereDate(dateString: String): java.util.Date? {
    if (dateString.isBlank()) return null
    val formats = listOf(
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ssX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSX",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd"
    )
    var date: java.util.Date? = null
    for (fmt in formats) {
        try {
            val sdf = java.text.SimpleDateFormat(fmt, java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getDefault()
            date = sdf.parse(dateString)
            if (date != null) break
        } catch (_: Exception) {}
    }
    return date
}

