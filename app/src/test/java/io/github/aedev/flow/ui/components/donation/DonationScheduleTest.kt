package io.github.aedev.flow.ui.components.donation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DonationScheduleTest {
    private val now = 10_000_000_000L

    @Test
    fun `the first run only starts the grace period`() {
        assertThat(
            DonationSchedule.decide(now, firstLaunchMs = 0, lastShownMs = 0, disabled = false),
        ).isEqualTo(DonationDecision.START_GRACE)
    }

    @Test
    fun `nothing is asked during the grace period`() {
        val firstLaunch = now - DonationSchedule.GRACE_MS + 1
        assertThat(DonationSchedule.decide(now, firstLaunch, 0, false)).isEqualTo(DonationDecision.WAIT)
    }

    @Test
    fun `after the grace period it asks, then waits out the interval`() {
        val firstLaunch = now - DonationSchedule.GRACE_MS
        assertThat(DonationSchedule.decide(now, firstLaunch, 0, false)).isEqualTo(DonationDecision.SHOW)
        assertThat(
            DonationSchedule.decide(now, firstLaunch, now - DonationSchedule.INTERVAL_MS + 1, false),
        ).isEqualTo(DonationDecision.WAIT)
        assertThat(DonationSchedule.decide(now, firstLaunch, now - DonationSchedule.INTERVAL_MS, false)).isEqualTo(DonationDecision.SHOW)
    }

    @Test
    fun `don't ask again is final`() {
        assertThat(DonationSchedule.decide(now, 1, 0, disabled = true)).isEqualTo(DonationDecision.WAIT)
    }
}
