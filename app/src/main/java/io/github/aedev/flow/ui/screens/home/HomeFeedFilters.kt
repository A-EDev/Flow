package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video

internal const val FRESH_SUB_WINDOW_MS = 72L * 60L * 60L * 1000L
internal const val HOME_MAX_SUGGESTION_AGE_MS = 365L * 24L * 60L * 60L * 1000L

private const val STORED_SUBS_WINDOW_MS = 14L * 24L * 60L * 60L * 1000L
private const val STORED_SUBS_MAX = 60
private const val STORED_REELS_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L

/**
 * The subscription store's uploads Home can show as cards: every followed channel is in it, but a row
 * RSS wrote has no length until a channel tab or the player fills one in, and without it a Short
 * cannot be told from a video.
 */
internal fun List<Video>.storedSubscriptionVideos(now: Long): List<Video> =
    asSequence()
        .filter { !it.isShort && !it.isUpcoming && (it.duration > 0 || it.isLive) && (now - it.timestamp) in 0..STORED_SUBS_WINDOW_MS }
        .sortedByDescending { it.timestamp }
        .take(STORED_SUBS_MAX)
        .toList()

internal fun List<Video>.storedSubscriptionReels(now: Long): List<Video> =
    filter { it.isShort && (now - it.timestamp) in 0..STORED_REELS_WINDOW_MS }

/** Followed channels with a fresh upload the store still has no length for, newest upload first. */
internal fun List<Video>.channelsMissingLengths(now: Long): Set<String> =
    filter { !it.isShort && !it.isUpcoming && !it.isLive && it.duration <= 0 && (now - it.timestamp) in 0..FRESH_SUB_WINDOW_MS }
        .sortedByDescending { it.timestamp }
        .mapTo(LinkedHashSet()) { it.channelId }

/**
 * Adds videos that arrived after first paint without moving anything the viewer has seen: they go
 * below [lastVisibleIndex], one after every [spacing] cards already there. A video never lands
 * within [channelWindow] cards of its own channel, nor, when [isGrouped] holds for it, next to a card
 * it also holds for; one with no such place is left out.
 */
internal fun insertBelowViewport(
    feed: List<Video>,
    late: List<Video>,
    lastVisibleIndex: Int,
    spacing: Int = 2,
    channelWindow: Int = RECENT_CHANNEL_WINDOW,
    isGrouped: (Video) -> Boolean = { false },
): List<Video> {
    val onScreen = feed.mapTo(HashSet()) { it.id }
    val arriving = late.filter { it.id !in onScreen }.distinctBy { it.id }
    if (arriving.isEmpty()) return feed
    val split = (lastVisibleIndex + 1).coerceIn(0, feed.size)
    val below = feed.drop(split)
    val pending = arriving.toMutableList()
    val merged =
        buildList<Video>(feed.size + arriving.size) {
            fun nearby(
                video: Video,
                next: Int,
            ) = (
                video.channelId.isNotBlank() &&
                    (
                        takeLast(channelWindow).any { it.channelId == video.channelId } ||
                            below.subList(next, (next + channelWindow).coerceAtMost(below.size)).any { it.channelId == video.channelId }
                    )
            ) ||
                (isGrouped(video) && (lastOrNull()?.let(isGrouped) == true || below.getOrNull(next)?.let(isGrouped) == true))

            fun placeOne(next: Int) {
                val index = pending.indexOfFirst { !nearby(it, next) }
                if (index >= 0) add(pending.removeAt(index))
            }
            addAll(feed.take(split))
            below.forEachIndexed { index, video ->
                add(video)
                if ((index + 1) % spacing == 0) placeOne(index + 1)
            }
            while (pending.isNotEmpty() && pending.any { !nearby(it, below.size) }) placeOne(below.size)
        }
    return if (merged.size == feed.size) feed else merged
}

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

/** Keeps the ranked order but moves what was shown recently to the back, so a shelf rotates. */
internal fun List<Video>.recentlyShownLast(isRecentlyShown: (videoId: String) -> Boolean): List<Video> = sortedBy { isRecentlyShown(it.id) }

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
