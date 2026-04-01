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
    return when {
        count >= 1_000_000_000 -> "${(count / 1_000_000_000.0 * 10).roundToInt() / 10.0}B"
        count >= 1_000_000 -> "${(count / 1_000_000.0 * 10).roundToInt() / 10.0}M"
        count >= 1_000 -> "${(count / 1_000.0 * 10).roundToInt() / 10.0}K"
        else -> "$count"
    }
}

fun formatTimeAgo(dateString: String?): String {
    if (dateString.isNullOrBlank()) return ""
    
    // If it's already a relative time (like "16 hours ago"), return it
    if (dateString.contains(" ago") || dateString.contains("前")) return dateString

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
        val now = java.util.Date().time
        val diff = now - date.time
        
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val months = days / 30
        val years = days / 365
        
        when {
            years > 0 -> "${years}y ago"
            months > 0 -> "${months}mo ago"
            days > 0 -> "${days}d ago"
            hours > 0 -> "${hours}h ago"
            minutes > 0 -> "${minutes}m ago"
            else -> "Just now"
        }
    } catch (e: Exception) {
        dateString
    }
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
    return if (date != null) {
        val out = java.text.SimpleDateFormat("M/d/yy, h:mm a", java.util.Locale.US)
        out.timeZone = java.util.TimeZone.getDefault()
        out.format(date)
    } else {
        null
    }
}

