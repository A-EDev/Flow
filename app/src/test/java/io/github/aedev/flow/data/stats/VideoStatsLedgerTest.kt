package io.github.aedev.flow.data.stats

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class VideoStatsLedgerTest {
    private val zone: ZoneId = ZoneOffset.UTC
    private val now = at(2026, 9, 14, 21)

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
    ): Long = LocalDateTime.of(year, month, day, hour, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun view(
        videoId: String = "v1",
        channelId: String = "UCa",
        format: ViewFormat = ViewFormat.LONG,
        watchedMs: Long = 120_000L,
        counted: Boolean = true,
        skipped: Boolean = false,
    ) = ViewEvent(videoId, "Title $videoId", channelId, "Channel $channelId", format, watchedMs, counted, skipped)

    private fun VideoStatsLedger.record(
        event: ViewEvent,
        time: Long = now,
        topics: List<String> = listOf("space", "physics", "science", "extra"),
    ) = VideoStatsLedgerOps.recordView(this, time, event, topics, zone)

    private fun VideoStatsLedger.month(time: Long = now) = months.getValue(LedgerTime.at(time, zone).monthKey)

    @Test
    fun `a counted view lands in every aggregate of its month`() {
        val ledger = VideoStatsLedger()
        ledger.record(view())

        val month = ledger.month()
        assertThat(month.views).isEqualTo(1)
        assertThat(month.sessions).isEqualTo(1)
        assertThat(month.watchedMs).isEqualTo(120_000L)
        assertThat(month.formatViews[ViewFormat.LONG]).isEqualTo(1)
        assertThat(month.channelViews["UCa"]).isEqualTo(1)
        assertThat(month.channelMs["UCa"]).isEqualTo(120_000L)
        assertThat(month.channelNames["UCa"]).isEqualTo("Channel UCa")
        assertThat(month.videoViews["v1"]).isEqualTo(1)
        assertThat(month.videoTitles["v1"]).isEqualTo("Title v1")
        assertThat(month.topicViews.keys).containsExactly("space", "physics", "science")
        assertThat(month.dayViews).containsExactly(14, 1)
        assertThat(month.dayMs).containsExactly(14, 120_000L)
        assertThat(month.hourViews).containsExactly(21, 1)
    }

    @Test
    fun `a rewatch counts as a second view of the same video`() {
        val ledger = VideoStatsLedger()
        ledger.record(view())
        ledger.record(view())

        assertThat(ledger.month().videoViews["v1"]).isEqualTo(2)
        assertThat(ledger.month().channelViews["UCa"]).isEqualTo(2)
    }

    @Test
    fun `a skip adds time and names what was passed over without counting a view`() {
        val ledger = VideoStatsLedger()
        ledger.record(view(watchedMs = 15_000L, counted = false, skipped = true))

        val month = ledger.month()
        assertThat(month.views).isEqualTo(0)
        assertThat(month.watchedMs).isEqualTo(15_000L)
        assertThat(month.videoSkips["v1"]).isEqualTo(1)
        assertThat(month.channelSkips["UCa"]).isEqualTo(1)
        assertThat(month.videoTitles["v1"]).isEqualTo("Title v1")
        assertThat(month.topicViews).isEmpty()
    }

    @Test
    fun `a session with no time and no view leaves nothing`() {
        val ledger = VideoStatsLedger()
        ledger.record(view(watchedMs = 0L, counted = false))
        assertThat(ledger.months).isEmpty()
    }

    @Test
    fun `formats are counted apart`() {
        val ledger = VideoStatsLedger()
        ledger.record(view(videoId = "s1", format = ViewFormat.SHORT, watchedMs = 20_000L))
        ledger.record(view(videoId = "l1", format = ViewFormat.LIVE, watchedMs = 600_000L))

        assertThat(ledger.month().formatViews).containsExactly(ViewFormat.SHORT, 1, ViewFormat.LIVE, 1)
        assertThat(ledger.month().formatMs).containsExactly(ViewFormat.SHORT, 20_000L, ViewFormat.LIVE, 600_000L)
    }

    @Test
    fun `only channels never seen before are discoveries`() {
        val ledger = VideoStatsLedger()
        ledger.seedChannels.add("UCold")
        ledger.rebuildKnownChannels()

        ledger.record(view(channelId = "UCold"))
        ledger.record(view(videoId = "v2", channelId = "UCnew"))
        ledger.record(view(videoId = "v3", channelId = "UCnew"), time = at(2026, 10, 2, 9))

        assertThat(ledger.month().discoveredChannels).containsExactly("UCnew")
        assertThat(ledger.month(at(2026, 10, 2, 9)).discoveredChannels).isEmpty()
    }

    @Test
    fun `known channels survive a round trip without being stored beyond the seed`() {
        val ledger = VideoStatsLedger()
        ledger.seedChannels.add("UCold")
        ledger.record(view(channelId = "UCnew"))

        val snapshot = ledger.toSnapshot()
        assertThat(snapshot.seedChannels).containsExactly("UCold")

        val restored = snapshot.toLedger()
        assertThat(restored.knownChannels).containsExactly("UCold", "UCnew")
        restored.record(view(videoId = "v9", channelId = "UCnew"))
        assertThat(restored.month().discoveredChannels).containsExactly("UCnew")
    }

    @Test
    fun `views split across calendar months`() {
        val ledger = VideoStatsLedger()
        ledger.record(view(), time = at(2026, 8, 31, 23))
        ledger.record(view(), time = at(2026, 9, 1, 0))

        assertThat(ledger.months.keys).containsExactly("2026-08", "2026-09")
    }

    @Test
    fun `a dislike is kept once with its details and can be taken back`() {
        val ledger = VideoStatsLedger()
        val disliked = DislikedVideo("v1", "Bad take", "UCa", now)
        VideoStatsLedgerOps.recordDislike(ledger, now, disliked, zone)
        VideoStatsLedgerOps.recordDislike(ledger, now, disliked.copy(at = now + 1), zone)
        assertThat(ledger.month().dislikes).hasSize(1)

        VideoStatsLedgerOps.clearDislike(ledger, now, "v1", zone)
        assertThat(ledger.month().dislikes).isEmpty()
    }

    @Test
    fun `searches always count but keep their text only when given`() {
        val ledger = VideoStatsLedger()
        VideoStatsLedgerOps.recordSearch(ledger, now, "  Black Holes ", zone)
        VideoStatsLedgerOps.recordSearch(ledger, now, null, zone)

        assertThat(ledger.month().actions[LedgerAction.SEARCH]).isEqualTo(2)
        assertThat(ledger.month().queries).containsExactly("black holes", 1)

        VideoStatsLedgerOps.clearQueries(ledger)
        assertThat(ledger.month().queries).isEmpty()
        assertThat(ledger.month().actions[LedgerAction.SEARCH]).isEqualTo(2)
    }

    @Test
    fun `sponsor skips add up per category`() {
        val ledger = VideoStatsLedger()
        VideoStatsLedgerOps.recordSponsorSkip(ledger, now, "sponsor", 30_000L, zone)
        VideoStatsLedgerOps.recordSponsorSkip(ledger, now, "sponsor", 12_000L, zone)
        VideoStatsLedgerOps.recordSponsorSkip(ledger, now, "intro", 0L, zone)

        assertThat(ledger.month().sponsorSkippedMs).containsExactly("sponsor", 42_000L)
    }

    @Test
    fun `per-month caps drop the weakest channels and their names`() {
        val ledger = VideoStatsLedger()
        repeat(3) { ledger.record(view(videoId = "fav$it", channelId = "UCfav")) }
        (0 until VideoStatsParams.CHANNELS_PER_MONTH + 20).forEach { i ->
            ledger.record(view(videoId = "v$i", channelId = "UC$i"))
        }

        val month = ledger.month()
        assertThat(month.channelViews.size).isAtMost(VideoStatsParams.CHANNELS_PER_MONTH)
        assertThat(month.channelViews).containsKey("UCfav")
        assertThat(month.channelNames.keys).containsAtLeastElementsIn(month.channelViews.keys)
    }

    @Test
    fun `only the newest months are kept`() {
        val ledger = VideoStatsLedger()
        (0 until VideoStatsParams.MONTHS_MAX + 3).forEach { offset ->
            val time = at(2023 + offset / 12, offset % 12 + 1, 10, 12)
            ledger.record(view(videoId = "v$offset"), time = time)
        }
        assertThat(ledger.months.size).isEqualTo(VideoStatsParams.MONTHS_MAX)
        assertThat(ledger.months.keys.minOrNull()).isEqualTo("2023-04")
    }

    @Test
    fun `the snapshot round trips every field`() {
        val ledger = VideoStatsLedger()
        ledger.record(view())
        ledger.record(view(videoId = "s1", format = ViewFormat.SHORT, counted = false, skipped = true, watchedMs = 3_000L))
        VideoStatsLedgerOps.recordDislike(ledger, now, DislikedVideo("v2", "Nope", "UCb", now), zone)
        VideoStatsLedgerOps.recordAction(ledger, now, LedgerAction.SAVE, zone)
        VideoStatsLedgerOps.recordSponsorSkip(ledger, now, "sponsor", 5_000L, zone)

        val json = LedgerJson.encodeToString(VideoStatsSnapshot.serializer(), ledger.toSnapshot())
        val restored = LedgerJson.decodeFromString(VideoStatsSnapshot.serializer(), json)
        assertThat(restored).isEqualTo(ledger.toSnapshot())
    }
}
