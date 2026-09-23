package io.github.aedev.flow.data.stats

import io.github.aedev.flow.data.recommendation.music.MusicStatsStorage
import io.github.aedev.flow.utils.ThumbnailUrlResolver
import java.time.LocalDate
import java.time.YearMonth

/**
 * Folds the two ledgers into what one period shows. Pure and allocation-light: the recap screens
 * call [summarize] once per period they display, off the main thread, and render only its result.
 */
internal object RecapAggregates {
    const val TOP_COUNT = 10

    private const val MIN_EVENTS_FOR_TRAITS = 20
    private const val NIGHT_SHARE = 0.30
    private const val MORNING_SHARE = 0.25
    private const val WEEKEND_SHARE = 0.45
    private const val LOYAL_SHARE = 0.30
    private const val LOYAL_MIN_VIEWS = 10
    private const val EXPLORER_MIN = 10
    private const val MARATHON_MS = 3 * 60 * 60 * 1000L
    private const val TIME_SAVER_MS = 10 * 60 * 1000L
    private val NightHours = setOf(22, 23, 0, 1, 2, 3)
    private val MorningHours = 5..8
    private val ViewCountName = Regex("""^[\d.,]+\s?[KMBkmb]?\s+views?$""", RegexOption.IGNORE_CASE)
    private const val TRACK_ART_SIZE = 544

    /** Every month either ledger holds, newest first. */
    fun availableMonths(
        video: VideoStatsSnapshot,
        music: MusicStatsStorage.SerializableStats,
    ): List<YearMonth> =
        (video.months.keys + music.months.keys)
            .mapNotNull(LedgerTime::parseMonth)
            .distinct()
            .sortedDescending()

    /**
     * [queriesSince] drops search texts from months before it, for the search history's own
     * auto-delete; null keeps every stored query.
     */
    fun summarize(
        period: RecapPeriod,
        video: VideoStatsSnapshot,
        music: MusicStatsStorage.SerializableStats,
        queriesSince: YearMonth? = null,
    ): RecapSummary {
        val videoMonths = video.months.inPeriod(period)
        val musicMonths = music.months.inPeriod(period)
        val previousVideo = period.previous()?.let { video.months.inPeriod(it) }
        val previousMusic = period.previous()?.let { music.months.inPeriod(it) }

        val videoRecap = videoRecap(videoMonths, previousVideo.orEmpty(), queriesSince)
        val musicRecap = musicRecap(musicMonths)
        val combined = combine(videoRecap.activity, musicRecap.activity)
        val previousTotal =
            if (previousVideo.isNullOrEmpty() && previousMusic.isNullOrEmpty()) {
                null
            } else {
                previousVideo.orEmpty().sumOf { it.second.watchedMs } + previousMusic.orEmpty().sumOf { it.second.listenedMs }
            }
        return RecapSummary(
            period = period,
            video = videoRecap,
            music = musicRecap,
            combined = combined,
            previousTotalMs = previousTotal,
            insights = insights(videoRecap, musicRecap, combined),
        )
    }

    /** Watching and listening time per day from [from] to [to], both ledgers together. */
    fun dailyTime(
        video: VideoStatsSnapshot,
        music: MusicStatsStorage.SerializableStats,
        from: LocalDate,
        to: LocalDate,
    ): Map<LocalDate, Long> {
        val days = HashMap<LocalDate, Long>()

        fun add(
            key: String,
            dayMs: Map<Int, Long>,
        ) {
            val month = LedgerTime.parseMonth(key) ?: return
            dayMs.forEach { (day, ms) ->
                val date = month.dateOrNull(day) ?: return@forEach
                if (date in from..to) days[date] = (days[date] ?: 0L) + ms
            }
        }
        video.months.forEach { (key, month) -> add(key, month.dayMs) }
        music.months.forEach { (key, month) -> add(key, musicDayMs(month)) }
        return days
    }

    /** The first day the video ledger holds anything; before it, only watch history knows. */
    fun videoLedgerStart(video: VideoStatsSnapshot): LocalDate? =
        video.months
            .mapNotNull { (key, month) ->
                val parsed = LedgerTime.parseMonth(key) ?: return@mapNotNull null
                month.dayMs.keys
                    .minOrNull()
                    ?.let { parsed.dateOrNull(it) }
            }.minOrNull()

    /** Some music results arrive with their view count where the artist belongs ("34M views"). */
    private fun String.looksLikeViewCount(): Boolean = ViewCountName.matches(trim())

    private fun <M> Map<String, M>.inPeriod(period: RecapPeriod): List<Pair<YearMonth, M>> =
        mapNotNull { (key, month) -> LedgerTime.parseMonth(key)?.takeIf(period::contains)?.let { it to month } }
            .sortedBy { it.first }

    private fun videoRecap(
        months: List<Pair<YearMonth, VideoMonthRecord>>,
        previous: List<Pair<YearMonth, VideoMonthRecord>>,
        queriesSince: YearMonth?,
    ): VideoRecap {
        val channelNames = HashMap<String, String>()
        val videoTitles = HashMap<String, String>()
        val videoChannels = HashMap<String, String>()
        val channelAvatars = HashMap<String, String>()
        months.forEach { (_, m) ->
            channelNames.putAll(m.channelNames)
            channelAvatars.putAll(m.channelAvatars)
            videoTitles.putAll(m.videoTitles)
            videoChannels.putAll(m.videoChannels)
        }
        val channelViews = months.sumCounts { it.channelViews }
        val channelMs = months.sumDurations { it.channelMs }

        fun channel(id: String) = channelNames[id].orEmpty().ifBlank { id }

        fun channelItem(
            id: String,
            count: Int,
        ) = RankedItem(id, channel(id), count, channelMs[id] ?: 0L, imageUrl = channelAvatars[id].orEmpty())

        fun videoItem(
            id: String,
            count: Int,
        ) = RankedItem(
            id = id,
            name = videoTitles[id].orEmpty(),
            count = count,
            detail = videoChannels[id]?.let(::channel).orEmpty(),
            imageUrl = ThumbnailUrlResolver.buildHighQualityYoutubeThumbnail(id),
        )

        val topics = months.sumCounts { it.topicViews }
        val topTopics = topics.ranked(TOP_COUNT) { id, count -> RankedItem(id, id, count) }
        val previousTopics = previous.flatMap { it.second.topicViews.keys }.toSet()
        val discovered = months.flatMap { it.second.discoveredChannels }.toSet()

        return VideoRecap(
            views = months.sumOf { it.second.views },
            sessions = months.sumOf { it.second.sessions },
            activity =
                activity(
                    months = months,
                    totalMs = months.sumOf { it.second.watchedMs },
                    dayMs = { it.dayMs },
                    dayCounts = { it.dayViews },
                    hourCounts = { it.hourViews },
                ),
            formatViews = months.sumCounts { it.formatViews },
            formatMs = months.sumDurations { it.formatMs },
            topChannels = channelViews.ranked(TOP_COUNT, ::channelItem),
            topVideos = months.sumCounts { it.videoViews }.ranked(TOP_COUNT, ::videoItem),
            topTopics = topTopics,
            newTopics = if (previous.isEmpty()) emptyList() else topTopics.map { it.id }.filterNot(previousTopics::contains),
            discoveredChannels =
                discovered
                    .associateWith { channelViews[it] ?: 0 }
                    .ranked(TOP_COUNT * 3, ::channelItem),
            skippedVideos = months.sumCounts { it.videoSkips }.ranked(TOP_COUNT, ::videoItem),
            skippedChannels = months.sumCounts { it.channelSkips }.ranked(TOP_COUNT, ::channelItem),
            dislikes = months.flatMap { it.second.dislikes }.sortedByDescending { it.at },
            actions = months.sumCounts { it.actions },
            topQueries =
                months
                    .filter { queriesSince == null || !it.first.isBefore(queriesSince) }
                    .sumCounts { it.queries }
                    .ranked(TOP_COUNT) { id, count -> RankedItem(id, id, count) },
            sponsorSkippedMs = months.sumDurations { it.sponsorSkippedMs },
        )
    }

    private fun musicRecap(months: List<Pair<YearMonth, MusicStatsStorage.SerializableMonth>>): MusicRecap {
        val artistNames = HashMap<String, String>()
        val trackTitles = HashMap<String, String>()
        val trackArt = HashMap<String, String>()
        val artistArt = HashMap<String, String>()
        months.forEach { (_, m) ->
            artistNames.putAll(m.artistNames)
            trackTitles.putAll(m.trackTitles)
            trackArt.putAll(m.trackArt)
            artistArt.putAll(m.artistArt)
        }

        fun artist(key: String) = artistNames[key].orEmpty().ifBlank { key }

        fun artistItem(
            key: String,
            count: Int,
        ) = RankedItem(key, artist(key), count, imageUrl = artistArt[key].orEmpty())

        fun trackItem(
            id: String,
            count: Int,
        ) = RankedItem(
            id = id,
            name = trackTitles[id].orEmpty().ifBlank { id },
            count = count,
            imageUrl = ThumbnailUrlResolver.resolveMusicThumbnail(id, trackArt[id], TRACK_ART_SIZE),
        )

        fun datedArtists(pick: (MusicStatsStorage.SerializableMonth) -> Map<String, Long>) =
            months
                .flatMap { pick(it.second).entries }
                .sortedByDescending { it.value }
                .distinctBy { it.key }
                .map { artistItem(it.key, 1) }

        val artistPlays = months.sumCounts { it.artistPlays }.filterKeys { !artist(it).looksLikeViewCount() }
        return MusicRecap(
            plays = months.sumOf { it.second.plays },
            sessions = months.sumOf { it.second.sessions },
            activity =
                activity(
                    months = months,
                    totalMs = months.sumOf { it.second.listenedMs },
                    dayMs = ::musicDayMs,
                    dayCounts = { it.dayPlays },
                    hourCounts = { it.hourPlays },
                ),
            topArtists = artistPlays.ranked(TOP_COUNT, ::artistItem),
            topTracks = months.sumCounts { it.trackPlays }.ranked(TOP_COUNT, ::trackItem),
            topGenres = months.sumCounts { it.genrePlays }.ranked(TOP_COUNT) { id, count -> RankedItem(id, id, count) },
            discoveredArtists =
                months
                    .flatMap { it.second.discoveredArtists }
                    .filterNot { artist(it).looksLikeViewCount() }
                    .toSet()
                    .associateWith { artistPlays[it] ?: 0 }
                    .ranked(TOP_COUNT * 3, ::artistItem),
            skippedTracks = months.sumCounts { it.trackSkips }.ranked(TOP_COUNT, ::trackItem),
            skippedArtists = months.sumCounts { it.artistSkips }.ranked(TOP_COUNT, ::artistItem),
            dislikedArtists = datedArtists { it.dislikedArtists },
            blockedArtists = datedArtists { it.blockedArtists },
        )
    }

    /**
     * Listening time per day. Days recorded before minutes were kept per day only have play counts,
     * so the month's time not yet placed on a day is spread over those days by their plays; a month
     * that straddles the change keeps both kinds of day.
     */
    internal fun musicDayMs(month: MusicStatsStorage.SerializableMonth): Map<Int, Long> {
        val legacyDays = month.dayPlays.filterKeys { it !in month.dayMs }
        val legacyPlays = legacyDays.values.sum()
        val unplaced = (month.listenedMs - month.dayMs.values.sum()).coerceAtLeast(0L)
        if (legacyPlays == 0 || unplaced == 0L) return month.dayMs
        return month.dayMs + legacyDays.mapValues { (_, count) -> unplaced * count / legacyPlays }
    }

    private fun <M> activity(
        months: List<Pair<YearMonth, M>>,
        totalMs: Long,
        dayMs: (M) -> Map<Int, Long>,
        dayCounts: (M) -> Map<Int, Int>,
        hourCounts: (M) -> Map<Int, Int>,
    ): ActivityPattern {
        val days = HashMap<LocalDate, Long>()
        val hours = IntArray(24)
        val weekdays = IntArray(7)
        months.forEach { (month, record) ->
            dayMs(record).forEach { (day, ms) -> month.dateOrNull(day)?.let { days[it] = (days[it] ?: 0L) + ms } }
            dayCounts(record).forEach { (day, count) ->
                month.dateOrNull(day)?.let { weekdays[ActivityPattern.weekdayIndex(it.dayOfWeek)] += count }
            }
            hourCounts(record).forEach { (hour, count) -> if (hour in 0..23) hours[hour] += count }
        }
        return ActivityPattern(totalMs, days, hours.toList(), weekdays.toList())
    }

    private fun YearMonth.dateOrNull(day: Int): LocalDate? = if (isValidDay(day)) atDay(day) else null

    private fun combine(
        video: ActivityPattern,
        music: ActivityPattern,
    ): ActivityPattern {
        val days = HashMap(video.dayMs)
        music.dayMs.forEach { (day, ms) -> days[day] = (days[day] ?: 0L) + ms }
        return ActivityPattern(
            totalMs = video.totalMs + music.totalMs,
            dayMs = days,
            hourCounts = video.hourCounts.zip(music.hourCounts, Int::plus),
            weekdayCounts = video.weekdayCounts.zip(music.weekdayCounts, Int::plus),
        )
    }

    private fun insights(
        video: VideoRecap,
        music: MusicRecap,
        combined: ActivityPattern,
    ): List<RecapInsight> {
        val events = combined.hourCounts.sum()
        val traits = mutableListOf<RecapInsight>()
        if (events >= MIN_EVENTS_FOR_TRAITS) {
            val night = NightHours.sumOf { combined.hourCounts[it] }.toDouble() / events
            val morning = MorningHours.sumOf { combined.hourCounts[it] }.toDouble() / events
            val weekend = (combined.weekdayCounts[5] + combined.weekdayCounts[6]).toDouble() / combined.weekdayCounts.sum().coerceAtLeast(1)
            if (night >= NIGHT_SHARE) traits += RecapInsight.NIGHT_OWL
            if (morning >= MORNING_SHARE) traits += RecapInsight.EARLY_BIRD
            if (weekend >= WEEKEND_SHARE) traits += RecapInsight.WEEKEND_WATCHER
        }
        val topChannelViews = video.topChannels.firstOrNull()?.count ?: 0
        if (video.views >= LOYAL_MIN_VIEWS && topChannelViews.toDouble() / video.views >= LOYAL_SHARE) traits += RecapInsight.LOYAL
        if (video.discoveredChannels.size + music.discoveredArtists.size >= EXPLORER_MIN) traits += RecapInsight.EXPLORER
        if ((combined.busiestDay?.second ?: 0L) >= MARATHON_MS) traits += RecapInsight.MARATHON
        if (video.sponsorSavedMs >= TIME_SAVER_MS) traits += RecapInsight.TIME_SAVER
        return traits
    }

    private fun <M, K> List<Pair<YearMonth, M>>.sumCounts(pick: (M) -> Map<K, Int>): Map<K, Int> {
        val sums = HashMap<K, Int>()
        forEach { (_, month) -> pick(month).forEach { (key, count) -> sums[key] = (sums[key] ?: 0) + count } }
        return sums
    }

    private fun <M, K> List<Pair<YearMonth, M>>.sumDurations(pick: (M) -> Map<K, Long>): Map<K, Long> {
        val sums = HashMap<K, Long>()
        forEach { (_, month) -> pick(month).forEach { (key, ms) -> sums[key] = (sums[key] ?: 0L) + ms } }
        return sums
    }

    private fun Map<String, Int>.ranked(
        limit: Int,
        item: (String, Int) -> RankedItem,
    ): List<RankedItem> =
        entries
            .asSequence()
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { item(it.key, it.value) }
            .toList()
}
