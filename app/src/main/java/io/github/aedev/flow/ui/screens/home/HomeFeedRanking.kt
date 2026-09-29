package io.github.aedev.flow.ui.screens.home

import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.recommendation.FlowPersona
import io.github.aedev.flow.data.recommendation.UserBrain
import kotlinx.coroutines.flow.map

// Format signals often tied to low-effort feed filler. NOT a blocklist: they only demote
// exploration candidates, and only when the user shows no matching interest.
internal val FEED_FORMAT_MARKERS =
    listOf(
        "compilation",
        "satisfying",
        "hour of",
        "hours of",
        "best of",
        "ending explained",
        "full movie",
        "full episode",
        "marathon",
        "movie recap",
        "series recap",
        "all parts",
    )

// Per-persona long-form comfort. 0 ⇒ no duration demotion (the user watches long content).
internal const val DURATION_COMFORT_DEFAULT_SEC = 3600 // 60 min: generic browse comfort
private const val DURATION_COMFORT_SKIMMER_SEC = 1500 // 25 min: fast-content persona
private const val FIT_PENALTY_WEIGHT = 0.6 // how hard a poor fit demotes engine rank

/** Per-user feed taste, read from the learned brain — drives demotion, never a global ban. */
internal data class FeedTasteProfile(
    val comfortDurationSec: Int,
    val affinityTopics: Set<String>,
)

internal fun feedTasteProfile(
    brain: UserBrain,
    persona: FlowPersona,
): FeedTasteProfile {
    val comfort =
        when (persona) {
            FlowPersona.DEEP_DIVER, FlowPersona.SCHOLAR,
            FlowPersona.BINGER, FlowPersona.AUDIOPHILE,
            -> 0

            FlowPersona.SKIMMER -> DURATION_COMFORT_SKIMMER_SEC

            else -> DURATION_COMFORT_DEFAULT_SEC
        }
    val affinity =
        (brain.topicAffinities.filterValues { it > 0.0 }.keys + brain.preferredTopics)
            .mapNotNull { it.lowercase().takeIf(String::isNotBlank) }
            .toSet()
    return FeedTasteProfile(comfort, affinity)
}

/** 0 = good fit for this user; →1 = poor fit. Demotes exploration candidates, never drops them. */
internal fun feedFitPenalty(
    video: Video,
    profile: FeedTasteProfile,
): Double {
    var penalty = 0.0
    val cap = profile.comfortDurationSec
    if (cap > 0 && video.duration > cap) {
        val over = (video.duration - cap).toDouble() / cap
        penalty += (0.5 * over).coerceAtMost(0.6)
    }
    val title = video.title.lowercase()
    if (FEED_FORMAT_MARKERS.any { title.contains(it) } &&
        profile.affinityTopics.none { title.contains(it) }
    ) {
        penalty += 0.4
    }
    return penalty.coerceAtMost(1.0)
}

/** Stable re-rank pushing poor-fit items below well-fit ones while preserving engine order. */
internal fun demoteByFit(
    ranked: List<Video>,
    profile: FeedTasteProfile,
): List<Video> {
    if (ranked.size < 2) return ranked
    val n = ranked.size.toDouble()
    return ranked
        .withIndex()
        .sortedByDescending { (i, v) -> (1.0 - i / n) - FIT_PENALTY_WEIGHT * feedFitPenalty(v, profile) }
        .map { it.value }
}

/**
 * Greedy reorder that keeps same-channel items at least `gap` slots apart when possible; order is
 * otherwise preserved. seedRecent primes the cooldown with the prior page's tail to space appends.
 */
internal fun spaceByChannel(
    videos: List<Video>,
    gap: Int = 1,
    seedRecent: List<String> = emptyList(),
): List<Video> {
    if (videos.size < 2) return videos
    val remaining = videos.toMutableList()
    val out = ArrayList<Video>(videos.size)
    val recent = ArrayDeque<String>()
    seedRecent.takeLast(gap).forEach { recent.addLast(it) }
    while (remaining.isNotEmpty()) {
        val idx =
            remaining
                .indexOfFirst { it.channelId.isBlank() || it.channelId !in recent }
                .let { if (it < 0) 0 else it }
        val pick = remaining.removeAt(idx)
        out.add(pick)
        if (pick.channelId.isNotBlank()) {
            recent.addLast(pick.channelId)
            while (recent.size > gap) recent.removeFirst()
        }
    }
    return out
}

internal fun Video.withChannelMetadataFrom(enriched: Video): Video {
    val avatarUrl = enriched.channelThumbnailUrl.ifBlank { channelThumbnailUrl }
    return copy(
        channelId = enriched.channelId.ifBlank { channelId },
        channelName = enriched.channelName.ifBlank { channelName },
        channelThumbnailUrl = avatarUrl,
        channelThumbnailUrls =
            if (avatarUrl.isNotBlank()) {
                (
                    listOf(avatarUrl) +
                        enriched.channelThumbnailUrls +
                        channelThumbnailUrls
                ).distinct()
            } else {
                channelThumbnailUrls
            },
    )
}
