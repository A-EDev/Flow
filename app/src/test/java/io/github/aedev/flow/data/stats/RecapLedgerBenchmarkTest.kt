package io.github.aedev.flow.data.stats

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.music.MusicStatsParams
import io.github.aedev.flow.data.recommendation.music.MusicStatsStorage
import org.junit.Test
import java.time.YearMonth

/**
 * Both ledgers filled to every cap for all 36 months: the worst case a device can reach. Floors are
 * generous for a JVM on CI; the printed figures are the ones to compare between changes.
 */
class RecapLedgerBenchmarkTest {
    private val months = (0 until VideoStatsParams.MONTHS_MAX).map { YearMonth.of(2024, 1).plusMonths(it.toLong()) }

    private fun title(i: Int) = "A fairly long video title that goes on for a while, number $i"

    private val videoMonth =
        VideoMonthRecord(
            views = 4_000,
            sessions = 5_000,
            watchedMs = 400L * 3_600_000L,
            formatViews = ViewFormat.entries.associateWith { 1_000 },
            formatMs = ViewFormat.entries.associateWith { 100L * 3_600_000L },
            channelViews = (0 until VideoStatsParams.CHANNELS_PER_MONTH).associate { "UCchannel$it" to it + 1 },
            channelMs = (0 until VideoStatsParams.CHANNELS_PER_MONTH).associate { "UCchannel$it" to it * 60_000L },
            channelNames = (0 until VideoStatsParams.CHANNELS_PER_MONTH).associate { "UCchannel$it" to "Channel name $it" },
            videoViews = (0 until VideoStatsParams.VIDEOS_PER_MONTH).associate { "video$it" to it + 1 },
            videoTitles = (0 until VideoStatsParams.VIDEOS_PER_MONTH).associate { "video$it" to title(it) },
            videoChannels = (0 until VideoStatsParams.VIDEOS_PER_MONTH).associate { "video$it" to "UCchannel${it % 300}" },
            topicViews = (0 until VideoStatsParams.TOPICS_PER_MONTH).associate { "topic$it" to it + 1 },
            discoveredChannels = (0 until 100).map { "UCchannel$it" },
            videoSkips = (0 until VideoStatsParams.SKIPS_PER_MONTH).associate { "video$it" to 2 },
            channelSkips = (0 until VideoStatsParams.SKIPS_PER_MONTH).associate { "UCchannel$it" to 2 },
            dislikes = (0 until VideoStatsParams.DISLIKES_PER_MONTH).map { DislikedVideo("bad$it", title(it), "Channel $it", it.toLong()) },
            actions = LedgerAction.entries.associateWith { 40 },
            queries = (0 until VideoStatsParams.QUERIES_PER_MONTH).associate { "search query $it" to it + 1 },
            sponsorSkippedMs = mapOf("sponsor" to 3_600_000L, "intro" to 600_000L),
            dayViews = (1..31).associateWith { 100 },
            dayMs = (1..31).associateWith { 3_600_000L },
            hourViews = (0..23).associateWith { 150 },
        )

    private val musicMonth =
        MusicStatsStorage.SerializableMonth(
            plays = 3_000,
            sessions = 3_500,
            listenedMs = 200L * 3_600_000L,
            artistPlays = (0 until MusicStatsParams.ARTISTS_PER_MONTH).associate { "artist$it" to it + 1 },
            artistNames = (0 until MusicStatsParams.ARTISTS_PER_MONTH).associate { "artist$it" to "Artist name $it" },
            trackPlays = (0 until MusicStatsParams.TRACKS_PER_MONTH).associate { "track$it" to it + 1 },
            trackTitles = (0 until MusicStatsParams.TRACKS_PER_MONTH).associate { "track$it" to title(it) },
            genrePlays = (0 until MusicStatsParams.GENRES_PER_MONTH).associate { "genre$it" to it + 1 },
            discoveredArtists = (0 until 50).map { "artist$it" },
            dayPlays = (1..31).associateWith { 100 },
            hourPlays = (0..23).associateWith { 125 },
            dayMs = (1..31).associateWith { 3_600_000L },
            trackSkips = (0 until MusicStatsParams.SKIPS_PER_MONTH).associate { "track$it" to 2 },
            artistSkips = (0 until MusicStatsParams.SKIPS_PER_MONTH).associate { "artist$it" to 2 },
        )

    private val video = VideoStatsSnapshot(months = months.associate { LedgerTime.monthKey(it) to videoMonth })
    private val music = MusicStatsStorage.SerializableStats(months = months.associate { LedgerTime.monthKey(it) to musicMonth })

    private fun timeMs(block: () -> Unit): Long {
        repeat(WARMUP) { block() }
        return (0 until RUNS)
            .map {
                System.nanoTime().also { block() }.let { start ->
                    (System.nanoTime() - start) / 1_000_000
                }
            }.sorted()[
            RUNS /
                2,
        ]
    }

    @Test
    fun `folding every month at the caps stays well inside one screen open`() {
        val month = timeMs { RecapAggregates.summarize(RecapPeriod.Month(months.last()), video, music) }
        val year = timeMs { RecapAggregates.summarize(RecapPeriod.Year(months.last().year), video, music) }
        val allTime = timeMs { RecapAggregates.summarize(RecapPeriod.AllTime, video, music) }
        println("RecapLedgerBenchmark: month=${month}ms year=${year}ms allTime=${allTime}ms")

        assertThat(month).isLessThan(MONTH_FLOOR_MS)
        assertThat(allTime).isLessThan(ALL_TIME_FLOOR_MS)
    }

    @Test
    fun `the file rewritten on every save holds one month and stays small`() {
        val hot =
            LedgerJson.encodeToString(
                StoredLedger.serializer(VideoMonthRecord.serializer()),
                StoredLedger(
                    months =
                        mapOf("2026-09" to videoMonth),
                ),
            )
        val cold = LedgerJson.encodeToString(VideoStatsSnapshot.serializer(), video)
        val musicHot =
            LedgerJson.encodeToString(
                StoredLedger.serializer(MusicStatsStorage.SerializableMonth.serializer()),
                StoredLedger(months = mapOf("2026-09" to musicMonth)),
            )
        println(
            "RecapLedgerBenchmark: videoHot=${hot.length / 1024}KB videoCold=${cold.length / 1024}KB musicHot=${musicHot.length / 1024}KB",
        )

        assertThat(hot.length).isLessThan(HOT_FILE_FLOOR_BYTES)
        assertThat(musicHot.length).isLessThan(HOT_FILE_FLOOR_BYTES)
    }

    private companion object {
        const val WARMUP = 3
        const val RUNS = 5
        const val MONTH_FLOOR_MS = 100L
        const val ALL_TIME_FLOOR_MS = 1_000L
        const val HOT_FILE_FLOOR_BYTES = 120 * 1024
    }
}
