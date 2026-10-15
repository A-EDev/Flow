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
 * Lays a page out in short runs, so it reads as a mix rather than a subscriptions feed: two videos
 * from the other lanes, taking turns between related, discovery and remembered channels, then one or
 * two from followed channels, and again. Each kind keeps its own order and the first [keepTop]
 * (fresh uploads pinned on purpose) stay put. Followed-channel videos the rhythm has no room for close
 * the page, or with [deferExtra] are left for later pages once it holds [minSize] cards.
 */
internal fun rhythmBySource(
    videos: List<Video>,
    isSubscribed: (Video) -> Boolean,
    sourceOf: (Video) -> FeedSource? = { null },
    keepTop: Int = 0,
    deferExtra: Boolean = false,
    minSize: Int = 0,
): List<Video> {
    val rest = videos.drop(keepTop)
    val (subscribed, others) = rest.partition(isSubscribed)
    if (subscribed.isEmpty() || others.isEmpty()) return videos
    val subs = ArrayDeque(subscribed)
    val lanes = OTHER_LANE_TURNS.map { lane -> ArrayDeque(others.filter { sourceOf(it) == lane }) }
    val out = ArrayList<Video>(videos.size)
    out += videos.take(keepTop)
    var turn = 0
    var subsNext = keepTop == 0
    while (lanes.any { it.isNotEmpty() }) {
        if (subsNext && subs.isNotEmpty()) {
            val othersLeft = lanes.sumOf { it.size }
            repeat(if (subs.size * OTHER_RUN > othersLeft) MAX_SUBS_RUN else 1) { subs.removeFirstOrNull()?.let(out::add) }
        } else {
            while (lanes[turn % lanes.size].isEmpty()) turn++
            val lane = lanes[turn++ % lanes.size]
            repeat(OTHER_RUN) { lane.removeFirstOrNull()?.let(out::add) }
        }
        subsNext = !subsNext
    }
    while (subs.isNotEmpty() && (!deferExtra || out.size < minSize)) out += subs.removeFirst()
    return out
}

// Lanes take turns in this order; null holds videos whose lane is not known.
private val OTHER_LANE_TURNS =
    listOf(FeedSource.RELATED, FeedSource.DISCOVERY, FeedSource.CHANNEL_MEMORY, FeedSource.NEIGHBOURS, FeedSource.SUBS, null)
private const val OTHER_RUN = 2
private const val MAX_SUBS_RUN = 2

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

/**
 * Gives each search an even share of the first [limit] places before any search takes more, so one
 * broad subject cannot fill the search lane on its own. Rank order holds within each share.
 */
internal fun spreadAcrossSearches(
    videos: List<Video>,
    queryOf: (videoId: String) -> String?,
    limit: Int,
): List<Video> {
    val searches = videos.mapNotNullTo(HashSet()) { queryOf(it.id) }.size
    if (searches < 2) return videos.take(limit)
    val share = (limit + searches - 1) / searches
    val placed = HashMap<String, Int>()
    val (even, overflow) =
        videos.partition { video ->
            val query = queryOf(video.id) ?: return@partition true
            val count = placed.getOrDefault(query, 0)
            (count < share).also { within -> if (within) placed[query] = count + 1 }
        }
    return (even + overflow).take(limit)
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
