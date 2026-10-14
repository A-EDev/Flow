package io.github.aedev.flow.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.music.MusicStatsStorage
import io.github.aedev.flow.data.stats.RecapAggregates
import io.github.aedev.flow.data.stats.VideoMonthRecord
import io.github.aedev.flow.data.stats.VideoStatsSnapshot
import io.github.aedev.flow.ui.screens.settings.wellbeing.summarizeLedgerTime
import org.junit.Test
import java.time.LocalDate

class WellbeingLedgerTest {
    private val today = LocalDate.of(2026, 9, 23)

    @Test
    fun `a ledger younger than a week leaves the estimate to watch history`() {
        assertThat(summarizeLedgerTime(emptyMap(), ledgerStart = today.minusDays(3), today = today)).isNull()
        assertThat(summarizeLedgerTime(emptyMap(), ledgerStart = null, today = today)).isNull()
    }

    @Test
    fun `a full week of ledger reads the real time per day`() {
        val summary = summarizeLedgerTime(mapOf(today to 60_000L, today.minusDays(6) to 30_000L), today.minusDays(10), today)

        assertThat(summary?.days?.map { it.millis }).containsExactly(30_000L, 0L, 0L, 0L, 0L, 0L, 60_000L).inOrder()
    }

    @Test
    fun `daily time adds both ledgers inside the range only`() {
        val video = VideoStatsSnapshot(months = mapOf("2026-09" to VideoMonthRecord(dayMs = mapOf(23 to 60_000L, 1 to 5_000L))))
        val music =
            MusicStatsStorage.SerializableStats(
                months =
                    mapOf("2026-09" to MusicStatsStorage.SerializableMonth(dayMs = mapOf(23 to 30_000L))),
            )

        val days = RecapAggregates.dailyTime(video, music, today.minusDays(6), today)
        assertThat(days).containsExactly(today, 90_000L)
        assertThat(RecapAggregates.videoLedgerStart(video)).isEqualTo(LocalDate.of(2026, 9, 1))
    }
}
