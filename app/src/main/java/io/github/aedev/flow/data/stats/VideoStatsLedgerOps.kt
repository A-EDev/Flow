package io.github.aedev.flow.data.stats

import java.time.ZoneId

/** One finished viewing session, as the player or the Shorts pager saw it. */
data class ViewEvent(
    val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val format: ViewFormat,
    val watchedMs: Long,
    /** A deliberate view: it counts toward views, channels, videos and topics. */
    val counted: Boolean,
    /** Abandoned early after a real look; named in the recap's "passed on" section. */
    val skipped: Boolean,
    val channelAvatarUrl: String = "",
    /** More time from a session already sent once; it adds time, not another session. */
    val continued: Boolean = false,
)

/** Pure mutations of the video ledger, called under the recorder's lock. No I/O, no Android. */
object VideoStatsLedgerOps {
    fun recordView(
        ledger: VideoStatsLedger,
        nowMs: Long,
        event: ViewEvent,
        topics: List<String>,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        if (event.videoId.isBlank() || (event.watchedMs <= 0L && !event.counted)) return
        val moment = LedgerTime.at(nowMs, zone)
        val month = ledger.months.getOrPut(moment.monthKey) { MonthViewing() }
        val watched = event.watchedMs.coerceAtLeast(0L)
        val channel = event.channelId.takeIf { it.isNotBlank() }

        if (!event.continued) month.sessions += 1
        month.watchedMs += watched
        month.formatMs.add(event.format, watched)
        if (watched > 0L) month.dayMs.add(moment.dayOfMonth, watched)
        channel?.let { month.channelMs.add(it, watched) }

        if (event.skipped) {
            month.videoSkips.add(event.videoId, 1)
            channel?.let { month.channelSkips.add(it, 1) }
            nameIn(month, event)
        }

        if (event.counted) {
            month.views += 1
            month.formatViews.add(event.format, 1)
            month.videoViews.add(event.videoId, 1)
            nameIn(month, event)
            topics.take(VideoStatsParams.TOPICS_PER_VIEW).forEach { month.topicViews.add(it, 1) }
            month.dayViews.add(moment.dayOfMonth, 1)
            month.hourViews.add(moment.hourOfDay, 1)
            if (channel != null) {
                month.channelViews.add(channel, 1)
                if (ledger.knownChannels.add(channel)) month.discoveredChannels.add(channel)
            }
        }
        tidy(ledger, month)
    }

    fun recordDislike(
        ledger: VideoStatsLedger,
        nowMs: Long,
        video: DislikedVideo,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        if (video.videoId.isBlank()) return
        val month = monthAt(ledger, nowMs, zone)
        month.dislikes.removeAll { it.videoId == video.videoId }
        month.dislikes.add(video)
        tidy(ledger, month)
    }

    /** Removes a dislike the user took back in the same month. */
    fun clearDislike(
        ledger: VideoStatsLedger,
        nowMs: Long,
        videoId: String,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        ledger.months[LedgerTime.at(nowMs, zone).monthKey]?.dislikes?.removeAll { it.videoId == videoId }
    }

    fun recordAction(
        ledger: VideoStatsLedger,
        nowMs: Long,
        action: LedgerAction,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        monthAt(ledger, nowMs, zone).actions.add(action, 1)
    }

    /** A submitted search. [query] is kept only when the caller says search history is on. */
    fun recordSearch(
        ledger: VideoStatsLedger,
        nowMs: Long,
        query: String?,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        val month = monthAt(ledger, nowMs, zone)
        month.actions.add(LedgerAction.SEARCH, 1)
        query
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?.let { month.queries.add(it, 1) }
        tidy(ledger, month)
    }

    fun recordSponsorSkip(
        ledger: VideoStatsLedger,
        nowMs: Long,
        category: String,
        skippedMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ) {
        if (category.isBlank() || skippedMs <= 0L) return
        monthAt(ledger, nowMs, zone).sponsorSkippedMs.add(category, skippedMs)
    }

    /** Forgets every stored search text, when search history is cleared or stops being kept. */
    fun clearQueries(ledger: VideoStatsLedger) {
        ledger.months.values.forEach { it.queries.clear() }
    }

    /** Applies every cap to every month; recording only tidies the month it touched. */
    fun prune(ledger: VideoStatsLedger) {
        LedgerTime.capMonths(ledger.months, VideoStatsParams.MONTHS_MAX)
        ledger.months.values.forEach(::pruneMonth)
    }

    private fun tidy(
        ledger: VideoStatsLedger,
        month: MonthViewing,
    ) {
        LedgerTime.capMonths(ledger.months, VideoStatsParams.MONTHS_MAX)
        pruneMonth(month)
    }

    private fun pruneMonth(month: MonthViewing) {
        LedgerTime.capWeakest(month.topicViews, VideoStatsParams.TOPICS_PER_MONTH)
        LedgerTime.capWeakest(month.queries, VideoStatsParams.QUERIES_PER_MONTH)
        LedgerTime.capWeakest(month.videoSkips, VideoStatsParams.SKIPS_PER_MONTH)
        LedgerTime.capWeakest(month.channelSkips, VideoStatsParams.SKIPS_PER_MONTH)
        LedgerTime.capWeakest(month.channelMs, VideoStatsParams.CHANNELS_PER_MONTH)
        LedgerTime.capWeakest(month.channelViews, VideoStatsParams.CHANNELS_PER_MONTH)
        LedgerTime.capWeakest(month.videoViews, VideoStatsParams.VIDEOS_PER_MONTH)
        if (month.discoveredChannels.size > VideoStatsParams.DISCOVERED_PER_MONTH) {
            val keep =
                month.discoveredChannels.sortedByDescending { month.channelViews[it] ?: 0 }.take(
                    VideoStatsParams.DISCOVERED_PER_MONTH,
                )
            month.discoveredChannels.retainAll(keep.toSet())
        }
        if (month.dislikes.size > VideoStatsParams.DISLIKES_PER_MONTH) {
            month.dislikes.sortBy { it.at }
            repeat(month.dislikes.size - VideoStatsParams.DISLIKES_PER_MONTH) { month.dislikes.removeAt(0) }
        }
        dropOrphanNames(month)
    }

    private fun dropOrphanNames(month: MonthViewing) {
        val videosInUse = month.videoViews.keys + month.videoSkips.keys
        month.videoTitles.keys.retainAll(videosInUse)
        month.videoChannels.keys.retainAll(videosInUse)
        val channelsInUse = month.channelViews.keys + month.channelSkips.keys + month.channelMs.keys + month.videoChannels.values
        month.channelNames.keys.retainAll(channelsInUse)
        month.channelAvatars.keys.retainAll(channelsInUse)
    }

    private fun nameIn(
        month: MonthViewing,
        event: ViewEvent,
    ) {
        if (event.title.isNotBlank()) month.videoTitles[event.videoId] = event.title
        val channel = event.channelId.takeIf { it.isNotBlank() } ?: return
        month.videoChannels[event.videoId] = channel
        if (event.channelName.isNotBlank()) month.channelNames[channel] = event.channelName
        if (event.channelAvatarUrl.isNotBlank()) month.channelAvatars[channel] = event.channelAvatarUrl
    }

    private fun monthAt(
        ledger: VideoStatsLedger,
        nowMs: Long,
        zone: ZoneId,
    ): MonthViewing = ledger.months.getOrPut(LedgerTime.at(nowMs, zone).monthKey) { MonthViewing() }

    private fun <K> MutableMap<K, Int>.add(
        key: K,
        amount: Int,
    ) {
        this[key] = (this[key] ?: 0) + amount
    }

    @JvmName("addLong")
    private fun <K> MutableMap<K, Long>.add(
        key: K,
        amount: Long,
    ) {
        this[key] = (this[key] ?: 0L) + amount
    }
}
