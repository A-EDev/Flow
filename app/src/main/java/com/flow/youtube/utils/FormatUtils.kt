package com.flow.youtube.utils

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
        count >= 1_000_000_000 -> "${(count / 1_000_000_000.0).roundToInt()}B views"
        count >= 1_000_000 -> "${(count / 1_000_000.0).roundToInt()}M views"
        count >= 1_000 -> "${(count / 1_000.0).roundToInt()}K views"
        else -> "$count views"
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

fun formatTimeAgo(uploadDate: String): String {
    // This is a placeholder - in production, you'd parse the date properly
    return uploadDate
}

