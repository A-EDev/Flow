package io.github.aedev.flow.ui.screens.recap.story

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.music.MusicStatsStorage
import io.github.aedev.flow.data.stats.LedgerTime
import io.github.aedev.flow.data.stats.RecapAggregates
import io.github.aedev.flow.data.stats.RecapPeriod
import io.github.aedev.flow.data.stats.VideoMonthRecord
import io.github.aedev.flow.data.stats.VideoStatsSnapshot
import io.github.aedev.flow.data.stats.ViewFormat
import io.github.aedev.flow.ui.screens.recap.RecapRoutes
import org.junit.Test
import java.time.YearMonth

class RecapStoryTest {
    private val september = YearMonth.of(2026, 9)

    @Test
    fun `periods survive the route and back`() {
        listOf(RecapPeriod.Month(september), RecapPeriod.Year(2026), RecapPeriod.AllTime).forEach { period ->
            assertThat(RecapRoutes.decode(RecapRoutes.encode(period))).isEqualTo(period)
        }
        assertThat(RecapRoutes.decode("not a period")).isNull()
        assertThat(RecapRoutes.decode(null)).isNull()
    }

    @Test
    fun `an empty period still opens and closes the story`() {
        val summary = RecapAggregates.summarize(RecapPeriod.Month(september), VideoStatsSnapshot(), MusicStatsStorage.SerializableStats())
        assertThat(storyPages(summary)).containsExactly(StoryPage.Intro, StoryPage.Summary).inOrder()
    }

    @Test
    fun `pages appear only when the period has data for them`() {
        val month =
            VideoMonthRecord(
                views = 12,
                watchedMs = 3_600_000L,
                formatMs = mapOf(ViewFormat.LONG to 3_000_000L, ViewFormat.SHORT to 600_000L),
                channelViews = mapOf("UCa" to 8, "UCb" to 4),
                channelNames = mapOf("UCa" to "A", "UCb" to "B"),
                videoViews = mapOf("v1" to 3),
                videoTitles = mapOf("v1" to "Repeat me"),
                topicViews = mapOf("space" to 5),
                hourViews = mapOf(21 to 12),
                dayMs = mapOf(1 to 60_000L, 2 to 60_000L),
            )
        val video = VideoStatsSnapshot(months = mapOf(LedgerTime.monthKey(september) to month))
        val pages = storyPages(RecapAggregates.summarize(RecapPeriod.Month(september), video, MusicStatsStorage.SerializableStats()))

        assertThat(pages.first()).isEqualTo(StoryPage.Intro)
        assertThat(pages.last()).isEqualTo(StoryPage.Summary)
        assertThat(
            pages
                .filterIsInstance<StoryPage.TopChannel>()
                .single()
                .channel.name,
        ).isEqualTo("A")
        assertThat(
            pages
                .filterIsInstance<StoryPage.OnRepeat>()
                .single()
                .video.name,
        ).isEqualTo("Repeat me")
        assertThat(pages).containsAtLeast(StoryPage.Topics, StoryPage.Clock, StoryPage.Streak, StoryPage.Formats)
        assertThat(pages.none { it is StoryPage.TopArtist }).isTrue()
    }
}
