package io.github.aedev.flow.data.stats

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.music.MusicStatsStorage
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class RecapAggregatesTest {
    private val september = YearMonth.of(2026, 9)
    private val august = YearMonth.of(2026, 8)

    private fun videoMonth(
        views: Int = 0,
        watchedMs: Long = 0L,
        channelViews: Map<String, Int> = emptyMap(),
        topicViews: Map<String, Int> = emptyMap(),
        dayMs: Map<Int, Long> = emptyMap(),
        dayViews: Map<Int, Int> = emptyMap(),
        hourViews: Map<Int, Int> = emptyMap(),
        queries: Map<String, Int> = emptyMap(),
        discovered: List<String> = emptyList(),
        sponsor: Map<String, Long> = emptyMap(),
    ) = VideoMonthRecord(
        views = views,
        watchedMs = watchedMs,
        channelViews = channelViews,
        channelNames = channelViews.keys.associateWith { "Name $it" },
        topicViews = topicViews,
        dayMs = dayMs,
        dayViews = dayViews,
        hourViews = hourViews,
        queries = queries,
        discoveredChannels = discovered,
        sponsorSkippedMs = sponsor,
    )

    private fun video(vararg months: Pair<YearMonth, VideoMonthRecord>) =
        VideoStatsSnapshot(months = months.associate { LedgerTime.monthKey(it.first) to it.second })

    private fun music(vararg months: Pair<YearMonth, MusicStatsStorage.SerializableMonth>) =
        MusicStatsStorage.SerializableStats(months = months.associate { LedgerTime.monthKey(it.first) to it.second })

    @Test
    fun `a month shows only its own numbers and compares with the month before`() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(september to videoMonth(views = 5, watchedMs = 3_000_000L), august to videoMonth(views = 2, watchedMs = 1_000_000L)),
                music(september to MusicStatsStorage.SerializableMonth(plays = 4, listenedMs = 600_000L)),
            )

        assertThat(summary.video.views).isEqualTo(5)
        assertThat(summary.music.plays).isEqualTo(4)
        assertThat(summary.combined.totalMs).isEqualTo(3_600_000L)
        assertThat(summary.previousTotalMs).isEqualTo(1_000_000L)
    }

    @Test
    fun `a first month has nothing to compare with`() {
        val summary = RecapAggregates.summarize(RecapPeriod.Month(september), video(september to videoMonth(views = 1)), music())
        assertThat(summary.previousTotalMs).isNull()
    }

    @Test
    fun `a year folds its months and ranks across them`() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Year(2026),
                video(
                    august to videoMonth(views = 3, channelViews = mapOf("UCa" to 3)),
                    september to videoMonth(views = 4, channelViews = mapOf("UCa" to 1, "UCb" to 3)),
                    YearMonth.of(2025, 12) to videoMonth(views = 9, channelViews = mapOf("UCz" to 9)),
                ),
                music(),
            )

        assertThat(summary.video.views).isEqualTo(7)
        assertThat(summary.video.topChannels.map { it.id to it.count }).containsExactly("UCa" to 4, "UCb" to 3).inOrder()
        assertThat(
            summary.video.topChannels
                .first()
                .name,
        ).isEqualTo("Name UCa")
    }

    @Test
    fun `a streak runs across the end of a month`() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Year(2026),
                video(
                    august to videoMonth(dayMs = mapOf(30 to 60_000L, 31 to 60_000L)),
                    september to videoMonth(dayMs = mapOf(1 to 60_000L, 2 to 60_000L, 5 to 60_000L)),
                ),
                music(),
            )

        assertThat(summary.combined.longestStreak).isEqualTo(4)
        assertThat(summary.combined.activeDays).isEqualTo(5)
    }

    @Test
    fun `video and music days add up in the combined calendar`() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(september to videoMonth(dayMs = mapOf(3 to 60_000L))),
                music(september to MusicStatsStorage.SerializableMonth(dayMs = mapOf(3 to 30_000L, 4 to 10_000L))),
            )

        assertThat(summary.combined.dayMs[LocalDate.of(2026, 9, 3)]).isEqualTo(90_000L)
        assertThat(summary.combined.busiestDay?.first).isEqualTo(LocalDate.of(2026, 9, 3))
    }

    @Test
    fun `older music months spread their time over the days they have plays on`() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(),
                music(september to MusicStatsStorage.SerializableMonth(plays = 4, listenedMs = 400_000L, dayPlays = mapOf(1 to 3, 2 to 1))),
            )

        assertThat(summary.music.activity.dayMs[LocalDate.of(2026, 9, 1)]).isEqualTo(300_000L)
        assertThat(summary.music.activity.dayMs[LocalDate.of(2026, 9, 2)]).isEqualTo(100_000L)
    }

    @Test
    fun `topics new this month are the top ones last month never had`() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(
                    august to videoMonth(topicViews = mapOf("space" to 5)),
                    september to videoMonth(topicViews = mapOf("space" to 4, "cooking" to 2)),
                ),
                music(),
            )

        assertThat(summary.video.newTopics).containsExactly("cooking")
    }

    @Test
    fun `search texts older than the history's own limit are left out`() {
        val snapshot =
            video(
                august to videoMonth(queries = mapOf("old" to 1)),
                september to videoMonth(queries = mapOf("new" to 1)),
            )

        val kept = RecapAggregates.summarize(RecapPeriod.Year(2026), snapshot, music(), queriesSince = september)
        assertThat(kept.video.topQueries.map { it.id }).containsExactly("new")
    }

    @Test
    fun `traits need enough activity to be claimed`() {
        val late = mapOf(23 to 15, 0 to 10)
        val quiet =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(september to videoMonth(hourViews = mapOf(23 to 2))),
                music(),
            )
        val owl = RecapAggregates.summarize(RecapPeriod.Month(september), video(september to videoMonth(hourViews = late)), music())

        assertThat(quiet.insights).doesNotContain(RecapInsight.NIGHT_OWL)
        assertThat(owl.insights).contains(RecapInsight.NIGHT_OWL)
    }

    @Test
    fun `sponsor skips of ten minutes or more earn the time saver trait`() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(september to videoMonth(sponsor = mapOf("sponsor" to 400_000L, "intro" to 200_000L))),
                music(),
            )

        assertThat(summary.video.sponsorSavedMs).isEqualTo(600_000L)
        assertThat(summary.insights).contains(RecapInsight.TIME_SAVER)
    }

    @Test
    fun `available months come from both ledgers, newest first`() {
        val months =
            RecapAggregates.availableMonths(
                video(august to videoMonth(views = 1)),
                music(
                    september to MusicStatsStorage.SerializableMonth(plays = 1),
                    august to MusicStatsStorage.SerializableMonth(plays = 1),
                ),
            )

        assertThat(months).containsExactly(september, august).inOrder()
    }

    @Test
    fun `nothing recorded is an empty recap`() {
        assertThat(RecapAggregates.summarize(RecapPeriod.AllTime, video(), music()).isEmpty).isTrue()
    }

    @Test
    fun `ranked items carry their portraits`() {
        val summary =
            RecapAggregates.summarize(
                RecapPeriod.Month(september),
                video(
                    september to
                        VideoMonthRecord(
                            views = 2,
                            channelViews = mapOf("UCa" to 2),
                            channelAvatars = mapOf("UCa" to "https://yt3/avatar"),
                            videoViews = mapOf("vid1" to 2),
                        ),
                ),
                music(
                    september to
                        MusicStatsStorage.SerializableMonth(
                            plays = 1,
                            artistPlays = mapOf("a1" to 1),
                            artistArt = mapOf("a1" to "https://art/a1"),
                            trackPlays = mapOf("t1" to 1),
                            trackArt = mapOf("t1" to "https://art/t1"),
                        ),
                ),
            )

        assertThat(
            summary.video.topChannels
                .single()
                .imageUrl,
        ).isEqualTo("https://yt3/avatar")
        assertThat(
            summary.video.topVideos
                .single()
                .imageUrl,
        ).contains("vid1")
        assertThat(
            summary.music.topArtists
                .single()
                .imageUrl,
        ).isEqualTo("https://art/a1")
        assertThat(
            summary.music.topTracks
                .single()
                .imageUrl,
        ).isEqualTo("https://art/t1")
    }

    @Test
    fun `a month that straddles per-day minutes keeps its older days`() {
        val month =
            MusicStatsStorage.SerializableMonth(
                plays = 6,
                listenedMs = 460_000L,
                dayPlays = mapOf(1 to 3, 2 to 1, 23 to 2),
                dayMs =
                    mapOf(23 to 60_000L),
            )
        val summary = RecapAggregates.summarize(RecapPeriod.Month(september), video(), music(september to month))

        assertThat(summary.music.activity.activeDays).isEqualTo(3)
        assertThat(summary.music.activity.dayMs[LocalDate.of(2026, 9, 1)]).isEqualTo(300_000L)
        assertThat(summary.music.activity.dayMs[LocalDate.of(2026, 9, 2)]).isEqualTo(100_000L)
        assertThat(summary.music.activity.dayMs[LocalDate.of(2026, 9, 23)]).isEqualTo(60_000L)
    }

    @Test
    fun `a view count is never listed as an artist`() {
        val month =
            MusicStatsStorage.SerializableMonth(
                plays = 3,
                artistPlays = mapOf("34m views" to 2, "a1" to 1),
                artistNames = mapOf("34m views" to "34M views", "a1" to "Real Artist"),
                discoveredArtists = listOf("34m views", "a1"),
            )
        val summary = RecapAggregates.summarize(RecapPeriod.Month(september), video(), music(september to month))

        assertThat(summary.music.topArtists.map { it.name }).containsExactly("Real Artist")
        assertThat(summary.music.discoveredArtists.map { it.name }).containsExactly("Real Artist")
    }
}
