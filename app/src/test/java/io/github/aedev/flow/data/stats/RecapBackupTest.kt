package io.github.aedev.flow.data.stats

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.recommendation.music.MusicStatsLedger
import io.github.aedev.flow.data.recommendation.music.MusicStatsLedgerOps
import io.github.aedev.flow.data.recommendation.music.toSerializable
import org.junit.Test

class RecapBackupTest {
    private val now = 1_790_000_000_000L

    @Test
    fun `both ledgers round trip through one backup entry`() {
        val video = VideoStatsLedger()
        VideoStatsLedgerOps.recordView(
            video,
            now,
            ViewEvent("v1", "T", "UCa", "A", ViewFormat.LONG, 60_000L, counted = true, skipped = false),
            listOf("space"),
        )
        val music = MusicStatsLedger()
        MusicStatsLedgerOps.record(music, now, "UCm", "M", "t1", "Song", null, 60_000L, counted = true, newArtist = true)

        val backup = RecapBackup(video = video.toSnapshot(), music = music.toSerializable())
        val restored = RecapBackup.decode(backup.encode())

        assertThat(restored).isEqualTo(backup)
    }

    @Test
    fun `an unreadable entry restores nothing`() {
        assertThat(RecapBackup.decode("not json".encodeToByteArray())).isNull()
    }
}
