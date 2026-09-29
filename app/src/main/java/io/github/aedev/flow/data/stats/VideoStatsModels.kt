package io.github.aedev.flow.data.stats

import kotlinx.serialization.Serializable

/** How a video was watched. Local files and music are never in this ledger. */
enum class ViewFormat { LONG, SHORT, LIVE }

/** A thumbs-down, kept with enough to name it in a recap after the video is long gone. */
@Serializable
data class DislikedVideo(
    val videoId: String,
    val title: String,
    val channelName: String,
    val at: Long,
)

/** Library actions the recap counts per month. */
enum class LedgerAction { LIKE, SAVE, DOWNLOAD, NOT_INTERESTED, BLOCK_CHANNEL, SEARCH }

/**
 * One month of viewing, the video counterpart of the music ledger's month. Display names live in
 * the ledger for the same reason: the engine prunes, and a December recap must still name a March
 * channel.
 */
class MonthViewing(
    /** Views that counted: past the skip threshold, or a Short watched rather than swiped. */
    var views: Int = 0,
    /** Every session that produced watch time, skipped ones included. */
    var sessions: Int = 0,
    var watchedMs: Long = 0L,
    val formatViews: MutableMap<ViewFormat, Int> = HashMap(),
    val formatMs: MutableMap<ViewFormat, Long> = HashMap(),
    val channelViews: MutableMap<String, Int> = HashMap(),
    val channelMs: MutableMap<String, Long> = HashMap(),
    val channelNames: MutableMap<String, String> = HashMap(),
    /** Channel avatar URLs seen with a view, for the recap's portraits; never fetched just for it. */
    val channelAvatars: MutableMap<String, String> = HashMap(),
    val videoViews: MutableMap<String, Int> = HashMap(),
    val videoTitles: MutableMap<String, String> = HashMap(),
    val videoChannels: MutableMap<String, String> = HashMap(),
    val topicViews: MutableMap<String, Int> = HashMap(),
    /** Channels whose first view ever happened this month. */
    val discoveredChannels: MutableSet<String> = HashSet(),
    val videoSkips: MutableMap<String, Int> = HashMap(),
    val channelSkips: MutableMap<String, Int> = HashMap(),
    val dislikes: MutableList<DislikedVideo> = ArrayList(),
    val actions: MutableMap<LedgerAction, Int> = HashMap(),
    /** Search text -> times searched, only while search history is kept. */
    val queries: MutableMap<String, Int> = HashMap(),
    /** SponsorBlock category -> time it skipped. */
    val sponsorSkippedMs: MutableMap<String, Long> = HashMap(),
    val dayViews: MutableMap<Int, Int> = HashMap(),
    val dayMs: MutableMap<Int, Long> = HashMap(),
    val hourViews: MutableMap<Int, Int> = HashMap(),
)

class VideoStatsLedger {
    val months: MutableMap<String, MonthViewing> = HashMap()

    /** Channels in watch history when the ledger started, so they never count as discoveries. */
    val seedChannels: MutableSet<String> = HashSet()

    /** Every channel seen so far: the seed plus every month's channels. Rebuilt on load, never stored. */
    val knownChannels: MutableSet<String> = HashSet()

    fun rebuildKnownChannels() {
        knownChannels.clear()
        knownChannels.addAll(seedChannels)
        months.values.forEach { month ->
            knownChannels.addAll(month.channelViews.keys)
            knownChannels.addAll(month.discoveredChannels)
        }
    }
}

object VideoStatsParams {
    const val MONTHS_MAX = 36
    const val CHANNELS_PER_MONTH = 300
    const val VIDEOS_PER_MONTH = 300
    const val TOPICS_PER_MONTH = 60
    const val DISCOVERED_PER_MONTH = 500
    const val SKIPS_PER_MONTH = 150
    const val DISLIKES_PER_MONTH = 100
    const val QUERIES_PER_MONTH = 20
    const val TOPICS_PER_VIEW = 3
}

@Serializable
data class VideoMonthRecord(
    val views: Int = 0,
    val sessions: Int = 0,
    val watchedMs: Long = 0L,
    val formatViews: Map<ViewFormat, Int> = emptyMap(),
    val formatMs: Map<ViewFormat, Long> = emptyMap(),
    val channelViews: Map<String, Int> = emptyMap(),
    val channelMs: Map<String, Long> = emptyMap(),
    val channelNames: Map<String, String> = emptyMap(),
    val channelAvatars: Map<String, String> = emptyMap(),
    val videoViews: Map<String, Int> = emptyMap(),
    val videoTitles: Map<String, String> = emptyMap(),
    val videoChannels: Map<String, String> = emptyMap(),
    val topicViews: Map<String, Int> = emptyMap(),
    val discoveredChannels: List<String> = emptyList(),
    val videoSkips: Map<String, Int> = emptyMap(),
    val channelSkips: Map<String, Int> = emptyMap(),
    val dislikes: List<DislikedVideo> = emptyList(),
    val actions: Map<LedgerAction, Int> = emptyMap(),
    val queries: Map<String, Int> = emptyMap(),
    val sponsorSkippedMs: Map<String, Long> = emptyMap(),
    val dayViews: Map<Int, Int> = emptyMap(),
    val dayMs: Map<Int, Long> = emptyMap(),
    val hourViews: Map<Int, Int> = emptyMap(),
)

/** An immutable copy of the whole video ledger, for the recap surfaces and for backup. */
@Serializable
data class VideoStatsSnapshot(
    val months: Map<String, VideoMonthRecord> = emptyMap(),
    val seedChannels: List<String> = emptyList(),
)

fun MonthViewing.toRecord(): VideoMonthRecord =
    VideoMonthRecord(
        views = views,
        sessions = sessions,
        watchedMs = watchedMs,
        formatViews = formatViews.toMap(),
        formatMs = formatMs.toMap(),
        channelViews = channelViews.toMap(),
        channelMs = channelMs.toMap(),
        channelNames = channelNames.toMap(),
        channelAvatars = channelAvatars.toMap(),
        videoViews = videoViews.toMap(),
        videoTitles = videoTitles.toMap(),
        videoChannels = videoChannels.toMap(),
        topicViews = topicViews.toMap(),
        discoveredChannels = discoveredChannels.toList(),
        videoSkips = videoSkips.toMap(),
        channelSkips = channelSkips.toMap(),
        dislikes = dislikes.toList(),
        actions = actions.toMap(),
        queries = queries.toMap(),
        sponsorSkippedMs = sponsorSkippedMs.toMap(),
        dayViews = dayViews.toMap(),
        dayMs = dayMs.toMap(),
        hourViews = hourViews.toMap(),
    )

fun VideoMonthRecord.toMonth(): MonthViewing =
    MonthViewing(
        views = views,
        sessions = sessions,
        watchedMs = watchedMs,
        formatViews = HashMap(formatViews),
        formatMs = HashMap(formatMs),
        channelViews = HashMap(channelViews),
        channelMs = HashMap(channelMs),
        channelNames = HashMap(channelNames),
        channelAvatars = HashMap(channelAvatars),
        videoViews = HashMap(videoViews),
        videoTitles = HashMap(videoTitles),
        videoChannels = HashMap(videoChannels),
        topicViews = HashMap(topicViews),
        discoveredChannels = HashSet(discoveredChannels),
        videoSkips = HashMap(videoSkips),
        channelSkips = HashMap(channelSkips),
        dislikes = ArrayList(dislikes),
        actions = HashMap(actions),
        queries = HashMap(queries),
        sponsorSkippedMs = HashMap(sponsorSkippedMs),
        dayViews = HashMap(dayViews),
        dayMs = HashMap(dayMs),
        hourViews = HashMap(hourViews),
    )

fun VideoStatsLedger.toSnapshot(): VideoStatsSnapshot =
    VideoStatsSnapshot(
        months = months.mapValues { it.value.toRecord() },
        seedChannels = seedChannels.sorted(),
    )

fun VideoStatsSnapshot.toLedger(): VideoStatsLedger =
    VideoStatsLedger().also { ledger ->
        months.forEach { (key, record) -> ledger.months[key] = record.toMonth() }
        ledger.seedChannels.addAll(seedChannels)
        ledger.rebuildKnownChannels()
    }
