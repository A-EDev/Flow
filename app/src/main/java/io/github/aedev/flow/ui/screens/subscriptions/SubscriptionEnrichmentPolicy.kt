package io.github.aedev.flow.ui.screens.subscriptions

import io.github.aedev.flow.data.model.Video

internal const val SUBSCRIPTION_ENRICHMENT_LOOKAHEAD = 4
internal const val MAX_VISIBLE_DURATION_CANDIDATES = 12

internal fun visibleSubscriptionEnrichmentWindow(
    videos: List<Video>,
    visibleVideoIds: Set<String>,
    lookahead: Int = SUBSCRIPTION_ENRICHMENT_LOOKAHEAD,
    maxCandidates: Int = MAX_VISIBLE_DURATION_CANDIDATES,
): List<Video> {
    if (videos.isEmpty() || visibleVideoIds.isEmpty() || maxCandidates <= 0) return emptyList()

    val lastVisibleIndex = videos.indexOfLast { it.id in visibleVideoIds }
    if (lastVisibleIndex < 0) return emptyList()

    val visible = videos.filter { it.id in visibleVideoIds }
    val trailing = videos
        .drop(lastVisibleIndex + 1)
        .take(lookahead.coerceAtLeast(0))

    return (visible + trailing)
        .distinctBy { it.id }
        .take(maxCandidates)
}

internal fun missingDurationCandidates(
    videos: List<Video>,
    attemptedAtMillis: Map<String, Long>,
    nowMillis: Long,
    retryAfterMillis: Long,
): List<Video> = videos.filter { video ->
    val lastAttempt = attemptedAtMillis[video.id]
    video.id.isNotBlank() &&
        video.duration <= 0 &&
        !video.isLive &&
        !video.isUpcoming &&
        (lastAttempt == null || nowMillis - lastAttempt >= retryAfterMillis)
}
