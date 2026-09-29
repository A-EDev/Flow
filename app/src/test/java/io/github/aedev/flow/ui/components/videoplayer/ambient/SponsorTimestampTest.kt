package io.github.aedev.flow.ui.screens.player.components

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SponsorTimestampTest {
    @Test
    fun `valid ranges require start before end within duration`() {
        assertThat(isValidSponsorTimestampRange(0, 1_000, 5_000)).isTrue()
        assertThat(isValidSponsorTimestampRange(0, 5_000, 5_000)).isTrue()
        assertThat(isValidSponsorTimestampRange(0, 1_000, 0)).isTrue()
        assertThat(isValidSponsorTimestampRange(1_000, 1_000, 5_000)).isFalse()
        assertThat(isValidSponsorTimestampRange(-1, 1_000, 5_000)).isFalse()
        assertThat(isValidSponsorTimestampRange(0, 6_000, 5_000)).isFalse()
    }

    @Test
    fun `timestamp parser accepts padded minutes and hours`() {
        assertThat(parseTimestamp("00:10")).isEqualTo(10_000)
        assertThat(parseTimestamp("1:02:03")).isEqualTo(3_723_000)
        assertThat(parseTimestamp("00:70")).isNull()
        assertThat(parseTimestamp("not-a-time")).isNull()
    }
}
