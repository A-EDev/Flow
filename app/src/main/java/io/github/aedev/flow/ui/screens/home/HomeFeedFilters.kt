package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

internal const val FRESH_SUB_WINDOW_MS = 72L * 60L * 60L * 1000L
internal const val HOME_MAX_SUGGESTION_AGE_MS = 365L * 24L * 60L * 60L * 1000L

internal fun dynamicFreshSubSlots(subCount: Int): Int =
    when {
        subCount >= 120 -> 5
        subCount >= 40 -> 4
        subCount >= 5 -> 3
        else -> 2
    }

internal fun isFreshSubscribedCandidate(
    video: Video,
    now: Long,
): Boolean {
    val ageByTimestamp = now - video.timestamp
    if (ageByTimestamp in 0..FRESH_SUB_WINDOW_MS) return true

    val text = video.uploadDate.lowercase()
    if (text.contains("second") || text.contains("minute") || text.contains("hour")) {
        return true
    }

    if (text.contains("day")) {
        val days = text.filter { it.isDigit() }.toIntOrNull() ?: 1
        return days <= 3
    }

    return false
}

internal fun List<Video>.filterValid(): List<Video> =
    this.filter {
        !it.isShort && (it.duration > 0 || it.isLive)
    }

internal fun List<GraphCandidate>.filterValidGraph(): List<GraphCandidate> =
    filter { candidate ->
        !candidate.video.isShort && (candidate.video.duration > 0 || candidate.video.isLive)
    }

/**
 * Filter that extracts shorts from a video list for the shelf.
 * Complements filterValid() by capturing what it discards.
 */
internal fun List<Video>.extractShorts(): List<Video> = this.filter { it.isShort }

internal fun List<Video>.filterRecentHomeSuggestion(now: Long): List<Video> = filter { video -> isRecentHomeSuggestion(video, now) }

internal fun List<GraphCandidate>.filterRecentHomeSuggestionGraph(now: Long): List<GraphCandidate> =
    filter { candidate -> isRecentHomeSuggestion(candidate.video, now) }

internal fun isRecentHomeSuggestion(
    video: Video,
    now: Long,
): Boolean {
    val text = video.uploadDate.lowercase()
    if (text.isBlank() || text == "unknown") return video.isLive

    val age = now - video.timestamp
    if (age in 0..HOME_MAX_SUGGESTION_AGE_MS) return true

    val value = text.filter { it.isDigit() }.toIntOrNull() ?: 1
    return when {
        text.contains("second") || text.contains("minute") || text.contains("hour") -> true
        text.contains("day") -> value <= 365
        text.contains("week") -> value <= 52
        text.contains("month") -> value <= 12
        text.contains("year") -> value <= 1
        else -> false
    }
}

/**
 * Remove videos the user has already fully watched (≥90 % progress)
 * so they don't re-appear in the home feed.
 */
internal fun List<Video>.filterWatched(watchedIds: Set<String>): List<Video> {
    if (watchedIds.isEmpty()) return this
    return this.filter { !watchedIds.contains(it.id) }
}

internal fun List<GraphCandidate>.filterWatchedGraph(watchedIds: Set<String>): List<GraphCandidate> {
    if (watchedIds.isEmpty()) return this
    return filter { !watchedIds.contains(it.video.id) }
}
